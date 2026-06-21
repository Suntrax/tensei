@file:JvmName("RequestsKt")

package eu.kanade.tachiyomi.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

fun GET(url: String, headers: Headers): Request {
    return Request.Builder().url(url).headers(headers).get().build()
}

suspend fun OkHttpClient.newCachelessCallWithProgress(
    request: Request,
): Response {
    return withContext(Dispatchers.IO) {
        newCall(request).awaitSuccess()
    }
}

interface ProgressListener {
    fun update(bytesRead: Long, contentLength: Long, done: Boolean)
}
