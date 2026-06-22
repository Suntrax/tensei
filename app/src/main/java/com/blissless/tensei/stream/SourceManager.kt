package com.blissless.tensei.stream

import android.content.Context
import android.util.Log
import com.blissless.tensei.extensions.Extension
import com.blissless.tensei.extensions.ExtensionDetector
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SourceManager(context: Context) {
    private val detector = ExtensionDetector(context)
    private val loader = ExtensionLoader(context)
    @Volatile
    private var sources: List<SourceWithExt> = emptyList()

    data class SourceWithExt(
        val source: AnimeCatalogueSource,
        val extension: Extension,
    )

    fun getSources(): List<SourceWithExt> = sources

    fun reloadSources() {
        sources = emptyList()
    }

    suspend fun loadSources() {
        withContext(Dispatchers.IO) {
            val extensions = detector.detectInstalledExtensions()
            val loaded = extensions.flatMap { ext ->
                try {
                    val loaded = loader.loadSources(ext)
                    loaded.map { SourceWithExt(it, ext) }
                } catch (e: Exception) {
                    Log.w("SourceManager", "Failed to load extension: ${ext.packageName}", e)
                    emptyList()
                }
            }
            sources = loaded
        }
    }

    suspend fun search(
        query: String,
        sourceFilter: SourceWithExt? = null,
        onProgress: (SourceWithExt, List<SAnime>) -> Unit,
    ) {
        Log.w("ExtensionSearch", "search() called: query=\"$query\", sourceFilter=${sourceFilter?.source?.name}")
        withContext(Dispatchers.IO) {
            val targets = if (sourceFilter != null) listOf(sourceFilter) else sources
            Log.w("ExtensionSearch", "search() targets: ${targets.size} sources")
            for (sw in targets) {
                try {
                    val filters = sw.source.getFilterList()
                    val page = sw.source.getSearchAnime(1, query, filters)
                    Log.w("ExtensionSearch", "${sw.source.name} (${sw.extension.name}): returned ${page.animes.size} results for \"$query\"")
                    if (page.animes.isNotEmpty()) {
                        onProgress(sw, page.animes)
                        page.animes.forEach { anime ->
                            Log.i("ExtensionSearch", "  -> [${sw.source.name}] ${anime.title} (${anime.url})")
                        }
                    }
                } catch (e: Exception) {
                    Log.w("SourceManager", "Search failed for ${sw.source.name}", e)
                }
            }
        }
    }

    suspend fun getEpisodes(source: AnimeCatalogueSource, anime: SAnime): List<SEpisode> {
        return withContext(Dispatchers.IO) {
            source.getEpisodeList(anime)
        }
    }

    suspend fun getAnimeDetails(source: AnimeCatalogueSource, anime: SAnime): SAnime {
        return withContext(Dispatchers.IO) {
            source.getAnimeDetails(anime)
        }
    }

    suspend fun getHosters(source: AnimeCatalogueSource, episode: SEpisode, anime: SAnime? = null): List<Hoster>? {
        return withContext(Dispatchers.IO) {
            Log.d("SourceManager", "getHosters: source=${source.name} ep=${episode.url.take(80)}")
            if (anime != null && source is AnimeHttpSource) {
                Log.d("SourceManager", "  calling prepareNewEpisode")
                source.prepareNewEpisode(episode, anime)
            }
            try {
                val hosters = source.getHosterList(episode)
                Log.d("SourceManager", "  got ${hosters.size} hosters: ${hosters.map { "${it.hosterName}: ${it.hosterUrl.take(80)} (lazy=${it.lazy})" }}")
                hosters
            } catch (e: Throwable) {
                Log.d("SourceManager", "  getHosterList failed: ${e.message}", e)
                try {
                    val videos = source.getVideoList(episode)
                    Log.d("SourceManager", "  got ${videos.size} videos from fallback getVideoList")
                    if (videos.isNotEmpty()) {
                        videos.forEach { v ->
                            Log.d("SourceManager", "    video: \"${v.videoTitle}\" res=${v.resolution}p url=${v.videoUrl.take(100)} headers=${v.headers?.let { h -> (0 until h.size).associate { h.name(it) to h.value(it) } }}")
                        }
                        val derivedHosters = videos.map { video ->
                            Hoster(
                                hosterUrl = video.videoUrl,
                                hosterName = video.videoTitle.take(50),
                                videoList = listOf(video),
                                lazy = false,
                            )
                        }
                        return@withContext derivedHosters.distinctBy { it.hosterName }
                    }
                } catch (e2: Throwable) {
                    Log.d("SourceManager", "  fallback getVideoList also failed: ${e2.message}")
                }
                null
            }
        }
    }

    suspend fun getVideosFromHoster(source: AnimeCatalogueSource, hoster: Hoster): List<Video> {
        return withContext(Dispatchers.IO) {
            Log.d("SourceManager", "getVideosFromHoster: source=${source.name} hoster=${hoster.hosterName} url=${hoster.hosterUrl.take(80)} lazy=${hoster.lazy}")
            val videos = if (hoster.lazy) {
                source.getVideoList(hoster)
            } else {
                hoster.videoList ?: source.getVideoList(hoster)
            }
            Log.d("SourceManager", "  returned ${videos.size} videos")
            videos.forEach { v ->
                Log.d("SourceManager", "    video: \"${v.videoTitle}\" res=${v.resolution}p url=${v.videoUrl.take(100)}")
            }
            videos
        }
    }

    suspend fun getVideosDirect(source: AnimeCatalogueSource, episode: SEpisode, anime: SAnime? = null): List<Video> {
        return withContext(Dispatchers.IO) {
            Log.d("SourceManager", "getVideosDirect: source=${source.name} ep=${episode.url.take(80)}")
            if (anime != null && source is AnimeHttpSource) {
                Log.d("SourceManager", "  calling prepareNewEpisode")
                source.prepareNewEpisode(episode, anime)
            }
            try {
                val videos = source.getVideoList(episode)
                Log.d("SourceManager", "  got ${videos.size} videos")
                videos.forEach { v ->
                    Log.d("SourceManager", "    video: \"${v.videoTitle}\" res=${v.resolution}p url=${v.videoUrl.take(100)} headers=${v.headers?.let { h -> (0 until h.size).associate { h.name(it) to h.value(it) } }}")
                }
                videos
            } catch (e: Throwable) {
                Log.d("SourceManager", "  getVideoList failed: ${e.message}", e)
                emptyList()
            }
        }
    }

}


