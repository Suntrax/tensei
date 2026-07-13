package com.blissless.tensei.playback

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.blissless.tensei.MainViewModel
import com.blissless.tensei.data.models.EpisodeStreams
import com.blissless.tensei.data.models.QualityOption
import com.blissless.tensei.data.models.ServerInfo
import com.blissless.tensei.torrent.TorrentEngine
import com.blissless.tensei.util.toast
// Extension functions on MainViewModel (defined in com.blissless.tensei.viewmodel)
import com.blissless.tensei.viewmodel.getPlaybackPosition
import com.blissless.tensei.viewmodel.invalidateStreamCache
import com.blissless.tensei.viewmodel.clearAnimeExtensionStreamCaches
import com.blissless.tensei.viewmodel.removeFromVideoCache
import com.blissless.tensei.viewmodel.playEpisodeWithExtension
import com.blissless.tensei.viewmodel.fetchExtensionHosterVideos
import com.blissless.tensei.viewmodel.getMagnetForEpisode
import com.blissless.tensei.viewmodel.fetchMagnetForEpisode
import com.blissless.tensei.viewmodel.fetchStreamUrlForEpisode
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.Track
import eu.kanade.tachiyomi.animesource.model.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import okhttp3.OkHttpClient
import java.io.File

/**
 * Owns all playback-related state and methods that were previously scattered
 * as ~40 remember{} variables inside the MainScreen composable.
 *
 * This class is created once per MainScreen composition and passed to
 * sub-composables. It makes the playback logic testable (no Compose
 * dependencies needed for unit tests) and encapsulates state that
 * was previously exposed as `internal` vars on MainViewModel.
 *
 * Usage in MainScreen:
 *   val playback = remember(viewModel, scope, torrentEngine) {
 *       PlaybackStateHolder(viewModel, scope, torrentEngine)
 *   }
 *
 * State is exposed as Compose observable properties (using mutableStateOf)
 * so the UI recomposes automatically when values change.
 */
