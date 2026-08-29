package com.blissless.tensei.ui.screens.player

import android.content.Context
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView

class ExoPlayerEngine(
    private val context: Context,
    private val bufferAheadSeconds: Int,
    private val onGetCacheDataSourceFactory: ((referer: String) -> Any?)? = null,
) : PlayerEngine {

    private var listener: PlayerEngine.Listener? = null
    private var exoPlayer: ExoPlayer? = null

    private var _isPlaying = false
    private var _currentPosition = 0L
    private var _duration = 0L
    private var _bufferedPosition = 0L
    private var _playbackState = PlayerEngine.STATE_IDLE
    private var _currentSubtitleTracks = emptyList<EmbeddedSubtitleTrack>()

    private val playerView: PlayerView by lazy {
        PlayerView(context).apply {
            useController = false
            setShowNextButton(false)
            setShowPreviousButton(false)
            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
            controllerShowTimeoutMs = 3000
            controllerAutoShow = false
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
    }

    override val view: View get() = playerView

    override val isPlaying: Boolean get() = _isPlaying
    override val currentPosition: Long get() = exoPlayer?.currentPosition ?: _currentPosition
    override val duration: Long get() = exoPlayer?.duration ?: _duration
    override val bufferedPosition: Long get() = exoPlayer?.bufferedPosition ?: _bufferedPosition
    override val playbackState: Int get() = _playbackState
    override val currentSubtitleTracks: List<EmbeddedSubtitleTrack> get() = _currentSubtitleTracks

    override val isHls: Boolean get() = _isHls
    private var _isHls = false

    fun getExoPlayer(): ExoPlayer? = exoPlayer

    override fun loadMedia(
        url: String,
        mimeType: String?,
        startPositionMs: Long,
        subtitleConfigs: List<SubtitleConfig>,
        headers: Map<String, String>,
        referer: String,
        httpClient: Any?,
    ) {
        val bufferAheadMs = bufferAheadSeconds * 1000
        val maxBufferMs = maxOf(bufferAheadMs + 60000, 180000)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(bufferAheadMs, maxBufferMs, 1500, 3000)
            .build()

        @Suppress("UNCHECKED_CAST")
        val cacheFactory = onGetCacheDataSourceFactory?.invoke(referer) as? androidx.media3.datasource.DataSource.Factory

        val upstreamFactory = if (httpClient is okhttp3.OkHttpClient && headers.isNotEmpty()) {
            androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(httpClient)
                .setDefaultRequestProperties(headers)
        } else if (httpClient is okhttp3.OkHttpClient) {
            androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(httpClient)
                .setDefaultRequestProperties(mapOf("Referer" to referer))
        } else if (headers.isNotEmpty()) {
            val trustClient = try { eu.kanade.tachiyomi.network.NetworkHelper.getInstance().trustAllClient } catch (_: Exception) { null }
            if (trustClient != null) {
                androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(trustClient)
                    .setDefaultRequestProperties(headers)
            } else {
                DefaultHttpDataSource.Factory()
                    .setConnectTimeoutMs(20000)
                    .setReadTimeoutMs(20000)
                    .setDefaultRequestProperties(headers)
            }
        } else {
            val trustClient = try { eu.kanade.tachiyomi.network.NetworkHelper.getInstance().trustAllClient } catch (_: Exception) { null }
            if (trustClient != null) {
                androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(trustClient)
                    .setDefaultRequestProperties(mapOf("Referer" to referer))
            } else {
                DefaultHttpDataSource.Factory()
                    .setConnectTimeoutMs(20000)
                    .setReadTimeoutMs(20000)
                    .setDefaultRequestProperties(mapOf("Referer" to referer))
            }
        }

        val dataSourceFactory = cacheFactory ?: upstreamFactory

        _isHls = url.contains(".m3u8") || url.contains("/m3u8")

        val resolvedMimeType = mimeType ?: when {
            _isHls -> MimeTypes.APPLICATION_M3U8
            url.contains(".mp4") -> MimeTypes.VIDEO_MP4
            url.contains(".webm") -> MimeTypes.VIDEO_WEBM
            url.contains(".mkv") -> "video/x-matroska"
            url.contains(".avi") -> "video/x-msvideo"
            url.contains(".mov") -> "video/quicktime"
            else -> MimeTypes.VIDEO_MP4
        }

        // Release previous ExoPlayer if any (e.g. server/quality change)
        exoPlayer?.removeListener(playerListener)
        exoPlayer?.release()

        exoPlayer = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory))
            .setLoadControl(loadControl)
            .build()
            .apply {
                addListener(playerListener)
                playerView.player = this
            }

        val mediaSubtitleConfigs = subtitleConfigs.map { config ->
            MediaItem.SubtitleConfiguration.Builder(config.url.toUri())
                .setMimeType(config.mimeType)
                .setLanguage(config.language)
                .setSelectionFlags(if (config.selected) C.SELECTION_FLAG_DEFAULT else 0)
                .build()
        }

        val mediaItem = MediaItem.Builder()
            .setUri(url)
            .setMimeType(resolvedMimeType)
            .setSubtitleConfigurations(mediaSubtitleConfigs)
            .build()

        if (startPositionMs > 0) {
            exoPlayer?.setMediaItem(mediaItem, startPositionMs)
        } else {
            exoPlayer?.setMediaItem(mediaItem)
        }
    }

    override fun prepare() {
        exoPlayer?.prepare()
    }

    override fun stop() {
        exoPlayer?.stop()
    }

    override fun clearMediaItems() {
        exoPlayer?.clearMediaItems()
    }

    override fun release() {
        exoPlayer?.removeListener(playerListener)
        exoPlayer?.release()
        exoPlayer = null
    }

    override fun play() {
        exoPlayer?.playWhenReady = true
    }

    override fun pause() {
        exoPlayer?.pause()
    }

    override fun togglePlayPause() {
        val player = exoPlayer ?: return
        player.playWhenReady = !player.playWhenReady
    }

    override fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
    }

    override fun seekOutsideBuffer(positionMs: Long) {
        val player = exoPlayer ?: return
        val item = player.currentMediaItem
        val isHls = item?.localConfiguration?.mimeType == MimeTypes.APPLICATION_M3U8
        if (isHls) {
            player.seekTo(positionMs)
        } else if (positionMs > player.bufferedPosition) {
            val wasPlaying = player.playWhenReady
            if (item != null) {
                val clippedItem = item.buildUpon()
                    .setClipStartPositionMs(positionMs)
                    .build()
                player.setMediaItem(clippedItem)
                player.prepare()
                player.playWhenReady = wasPlaying
            } else {
                player.seekTo(positionMs)
            }
        } else {
            player.seekTo(positionMs)
        }
    }

    override fun setPlaybackSpeed(speed: Float) {
        exoPlayer?.setPlaybackSpeed(speed)
    }

    override fun setVolume(volume: Float) {
        exoPlayer?.volume = volume
    }

    override var playWhenReady: Boolean
        get() = exoPlayer?.playWhenReady ?: false
        set(value) { exoPlayer?.playWhenReady = value }

    override fun disableSubtitles() {
        val player = exoPlayer ?: return
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
    }

    override fun rebuildWithSubtitles(
        url: String,
        subtitleConfigs: List<SubtitleConfig>,
        startPositionMs: Long,
        playWhenReady: Boolean,
    ) {
        val player = exoPlayer ?: return
        val mediaSubtitleConfigs = subtitleConfigs.map { config ->
            MediaItem.SubtitleConfiguration.Builder(config.url.toUri())
                .setMimeType(config.mimeType)
                .setLanguage(config.language)
                .setSelectionFlags(if (config.selected) C.SELECTION_FLAG_DEFAULT else 0)
                .build()
        }
        val currentItem = player.currentMediaItem ?: return
        val newItem = currentItem.buildUpon()
            .setSubtitleConfigurations(mediaSubtitleConfigs)
            .build()
        player.stop()
        player.setMediaItem(newItem, startPositionMs)
        player.prepare()
        player.playWhenReady = playWhenReady
    }

    override fun setResizeMode(mode: Int) {
        // PlayerScreen passes the official media3 AspectRatioFrameLayout constants directly:
        //   RESIZE_MODE_FIT (0)  -> 16:9 / Fit (letterbox)
        //   RESIZE_MODE_FILL (3) -> Stretch (fills the whole view, ignoring the aspect ratio)
        // Forwarding the constant lets PlayerView handle both natively.
        playerView.resizeMode = mode
    }

    override fun selectSubtitleTrack(index: Int) {
        val player = exoPlayer ?: return
        val textTracks = _currentSubtitleTracks
        if (index in textTracks.indices) {
            val track = textTracks[index]
            overrideSubtitleTrack(track.trackIndex, 0)
        }
    }

    override fun overrideSubtitleTrack(trackIndex: Int, groupIndex: Int) {
        val player = exoPlayer ?: return
        val tracks = player.currentTracks
        for (i in 0 until tracks.groups.size) {
            val group = tracks.groups[i]
            if (group.type == C.TRACK_TYPE_TEXT) {
                val override = TrackSelectionOverride(group.mediaTrackGroup, listOf(trackIndex))
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .setOverrideForType(override)
                    .build()
                return
            }
        }
    }

    fun setPlayerViewResizeMode(mode: Int) {
        setResizeMode(mode)
    }

    override fun setListener(listener: PlayerEngine.Listener) {
        this.listener = listener
    }

    fun updatePosition(position: Long, duration: Long, buffered: Long) {
        _currentPosition = position
        _duration = duration
        _bufferedPosition = buffered
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            _isPlaying = playing
            listener?.onIsPlayingChanged(playing)
        }

        override fun onPlaybackSuppressionReasonChanged(reason: Int) {
            // Buffering state managed externally
        }

        override fun onTracksChanged(tracks: Tracks) {
            val discovered = mutableListOf<EmbeddedSubtitleTrack>()
            for (groupIndex in 0 until tracks.groups.size) {
                val group = tracks.groups[groupIndex]
                if (group.type == C.TRACK_TYPE_TEXT) {
                    for (trackIndex in 0 until group.length) {
                        val format = group.getTrackFormat(trackIndex)
                        val label = format.label
                            ?: format.language
                            ?: "Track ${discovered.size + 1}"
                        discovered.add(
                            EmbeddedSubtitleTrack(
                                trackIndex = trackIndex,
                                label = label,
                            )
                        )
                    }
                }
            }
            _currentSubtitleTracks = discovered
            listener?.onTracksChanged(discovered)
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e("ExoPlayerEngine", "onPlayerError: code=${error.errorCode}")
            listener?.onError(error.message ?: "Playback error")
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _playbackState = when (playbackState) {
                Player.STATE_IDLE -> PlayerEngine.STATE_IDLE
                Player.STATE_BUFFERING -> PlayerEngine.STATE_BUFFERING
                Player.STATE_READY -> PlayerEngine.STATE_READY
                Player.STATE_ENDED -> PlayerEngine.STATE_ENDED
                else -> PlayerEngine.STATE_IDLE
            }
            listener?.onPlaybackStateChanged(_playbackState)
        }
    }
}
