package com.blissless.tensei.ui.screens.player

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import android.view.WindowManager
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.edit
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import com.blissless.tensei.api.AnimeSkipService
import com.blissless.tensei.data.models.EpisodeStreams
import com.blissless.tensei.data.models.EpisodeTimestamps
import com.blissless.tensei.data.models.ServerInfo
import com.blissless.tensei.data.models.SubtitleProfileData
import com.blissless.tensei.data.models.SubtitleSettings
import com.blissless.tensei.data.models.Timestamp
import com.blissless.tensei.util.longToast
import com.blissless.tensei.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

private fun subtitleMimeType(url: String): String {
    val ext = url.substringAfterLast('.', "").substringBefore('?').lowercase()
    return when (ext) {
        "srt" -> MimeTypes.APPLICATION_SUBRIP
        "ass", "ssa" -> MimeTypes.TEXT_SSA
        else -> MimeTypes.TEXT_VTT
    }
}

@RequiresApi(Build.VERSION_CODES.R)
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    videoUrl: String,
    referer: String,
    subtitleUrl: String? = null,
    subtitleTracks: List<eu.kanade.tachiyomi.animesource.model.Track> = emptyList(),
    currentEpisode: Int = 1,
    totalEpisodes: Int = 0,
    animeName: String = "",
    episodeTitle: String? = null,
    animeId: Int = 0,
    malId: Int = 0,
    animeYear: Int? = null,
    isLoadingStream: Boolean = false,
    episodeInfo: EpisodeStreams? = null,
    currentServerName: String = "",
    currentCategory: String = "sub",
    isFallbackStream: Boolean = false,
    requestedCategory: String = "sub",
    forwardSkipSeconds: Int = 10,
    backwardSkipSeconds: Int = 10,
    autoSkipOpening: Boolean = false,
    autoSkipEnding: Boolean = false,
    autoPlayNextEpisode: Boolean = false,
    savedPosition: Long = 0L,
    pendingSeekPosition: Long? = null,
    onSetPendingSeekPosition: ((Long?) -> Unit)? = null,
    currentQuality: String = "Auto",
    isLatestEpisode: Boolean = false,
    disableMaterialColors: Boolean = false,
    showBufferIndicator: Boolean = true,
    bufferAheadSeconds: Int = 30,
    playerEngine: String = "exo",
    swipeVolume: Boolean = false,
    swipeBrightness: Boolean = false,
    swipeSwap: Boolean = false,
    onSwipeVolumeChange: ((Boolean) -> Unit)? = null,
    onSwipeBrightnessChange: ((Boolean) -> Unit)? = null,
    onSwipeSwapChange: ((Boolean) -> Unit)? = null,
    animekaiIntroStart: Int? = null,
    animekaiIntroEnd: Int? = null,
    animekaiOutroStart: Int? = null,
    animekaiOutroEnd: Int? = null,
    onSavePosition: ((Long, Long) -> Unit)? = null,
    onClearPlaybackPosition: ((Int, Int) -> Unit)? = null,
    onPositionSaved: ((Long) -> Unit)? = null,
    onProgressUpdate: (percentage: Int) -> Unit = {},
    onPreviousEpisode: (() -> Unit)? = null,
    onNextEpisode: (() -> Unit)? = null,
    onServerChange: ((serverName: String, category: String) -> Unit)? = null,
    onPlaybackError: (() -> Unit)? = null,
    onInvalidateStreamCache: (() -> Unit)? = null,
    onRefreshStream: (() -> Unit)? = null,
    onPrefetchAdjacent: (() -> Unit)? = null,
    onGetCacheDataSourceFactory: (String) -> CacheDataSource.Factory? = { null },
    onBackClick: (() -> Unit)? = null,
    isFullscreen: Boolean = true,
    onFullscreenChanged: ((Boolean) -> Unit)? = null,
    extensionOkHttpClient: okhttp3.OkHttpClient? = null,
    extensionVideoHeaders: Map<String, String> = emptyMap(),
    extensionServers: List<ServerInfo> = emptyList(),
    extensionName: String = "",
    onExtensionServerChange: ((hosterName: String) -> Unit)? = null,
    onPrefetchNextExtensionEpisode: (() -> Unit)? = null,
    onAutoPlayNextEpisodeChanged: ((Boolean) -> Unit)? = null,
    isTorrentStream: Boolean = false,
    onTorrentSeek: ((positionMs: Long, durationMs: Long) -> Unit)? = null,
    supportsPiP: Boolean = false,
    onPiPToggle: ((Boolean) -> Unit)? = null,
    isInPiPMode: Boolean = false,
    onPlayerBoundsChanged: ((left: Int, top: Int, right: Int, bottom: Int) -> Unit)? = null,
    discordRichPresence: Boolean = false,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    var hasTriggeredProgressUpdate by remember { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var hasError by remember { mutableStateOf(false) }
    var isChangingServer by remember { mutableStateOf(false) }
    var serverChangeTrigger by remember { mutableIntStateOf(0) }
    var hasPlaybackStarted by remember { mutableStateOf(false) }
    var isManuallySeeking by remember { mutableStateOf(false) }
    var seekRetryCount by remember { mutableIntStateOf(0) }
    var isInitialLoading by remember { mutableStateOf(false) }
    val autoRetryServers = remember { mutableSetOf<String>() }
    var pendingAutoRetry by remember { mutableStateOf<String?>(null) }

    var resizeModeIndex by remember { mutableIntStateOf(0) }
    val resizeModes = listOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT to "Fit",
        AspectRatioFrameLayout.RESIZE_MODE_FILL to "Stretch",
        AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH to "16:9"
    )

    // Fullscreen state is owned by the host (lifted so it survives player recreation
    // on episode changes). The host mirrors it back in as this parameter.
    fun applyFullscreenWindow(fullscreen: Boolean) {
        activity?.let { act ->
            val window = act.window
            if (fullscreen) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
                window.attributes.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                WindowCompat.setDecorFitsSystemWindows(window, false)
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                @Suppress("DEPRECATION")
                window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
                WindowCompat.setDecorFitsSystemWindows(window, true)
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
    }

    LaunchedEffect(isFullscreen) {
        applyFullscreenWindow(isFullscreen)
    }

    // Handle fullscreen toggle
    fun toggleFullscreen() {
        onFullscreenChanged?.invoke(!isFullscreen)
    }

    // Exit fullscreen when closing
    fun exitFullscreen() {
        if (isFullscreen) {
            onFullscreenChanged?.invoke(false)
        }
    }

    var showControls by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(false) }
    var isOffline by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var bufferedPosition by remember { mutableLongStateOf(0L) }
    var maxBufferedPosition by remember { mutableLongStateOf(0L) }
    var showServerMenu by remember { mutableStateOf(false) }
    var showQualityMenu by remember { mutableStateOf(false) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showSubtitleMenu by remember { mutableStateOf(false) }
    var showPlayerSettings by remember { mutableStateOf(false) }
    var subtitlesEnabled by remember { mutableStateOf(subtitleTracks.isNotEmpty()) }
    var selectedSubtitleIndex by remember { mutableIntStateOf(0) }
    var showSubtitleSettings by remember { mutableStateOf(false) }
    var subtitleProfileData by remember { mutableStateOf(loadSubtitleProfileData(context)) }
    var subtitleViewRef by remember { mutableStateOf<SubtitleView?>(null) }
    var embeddedSubtitleTracks by remember { mutableStateOf<List<EmbeddedSubtitleTrack>>(emptyList()) }
    var selectedEmbeddedTrackIndex by remember { mutableIntStateOf(-1) }
    var accumulatedSkipMs by remember { mutableLongStateOf(0L) }
    var lastTapTime by remember { mutableLongStateOf(0L) }

    var selectedQuality by remember { mutableStateOf(currentQuality) }

    var sliderValue by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var wasPlayingBeforeScrub by remember { mutableStateOf(false) }

    var showSkipIndicator by remember { mutableStateOf(false) }
    var skipIndicatorText by remember { mutableStateOf("") }
    var skipIsForward by remember { mutableStateOf(true) }
    var skipResetJob by remember { mutableStateOf<Job?>(null) }

    var playerVolume by remember { mutableFloatStateOf(1f) }
    var currentBrightness by remember { mutableFloatStateOf(0.5f) }
    var showVolumeOverlay by remember { mutableStateOf(false) }
    var showBrightnessOverlay by remember { mutableStateOf(false) }

    LaunchedEffect(showControls, hasError, showSkipIndicator, isInPiPMode) {
        if (isInPiPMode) {
            delay(300.milliseconds)
            controlsVisible = false
        } else {
            controlsVisible = showControls || hasError || showSkipIndicator
        }
    }

    // Helper to check if device has internet connection
    fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    val animeSkipService = remember { AnimeSkipService(context) }

    var episodeTimestamps by remember(videoUrl) { mutableStateOf<EpisodeTimestamps?>(null) }
    var isFetchingTimestamps by remember(videoUrl) { mutableStateOf(false) }
    var hasSkippedIntro by remember(videoUrl) { mutableStateOf(false) }
    var hasSkippedOutro by remember(videoUrl) { mutableStateOf(false) }
    var showSkipOpeningButton by remember(videoUrl) { mutableStateOf(false) }
    var showSkipEndingButton by remember(videoUrl) { mutableStateOf(false) }
    var introEnteredTime by remember(videoUrl) { mutableLongStateOf(0L) }
    var creditsEnteredTime by remember(videoUrl) { mutableLongStateOf(0L) }
    var hasFetchedTimestamps by remember(videoUrl) { mutableStateOf(false) }
    var actualEpisodeLength by remember(videoUrl) { mutableStateOf<Int?>(null) }

    var pendingQualityChange by remember { mutableStateOf<String?>(null) }
    var savedPositionForQuality by remember { mutableLongStateOf(0L) }

    val scope = rememberCoroutineScope()

    var hasShownFallbackToast by remember(videoUrl) { mutableStateOf(false) }
    var hasRestoredPosition by remember(videoUrl) { mutableStateOf(false) }
    var hasTriggeredPrefetch by remember(videoUrl) { mutableStateOf(false) }

    // PRIMARY: Use Animekai timestamps if available, create initial timestamps immediately
    val animekaiTimestamps = remember(animekaiIntroStart, animekaiIntroEnd, animekaiOutroStart, animekaiOutroEnd, currentEpisode) {
        if (animekaiIntroStart != null || animekaiOutroStart != null) {
            EpisodeTimestamps(
                episodeNumber = currentEpisode,
                introStart = animekaiIntroStart?.toLong(),
                introEnd = animekaiIntroEnd?.toLong(),
                creditsStart = animekaiOutroStart?.toLong(),
                creditsEnd = animekaiOutroEnd?.toLong(),
                recapStart = null,
                recapEnd = null,
                allTimestamps = buildList {
                    if (animekaiIntroStart != null) add(Timestamp(animekaiIntroStart.toDouble(), "op", "op"))
                    if (animekaiOutroStart != null) add(Timestamp(animekaiOutroStart.toDouble(), "ed", "ed"))
                }
            )
        } else null
    }

    val effectiveTimestamps by remember(episodeTimestamps, animekaiTimestamps) {
        derivedStateOf {
            episodeTimestamps ?: animekaiTimestamps ?: EpisodeTimestamps(
                episodeNumber = currentEpisode,
                introStart = null,
                introEnd = null,
                creditsStart = null,
                creditsEnd = null,
                recapStart = null,
                recapEnd = null,
                allTimestamps = emptyList()
            )
        }
    }

    // Update selected quality when currentQuality prop changes
    LaunchedEffect(currentQuality) {
        selectedQuality = currentQuality
    }

    val engine = remember(context, bufferAheadSeconds, playerEngine) {
        when (playerEngine) {
            "mpv" -> MpvEngine(context)
            else -> ExoPlayerEngine(context, bufferAheadSeconds) { ref -> onGetCacheDataSourceFactory(ref) }
        }
    }

    LaunchedEffect(engine) {
        engine.setListener(object : PlayerEngine.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (playing) {
                    hasPlaybackStarted = true
                }
                val act = context as? Activity
                if (act is com.blissless.tensei.MainActivity && act.isInPiPMode.value) {
                    act.updatePiPPlayPauseIcon(playing)
                }
            }

            override fun onTracksChanged(tracks: List<EmbeddedSubtitleTrack>) {
                embeddedSubtitleTracks = tracks
                Log.d("PlayerScreen", "onTracksChanged: discovered ${tracks.size} embedded subtitle tracks: ${tracks.map { it.label }}")
            }

            override fun onError(error: String) {
                Log.e("PlayerScreen", "onError: msg=$error isChangingServer=$isChangingServer isInitialLoading=$isInitialLoading url=${videoUrl.take(80)}")
                if (isChangingServer) {
                    return
                }

                if (!isNetworkAvailable()) {
                    isOffline = true
                    isBuffering = true
                    hasError = false
                    playbackError = null
                    return
                }

                if ((isManuallySeeking || seekRetryCount > 0) && seekRetryCount < 3) {
                    seekRetryCount++
                    hasError = false
                    playbackError = null
                    isBuffering = true
                    val seekPos = currentPosition
                    Log.w("PlayerScreen", "onError: retry #$seekRetryCount seekPos=$seekPos isTorrentStream=$isTorrentStream error=$error")
                    if (isTorrentStream && seekPos > 0) {
                        Log.d("PlayerScreen", "onError: retry#$seekRetryCount torrent seekTo=$seekPos (TorrentStreamServer handles Range)")
                        onTorrentSeek?.invoke(seekPos, engine.duration)
                        engine.seekTo(seekPos)
                    } else if (seekPos > 0) {
                        Log.d("PlayerScreen", "onError: retry#$seekRetryCount seekOutsideBuffer(seekPos=$seekPos)")
                        engine.seekOutsideBuffer(seekPos)
                    } else {
                        Log.d("PlayerScreen", "onError: retry#$seekRetryCount prepare() only")
                        engine.prepare()
                    }
                    return
                }

                // For torrent streams (local server), don't auto-refresh — that
                // restarts the entire torrent download. Instead, retry the seek
                // so the engine reconnects to the TorrentStreamServer after more
                // data has downloaded.
                if (isInitialLoading && isTorrentStream) {
                    Log.d("PlayerScreen", "onError: torrent stream initial-load error, re-prepare")
                    seekRetryCount = 1
                    hasError = false
                    playbackError = null
                    isBuffering = true
                    onTorrentSeek?.invoke(currentPosition, engine.duration)
                    engine.prepare()
                    return
                }

                // Auto-retry: try next untried server before anything else
                if (onExtensionServerChange != null && extensionServers.isNotEmpty()) {
                    autoRetryServers.add(currentServerName)
                    extensionServers.find { it.url == videoUrl }?.let { autoRetryServers.add(it.name) }
                    fun srvCat(name: String): String = when {
                        name.contains("dub", ignoreCase = true) -> "dub"
                        name.contains("sub", ignoreCase = true) -> "sub"
                        else -> "other"
                    }
                    val curCat = srvCat(currentServerName)
                    val remaining = extensionServers.filter {
                        it.name !in autoRetryServers && it.url != videoUrl
                    }
                    val sameCat = remaining.filter { srvCat(it.name) == curCat }
                    val otherCat = remaining.filter { srvCat(it.name) != curCat }
                    val nextServer = (sameCat + otherCat).firstOrNull()
                    if (nextServer != null) {
                        Log.d("PlayerScreen", "Auto-retrying server: ${nextServer.name} (tried: $autoRetryServers)")
                        pendingAutoRetry = nextServer.name
                        return
                    }
                    Log.w("PlayerScreen", "All extension servers exhausted")
                }

                // Auto-refresh for initial load / re-entry failure (stale cached URL).
                // isAutoRefreshing in MainActivity prevents infinite refreshes.
                if (isInitialLoading && onRefreshStream != null) {
                    onInvalidateStreamCache?.invoke()
                    onRefreshStream.invoke()
                    return
                }

                Log.e("PlayerScreen", "SURFACING ERROR TO USER: msg=$error isManuallySeeking=$isManuallySeeking seekRetryCount=$seekRetryCount isInitialLoading=$isInitialLoading")
                hasError = true
                playbackError = error
                showControls = true
                Log.e("PlayerScreen", "Playback error details: msg=$error videoUrl=${videoUrl.take(120)}")
            }

            override fun onPlaybackStateChanged(state: Int) {
                val stateName = when (state) { PlayerEngine.STATE_IDLE -> "IDLE"; PlayerEngine.STATE_BUFFERING -> "BUFFERING"; PlayerEngine.STATE_READY -> "READY"; PlayerEngine.STATE_ENDED -> "ENDED"; else -> "$state" }
                Log.d("PlayerScreen", "onPlaybackStateChanged: $stateName isManuallySeeking=$isManuallySeeking seekRetryCount=$seekRetryCount")
                isBuffering = state == PlayerEngine.STATE_BUFFERING
                if (state == PlayerEngine.STATE_READY) {
                    hasError = false
                    playbackError = null
                    isChangingServer = false
                    isBuffering = false
                    hasPlaybackStarted = true
                    isInitialLoading = false
                    autoRetryServers.clear()
                    if (pendingQualityChange != null && savedPositionForQuality > 0) {
                        val wasPlaying = engine.playWhenReady
                        val restorePos = savedPositionForQuality
                        engine.stop()
                        engine.clearMediaItems()
                        engine.loadMedia(
                            url = videoUrl,
                            mimeType = null,
                            startPositionMs = restorePos,
                            subtitleConfigs = emptyList(),
                            headers = extensionVideoHeaders,
                            referer = referer,
                            httpClient = extensionOkHttpClient,
                        )
                        engine.prepare()
                        engine.playWhenReady = wasPlaying
                        if (engine is ExoPlayerEngine) {
                            com.blissless.tensei.stream.PlayerData.exoPlayer = engine.getExoPlayer()
                        }
                        pendingQualityChange = null
                        savedPositionForQuality = 0L
                    }
                }
                if (state == PlayerEngine.STATE_ENDED) {
                    onClearPlaybackPosition?.invoke(animeId, currentEpisode)
                    if (autoPlayNextEpisode && onNextEpisode != null && !isChangingServer) {
                        if (isLatestEpisode) {
                            context.toast("Latest episode watched")
                        } else {
                            onNextEpisode.invoke()
                        }
                    }
                }
            }
        })
    }

    DisposableEffect(engine) {
        com.blissless.tensei.stream.PlayerData.playerEngine = engine
        if (engine is ExoPlayerEngine) {
            com.blissless.tensei.stream.PlayerData.exoPlayer = engine.getExoPlayer()
        }
        onDispose {
            engine.stop()
            engine.release()
            com.blissless.tensei.stream.PlayerData.exoPlayer = null
            com.blissless.tensei.stream.PlayerData.playerEngine = null
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            (activity as? com.blissless.tensei.MainActivity)?.releasePiPMediaSession()
            onSavePosition?.invoke(currentPosition, duration)
            activity?.window?.let { window ->
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
                WindowCompat.setDecorFitsSystemWindows(window, true)
                // Restore system brightness when leaving the player
                window.attributes = window.attributes.apply {
                    screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
            }
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            if (discordRichPresence) {
                com.blissless.tensei.discord.DiscordRichPresence.clearPresence()
            }
        }
    }

    // Discord Rich Presence — set when playback starts, update countdown periodically
    LaunchedEffect(hasPlaybackStarted, discordRichPresence, animeName, currentEpisode, totalEpisodes) {
        if (hasPlaybackStarted && discordRichPresence && animeName.isNotEmpty()) {
            com.blissless.tensei.discord.DiscordRichPresence.connect()
            // Wait for duration to become available before sending presence
            while (engine.duration <= 0) {
                delay(250L.milliseconds)
            }
            com.blissless.tensei.discord.DiscordRichPresence.setAnimePresence(
                animeName = animeName,
                episode = currentEpisode,
                totalEpisodes = totalEpisodes,
                durationMs = engine.duration,
                currentPositionMs = engine.currentPosition,
            )
            while (true) {
                delay(30_000L.milliseconds)
                com.blissless.tensei.discord.DiscordRichPresence.setAnimePresence(
                    animeName = animeName,
                    episode = currentEpisode,
                    totalEpisodes = totalEpisodes,
                    durationMs = engine.duration,
                    currentPositionMs = engine.currentPosition,
                )
            }
        }
    }

    LaunchedEffect(videoUrl, serverChangeTrigger) {
        hasError = false
        playbackError = null
        hasRestoredPosition = false
        hasSkippedIntro = false
        hasSkippedOutro = false
        introEnteredTime = 0L
        creditsEnteredTime = 0L
        hasTriggeredPrefetch = false
        isChangingServer = false
        hasPlaybackStarted = false
        bufferedPosition = 0L
        maxBufferedPosition = 0L
        isOffline = false
        seekRetryCount = 0
        isInitialLoading = true

        engine.stop()
        delay(100.milliseconds)
        engine.clearMediaItems()

        // Use pendingSeekPosition for refresh-based seeks, otherwise restore savedPosition.
        // pendingSeekPosition is cleared externally (onBackClick / new episode load) to
        // avoid races when multiple consecutive seeks trigger overlapping refreshes.
        val restorePosition = pendingSeekPosition ?: savedPosition
        val startPositionMs = if (restorePosition > 0) restorePosition else 0L

        val subtitleConfigs = if (subtitlesEnabled && subtitleTracks.isNotEmpty()) {
            subtitleTracks.mapIndexed { index, track ->
                SubtitleConfig(
                    url = track.url,
                    mimeType = subtitleMimeType(track.url),
                    language = track.lang,
                    selected = index == selectedSubtitleIndex,
                )
            }
        } else if (subtitleUrl != null) {
            listOf(SubtitleConfig(
                url = subtitleUrl,
                mimeType = subtitleMimeType(subtitleUrl),
                language = "en",
                selected = true,
            ))
        } else {
            emptyList()
        }

        Log.d("PlayerScreen", "Preparing playback: videoUrl=${videoUrl.take(120)} referer=$referer subtitleUrl=${subtitleUrl?.take(80)} extensionOkHttpClient=${extensionOkHttpClient != null} videoHeaders=$extensionVideoHeaders")

        engine.loadMedia(
            url = videoUrl,
            mimeType = null, // engine auto-detects
            startPositionMs = startPositionMs,
            subtitleConfigs = subtitleConfigs,
            headers = extensionVideoHeaders,
            referer = referer,
            httpClient = extensionOkHttpClient,
        )
        engine.prepare()

        // The engine creates its backing ExoPlayer during loadMedia(); keep
        // PlayerData in sync for PiP compatibility.
        if (engine is ExoPlayerEngine) {
            com.blissless.tensei.stream.PlayerData.exoPlayer = engine.getExoPlayer()
        }

        hasPlaybackStarted = true

        hasTriggeredProgressUpdate = false
        currentPosition = startPositionMs
        sliderValue = startPositionMs.toFloat()
    }

    LaunchedEffect(engine.playbackState, hasRestoredPosition, videoUrl) {
        if (engine.playbackState == PlayerEngine.STATE_READY && hasPlaybackStarted && !hasRestoredPosition) {
            hasRestoredPosition = true
            // Start playback after seek
            engine.playWhenReady = true
        }
    }

    LaunchedEffect(isPlaying, hasTriggeredPrefetch, hasError, isLatestEpisode) {
        if (isPlaying && !hasTriggeredPrefetch && !hasError && onPrefetchAdjacent != null && !isLatestEpisode) {
            delay(5000.milliseconds)
            if (!hasTriggeredPrefetch && isPlaying && !hasError) {
                hasTriggeredPrefetch = true
                onPrefetchAdjacent.invoke()
            }
        }
    }

    LaunchedEffect(videoUrl, isFallbackStream) {
        if (isFallbackStream && !hasShownFallbackToast && videoUrl.isNotEmpty()) {
            hasShownFallbackToast = true
            val message = if (requestedCategory == "dub") "Dub not available, playing sub" else "Sub not available, playing dub"
            context.longToast(message)
        }
    }

    // Prefetch next extension episode on first playback
    LaunchedEffect(hasPlaybackStarted) {
        if (hasPlaybackStarted && extensionServers.isNotEmpty()) {
            onPrefetchNextExtensionEpisode?.invoke()
        }
    }

    fun seekToPosition(position: Long) {
        Log.d("PlayerScreen", "seekToPosition: pos=$position bufferedPos=$bufferedPosition maxBufferedPos=$maxBufferedPosition duration=${engine.duration} isManuallySeeking=$isManuallySeeking isTorrentStream=$isTorrentStream")
        hasError = false
        playbackError = null
        isBuffering = true
        maxBufferedPosition = position
        bufferedPosition = position
        if (isTorrentStream) {
            Log.d("PlayerScreen", "seekToPosition: torrent stream — calling onTorrentSeek then seekTo($position), TorrentStreamServer handles Range requests")
            onTorrentSeek?.invoke(position, engine.duration)
            engine.seekTo(position)
            return
        }
        // For non-torrent streams, seekOutsideBuffer handles HLS natively,
        // inside-buffer seeks and clip-based re-prepare for out-of-buffer seeks.
        engine.seekOutsideBuffer(position)
    }

    fun seekBy(milliseconds: Long) {
        Log.d("PlayerScreen", "seekBy: ms=$milliseconds currentPos=$currentPosition bufferedPos=$bufferedPosition duration=$duration")
        isManuallySeeking = true
        seekRetryCount = 0

        // Show skip indicator (separate from player UI)
        skipIndicatorText = if (milliseconds > 0) "+${abs(milliseconds / 1000)}s" else "-${abs(milliseconds / 1000)}s"
        skipIsForward = milliseconds >= 0
        showSkipIndicator = true

        // Handle accumulated skips within 300ms window
        val now = System.currentTimeMillis()
        if (now - lastTapTime < 300) {
            accumulatedSkipMs += milliseconds
        } else {
            accumulatedSkipMs = milliseconds
        }
        lastTapTime = now

        // Always seek by single skip amount, not accumulated
        // Use currentPosition (displayed position) instead of engine.currentPosition
        // because the engine may still be seeking from a previous scrub and return a stale value
        val duration = engine.duration
        val newPosition = if (duration > 0) {
            (currentPosition + milliseconds).coerceIn(0, duration)
        } else {
            (currentPosition + milliseconds).coerceAtLeast(0)
        }
        seekToPosition(newPosition)
        currentPosition = newPosition
        sliderValue = newPosition.toFloat()

        // Update text with accumulated skip time
        val totalSeconds = abs(accumulatedSkipMs / 1000)
        skipIndicatorText = if (accumulatedSkipMs > 0) "+${totalSeconds}s" else "-${totalSeconds}s"

        // Schedule reset after 500ms of no taps
        skipResetJob?.cancel()
        skipResetJob = scope.launch {
            delay(500.milliseconds)
            showSkipIndicator = false
            isManuallySeeking = false
            accumulatedSkipMs = 0L
        }
    }

    fun performManualSeek(position: Long) {
        Log.d("PlayerScreen", "performManualSeek: pos=$position bufferedPos=$bufferedPosition duration=$duration")
        isManuallySeeking = true
        seekRetryCount = 0
        seekToPosition(position)
        currentPosition = position
        sliderValue = position.toFloat()
        skipResetJob?.cancel()
        skipResetJob = scope.launch {
            delay(1500.milliseconds)
            isManuallySeeking = false
        }
    }

    LaunchedEffect(engine, videoUrl) {
        while (true) {
            delay(500.milliseconds)
            if (!isDragging && !isManuallySeeking) {
                currentPosition = engine.currentPosition
                duration = engine.duration
                bufferedPosition = engine.bufferedPosition
                maxBufferedPosition = bufferedPosition
                if (duration > 0) {
                    sliderValue = currentPosition.toFloat()
                    if (actualEpisodeLength == null && duration > 60000 && engine.playbackState == PlayerEngine.STATE_READY) {
                        actualEpisodeLength = (duration / 1000).toInt()
                    }
                }
            }
        }
    }

    // FALLBACK: Only fetch from AnimeSkip/AnimeThemes if Animekai timestamps are NOT available
    LaunchedEffect(
        actualEpisodeLength,
        videoUrl,
        malId,
        animeYear,
        animeName,
        animekaiTimestamps?.hasTimestamps()
    ) {
        val epLength = actualEpisodeLength
        if (epLength == null || hasFetchedTimestamps) return@LaunchedEffect

        if (animekaiTimestamps?.hasTimestamps() == true) {
            hasFetchedTimestamps = true
            return@LaunchedEffect
        }

        isFetchingTimestamps = true

        withContext(Dispatchers.IO) {
            try {
                val timestamps = if (malId > 0) {
                    animeSkipService.getSkipTimestampsWithFallback(
                        malId = malId,
                        episodeNumber = currentEpisode,
                        episodeLength = epLength,
                        animeName = animeName,
                        animeYear = animeYear,
                        animeId = animeId
                    )
                } else if (animeName.isNotEmpty()) {
                    animeSkipService.getSkipTimestampsByName(
                        animeName = animeName,
                        episodeNumber = currentEpisode,
                        episodeLength = epLength,
                        year = animeYear
                    )
                } else null

                if (timestamps != null && timestamps.hasTimestamps()) {
                    episodeTimestamps = timestamps
                }
            } catch (_: Exception) {
            }
        }

        isFetchingTimestamps = false
        hasFetchedTimestamps = true
    }

    LaunchedEffect(currentPosition, effectiveTimestamps, hasError, isManuallySeeking, isChangingServer, isDragging, isPlaying, controlsVisible) {
        if (hasError || isChangingServer || isDragging) return@LaunchedEffect

        val ts = effectiveTimestamps
        val posSeconds = currentPosition / 1000

        if (ts.introStart != null && ts.introEnd != null) {
            val isInIntro = posSeconds >= ts.introStart && posSeconds < ts.introEnd
            if (isInIntro) {
                if (autoSkipOpening && !hasSkippedIntro && !isManuallySeeking) {
                    engine.seekTo(ts.introEnd * 1000L)
                    hasSkippedIntro = true
                }
                if (!autoSkipOpening) {
                    if (introEnteredTime == 0L) {
                        introEnteredTime = System.currentTimeMillis()
                        Log.d("SkipButton", "enterIntro: pos=$posSeconds t=${introEnteredTime}")
                    }
                    val elapsed = System.currentTimeMillis() - introEnteredTime
                    val shouldShow = controlsVisible || !isPlaying || elapsed < 5000
                    Log.d("SkipButton", "opening: pos=$posSeconds introTime=$elapsed controls=$controlsVisible isPlaying=$isPlaying show=$shouldShow")
                    showSkipOpeningButton = shouldShow
                } else {
                    showSkipOpeningButton = false
                }
            } else {
                if (introEnteredTime != 0L) {
                    Log.d("SkipButton", "exitIntro: pos=$posSeconds")
                }
                introEnteredTime = 0L
                showSkipOpeningButton = false
            }
        }

        if (ts.creditsStart != null && onNextEpisode != null) {
            val isInCredits = posSeconds >= ts.creditsStart
            if (isInCredits) {
                if (autoSkipEnding && !hasSkippedOutro && !isManuallySeeking) {
                    if (isLatestEpisode) {
                        context.toast("Latest episode watched")
                    } else {
                        onNextEpisode.invoke()
                    }
                    hasSkippedOutro = true
                }
                if (!autoSkipEnding) {
                    if (creditsEnteredTime == 0L) {
                        creditsEnteredTime = System.currentTimeMillis()
                        Log.d("SkipButton", "enterCredits: pos=$posSeconds t=${creditsEnteredTime}")
                    }
                    val elapsed = System.currentTimeMillis() - creditsEnteredTime
                    val shouldShow = controlsVisible || !isPlaying || elapsed < 5000
                    Log.d("SkipButton", "ending: pos=$posSeconds creditsTime=$elapsed controls=$controlsVisible isPlaying=$isPlaying show=$shouldShow")
                    showSkipEndingButton = shouldShow
                } else {
                    showSkipEndingButton = false
                }
            } else {
                if (creditsEnteredTime != 0L) {
                    Log.d("SkipButton", "exitCredits: pos=$posSeconds")
                }
                creditsEnteredTime = 0L
                showSkipEndingButton = false
            }
        }
    }

    LaunchedEffect(engine, hasTriggeredProgressUpdate) {
        while (!hasTriggeredProgressUpdate) {
            delay(1000.milliseconds)
            if (engine.playbackState == PlayerEngine.STATE_READY && engine.duration > 0) {
                val percentage = ((engine.currentPosition.toFloat() / engine.duration) * 100).toInt()
                onProgressUpdate(percentage)
            }
        }
    }
    LaunchedEffect(showControls, isPlaying, isDragging, hasError, showServerMenu, showQualityMenu, showSpeedMenu, showSubtitleMenu, showPlayerSettings, isManuallySeeking) {
        if (showControls && isPlaying && !isDragging && !hasError && !showServerMenu && !showQualityMenu && !showSpeedMenu && !showSubtitleMenu && !showPlayerSettings && !isManuallySeeking) {
            delay(2000.milliseconds)
            if (showControls && !isDragging && !hasError && isPlaying && !showServerMenu && !showSpeedMenu && !showSubtitleMenu && !showPlayerSettings && !isManuallySeeking) {
                showControls = false
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            engine.stop()
            engine.clearMediaItems()
            engine.release()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, engine) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                val mainAct = activity as? com.blissless.tensei.MainActivity
                val inPiP = mainAct?.isInPiPMode?.value == true
                val autoEnterPiP = mainAct?.shouldAutoEnterPiP == true
                if (!inPiP && !autoEnterPiP) {
                    engine.pause()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun formatTime(ms: Long): String {
        val seconds = (ms / 1000) % 60
        val minutes = (ms / (1000 * 60)) % 60
        val hours = ms / (1000 * 60 * 60)
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }

    val subServers = episodeInfo?.subServers ?: emptyList()
    val dubServers = episodeInfo?.dubServers ?: emptyList()

    val introStartRatio = if (duration > 0 && effectiveTimestamps.introStart != null) {
        (effectiveTimestamps.introStart!! * 1000).toFloat() / duration.toFloat()
    } else null
    val introEndRatio = if (duration > 0 && effectiveTimestamps.introEnd != null) {
        (effectiveTimestamps.introEnd!! * 1000).toFloat() / duration.toFloat()
    } else null
    val creditsStartRatio = if (duration > 0 && effectiveTimestamps.creditsStart != null) {
        (effectiveTimestamps.creditsStart!! * 1000).toFloat() / duration.toFloat()
    } else null
    val creditsAtEnd = if (duration > 0 && effectiveTimestamps.creditsEnd != null) {
        val creditsEndSeconds = effectiveTimestamps.creditsEnd!! * 1000
        val durationDiff = duration - creditsEndSeconds
        durationDiff < 30000 // Credits end within 30 seconds of the end
    } else false

    fun handleServerChange(serverName: String, category: String) {
        isChangingServer = true
        hasPlaybackStarted = false
        hasError = false
        playbackError = null

        // Save current position BEFORE stopping
        val currentDur = engine.duration
        onSavePosition?.invoke(engine.currentPosition, if (currentDur > 0) currentDur else 0L)
        onPositionSaved?.invoke(engine.currentPosition)

        // Stop and clear the current playback to prevent audio overlap
        engine.stop()
        engine.clearMediaItems()

        // Small delay before triggering server change to ensure error popup disappears
        scope.launch {
            delay(50.milliseconds)
            serverChangeTrigger++
        }
        onServerChange?.invoke(serverName, category)
    }

    LaunchedEffect(pendingAutoRetry) {
        val target = pendingAutoRetry ?: return@LaunchedEffect
        pendingAutoRetry = null
        Log.d("PlayerScreen", "Executing auto-retry for server: $target")
        isChangingServer = true
        hasPlaybackStarted = false
        hasError = false
        playbackError = null
        engine.stop()
        engine.clearMediaItems()
        if (extensionServers.isNotEmpty()) {
            onExtensionServerChange?.invoke(target)
        } else {
            handleServerChange(target, currentCategory)
        }
    }

    fun handlePlaybackError() {
        onInvalidateStreamCache?.invoke()
        onPlaybackError?.invoke()
    }

    fun rebuildWithSubtitles(enable: Boolean) {
        subtitlesEnabled = enable
        val position = engine.currentPosition
        val playWhenReady = engine.playWhenReady
        val subtitleConfigs = if (subtitlesEnabled && subtitleTracks.isNotEmpty()) {
            subtitleTracks.mapIndexed { index, track ->
                SubtitleConfig(
                    url = track.url,
                    mimeType = subtitleMimeType(track.url),
                    language = track.lang,
                    selected = index == selectedSubtitleIndex,
                )
            }
        } else if (subtitlesEnabled && subtitleUrl != null) {
            listOf(SubtitleConfig(
                url = subtitleUrl,
                mimeType = subtitleMimeType(subtitleUrl),
                language = "en",
                selected = true,
            ))
        } else {
            emptyList()
        }
        if (!enable) {
            engine.disableSubtitles()
        } else {
            selectedEmbeddedTrackIndex = -1
        }
        engine.rebuildWithSubtitles(videoUrl, subtitleConfigs, position, playWhenReady)
    }

    fun getActiveSubtitleSettings(): SubtitleSettings {
        val data = subtitleProfileData
        return data.profiles.getOrElse(data.activeProfileIndex) { SubtitleSettings.DEFAULT }
    }

    fun saveSubtitleProfileData(data: SubtitleProfileData) {
        subtitleProfileData = data
        val json = kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
        val encoded = json.encodeToString(SubtitleProfileData.serializer(), data)
        context.getSharedPreferences("anilist_prefs", Context.MODE_PRIVATE).edit {
                putString("subtitle_profiles", encoded)
                .putInt("subtitle_active_profile", data.activeProfileIndex)
            }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onGloballyPositioned { coords: LayoutCoordinates ->
                val bounds = coords.boundsInWindow()
                onPlayerBoundsChanged?.invoke(
                    bounds.left.toInt(), bounds.top.toInt(),
                    bounds.right.toInt(), bounds.bottom.toInt()
                )
            }
    ) {
        // Compact layout (video shown in a 16:9 box with the episode list below):
        // shrink paddings, hide secondary text and use smaller buttons so the
        // controls fit the reduced-height video area.
        val isCompact = !isFullscreen
        // Player view - recreate when server or engine changes
        key(serverChangeTrigger, playerEngine) {
            AndroidView(
                factory = { _ ->
                    // Apply initial settings; the engine owns and configures its own view
                    val v = engine.view
                    if (v is PlayerView) {
                        v.useController = false
                        v.setShowNextButton(false)
                        v.setShowPreviousButton(false)
                        v.setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                        v.controllerShowTimeoutMs = 3000
                        v.controllerAutoShow = false
                        v.resizeMode = resizeModes[resizeModeIndex].first
                        val activeSubSettings = getActiveSubtitleSettings()
                        v.subtitleView?.apply { applySubtitleStyle(this, activeSubSettings) }
                    }
                    v
                },
                modifier = Modifier
                    .fillMaxSize(),
                update = { view ->
                    if (view is PlayerView) {
                        view.resizeMode = resizeModes[resizeModeIndex].first
                        subtitleViewRef = view.subtitleView
                        val activeSubSettings = getActiveSubtitleSettings()
                        view.subtitleView?.apply { applySubtitleStyle(this, activeSubSettings) }
                    }
                }
            )
        }

        LaunchedEffect(subtitleProfileData) {
            subtitleViewRef?.let { view ->
                applySubtitleStyle(view, getActiveSubtitleSettings())
            }
        }

        // 2. Active Gesture Zones (Middle Layer)
        // These handle seeking and toggling controls. Defined first so they are "under" the padding zones.

        // Left Seek Zone (30% width, offset by padding)
        // Also handles vertical drag for volume when swipeVolume is enabled
        var lastLeftTapTime by remember { mutableLongStateOf(0L) }
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.3f)
                .padding(start = if (isCompact) 0.dp else 40.dp)
                .align(Alignment.CenterStart)
                .pointerInput(swipeVolume, swipeBrightness, swipeSwap) {
                    val leftEnabled = if (swipeSwap) swipeBrightness else swipeVolume
                    if (leftEnabled) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { _, dragAmount ->
                                if (!hasError) {
                                    if (swipeSwap) {
                                        val brightnessChange = -(dragAmount / 1000f)
                                        currentBrightness = (currentBrightness + brightnessChange).coerceIn(0.01f, 1f)
                                        activity?.let { act ->
                                            val lp = act.window.attributes
                                            lp.screenBrightness = currentBrightness
                                            act.window.attributes = lp
                                        }
                                        showBrightnessOverlay = true
                                        scope.launch {
                                            delay(1500.milliseconds)
                                            showBrightnessOverlay = false
                                        }
                                    } else {
                                        val volumeChange = -(dragAmount / 500f)
                                        playerVolume = (playerVolume + volumeChange).coerceIn(0f, 1f)
                                        engine.setVolume(playerVolume)
                                        showVolumeOverlay = true
                                        scope.launch {
                                            delay(1500.milliseconds)
                                            showVolumeOverlay = false
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
                .pointerInput(backwardSkipSeconds) {
                    detectTapGestures(
                        onTap = {
                            if (!hasError) {
                                val now = System.currentTimeMillis()
                                if (now - lastLeftTapTime < 300) {
                                    // Double tap - seek
                                    seekBy(-(backwardSkipSeconds * 1000L))
                                } else {
                                    // Single tap - toggle controls
                                    showControls = !showControls
                                }
                                lastLeftTapTime = now
                            }
                        }
                    )
                }
        )

        // Center Toggle Zone (40% width)
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.4f)
                .align(Alignment.Center)
                .pointerInput(Unit) { detectTapGestures(onTap = { if (!hasError) showControls = !showControls }) }
        )

        // Right Seek Zone (30% width, offset by padding)
        // Also handles vertical drag for brightness when swipeBrightness is enabled
        var lastRightTapTime by remember { mutableLongStateOf(0L) }
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.3f)
                .padding(end = if (isCompact) 0.dp else 40.dp)
                .align(Alignment.CenterEnd)
                .pointerInput(swipeVolume, swipeBrightness, swipeSwap) {
                    val rightEnabled = if (swipeSwap) swipeVolume else swipeBrightness
                    if (rightEnabled) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { _, dragAmount ->
                                if (!hasError) {
                                    if (swipeSwap) {
                                        val volumeChange = -(dragAmount / 500f)
                                        playerVolume = (playerVolume + volumeChange).coerceIn(0f, 1f)
                                        engine.setVolume(playerVolume)
                                        showVolumeOverlay = true
                                        scope.launch {
                                            delay(1500.milliseconds)
                                            showVolumeOverlay = false
                                        }
                                    } else {
                                        val brightnessChange = -(dragAmount / 1000f)
                                        currentBrightness = (currentBrightness + brightnessChange).coerceIn(0.01f, 1f)
                                        activity?.let { act ->
                                            val lp = act.window.attributes
                                            lp.screenBrightness = currentBrightness
                                            act.window.attributes = lp
                                        }
                                        showBrightnessOverlay = true
                                        scope.launch {
                                            delay(1500.milliseconds)
                                            showBrightnessOverlay = false
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
                .pointerInput(forwardSkipSeconds) {
                    detectTapGestures(
                        onTap = {
                            if (!hasError) {
                                val now = System.currentTimeMillis()
                                if (now - lastRightTapTime < 300) {
                                    // Double tap - seek
                                    seekBy(forwardSkipSeconds * 1000L)
                                } else {
                                    // Single tap - toggle controls
                                    showControls = !showControls
                                }
                                lastRightTapTime = now
                            }
                        }
                    )
                }
        )

        // 3. Padding Zones (Top Layer over Active Zones, Under UI Controls)
        // These consume touches to prevent UI toggling in safe areas.
        // Defined after active zones so they take precedence in overlap areas.
        // Only in fullscreen — compact player uses minimal chrome.
        if (!isCompact) {
            // Left Padding
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(40.dp)
                    .align(Alignment.CenterStart)
                    .pointerInput(Unit) { detectTapGestures(onTap = {}) }
            )

            // Right Padding
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(40.dp)
                    .align(Alignment.CenterEnd)
                    .pointerInput(Unit) { detectTapGestures(onTap = {}) }
            )

            // Top Padding (Matches side padding logic)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .align(Alignment.TopCenter)
                    .pointerInput(Unit) { detectTapGestures(onTap = {}) }
            )
        }

        // Controls UI with darkening overlay (drawn first)
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(animationSpec = tween(150)),
            exit = fadeOut(animationSpec = tween(100)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Darkening overlay when controls are visible
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = if (controlsVisible) 0.3f else 0f))
                )

                // Top gradient - slides from top
                AnimatedVisibility(
                    visible = controlsVisible,
                    enter = slideInVertically(initialOffsetY = { -it }, animationSpec = tween(200)),
                    exit = slideOutVertically(targetOffsetY = { -it }, animationSpec = tween(100)),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(colors = listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)))
                            .padding(if (isCompact) 4.dp else 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = if (isCompact) Modifier else Modifier.weight(1f)
                            ) {
                                IconButton(
                                    onClick = {
                                        exitFullscreen()
                                        onBackClick?.invoke()
                                    },
                                    modifier = Modifier.size(if (isCompact) 32.dp else 40.dp)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = Color.White,
                                        modifier = Modifier.size(if (isCompact) 20.dp else 24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                if (!isCompact) {
                                    Column(modifier = Modifier.weight(1f, fill = false)) {
                                        if (animeName.isNotEmpty()) {
                                            Text(
                                                text = animeName,
                                                color = Color.White,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            if (!episodeTitle.isNullOrEmpty()) {
                                                Text(
                                                    text = episodeTitle,
                                                    color = Color.White.copy(alpha = 0.7f),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text("·", color = Color.White.copy(alpha = 0.4f), style = MaterialTheme.typography.labelSmall)
                                            }
                                            Text(
                                                text = "Ep $currentEpisode${if (totalEpisodes > 0) "/$totalEpisodes" else ""}",
                                                color = Color.White.copy(alpha = 0.7f),
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                            if (isFetchingTimestamps || isChangingServer) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(12.dp),
                                                    strokeWidth = 1.5.dp,
                                                    color = if (disableMaterialColors) Color.White else MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                }

                            }

                            fun catFromName(name: String): String = when {
                                name.contains("dub", ignoreCase = true) -> "DUB"
                                name.contains("sub", ignoreCase = true) -> "SUB"
                                extensionServers.isNotEmpty() -> extensionName.ifEmpty { "EXT" }
                                else -> "EXT"
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(if (isCompact) 4.dp else 8.dp), modifier = Modifier.width(IntrinsicSize.Max)) {
                                // Server selector
                                if (!isCompact && ((onServerChange != null && (subServers.isNotEmpty() || dubServers.isNotEmpty())) || extensionServers.isNotEmpty())) {
                                    Box {
                                        Surface(
                                            shape = RoundedCornerShape(if (isCompact) 10.dp else 14.dp),
                                            color = if (isCompact) Color.Transparent else Color.Black.copy(alpha = 0.5f),
                                            onClick = { showServerMenu = true }
                                        ) {
                                        Row(
                                            modifier = Modifier
                                                .defaultMinSize(minWidth = if (isCompact) 28.dp else 44.dp)
                                                .padding(horizontal = if (isCompact) 6.dp else 12.dp, vertical = if (isCompact) 2.dp else 16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = currentServerName.take(12),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.White,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                val serverCat = if (extensionServers.isNotEmpty()) catFromName(currentServerName) else currentCategory.uppercase()
                                                Text(
                                                    text = serverCat,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.Gray
                                                )
                                            }
                                        }

                                        DropdownMenu(
                                            expanded = showServerMenu,
                                            onDismissRequest = { showServerMenu = false },
                                            modifier = Modifier.background(Color(0xFF1A1A1A)).width(180.dp)
                                        ) {
                                            val headerCat = if (extensionServers.isNotEmpty()) catFromName(currentServerName) else currentCategory.uppercase()
                                            Text(
                                                text = "${currentServerName.uppercase()} ($headerCat)",
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.labelMedium,
                                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                            )
                                            if (extensionServers.isNotEmpty()) {
                                                val extSubServers = extensionServers.filter { it.name.contains("sub", ignoreCase = true) || !it.name.contains("dub", ignoreCase = true) }
                                                val extDubServers = extensionServers.filter { it.name.contains("dub", ignoreCase = true) && !it.name.contains("sub", ignoreCase = true) }
                                                if (extSubServers.isNotEmpty()) {
                                                    Text("SUB", color = Color.Gray, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                                                     extSubServers.forEach { server ->
                                                        ServerSelectorButton(
                                                            serverName = server.name,
                                                            isSelected = server.name == currentServerName,
                                                            onClick = {
                                                                showServerMenu = false
                                                                autoRetryServers.clear()
                                                                pendingAutoRetry = null
                                                                engine.stop()
                                                                engine.clearMediaItems()
                                                                onExtensionServerChange?.invoke(server.name)
                                                            }
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                }
                                                if (extDubServers.isNotEmpty()) {
                                                    Text("DUB", color = Color.Gray, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                                                     extDubServers.forEach { server ->
                                                        ServerSelectorButton(
                                                            serverName = server.name,
                                                            isSelected = server.name == currentServerName,
                                                            onClick = {
                                                                showServerMenu = false
                                                                autoRetryServers.clear()
                                                                pendingAutoRetry = null
                                                                engine.stop()
                                                                engine.clearMediaItems()
                                                                onExtensionServerChange?.invoke(server.name)
                                                            }
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                }
                                                if (extSubServers.isEmpty() && extDubServers.isEmpty()) {
                                                    Text(extensionName.ifEmpty { "EXT" }, color = Color.Gray, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                                                     extensionServers.forEach { server ->
                                                        ServerSelectorButton(
                                                            serverName = server.name,
                                                            isSelected = server.name == currentServerName,
                                                            onClick = {
                                                                showServerMenu = false
                                                                autoRetryServers.clear()
                                                                pendingAutoRetry = null
                                                                engine.stop()
                                                                engine.clearMediaItems()
                                                                onExtensionServerChange?.invoke(server.name)
                                                            }
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                }
                                            }
                                            if (subServers.isNotEmpty()) {
                                                Text("SUB", color = Color.Gray, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                                                subServers.forEach { server ->
                                                    ServerSelectorButton(
                                                        serverName = server.name,
                                                        isSelected = server.name == currentServerName && currentCategory == "sub",
                                                        onClick = {
                                                            showServerMenu = false
                                                            handleServerChange(server.name, "sub")
                                                        }
                                                    )
                                                }
                                            }
                                            if (dubServers.isNotEmpty()) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text("DUB", color = Color.Gray, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                                                dubServers.forEach { server ->
                                                    ServerSelectorButton(
                                                        serverName = server.name,
                                                        isSelected = server.name == currentServerName && currentCategory == "dub",
                                                        onClick = {
                                                            showServerMenu = false
                                                            handleServerChange(server.name, "dub")
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // CC/Subtitles button
                                val hasExternalSubs = subtitleTracks.isNotEmpty() || subtitleUrl != null
                                val hasEmbeddedSubs = embeddedSubtitleTracks.isNotEmpty()
                                if (!isCompact && (hasExternalSubs || hasEmbeddedSubs)) {
                                    Box {
                                        Surface(
                                            shape = RoundedCornerShape(if (isCompact) 10.dp else 14.dp),
                                            color = if (isCompact) Color.Transparent else Color.Black.copy(alpha = 0.5f),
                                            onClick = { showSubtitleMenu = true }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = if (isCompact) 6.dp else 12.dp, vertical = if (isCompact) 2.dp else 10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                Icon(
                                                    Icons.Filled.ClosedCaption,
                                                    contentDescription = "Subtitles",
                                                    tint = if (subtitlesEnabled) Color.White else Color.Gray.copy(alpha = 0.5f),
                                                    modifier = Modifier.size(if (isCompact) 16.dp else 20.dp)
                                                )
                                            }
                                        }

                                        var subtitleSettingsView by remember(showSubtitleMenu) { mutableStateOf(false) }

                                        DropdownMenu(
                                            expanded = showSubtitleMenu,
                                            onDismissRequest = { showSubtitleMenu = false },
                                            modifier = Modifier.background(Color(0xFF1A1A1A)).width(180.dp)
                                        ) {
                                            if (subtitleSettingsView) {
                                                Text(
                                                    "Profiles",
                                                    color = Color.Gray,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                                )
                                                subtitleProfileData.profiles.forEachIndexed { index, profile ->
                                                    val isActive = index == subtitleProfileData.activeProfileIndex
                                                    DropdownMenuItem(
                                                        text = { Text(profile.profileName, color = if (isActive) MaterialTheme.colorScheme.primary else Color.White) },
                                                        onClick = {
                                                            val data = subtitleProfileData
                                                            saveSubtitleProfileData(data.copy(activeProfileIndex = index))
                                                            subtitleSettingsView = false
                                                            showSubtitleMenu = false
                                                        },
                                                        leadingIcon = if (isActive) { { Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) } } else null
                                                    )
                                                }
                                                HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f))
                                                DropdownMenuItem(
                                                    text = {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Icon(Icons.Default.Settings, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                                            Spacer(Modifier.width(8.dp))
                                                            Text("Edit Subtitles", color = Color.White)
                                                        }
                                                    },
                                                    onClick = {
                                                        showSubtitleMenu = false
                                                        subtitleSettingsView = false
                                                        showSubtitleSettings = true
                                                    }
                                                )
                                            } else {
                                                DropdownMenuItem(
                                                    text = { Text("Off", color = if (!subtitlesEnabled) MaterialTheme.colorScheme.primary else Color.White) },
                                                    onClick = {
                                                        if (subtitlesEnabled) rebuildWithSubtitles(false)
                                                        selectedEmbeddedTrackIndex = -1
                                                        engine.disableSubtitles()
                                                        showSubtitleMenu = false
                                                    },
                                                    leadingIcon = if (!subtitlesEnabled) { { Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) } } else null
                                                )
                                                val externalTrackList = subtitleTracks.ifEmpty {
                                                    if (subtitleUrl != null) listOf(eu.kanade.tachiyomi.animesource.model.Track(subtitleUrl, "en"))
                                                    else emptyList()
                                                }
                                                if (externalTrackList.isNotEmpty()) {
                                                    Text("External", color = Color.Gray, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                                                    externalTrackList.forEachIndexed { index, track ->
                                                        val isSelected = subtitlesEnabled && selectedSubtitleIndex == index && selectedEmbeddedTrackIndex < 0
                                                        DropdownMenuItem(
                                                            text = { Text(track.lang.uppercase(), color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White) },
                                                            onClick = {
                                                                selectedSubtitleIndex = index
                                                                selectedEmbeddedTrackIndex = -1
                                                                rebuildWithSubtitles(true)
                                                                showSubtitleMenu = false
                                                            },
                                                            leadingIcon = if (isSelected) { { Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) } } else null
                                                        )
                                                    }
                                                }
                                                if (embeddedSubtitleTracks.isNotEmpty()) {
                                                    if (externalTrackList.isNotEmpty()) {
                                                        HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f))
                                                    }
                                                    Text("Embedded", color = Color.Gray, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                                                    embeddedSubtitleTracks.forEachIndexed { index, track ->
                                                        val isSelected = subtitlesEnabled && selectedEmbeddedTrackIndex == index
                                                        DropdownMenuItem(
                                                            text = { Text(track.label, color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White) },
                                                            onClick = {
                                                                engine.overrideSubtitleTrack(track.trackIndex, 0)
                                                                Log.d("PlayerScreen", "selectEmbeddedSubtitle: selected '${track.label}' idx=${track.trackIndex}")
                                                                selectedEmbeddedTrackIndex = index
                                                                subtitlesEnabled = true
                                                                showSubtitleMenu = false
                                                            },
                                                            leadingIcon = if (isSelected) { { Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) } } else null
                                                        )
                                                    }
                                                }
                                                HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f))
                                                DropdownMenuItem(
                                                    text = {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Icon(Icons.Default.Settings, null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                                                            Spacer(Modifier.width(6.dp))
                                                            Text("Settings", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                                                        }
                                                    },
                                                    onClick = { subtitleSettingsView = true }
                                                )
                                            }
                                        }
                                    }
                                }

                                // Resize button
                                if (!isCompact) {
                                    ResizeButton(
                                        onClick = { resizeModeIndex = (resizeModeIndex + 1) % resizeModes.size },
                                    )
                                }

                                // Player settings button
                                PlayerSettingsButton(
                                    showMenu = showPlayerSettings,
                                    onShowMenuChange = { showPlayerSettings = it },
                                    swipeVolume = swipeVolume,
                                    swipeBrightness = swipeBrightness,
                                    swipeSwap = swipeSwap,
                                    onSwipeVolumeChange = { onSwipeVolumeChange?.invoke(it) },
                                    onSwipeBrightnessChange = { onSwipeBrightnessChange?.invoke(it) },
                                    onSwipeSwapChange = { onSwipeSwapChange?.invoke(it) },
                                    isCompact = isCompact,
                                    autoPlayNextEpisode = autoPlayNextEpisode,
                                    onAutoPlayChange = { onAutoPlayNextEpisodeChanged?.invoke(it) },
                                    supportsPiP = supportsPiP,
                                    onPiPToggle = { onPiPToggle?.invoke(it) },
                                )

                            }
                        }
                    }
                }

                Box(modifier = Modifier.align(Alignment.Center)) {
                    AnimatedVisibility(
                        visible = controlsVisible,
                        enter = scaleIn(initialScale = 0.8f, animationSpec = tween(200)),
                        exit = scaleOut(targetScale = 0.8f, animationSpec = tween(100)),
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalArrangement = Arrangement.spacedBy(if (isCompact) 32.dp else 40.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { onPreviousEpisode?.invoke() },
                                modifier = Modifier.size(if (isCompact) 40.dp else 56.dp).then(if (!isCompact) Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape) else Modifier).alpha(if (onPreviousEpisode != null && !isLoadingStream && !isChangingServer) 1f else 0.3f),
                                enabled = onPreviousEpisode != null && !isLoadingStream && !isChangingServer
                            ) {
                                Icon(Icons.Default.SkipPrevious, "Previous Episode", tint = Color.White, modifier = Modifier.size(if (isCompact) 24.dp else 32.dp))
                            }

                            IconButton(
                                onClick = {
                                    if (hasError) {
                                        handlePlaybackError()
                                        engine.prepare()
                                        engine.playWhenReady = true
                                    } else {
                                        if (isPlaying) engine.pause() else engine.play()
                                    }
                                },
                                modifier = Modifier.size(if (isCompact) 52.dp else 72.dp).then(if (!isCompact) Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape) else Modifier)
                            ) {
                                if (isBuffering || isOffline) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        modifier = Modifier.size(if (isCompact) 28.dp else 42.dp),
                                        strokeWidth = 3.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (hasError) Icons.Default.Refresh else if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (hasError) "Retry" else if (isPlaying) "Pause" else "Play",
                                        tint = Color.White,
                                        modifier = Modifier.size(if (isCompact) 30.dp else 42.dp)
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    onNextEpisode?.invoke()
                                },
                                modifier = Modifier.size(if (isCompact) 40.dp else 56.dp).then(if (!isCompact) Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape) else Modifier).alpha(if (onNextEpisode != null && !isLatestEpisode && !isLoadingStream && !isChangingServer) 1f else 0.3f),
                                enabled = onNextEpisode != null && !isLatestEpisode && !isLoadingStream && !isChangingServer
                            ) {
                                Icon(Icons.Default.SkipNext, "Next Episode", tint = Color.White, modifier = Modifier.size(if (isCompact) 24.dp else 32.dp))
                            }
                        }
                    }

                    SkipIndicatorOverlay(
                        visible = showSkipIndicator && !skipIsForward,
                        isForward = false,
                        text = skipIndicatorText,
                        modifier = Modifier.align(Alignment.CenterStart).offset(x = (-120).dp),
                    )

                    SkipIndicatorOverlay(
                        visible = showSkipIndicator && skipIsForward,
                        isForward = true,
                        text = skipIndicatorText,
                        modifier = Modifier.align(Alignment.CenterEnd).offset(x = 120.dp),
                    )
                }

                if (isLoadingStream || isChangingServer) {
                    PlayerLoadingIndicator(modifier = Modifier.align(Alignment.Center).offset(y = 64.dp))
                }

                if (hasError && playbackError != null) {
                    val hasMoreServers = if (extensionServers.isNotEmpty()) {
                        extensionServers.any { it.name !in autoRetryServers && it.url != videoUrl }
                    } else {
                        onServerChange != null && let {
                            val servers = if (currentCategory == "sub") subServers else dubServers
                            servers.size > 1
                        }
                    }
                    StreamErrorOverlay(
                        errorMessage = playbackError ?: "Unknown error",
                        showTryNextServer = hasMoreServers,
                        onTryNextServer = {
                            if (extensionServers.isNotEmpty()) {
                                autoRetryServers.add(currentServerName)
                                extensionServers.find { it.url == videoUrl }?.let { autoRetryServers.add(it.name) }
                                fun srvCat2(name: String): String = when {
                                    name.contains("dub", ignoreCase = true) -> "dub"
                                    name.contains("sub", ignoreCase = true) -> "sub"
                                    else -> "other"
                                }
                                val curCat = srvCat2(currentServerName)
                                val remaining = extensionServers.filter {
                                    it.name !in autoRetryServers && it.url != videoUrl
                                }
                                val sameCat = remaining.filter { srvCat2(it.name) == curCat }
                                val otherCat = remaining.filter { srvCat2(it.name) != curCat }
                                val next = (sameCat + otherCat).firstOrNull()
                                if (next != null) {
                                    pendingAutoRetry = next.name
                                }
                            } else {
                                val servers = if (currentCategory == "sub") subServers else dubServers
                                val currentIndex = servers.indexOfFirst { it.name == currentServerName }
                                val nextIndex = (currentIndex + 1) % servers.size
                                handleServerChange(servers[nextIndex].name, currentCategory)
                            }
                        },
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                // Bottom gradient - slides from bottom
                AnimatedVisibility(
                    visible = controlsVisible,
                    enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(200)),
                    exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(100)),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                            .then(if (isCompact) Modifier else Modifier.navigationBarsPadding())
                            .pointerInput(Unit) { detectTapGestures(onTap = { showControls = !showControls }) }
                            .padding(horizontal = if (isCompact) 10.dp else 16.dp)
                            .padding(bottom = if (isCompact) 2.dp else 12.dp, top = if (isCompact) 12.dp else 12.dp)
                    ) {
                        // Timer above progress bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(formatTime(currentPosition), color = Color.White, style = if (isCompact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium)
                            Text(if (duration > 0) formatTime(duration) else "--:--", color = Color.White, style = if (isCompact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium)
                        }

                        Spacer(modifier = Modifier.height(if (isCompact) 2.dp else 4.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (isCompact) 14.dp else 24.dp)
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = { offset ->
                                            if (duration > 0) {
                                                val ratio = (offset.x / size.width).coerceIn(0f, 1f)
                                                val seekPosition = (ratio * duration).toLong()
                                                performManualSeek(seekPosition)
                                            }
                                        }
                                    )
                                }
                                .pointerInput(Unit) {
                                    detectHorizontalDragGestures(
                                        onDragStart = { offset ->
                                            isDragging = true
                                            isManuallySeeking = true
                                            wasPlayingBeforeScrub = isPlaying
                                            val ratio = (offset.x / size.width).coerceIn(0f, 1f)
                                            sliderValue = ratio * (if (duration > 0) duration.toFloat() else 1000f)
                                            currentPosition = sliderValue.toLong()
                                        },
                                        onDragEnd = {
                                            isDragging = false
                                            val seekPos = sliderValue.toLong()
                                            Log.d("PlayerScreen", "onDragEnd: seekPos=$seekPos bufferedPos=$bufferedPosition duration=$duration")
                                            seekToPosition(seekPos)
                                            skipResetJob?.cancel()
                                            skipResetJob = scope.launch {
                                                delay(1500.milliseconds)
                                                isManuallySeeking = false
                                            }
                                        },
                                        onHorizontalDrag = { _, dragAmount ->
                                            val currentRatio = sliderValue / (if (duration > 0) duration.toFloat() else 1000f)
                                            val newRatio = (currentRatio + dragAmount / size.width).coerceIn(0f, 1f)
                                            sliderValue = newRatio * (if (duration > 0) duration.toFloat() else 1000f)
                                            currentPosition = sliderValue.toLong()
                                        }
                                    )
                                }
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val sliderWidth = size.width
                                val trackHeight = if (isCompact) 3.dp.toPx() else 5.dp.toPx()
                                val trackTop = (size.height - trackHeight) / 2f
                                val cornerRadius = if (isCompact) 1.5.dp.toPx() else 2.5.dp.toPx()
                                val thumbRadiusPx = if (isCompact) 5.dp.toPx() else 7.dp.toPx()

                                if (duration > 0) {
                                    val progressRatio = currentPosition.toFloat() / duration
                                    val bufferedRatio = maxBufferedPosition.toFloat() / duration

                                    // Draw inactive track background
                                    drawRoundRect(
                                        color = Color.White.copy(alpha = 0.3f),
                                        topLeft = Offset(0f, trackTop),
                                        size = Size(sliderWidth, trackHeight),
                                        cornerRadius = CornerRadius(cornerRadius)
                                    )

                                    // Draw buffer indicator
                                    val bufferWidth = (bufferedRatio - progressRatio) * sliderWidth
                                    if (showBufferIndicator && bufferWidth >= 2.dp.toPx()) {
                                        val bufferStartX = progressRatio * sliderWidth
                                        val bufferEndX = bufferedRatio * sliderWidth
                                        drawRoundRect(
                                            color = Color.White.copy(alpha = 0.5f),
                                            topLeft = Offset(bufferStartX, trackTop),
                                            size = Size(bufferEndX - bufferStartX, trackHeight),
                                            cornerRadius = CornerRadius(2.dp.toPx())
                                        )
                                    }

                                    // Draw active track (played portion)
                                    val progressX = progressRatio * sliderWidth
                                    drawRoundRect(
                                        color = Color.White,
                                        topLeft = Offset(0f, trackTop),
                                        size = Size(progressX.coerceAtLeast(thumbRadiusPx), trackHeight),
                                        cornerRadius = CornerRadius(cornerRadius)
                                    )

                                    // Draw intro/credits markers with manual color blending
                                    // Colors calculated to match BlendMode.Multiply result:
                                    // - Watched portion: solid orange (multiply with white)
                                    // - Unwatched portion: darker orange (multiply with gray background)
                                    val watchedOrange = Color(0xFFFF9800)
                                    val unwatchedOrange = Color(0xFFA67C00)

                                    if (introStartRatio != null && introEndRatio != null) {
                                        val introStartX = introStartRatio * sliderWidth
                                        val introEndX = introEndRatio * sliderWidth
                                        val introWidth = introEndX - introStartX
                                        if (introWidth > 0) {
                                            val leftRadius = if (introStartX < 10f) cornerRadius else 2.dp.toPx()
                                            val rightRadius = if (introEndX > sliderWidth - 10f) cornerRadius else 2.dp.toPx()

                                            // Draw watched part (before progress) with bright orange
                                            if (introStartX < progressX && introEndX <= progressX) {
                                                drawRoundRect(
                                                    color = watchedOrange,
                                                    topLeft = Offset(introStartX, trackTop),
                                                    size = Size(introWidth, trackHeight),
                                                    cornerRadius = CornerRadius(cornerRadius, cornerRadius)
                                                )
                                            }
                                            // Draw unwatched part (after progress) with dark orange
                                            else if (introStartX >= progressX) {
                                                drawRoundRect(
                                                    color = unwatchedOrange,
                                                    topLeft = Offset(introStartX.coerceAtLeast(0f), trackTop),
                                                    size = Size(
                                                        introWidth.coerceAtMost(sliderWidth - introStartX.coerceAtLeast(0f)),
                                                        trackHeight
                                                    ),
                                                    cornerRadius = CornerRadius(leftRadius, rightRadius)
                                                )
                                            }
                                            // Draw both parts (spans across progress)
                                            else if (introStartX < progressX && introEndX > progressX) {
                                                val watchedWidth = progressX - introStartX
                                                val unwatchedWidth = introEndX - progressX
                                                drawRoundRect(
                                                    color = watchedOrange,
                                                    topLeft = Offset(introStartX, trackTop),
                                                    size = Size(watchedWidth, trackHeight),
                                                    cornerRadius = CornerRadius(cornerRadius, cornerRadius)
                                                )
                                                drawRoundRect(
                                                    color = unwatchedOrange,
                                                    topLeft = Offset(progressX, trackTop),
                                                    size = Size(unwatchedWidth, trackHeight),
                                                    cornerRadius = CornerRadius(cornerRadius, cornerRadius)
                                                )
                                            }
                                        }
                                    }

                                    if (creditsStartRatio != null) {
                                        val creditsStartX = creditsStartRatio * sliderWidth
                                        if (creditsStartX < sliderWidth && creditsStartX > 0) {
                                            val creditsColor = if (creditsStartX < progressX) watchedOrange else unwatchedOrange
                                            drawRoundRect(
                                                color = creditsColor,
                                                topLeft = Offset(creditsStartX, trackTop),
                                                size = Size((sliderWidth - creditsStartX).coerceAtLeast(0f), trackHeight),
                                                cornerRadius = CornerRadius(cornerRadius, cornerRadius)
                                            )
                                        }
                                    }

                                    // Draw the thumb as a circle
                                    drawCircle(
                                        color = Color.White,
                                        radius = thumbRadiusPx,
                                        center = Offset(progressX, size.height / 2)
                                    )
                                }
                            }
                        }

                        // Remaining time
                        if (duration > 0 && !isCompact) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Text(
                                    text = "-${formatTime((duration - currentPosition).coerceAtLeast(0L))}",
                                    color = Color.White.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }

                        // Bottom row with speed selector on left and time on right
                        var currentSpeed by rememberSaveable { mutableFloatStateOf(1f) }
                        val speedOptions = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = if (isCompact) 2.dp else 0.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Playback speed selector on the left
                            PlaybackSpeedSelector(
                                currentSpeed = currentSpeed,
                                showMenu = showSpeedMenu,
                                onShowMenuChange = { showSpeedMenu = it },
                                onSpeedChange = { speed ->
                                    currentSpeed = speed
                                    engine.setPlaybackSpeed(speed)
                                },
                                isCompact = isCompact,
                            )

                            // Autoplay + Fullscreen (linked)
                            if (isCompact) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color.Transparent,
                                    onClick = { toggleFullscreen() }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                            contentDescription = if (isFullscreen) "Exit Fullscreen" else "Fullscreen",
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            } else {
                                AutoplayFullscreenRow(
                                    autoPlayNextEpisode = autoPlayNextEpisode,
                                    isFullscreen = isFullscreen,
                                    onAutoPlayChange = { onAutoPlayNextEpisodeChanged?.invoke(it) },
                                    onFullscreenToggle = { toggleFullscreen() },
                                    isCompact = isCompact,
                                )
                            }
                        }
                        if (isCompact) {
                            Spacer(modifier = Modifier.height(2.dp))
                        }
                    }
                }
            }
        }

        // Skip Opening/Ending buttons - outside controls AnimatedVisibility so they're visible even when UI is hidden
        SkipButtonsOverlay(
            showSkipOpening = showSkipOpeningButton,
            showSkipEnding = showSkipEndingButton,
            isLatestEpisode = isLatestEpisode,
            creditsAtEnd = creditsAtEnd,
            isChangingServer = isChangingServer,
            onSkipOpening = {
                val ts = effectiveTimestamps
                if (ts.introEnd != null) {
                    engine.seekTo(ts.introEnd * 1000L)
                    if (engine.isPlaying) {
                        engine.play()
                    }
                    hasSkippedIntro = true
                }
            },
            onSkipEnding = {
                if (isLatestEpisode || !creditsAtEnd) {
                    if (engine.duration > 0) {
                        engine.seekTo(engine.duration)
                    }
                } else if (!isChangingServer) {
                    onNextEpisode?.invoke()
                }
            },
            isCompact = isCompact,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = if (isCompact) 8.dp else 16.dp),
        )

        // Volume Overlay Indicator (on top of controls)
        // When swipeSwap is true, volume shows on the right; otherwise left
        VolumeOverlay(
            visible = showVolumeOverlay,
            volume = playerVolume,
            disableMaterialColors = disableMaterialColors,
            modifier = Modifier.align(if (swipeSwap) Alignment.CenterEnd else Alignment.CenterStart),
        )

        // Brightness Overlay Indicator (on top of controls)
        // When swipeSwap is true, brightness shows on the left; otherwise right
        BrightnessOverlay(
            visible = showBrightnessOverlay,
            brightness = currentBrightness,
            disableMaterialColors = disableMaterialColors,
            modifier = Modifier.align(if (swipeSwap) Alignment.CenterStart else Alignment.CenterEnd),
        )

        // Subtitle Settings full-screen overlay
        if (showSubtitleSettings) {
            SubtitleSettingsDialog(
                currentSettings = getActiveSubtitleSettings(),
                profiles = subtitleProfileData.profiles,
                activeProfileIndex = subtitleProfileData.activeProfileIndex,
                onSettingsChange = { newSettings ->
                    val data = subtitleProfileData
                    val updatedProfiles = data.profiles.toMutableList().also {
                        it[data.activeProfileIndex] = newSettings
                    }
                    saveSubtitleProfileData(data.copy(profiles = updatedProfiles))
                },
                onProfileSelect = { index ->
                    val data = subtitleProfileData
                    saveSubtitleProfileData(data.copy(activeProfileIndex = index))
                },
                onResetProfile = { index ->
                    val data = subtitleProfileData
                    val updatedProfiles = data.profiles.toMutableList().also {
                        it[index] = SubtitleSettings(profileName = "Profile ${index + 1}")
                    }
                    saveSubtitleProfileData(data.copy(profiles = updatedProfiles))
                },
                onRenameProfile = { index, name ->
                    val data = subtitleProfileData
                    val updated = data.profiles[index].copy(profileName = name)
                    val updatedProfiles = data.profiles.toMutableList().also {
                        it[index] = updated
                    }
                    saveSubtitleProfileData(data.copy(profiles = updatedProfiles))
                },
                onDismiss = { showSubtitleSettings = false },
                onSave = {
                    saveSubtitleProfileData(subtitleProfileData)
                }
            )
        }
    }
}

// loadSubtitleProfileData and applySubtitleStyle moved to SubtitleStyleHelpers.kt