class PlaybackStateHolder(
    val viewModel: MainViewModel,
    val scope: kotlinx.coroutines.CoroutineScope,
    val torrentEngine: TorrentEngine,
    val context: android.content.Context,
) {
    // ─── Core playback state ──────────────────────────────────────────────
    var showPlayer by mutableStateOf(false)
    var isAutoRefreshing by mutableStateOf(false)
    var pendingSeekPosition by mutableStateOf<Long?>(null)
    var currentVideoUrl by mutableStateOf<String?>(null)
    var currentReferer by mutableStateOf("https://megacloud.tv/")
    var currentSubtitleUrl by mutableStateOf<String?>(null)
    var currentAnime by mutableStateOf<com.blissless.tensei.data.models.AnimeMedia?>(null)
    var currentEpisode by mutableIntStateOf(0)
    var totalEpisodes by mutableIntStateOf(0)
    var isLoadingStream by mutableStateOf(false)
    var loadingJob by mutableStateOf<Job?>(null)
    var streamError by mutableStateOf<String?>(null)
    var currentServerAttempt by mutableStateOf<String?>(null)
    var currentServerAttemptIsFallback by mutableStateOf(false)

    // ─── Episode info ─────────────────────────────────────────────────────
    var currentEpisodeInfo by mutableStateOf<EpisodeStreams?>(null)
    var currentEpisodeTitle by mutableStateOf<String?>(null)
    var hasPrefetchedNextOnTracking by mutableStateOf(false)

    // ─── Server / quality state ───────────────────────────────────────────
    var currentCategory by mutableStateOf("sub")
    var currentServerName by mutableStateOf("")
    var currentServerIndex by mutableIntStateOf(0)
    var isFallbackStream by mutableStateOf(false)
    var requestedCategory by mutableStateOf("sub")
    var actualCategory by mutableStateOf("sub")
    var isManualServerChange by mutableStateOf(false)
    var isChangingEpisode by mutableStateOf(false)
    var episodeTrigger by mutableIntStateOf(0)
    var currentQualityOptions by mutableStateOf<List<QualityOption>>(emptyList())
    var currentQuality by mutableStateOf("Auto")
    var savedPlaybackPosition by mutableLongStateOf(0L)

    // ─── Extension flow state ─────────────────────────────────────────────
    var extensionVideos by mutableStateOf<List<Video>?>(null)
    var extensionHosters by mutableStateOf<List<Hoster>?>(null)
    var showExtHosterDialog by mutableStateOf(false)
    var showExtVideoDialog by mutableStateOf(false)
    var pendingExtResult by mutableStateOf<MainViewModel.ExtensionStreamResult?>(null)
    var isExtensionFlow by mutableStateOf(false)
    var extensionOkHttpClient by mutableStateOf<OkHttpClient?>(null)
    var extensionVideoHeaders by mutableStateOf<Map<String, String>>(emptyMap())
    var extensionSourcePackage by mutableStateOf("")
    var extensionEpisodeUrl by mutableStateOf("")
    var extensionEpisodeNumber by mutableIntStateOf(0)
    var extensionServers by mutableStateOf(emptyList<ServerInfo>())
    var extensionName by mutableStateOf("")
    var currentSubtitleTracks by mutableStateOf<List<Track>>(emptyList())
    var cachedExtensionNext by mutableStateOf<MainViewModel.ExtensionStreamResult?>(null)
    val episodeCache = mutableMapOf<Int, MainViewModel.ExtensionStreamResult>()

    // ─── Torrent state ────────────────────────────────────────────────────
    var currentTorrentListener by mutableStateOf<TorrentEngine.EngineListener?>(null)

    // ─── Timestamps (Animekai / AniSkip) ─────────────────────────────────
    var animekaiIntroStart by mutableStateOf<Int?>(null)
    var animekaiIntroEnd by mutableStateOf<Int?>(null)
    var animekaiOutroStart by mutableStateOf<Int?>(null)
    var animekaiOutroEnd by mutableStateOf<Int?>(null)

    // ─── UI-level state (shared between holder and composable) ───────────
    var showNoExtDialog by mutableStateOf(false)
    val torrentStreamServer = mutableStateOf<com.blissless.tensei.torrent.TorrentStreamServer?>(null)

    /** Shortcut for viewModel.preferredCategory.value */
    val preferredCategory: String get() = viewModel.preferredCategory.value

    // ─── Methods (to be migrated from MainScreen) ────────────────────────

    /**
     * Strips the "Episode N:" prefix from an episode title.
     * Delegates to the pure function in PlaybackHelpers.
     */
    fun sanitizeEpisodeTitle(title: String?): String? =
        com.blissless.tensei.ui.screens.player.sanitizeEpisodeTitle(title)

    /**
     * Invalidates the cache for the current stream, forcing a re-fetch
     * on next playback. Called on playback errors or manual refresh.
     */
    fun invalidateCurrentStreamCache() {
        currentAnime?.let { anime ->
            viewModel.invalidateStreamCache(anime.id, currentEpisode, currentCategory)
            viewModel.clearAnimeExtensionStreamCaches(anime.id)
            currentVideoUrl?.let { viewModel.removeFromVideoCache(it) }
        }
    }

    /**
     * Called when a playback error occurs. Invalidates the stream cache
     * so the next attempt fetches fresh data.
     */
    fun onPlaybackError() {
        invalidateCurrentStreamCache()
        currentAnime?.let { _ ->
            // Error recovery logic can be added here
        }
    }

    /**
     * Fetches and caches an episode's stream data for instant playback.
     * Skips if already cached. Used to pre-load adjacent episodes.
     */
    fun fetchAndCacheEpisode(ep: Int) {
        if (currentAnime == null) return
        if (viewModel.streamMethod.value == "magnet") return
        val pkg = extensionSourcePackage.ifEmpty { viewModel.defaultExtensionPackage.value }
        if (pkg.isEmpty()) return
        scope.launch {
            if (episodeCache.containsKey(ep)) return@launch
            val result = viewModel.playEpisodeWithExtension(currentAnime!!, ep, pkg)
            if (result != null) {
                episodeCache[ep] = result
            }
        }
    }

    /**
     * Pre-fetches the next episode's stream data so playback starts
     * instantly when the user taps "Next Episode".
     */
    fun prefetchExtensionNextEpisode() {
        if (currentAnime == null) return
        if (viewModel.streamMethod.value == "magnet") return
        scope.launch {
            val nextEp = currentEpisode + 1
            val pkg = extensionSourcePackage.ifEmpty { viewModel.defaultExtensionPackage.value }
            if (pkg.isEmpty()) return@launch
            val result = viewModel.playEpisodeWithExtension(currentAnime!!, nextEp, pkg)
            if (result != null) {
                cachedExtensionNext = result
                episodeCache[nextEp] = result
            }
        }
    }

    /**
     * Fetches the episode title from TMDB cache or API.
     * Falls back to "Episode N" if not found or on error.
     */
    suspend fun getTmdbEpisodeTitle(anime: com.blissless.tensei.data.models.AnimeMedia, episode: Int): String {
        val cachedEpisodes = viewModel.getCachedTmdbEpisodes(anime.id)
        if (cachedEpisodes != null) {
            val title = cachedEpisodes.find { it.episode == episode }?.title
            if (!title.isNullOrEmpty()) return sanitizeEpisodeTitle(title) ?: "Episode $episode"
        }
        return try {
            val tmdbEpisodes = viewModel.fetchTmdbEpisodes(anime.title, anime.id, anime.year, anime.format)
            val title = tmdbEpisodes.find { it.episode == episode }?.title
            sanitizeEpisodeTitle(title) ?: "Episode $episode"
        } catch (_: Exception) {
            "Episode $episode"
        }
    }

    /**
     * Switches to a different hoster/server within the current extension source.
     * Fetches video list for the selected hoster and updates playback state.
     */
    fun handleExtensionServerChange(hosterName: String) {
        val hoster = extensionHosters?.find { it.hosterName == hosterName } ?: return
        val source = com.blissless.tensei.stream.PlayerData.extensionSource
        if (source == null) {
            context.toast("Source not available")
            return
        }
        scope.launch {
            isLoadingStream = true
            val result = viewModel.fetchExtensionHosterVideos(source, hoster)
            if (result != null) {
                currentVideoUrl = result.url
                currentReferer = result.referer
                currentSubtitleUrl = result.subtitleUrl
                currentServerName = hosterName
                currentCategory = if (hosterName.contains("dub", ignoreCase = true) || result.videoTitle.contains("dub", ignoreCase = true)) "dub" else "sub"
                currentQualityOptions = com.blissless.tensei.ui.screens.player.buildQualityOptions(result.videos)
                currentQuality = result.videoTitle
                extensionOkHttpClient = result.extensionClient
                extensionVideoHeaders = result.videoHeaders
                episodeTrigger++
            } else {
                context.toast("Failed to load $hosterName")
            }
            isLoadingStream = false
        }
    }

    /**
     * Callback to start playing an episode. If no title is provided,
     * fetches it from TMDB first.
     */
    val onPlayEpisode: (com.blissless.tensei.data.models.AnimeMedia, Int, String?) -> Unit = { anime, episode, title ->
        if (title == null) {
            isLoadingStream = true
            scope.launch {
                currentEpisodeTitle = getTmdbEpisodeTitle(anime, episode)
                loadAndPlayEpisode(anime, episode)
            }
        } else {
            currentEpisodeTitle = sanitizeEpisodeTitle(title) ?: "Episode $episode"
            loadAndPlayEpisode(anime, episode)
        }
    }

    /**
     * Starts playback from an extension source. Sets up the video URL,
     * subtitle tracks, server list, and quality options.
     */
    fun playExtensionVideo(result: MainViewModel.ExtensionStreamResult, index: Int) {
        result.videos.forEachIndexed { _, _ -> }
        val video = result.videos.find { it.videoUrl == result.url }
            ?: result.videos.getOrNull(index)
            ?: return
        streamError = null
        currentEpisodeTitle = sanitizeEpisodeTitle(result.episode?.name) ?: "Episode $currentEpisode"
        currentVideoUrl = result.url.ifEmpty { video.videoUrl }
        currentReferer = result.referer
        val preferredLang = viewModel.defaultSubtitleLang.value
        val sortedTracks = com.blissless.tensei.ui.screens.player.sortSubtitleTracks(video.subtitleTracks, preferredLang)
        currentSubtitleTracks = sortedTracks
        currentSubtitleUrl = sortedTracks.firstOrNull()?.url
        sortedTracks.forEachIndexed { _, _ -> }
        extensionName = result.source?.name ?: ""
        currentServerName = result.hosters?.firstOrNull()?.hosterName ?: extensionName.ifEmpty { "Extension" }
        val hasDubHoster = result.hosters?.any { it.hosterName.contains("dub", ignoreCase = true) } == true
        currentCategory = if (hasDubHoster || result.videoTitle.contains("dub", ignoreCase = true)) "dub" else "sub"
        actualCategory = currentCategory
        requestedCategory = preferredCategory
        currentQualityOptions = emptyList()
        currentQuality = "Auto"
        currentServerIndex = 0
        isExtensionFlow = false
        extensionOkHttpClient = result.extensionClient
        extensionVideoHeaders = result.videoHeaders
        extensionServers = (result.hosters ?: emptyList()).map { hoster ->
            ServerInfo(name = hoster.hosterName, url = hoster.hosterUrl)
        }
        showPlayer = true
        if (currentCategory == "dub" && result.source != null && result.episode != null) {
            val src = result.source
            val ep = result.episode
            scope.launch {
                val episodeVideos = withContext(Dispatchers.IO) {
                    try { src.getVideoList(ep) } catch (e: Throwable) { com.blissless.tensei.util.ErrorHandler.report("Playback", "getVideoList failed", e); emptyList() }
                }
                val subVideo = episodeVideos.find {
                    it.videoTitle.contains("sub", ignoreCase = true) && !it.videoTitle.contains("dub", ignoreCase = true) && it.subtitleTracks.isNotEmpty()
                } ?: episodeVideos.find {
                    !it.videoTitle.contains("dub", ignoreCase = true) && it.subtitleTracks.isNotEmpty()
                }
                if (subVideo != null) {
                    subVideo.subtitleTracks.forEachIndexed { _, _ -> }
                    currentSubtitleTracks = currentSubtitleTracks + subVideo.subtitleTracks
                    if (currentSubtitleUrl == null) {
                        currentSubtitleUrl = currentSubtitleTracks.firstOrNull()?.url
                    }
                }
            }
        }
    }

    /**
     * Starts torrent playback for a magnet URI. Sets up the torrent engine,
     * stream server, and waits for enough data before handing off to the player.
     */
    fun playTorrent(magnetUri: String, anime: com.blissless.tensei.data.models.AnimeMedia, episode: Int) {
        android.util.Log.i("Playback", "playTorrent: START anime='${anime.title}' ep=$episode magnet=${magnetUri.take(60)}...")
        isLoadingStream = true
        streamError = null
        torrentStreamServer.value?.stop()
        torrentStreamServer.value = null

        val engine = torrentEngine
        android.util.Log.d("Playback", "playTorrent: engine.isRunning=${engine.isRunning.get()}")
        if (!engine.isRunning.get()) {
            android.util.Log.d("Playback", "playTorrent: starting engine")
            engine.start()
        }
        android.util.Log.d("Playback", "playTorrent: removing any previous torrent")
        engine.removeCurrentTorrent()

        val server = com.blissless.tensei.torrent.TorrentStreamServer(engine.saveDir)
        torrentStreamServer.value = server
        android.util.Log.d("Playback", "playTorrent: created TorrentStreamServer (saveDir=${engine.saveDir.absolutePath})")

        currentTorrentListener?.let { engine.removeListener(it) }
        val listener = object : TorrentEngine.EngineListener {
            override fun onMetadataReceived(meta: com.blissless.tensei.torrent.TorrentMeta) {
                android.util.Log.i("Playback", "onMetadataReceived: name='${meta.name}' files=${meta.files.size}")
                scope.launch {
                    try {
                        val videoExts = setOf("mkv", "mp4", "webm", "avi", "mov", "m4v")
                        val videoFiles = meta.files.filter { f ->
                            f.name.substringAfterLast('.', "").lowercase() in videoExts
                        }
                        val epPattern = Regex("(?:^|[._ \\[\\]()-])0*${episode}(?:\$|[._ \\[\\]()-])", RegexOption.IGNORE_CASE)
                        val matched = videoFiles.filter { f ->
                            epPattern.containsMatchIn(f.name) || epPattern.containsMatchIn(f.path)
                        }
                        val fileIndex = if (matched.isNotEmpty()) {
                            android.util.Log.d("Playback", "onMetadataReceived: matched ep $episode -> '${matched.maxBy { it.size }.name}'")
                            matched.maxBy { it.size }.index
                        } else {
                            android.util.Log.w("Playback", "onMetadataReceived: no pattern match for ep $episode, sample files:")
                            videoFiles.take(10).forEach { f ->
                                android.util.Log.w("Playback", "  video file: [${f.index}] '${f.name}'")
                            }
                            android.util.Log.w("Playback", "onMetadataReceived: trying fallback contains match")
                            val fallbackMatched = videoFiles.filter { f ->
                                f.name.contains("$episode") || f.path.contains("$episode")
                            }
                            android.util.Log.d("Playback", "onMetadataReceived: fallback matched ${fallbackMatched.size} files")
                            fallbackMatched.maxByOrNull { it.size }?.index ?: run {
                                android.util.Log.w("Playback", "onMetadataReceived: fallback also failed, using largest")
                                engine.getLargestVideoFileIndex()
                            }
                        }
                        engine.startDownload(fileIndex)
                        val port = server.start()
                        android.util.Log.d("Playback", "onMetadataReceived: stream server started on port $port")
                        val filePath = engine.getFileSavePath(fileIndex)
                        if (filePath == null) {
                            android.util.Log.e("Playback", "onMetadataReceived: getFileSavePath returned null, aborting")
                            streamError = "Could not resolve torrent file path"
                            isLoadingStream = false
                            return@launch
                        }
                        val saveDirPath = engine.saveDir.absolutePath + File.separator
                        val fileName = if (filePath.startsWith(saveDirPath)) filePath.removePrefix(saveDirPath) else filePath.substringAfterLast(File.separator)
                        android.util.Log.d("Playback", "onMetadataReceived: filePath='$filePath' fileName='$fileName'")
                        server.setTotalFileSize(engine.getFileSize(fileIndex))
                        server.setPieceSize(engine.getPieceSize())
                        server.setPieceChecker { i -> engine.havePiece(i) }
                        server.setSafeBytesProvider { engine.getContiguousDownloadedBytes() }

                        val minBytes = 8L * 1024 * 1024
                        android.util.Log.d("Playback", "onMetadataReceived: waiting for ${minBytes / 1024 / 1024}MB contiguous...")
                        val waitDeadline = System.nanoTime() + 120_000_000_000L
                        var waited = false
                        while (System.nanoTime() < waitDeadline) {
                            val contiguous = engine.getContiguousDownloadedBytes()
                            if (contiguous >= minBytes) {
                                android.util.Log.d("Playback", "onMetadataReceived: ${contiguous / 1024 / 1024}MB contiguous — starting playback")
                                break
                            }
                            waited = true
                            delay(500)
                        }
                        if (waited) {
                            val finalContiguous = engine.getContiguousDownloadedBytes()
                            android.util.Log.i("Playback", "onMetadataReceived: waited, contiguous=${finalContiguous / 1024 / 1024}MB")
                        }

                        currentVideoUrl = "http://127.0.0.1:$port/$fileName"
                        currentReferer = ""
                        currentEpisodeTitle = sanitizeEpisodeTitle(anime.title) ?: "Episode $episode"
                        currentSubtitleTracks = emptyList()
                        currentSubtitleUrl = null
                        currentQualityOptions = emptyList()
                        currentQuality = "Auto"
                        currentServerName = "Torrent"
                        currentServerIndex = 0
                        isExtensionFlow = false
                        showPlayer = true
                        isLoadingStream = false
                        android.util.Log.i("Playback", "onMetadataReceived: player URL=$currentVideoUrl — handing off to player")
                    } catch (e: Exception) {
                        android.util.Log.e("Playback", "onMetadataReceived: FAILED", e)
                        streamError = "Failed to start streaming: ${e.message}"
                        isLoadingStream = false
                    }
                }
            }
            override fun onProgress(downloaded: Long, total: Long) {
                if (total > 0) {
                    android.util.Log.v("Playback", "onProgress: $downloaded/$total (${(downloaded * 100 / total)}%)")
                }
            }
            override fun onFinished() {
                android.util.Log.i("Playback", "onFinished: torrent download complete")
            }
            override fun onError(message: String) {
                android.util.Log.e("Playback", "onError: $message")
                scope.launch {
                    streamError = message
                    isLoadingStream = false
                }
            }
        }
        engine.addListener(listener)
        currentTorrentListener = listener

        android.util.Log.d("Playback", "playTorrent: calling engine.addTorrentFromMagnet()")
        engine.addTorrentFromMagnet(magnetUri)
        android.util.Log.d("Playback", "playTorrent: magnet submitted, waiting for metadata...")
    }

    /**
     * Loads and plays an episode. Handles both magnet and direct extension
     * stream methods, delegating to playTorrent or playExtensionVideo.
     */
    fun loadAndPlayEpisode(anime: com.blissless.tensei.data.models.AnimeMedia, episode: Int, isAutoRefresh: Boolean = false) {
        android.util.Log.d("Playback", "loadAndPlayEpisode: anime=${anime.id} ep=$episode autoRefresh=$isAutoRefresh")
        if (!isAutoRefresh) {
            isAutoRefreshing = false
            pendingSeekPosition = null
        }
        currentAnime = anime
        currentEpisode = episode
        totalEpisodes = anime.totalEpisodes
        streamError = null
        savedPlaybackPosition = viewModel.getPlaybackPosition(anime.id, episode)
        if (!isAutoRefresh) {
            showPlayer = false
        }

        val streamMethod = viewModel.streamMethod.value
        android.util.Log.d("Playback", "loadAndPlayEpisode: anime='${anime.title}' ep=$episode streamMethod='$streamMethod'")
        if (streamMethod == "magnet") {
            if (isAutoRefresh && isAutoRefreshing) {
                android.util.Log.d("Playback", "loadAndPlayEpisode: already auto-refreshing, ignoring")
                return
            }
            if (isAutoRefresh) isAutoRefreshing = true
            android.util.Log.i("Playback", "loadAndPlayEpisode: using magnet stream method")
            isExtensionFlow = false
            isLoadingStream = true
            scope.launch {
                yield()
                android.util.Log.d("Playback", "loadAndPlayEpisode: checking cache for magnet (animeId=${anime.id})")
                val cached = viewModel.getMagnetForEpisode(anime.id, episode)
                android.util.Log.d("Playback", "loadAndPlayEpisode: cache lookup result=${cached != null}")
                val magnetUri = withContext(Dispatchers.IO) {
                    cached ?: viewModel.fetchMagnetForEpisode(anime, episode)
                }
                android.util.Log.i("Playback", "loadAndPlayEpisode: fetch result=${magnetUri != null} magnet=${magnetUri?.take(60)}")
                if (magnetUri != null && magnetUri.isNotEmpty()) {
                    android.util.Log.d("Playback", "loadAndPlayEpisode: magnet found, calling playTorrent")
                    playTorrent(magnetUri, anime, episode)
                } else if (magnetUri != null) {
                    android.util.Log.d("Playback", "loadAndPlayEpisode: empty magnet, trying stream URL")
                    val streamResult = viewModel.fetchStreamUrlForEpisode(anime, episode, viewModel.preferredCategory.value)
                    android.util.Log.i("Playback", "loadAndPlayEpisode: streamUrl result=${streamResult != null}")
                    if (streamResult != null) {
                        currentVideoUrl = streamResult.url
                        currentReferer = streamResult.headers["Referer"] ?: ""
                        currentEpisodeTitle = sanitizeEpisodeTitle(anime.title) ?: "Episode $episode"
                        currentSubtitleTracks = streamResult.subtitles
                        currentSubtitleUrl = streamResult.subtitles.firstOrNull { s ->
                            s.lang.contains("english", ignoreCase = true) || s.lang.contains("en", ignoreCase = true)
                        }?.url ?: streamResult.subtitles.firstOrNull()?.url
                        currentQualityOptions = emptyList()
                        currentQuality = "Auto"
                        currentServerName = "Tensei"
                        currentServerIndex = 0
                        extensionVideoHeaders = streamResult.headers
                        extensionOkHttpClient = try { eu.kanade.tachiyomi.network.NetworkHelper.getInstance().trustAllClient } catch (_: Exception) { null }
                        isExtensionFlow = false
                        showPlayer = true
                        isLoadingStream = false
                    } else {
                        streamError = "No stream available for Ep $episode"
                        isLoadingStream = false
                        context.toast("No stream available for Ep $episode")
                    }
                } else {
                    android.util.Log.e("Playback", "loadAndPlayEpisode: no magnet link found for Ep $episode")
                    streamError = "No magnet link found for Ep $episode"
                    isLoadingStream = false
                    context.toast("No magnet available for Ep $episode")
                }
                if (isAutoRefresh) isAutoRefreshing = false
            }
            return
        }

        val extPackage = viewModel.defaultExtensionPackage.value
        if (extPackage.isNotEmpty()) {
            isExtensionFlow = true
            isLoadingStream = true
            extensionVideos = null
            extensionHosters = null
            pendingExtResult = null
            showExtHosterDialog = false
            showExtVideoDialog = false
            scope.launch {
                yield()
                val result = viewModel.playEpisodeWithExtension(anime, episode, extPackage)
                pendingExtResult = result
                if (result != null && result.videos.isNotEmpty()) {
                    extensionVideos = result.videos
                    extensionHosters = result.hosters
                    extensionSourcePackage = extPackage
                    extensionEpisodeNumber = episode
                    extensionEpisodeUrl = result.episode?.url ?: ""
                    com.blissless.tensei.stream.PlayerData.extensionSource = result.source
                    com.blissless.tensei.stream.PlayerData.extensionEpisode = result.episode
                    com.blissless.tensei.stream.PlayerData.allHosters = result.hosters ?: emptyList()
                    playExtensionVideo(result, 0)
                } else {
                    streamError = "Extension stream not found: Ep $episode"
                    context.toast("Extension failed for Ep $episode")
                }
                if (isAutoRefresh) isAutoRefreshing = false
                isLoadingStream = false
            }
            return
        }

        showNoExtDialog = true
    }

    /**
     * Navigate to the previous episode. Uses cache if available,
     * otherwise delegates to loadAndPlayEpisode.
     */
    val onPreviousEpisode: () -> Unit = {
        if (!isChangingEpisode && currentAnime != null && currentEpisode > 1) {
            isChangingEpisode = true
            val prevEp = currentEpisode - 1
            val cached = episodeCache[prevEp]
            if (cached != null) {
                currentEpisode = prevEp
                savedPlaybackPosition = viewModel.getPlaybackPosition(currentAnime!!.id, prevEp)
                currentEpisodeTitle = sanitizeEpisodeTitle(cached.episode?.name) ?: "Episode $prevEp"
                currentVideoUrl = cached.url
                currentReferer = cached.referer
                currentSubtitleUrl = cached.subtitleUrl
                currentSubtitleTracks = cached.videos.firstOrNull()?.subtitleTracks ?: emptyList()
                currentServerName = if (!cached.hosters.isNullOrEmpty()) cached.hosters.first().hosterName else "Extension"
                currentCategory = "sub"
                currentQualityOptions = com.blissless.tensei.ui.screens.player.buildQualityOptions(cached.videos)
                currentQuality = cached.videoTitle
                extensionOkHttpClient = cached.extensionClient
                extensionVideoHeaders = cached.videoHeaders
                extensionHosters = cached.hosters
                extensionServers = com.blissless.tensei.ui.screens.player.buildServerList(cached.hosters)
                episodeTrigger++
                isChangingEpisode = false
                prefetchExtensionNextEpisode()
                fetchAndCacheEpisode(prevEp - 1)
            } else {
                isChangingEpisode = false
                isAutoRefreshing = false
                loadAndPlayEpisode(currentAnime!!, prevEp, isAutoRefresh = true)
            }
        }
    }

    /**
     * Navigate to the next episode. Uses cache if available,
     * otherwise delegates to loadAndPlayEpisode.
     */
    val onNextEpisode: () -> Unit = {
        if (!isChangingEpisode && currentAnime != null) {
            isChangingEpisode = true
            val nextEp = currentEpisode + 1
            val cached = cachedExtensionNext ?: episodeCache[nextEp]
            if (cached != null) {
                cachedExtensionNext = null
                currentEpisode = nextEp
                savedPlaybackPosition = viewModel.getPlaybackPosition(currentAnime!!.id, nextEp)
                currentEpisodeTitle = sanitizeEpisodeTitle(cached.episode?.name) ?: "Episode $nextEp"
                currentVideoUrl = cached.url
                currentReferer = cached.referer
                currentSubtitleUrl = cached.subtitleUrl
                currentSubtitleTracks = cached.videos.firstOrNull()?.subtitleTracks ?: emptyList()
                currentServerName = if (!cached.hosters.isNullOrEmpty()) cached.hosters.first().hosterName else "Extension"
                currentCategory = "sub"
                currentQualityOptions = com.blissless.tensei.ui.screens.player.buildQualityOptions(cached.videos)
                currentQuality = cached.videoTitle
                extensionOkHttpClient = cached.extensionClient
                extensionVideoHeaders = cached.videoHeaders
                extensionHosters = cached.hosters
                extensionServers = com.blissless.tensei.ui.screens.player.buildServerList(cached.hosters)
                episodeCache[nextEp] = cached
                episodeTrigger++
                isChangingEpisode = false
                prefetchExtensionNextEpisode()
                fetchAndCacheEpisode(nextEp - 1)
            } else {
                isChangingEpisode = false
                isAutoRefreshing = false
                loadAndPlayEpisode(currentAnime!!, nextEp, isAutoRefresh = true)
            }
        }
    }
}
