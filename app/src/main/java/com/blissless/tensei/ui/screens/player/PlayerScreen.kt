package com.blissless.tensei.ui.screens.player

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.blissless.tensei.api.AnimeSkipService
import com.blissless.tensei.data.models.EpisodeStreams
import com.blissless.tensei.data.models.EpisodeTimestamps
import com.blissless.tensei.data.models.ServerInfo
import com.blissless.tensei.data.models.SubtitleProfileData
import com.blissless.tensei.data.models.SubtitleSettings
import com.blissless.tensei.data.models.Timestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds
import androidx.core.content.edit

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
    currentQuality: String = "Auto",
    isLatestEpisode: Boolean = false,
    disableMaterialColors: Boolean = false,
    showBufferIndicator: Boolean = true,
    bufferAheadSeconds: Int = 30,
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
    extensionOkHttpClient: okhttp3.OkHttpClient? = null,
    extensionVideoHeaders: Map<String, String> = emptyMap(),
    extensionServers: List<ServerInfo> = emptyList(),
    extensionName: String = "",
    onExtensionServerChange: ((hosterName: String) -> Unit)? = null,
    onPrefetchNextExtensionEpisode: (() -> Unit)? = null,
    onAutoPlayNextEpisodeChanged: ((Boolean) -> Unit)? = null,
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

    var resizeModeIndex by remember { mutableIntStateOf(0) }
    val resizeModes = listOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT to "Fit",
        AspectRatioFrameLayout.RESIZE_MODE_FILL to "Stretch",
        AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH to "16:9"
    )

    var isFullscreen by remember { mutableStateOf(true) }

    // Handle fullscreen toggle
    fun toggleFullscreen() {
        isFullscreen = !isFullscreen
        activity?.let { act ->
            if (isFullscreen) {
                @Suppress("DEPRECATION") act.window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                @Suppress("DEPRECATION") act.window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    // Exit fullscreen when closing
    fun exitFullscreen() {
        if (isFullscreen) {
            isFullscreen = false
            activity?.let { act ->
                @Suppress("DEPRECATION") act.window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
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
    var subtitlesEnabled by remember { mutableStateOf(subtitleTracks.isNotEmpty()) }
    var selectedSubtitleIndex by remember { mutableIntStateOf(0) }
    var showSubtitleSettings by remember { mutableStateOf(false) }
    var subtitleProfileData by remember { mutableStateOf(loadSubtitleProfileData(context)) }
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

    LaunchedEffect(showControls, hasError, showSkipIndicator) {
        controlsVisible = showControls || hasError || showSkipIndicator
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

    LaunchedEffect(Unit) {
        activity?.window?.let { window ->
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            WindowCompat.setDecorFitsSystemWindows(window, false)
        }
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    DisposableEffect(Unit) {
        onDispose {
            onSavePosition?.invoke(currentPosition, duration)
            activity?.window?.let { window ->
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
                WindowCompat.setDecorFitsSystemWindows(window, true)
            }
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    val exoPlayer = remember(context, bufferAheadSeconds, referer, serverChangeTrigger, videoUrl, extensionOkHttpClient, extensionVideoHeaders) {
        val bufferAheadMs = bufferAheadSeconds * 1000
        val maxBufferMs = maxOf(bufferAheadMs + 60000, 180000)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(bufferAheadMs, maxBufferMs, 1500, 3000)
            .build()

        val cacheDataSourceFactory = onGetCacheDataSourceFactory(referer)

        val upstreamFactory = if (extensionOkHttpClient != null && extensionVideoHeaders.isNotEmpty()) {
            Log.d("PlayerScreen", "Using extension OkHttpClient with headers: $extensionVideoHeaders")
            val okHttpFactory = androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(extensionOkHttpClient)
            okHttpFactory.setDefaultRequestProperties(extensionVideoHeaders)
            okHttpFactory
        } else if (extensionOkHttpClient != null) {
            Log.d("PlayerScreen", "Using extension OkHttpClient with Referer: $referer")
            androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(extensionOkHttpClient)
                .setDefaultRequestProperties(mapOf("Referer" to referer))
        } else {
            Log.d("PlayerScreen", "Using default DataSource with Referer: $referer")
            DefaultHttpDataSource.Factory()
                .setConnectTimeoutMs(20000)
                .setReadTimeoutMs(20000)
                .setDefaultRequestProperties(mapOf("Referer" to referer))
        }

        val dataSourceFactory = cacheDataSourceFactory ?: upstreamFactory

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory)
            )
            .setLoadControl(loadControl)
            .build()
            .apply {
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        isPlaying = playing
                        if (playing) {
                            hasPlaybackStarted = true
                        }
                    }

                    override fun onPlaybackSuppressionReasonChanged(reason: Int) {
                        isBuffering = isPlaying && reason != Player.PLAYBACK_SUPPRESSION_REASON_NONE
                    }

                    override fun onPlayerError(error: PlaybackException) {
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

                        // Auto-retry for manual seeks: retry up to 2 times with seekPlayerTo.
                        // After exhaustion, auto-refresh the stream URL (the URL may have expired).
                        if (isManuallySeeking && seekRetryCount < 2) {
                            seekRetryCount++
                            hasError = false
                            playbackError = null
                            isBuffering = true
                            val seekPos = currentPosition
                            if (seekPos > 0) {
                                val wasPlaying = playWhenReady
                                val currentItem = currentMediaItem
                                if (currentItem != null) {
                                    stop()
                                    setMediaItem(currentItem, seekPos)
                                    prepare()
                                    playWhenReady = wasPlaying
                                }
                            } else {
                                prepare()
                            }
                            return
                        }
                        if (isManuallySeeking && onRefreshStream != null) {
                            onInvalidateStreamCache?.invoke()
                            onRefreshStream.invoke()
                            return
                        }

                        // Auto-refresh for initial load / re-entry failure (stale cached URL).
                        // isAutoRefreshing in MainActivity prevents infinite refreshes.
                        if (isInitialLoading && onRefreshStream != null) {
                            onInvalidateStreamCache?.invoke()
                            onRefreshStream.invoke()
                        }

                        hasError = true
                        playbackError = "${error.errorCode}: ${error.message ?: "Unknown"}"
                        showControls = true
                        Log.e("PlayerScreen", "Playback error: code=${error.errorCode} msg=${error.message} videoUrl=${videoUrl.take(120)}")
                        Log.e("PlayerScreen", "  cause=${error.cause?.message}")
                        error.cause?.let { cause ->
                            Log.e("PlayerScreen", "  cause type=${cause::class.simpleName}")
                            cause.stackTrace.take(5).forEach { frame ->
                                Log.e("PlayerScreen", "    at $frame")
                            }
                        }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        isBuffering = playbackState == Player.STATE_BUFFERING
                        if (playbackState == Player.STATE_READY) {
                            hasError = false
                            playbackError = null
                            isChangingServer = false
                            isBuffering = false
                            hasPlaybackStarted = true
                            isInitialLoading = false
                            if (pendingQualityChange != null && savedPositionForQuality > 0) {
                                val wasPlaying = playWhenReady
                                val currentItem = currentMediaItem
                                if (currentItem != null) {
                                    stop()
                                    setMediaItem(currentItem, savedPositionForQuality)
                                    prepare()
                                    playWhenReady = wasPlaying
                                }
                                pendingQualityChange = null
                                savedPositionForQuality = 0L
                            }
                        }
                        if (playbackState == Player.STATE_ENDED) {
                            onClearPlaybackPosition?.invoke(animeId, currentEpisode)
                            if (autoPlayNextEpisode && onNextEpisode != null && !isChangingServer) {
                                if (isLatestEpisode) {
                                    Toast.makeText(context, "Latest episode watched", Toast.LENGTH_SHORT).show()
                                } else {
                                    onNextEpisode.invoke()
                                }
                            }
                        }
                    }
                })
            }
    }

    LaunchedEffect(videoUrl, serverChangeTrigger) {
        hasError = false
        playbackError = null
        hasRestoredPosition = false
        hasSkippedIntro = false
        hasSkippedOutro = false
        hasTriggeredPrefetch = false
        isChangingServer = false
        hasPlaybackStarted = false
        bufferedPosition = 0L
        maxBufferedPosition = 0L
        isOffline = false
        seekRetryCount = 0
        isInitialLoading = true

        exoPlayer.stop()
        delay(100.milliseconds)
        exoPlayer.clearMediaItems()

        val startPositionMs = if (savedPosition > 0) savedPosition else 0L

        val subtitleConfigs = if (subtitlesEnabled && subtitleTracks.isNotEmpty()) {
            subtitleTracks.mapIndexed { index, track ->
                val flags = if (index == selectedSubtitleIndex) C.SELECTION_FLAG_DEFAULT else 0
                MediaItem.SubtitleConfiguration.Builder(track.url.toUri())
                    .setMimeType(MimeTypes.TEXT_VTT)
                    .setLanguage(track.lang)
                    .setSelectionFlags(flags)
                    .build()
            }
        } else if (subtitleUrl != null) {
            listOf(MediaItem.SubtitleConfiguration.Builder(subtitleUrl.toUri())
                .setMimeType(MimeTypes.TEXT_VTT)
                .setLanguage("en")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build())
        } else {
            emptyList()
        }

        Log.d("PlayerScreen", "Preparing playback: videoUrl=${videoUrl.take(120)} referer=$referer subtitleUrl=${subtitleUrl?.take(80)} extensionOkHttpClient=${extensionOkHttpClient != null} videoHeaders=$extensionVideoHeaders")
        val mimeType = if (videoUrl.contains(".m3u8") || videoUrl.contains("/m3u8")) MimeTypes.APPLICATION_M3U8
        else if (videoUrl.contains(".mp4")) MimeTypes.VIDEO_MP4
        else if (videoUrl.contains(".webm")) MimeTypes.VIDEO_WEBM
        else {
            Log.d("PlayerScreen", "Unknown mime type for URL: ${videoUrl.take(100)}, defaulting to MP4")
            MimeTypes.VIDEO_MP4
        }

        val mediaItem = MediaItem.Builder()
            .setUri(videoUrl)
            .setMimeType(mimeType)
            .setSubtitleConfigurations(subtitleConfigs)
            .build()

        if (savedPosition > 0) {
            exoPlayer.setMediaItem(mediaItem, savedPosition)
        } else {
            exoPlayer.setMediaItem(mediaItem)
        }
        exoPlayer.prepare()

        hasPlaybackStarted = true

        hasTriggeredProgressUpdate = false
        currentPosition = startPositionMs
        sliderValue = startPositionMs.toFloat()
    }

    LaunchedEffect(exoPlayer.playbackState, hasRestoredPosition, videoUrl) {
        if (exoPlayer.playbackState == Player.STATE_READY && hasPlaybackStarted && !hasRestoredPosition) {
            hasRestoredPosition = true
            // Start playback after seek
            exoPlayer.playWhenReady = true
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
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    // Prefetch next extension episode on first playback
    LaunchedEffect(hasPlaybackStarted) {
        if (hasPlaybackStarted && extensionServers.isNotEmpty()) {
            onPrefetchNextExtensionEpisode?.invoke()
        }
    }

    /** Seek without byte-range requests — rebuilds player with a clip start position.
     *  Prevents error 2001 on proxy streams that don't support Range headers. */
    fun seekPlayerTo(position: Long) {
        val wasPlaying = exoPlayer.playWhenReady
        val currentItem = exoPlayer.currentMediaItem ?: return
        exoPlayer.stop()
        exoPlayer.setMediaItem(currentItem, position)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = wasPlaying
    }

    fun seekBy(milliseconds: Long) {
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
        // Use currentPosition (displayed position) instead of exoPlayer.currentPosition
        // because exoPlayer may still be seeking from a previous scrub and return stale value
        val duration = exoPlayer.duration
        val newPosition = if (duration > 0) {
            (currentPosition + milliseconds).coerceIn(0, duration)
        } else {
            (currentPosition + milliseconds).coerceAtLeast(0)
        }
        seekPlayerTo(newPosition)
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
        isManuallySeeking = true
        seekRetryCount = 0
        seekPlayerTo(position)
        currentPosition = position
        sliderValue = position.toFloat()
        skipResetJob?.cancel()
        skipResetJob = scope.launch {
            delay(1500.milliseconds)
            isManuallySeeking = false
        }
    }

    LaunchedEffect(exoPlayer, videoUrl) {
        while (true) {
            delay(500.milliseconds)
            if (!isDragging && !isManuallySeeking) {
                currentPosition = exoPlayer.currentPosition
                duration = exoPlayer.duration
                // Get buffered position from ExoPlayer
                bufferedPosition = exoPlayer.bufferedPosition
                // Track max buffer position to preserve buffer when scrubbing back
                if (bufferedPosition > maxBufferedPosition) {
                    maxBufferedPosition = bufferedPosition
                }
                if (duration > 0) {
                    sliderValue = currentPosition.toFloat()
                    if (actualEpisodeLength == null && duration > 60000 && exoPlayer.playbackState == Player.STATE_READY) {
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

    LaunchedEffect(currentPosition, effectiveTimestamps, hasError, isManuallySeeking, isChangingServer, isDragging) {
        if (hasError || isChangingServer || isDragging) return@LaunchedEffect

        val ts = effectiveTimestamps
        val posSeconds = currentPosition / 1000

        if (ts.introStart != null && ts.introEnd != null) {
            val isInIntro = posSeconds >= ts.introStart && posSeconds < ts.introEnd
            if (isInIntro) {
                if (autoSkipOpening && !hasSkippedIntro && !isManuallySeeking) {
                    exoPlayer.seekTo(ts.introEnd * 1000L)
                    hasSkippedIntro = true
                }
                showSkipOpeningButton = !autoSkipOpening
            } else {
                showSkipOpeningButton = false
            }
        }

        if (ts.creditsStart != null && onNextEpisode != null) {
            val isInCredits = posSeconds >= ts.creditsStart
            if (isInCredits) {
                if (autoSkipEnding && !hasSkippedOutro && !isManuallySeeking) {
                    if (isLatestEpisode) {
                        Toast.makeText(context, "Latest episode watched", Toast.LENGTH_SHORT).show()
                    } else {
                        onNextEpisode.invoke()
                    }
                    hasSkippedOutro = true
                }
                showSkipEndingButton = true // Always show skip ending button
            } else {
                showSkipEndingButton = false
            }
        }
    }

    LaunchedEffect(exoPlayer, hasTriggeredProgressUpdate) {
        while (!hasTriggeredProgressUpdate) {
            delay(1000.milliseconds)
            if (exoPlayer.playbackState == Player.STATE_READY && exoPlayer.duration > 0) {
                val percentage = ((exoPlayer.currentPosition.toFloat() / exoPlayer.duration) * 100).toInt()
                onProgressUpdate(percentage)
            }
        }
    }

    LaunchedEffect(showControls, isPlaying, isDragging, hasError, showServerMenu, showQualityMenu, showSpeedMenu, showSubtitleMenu, isManuallySeeking) {
        if (showControls && isPlaying && !isDragging && !hasError && !showServerMenu && !showQualityMenu && !showSpeedMenu && !showSubtitleMenu && !isManuallySeeking) {
            delay(2000.milliseconds)
            if (showControls && !isDragging && !hasError && isPlaying && !showServerMenu && !showSpeedMenu && !showSubtitleMenu && !isManuallySeeking) {
                showControls = false
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            exoPlayer.release()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                exoPlayer.pause()
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
        val currentDur = exoPlayer.duration
        onSavePosition?.invoke(exoPlayer.currentPosition, if (currentDur > 0) currentDur else 0L)
        onPositionSaved?.invoke(exoPlayer.currentPosition)

        // Stop and clear the current playback to prevent audio overlap
        exoPlayer.stop()
        exoPlayer.clearMediaItems()

        // Small delay before triggering server change to ensure error popup disappears
        scope.launch {
            delay(50.milliseconds)
            serverChangeTrigger++
        }
        onServerChange?.invoke(serverName, category)
    }

    fun handlePlaybackError() {
        onInvalidateStreamCache?.invoke()
        onPlaybackError?.invoke()
    }

    fun rebuildWithSubtitles(enable: Boolean) {
        subtitlesEnabled = enable
        val position = exoPlayer.currentPosition
        val playWhenReady = exoPlayer.playWhenReady
        val currentItem = exoPlayer.currentMediaItem ?: return
        val subtitleConfigs = if (subtitlesEnabled && subtitleTracks.isNotEmpty()) {
            subtitleTracks.mapIndexed { index, track ->
                val flags = if (index == selectedSubtitleIndex) C.SELECTION_FLAG_DEFAULT else 0
                MediaItem.SubtitleConfiguration.Builder(track.url.toUri())
                    .setMimeType(MimeTypes.TEXT_VTT)
                    .setLanguage(track.lang)
                    .setSelectionFlags(flags)
                    .build()
            }
        } else if (subtitlesEnabled && subtitleUrl != null) {
            listOf(MediaItem.SubtitleConfiguration.Builder(subtitleUrl.toUri())
                .setMimeType(MimeTypes.TEXT_VTT)
                .setLanguage("en")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build())
        } else {
            emptyList()
        }
        val newItem = currentItem.buildUpon()
            .setSubtitleConfigurations(subtitleConfigs)
            .build()
        exoPlayer.stop()
        exoPlayer.setMediaItem(newItem, position)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = playWhenReady
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
    ) {
        // PlayerView - recreate when server or subtitle profile changes
            key(serverChangeTrigger, subtitleProfileData) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        resizeMode = resizeModes[resizeModeIndex].first
                        useController = false
                        setShowNextButton(false)
                        setShowPreviousButton(false)
                        setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                        controllerShowTimeoutMs = 3000
                        controllerAutoShow = false

                        val activeSubSettings = getActiveSubtitleSettings()
                        subtitleView?.apply {
                            applySubtitleStyle(this, activeSubSettings)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxSize(),
                update = { view ->
                    view.resizeMode = resizeModes[resizeModeIndex].first
                    view.player = exoPlayer
                    val activeSubSettings = getActiveSubtitleSettings()
                    view.subtitleView?.apply {
                        applySubtitleStyle(this, activeSubSettings)
                    }
                }
            )
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
                .padding(start = 40.dp)
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
                                        exoPlayer.volume = playerVolume
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
                .padding(end = 40.dp)
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
                                        exoPlayer.volume = playerVolume
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

        // Bottom Padding (Matches side padding logic)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .align(Alignment.BottomCenter)
                .pointerInput(Unit) { detectTapGestures(onTap = {}) }
        )

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
                            .background(Brush.verticalGradient(colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
                            .statusBarsPadding()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                IconButton(
                                    onClick = {
                                        exitFullscreen()
                                        onBackClick?.invoke()
                                    },
                                    modifier = Modifier.size(40.dp).background(Color.Black.copy(alpha = 0.5f), shape = MaterialTheme.shapes.small)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    if (animeName.isNotEmpty()) {
                                        Text(
                                            text = animeName,
                                            color = Color.White,
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (!episodeTitle.isNullOrEmpty()) {
                                        Text(
                                            text = episodeTitle,
                                            color = Color.White.copy(alpha = 0.7f),
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "Episode $currentEpisode${if (totalEpisodes > 0) " / $totalEpisodes" else ""}",
                                            color = Color.White.copy(alpha = 0.8f),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        if (isFetchingTimestamps) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(14.dp),
                                                strokeWidth = 1.5.dp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        if (isChangingServer) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(14.dp),
                                                strokeWidth = 1.5.dp,
                                                color = if (disableMaterialColors) Color.White else MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }

                                // Closing Column for text content
                            }

                            fun catFromName(name: String): String = when {
                                name.contains("dub", ignoreCase = true) -> "DUB"
                                name.contains("sub", ignoreCase = true) -> "SUB"
                                extensionServers.isNotEmpty() -> extensionName.ifEmpty { "EXT" }
                                else -> "EXT"
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.width(IntrinsicSize.Max)) {
                                // Server selector
                                if ((onServerChange != null && (subServers.isNotEmpty() || dubServers.isNotEmpty())) || extensionServers.isNotEmpty()) {
                                    Box {
                                        Surface(
                                            shape = RoundedCornerShape(14.dp),
                                            color = Color.Black.copy(alpha = 0.5f),
                                            onClick = { showServerMenu = true }
                                        ) {
                                        Row(
                                            modifier = Modifier
                                                .defaultMinSize(minWidth = 44.dp)
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
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
                                if (subtitleTracks.isNotEmpty() || subtitleUrl != null) {
                                    Box {
                                        Surface(
                                            shape = RoundedCornerShape(14.dp),
                                            color = Color.Black.copy(alpha = 0.5f),
                                            onClick = { showSubtitleMenu = true }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                Icon(
                                                    Icons.Filled.ClosedCaption,
                                                    contentDescription = "Subtitles",
                                                    tint = if (subtitlesEnabled) Color.White else Color.Gray.copy(alpha = 0.5f),
                                                    modifier = Modifier.size(20.dp)
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
                                                        showSubtitleMenu = false
                                                    },
                                                    leadingIcon = if (!subtitlesEnabled) { { Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) } } else null
                                                )
                                                val trackList = subtitleTracks.ifEmpty {
                                                    if (subtitleUrl != null) listOf(eu.kanade.tachiyomi.animesource.model.Track(subtitleUrl, "en"))
                                                    else emptyList()
                                                }
                                                trackList.forEachIndexed { index, track ->
                                                    val isSelected = subtitlesEnabled && index == selectedSubtitleIndex
                                                    DropdownMenuItem(
                                                        text = { Text(track.lang.uppercase(), color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White) },
                                                        onClick = {
                                                            selectedSubtitleIndex = index
                                                            rebuildWithSubtitles(true)
                                                            showSubtitleMenu = false
                                                        },
                                                        leadingIcon = if (isSelected) { { Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) } } else null
                                                    )
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
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color.Black.copy(alpha = 0.5f),
                                    onClick = { resizeModeIndex = (resizeModeIndex + 1) % resizeModes.size }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            Icons.Default.AspectRatio,
                                            "Change aspect ratio",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                // Player settings button
                                var showPlayerSettings by remember { mutableStateOf(false) }
                                Box {
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = Color.Black.copy(alpha = 0.5f),
                                        onClick = { showPlayerSettings = true }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                Icons.Default.Settings,
                                                "Player Settings",
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    DropdownMenu(
                                        expanded = showPlayerSettings,
                                        onDismissRequest = { showPlayerSettings = false },
                                        modifier = Modifier.background(Color(0xFF1A1A1A)).width(220.dp)
                                    ) {
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text("Swipe for Volume (${if (swipeSwap) "Right" else "Left"})", color = Color.White)
                                                    Switch(
                                                        checked = swipeVolume,
                                                        onCheckedChange = { onSwipeVolumeChange?.invoke(it) },
                                                        colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
                                                    )
                                                }
                                            },
                                            onClick = { onSwipeVolumeChange?.invoke(!swipeVolume) }
                                        )
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text("Swipe for Brightness (${if (swipeSwap) "Left" else "Right"})", color = Color.White)
                                                    Switch(
                                                        checked = swipeBrightness,
                                                        onCheckedChange = { onSwipeBrightnessChange?.invoke(it) },
                                                        colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
                                                    )
                                                }
                                            },
                                            onClick = { onSwipeBrightnessChange?.invoke(!swipeBrightness) }
                                        )
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text("Swap Sides", color = Color.White)
                                                    Switch(
                                                        checked = swipeSwap,
                                                        onCheckedChange = { onSwipeSwapChange?.invoke(it) },
                                                        colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
                                                    )
                                                }
                                            },
                                            onClick = { onSwipeSwapChange?.invoke(!swipeSwap) }
                                        )
                                    }
                                }

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
                            horizontalArrangement = Arrangement.spacedBy(32.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { onPreviousEpisode?.invoke() },
                                modifier = Modifier.size(56.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape).alpha(if (onPreviousEpisode != null && !isLoadingStream && !isChangingServer) 1f else 0.3f),
                                enabled = onPreviousEpisode != null && !isLoadingStream && !isChangingServer
                            ) {
                                Icon(Icons.Default.SkipPrevious, "Previous Episode", tint = Color.White, modifier = Modifier.size(32.dp))
                            }

                            IconButton(
                                onClick = {
                                    if (hasError) {
                                        handlePlaybackError()
                                        exoPlayer.prepare()
                                        exoPlayer.playWhenReady = true
                                    } else {
                                        if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                                    }
                                },
                                modifier = Modifier.size(72.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            ) {
                                if (isBuffering || isOffline) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        modifier = Modifier.size(42.dp),
                                        strokeWidth = 3.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (hasError) Icons.Default.Refresh else if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (hasError) "Retry" else if (isPlaying) "Pause" else "Play",
                                        tint = Color.White,
                                        modifier = Modifier.size(42.dp)
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    onNextEpisode?.invoke()
                                },
                                modifier = Modifier.size(56.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape).alpha(if (onNextEpisode != null && !isLatestEpisode && !isLoadingStream && !isChangingServer) 1f else 0.3f),
                                enabled = onNextEpisode != null && !isLatestEpisode && !isLoadingStream && !isChangingServer
                            ) {
                                Icon(Icons.Default.SkipNext, "Next Episode", tint = Color.White, modifier = Modifier.size(32.dp))
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = showSkipIndicator && !skipIsForward,
                        enter = fadeIn() + scaleIn(initialScale = 0.8f),
                        exit = fadeOut() + scaleOut(targetScale = 0.8f),
                        modifier = Modifier.align(Alignment.CenterStart).offset(x = (-120).dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier.size(56.dp).background(Color.Black.copy(alpha = 0.6f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.FastRewind, "Rewind", tint = Color.White, modifier = Modifier.size(32.dp))
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(skipIndicatorText, color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                    }

                    AnimatedVisibility(
                        visible = showSkipIndicator && skipIsForward,
                        enter = fadeIn() + scaleIn(initialScale = 0.8f),
                        exit = fadeOut() + scaleOut(targetScale = 0.8f),
                        modifier = Modifier.align(Alignment.CenterEnd).offset(x = 120.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier.size(56.dp).background(Color.Black.copy(alpha = 0.6f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.FastForward, "Forward", tint = Color.White, modifier = Modifier.size(32.dp))
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(skipIndicatorText, color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Skip Opening/Ending buttons - outside controls visibility so they don't get darkened
                AnimatedVisibility(
                    visible = showSkipOpeningButton || showSkipEndingButton,
                    enter = fadeIn() + scaleIn(initialScale = 0.8f),
                    exit = fadeOut() + scaleOut(targetScale = 0.8f),
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (showSkipOpeningButton) {
                            SkipIconButton(
                                icon = Icons.Default.FastForward,
                                label = "Skip\nOpening",
                                backgroundColor = Color.Black.copy(alpha = 0.6f),
                                iconTint = Color.White,
                                onClick = {
                                    val ts = effectiveTimestamps
                                    if (ts.introEnd != null) {
                                        exoPlayer.seekTo(ts.introEnd * 1000L)
                                        exoPlayer.play()
                                        hasSkippedIntro = true
                                    }
                                }
                            )
                        }
                        if (showSkipEndingButton) {
                            SkipIconButton(
                                icon = Icons.Default.SkipNext,
                                label = if (isLatestEpisode || !creditsAtEnd) "Skip\nEnding" else "Next\nEpisode",
                                backgroundColor = Color.Black.copy(alpha = 0.6f),
                                iconTint = Color.White,
                                onClick = {
                                    if (isLatestEpisode || !creditsAtEnd) {
                                        if (exoPlayer.duration > 0) {
                                            exoPlayer.seekTo(exoPlayer.duration)
                                        }
                                    } else if (!isChangingServer) {
                                        onNextEpisode?.invoke()
                                    }
                                }
                            )
                        }
                    }
                }

                if (isLoadingStream || isChangingServer) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center).offset(y = 64.dp), color = Color.White)
                }

                if (hasError && playbackError != null) {
                    Card(
                        modifier = Modifier.align(Alignment.Center).padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Refresh, null, tint = Color(0xFFFFA726), modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Stream Error", color = Color.White, style = MaterialTheme.typography.titleMedium)
                            Text(playbackError ?: "Unknown error", color = Color.Gray, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))

                            Spacer(modifier = Modifier.height(12.dp))

                            if (onServerChange != null) {
                                val servers = if (currentCategory == "sub") subServers else dubServers
                                if (servers.size > 1) {
                                    Button(
                                        onClick = {
                                            val currentIndex = servers.indexOfFirst { it.name == currentServerName }
                                            val nextIndex = (currentIndex + 1) % servers.size
                                            handleServerChange(servers[nextIndex].name, currentCategory)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                    ) {
                                        Text("Try Next Server")
                                    }
                                }
                            }
                        }
                    }
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
                            .background(Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                            .navigationBarsPadding()
                            .padding(16.dp)
                    ) {
                        // Timer above progress bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(formatTime(currentPosition), color = Color.White, style = MaterialTheme.typography.labelMedium)
                            Text(if (duration > 0) formatTime(duration) else "--:--", color = Color.White, style = MaterialTheme.typography.labelMedium)
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(24.dp)
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
                                            seekPlayerTo(sliderValue.toLong())
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
                                val trackHeight = 8.dp.toPx()
                                val trackTop = (size.height - trackHeight) / 2f
                                val cornerRadius = 4.dp.toPx()
                                val thumbRadiusPx = 8.dp.toPx()

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
                                    if (showBufferIndicator && maxBufferedPosition > currentPosition) {
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
                        if (duration > 0) {
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
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Playback speed selector on the left
                            Box {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color.Black.copy(alpha = 0.5f),
                                    onClick = { showSpeedMenu = true }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Speed,
                                            contentDescription = "Playback speed",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "${currentSpeed}x",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Color.White
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = showSpeedMenu,
                                    onDismissRequest = { showSpeedMenu = false }
                                ) {
                                    speedOptions.forEach { speed ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = "${speed}x",
                                                    color = if (currentSpeed == speed) MaterialTheme.colorScheme.primary else Color.White
                                                )
                                            },
                                            onClick = {
                                                currentSpeed = speed
                                                exoPlayer.setPlaybackSpeed(speed)
                                                showSpeedMenu = false
                                            },
                                            leadingIcon = if (currentSpeed == speed) {
                                                { Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) }
                                            } else null
                                        )
                                    }
                                }
                            }

                            // Autoplay + Fullscreen (linked)
                            Row(
                                modifier = Modifier
                                    .background(Color.Black.copy(alpha = 0.5f), shape = RoundedCornerShape(14.dp)),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    onClick = { onAutoPlayNextEpisodeChanged?.invoke(!autoPlayNextEpisode) },
                                    color = Color.Transparent
                                ) {
                                    Row(
                                        modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "Autoplay",
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                        Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                                            Switch(
                                                checked = autoPlayNextEpisode,
                                                onCheckedChange = { onAutoPlayNextEpisodeChanged?.invoke(it) },
                                                modifier = Modifier.scale(0.5f),
                                                colors = SwitchDefaults.colors(
                                                    checkedTrackColor = Color.White,
                                                    checkedThumbColor = Color.Black,
                                                    uncheckedTrackColor = Color.White.copy(alpha = 0.3f),
                                                    uncheckedThumbColor = Color.White
                                                )
                                            )
                                        }
                                    }
                                }
                                Surface(
                                    onClick = { toggleFullscreen() },
                                    color = Color.Transparent
                                ) {
                                    Row(
                                        modifier = Modifier.padding(start = 6.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                            contentDescription = if (isFullscreen) "Exit fullscreen" else "Enter fullscreen",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Volume Overlay Indicator (on top of controls)
        val accentColor = if (disableMaterialColors) Color.White else MaterialTheme.colorScheme.primary
        AnimatedVisibility(
            visible = showVolumeOverlay,
            enter = fadeIn(animationSpec = tween(200)) + slideInVertically { it / 4 },
            exit = fadeOut(animationSpec = tween(300)),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Column(
                modifier = Modifier
                    .padding(start = 16.dp)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Volume",
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "${(playerVolume * 100).toInt()}%",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium
                )
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(80.dp)
                        .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(2.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(playerVolume)
                            .align(Alignment.BottomCenter)
                            .background(accentColor, RoundedCornerShape(2.dp))
                    )
                }
            }
        }

        // Brightness Overlay Indicator (on top of controls)
        AnimatedVisibility(
            visible = showBrightnessOverlay,
            enter = fadeIn(animationSpec = tween(200)) + slideInVertically { it / 4 },
            exit = fadeOut(animationSpec = tween(300)),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Column(
                modifier = Modifier
                    .padding(end = 16.dp)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.BrightnessHigh,
                    contentDescription = "Brightness",
                    tint = if (disableMaterialColors) Color.White else Color(0xFFFFD54F),
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "${(currentBrightness * 100).toInt()}%",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium
                )
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(80.dp)
                        .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(2.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(currentBrightness)
                            .align(Alignment.BottomCenter)
                            .background(if (disableMaterialColors) Color.White else Color(0xFFFFD54F), RoundedCornerShape(2.dp))
                    )
                }
            }
        }

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

internal fun loadSubtitleProfileData(context: Context): SubtitleProfileData {
    val prefs = context.getSharedPreferences("anilist_prefs", Context.MODE_PRIVATE)
    val saved = prefs.getString("subtitle_profiles", null)
    val activeIndex = prefs.getInt("subtitle_active_profile", 0)
    if (saved != null) {
        try {
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            val data = json.decodeFromString(SubtitleProfileData.serializer(), saved)
            return if (activeIndex in data.profiles.indices) data.copy(activeProfileIndex = activeIndex)
            else data
        } catch (_: Exception) { }
    }
    return SubtitleProfileData()
}

@OptIn(UnstableApi::class)
internal fun applySubtitleStyle(subtitleView: androidx.media3.ui.SubtitleView, settings: SubtitleSettings) {
    val edgeType = when {
        settings.enableOutline && settings.enableShadow -> CaptionStyleCompat.EDGE_TYPE_OUTLINE
        settings.enableOutline -> CaptionStyleCompat.EDGE_TYPE_OUTLINE
        settings.enableShadow -> CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
        else -> CaptionStyleCompat.EDGE_TYPE_NONE
    }
    val style = CaptionStyleCompat(
        (settings.fontColor and 0xFFFFFFFFL).toInt(),
        (settings.backgroundColor and 0xFFFFFFFFL).toInt(),
        android.graphics.Color.TRANSPARENT,
        edgeType,
        (settings.outlineColor and 0xFFFFFFFFL).toInt(),
        null
    )
    subtitleView.setStyle(style)
    subtitleView.setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, settings.fontSize)
    subtitleView.setBottomPaddingFraction(1f - settings.verticalPosition)
}