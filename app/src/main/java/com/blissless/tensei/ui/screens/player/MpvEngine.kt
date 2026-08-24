package com.blissless.tensei.ui.screens.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import `is`.xyz.mpv.MPV

class MpvEngine(private val context: Context) : PlayerEngine {

    companion object {
        private const val TAG = "MpvEngine"
    }

    private var listener: PlayerEngine.Listener? = null
    private var mpv: MPV? = null

    private var _isPlaying = false
    private var _currentPosition = 0L
    private var _duration = 0L
    private var _bufferedPosition = 0L
    private var _playbackState = PlayerEngine.STATE_IDLE
    private var _currentSubtitleTracks = emptyList<EmbeddedSubtitleTrack>()
    private var _isHls = false
    private var _isPaused = true

    private var pendingUrl: String? = null
    private var pendingHeaders: Map<String, String> = emptyMap()
    private var pendingReferer: String = ""
    private var surfaceReady = false
    private var currentSurface: Surface? = null
    private val positionPoller = Handler(Looper.getMainLooper())
    private val positionPollRunnable = object : Runnable {
        override fun run() {
            val player = mpv ?: return
            try {
                val pos = player.getPropertyInt("time-pos")
                if (pos != null) {
                    _currentPosition = pos * 1000L
                }
                val dur = player.getPropertyDouble("duration")
                if (dur != null) {
                    _duration = (dur * 1000).toLong()
                }
                val paused = player.getPropertyBoolean("pause")
                if (paused != null) {
                    val playing = !paused
                    if (playing != _isPlaying) {
                        _isPlaying = playing
                        _isPaused = paused
                        listener?.onIsPlayingChanged(playing)
                    }
                }
                val buffering = player.getPropertyBoolean("paused-for-cache")
                if (buffering != null) {
                    val newState = if (buffering) PlayerEngine.STATE_BUFFERING else PlayerEngine.STATE_READY
                    if (newState != _playbackState && _playbackState != PlayerEngine.STATE_IDLE && _playbackState != PlayerEngine.STATE_ENDED) {
                        _playbackState = newState
                        listener?.onPlaybackStateChanged(newState)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Position poll error", e)
            }
            positionPoller.postDelayed(this, 250)
        }
    }

    private val mpvView: MpvSurfaceView by lazy {
        Log.d(TAG, "Creating MpvSurfaceView")
        MpvSurfaceView(context).also { view ->
            view.holder.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) {
                    Log.d(TAG, "Surface created")
                    surfaceReady = true
                    currentSurface = holder.surface
                    mpv?.attachSurface(holder.surface)
                    pendingUrl?.let { url ->
                        Log.d(TAG, "Loading pending URL: ${url.take(100)}")
                        loadFileWithHeaders(url)
                        pendingUrl = null
                    }
                    startPositionPolling()
                }

                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                    Log.d(TAG, "Surface changed: ${width}x$height")
                }

                override fun surfaceDestroyed(holder: SurfaceHolder) {
                    Log.d(TAG, "Surface destroyed")
                    surfaceReady = false
                    currentSurface = null
                    stopPositionPolling()
                    mpv?.detachSurface()
                }
            })
        }
    }

    private val eventObserver = object : MPV.EventObserver {
        override fun eventProperty(property: String) {
            Log.v(TAG, "eventProperty(no value): $property")
        }

        override fun eventProperty(property: String, value: Long) {
            when (property) {
                "time-pos" -> _currentPosition = value * 1000
            }
        }

        override fun eventProperty(property: String, value: Boolean) {
            Log.d(TAG, "eventProperty bool: $property = $value")
            when (property) {
                "pause" -> {
                    _isPlaying = !value
                    _isPaused = value
                    listener?.onIsPlayingChanged(_isPlaying)
                }
                "paused-for-cache" -> {
                    val newState = if (value) PlayerEngine.STATE_BUFFERING else PlayerEngine.STATE_READY
                    if (newState != _playbackState && _playbackState != PlayerEngine.STATE_IDLE) {
                        _playbackState = newState
                        listener?.onPlaybackStateChanged(_playbackState)
                    }
                }
            }
        }

        override fun eventProperty(property: String, value: String) {
            Log.v(TAG, "eventProperty str: $property = $value")
            when (property) {
                "track-list" -> loadTracksFromMpv()
            }
        }

        override fun eventProperty(property: String, value: Double) {
            when (property) {
                "duration" -> _duration = (value * 1000).toLong()
            }
        }

        override fun eventProperty(property: String, value: `is`.xyz.mpv.MPVNode) {}

        override fun event(eventId: Int, data: `is`.xyz.mpv.MPVNode) {
            Log.d(TAG, "mpv event: $eventId")
            when (eventId) {
                MPV.mpvEvent.MPV_EVENT_FILE_LOADED -> {
                    Log.d(TAG, "FILE_LOADED")
                    _playbackState = PlayerEngine.STATE_READY
                    listener?.onPlaybackStateChanged(PlayerEngine.STATE_READY)
                    val dur = mpv?.getPropertyDouble("duration")
                    if (dur != null) _duration = (dur * 1000).toLong()
                    loadTracksFromMpv()
                    listener?.onIsPlayingChanged(true)
                }
                MPV.mpvEvent.MPV_EVENT_END_FILE -> {
                    Log.d(TAG, "END_FILE")
                    _playbackState = PlayerEngine.STATE_ENDED
                    listener?.onPlaybackStateChanged(PlayerEngine.STATE_ENDED)
                }
                MPV.mpvEvent.MPV_EVENT_START_FILE -> {
                    Log.d(TAG, "START_FILE")
                    _playbackState = PlayerEngine.STATE_BUFFERING
                    listener?.onPlaybackStateChanged(PlayerEngine.STATE_BUFFERING)
                }
            }
        }
    }

    private var mpvInitFailed = false

    private fun ensureInit(): Boolean {
        if (mpvInitFailed) return false
        if (mpv != null) return true
        Log.d(TAG, "Initializing MPV")
        try {
            mpv = MPV().apply {
                create(context)

                setOptionString("config", "no")
                setOptionString("vo", "gpu")
                setOptionString("hwdec", "mediacodec")
                setOptionString("hwdec-codecs", "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1")
                setOptionString("ao", "audiotrack")
                setOptionString("input-default-bindings", "no")
                setOptionString("input-vo-keyboard", "no")
                setOptionString("idle", "once")
                setOptionString("demuxer-max-bytes", "67108864")
                setOptionString("demuxer-max-back-bytes", "67108864")
                setOptionString("save-position-on-quit", "no")
                setOptionString("gpu-context", "android")
                setOptionString("opengl-es", "yes")
                setOptionString("keep-open", "no")

                init()

                addObserver(eventObserver)
                observeProperty("time-pos", MPV.mpvFormat.MPV_FORMAT_INT64)
                observeProperty("duration", MPV.mpvFormat.MPV_FORMAT_DOUBLE)
                observeProperty("pause", MPV.mpvFormat.MPV_FORMAT_FLAG)
                observeProperty("paused-for-cache", MPV.mpvFormat.MPV_FORMAT_FLAG)
                observeProperty("track-list", MPV.mpvFormat.MPV_FORMAT_NONE)
            }
        } catch (e: Error) {
            Log.e(TAG, "MPV native library failed to load: ${e.message}", e)
            mpvInitFailed = true
            mpv = null
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MPV", e)
            mpvInitFailed = true
            mpv = null
            return false
        }
        Log.d(TAG, "MPV initialized, surfaceReady=$surfaceReady")

        if (surfaceReady) {
            currentSurface?.let { surface ->
                Log.d(TAG, "Attaching surface to newly created mpv")
                mpv?.attachSurface(surface)
            }
        }
        return true
    }

    override val view: View get() = mpvView
    override val isPlaying: Boolean get() = _isPlaying
    override val currentPosition: Long get() = _currentPosition
    override val duration: Long get() = _duration
    override val bufferedPosition: Long get() = _bufferedPosition
    override val playbackState: Int get() = _playbackState
    override val currentSubtitleTracks: List<EmbeddedSubtitleTrack> get() = _currentSubtitleTracks
    override val isHls: Boolean get() = _isHls

    override var playWhenReady: Boolean
        get() = !_isPaused
        set(value) { if (value) play() else pause() }

    override fun loadMedia(
        url: String,
        mimeType: String?,
        startPositionMs: Long,
        subtitleConfigs: List<SubtitleConfig>,
        headers: Map<String, String>,
        referer: String,
        httpClient: Any?,
    ) {
        Log.d(TAG, "loadMedia: ${url.take(120)} startMs=$startPositionMs headers=${headers.size} subs=${subtitleConfigs.size}")
        if (!ensureInit()) {
            listener?.onError("MPV native library is not compatible with this device")
            return
        }
        _isHls = url.contains(".m3u8") || url.contains("/m3u8")

        pendingHeaders = headers
        pendingReferer = referer

        for (config in subtitleConfigs) {
            Log.d(TAG, "Adding subtitle: ${config.url.take(80)}")
            mpv?.command("sub-add", config.url, "select")
        }

        if (surfaceReady) {
            Log.d(TAG, "Surface ready, loading file now")
            loadFileWithHeaders(url)
        } else {
            Log.d(TAG, "Surface not ready, storing pending URL")
            pendingUrl = url
        }

        if (startPositionMs > 0) {
            Handler(Looper.getMainLooper()).postDelayed({
                Log.d(TAG, "Restoring position: ${startPositionMs}ms")
                mpv?.command("seek", startPositionMs.toString(), "absolute=exact")
            }, 500)
        }
    }

    private fun loadFileWithHeaders(url: String) {
        val player = mpv ?: return
        val allHeaders = pendingHeaders.toMutableMap()
        if (pendingReferer.isNotEmpty()) allHeaders["Referer"] = pendingReferer

        if (allHeaders.isNotEmpty()) {
            val headerString = allHeaders.entries.joinToString(",") { "${it.key}: ${it.value}" }
            Log.d(TAG, "Setting http-header-fields: $headerString")
            try {
                player.setPropertyString("http-header-fields", headerString)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set http-header-fields", e)
            }
        }

        Log.d(TAG, "loadfile $url")
        player.command("loadfile", url)
    }

    override fun prepare() {
        Log.d(TAG, "prepare()")
    }

    override fun stop() {
        Log.d(TAG, "stop()")
        mpv?.command("stop")
    }

    override fun clearMediaItems() {
        Log.d(TAG, "clearMediaItems()")
        mpv?.command("stop")
    }

    override fun release() {
        Log.d(TAG, "release()")
        stopPositionPolling()
        surfaceReady = false
        currentSurface = null
        try { mpv?.removeObserver(eventObserver) } catch (_: Exception) {}
        try { mpv?.destroySession() } catch (_: Exception) {}
        try { mpv?.destroy() } catch (_: Exception) {}
        mpv = null
    }

    override fun play() {
        Log.d(TAG, "play()")
        _isPaused = false
        mpv?.setPropertyBoolean("pause", false)
    }

    override fun pause() {
        Log.d(TAG, "pause()")
        _isPaused = true
        mpv?.setPropertyBoolean("pause", true)
    }

    override fun togglePlayPause() {
        Log.d(TAG, "togglePlayPause()")
        mpv?.command("cycle", "pause")
    }

    override fun seekTo(positionMs: Long) {
        Log.d(TAG, "seekTo($positionMs)")
        mpv?.command("seek", positionMs.toString(), "absolute=exact")
    }

    override fun seekOutsideBuffer(positionMs: Long) {
        Log.d(TAG, "seekOutsideBuffer($positionMs)")
        mpv?.command("seek", positionMs.toString(), "absolute=exact")
    }

    override fun setPlaybackSpeed(speed: Float) {
        Log.d(TAG, "setPlaybackSpeed($speed)")
        mpv?.setPropertyDouble("speed", speed.toDouble())
    }

    override fun setVolume(volume: Float) {
        mpv?.setPropertyDouble("volume", (volume * 100).toDouble())
    }

    override fun disableSubtitles() {
        Log.d(TAG, "disableSubtitles()")
        mpv?.setPropertyInt("sid", 0)
    }

    override fun rebuildWithSubtitles(
        url: String,
        subtitleConfigs: List<SubtitleConfig>,
        startPositionMs: Long,
        playWhenReady: Boolean,
    ) {
        Log.d(TAG, "rebuildWithSubtitles: subs=${subtitleConfigs.size}")
        for (config in subtitleConfigs) {
            mpv?.command("sub-add", config.url, "select")
        }
        if (startPositionMs > 0) {
            Handler(Looper.getMainLooper()).postDelayed({
                mpv?.command("seek", startPositionMs.toString(), "absolute=exact")
            }, 200)
        }
    }

    override fun setResizeMode(mode: Int) {
        Log.d(TAG, "setResizeMode($mode) - not supported in mpv engine")
    }

    override fun selectSubtitleTrack(index: Int) {
        Log.d(TAG, "selectSubtitleTrack($index)")
        mpv?.setPropertyInt("sid", index)
    }

    override fun overrideSubtitleTrack(trackIndex: Int, groupIndex: Int) {
        Log.d(TAG, "overrideSubtitleTrack(track=$trackIndex, group=$groupIndex)")
        mpv?.setPropertyInt("sid", trackIndex)
    }

    override fun setListener(listener: PlayerEngine.Listener) {
        this.listener = listener
    }

    private fun loadTracksFromMpv() {
        val player = mpv ?: return
        val count = player.getPropertyInt("track-list/count") ?: return
        Log.d(TAG, "loadTracksFromMpv: count=$count")
        val tracks = mutableListOf<EmbeddedSubtitleTrack>()
        for (i in 0 until count) {
            val type = player.getPropertyString("track-list/$i/type") ?: continue
            if (type == "sub") {
                val id = player.getPropertyInt("track-list/$i/id") ?: continue
                val lang = player.getPropertyString("track-list/$i/lang")
                val title = player.getPropertyString("track-list/$i/title")
                val label = when {
                    !title.isNullOrEmpty() && !lang.isNullOrEmpty() -> "$title ($lang)"
                    !title.isNullOrEmpty() -> title
                    !lang.isNullOrEmpty() -> lang
                    else -> "Track $id"
                }
                Log.d(TAG, "  Sub track: id=$id label=$label")
                tracks.add(EmbeddedSubtitleTrack(trackIndex = id, label = label))
            }
        }
        _currentSubtitleTracks = tracks
        listener?.onTracksChanged(tracks)
    }

    private fun startPositionPolling() {
        positionPoller.removeCallbacks(positionPollRunnable)
        positionPoller.post(positionPollRunnable)
    }

    private fun stopPositionPolling() {
        positionPoller.removeCallbacks(positionPollRunnable)
    }

    private class MpvSurfaceView(context: Context) : SurfaceView(context)
}
