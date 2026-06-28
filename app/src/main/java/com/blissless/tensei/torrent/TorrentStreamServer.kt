package com.blissless.tensei.torrent

import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.Executors

class TorrentStreamServer(private val saveDir: File) {

    @Volatile var running = false
    private var serverSocket: ServerSocket? = null
    private var safeBytes: () -> Long = { 0L }
    private var pieceChecker: ((Int) -> Boolean)? = null
    private var pieceSize: Long = 4L * 1024 * 1024
    @Volatile private var totalFileSize: Long = 0L

    private val executor = Executors.newCachedThreadPool { r ->
        Thread(r, "stream-client").also { it.isDaemon = true }
    }

    fun setSafeBytesProvider(provider: () -> Long) { safeBytes = provider }
    fun setPieceChecker(checker: (Int) -> Boolean) { pieceChecker = checker }
    fun setPieceSize(size: Long) { pieceSize = size }
    fun setTotalFileSize(size: Long) { totalFileSize = size }

    fun start(port: Int = 0): Int {
        serverSocket = ServerSocket(port, 50, InetAddress.getByName("127.0.0.1"))
        running = true
        val actualPort = serverSocket!!.localPort
        Log.d(TAG, "start: server listening on 127.0.0.1:$actualPort, saveDir=${saveDir.absolutePath}")
        Log.d(TAG, "start: totalFileSize=$totalFileSize, pieceSize=$pieceSize")
        thread(name = "stream-server") {
            Log.d(TAG, "start: accept loop started")
            var clientCount = 0
            while (running) {
                try {
                    val client = serverSocket!!.accept()
                    clientCount++
                    Log.d(TAG, "start: accepted client #$clientCount from ${client.inetAddress}:${client.port}")
                    executor.execute { handleClient(client) }
                } catch (e: java.io.IOException) {
                    if (running) Log.e(TAG, "start: accept error", e)
                }
            }
            Log.d(TAG, "start: accept loop ended, served $clientCount clients")
        }
        return actualPort
    }

    fun stop() {
        Log.d(TAG, "stop: shutting down server")
        running = false
        executor.shutdownNow()
        try {
            serverSocket?.close()
            Log.d(TAG, "stop: server socket closed")
        } catch (e: Exception) { Log.e(TAG, "stop: error closing socket", e) }
    }

