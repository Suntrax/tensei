package com.blissless.tensei.discord

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.blissless.tensei.BuildConfig

object DiscordRichPresence {
    private const val TAG = "DiscordRPC"
    private var initialized = false
    private val handler = Handler(Looper.getMainLooper())
    private const val CALLBACK_INTERVAL_MS = 1000L

    private val callbackRunnable = object : Runnable {
        override fun run() {
            if (initialized) {
                try {
                    DiscordNative.nativeRunCallbacks()
                } catch (_: Exception) {}
                handler.postDelayed(this, CALLBACK_INTERVAL_MS)
            }
        }
    }

    fun connect() {
        if (initialized) return
        if (!DiscordNative.isAvailable()) {
            Log.w(TAG, "Discord native SDK not available")
            return
        }
        try {
            DiscordNative.nativeInitialize(BuildConfig.DISCORD_APP_ID)
            initialized = true
            handler.post(callbackRunnable)
            Log.i(TAG, "Discord SDK initialized")
            handler.postDelayed({ setBrowsingPresence() }, 2000)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Discord SDK", e)
        }
    }

    fun disconnect() {
        if (!initialized) return
        handler.removeCallbacks(callbackRunnable)
        try {
            DiscordNative.nativeDestroy()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to destroy Discord SDK", e)
        }
        initialized = false
        Log.i(TAG, "Discord SDK destroyed")
    }

    fun setAnimePresence(
        animeName: String,
        episode: Int,
        totalEpisodes: Int,
        durationMs: Long = 0L,
        currentPositionMs: Long = 0L,
    ) {
        if (!initialized) return
        val epText = if (totalEpisodes > 0) "Episode $episode / $totalEpisodes" else "Episode $episode"
        val nowSec = System.currentTimeMillis() / 1000
        val endTs: Long
        val startTs: Long
        if (durationMs > 0 && currentPositionMs >= 0) {
            val remainingMs = (durationMs - currentPositionMs).coerceAtLeast(0)
            endTs = nowSec + (remainingMs / 1000)
            startTs = nowSec - (currentPositionMs / 1000)
        } else {
            endTs = 0L
            startTs = 0L
        }
        Log.i(TAG, "setAnimePresence: $animeName - $epText, startTs=$startTs endTs=$endTs")
        DiscordNative.nativeSetPresence(
            details = animeName,
            state = epText,
            type = 3,
            startTimestamp = startTs,
            endTimestamp = endTs,
            largeImage = "tensei",
            largeText = "Tensei",
            smallImage = null,
            smallText = null,
            largeUrl = "https://github.com/Suntrax/tensei",
        )
    }

    fun setMangaPresence(
        mangaTitle: String,
        chapterLabel: String,
    ) {
        if (!initialized) return
        val startTs = System.currentTimeMillis() / 1000
        Log.i(TAG, "setMangaPresence: $mangaTitle - $chapterLabel")
        DiscordNative.nativeSetPresence(
            details = mangaTitle,
            state = chapterLabel,
            type = 3,
            startTimestamp = startTs,
            endTimestamp = 0,
            largeImage = "tensei",
            largeText = "Tensei",
            smallImage = null,
            smallText = null,
            largeUrl = "https://github.com/Suntrax/tensei",
        )
    }

    fun setBrowsingPresence() {
        if (!initialized) return
        Log.i(TAG, "setBrowsingPresence")
        DiscordNative.nativeSetPresence(
            details = "Tensei",
            state = "Exploring Anime & Manga",
            type = 3,
            startTimestamp = 0,
            endTimestamp = 0,
            largeImage = "tensei",
            largeText = "Tensei",
            smallImage = null,
            smallText = null,
            largeUrl = "https://github.com/Suntrax/tensei",
        )
    }

    fun clearPresence() {
        if (!initialized) return
        Log.i(TAG, "clearPresence")
        setBrowsingPresence()
    }

    fun isConnected(): Boolean = initialized
}
