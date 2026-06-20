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
            if (anime != null && source is AnimeHttpSource) {
                source.prepareNewEpisode(episode, anime)
            }
            try {
                source.getHosterList(episode)
            } catch (_: Throwable) {
                try {
                    val videos = source.getVideoList(episode)
                    if (videos.isNotEmpty()) {
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
                } catch (_: Throwable) {}
                null
            }
        }
    }

    suspend fun getVideosFromHoster(source: AnimeCatalogueSource, hoster: Hoster): List<Video> {
        return withContext(Dispatchers.IO) {
            if (hoster.lazy) {
                source.getVideoList(hoster)
            } else {
                hoster.videoList ?: source.getVideoList(hoster)
            }
        }
    }

    suspend fun getVideosDirect(source: AnimeCatalogueSource, episode: SEpisode, anime: SAnime? = null): List<Video> {
        return withContext(Dispatchers.IO) {
            if (anime != null && source is AnimeHttpSource) {
                source.prepareNewEpisode(episode, anime)
            }
            try {
                source.getVideoList(episode)
            } catch (_: Throwable) {
                emptyList()
            }
        }
    }

}


