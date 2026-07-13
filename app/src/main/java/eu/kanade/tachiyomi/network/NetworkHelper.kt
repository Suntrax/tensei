package eu.kanade.tachiyomi.network

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class NetworkHelper {
    val client: OkHttpClient
    val cloudflareClient: OkHttpClient
    val trustAllClient: OkHttpClient

    init {
        val cookieJar = object : CookieJar {
            private val store = ConcurrentHashMap<String, List<Cookie>>()

            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                store[url.host] = cookies
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                return store[url.host] ?: emptyList()
            }
        }

        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .cookieJar(cookieJar)
            .addInterceptor { chain ->
                val request = chain.request()
                val host = request.url.host
                // Ensure Referer is always set for aniwave sites (required by their API)
                val newRequest = if (!request.headers.names().any { it.equals("Referer", ignoreCase = true) }
                    && (host.contains("animewave") || host.contains("aniwave"))) {
                    request.newBuilder()
                        .header("Referer", "https://$host/")
                        .build()
                } else {
                    request
                }
                val response = chain.proceed(newRequest)
                if (response.code == 500) {
                    // HTTP 500 logged silently
                }
                response
            }

        client = builder.build()
        cloudflareClient = client

        val defaultTmFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
            init(null as KeyStore?)
        }
        val defaultTrustManager = defaultTmFactory.trustManagers.filterIsInstance<X509TrustManager>().first()
        val trustAllManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = defaultTrustManager.acceptedIssuers
        }
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(trustAllManager), SecureRandom())
        }
        trustAllClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS)
            .sslSocketFactory(sslContext.socketFactory, trustAllManager)
            .hostnameVerifier { _, _ -> true }
            .cookieJar(cookieJar)
            .build()
    }

    fun defaultUserAgentProvider(): String {
        return "Mozilla/5.0 (Android 14; Mobile; rv:120.0) Gecko/120.0 Firefox/120.0"
    }

    companion object {
        @Volatile
        private var instance: NetworkHelper? = null

        fun getInstance(): NetworkHelper {
            return instance ?: synchronized(this) {
                instance ?: NetworkHelper().also { instance = it }
            }
        }
    }
}
