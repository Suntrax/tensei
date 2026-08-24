package com.blissless.tensei.ui.screens.player

import android.content.Context
import android.view.View

interface PlayerEngine {
    val view: View

    fun loadMedia(
        url: String,
        mimeType: String?,
        startPositionMs: Long,
        subtitleConfigs: List<SubtitleConfig>,
        headers: Map<String, String>,
        referer: String,
        httpClient: Any?,
    )
    fun prepare()
    fun stop()
    fun clearMediaItems()
    fun release()
    fun play()
    fun pause()
    fun togglePlayPause()
    fun seekTo(positionMs: Long)
    fun seekOutsideBuffer(positionMs: Long)
    fun setPlaybackSpeed(speed: Float)
    fun setVolume(volume: Float)
    fun disableSubtitles()
    fun rebuildWithSubtitles(
        url: String,
        subtitleConfigs: List<SubtitleConfig>,
        startPositionMs: Long,
        playWhenReady: Boolean,
    )
    fun setResizeMode(mode: Int)
    fun selectSubtitleTrack(index: Int)
    fun overrideSubtitleTrack(trackIndex: Int, groupIndex: Int)

    val isPlaying: Boolean
    val currentPosition: Long
    val duration: Long
    val bufferedPosition: Long
    val playbackState: Int
    val currentSubtitleTracks: List<EmbeddedSubtitleTrack>
    val isHls: Boolean
    var playWhenReady: Boolean

    fun setListener(listener: Listener)

    interface Listener {
        fun onIsPlayingChanged(isPlaying: Boolean) {}
        fun onPlaybackStateChanged(state: Int) {}
        fun onError(error: String) {}
        fun onTracksChanged(tracks: List<EmbeddedSubtitleTrack>) {}
    }

    companion object {
        const val STATE_IDLE = 1
        const val STATE_BUFFERING = 2
        const val STATE_READY = 3
        const val STATE_ENDED = 4

        const val RESIZE_MODE_FIT = 0
        const val RESIZE_MODE_FILL = 1
        const val RESIZE_MODE_FIXED_WIDTH = 2
    }
}

data class SubtitleConfig(
    val url: String,
    val mimeType: String,
    val language: String,
    val selected: Boolean,
)

data class EmbeddedSubtitleTrack(
    val trackIndex: Int,
    val label: String,
)
