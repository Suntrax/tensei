@file:Suppress("unused_parameter")

package eu.kanade.tachiyomi.network

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import rx.Observable
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

fun Call.asObservable(): Observable<Response> {
    @Suppress("DEPRECATION")
    return Observable.create<Response> { subscriber ->
        enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    subscriber.onNext(response)
                    subscriber.onCompleted()
                } else {
                    subscriber.onError(HttpException(response.code))
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                subscriber.onError(e)
            }
        })
    }
}

fun Call.asObservableSuccess(): Observable<Response> {
    return asObservable()
}

suspend fun Call.await(): Response {
    return suspendCancellableCoroutine { continuation ->
        enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response)
            }

            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }
        })
        continuation.invokeOnCancellation {
            try {
                cancel()
            } catch (_: Exception) {
            }
        }
    }
}

suspend fun Call.awaitSuccess(): Response {
    val response = await()
    if (response.isSuccessful) return response
    throw HttpException(response.code)
}

class HttpException(val code: Int) : IllegalStateException("HTTP error $code")
