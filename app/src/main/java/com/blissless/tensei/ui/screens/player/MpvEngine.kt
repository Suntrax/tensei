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
import `is`.xyz.mpv.MPVNode
import java.io.File

object Anime4KShaders {
    private val SHADER_CHAINS = mapOf(
        "mode_a" to listOf(
            "Anime4K_Clamp_Highlights.glsl",
            "Anime4K_Restore_CNN_S.glsl",
            "Anime4K_Upscale_CNN_x2_S.glsl",
        ),
        "mode_b" to listOf(
            "Anime4K_Clamp_Highlights.glsl",
            "Anime4K_Restore_CNN_M.glsl",
            "Anime4K_Upscale_CNN_x2_M.glsl",
        ),
        "mode_c" to listOf(
            "Anime4K_Clamp_Highlights.glsl",
            "Anime4K_Restore_CNN_VL.glsl",
            "Anime4K_Upscale_CNN_x2_VL.glsl",
        ),
        "deblur" to listOf("Anime4K_Deblur_DoG.glsl"),
        "denoise" to listOf("Anime4K_Denoise_Bilateral_Mode.glsl"),
    )

    fun getChain(shaderKey: String): List<String> = SHADER_CHAINS[shaderKey].orEmpty()

    fun copyShadersToInternal(context: Context): File {
        val destDir = File(context.filesDir, "shaders")
        if (!destDir.exists()) destDir.mkdirs()
        val allShaders = SHADER_CHAINS.values.flatten().distinct()
        for (name in allShaders) {
            val dest = File(destDir, name)
            if (!dest.exists() || dest.length() == 0L) {
                context.assets.open("shaders/$name").use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
        return destDir
    }
}

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
    private var surfaceReady = false
    private var currentSurface: Surface? = null
    var anime4kShader: String = "none"

    private var initHeaders: Map<String, String> = emptyMap()
    private var initReferer: String = ""
    private var initShaderKey: String = "none"

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
                        loadFileDirect(url)
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
                    Log.d("serverChange", "FILE_LOADED")
                    _playbackState = PlayerEngine.STATE_READY
                    // Keep the engine isPlaying state in sync with the listener
                    // notification so PlayerData.playerEngine.isPlaying (used by the
                    // PIP play/pause icon) reflects actual playback after load.
                    _isPlaying = true
                    _isPaused = false
                    listener?.onPlaybackStateChanged(PlayerEngine.STATE_READY)
                    val dur = mpv?.getPropertyDouble("duration")
                    if (dur != null) _duration = (dur * 1000).toLong()
                    loadTracksFromMpv()
                    listener?.onIsPlayingChanged(true)
                }
                MPV.mpvEvent.MPV_EVENT_END_FILE -> {
                    var rawReason: Any = "N/A"
                    try { rawReason = data?.get("reason") ?: "NULL" } catch (_: Exception) { rawReason = "ERR" }
                    Log.d("serverChange", "END_FILE id=$eventId data=$data rawReason=$rawReason")
                    val reason = endFileReason(data)
                    Log.d("serverChange", "END_FILE resolvedReason=$reason")
                    when (reason) {
                        // Genuine end of file (mpv_end_file_reason EOF)
                        0L -> {
                            _playbackState = PlayerEngine.STATE_ENDED
                            listener?.onPlaybackStateChanged(PlayerEngine.STATE_ENDED)
                        }
                        // ERROR: surface as error so PlayerScreen's retry logic (seek retry)
                        // handles it instead of treating it as the episode ending.
                        4L -> listener?.onError("MPV playback error during seek")
                        // STOP/QUIT/REDIRECT: transitional (episode change, stop(), or the
                        // demuxer reload that happens on seeks of streamed/HLS sources).
                        // Do NOT treat these as the episode ending, otherwise seeking would
                        // advance to the next episode / clear playback position.
                        else -> Log.d("serverChange", "END_FILE transitional (reason=$reason), not advancing")
                    }
                }
                MPV.mpvEvent.MPV_EVENT_START_FILE -> {
                    Log.d("serverChange", "START_FILE")
                    _playbackState = PlayerEngine.STATE_BUFFERING
                    listener?.onPlaybackStateChanged(PlayerEngine.STATE_BUFFERING)
                }
                MPV.mpvEvent.MPV_EVENT_SEEK -> {
                    Log.d(TAG, "SEEK")
                    _playbackState = PlayerEngine.STATE_BUFFERING
                    listener?.onPlaybackStateChanged(PlayerEngine.STATE_BUFFERING)
                }
                MPV.mpvEvent.MPV_EVENT_PLAYBACK_RESTART -> {
                    Log.d(TAG, "PLAYBACK_RESTART")
                    // Playback restarted after a seek/buffer. Report READY so PlayerScreen
                    // clears its isBuffering (loading) flag. If mpv is still paused-for-cache,
                    // leave BUFFERING and let the position poller flip to READY when it resumes.
                    val stillBuffering = mpv?.getPropertyBoolean("paused-for-cache") ?: false
                    if (!stillBuffering && _playbackState != PlayerEngine.STATE_READY) {
                        _playbackState = PlayerEngine.STATE_READY
                        listener?.onPlaybackStateChanged(PlayerEngine.STATE_READY)
                    }
                }
            }
        }
    }

    private var mpvInitFailed = false

    private fun destroyMpv() {
        try { mpv?.removeObserver(eventObserver) } catch (_: Exception) {}
        try { mpv?.destroySession() } catch (_: Exception) {}
        try { mpv?.destroy() } catch (_: Exception) {}
        mpv = null
    }

    private fun initMpv(headers: Map<String, String>, referer: String, shaderKey: String): Boolean {
        Log.d(TAG, "initMpv: headers=${headers.size} referer=${referer.take(40)} shader=$shaderKey")
        try {
            val player = MPV()
            player.create(context)

            player.setOptionString("config", "no")
            player.setOptionString("vo", "gpu")
            player.setOptionString("hwdec", "mediacodec")
            player.setOptionString("hwdec-codecs", "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1")
            player.setOptionString("ao", "audiotrack")
            player.setOptionString("input-default-bindings", "no")
            player.setOptionString("input-vo-keyboard", "no")
            player.setOptionString("idle", "once")
            player.setOptionString("demuxer-max-bytes", "67108864")
            player.setOptionString("demuxer-max-back-bytes", "67108864")
            player.setOptionString("save-position-on-quit", "no")
            player.setOptionString("gpu-context", "android")
            player.setOptionString("opengl-es", "yes")
            player.setOptionString("keep-open", "no")
            player.setOptionString("ytdl", "no")

            val userAgent = headers["User-Agent"]
            if (userAgent != null) {
                val r = player.setOptionString("user-agent", userAgent)
                Log.d(TAG, "setOptionString user-agent: result=$r")
            }
            if (referer.isNotEmpty()) {
                val r = player.setOptionString("http-header-fields", "\nReferer: $referer")
                Log.d(TAG, "setOptionString http-header-fields (before init): result=$r")
            }

            val chain = Anime4KShaders.getChain(shaderKey)
            if (chain.isNotEmpty()) {
                val shaderDir = Anime4KShaders.copyShadersToInternal(context)
                val shaderPaths = chain.joinToString("\n") { File(shaderDir, it).absolutePath }
                val r = player.setOptionString("glsl-shaders", "\n$shaderPaths")
                Log.d(TAG, "setOptionString glsl-shaders: result=$r chain=$shaderKey")
            }

            player.init()
            player.addObserver(eventObserver)
            player.observeProperty("time-pos", MPV.mpvFormat.MPV_FORMAT_INT64)
            player.observeProperty("duration", MPV.mpvFormat.MPV_FORMAT_DOUBLE)
            player.observeProperty("pause", MPV.mpvFormat.MPV_FORMAT_FLAG)
            player.observeProperty("paused-for-cache", MPV.mpvFormat.MPV_FORMAT_FLAG)
            player.observeProperty("track-list", MPV.mpvFormat.MPV_FORMAT_NONE)

            if (referer.isNotEmpty()) {
                val headerNode = MPVNode.ArrayNode(arrayOf(MPVNode.StringNode("Referer: $referer")))
                player.setPropertyNode("http-header-fields", headerNode)
                Log.d(TAG, "setPropertyNode http-header-fields (after init): Referer=$referer")
            }

            mpv = player
            initHeaders = headers
            initReferer = referer
            initShaderKey = shaderKey

            Log.d(TAG, "MPV initialized successfully")
            return true
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "MPV native library not available: ${e.message}", e)
            mpvInitFailed = true
            return false
        } catch (e: Error) {
            Log.e(TAG, "MPV native library failed to load: ${e.message}", e)
            mpvInitFailed = true
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MPV", e)
            mpvInitFailed = true
            return false
        }
    }

        override val view: View get() = mpvView
    override val isPlaying: Boolean get() = _isPlaying

    fun liveIsPlaying(): Boolean? {
        val paused = try { mpv?.getPropertyBoolean("pause") } catch (_: Exception) { null }
        return paused?.let { !it }
    }


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
        Log.d("serverChange", "MPV loadMedia: ${url.take(120)}")
        _isHls = url.contains(".m3u8") || url.contains("/m3u8")

        val needsReinit = mpv != null && (
            headers != initHeaders || referer != initReferer || anime4kShader != initShaderKey
        )
        if (needsReinit) {
            Log.d(TAG, "Headers or shader changed, recreating MPV")
            destroyMpv()
        }

        if (mpv == null) {
            if (mpvInitFailed) {
                listener?.onError("MPV native library is not compatible with this device")
                return
            }
            if (!initMpv(headers, referer, anime4kShader)) {
                listener?.onError("MPV native library is not compatible with this device")
                return
            }
        }

        if (surfaceReady) {
            currentSurface?.let { mpv?.attachSurface(it) }
        }

        for (config in subtitleConfigs) {
            Log.d(TAG, "Adding subtitle: ${config.url.take(80)}")
            mpv?.command("sub-add", config.url, "select")
        }

        if (surfaceReady) {
            Log.d(TAG, "Surface ready, loading file now")
            loadFileDirect(url)
        } else {
            Log.d(TAG, "Surface not ready, storing pending URL")
            pendingUrl = url
        }

        if (startPositionMs > 0) {
            Handler(Looper.getMainLooper()).postDelayed({
                Log.d(TAG, "Restoring position: ${startPositionMs}ms -> seek seconds=${startPositionMs / 1000}")
                mpv?.command("seek", (startPositionMs / 1000).toString(), "absolute+exact")
            }, 500)
        }
    }

    private fun loadFileDirect(url: String) {
        val player = mpv ?: return
        Log.d(TAG, "loadfile $url")
        player.command("loadfile", url)
    }

    override fun prepare() {
        Log.d(TAG, "prepare()")
    }

    override fun stop() {
        Log.d("serverChange", "MPV stop()")
        mpv?.command("stop")
    }

    override fun clearMediaItems() {
        Log.d("serverChange", "MPV clearMediaItems()")
        mpv?.command("stop")
    }

    override fun release() {
        Log.d(TAG, "release()")
        stopPositionPolling()
        surfaceReady = false
        currentSurface = null
        destroyMpv()
    }

    override fun play() {
        Log.d(TAG, "play()")
        _isPaused = false
        // Set isPlaying directly (not just via the pause poll/event) so the engine state,
        // which the PIP play/pause icon reads, is correct immediately on play.
        _isPlaying = true
        mpv?.setPropertyBoolean("pause", false)
        listener?.onIsPlayingChanged(true)
    }

    override fun pause() {
        Log.d(TAG, "pause()")
        _isPaused = true
        _isPlaying = false
        mpv?.setPropertyBoolean("pause", true)
        listener?.onIsPlayingChanged(false)
    }

    override fun togglePlayPause() {
        Log.d(TAG, "togglePlayPause()")
        _isPlaying = !_isPlaying
        _isPaused = !_isPlaying
        mpv?.command("cycle", "pause")
        listener?.onIsPlayingChanged(_isPlaying)
    }

    override fun seekTo(positionMs: Long) {
        Log.d(TAG, "seekTo($positionMs)ms -> seek seconds=${positionMs / 1000}")
        mpv?.command("seek", (positionMs / 1000).toString(), "absolute+exact")
    }

    override fun seekOutsideBuffer(positionMs: Long) {
        Log.d(TAG, "seekOutsideBuffer($positionMs)ms -> seek seconds=${positionMs / 1000}")
        mpv?.command("seek", (positionMs / 1000).toString(), "absolute+exact")
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
                mpv?.command("seek", (startPositionMs / 1000).toString(), "absolute+exact")
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

    // Reads mpv_end_file_reason from the MPV_EVENT_END_FILE data node.
    // This binding reports the reason as a String ("end-of-file", "stop", "error",
    // "quit", "redirect"). Map it to the mpv_end_file_reason constants:
    // 0 = EOF (genuine end), 2 = STOP, 3 = QUIT, 4 = ERROR, 5 = REDIRECT.
    // Only a genuine "end-of-file" should advance the episode.
    private fun endFileReason(data: MPVNode): Long {
        val node = try { data["reason"] } catch (_: Exception) { null } ?: return 0L
        val intValue = try { node.asInt() } catch (_: Exception) { null }
        if (intValue != null) return intValue
        return when (try { node.asString() } catch (_: Exception) { null }) {
            "end-of-file" -> 0L
            "stop" -> 2L
            "quit" -> 3L
            "error" -> 4L
            "redirect" -> 5L
            else -> 0L
        }
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
