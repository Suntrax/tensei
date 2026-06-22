package com.blissless.tensei.stream

import android.util.Log
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.animesource.model.Video
import okhttp3.OkHttpClient
import okhttp3.Request as OkRequest
import okhttp3.Response as OkResponse
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.util.concurrent.Executors

object LocalProxyServer {
    private const val TAG = "LocalProxyServer"
    const val PROXY_PORT = 41223

    private var serverSocket: ServerSocket? = null
    private var running = false
    private var extensionClient: OkHttpClient? = null
    private var currentSource: AnimeHttpSource? = null
    private val pathToVideo = mutableMapOf<String, Video>()

    fun start(client: OkHttpClient?, source: AnimeCatalogueSource?) {
        if (serverSocket != null) return
        extensionClient = client ?: try { eu.kanade.tachiyomi.network.NetworkHelper.getInstance().client } catch (_: Exception) { null }
        if (extensionClient == null) {
            Log.w(TAG, "No extension client available — proxy will return 502")
        }
        currentSource = source as? AnimeHttpSource

        try {
            serverSocket = ServerSocket()
            serverSocket!!.reuseAddress = true
            serverSocket!!.bind(InetSocketAddress(PROXY_PORT))
            running = true
            Log.i(TAG, "Proxy server started on port $PROXY_PORT")

            val executor = Executors.newSingleThreadExecutor()
            executor.submit {
                while (running) {
                    try {
                        val clientSocket = serverSocket!!.accept()
                        handleClient(clientSocket)
                    } catch (e: Exception) {
                        if (running) Log.e(TAG, "Accept error", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start proxy server", e)
        }
    }

    fun registerVideo(video: Video) {
        try {
            val path = URI(video.videoUrl).path
            pathToVideo[path] = video
            Log.d(TAG, "Registered video path: $path -> ${video.videoUrl.take(60)}")
        } catch (_: Exception) {}
    }

    fun stop() {
        running = false
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
        pathToVideo.clear()
        Log.d(TAG, "Proxy server stopped")
    }

    private fun handleClient(clientSocket: Socket) {
        try {
            clientSocket.soTimeout = 30000
            val reader = BufferedReader(InputStreamReader(clientSocket.getInputStream(), Charsets.UTF_8))
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0]
            val requestUri = parts[1]
            val uri = URI(requestUri)
            val requestPath = uri.path
            val query = uri.query

            val headers = mutableMapOf<String, String>()
            var line = reader.readLine()
            var contentLength = 0
            while (line != null && line.isNotEmpty()) {
                val colonIdx = line.indexOf(":")
                if (colonIdx > 0) {
                    val key = line.substring(0, colonIdx).trim()
                    val value = line.substring(colonIdx + 1).trim()
                    headers[key] = value
                    if (key.equals("Content-Length", ignoreCase = true)) {
                        contentLength = value.toIntOrNull() ?: 0
                    }
                }
                line = reader.readLine()
            }

            if (contentLength > 0) {
                reader.skip(contentLength.toLong())
            }

            Log.d(TAG, "Proxy $method $requestPath${if (query != null) "?$query" else ""}")

            val video = pathToVideo[requestPath]
            val videoHeaders = video?.headers
            val sourceHeaders = currentSource?.headers

            val client = extensionClient
            if (client == null) {
                sendResponse(clientSocket, 502, "text/plain", "No extension client".toByteArray())
                return
            }

            val sourceBaseUrl = currentSource?.baseUrl
            val referer = videoHeaders?.let { h ->
                (0 until h.size).firstOrNull { h.name(it).equals("Referer", ignoreCase = true) }
                    ?.let { h.value(it) }
            } ?: sourceHeaders?.let { h ->
                (0 until h.size).firstOrNull { h.name(it).equals("Referer", ignoreCase = true) }
                    ?.let { h.value(it) }
            }
            val upstreamHost = when {
                referer != null -> try { URI(referer).let { "${it.scheme}://${it.host}" } } catch (_: Exception) { null }
                sourceBaseUrl != null -> sourceBaseUrl
                else -> null
            }

            if (upstreamHost == null) {
                Log.w(TAG, "Cannot determine upstream host for $requestPath")
                sendResponse(clientSocket, 502, "text/plain", "Unknown upstream host".toByteArray())
                return
            }

            val upstreamUrl = "$upstreamHost$requestPath${if (query != null) "?$query" else ""}"
            Log.d(TAG, "Forwarding to: $upstreamUrl")

            val effectiveHeaders = videoHeaders ?: sourceHeaders
            val reqBuilder = OkRequest.Builder().url(upstreamUrl)
            if (effectiveHeaders != null) {
                for (i in 0 until effectiveHeaders.size) {
                    val name = effectiveHeaders.name(i)
                    if (!name.equals("Host", ignoreCase = true) && !name.equals("Content-Length", ignoreCase = true)) {
                        reqBuilder.header(name, effectiveHeaders.value(i))
                    }
                }
            }
            reqBuilder.header("Connection", "close")
            if (upstreamHost != null) reqBuilder.header("Origin", upstreamHost)

            val upstreamResponse: OkResponse = client.newCall(reqBuilder.build()).execute()
            val bodyBytes = upstreamResponse.body?.bytes() ?: ByteArray(0)
            val contentType = upstreamResponse.header("Content-Type") ?: "application/octet-stream"

            val responseBytes = if (contentType.contains("m3u8", ignoreCase = true) ||
                contentType.contains("vnd.apple.mpegurl", ignoreCase = true)) {
                rewriteM3u8(bodyBytes.toString(Charsets.UTF_8), upstreamHost).toByteArray(Charsets.UTF_8)
            } else {
                bodyBytes
            }

            sendResponse(clientSocket, 200, contentType, responseBytes)
            if (bodyBytes.isNotEmpty()) {
                Log.d(TAG, "Proxied $method ${requestPath.take(60)} -> ${bodyBytes.size} bytes")
            } else {
                Log.d(TAG, "Proxied $method ${requestPath.take(60)} -> empty body")
            }
            upstreamResponse.close()
        } catch (e: Exception) {
            Log.e(TAG, "Proxy handler error", e)
        } finally {
            try { clientSocket.close() } catch (_: Exception) {}
        }
    }

    private fun rewriteM3u8(content: String, upstreamHost: String): String {
        val proxyBase = "http://127.0.0.1:$PROXY_PORT"
        return content.lines().joinToString("\n") { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("http://") || trimmed.startsWith("https://") -> {
                    try {
                        val uri = URI(trimmed)
                        "$proxyBase${uri.path}${if (uri.query != null) "?${uri.query}" else ""}"
                    } catch (_: Exception) { line }
                }
                trimmed.endsWith(".ts") || trimmed.endsWith(".m3u8") ||
                    trimmed.endsWith(".aac") || trimmed.endsWith(".mp4") ||
                    trimmed.endsWith(".vtt") || trimmed.endsWith(".png") ||
                    trimmed.endsWith(".jpg") -> {
                    if (trimmed.startsWith("/")) "$proxyBase$trimmed" else "$proxyBase/$trimmed"
                }
                else -> line
            }
        }
    }

    private fun sendResponse(socket: Socket, statusCode: Int, contentType: String, body: ByteArray) {
        try {
            val writer = socket.getOutputStream()
            val statusText = when (statusCode) {
                200 -> "OK"
                502 -> "Bad Gateway"
                else -> "Error"
            }
            val header = "HTTP/1.1 $statusCode $statusText\r\n" +
                "Content-Type: $contentType\r\n" +
                "Content-Length: ${body.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n" +
                "\r\n"
            writer.write(header.toByteArray())
            writer.write(body)
            writer.flush()
        } catch (_: Exception) {}
    }
}
