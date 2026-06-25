package com.blissless.tensei.ui.screens.player

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.blissless.tensei.api.AnimeSkipService
import com.blissless.tensei.data.models.EpisodeTimestamps
import com.blissless.tensei.data.models.SubtitleProfileData
import com.blissless.tensei.data.models.SubtitleSettings
import com.blissless.tensei.data.models.TmdbEpisode
import com.blissless.tensei.download.EpisodeDownloadManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds
import androidx.core.net.toUri

@UnstableApi
@Composable
fun OfflinePlayerScreen(
    downloadInfo: EpisodeDownloadManager.DownloadInfo,
    downloadManager: EpisodeDownloadManager,
    onDismiss: () -> Unit,
    allEpisodes: List<EpisodeDownloadManager.DownloadInfo> = emptyList(),
    onNavbarHidden: (Boolean) -> Unit = {},
    useMonochrome: Boolean = false,
    defaultSubtitleLang: String = "English",
    swipeVolume: Boolean = false,
    swipeBrightness: Boolean = false,
    swipeSwap: Boolean = false,
    onSwipeVolumeChange: ((Boolean) -> Unit)? = null,
    onSwipeBrightnessChange: ((Boolean) -> Unit)? = null,
    onSwipeSwapChange: ((Boolean) -> Unit)? = null,
    autoSkipOpening: Boolean = false,
    autoSkipEnding: Boolean = false,
    autoPlayNextEpisode: Boolean = false,
    tmdbEpisodes: Map<Int, TmdbEpisode> = emptyMap(),
    onSavePlaybackPosition: ((Int, Int, Long, Long) -> Unit)? = null,
    onAutoPlayNextEpisodeChanged: ((Boolean) -> Unit)? = null,
    initialPosition: Long = 0L,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val offlineFactory = remember { downloadManager.getOfflineDataSourceFactory() }

    LaunchedEffect(Unit) {
        onNavbarHidden(true)
        activity?.window?.let { window ->
            val ctrl = WindowCompat.getInsetsController(window, window.decorView)
            ctrl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            ctrl.hide(WindowInsetsCompat.Type.systemBars())
        }
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    var showControls by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var maxBufferedPosition by remember { mutableLongStateOf(0L) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

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

    var resizeModeIndex by remember { mutableIntStateOf(0) }
    val resizeModes = listOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT to "Fit",
        AspectRatioFrameLayout.RESIZE_MODE_FILL to "Stretch",
        AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH to "16:9"
    )

    var isFullscreen by remember { mutableStateOf(true) }
    var subtitlesEnabled by remember { mutableStateOf(true) }
    var showSubtitleMenu by remember { mutableStateOf(false) }
    var selectedSubtitleIndex by remember { mutableIntStateOf(0) }
    var subtitleTrackList by remember { mutableStateOf<List<EpisodeDownloadManager.SubtitleTrackData>>(emptyList()) }
    var showSubtitleSettings by remember { mutableStateOf(false) }
    var subtitleProfileData by remember { mutableStateOf(loadSubtitleProfileData(context)) }

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
        context.getSharedPreferences("anilist_prefs", Context.MODE_PRIVATE).edit()
            .putString("subtitle_profiles", encoded)
            .putInt("subtitle_active_profile", data.activeProfileIndex)
            .apply()
    }

    // Auto-select subtitle based on user preference
    LaunchedEffect(subtitleTrackList) {
        if (subtitleTrackList.isNotEmpty()) {
            val idx = subtitleTrackList.indexOfFirst {
                it.lang.equals(defaultSubtitleLang, ignoreCase = true)
            }.let { it ->
                if (it >= 0) it else
                subtitleTrackList.indexOfFirst {
                    it.lang.equals("English", ignoreCase = true)
                }.let { if (it >= 0) it else 0 }
            }
            selectedSubtitleIndex = idx
        }
    }

    val sortedEpisodes = remember(allEpisodes) {
        allEpisodes.sortedBy { it.episode }
    }
    val initialIndex = remember(downloadInfo, sortedEpisodes) {
        sortedEpisodes.indexOfFirst { it.episode == downloadInfo.episode }.coerceAtLeast(0)
    }
    var currentIndex by remember { mutableIntStateOf(initialIndex) }
    val currentDownload = if (sortedEpisodes.isNotEmpty()) sortedEpisodes[currentIndex] else downloadInfo

    // Skip timestamp state
    var episodeTimestamps by remember { mutableStateOf<EpisodeTimestamps?>(null) }
    var showSkipOpeningButton by remember { mutableStateOf(false) }
    var showSkipEndingButton by remember { mutableStateOf(false) }
    var hasSkippedIntro by remember { mutableStateOf(false) }
    var hasSkippedOutro by remember { mutableStateOf(false) }
    var hasFetchedTimestamps by remember { mutableStateOf(false) }
    var isFetchingTimestamps by remember { mutableStateOf(false) }
    var actualEpisodeLength by remember { mutableStateOf<Long?>(null) }
    val animeSkipService = remember { AnimeSkipService(context) }

    val effectiveTimestamps: EpisodeTimestamps = remember(episodeTimestamps) {
        episodeTimestamps ?: EpisodeTimestamps(
            episodeNumber = currentDownload.episode,
            introStart = null, introEnd = null,
            creditsStart = null, creditsEnd = null,
            recapStart = null, recapEnd = null,
            allTimestamps = emptyList()
        )
    }

    val scope = rememberCoroutineScope()

    fun toggleFullscreen() {
        isFullscreen = !isFullscreen
        activity?.window?.let { window ->
            if (isFullscreen) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
                WindowCompat.setDecorFitsSystemWindows(window, false)
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
                WindowCompat.setDecorFitsSystemWindows(window, true)
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    fun exitFullscreen() {
        if (isFullscreen) {
            isFullscreen = false
            activity?.window?.let { window ->
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
                WindowCompat.setDecorFitsSystemWindows(window, true)
            }
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }

    LaunchedEffect(showControls, showError) {
        controlsVisible = showControls || showError
    }

    fun seekBy(milliseconds: Long) {
        val player = exoPlayer ?: return
        skipIndicatorText = if (milliseconds > 0) "+${abs(milliseconds / 1000)}s" else "-${abs(milliseconds / 1000)}s"
        skipIsForward = milliseconds >= 0
        showSkipIndicator = true
        val newPosition = (player.currentPosition + milliseconds).coerceIn(0, player.duration.coerceAtLeast(0))
        player.seekTo(newPosition)
        currentPosition = newPosition
        sliderValue = newPosition.toFloat()
        skipResetJob?.cancel()
        skipResetJob = scope.launch {
            delay(500.milliseconds)
            showSkipIndicator = false
        }
    }

    fun goToNextEpisode() {
        if (currentIndex < sortedEpisodes.lastIndex) {
            currentIndex++
            exoPlayer?.stop()
        }
    }

    fun buildMediaItem(subtitles: Boolean): MediaItem {
        val mimeType = when {
            currentDownload.videoUrl.contains(".m3u8") -> MimeTypes.APPLICATION_M3U8
            currentDownload.videoUrl.contains(".mp4") -> MimeTypes.VIDEO_MP4
            currentDownload.videoUrl.contains(".webm") -> MimeTypes.VIDEO_WEBM
            else -> MimeTypes.APPLICATION_M3U8
        }
        if (subtitles) {
            val id = "${currentDownload.animeId}_${currentDownload.episode}"
            subtitleTrackList = downloadManager.getSubtitleTracks(id)
        }
        val subtitleConfigs = if (subtitles && subtitleTrackList.isNotEmpty()) {
            subtitleTrackList.mapIndexed { index, track ->
                val file = track.cachedPath?.let { File(it) }
                if (file?.exists() == true) {
                    val flags = if (index == selectedSubtitleIndex) C.SELECTION_FLAG_DEFAULT else 0
                    MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(file))
                        .setMimeType(MimeTypes.TEXT_VTT)
                        .setLanguage(track.lang)
                        .setSelectionFlags(flags)
                        .build()
                } else null
            }.filterNotNull()
        } else {
            emptyList()
        }
        return MediaItem.Builder()
            .setUri(currentDownload.videoUrl.toUri())
            .setMimeType(mimeType)
            .setSubtitleConfigurations(subtitleConfigs)
            .build()
    }

    fun rebuildWithSubtitles(enabled: Boolean, index: Int = selectedSubtitleIndex) {
        selectedSubtitleIndex = index
        subtitlesEnabled = enabled
        val player = exoPlayer ?: return
        val position = player.currentPosition
        val playWhenReady = player.playWhenReady
        val newItem = buildMediaItem(enabled)
        player.stop()
        player.clearMediaItems()
        player.setMediaItem(newItem, position)
        player.prepare()
        player.playWhenReady = playWhenReady
    }

    fun buildPlayer(): ExoPlayer? {
        if (offlineFactory == null) return null
        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(context).setDataSourceFactory(offlineFactory)
            )
            .build().apply {
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        isPlaying = playing
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        isBuffering = playbackState == Player.STATE_BUFFERING
                        if (playbackState == Player.STATE_READY) {
                            showError = false
                            errorMessage = null
                            isBuffering = false
                            val playerDuration = exoPlayer?.duration ?: 0L
                            if (playerDuration > 0) actualEpisodeLength = playerDuration
                        }
                        if (playbackState == Player.STATE_ENDED) {
                            showControls = true
                        }
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        showError = true
                        errorMessage = "${error.errorCode}: ${error.message ?: "Unknown"}"
                        showControls = true
                    }

                    @Deprecated("Use onCues(CueGroup) instead", ReplaceWith("onCues(CueGroup(cues))"))
                    override fun onCues(cues: MutableList<androidx.media3.common.text.Cue>) {
                        android.util.Log.d("OfflinePlayer", "onCues: ${cues.size} cues received")
                    }
                })
                setMediaItem(buildMediaItem(subtitlesEnabled))
                if (initialPosition > 0L) {
                    seekTo(initialPosition)
                }
                prepare()
                playWhenReady = true
            }
    }

    DisposableEffect(currentDownload) {
        exoPlayer?.stop()
        exoPlayer?.clearMediaItems()
        exoPlayer?.release()
        val player = buildPlayer()
        exoPlayer = player
        onDispose {
            player?.stop()
            player?.clearMediaItems()
            player?.release()
        }
    }

    LaunchedEffect(exoPlayer) {
        val player = exoPlayer ?: return@LaunchedEffect
        var saveCounter = 0
        while (true) {
            delay(500.milliseconds)
            if (!isDragging) {
                currentPosition = player.currentPosition
                duration = player.duration
                val bp = player.bufferedPosition
                if (bp > maxBufferedPosition) {
                    maxBufferedPosition = bp
                }
                if (duration > 0) {
                    sliderValue = currentPosition.toFloat()
                }
                // Save playback position every 5 seconds for continue watching
                saveCounter++
                if (saveCounter >= 10 && currentPosition > 5000L && duration > 0 && onSavePlaybackPosition != null) {
                    onSavePlaybackPosition(currentDownload.animeId, currentDownload.episode, currentPosition, duration)
                    saveCounter = 0
                }
            }
        }
    }

    LaunchedEffect(showControls, isPlaying, isDragging, showError, showSpeedMenu) {
        if (showControls && isPlaying && !isDragging && !showError && !showSpeedMenu) {
            delay(2000.milliseconds)
            if (showControls && !isDragging && !showError && isPlaying && !showSpeedMenu) {
                showControls = false
            }
        }
    }

    // Fetch skip timestamps from anime skip service
    LaunchedEffect(actualEpisodeLength, currentDownload.episode) {
        if (actualEpisodeLength == null || hasFetchedTimestamps) return@LaunchedEffect
        isFetchingTimestamps = true
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val malId = currentDownload.malId
                val epNum = currentDownload.episode
                val epLengthMs = actualEpisodeLength ?: return@withContext
                val epLengthSec = (epLengthMs / 1000).toInt()
                val animeName = currentDownload.animeName
                val year = currentDownload.year
                val timestamps = if (malId != null && malId > 0) {
                    animeSkipService.getSkipTimestampsWithFallback(
                        malId = malId,
                        episodeNumber = epNum,
                        episodeLength = epLengthSec,
                        animeName = animeName,
                        animeYear = year,
                        animeId = currentDownload.animeId
                    )
                } else if (animeName.isNotEmpty()) {
                    animeSkipService.getSkipTimestampsByName(
                        animeName = animeName,
                        episodeNumber = epNum,
                        episodeLength = epLengthSec,
                        year = year
                    )
                } else null
                if (timestamps != null && timestamps.hasTimestamps()) {
                    episodeTimestamps = timestamps
                }
            } catch (_: Exception) { }
        }
        isFetchingTimestamps = false
        hasFetchedTimestamps = true
    }

    // Auto-skip logic
    LaunchedEffect(currentPosition, effectiveTimestamps) {
        if (showError) return@LaunchedEffect
        val posSeconds = currentPosition / 1000

        if (effectiveTimestamps.introStart != null && effectiveTimestamps.introEnd != null) {
            val isInIntro = posSeconds >= effectiveTimestamps.introStart && posSeconds < effectiveTimestamps.introEnd
            if (isInIntro) {
                if (autoSkipOpening && !hasSkippedIntro) {
                    exoPlayer?.seekTo(effectiveTimestamps.introEnd * 1000L)
                    exoPlayer?.play()
                    hasSkippedIntro = true
                }
                showSkipOpeningButton = !autoSkipOpening
            } else {
                showSkipOpeningButton = false
            }
        }

        if (effectiveTimestamps.creditsStart != null) {
            val isInCredits = posSeconds >= effectiveTimestamps.creditsStart
            if (isInCredits) {
                if (autoSkipEnding && !hasSkippedOutro) {
                    val isLatest = currentIndex >= sortedEpisodes.lastIndex
                    if (!isLatest && autoPlayNextEpisode) {
                        goToNextEpisode()
                    }
                    hasSkippedOutro = true
                }
                showSkipEndingButton = true
            } else {
                showSkipEndingButton = false
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // Save final playback position on close
            if (currentPosition > 5000L && duration > 0 && onSavePlaybackPosition != null) {
                onSavePlaybackPosition(currentDownload.animeId, currentDownload.episode, currentPosition, duration)
            }
            exoPlayer?.stop()
            exoPlayer?.clearMediaItems()
            exoPlayer?.release()
            exitFullscreen()
            onNavbarHidden(false)
        }
    }

    val animeName = currentDownload.animeName.replace("_", " ").replaceFirstChar { it.uppercase() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // PlayerView - recreate when subtitle profile changes
        key(subtitleProfileData) {
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
                            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                            subtitleView?.apply {
                                applySubtitleStyle(this, getActiveSubtitleSettings())
                            }
                        }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    view.resizeMode = resizeModes[resizeModeIndex].first
                    view.player = exoPlayer
                    view.subtitleView?.apply {
                        applySubtitleStyle(this, getActiveSubtitleSettings())
                    }
                }
            )
        }

        // Gesture zones
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
                                    exoPlayer?.volume = playerVolume
                                    showVolumeOverlay = true
                                    scope.launch {
                                        delay(1500.milliseconds)
                                        showVolumeOverlay = false
                                    }
                                }
                            }
                        )
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            val now = System.currentTimeMillis()
                            if (now - lastLeftTapTime < 300) {
                                seekBy(-10000L)
                            } else {
                                showControls = !showControls
                            }
                            lastLeftTapTime = now
                        }
                    )
                }
        )

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.4f)
                .align(Alignment.Center)
                .pointerInput(Unit) { detectTapGestures(onTap = { showControls = !showControls }) }
        )

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
                                if (swipeSwap) {
                                    val volumeChange = -(dragAmount / 500f)
                                    playerVolume = (playerVolume + volumeChange).coerceIn(0f, 1f)
                                    exoPlayer?.volume = playerVolume
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
                        )
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            val now = System.currentTimeMillis()
                            if (now - lastRightTapTime < 300) {
                                seekBy(10000L)
                            } else {
                                showControls = !showControls
                            }
                            lastRightTapTime = now
                        }
                    )
                }
        )

        // Padding zones to prevent UI toggling in safe areas
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(40.dp)
                .align(Alignment.CenterStart)
                .pointerInput(Unit) { detectTapGestures(onTap = {}) }
        )
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(40.dp)
                .align(Alignment.CenterEnd)
                .pointerInput(Unit) { detectTapGestures(onTap = {}) }
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .align(Alignment.TopCenter)
                .pointerInput(Unit) { detectTapGestures(onTap = {}) }
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .align(Alignment.BottomCenter)
                .pointerInput(Unit) { detectTapGestures(onTap = {}) }
        )

        // Controls overlay
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(animationSpec = tween(150)),
            exit = fadeOut(animationSpec = tween(100)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Darkening overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = if (controlsVisible) 0.3f else 0f))
                )

                // Top bar
                AnimatedVisibility(
                    visible = controlsVisible,
                    enter = slideInVertically(initialOffsetY = { -it }, animationSpec = tween(200)),
                    exit = slideOutVertically(targetOffsetY = { -it }, animationSpec = tween(100)),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.7f),
                                        Color.Transparent
                                    )
                                )
                            )
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
                                        exoPlayer?.stop()
                                        onDismiss()
                                    },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(
                                            Color.Black.copy(alpha = 0.5f),
                                            shape = MaterialTheme.shapes.small
                                        )
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
                                    Text(
                                        text = animeName,
                                        color = Color.White,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val epTitle = tmdbEpisodes[currentDownload.episode]?.title
                                    Text(
                                        text = if (epTitle != null) "Ep ${currentDownload.episode} - $epTitle" else "Episode ${currentDownload.episode}",
                                        color = Color.White.copy(alpha = 0.7f),
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // CC/Subtitles button
                            if (subtitleTrackList.isNotEmpty()) {
                                Box {
                                    val ccActive = subtitlesEnabled && subtitleTrackList.isNotEmpty()
                                    val ccColor = when {
                                        useMonochrome -> if (ccActive) Color.White else Color.White.copy(alpha = 0.5f)
                                        ccActive -> Color(0xFF4FC3F7)
                                        else -> Color.White.copy(alpha = 0.5f)
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = Color.Black.copy(alpha = 0.5f),
                                        onClick = {
                                            if (subtitleTrackList.isEmpty()) {
                                                val id = "${currentDownload.animeId}_${currentDownload.episode}"
                                                subtitleTrackList = downloadManager.getSubtitleTracks(id)
                                            }
                                            showSubtitleMenu = true
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                Icons.Filled.ClosedCaption,
                                                contentDescription = "Subtitles",
                                                tint = ccColor,
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
                                                leadingIcon = if (!subtitlesEnabled) {
                                                    { Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) }
                                                } else null
                                            )
                                            subtitleTrackList.forEachIndexed { index, track ->
                                                val isSelected = subtitlesEnabled && index == selectedSubtitleIndex
                                                val trackLang = track.lang.ifEmpty { "en" }
                                                DropdownMenuItem(
                                                    text = { Text(trackLang.uppercase(), color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White) },
                                                    onClick = {
                                                        rebuildWithSubtitles(true, index)
                                                        showSubtitleMenu = false
                                                    },
                                                    leadingIcon = if (isSelected) {
                                                        { Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) }
                                                    } else null
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
                            // Resize button only (no server selectors)
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

                // Center play/pause + prev/next
                Box(modifier = Modifier.align(Alignment.Center)) {
                    AnimatedVisibility(
                        visible = controlsVisible,
                        enter = fadeIn(animationSpec = tween(200)),
                        exit = fadeOut(animationSpec = tween(100)),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            // Previous episode
                            val hasPrev = sortedEpisodes.isNotEmpty() && currentIndex > 0
                            IconButton(
                                onClick = {
                                    if (hasPrev) {
                                        currentIndex--
                                        exoPlayer?.stop()
                                    }
                                },
                                modifier = Modifier
                                    .size(52.dp)
                                    .background(Color.Black.copy(alpha = if (hasPrev) 0.5f else 0.2f), CircleShape)
                            ) {
                                Icon(
                                    Icons.Default.FastRewind,
                                    "Previous episode",
                                    tint = Color.White.copy(alpha = if (hasPrev) 1f else 0.3f),
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    if (showError) {
                                        exoPlayer?.prepare()
                                        exoPlayer?.playWhenReady = true
                                    } else {
                                        if (isPlaying) exoPlayer?.pause() else exoPlayer?.play()
                                    }
                                },
                                modifier = Modifier
                                    .size(72.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            ) {
                                if (isBuffering) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        modifier = Modifier.size(42.dp),
                                        strokeWidth = 3.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (showError) Icons.Default.Refresh
                                        else if (isPlaying) Icons.Default.Pause
                                        else Icons.Default.PlayArrow,
                                        contentDescription = if (showError) "Retry"
                                        else if (isPlaying) "Pause" else "Play",
                                        tint = Color.White,
                                        modifier = Modifier.size(42.dp)
                                    )
                                }
                            }

                            // Next episode
                            val hasNext = sortedEpisodes.isNotEmpty() && currentIndex < sortedEpisodes.size - 1
                            IconButton(
                                onClick = {
                                    if (hasNext) {
                                        currentIndex++
                                        exoPlayer?.stop()
                                    }
                                },
                                modifier = Modifier
                                    .size(52.dp)
                                    .background(Color.Black.copy(alpha = if (hasNext) 0.5f else 0.2f), CircleShape)
                            ) {
                                Icon(
                                    Icons.Default.FastForward,
                                    "Next episode",
                                    tint = Color.White.copy(alpha = if (hasNext) 1f else 0.3f),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }

                    // Skip indicators
                    AnimatedVisibility(
                        visible = showSkipIndicator && !skipIsForward,
                        enter = fadeIn() + scaleIn(initialScale = 0.8f),
                        exit = fadeOut() + scaleOut(targetScale = 0.8f),
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .offset(x = (-120).dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.FastRewind,
                                    "Rewind",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                skipIndicatorText,
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = showSkipIndicator && skipIsForward,
                        enter = fadeIn() + scaleIn(initialScale = 0.8f),
                        exit = fadeOut() + scaleOut(targetScale = 0.8f),
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .offset(x = 120.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.FastForward,
                                    "Forward",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                skipIndicatorText,
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Error state
                if (showError && errorMessage != null) {
                    androidx.compose.material3.Card(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        colors = androidx.compose.material3.CardDefaults.cardColors(
                            containerColor = Color(0xFF1A1A1A)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Fullscreen,
                                null,
                                tint = Color.Red,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Playback Error",
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                errorMessage ?: "Unknown error",
                                color = Color.Gray,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }

                // Skip Opening/Ending buttons
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
                                    if (effectiveTimestamps.introEnd != null) {
                                        exoPlayer?.seekTo(effectiveTimestamps.introEnd * 1000L)
                                        exoPlayer?.play()
                                        hasSkippedIntro = true
                                    }
                                }
                            )
                        }
                        if (showSkipEndingButton) {
                            val isLatest = currentIndex >= sortedEpisodes.lastIndex
                            SkipIconButton(
                                icon = Icons.Default.SkipNext,
                                label = if (isLatest) "Skip\nEnding" else "Next\nEpisode",
                                backgroundColor = Color.Black.copy(alpha = 0.6f),
                                iconTint = Color.White,
                                onClick = {
                                    if (isLatest) {
                                        if ((exoPlayer?.duration ?: 0) > 0) {
                                            exoPlayer?.seekTo(exoPlayer?.duration ?: 0)
                                        }
                                    } else {
                                        goToNextEpisode()
                                    }
                                }
                            )
                        }
                    }
                }

                if (exoPlayer == null) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text("Cache not available", color = Color.White)
                    }
                }

                // Bottom controls
                AnimatedVisibility(
                    visible = controlsVisible,
                    enter = slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = tween(200)
                    ),
                    exit = slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = tween(100)
                    ),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.7f)
                                    )
                                )
                            )
                            .navigationBarsPadding()
                            .padding(16.dp)
                    ) {
                        // Time labels
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                formatTime(currentPosition),
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium
                            )
                            Text(
                                if (duration > 0) formatTime(duration) else "--:--",
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Seek bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(24.dp)
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = { offset ->
                                            if (duration > 0) {
                                                val ratio =
                                                    (offset.x / size.width).coerceIn(0f, 1f)
                                                val seekPosition = (ratio * duration).toLong()
                                                exoPlayer?.seekTo(seekPosition)
                                                currentPosition = seekPosition
                                                sliderValue = seekPosition.toFloat()
                                            }
                                        }
                                    )
                                }
                                .pointerInput(Unit) {
                                    detectHorizontalDragGestures(
                                        onDragStart = { offset ->
                                            isDragging = true
                                            wasPlayingBeforeScrub = isPlaying
                                            val ratio =
                                                (offset.x / size.width).coerceIn(0f, 1f)
                                            sliderValue =
                                                ratio * (if (duration > 0) duration.toFloat() else 1000f)
                                            currentPosition = sliderValue.toLong()
                                        },
                                        onDragEnd = {
                                            isDragging = false
                                            exoPlayer?.seekTo(sliderValue.toLong())
                                            if (wasPlayingBeforeScrub) {
                                                exoPlayer?.play()
                                            }
                                        },
                                        onHorizontalDrag = { _, dragAmount ->
                                            val currentRatio =
                                                sliderValue / (if (duration > 0) duration.toFloat() else 1000f)
                                            val newRatio =
                                                (currentRatio + dragAmount / size.width).coerceIn(
                                                    0f,
                                                    1f
                                                )
                                            sliderValue =
                                                newRatio * (if (duration > 0) duration.toFloat() else 1000f)
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
                                    val progressRatio =
                                        currentPosition.toFloat() / duration
                                    val bufferedRatio =
                                        maxBufferedPosition.toFloat() / duration

                                    drawRoundRect(
                                        color = Color.White.copy(alpha = 0.3f),
                                        topLeft = Offset(0f, trackTop),
                                        size = Size(sliderWidth, trackHeight),
                                        cornerRadius = CornerRadius(cornerRadius)
                                    )

                                    if (maxBufferedPosition > currentPosition) {
                                        val bufferStartX = progressRatio * sliderWidth
                                        val bufferEndX = bufferedRatio * sliderWidth
                                        drawRoundRect(
                                            color = Color.White.copy(alpha = 0.5f),
                                            topLeft = Offset(bufferStartX, trackTop),
                                            size = Size(
                                                bufferEndX - bufferStartX,
                                                trackHeight
                                            ),
                                            cornerRadius = CornerRadius(2.dp.toPx())
                                        )
                                    }

                                    // Intro/outro visual markers - same colors as normal player
                                    val ts = effectiveTimestamps
                                    val watchedOrange = Color(0xFFFF9800)
                                    val unwatchedOrange = Color(0xFFA67C00)
                                    val progressX = progressRatio * sliderWidth
                                    if (duration > 0) {
                                        val introStartRatio = if (ts.introStart != null) (ts.introStart * 1000f) / duration else null
                                        val introEndRatio = if (ts.introEnd != null) (ts.introEnd * 1000f) / duration else null
                                        val creditsStartRatio = if (ts.creditsStart != null) (ts.creditsStart * 1000f) / duration else null

                                        if (introStartRatio != null && introEndRatio != null) {
                                            val introStartX = introStartRatio * sliderWidth
                                            val introEndX = introEndRatio * sliderWidth
                                            val introWidth = introEndX - introStartX
                                            if (introWidth > 0) {
                                                val leftRadius = if (introStartX < 10f) cornerRadius else 2.dp.toPx()
                                                val rightRadius = if (introEndX > sliderWidth - 10f) cornerRadius else 2.dp.toPx()
                                                if (introStartX < progressX && introEndX <= progressX) {
                                                    drawRoundRect(color = watchedOrange, topLeft = Offset(introStartX, trackTop), size = Size(introWidth, trackHeight), cornerRadius = CornerRadius(cornerRadius, cornerRadius))
                                                } else if (introStartX >= progressX) {
                                                    drawRoundRect(color = unwatchedOrange, topLeft = Offset(introStartX.coerceAtLeast(0f), trackTop), size = Size(introWidth.coerceAtMost(sliderWidth - introStartX.coerceAtLeast(0f)), trackHeight), cornerRadius = CornerRadius(leftRadius, rightRadius))
                                                } else if (introStartX < progressX && introEndX > progressX) {
                                                    val watchedWidth = progressX - introStartX
                                                    val unwatchedWidth = introEndX - progressX
                                                    drawRoundRect(color = watchedOrange, topLeft = Offset(introStartX, trackTop), size = Size(watchedWidth, trackHeight), cornerRadius = CornerRadius(cornerRadius, cornerRadius))
                                                    drawRoundRect(color = unwatchedOrange, topLeft = Offset(progressX, trackTop), size = Size(unwatchedWidth, trackHeight), cornerRadius = CornerRadius(cornerRadius, cornerRadius))
                                                }
                                            }
                                        }
                                        if (creditsStartRatio != null) {
                                            val creditsStartX = creditsStartRatio * sliderWidth
                                            if (creditsStartX < sliderWidth && creditsStartX > 0) {
                                                val creditsColor = if (creditsStartX < progressX) watchedOrange else unwatchedOrange
                                                drawRoundRect(color = creditsColor, topLeft = Offset(creditsStartX, trackTop), size = Size((sliderWidth - creditsStartX).coerceAtLeast(0f), trackHeight), cornerRadius = CornerRadius(cornerRadius, cornerRadius))
                                            }
                                        }
                                    }

                                    drawRoundRect(
                                        color = Color.White,
                                        topLeft = Offset(0f, trackTop),
                                        size = Size(
                                            progressX.coerceAtLeast(thumbRadiusPx),
                                            trackHeight
                                        ),
                                        cornerRadius = CornerRadius(cornerRadius)
                                    )

                                    // Continue-watching position marker (initialPosition)
                                    if (initialPosition > 0 && duration > 0) {
                                        val resumeRatio = initialPosition.toFloat() / duration
                                        val resumeX = resumeRatio * sliderWidth
                                        if (resumeX > thumbRadiusPx && resumeX < sliderWidth - thumbRadiusPx) {
                                            drawCircle(color = Color(0xFF4FC3F7), radius = thumbRadiusPx * 0.7f, center = Offset(resumeX, size.height / 2))
                                        }
                                    }

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

                        // Bottom row: speed + fullscreen
                        var currentSpeed by rememberSaveable { mutableFloatStateOf(1f) }
                        val speedOptions = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color.Black.copy(alpha = 0.5f),
                                    onClick = { showSpeedMenu = true }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(
                                            horizontal = 12.dp,
                                            vertical = 10.dp
                                        ),
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
                                                exoPlayer?.setPlaybackSpeed(speed)
                                                showSpeedMenu = false
                                            },
                                            leadingIcon = if (currentSpeed == speed) {
                                                {
                                                    Icon(
                                                        Icons.Default.Check,
                                                        null,
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
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

                // Volume overlay
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
                            tint = Color.White,
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
                                    .background(Color.White, RoundedCornerShape(2.dp))
                            )
                        }
                    }
                }

                // Brightness overlay
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
                            tint = Color(0xFFFFD54F),
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
                                    .background(Color(0xFFFFD54F), RoundedCornerShape(2.dp))
                            )
                        }
                    }
                }
            }

            // Subtitle Settings Dialog overlay
            if (showSubtitleSettings) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { showSubtitleSettings = false }
                        .padding(16.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
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
    }
}

private fun formatTime(ms: Long): String {
    val seconds = (ms / 1000) % 60
    val minutes = (ms / (1000 * 60)) % 60
    val hours = ms / (1000 * 60 * 60)
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}


