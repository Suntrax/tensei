package com.blissless.tensei.stream

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.TextureView
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.SubtitleView
import eu.kanade.tachiyomi.animesource.model.Track
import eu.kanade.tachiyomi.animesource.model.Video
import kotlinx.coroutines.delay
import androidx.core.net.toUri
import kotlin.time.Duration.Companion.milliseconds

@UnstableApi
class PlayerActivity : ComponentActivity() {

    private var player: ExoPlayer? = null
    private var videoContainer: AspectRatioFrameLayout? = null
    private var subtitleView: SubtitleView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val videos = PlayerData.videos.toList()
        val startQuality = PlayerData.currentQualityIndex.coerceIn(0, videos.lastIndex)
        var currentVideo by mutableStateOf(videos.getOrNull(startQuality))
        var showOverlay by mutableStateOf(true)
        var showQualitySheet by mutableStateOf(false)
        var showSubSheet by mutableStateOf(false)
        var showAudioSheet by mutableStateOf(false)
        var selectedSubtitle by mutableStateOf(PlayerData.selectedSubtitle)
        var selectedAudio by mutableStateOf(PlayerData.selectedAudio)
        var isPlaying by mutableStateOf(true)
        var adaptiveMode by mutableStateOf(false)
        var playbackPosition by mutableLongStateOf(0L)
        var playbackDuration by mutableLongStateOf(0L)

        val handler = Handler(Looper.getMainLooper())
        val hideOverlay = Runnable { showOverlay = false }

        fun scheduleHide() {
            handler.removeCallbacks(hideOverlay)
            handler.postDelayed(hideOverlay, 3000L)
        }

        fun formatTime(ms: Long): String {
            val totalSec = ms / 1000
            return "%02d:%02d".format(totalSec / 60, totalSec % 60)
        }

