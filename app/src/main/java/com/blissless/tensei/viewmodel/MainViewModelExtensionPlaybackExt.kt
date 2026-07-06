package com.blissless.tensei.viewmodel

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.blissless.tensei.MainViewModel
import com.blissless.tensei.data.models.AnimeMedia
import com.blissless.tensei.data.models.CachedExtensionStream
import com.blissless.tensei.data.models.CachedHoster
import com.blissless.tensei.data.models.CachedTrack
import com.blissless.tensei.data.models.CachedVideo
import com.blissless.tensei.stream.LocalProxyServer
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Track
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.OkHttpClient
import com.blissless.tensei.util.ErrorHandler

/**
 * Extension playback logic for [MainViewModel].
 *
 * Extracted from MainViewModel.kt. These two methods are the largest single
 * chunk of the god-object — together they orchestrate:
 *   - Resolving an anime to a source SAnime via fuzzy title matching
 *   - Fetching the episode list and matching the requested episode number
 *   - Walking the hoster list / direct video list of an extension source
 *   - Picking the best video (prefer dub/sub per user setting, then by resolution)
 *   - Resolving proxy URLs through LocalProxyServer
 *   - Caching the resolved stream for instant replay
 *
 * Public signatures preserved as extension functions.
 */

suspend fun MainViewModel.playEpisodeWithExtension(
    anime: AnimeMedia,
    episodeNumber: Int,
    defaultPackage: String,
): MainViewModel.ExtensionStreamResult? {
    val epTag = "AnimeDownload"
    val cached = getCachedExtensionStream(anime.id, episodeNumber)
    if (cached != null) {
        Log.i(epTag, "playEpisodeWithExtension: cache hit for ep $episodeNumber url=${cached.url.take(100)}")
        val isOurPort = "127.0.0.1:${LocalProxyServer.PROXY_PORT}"
        val cachedIsOurProxy = cached.url.contains(isOurPort) || cached.url.contains("localhost:${LocalProxyServer.PROXY_PORT}")
        val staleProxy = !cachedIsOurProxy && (cached.url.contains("127.0.0.1:") || cached.url.contains("localhost:"))
        if (staleProxy) {
            Log.w(epTag, "playEpisodeWithExtension: stale proxy cache for ep $episodeNumber, refetching")
            invalidateExtensionStreamCache(anime.id, episodeNumber)
        } else {
            val cacheSource = withContext(Dispatchers.IO) {
                val smForCache = sourceManager
                if (smForCache != null) {
                    if (smForCache.getSources().isEmpty()) { smForCache.loadSources() }
                    smForCache.getSources().find { it.extension.packageName == defaultPackage }?.source
                } else null
            }
            val cacheSourceHttp = cacheSource as? AnimeHttpSource
            val cacheClient = (cacheSourceHttp?.client) ?: try { NetworkHelper.getInstance().client } catch (e: Exception) { ErrorHandler.report(MainViewModel.TAG, "operation failed, returning null", e); null }
            if (cachedIsOurProxy) {
                LocalProxyServer.start(cacheClient, cacheSource)
                val portFix = Regex("127\\.0\\.0\\.1:\\d+")
                val ourPort = "127.0.0.1:${LocalProxyServer.PROXY_PORT}"
                cached.videos.forEach { cv ->
                    val headers = cv.headers?.let { map ->
                        Headers.Builder().apply { map.forEach { (k, v) -> add(k, v) } }.build()
                    }
                    LocalProxyServer.registerVideo(
                        Video(
                            videoUrl = cv.videoUrl.replace(portFix, ourPort),
                            videoTitle = cv.videoTitle,
                            resolution = cv.resolution,
                            headers = headers,
                            subtitleTracks = cv.subtitleTracks.map { Track(it.url.replace(portFix, ourPort), it.lang) },
                            audioTracks = cv.audioTracks.map { Track(it.url.replace(portFix, ourPort), it.lang) },
                        )
                    )
                    Log.d(epTag, "  cache registered video: ${cv.videoUrl.take(80)}")
                }
            }
            val cacheHeaders = if (cached.videoHeaders.isEmpty() && cachedIsOurProxy && cacheSourceHttp != null) {
                cacheSourceHttp.headers?.let { h ->
                    (0 until h.size).associate { h.name(it) to h.value(it) }
                } ?: cached.videoHeaders
            } else {
                cached.videoHeaders
            }
            val fixedUrl = if (cachedIsOurProxy) {
                cached.url.replace(Regex("127\\.0\\.0\\.1:\\d+"), "127.0.0.1:${LocalProxyServer.PROXY_PORT}")
            } else cached.url
            return MainViewModel.ExtensionStreamResult(
                url = fixedUrl,
                referer = cached.referer.ifEmpty {
                    cacheHeaders.entries.firstOrNull { it.key.equals("Referer", ignoreCase = true) }?.value ?: ""
                },
                subtitleUrl = if (cachedIsOurProxy) cached.subtitleUrl?.replace(Regex("127\\.0\\.0\\.1:\\d+"), "127.0.0.1:${LocalProxyServer.PROXY_PORT}") else cached.subtitleUrl,
                subtitleTrackList = cached.subtitleTracks.map { Track(it.url, it.lang) },
                videoTitle = cached.videoTitle,
                videos = cached.videos.map { v ->
                    val headers = v.headers?.let { map ->
                        Headers.Builder().apply {
                            map.forEach { (k, v) -> add(k, v) }
                        }.build()
                    }
                    Video(
                        videoUrl = if (cachedIsOurProxy) v.videoUrl.replace(Regex("127\\.0\\.0\\.1:\\d+"), "127.0.0.1:${LocalProxyServer.PROXY_PORT}") else v.videoUrl,
                        videoTitle = v.videoTitle,
                        resolution = v.resolution,
                        headers = headers,
                        subtitleTracks = v.subtitleTracks.map { Track(it.url, it.lang) },
                        audioTracks = v.audioTracks.map { Track(it.url, it.lang) },
                    )
                },
                hosters = cached.hosters?.map { Hoster(hosterUrl = it.hosterUrl, hosterName = it.hosterName) },
                extensionClient = if (cachedIsOurProxy) cacheClient else null,
                videoHeaders = cacheHeaders,
                source = if (cachedIsOurProxy) cacheSource else null,
                episode = null,
            )
        }
    }
    Log.i(epTag, "playEpisodeWithExtension: anime=${anime.id} ep=$episodeNumber pkg=$defaultPackage")
    return withContext(Dispatchers.IO) {
        try {
            val sm = sourceManager
            if (sm == null) {
                Log.w(epTag, "playEpisodeWithExtension: sourceManager is null")
                viewModelScope.launch { _toastMessage.emit("Download failed: Source manager not initialized") }
                return@withContext null
            }

            Log.d(epTag, "playEpisodeWithExtension: loading sources")
            sm.loadSources()
            val allSources = sm.getSources()
            var sourceWithExt = allSources.find { it.extension.packageName == defaultPackage }
            if (sourceWithExt == null) {
                Log.w(epTag, "playEpisodeWithExtension: source $defaultPackage not found, reloading")
                sm.reloadSources()
                sm.loadSources()
                val reloaded = sm.getSources()
                sourceWithExt = reloaded.find { it.extension.packageName == defaultPackage }
                if (sourceWithExt == null) {
                    Log.e(epTag, "playEpisodeWithExtension: source $defaultPackage not found even after reload")
                    viewModelScope.launch { _toastMessage.emit("Download failed: Extension '$defaultPackage' not found") }
                    return@withContext null
                }
            }
            val sw = sourceWithExt
            val source = sw.source
            Log.i(epTag, "playEpisodeWithExtension: using source ${sw.source.name} for ep $episodeNumber")

            val sourceHttp = source as? AnimeHttpSource
            val directClient = sourceHttp?.client
            val extensionClient = directClient ?: run {
                Log.w(epTag, "  AnimeHttpSource.client was null, falling back to NetworkHelper.getInstance().client")
                try { NetworkHelper.getInstance().client } catch (e: Exception) { ErrorHandler.report(MainViewModel.TAG, "operation failed, returning null", e); null }
            }
            Log.d(epTag, "  extensionClient=${extensionClient != null} (source is AnimeHttpSource=${sourceHttp != null} directClient=${directClient != null})")

            var matchedSAnime: SAnime? = null
            var sEpisodes: List<SEpisode> = emptyList()

            val preFetched = getPreFetchedExtensionData(anime.id)
            if (preFetched != null && preFetched.source == source) {
                Log.i(epTag, "playEpisodeWithExtension: using pre-fetched data for ep $episodeNumber")
                matchedSAnime = preFetched.sAnime
                sEpisodes = preFetched.episodes
            }

            if (matchedSAnime == null) {
                val searchTerms = listOfNotNull(anime.titleEnglish, anime.title).distinct()
                Log.d(epTag, "playEpisodeWithExtension: searching for anime with terms: $searchTerms")
                for (query in searchTerms) {
                    try {
                        val page = source.getSearchAnime(1, query, AnimeFilterList())
                        val results = page.animes
                        Log.d(epTag, "${sw.source.name}: got ${results.size} results for \"$query\"")
                        if (results.isEmpty()) continue
                        val normalizedQuery = query.lowercase()
                            .replace(Regex("[-–—_:;]"), " ")
                            .replace(Regex("['’´`]"), "'")
                            .replace(Regex("\\s+"), " ")
                            .trim()
                        val queryWords = normalizedQuery.split(" ").filter { it.length > 1 }
                        val scored = results.map { a ->
                            val title = a.title
                            val normalizedTitle = title.lowercase()
                                .replace(Regex("[-–—_:;]"), " ")
                                .replace(Regex("['’´`]"), "'")
                                .replace(Regex("\\s+"), " ")
                                .trim()
                            val titleWords = normalizedTitle.split(" ").filter { it.length > 1 }

                            val score = when {
                                normalizedTitle == normalizedQuery -> 1000
                                normalizedTitle.contains(normalizedQuery) -> 600 + normalizedTitle.length / 10
                                normalizedQuery.contains(normalizedTitle) -> {
                                    val lengthPenalty = (normalizedQuery.length - normalizedTitle.length)
                                    maxOf(50, 400 - lengthPenalty)
                                }
                                else -> {
                                    val matchingWords = queryWords.count { w -> titleWords.any { it == w || it.startsWith(w) || w.startsWith(it) } }
                                    val wordScore = if (queryWords.isNotEmpty()) (matchingWords * 200) / queryWords.size else 0
                                    wordScore + (matchingWords * 10)
                                }
                            }
                            Log.d(epTag, "  \"${a.title}\" -> score=$score")
                            a to score
                        }
                        val best = scored.maxByOrNull { it.second }
                        matchedSAnime = best?.first
                        if (matchedSAnime != null && (best?.second ?: 0) >= 300) break
                    } catch (e: Exception) {
                        Log.w("ExtensionSearch", "Search failed for ${sw.source.name}: ${e.message}")
                    }
                }

                if (matchedSAnime == null) {
                    Log.w(epTag, "playEpisodeWithExtension: no matching anime found for ep $episodeNumber after searching all terms")
                    viewModelScope.launch { _toastMessage.emit("Download failed for Ep $episodeNumber: Could not find '${anime.title}' in extension") }
                    return@withContext null
                }
                Log.i(epTag, "playEpisodeWithExtension: matched anime '${matchedSAnime.title}' (url=${matchedSAnime.url}) for ep $episodeNumber")

                sEpisodes = sm.getEpisodes(source, matchedSAnime)
                Log.d(epTag, "playEpisodeWithExtension: got ${sEpisodes.size} episodes from source")

                if (sEpisodes.isEmpty()) {
                    Log.d(epTag, "playEpisodeWithExtension: no episodes, fetching anime details")
                    try {
                        matchedSAnime = sm.getAnimeDetails(source, matchedSAnime)
                    } catch (e: Exception) {
                        Log.w(epTag, "playEpisodeWithExtension: getAnimeDetails failed", e)
                    }
                    sEpisodes = sm.getEpisodes(source, matchedSAnime)
                    Log.d(epTag, "playEpisodeWithExtension: after details, got ${sEpisodes.size} episodes")
                }
            }

            val sEpisode = if (sEpisodes.isEmpty()) {
                Log.w(epTag, "playEpisodeWithExtension: no episodes from source, creating fallback episode")
                SEpisode.create().apply {
                    url = matchedSAnime.url
                    name = matchedSAnime.title
                    episode_number = 1.0f
                }
            } else {
                val matched = sEpisodes.find { it.episode_number.toInt() == episodeNumber }
                    ?: sEpisodes.firstOrNull { it.name.contains("Episode $episodeNumber", ignoreCase = true) }
                    ?: sEpisodes.firstOrNull { it.name.contains("$episodeNumber", ignoreCase = true) }
                    ?: sEpisodes.getOrNull(episodeNumber - 1)
                if (matched == null) {
                    Log.w(epTag, "playEpisodeWithExtension: episode $episodeNumber not found among ${sEpisodes.size} episodes")
                    viewModelScope.launch { _toastMessage.emit("Download failed for Ep $episodeNumber: Episode not found in extension") }
                    return@withContext null
                }
                matched
            }

            if (source is AnimeHttpSource) {
                source.prepareNewEpisode(sEpisode, matchedSAnime)
            }

            data class VideoWithHoster(val video: Video, val hosterName: String)
            val allVideos = mutableListOf<VideoWithHoster>()
            var resolvedHosters: List<Hoster>? = null

            // Start proxy server in case videos return localhost URLs
            LocalProxyServer.start(extensionClient, source)

            val hosters = try {
                source.getHosterList(sEpisode)
            } catch (_: Throwable) {
                null
            }

            if (!hosters.isNullOrEmpty()) {
                resolvedHosters = hosters
                for (hoster in hosters) {
                    val hosterVideos = try {
                        if (hoster.lazy) source.getVideoList(hoster) else hoster.videoList ?: source.getVideoList(hoster)
                    } catch (_: Throwable) {
                        emptyList()
                    }
                    hosterVideos.forEach {
                        allVideos.add(VideoWithHoster(it, hoster.hosterName))
                        LocalProxyServer.registerVideo(it)
                    }
                }
            } else {
                val directVideos = try { source.getVideoList(sEpisode) } catch (_: Throwable) { emptyList() }
                directVideos.forEach {
                    allVideos.add(VideoWithHoster(it, ""))
                    LocalProxyServer.registerVideo(it)
                }
            }

            if (allVideos.isEmpty()) {
                Log.w(epTag, "playEpisodeWithExtension: no videos found for ep $episodeNumber")
                viewModelScope.launch { _toastMessage.emit("Download failed for Ep $episodeNumber: No video sources found") }
                return@withContext null
            }
            Log.d(epTag, "playEpisodeWithExtension: found ${allVideos.size} videos for ep $episodeNumber")
            allVideos.forEach { v ->
                Log.d(epTag, "  video: ${v.video.videoTitle} (${v.video.resolution}p) url=${v.video.videoUrl.take(100)} hoster=${v.hosterName} internalData=${v.video.internalData.take(60)} mpvArgs=${v.video.mpvArgs}")
            }

            val dubVideos = allVideos.filter {
                it.hosterName.contains("dub", ignoreCase = true) || it.video.videoTitle.contains("dub", ignoreCase = true)
            }
            val subVideos = allVideos.filter { v ->
                !v.hosterName.contains("dub", ignoreCase = true) && !v.video.videoTitle.contains("dub", ignoreCase = true) &&
                (v.hosterName.contains("sub", ignoreCase = true) || v.video.videoTitle.contains("sub", ignoreCase = true))
            }

            val preferDub = preferredCategory.value == "dub"
            val preferSub = preferredCategory.value == "sub"
            val candidates = when {
                preferDub && dubVideos.isNotEmpty() -> dubVideos
                preferSub && subVideos.isNotEmpty() -> subVideos
                dubVideos.isNotEmpty() -> dubVideos
                subVideos.isNotEmpty() -> subVideos
                else -> allVideos
            }

            val bestVideo = candidates.maxByOrNull {
                val res = it.video.resolution ?: 0
                if (res == 0) it.video.videoTitle.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 else res
            }?.video ?: allVideos.last().video
            Log.i(epTag, "playEpisodeWithExtension: selected video '${bestVideo.videoTitle}' (${bestVideo.resolution}p) url=${bestVideo.videoUrl.take(120)}")

            val isOurProxy = bestVideo.videoUrl.contains("127.0.0.1:${LocalProxyServer.PROXY_PORT}") || bestVideo.videoUrl.contains("localhost:${LocalProxyServer.PROXY_PORT}")
            var effectiveVideoUrl = bestVideo.videoUrl
            // Try to resolve the video URL via getVideoUrl or resolveVideo
            if (isOurProxy) {
                Log.d(epTag, "  our proxy URL detected, trying getVideoUrl...")
                try {
                    if (source is AnimeHttpSource) {
                        val result = source.getVideoUrl(bestVideo)
                        Log.d(epTag, "  getVideoUrl returned: ${result.take(120)}")
                        if (!result.contains("127.0.0.1") && !result.contains("localhost") && result.isNotBlank()) {
                            effectiveVideoUrl = result
                        }
                    }
                } catch (e: Exception) {
                    Log.d(epTag, "  getVideoUrl failed: ${e.message}")
                    try {
                        if (source is AnimeHttpSource) {
                            Log.d(epTag, "  trying resolveVideo...")
                            val resolved = source.resolveVideo(bestVideo)
                            val rUrl = resolved?.videoUrl
                            Log.d(epTag, "  resolveVideo returned: ${rUrl?.take(120)}")
                            if (rUrl != null && !rUrl.contains("127.0.0.1") && !rUrl.contains("localhost")) {
                                effectiveVideoUrl = rUrl
                            }
                        }
                    } catch (e2: Exception) {
                        Log.d(epTag, "  resolveVideo also failed: ${e2.message}")
                    }
                }
            }
            // Rewrite non-41223 localhost URLs to go through our multi-threaded proxy
            if (!effectiveVideoUrl.contains("127.0.0.1:${LocalProxyServer.PROXY_PORT}") &&
                (effectiveVideoUrl.contains("127.0.0.1") || effectiveVideoUrl.contains("localhost"))) {
                effectiveVideoUrl = effectiveVideoUrl
                    .replace(Regex("127\\.0\\.0\\.1:\\d+"), "127.0.0.1:${LocalProxyServer.PROXY_PORT}")
                    .replace(Regex("localhost:\\d+"), "127.0.0.1:${LocalProxyServer.PROXY_PORT}")
                Log.d(epTag, "  rewrote to our proxy: ${effectiveVideoUrl.take(120)}")
            }
            Log.d(epTag, "  effectiveVideoUrl=${effectiveVideoUrl.take(120)} (isOurProxy=${isOurProxy})")

            val videoHeadersRaw = bestVideo.headers
            val sourceHeadersRaw = (source as? AnimeHttpSource)?.headers
            val referer = videoHeadersRaw?.let { h ->
                (0 until h.size).firstOrNull { h.name(it).equals("Referer", ignoreCase = true) }
                    ?.let { h.value(it) }
            } ?: sourceHeadersRaw?.let { h ->
                (0 until h.size).firstOrNull { h.name(it).equals("Referer", ignoreCase = true) }
                    ?.let { h.value(it) }
            } ?: ""
            val videoHeaders = if (videoHeadersRaw != null) {
                (0 until videoHeadersRaw.size).associate { videoHeadersRaw.name(it) to videoHeadersRaw.value(it) }
            } else if (sourceHeadersRaw != null) {
                (0 until sourceHeadersRaw.size).associate { sourceHeadersRaw.name(it) to sourceHeadersRaw.value(it) }
            } else {
                emptyMap()
            }
            Log.d(epTag, "  referer=${referer.take(60)} videoHeaders=${videoHeaders} hasVideoHeaders=${videoHeadersRaw != null}")
            Log.d(epTag, "  subtitle tracks: ${bestVideo.subtitleTracks.size}, audio tracks: ${bestVideo.audioTracks.size}")

            val bestVideoHost = allVideos.find { it.video.videoUrl == bestVideo.videoUrl }
            val derivedHosters = if (resolvedHosters != null) {
                val selectedName = bestVideoHost?.hosterName
                val reordered = resolvedHosters.toMutableList()
                val idx = selectedName?.let { n -> reordered.indexOfFirst { it.hosterName == n } } ?: -1
                if (idx > 0) {
                    val item = reordered.removeAt(idx)
                    reordered.add(0, item)
                }
                reordered
            } else {
                val hosterForSelected = bestVideoHost?.let {
                    Hoster(hosterUrl = it.video.videoUrl, hosterName = it.video.videoTitle.take(50), videoList = listOf(it.video), lazy = false)
                }
                val rest = allVideos.filter { it.video.videoUrl != bestVideo.videoUrl }.map {
                    Hoster(hosterUrl = it.video.videoUrl, hosterName = it.video.videoTitle.take(50), videoList = listOf(it.video), lazy = false)
                }.distinctBy { it.hosterName }
                listOfNotNull(hosterForSelected) + rest
            }

            val videos = allVideos.map { it.video }

            val preferredLang = defaultSubtitleLang.value
            val sortedSubs = bestVideo.subtitleTracks.sortedByDescending { t ->
                when {
                    t.lang.equals(preferredLang, ignoreCase = true) -> 2
                    t.lang.equals("English", ignoreCase = true) -> 1
                    else -> 0
                }
            }

            cacheExtensionStream(anime.id, episodeNumber, CachedExtensionStream(
                url = effectiveVideoUrl,
                referer = referer,
                subtitleUrl = sortedSubs.firstOrNull()?.url,
                subtitleTracks = sortedSubs.map { CachedTrack(it.url, it.lang) },
                videoTitle = bestVideo.videoTitle,
                videos = videos.map { v ->
                    val headersMap = v.headers?.let { h ->
                        (0 until h.size).associate { h.name(it) to h.value(it) }
                    }
                    CachedVideo(
                        videoUrl = v.videoUrl,
                        videoTitle = v.videoTitle,
                        resolution = v.resolution,
                        headers = headersMap,
                        subtitleTracks = v.subtitleTracks.map { CachedTrack(it.url, it.lang) },
                        audioTracks = v.audioTracks.map { CachedTrack(it.url, it.lang) },
                    )
                },
                hosters = derivedHosters.map { CachedHoster(hosterUrl = it.hosterUrl, hosterName = it.hosterName) },
                videoHeaders = videoHeaders,
                cachedAt = System.currentTimeMillis(),
            ))
            val resultIsOurProxy = effectiveVideoUrl.contains("127.0.0.1:${LocalProxyServer.PROXY_PORT}") || effectiveVideoUrl.contains("localhost:${LocalProxyServer.PROXY_PORT}")
            val resultClient = if (resultIsOurProxy) {
                extensionClient  // our proxy URL needs the client for forwarding
            } else {
                null  // direct or other proxy URL: use DefaultHttpDataSource
            }
            MainViewModel.ExtensionStreamResult(
                url = effectiveVideoUrl,
                referer = referer,
                subtitleUrl = sortedSubs.firstOrNull()?.url,
                subtitleTrackList = sortedSubs,
                videoTitle = bestVideo.videoTitle,
                videos = videos,
                hosters = derivedHosters,
                extensionClient = resultClient,
                videoHeaders = videoHeaders,
                source = source,
                episode = sEpisode,
            )
        } catch (e: Exception) {
            Log.e(epTag, "playEpisodeWithExtension: exception for ep $episodeNumber", e)
            viewModelScope.launch { _toastMessage.emit("Download failed for Ep $episodeNumber: ${e.message}") }
            null
        }
    }
}

