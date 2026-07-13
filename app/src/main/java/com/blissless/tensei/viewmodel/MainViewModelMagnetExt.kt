package com.blissless.tensei.viewmodel

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.blissless.tensei.MainViewModel
import com.blissless.tensei.data.models.AnimeMedia
import com.blissless.tensei.torrent.MagnetData
import com.blissless.tensei.torrent.MagnetExtensionClient
import com.blissless.tensei.torrent.StreamUrlResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Magnet extension logic for [MainViewModel].
 *
 * Extracted from MainViewModel.kt. Manages the MagnetExtensionClient lifecycle,
 * magnet link prefetching, and per-episode stream URL resolution.
 * Public signatures preserved as extension functions.
 */

/**
 * Lazily create the magnet extension client. [loadAvailableMagnetExtensions] is the
 * normal entry point, but playback (deep links, auto-refresh, widgets) can be
 * triggered before any screen calls it — without this guard the client stays null
 * and [fetchMagnetForEpisode] silently returns null ("no magnet link found").
 */
fun MainViewModel.ensureMagnetClient(): MagnetExtensionClient {
    return magnetExtensionClient ?: MagnetExtensionClient(context).also {
        magnetExtensionClient = it
        Log.d(MainViewModel.TAG, "ensureMagnetClient: created new MagnetExtensionClient")
    }
}

fun MainViewModel.loadAvailableMagnetExtensions() {
    Log.d(MainViewModel.TAG, "loadAvailableMagnetExtensions: start")
    val client = ensureMagnetClient()
    viewModelScope.launch(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val detected = client.detectExtensions()
        val elapsed = System.currentTimeMillis() - startTime
        Log.d(MainViewModel.TAG, "loadAvailableMagnetExtensions: found ${detected.size} extensions in ${elapsed}ms: $detected")
        _availableMagnetExtensions.value = detected.map { it.name to it.authority }
    }
}

internal fun MainViewModel.parseTitleForSearch(title: String): String {
    var cleaned = title
    cleaned = cleaned.replace(Regex("\\([^)]*\\)"), "").trim()
    cleaned = cleaned.replace(Regex("\\[[^\\]]*\\]"), "").trim()
    cleaned = cleaned.replace(Regex("\\{.*?\\}"), "").trim()
    cleaned = cleaned.replace(Regex("\\s+"), " ").trim()
    Log.v(MainViewModel.TAG, "parseTitleForSearch: '$title' -> '$cleaned'")
    return cleaned
}

fun MainViewModel.fetchMagnetEpisodes(anime: AnimeMedia, authority: String) {
    Log.d(MainViewModel.TAG, "fetchMagnetEpisodes: anime=${anime.id} title=${anime.title} authority=$authority")
    viewModelScope.launch(Dispatchers.IO) {
        fetchMagnetEpisodesSync(anime, authority)
    }
}

internal suspend fun MainViewModel.fetchMagnetEpisodesSync(anime: AnimeMedia, authority: String): MagnetData? {
    val eng = anime.titleEnglish
    Log.d(MainViewModel.TAG, "fetchMagnetEpisodesSync: anime=${anime.id} engTitle='$eng' romaji='${anime.title}' authority=$authority")
    Log.d(MainViewModel.TAG, "fetchMagnetEpisodesSync: using ONLY AniList English title, skipping romaji")

    val searchTerms = mutableListOf<String>()
    if (!eng.isNullOrBlank()) {
        val cleaned = parseTitleForSearch(eng)
        searchTerms.add(cleaned)
        if (cleaned != eng) searchTerms.add(eng)
    } else {
        Log.w(MainViewModel.TAG, "fetchMagnetEpisodesSync: no English title available, falling back to romaji")
        anime.title?.let { searchTerms.add(parseTitleForSearch(it)) }
    }
    Log.d(MainViewModel.TAG, "fetchMagnetEpisodesSync: generated ${searchTerms.size} English-only search terms: $searchTerms")

    val client = ensureMagnetClient()
    var result: MagnetData? = null
    for ((i, query) in searchTerms.withIndex()) {
        val startTime = System.currentTimeMillis()
        val fetched = try {
            client.fetchMagnets(authority, anime.id, query, parseTitleForSearch(anime.title ?: query), preferredCategory.value)
        } catch (e: Exception) {
            Log.e(MainViewModel.TAG, "fetchMagnetEpisodesSync: fetchMagnets threw for term[$i]='$query'", e)
            null
        }
        val elapsed = System.currentTimeMillis() - startTime
        Log.d(MainViewModel.TAG, "fetchMagnetEpisodesSync: term[$i]='$query' -> result=${fetched != null} episodes=${fetched?.episodes?.size ?: 0} in ${elapsed}ms")
        if (fetched != null) {
            if (fetched.episodes.isEmpty()) {
                Log.w(MainViewModel.TAG, "fetchMagnetEpisodesSync: extension returned MagnetData but 0 episodes (isSingleTorrent=${fetched.isSingleTorrent})")
            } else {
                result = fetched
                break
            }
        }
    }

    if (result != null) {
        Log.d(MainViewModel.TAG, "fetchMagnetEpisodesSync: success — ${result.episodes.size} episodes for anime ${anime.id}")
        _magnetEpisodes.value += (anime.id to result)
    } else {
        Log.w(MainViewModel.TAG, "fetchMagnetEpisodesSync: no magnet data found for anime ${anime.id} with any search term")
        _magnetEpisodes.value += (anime.id to MagnetData(emptyList(), false))
    }
    return result
}

