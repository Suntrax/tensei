package com.blissless.tensei.discord

import android.util.Log

object DiscordNative {
    private const val TAG = "DiscordNative"
    private var loaded = false

    init {
        try {
            System.loadLibrary("tensei_discord")
            loaded = true
            Log.i(TAG, "Native library loaded")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native library", e)
            loaded = false
        }
    }

    fun isAvailable(): Boolean = loaded

    external fun nativeInitialize(appId: String)
    external fun nativeSetPresence(
        details: String?,
        state: String?,
        type: Int,
        startTimestamp: Long,
        endTimestamp: Long,
        largeImage: String?,
        largeText: String?,
        smallImage: String?,
        smallText: String?,
        largeUrl: String?,
        smallUrl: String?,
    )
    external fun nativeClearPresence()
    external fun nativeRunCallbacks()
    external fun nativeDestroy()
}
