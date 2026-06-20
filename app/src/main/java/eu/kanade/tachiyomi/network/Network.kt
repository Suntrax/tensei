@file:JvmName("RequestsKt")

package eu.kanade.tachiyomi.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

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