fun MainViewModel.clearMagnetEpisodes(animeId: Int) {
    Log.d(MainViewModel.TAG, "clearMagnetEpisodes: removing cached magnet data for animeId=$animeId")
    _magnetEpisodes.value -= animeId
}

fun MainViewModel.getMagnetEpisodeNumbers(animeId: Int): Set<Int> {
    val data = _magnetEpisodes.value[animeId]
    if (data == null) {
        Log.d(MainViewModel.TAG, "getMagnetEpisodeNumbers: no data for animeId=$animeId")
        return emptySet()
    }
    if (data.isSingleTorrent && data.episodes.size == 1) {
        Log.d(MainViewModel.TAG, "getMagnetEpisodeNumbers: single torrent mode, returning empty set")
        return emptySet()
    }
    val eps = data.episodes.map { it.episode }.toSet()
    Log.d(MainViewModel.TAG, "getMagnetEpisodeNumbers: animeId=$animeId -> ${eps.size} episodes: $eps")
    return eps
}

fun MainViewModel.getMagnetForEpisode(animeId: Int, episode: Int): String? {
    Log.d(MainViewModel.TAG, "getMagnetForEpisode: animeId=$animeId ep=$episode")
    val data = _magnetEpisodes.value[animeId]
    if (data == null) {
        Log.d(MainViewModel.TAG, "getMagnetForEpisode: no cached data for animeId=$animeId")
        return null
    }
    val magnet = if (data.isSingleTorrent) {
        Log.d(MainViewModel.TAG, "getMagnetForEpisode: single-torrent mode")
        data.episodes.firstOrNull()?.magnet
    } else {
        val ep = data.episodes.find { it.episode == episode }
        Log.d(MainViewModel.TAG, "getMagnetForEpisode: cached episodes=${data.episodes.map { it.episode }} found=${ep != null}")
        ep?.magnet
    }
    Log.d(MainViewModel.TAG, "getMagnetForEpisode: result=${magnet != null} ${if (magnet != null) "URI=${magnet.take(80)}..." else ""}")
    return magnet
}

suspend fun MainViewModel.fetchMagnetForEpisode(anime: AnimeMedia, episode: Int): String? {
    Log.d(MainViewModel.TAG, "fetchMagnetForEpisode: anime=${anime.id} ep=$episode engTitle='${anime.titleEnglish}' rawTitle='${anime.title}'")
    ensureMagnetClient()
    val authority = defaultMagnetExtension.value
        ?: _availableMagnetExtensions.value.firstOrNull()?.second
    if (authority.isNullOrBlank()) {
        Log.w(MainViewModel.TAG, "fetchMagnetForEpisode: no magnet extension authority " +
                "(default=${defaultMagnetExtension.value}, detected=${_availableMagnetExtensions.value.size})")
        return null
    }
    Log.d(MainViewModel.TAG, "fetchMagnetForEpisode: using authority=$authority")
    val data = withContext(Dispatchers.IO) {
        fetchMagnetEpisodesSync(anime, authority)
    }
    if (data != null && data.isSingleTorrent) {
        val magnet = data.episodes.firstOrNull()?.magnet
        Log.d(MainViewModel.TAG, "fetchMagnetForEpisode: single-torrent result=${magnet != null}")
        return magnet
    }
    val magnet = getMagnetForEpisode(anime.id, episode)
    Log.d(MainViewModel.TAG, "fetchMagnetForEpisode: final result=${magnet != null}")
    return magnet
}

suspend fun MainViewModel.fetchStreamUrlForEpisode(anime: AnimeMedia, episode: Int, lang: String): StreamUrlResult? {
    val engName = anime.titleEnglish ?: ""
    val romajiName = anime.title ?: ""
    Log.d(MainViewModel.TAG, "fetchStreamUrlForEpisode: anime=${anime.id} ep=$episode lang=$lang eng='$engName' romaji='$romajiName'")
    ensureMagnetClient()
    val authority = defaultMagnetExtension.value
        ?: _availableMagnetExtensions.value.firstOrNull()?.second
    if (authority.isNullOrBlank()) {
        Log.w(MainViewModel.TAG, "fetchStreamUrlForEpisode: no magnet extension authority")
        return null
    }
    return withContext(Dispatchers.IO) {
        magnetExtensionClient?.fetchStreamUrl(authority, anime.id, episode, lang, engName, romajiName)
    }
}
