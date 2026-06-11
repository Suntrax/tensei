package eu.kanade.tachiyomi.animesource.online

import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

abstract class ParsedAnimeHttpSource : AnimeHttpSource() {

    override fun popularAnimeParse(response: Response): AnimesPage {
        val document = response.asJsoup()

        val animes = document.select(popularAnimeSelector()).map { element ->
            popularAnimeFromElement(element)
        }

        val hasNextPage = popularAnimeNextPageSelector()?.let { selector ->
            document.select(selector).first()
        } != null

        return AnimesPage(animes, hasNextPage)
    }

    protected open fun popularAnimeSelector(): String = ""

    protected open fun popularAnimeFromElement(element: Element): SAnime {
        throw UnsupportedOperationException("popularAnimeFromElement not implemented")
    }

    protected open fun popularAnimeNextPageSelector(): String? = null

    override fun searchAnimeParse(response: Response): AnimesPage {
        val document = response.asJsoup()

        val animes = document.select(searchAnimeSelector()).map { element ->
            searchAnimeFromElement(element)
        }

        val hasNextPage = searchAnimeNextPageSelector()?.let { selector ->
            document.select(selector).first()
        } != null

        return AnimesPage(animes, hasNextPage)
    }

    protected open fun searchAnimeSelector(): String = ""

    protected open fun searchAnimeFromElement(element: Element): SAnime {
        throw UnsupportedOperationException("searchAnimeFromElement not implemented")
    }

    protected open fun searchAnimeNextPageSelector(): String? = null

    override fun latestUpdatesParse(response: Response): AnimesPage {
        val document = response.asJsoup()

        val animes = document.select(latestUpdatesSelector()).map { element ->
            latestUpdatesFromElement(element)
        }

        val hasNextPage = latestUpdatesNextPageSelector()?.let { selector ->
            document.select(selector).first()
        } != null

        return AnimesPage(animes, hasNextPage)
    }

    protected open fun latestUpdatesSelector(): String = ""

    protected open fun latestUpdatesFromElement(element: Element): SAnime {
        throw UnsupportedOperationException("latestUpdatesFromElement not implemented")
    }

    protected open fun latestUpdatesNextPageSelector(): String? = null

    override fun animeDetailsParse(response: Response): SAnime {
        return animeDetailsParse(response.asJsoup())
    }

    protected open fun animeDetailsParse(document: Document): SAnime {
        throw UnsupportedOperationException("animeDetailsParse not implemented")
    }

    override fun episodeListParse(response: Response): List<SEpisode> {
        val document = response.asJsoup()
        return document.select(episodeListSelector()).map { episodeFromElement(it) }
    }

    protected open fun episodeListSelector(): String = ""

    protected open fun episodeFromElement(element: Element): SEpisode {
        throw UnsupportedOperationException("episodeFromElement not implemented")
    }

    override fun seasonListParse(response: Response): List<SAnime> {
        val document = response.asJsoup()
        return document.select(seasonListSelector()).map { seasonFromElement(it) }
    }

    protected open fun seasonListSelector(): String = ""

    protected open fun seasonFromElement(element: Element): SAnime {
        throw UnsupportedOperationException("seasonFromElement not implemented")
    }

    override fun hosterListParse(response: Response): List<Hoster> {
        val document = response.asJsoup()
        return document.select(hosterListSelector()).map(::hosterFromElement)
    }

    protected open fun hosterListSelector(): String = ""

    protected open fun hosterFromElement(element: Element): Hoster {
        throw UnsupportedOperationException("hosterFromElement not implemented")
    }

    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        return document.select(videoListSelector()).map { videoFromElement(it) }
    }

    protected open fun videoListSelector(): String = ""

    protected open fun videoFromElement(element: Element): Video {
        throw UnsupportedOperationException("videoFromElement not implemented")
    }

    override fun videoUrlParse(response: Response): String {
        return videoUrlParse(response.asJsoup())
    }

    protected open fun videoUrlParse(document: Document): String {
        throw UnsupportedOperationException("videoUrlParse not implemented")
    }
}