    private fun handleClient(client: Socket) {
        val clientAddr = "${client.inetAddress}:${client.port}"
        Log.d(TAG, "handleClient: handling $clientAddr")
        try {
            client.soTimeout = 120000
            val reader = BufferedReader(InputStreamReader(client.getInputStream(), "UTF-8"))
            val requestLine = reader.readLine()
            if (requestLine == null) {
                Log.w(TAG, "handleClient: empty request from $clientAddr")
                client.close(); return
            }
            Log.d(TAG, "handleClient: request line: $requestLine")
            val parts = requestLine.split(" ")
            if (parts.size < 2) {
                Log.w(TAG, "handleClient: malformed request line from $clientAddr")
                return
            }
            val method = parts[0]
            val requestPath = URLDecoder.decode(parts[1], "UTF-8")

            val headers = mutableMapOf<String, String>()
            var line: String?
            while (reader.readLine().also { line = it } != null && line!!.isNotEmpty()) {
                val idx = line!!.indexOf(':')
                if (idx > 0) {
                    headers[line!!.substring(0, idx).trim().lowercase()] =
                        line!!.substring(idx + 1).trim()
                }
            }
            Log.d(TAG, "handleClient: method=$method path=$requestPath headers=${headers.keys}")

            val relativePath = requestPath.trimStart('/')
            val file = File(saveDir, relativePath)
            Log.d(TAG, "handleClient: looking for file: ${file.absolutePath}, exists=${file.exists()}")

            if (!file.exists()) {
                Log.d(TAG, "handleClient: file not found yet, waiting up to 60s...")
                val waitDeadline = System.nanoTime() + 60_000_000_000L
                var waited = false
                while (System.nanoTime() < waitDeadline && running) {
                    if (file.exists()) { waited = true; break }
                    try { Thread.sleep(200) } catch (_: InterruptedException) { break }
                }
                Log.d(TAG, "handleClient: waited for file, exists=${file.exists()} waited=$waited")
                if (!file.exists()) {
                    Log.e(TAG, "handleClient: file not found after waiting: ${file.absolutePath}")
                    sendError(client, 404, "Not Found")
                    return
                }
            }

            val fileLength = if (totalFileSize > 0) totalFileSize else file.length()
            Log.d(TAG, "handleClient: fileLength=$fileLength, totalFileSize=$totalFileSize")
            if (fileLength <= 0) {
                Log.e(TAG, "handleClient: empty file (length=$fileLength)")
                sendError(client, 500, "Empty file"); return
            }

            val rangeHeader = headers["range"]
            var startOffset = 0L
            var endOffset = fileLength - 1
            val isRange: Boolean

            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                val range = rangeHeader.substring(6).trim()
                val dashIdx = range.indexOf('-')
                Log.d(TAG, "handleClient: range header=$rangeHeader, parsed range=$range, dashIdx=$dashIdx")
                if (dashIdx > 0) {
                    startOffset = range.substring(0, dashIdx).toLongOrNull() ?: 0L
                    val endStr = range.substring(dashIdx + 1)
                    if (endStr.isNotEmpty()) { endOffset = endStr.toLongOrNull() ?: (fileLength - 1) }
                    startOffset = startOffset.coerceIn(0, fileLength - 1)
                    endOffset = endOffset.coerceIn(startOffset, fileLength - 1)
                    isRange = true
                } else if (dashIdx == 0) {
                    val suffixLen = range.substring(1).toLongOrNull() ?: fileLength
                    startOffset = maxOf(0L, fileLength - suffixLen)
                    isRange = true
                } else { isRange = false }
                Log.d(TAG, "handleClient: range request: $startOffset-$endOffset/$fileLength")
            } else {
                isRange = false
                Log.d(TAG, "handleClient: no range header, full file request")
            }

            if (method == "HEAD") {
                Log.d(TAG, "handleClient: HEAD request, sending headers")
                val resp = buildHeaders(if (isRange) 206 else 200, endOffset - startOffset + 1, file.name, isRange, startOffset, endOffset, fileLength)
                client.getOutputStream().write(resp.toByteArray()); client.close(); return
            }

            val safeNow = minOf(safeBytes(), fileLength)
            Log.d(TAG, "handleClient: safeBytes=$safeNow, startOffset=$startOffset, fileLength=$fileLength, need to wait for pieces? ${startOffset >= safeNow}")
            if (startOffset >= safeNow) {
                val startPiece = (startOffset / pieceSize).toInt()
                val endPiece = (minOf(endOffset, fileLength - 1) / pieceSize).toInt()
                Log.d(TAG, "handleClient: waiting for pieces $startPiece-$endPiece (pieceSize=$pieceSize)")
                if (pieceChecker?.invoke(startPiece) != true || (startPiece != endPiece && pieceChecker?.invoke(endPiece) != true)) {
                    Log.d(TAG, "handleClient: pieces not available, waiting up to 30s...")
                    val deadline = System.nanoTime() + 30_000_000_000L
                    var available = false
                    while (System.nanoTime() < deadline && running) {
                        val p = pieceChecker
                        if (p != null && p(startPiece) && (startPiece == endPiece || p(endPiece))) { available = true; break }
                        try { Thread.sleep(100) } catch (_: InterruptedException) { break }
                    }
                    if (!available) {
                        Log.e(TAG, "handleClient: pieces $startPiece-$endPiece not available after waiting")
                        sendError(client, 503, "Not yet available"); return
                    }
                    Log.d(TAG, "handleClient: pieces now available after waiting")
                } else {
                    Log.d(TAG, "handleClient: pieces already available")
                }
            }

            val actualEnd = minOf(endOffset, fileLength - 1)
            val sendLength = actualEnd - startOffset + 1
            Log.d(TAG, "handleClient: sending $sendLength bytes [$startOffset-$actualEnd] to $clientAddr")
            val resp = buildHeaders(if (isRange) 206 else 200, sendLength, file.name, isRange, startOffset, actualEnd, fileLength)
            val out = client.getOutputStream()
            out.write(resp.toByteArray())

            val raf = RandomAccessFile(file, "r")
            val startTime = System.nanoTime()
            var totalSent = 0L
            try {
                raf.seek(startOffset)
                val buf = ByteArray(262144)
                var remaining = sendLength
                var pos = startOffset
                var waitStart = System.nanoTime()

                while (remaining > 0 && running) {
                    val currentSafe = minOf(safeBytes(), fileLength)
                    var canRead: Long
                    if (pos < currentSafe) {
                        canRead = minOf(remaining, currentSafe - pos)
                    } else {
                        val p = (pos / pieceSize).toInt()
                        if (pieceChecker?.invoke(p) == true) {
                            val pieceEnd = ((p + 1) * pieceSize).coerceAtMost(fileLength)
                            canRead = minOf(remaining, pieceEnd - pos)
                        } else { canRead = 0L }
                    }
                    if (canRead <= 0) {
                        val waitTime = System.nanoTime() - waitStart
                        if (waitTime > 30_000_000_000L) {
                            Log.w(TAG, "handleClient: timed out waiting for data at pos=$pos (waited ${waitTime / 1_000_000}ms)")
                            break
                        }
                        if (waitTime > 5_000_000_000L && (waitTime / 1000) % 2000 < 100) {
                            Log.d(TAG, "handleClient: still waiting for data at pos=$pos (waited ${waitTime / 1_000_000}ms)")
                        }
                        try { Thread.sleep(100) } catch (_: InterruptedException) { break }
                        continue
                    }
                    waitStart = System.nanoTime()
                    val toRead = minOf(buf.size.toLong(), canRead).toInt()
                    val read = raf.read(buf, 0, toRead)
                    if (read < 0) {
                        Log.w(TAG, "handleClient: unexpected EOF at pos=$pos")
                        break
                    }
                    out.write(buf, 0, read)
                    remaining -= read; pos += read; totalSent += read
                }
            } finally {
                raf.close()
                val elapsed = (System.nanoTime() - startTime) / 1_000_000
                val rate = if (elapsed > 0) (totalSent * 1000 / elapsed / 1024) else 0
                Log.d(TAG, "handleClient: sent $totalSent bytes to $clientAddr in ${elapsed}ms (${rate}KB/s)")
            }
            out.flush()
            client.close()
            Log.d(TAG, "handleClient: done with $clientAddr")
        } catch (e: Exception) {
            if (running) Log.e(TAG, "handleClient: error for $clientAddr", e)
            try { client.close() } catch (_: Exception) {}
        }
    }

    private fun buildHeaders(status: Int, contentLen: Long, fileName: String, isRange: Boolean, start: Long, end: Long, fileLen: Long): String {
        val sb = StringBuilder()
        sb.append("HTTP/1.1 $status ${if (status == 206) "Partial Content" else "OK"}\r\n")
        sb.append("Content-Type: ${getMimeType(fileName)}\r\n")
        sb.append("Content-Length: $contentLen\r\n")
        sb.append("Accept-Ranges: bytes\r\n")
        sb.append("Connection: close\r\n")
        if (isRange) sb.append("Content-Range: bytes $start-$end/$fileLen\r\n")
        sb.append("\r\n")
        return sb.toString()
    }

    private fun sendError(client: Socket, code: Int, msg: String) {
        val resp = "HTTP/1.1 $code $msg\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
        try { client.getOutputStream().write(resp.toByteArray()); client.getOutputStream().flush() } catch (_: Exception) {}
        try { client.close() } catch (_: Exception) {}
    }

    private fun thread(name: String, action: () -> Unit): Thread {
        return Thread(action, name).also { it.isDaemon = true; it.start() }
    }

    companion object {
        private const val TAG = "TorrentStreamServer"
        fun getMimeType(filename: String): String {
            val ext = filename.substringAfterLast('.', "").lowercase()
            return when (ext) {
                "mkv" -> "video/x-matroska"
                "mp4" -> "video/mp4"
                "webm" -> "video/webm"
                "avi" -> "video/x-msvideo"
                "mov" -> "video/quicktime"
                "ts" -> "video/mp2t"
                "m4v" -> "video/x-m4v"
                else -> "application/octet-stream"
            }
        }
    }
}