        fun buildMediaItem(video: Video, subtitle: Track?): MediaItem {
            val builder = MediaItem.Builder()
                .setUri(video.videoUrl.toUri())
            builder.setMediaMetadata(
                    androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(PlayerData.animeTitle)
                        .setSubtitle(video.videoTitle)
                        .build()
                )
            subtitle?.let { track ->
                val mime = when {
                    track.url.contains(".vtt") -> "text/vtt"
                    track.url.contains(".srt") -> "application/x-subrip"
                    else -> "text/vtt"
                }
                builder.setSubtitleConfigurations(
                    listOf(
                        MediaItem.SubtitleConfiguration.Builder(track.url.toUri())
                            .setMimeType(mime)
                            .setLanguage(track.lang)
                            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT or C.SELECTION_FLAG_AUTOSELECT)
                            .build()
                    )
                )
            }
            return builder.build()
        }

        fun playVideo(video: Video) {
            currentVideo = video
            val mediaItem = buildMediaItem(video, selectedSubtitle)
            val pos = player?.currentPosition ?: 0L
            player?.let { p ->
                p.stop()
                p.clearMediaItems()
                p.setMediaItem(mediaItem)
                if (pos > 0) p.seekTo(pos)
                p.prepare()
                p.playWhenReady = true
            }
            PlayerData.currentQualityIndex = videos.indexOf(video).coerceAtLeast(0)
        }

        val extClient = PlayerData.extensionClient
        val firstVideo = videos.getOrNull(startQuality)

        player = if (extClient != null) {
            val dsFactory = OkHttpDataSource.Factory(extClient)
            firstVideo?.headers?.let { h ->
                val props = (0 until h.size).associate { h.name(it) to h.value(it) }
                dsFactory.setDefaultRequestProperties(props)
            }
            val msFactory = DefaultMediaSourceFactory(this).setDataSourceFactory(dsFactory)
            ExoPlayer.Builder(this).setMediaSourceFactory(msFactory).build()
        } else {
            ExoPlayer.Builder(this).build()
        }.also { exo ->
            val first = videos.getOrNull(startQuality)
            if (first != null) {
                val mediaItemBuilder = MediaItem.Builder()
                    .setUri(first.videoUrl.toUri())

                // Add subtitle config
                val sub = PlayerData.selectedSubtitle
                if (sub != null) {
                    val subMime = when {
                        sub.url.contains(".vtt") -> "text/vtt"
                        sub.url.contains(".srt") -> "application/x-subrip"
                        else -> "text/vtt"
                    }
                    val subConfig = MediaItem.SubtitleConfiguration.Builder(sub.url.toUri())
                        .setMimeType(subMime)
                        .setLanguage(sub.lang)
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT or C.SELECTION_FLAG_AUTOSELECT)
                        .build()
                    mediaItemBuilder.setSubtitleConfigurations(listOf(subConfig))
                }

                val mediaItem = mediaItemBuilder.build()
                exo.setMediaItem(mediaItem)
                exo.prepare()
                exo.playWhenReady = true
            }
            exo.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        isPlaying = exo.playWhenReady
                    }
                }
                override fun onIsPlayingChanged(it: Boolean) {
                    isPlaying = it
                }
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                }
                override fun onPlayerErrorChanged(error: androidx.media3.common.PlaybackException?) {
                }
                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    val ratio = if (videoSize.width == 0 || videoSize.height == 0) 0f
                    else {
                        var r = videoSize.width.toFloat() / videoSize.height.toFloat() * videoSize.pixelWidthHeightRatio
                        @Suppress("DEPRECATION") if (videoSize.unappliedRotationDegrees == 90 || videoSize.unappliedRotationDegrees == 270) {
                            r = 1f / r
                        }
                        r
                    }
                    videoContainer?.setAspectRatio(ratio)
                }
                @Deprecated("Use onCues(CueGroup) instead", ReplaceWith("onCues(CueGroup(cues))"))
                override fun onCues(cues: List<Cue>) {
                    subtitleView?.setCues(cues)
                }
                override fun onCues(cueGroup: CueGroup) {
                    subtitleView?.setCues(cueGroup.cues)
                }
            })
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            val container = AspectRatioFrameLayout(ctx).apply {
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                setBackgroundColor(android.graphics.Color.BLACK)
                            }
                            val textureView = TextureView(ctx)
                            container.addView(textureView, FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT
                            ))
                            val subView = SubtitleView(ctx).apply {
                                setUserDefaultStyle()
                                setUserDefaultTextSize()
                            }
                            subtitleView = subView
                            container.addView(subView, FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT
                            ))
                            player?.setVideoTextureView(textureView)
                            videoContainer = container
                            container.setOnClickListener { showOverlay = true; scheduleHide() }
                            container
                        }
                    )

                    LaunchedEffect(player) {
                        while (true) {
                            delay(200.milliseconds)
                            playbackPosition = player?.currentPosition ?: 0L
                            playbackDuration = player?.duration ?: 0L
                        }
                    }

                    if (showOverlay) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0x80000000))
                                .clickable { showOverlay = true; scheduleHide() }
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                                }
                                Text(
                                    PlayerData.animeTitle,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            SeekBarSection(
                                position = playbackPosition,
                                duration = playbackDuration,
                                formatTime = ::formatTime,
                                onSeek = { pos -> player?.seekTo(pos) }
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            if (showQualitySheet) {
                                QualitySheet(
                                    videos = videos,
                                    currentIndex = PlayerData.currentQualityIndex,
                                    adaptiveMode = adaptiveMode,
                                    onSelect = { idx ->
                                        showQualitySheet = false; playVideo(videos[idx])
                                    },
                                    onToggleAdaptive = { adaptiveMode = !adaptiveMode }
                                )
                            }
                            if (showSubSheet) {
                                SubtitleSheet(
                                    tracks = currentVideo?.subtitleTracks ?: emptyList(),
                                    selected = selectedSubtitle,
                                    onSelect = { track ->
                                        showSubSheet = false
                                        selectedSubtitle = track
                                        PlayerData.selectedSubtitle = track
                                        currentVideo?.let { playVideo(it) }
                                    }
                                )
                            }
                            if (showAudioSheet) {
                                AudioSheet(
                                    tracks = currentVideo?.audioTracks ?: emptyList(),
                                    selected = selectedAudio,
                                    onSelect = { track ->
                                        showAudioSheet = false
                                        selectedAudio = track
                                        PlayerData.selectedAudio = track
                                        currentVideo?.let { playVideo(it) }
                                    }
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                ControlChip(
                                    icon = Icons.Default.Settings,
                                    label = "Quality",
                                    onClick = { showQualitySheet = !showQualitySheet; showSubSheet = false; showAudioSheet = false; scheduleHide() }
                                )
                                if ((currentVideo?.subtitleTracks?.size ?: 0) > 0) {
                                    ControlChip(
                                        icon = Icons.Default.Subtitles,
                                        label = selectedSubtitle?.lang?.uppercase() ?: "Subs",
                                        onClick = { showSubSheet = !showSubSheet; showQualitySheet = false; showAudioSheet = false; scheduleHide() }
                                    )
                                }
                                if ((currentVideo?.audioTracks?.size ?: 0) > 0) {
                                    ControlChip(
                                        icon = Icons.AutoMirrored.Filled.VolumeUp,
                                        label = selectedAudio?.lang?.uppercase() ?: "Audio",
                                        onClick = { showAudioSheet = !showAudioSheet; showQualitySheet = false; showSubSheet = false; scheduleHide() }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                IconButton(onClick = {
                                    player?.let { p ->
                                        if (p.isPlaying) p.pause() else p.play()
                                    }
                                }) {
                                    Icon(
                                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "Play/Pause",
                                        tint = Color.White,
                                        modifier = Modifier.size(48.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        scheduleHide()
    }

    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
        PlayerData.videos = emptyList()
    }
}

@Composable
private fun SeekBarSection(
    position: Long,
    duration: Long,
    formatTime: (Long) -> String,
    onSeek: (Long) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        Slider(
            value = if (duration > 0) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f,
            onValueChange = { onSeek((it * duration).toLong()) },
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color(0x66FFFFFF),
            ),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(formatTime(position), color = Color.White, fontSize = 12.sp)
            Text(formatTime(duration), color = Color.White, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ControlChip(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = Color(0xCCFFFFFF),
        modifier = Modifier.height(40.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
            Text(label, color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun QualitySheet(
    videos: List<Video>,
    currentIndex: Int,
    adaptiveMode: Boolean,
    onSelect: (Int) -> Unit,
    onToggleAdaptive: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xEE333333))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Quality", color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = adaptiveMode,
                    onClick = onToggleAdaptive,
                    label = { Text("Auto") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF1976D2), selectedLabelColor = Color.White
                    )
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(videos) { idx, v ->
                        val label = if (v.resolution != null && v.resolution > 0) "${v.resolution}p"
                                     else v.videoTitle.ifEmpty { "Q$idx" }
                        FilterChip(
                            selected = !adaptiveMode && idx == currentIndex,
                            onClick = { onSelect(idx) },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF1976D2), selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SubtitleSheet(tracks: List<Track>, selected: Track?, onSelect: (Track?) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xEE333333))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Subtitles", color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = selected == null,
                    onClick = { onSelect(null) },
                    label = { Text("Off") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF1976D2), selectedLabelColor = Color.White
                    )
                )
                tracks.forEach { track ->
                    FilterChip(
                        selected = selected == track,
                        onClick = { onSelect(track) },
                        label = { Text(track.lang.uppercase()) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF1976D2), selectedLabelColor = Color.White
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun AudioSheet(tracks: List<Track>, selected: Track?, onSelect: (Track?) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xEE333333))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Audio", color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = selected == null,
                    onClick = { onSelect(null) },
                    label = { Text("Default") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF1976D2), selectedLabelColor = Color.White
                    )
                )
                tracks.forEach { track ->
                    FilterChip(
                        selected = selected == track,
                        onClick = { onSelect(track) },
                        label = { Text(track.lang.uppercase()) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF1976D2), selectedLabelColor = Color.White
                        )
                    )
                }
            }
        }
    }
}