suspend fun MainViewModel.fetchExtensionHosterVideos(
    source: AnimeCatalogueSource,
    hoster: Hoster,
): MainViewModel.ExtensionStreamResult? {
    val epTag = "AnimeDownload"
    return withContext(Dispatchers.IO) {
        try {
            val videos = withContext(Dispatchers.IO) {
                val result = if (hoster.lazy) {
                    try { source.getVideoList(hoster) } catch (_: Throwable) { emptyList() }
                } else {
                    hoster.videoList ?: try { source.getVideoList(hoster) } catch (_: Throwable) { emptyList() }
                }
                result
            }
            if (videos.isEmpty()) return@withContext null
            videos.forEach { LocalProxyServer.registerVideo(it) }

            val bestVideo = videos.maxByOrNull {
                val res = it.resolution ?: 0
                if (res == 0) it.videoTitle.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 else res
            } ?: videos.last()

            var effectiveVideoUrl = bestVideo.videoUrl
            if (effectiveVideoUrl.contains("127.0.0.1") || effectiveVideoUrl.contains("localhost")) {
                Log.d(epTag, "fetchExtensionHosterVideos: proxy URL detected, trying getVideoUrl...")
                try {
                    if (source is AnimeHttpSource) {
                        val result = source.getVideoUrl(bestVideo)
                        Log.d(epTag, "  getVideoUrl returned: ${result.take(120)}")
                        if (!result.contains("127.0.0.1") && !result.contains("localhost") && result.isNotBlank()) {
                            effectiveVideoUrl = result
                        }
                    }
                } catch (e: Exception) {
                    Log.d(epTag, "  getVideoUrl failed: ${e.message}")
                    try {
                        if (source is AnimeHttpSource) {
                            val resolved = source.resolveVideo(bestVideo)
                            val rUrl = resolved?.videoUrl
                            Log.d(epTag, "  resolveVideo returned: ${rUrl?.take(120)}")
                            if (rUrl != null && !rUrl.contains("127.0.0.1") && !rUrl.contains("localhost")) {
                                effectiveVideoUrl = rUrl
                            }
                        }
                    } catch (e2: Exception) {
                        Log.d(epTag, "  resolveVideo also failed: ${e2.message}")
                    }
                }
            }

            // Rewrite non-41223 localhost URLs to go through our multi-threaded proxy
            if (!effectiveVideoUrl.contains("127.0.0.1:${LocalProxyServer.PROXY_PORT}") &&
                (effectiveVideoUrl.contains("127.0.0.1") || effectiveVideoUrl.contains("localhost"))) {
                effectiveVideoUrl = effectiveVideoUrl
                    .replace(Regex("127\\.0\\.0\\.1:\\d+"), "127.0.0.1:${LocalProxyServer.PROXY_PORT}")
                    .replace(Regex("localhost:\\d+"), "127.0.0.1:${LocalProxyServer.PROXY_PORT}")
                Log.d(epTag, "fetchExtensionHosterVideos: rewrote to our proxy: ${effectiveVideoUrl.take(120)}")
            }

            val videoHeadersRaw = bestVideo.headers
            val sourceHeadersRaw = (source as? AnimeHttpSource)?.headers
            val referer = videoHeadersRaw?.let { h ->
                (0 until h.size).firstOrNull { h.name(it).equals("Referer", ignoreCase = true) }
                    ?.let { h.value(it) }
            } ?: sourceHeadersRaw?.let { h ->
                (0 until h.size).firstOrNull { h.name(it).equals("Referer", ignoreCase = true) }
                    ?.let { h.value(it) }
            } ?: ""
            val videoHeaders = if (videoHeadersRaw != null) {
                (0 until videoHeadersRaw.size).associate { videoHeadersRaw.name(it) to videoHeadersRaw.value(it) }
            } else if (sourceHeadersRaw != null) {
                (0 until sourceHeadersRaw.size).associate { sourceHeadersRaw.name(it) to sourceHeadersRaw.value(it) }
            } else {
                emptyMap()
            }

            val sourceHttp = source as? AnimeHttpSource
            val directClient = sourceHttp?.client
            val extensionClient = directClient ?: run {
                try { NetworkHelper.getInstance().client } catch (e: Exception) { ErrorHandler.report(MainViewModel.TAG, "operation failed, returning null", e); null }
            }
            val isOurProxy = effectiveVideoUrl.contains("127.0.0.1:${LocalProxyServer.PROXY_PORT}") || effectiveVideoUrl.contains("localhost:${LocalProxyServer.PROXY_PORT}")
            val resultClient = if (isOurProxy) extensionClient else null

            val preferredLang = defaultSubtitleLang.value
            val sortedSubs = bestVideo.subtitleTracks.sortedByDescending { t ->
                when {
                    t.lang.equals(preferredLang, ignoreCase = true) -> 2
                    t.lang.equals("English", ignoreCase = true) -> 1
                    else -> 0
                }
            }

            MainViewModel.ExtensionStreamResult(
                url = effectiveVideoUrl,
                referer = referer,
                subtitleUrl = sortedSubs.firstOrNull()?.url,
                subtitleTrackList = sortedSubs,
                videoTitle = bestVideo.videoTitle,
                videos = videos,
                hosters = listOf(hoster),
                extensionClient = resultClient,
                videoHeaders = videoHeaders,
                source = source,
            )
        } catch (_: Exception) {
            null
        }
    }
}
