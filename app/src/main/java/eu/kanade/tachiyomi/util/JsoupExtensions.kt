package eu.kanade.tachiyomi.util

import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

fun Response.asJsoup(baseUrl: String? = null): Document {
    val html = body.string() ?: ""
    return Jsoup.parse(html, baseUrl ?: request.url.toString())
}
