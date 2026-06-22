package eu.kanade.tachiyomi.animesource.online

import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.ProgressListener
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.newCachelessCallWithProgress
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.net.URI
import java.net.URISyntaxException
import java.security.MessageDigest
import java.util.Locale

abstract class AnimeHttpSource : AnimeCatalogueSource {

    protected val network: NetworkHelper by lazy { NetworkHelper.getInstance() }

    abstract val baseUrl: String

    open val versionId = 1

    override val id by lazy { generateId(name, lang, versionId) }

    val headers: Headers by lazy { headersBuilder().build() }

    open val client: OkHttpClient
        get() = network.client

    protected fun generateId(name: String, lang: String, versionId: Int): Long {
        val key = "${name.lowercase(Locale.ROOT)}/$lang/$versionId"
        val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
        return (0..7).map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }.reduce(Long::or) and Long.MAX_VALUE
    }

    protected open fun headersBuilder() = Headers.Builder().apply {
        add("User-Agent", network.defaultUserAgentProvider())
        add("Referer", "${baseUrl.trimEnd('/')}/")
    }

    override fun toString() = "$name (${lang.uppercase()})"

    override suspend fun getPopularAnime(page: Int): AnimesPage {
        return client.newCall(popularAnimeRequest(page))
            .awaitSuccess()
            .let { response -> popularAnimeParse(response) }
    }

    protected open fun popularAnimeRequest(page: Int): Request {
        throw UnsupportedOperationException("popularAnimeRequest not implemented")
    }

    protected open fun popularAnimeParse(response: Response): AnimesPage {
        throw UnsupportedOperationException("popularAnimeParse not implemented")
    }

    override suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage {
        return client.newCall(searchAnimeRequest(page, query, filters))
            .awaitSuccess()
            .let { response -> searchAnimeParse(response) }
    }

    protected open fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        throw UnsupportedOperationException("searchAnimeRequest not implemented")
    }

    protected open fun searchAnimeParse(response: Response): AnimesPage {
        throw UnsupportedOperationException("searchAnimeParse not implemented")
    }

    override suspend fun getLatestUpdates(page: Int): AnimesPage {
        return client.newCall(latestUpdatesRequest(page))
            .awaitSuccess()
            .let { response -> latestUpdatesParse(response) }
    }

    protected open fun latestUpdatesRequest(page: Int): Request {
        throw UnsupportedOperationException("latestUpdatesRequest not implemented")
    }

    protected open fun latestUpdatesParse(response: Response): AnimesPage {
        throw UnsupportedOperationException("latestUpdatesParse not implemented")
    }

    override suspend fun getAnimeDetails(anime: SAnime): SAnime {
        return client.newCall(animeDetailsRequest(anime))
            .awaitSuccess()
            .let { response ->
                animeDetailsParse(response).apply { initialized = true }
            }
    }

    open fun animeDetailsRequest(anime: SAnime): Request {
        return GET(resolveUrl(anime.url), headers)
    }

    protected open fun animeDetailsParse(response: Response): SAnime {
        throw UnsupportedOperationException("animeDetailsParse not implemented")
    }

    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> {
        return client.newCall(episodeListRequest(anime))
            .awaitSuccess()
            .let { response -> episodeListParse(response) }
    }

    protected open fun episodeListRequest(anime: SAnime): Request {
        return GET(resolveUrl(anime.url), headers)
    }

    protected open fun episodeListParse(response: Response): List<SEpisode> {
        return emptyList()
    }

    protected open fun episodeVideoParse(response: Response): SEpisode {
        throw UnsupportedOperationException("episodeVideoParse not implemented")
    }

    override suspend fun getSeasonList(anime: SAnime): List<SAnime> {
        return client.newCall(seasonListRequest(anime))
            .awaitSuccess()
            .let { response -> seasonListParse(response) }
    }

    protected open fun seasonListRequest(anime: SAnime): Request {
        return GET(resolveUrl(anime.url), headers)
    }

    protected open fun seasonListParse(response: Response): List<SAnime> {
        throw UnsupportedOperationException("seasonListParse not implemented")
    }

    override suspend fun getHosterList(episode: SEpisode): List<Hoster> {
        return client.newCall(hosterListRequest(episode))
            .awaitSuccess()
            .let { response -> hosterListParse(response) }
    }

    protected open fun hosterListRequest(episode: SEpisode): Request {
        return GET(resolveUrl(episode.url), headers)
    }

    protected open fun hosterListParse(response: Response): List<Hoster> {
        return emptyList()
    }

    override suspend fun getVideoList(hoster: Hoster): List<Video> {
        return client.newCall(videoListRequest(hoster))
            .awaitSuccess()
            .let { response -> videoListParse(response, hoster) }
    }

    protected open fun videoListRequest(hoster: Hoster): Request {
        return GET(hoster.hosterUrl, headers)
    }

    protected open fun videoListParse(response: Response, hoster: Hoster): List<Video> {
        return emptyList()
    }

    open suspend fun resolveVideo(video: Video): Video? {
        return video
    }

    override suspend fun getVideoList(episode: SEpisode): List<Video> {
        return client.newCall(videoListRequest(episode))
            .awaitSuccess()
            .let { response -> videoListParse(response) }
    }

    protected open fun videoListRequest(episode: SEpisode): Request {
        return GET(resolveUrl(episode.url), headers)
    }

    protected open fun videoListParse(response: Response): List<Video> {
        return emptyList()
    }

    open fun List<Hoster>.sortHosters(): List<Hoster> {
        return this
    }

    open fun List<Video>.sortVideos(): List<Video> {
        return this
    }

    open suspend fun getVideoUrl(video: Video): String {
        return client.newCall(videoUrlRequest(video))
            .awaitSuccess()
            .let { response -> videoUrlParse(response) }
    }

    protected open fun videoUrlRequest(video: Video): Request {
        return GET(video.videoUrl, headers)
    }

    protected open fun videoUrlParse(response: Response): String {
        throw UnsupportedOperationException("videoUrlParse not implemented")
    }

    suspend fun getVideo(
        request: Request,
        listener: ProgressListener,
    ): Response {
        return client.newCachelessCallWithProgress(request, listener)
    }

    fun SEpisode.setUrlWithoutDomain(url: String) {
        this.url = getUrlWithoutDomain(url)
    }

    fun SAnime.setUrlWithoutDomain(url: String) {
        this.url = getUrlWithoutDomain(url)
    }

    private fun getUrlWithoutDomain(orig: String): String {
        return try {
            val uri = URI(orig)
            var out = uri.path
            if (uri.query != null) {
                out += "?" + uri.query
            }
            if (uri.fragment != null) {
                out += "#" + uri.fragment
            }
            out
        } catch (e: URISyntaxException) {
            orig
        }
    }

    protected fun resolveUrl(path: String): String {
        return when {
            path.startsWith("http://") || path.startsWith("https://") -> path
            else -> {
                val base = baseUrl.trimEnd('/')
                val p = path.trimStart('/')
                "$base/$p"
            }
        }
    }

    open fun getAnimeUrl(anime: SAnime): String {
        return animeDetailsRequest(anime).url.toString()
    }

    open fun getEpisodeUrl(episode: SEpisode): String {
        return episode.url
    }

    open fun prepareNewEpisode(episode: SEpisode, anime: SAnime) {}

    override fun getFilterList() = AnimeFilterList()
}
