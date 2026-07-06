package com.blissless.tensei.viewmodel

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.blissless.tensei.MainViewModel
import com.blissless.tensei.data.models.CachedExtensionStream
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/**
 * Cache and playback-position passthroughs for [MainViewModel].
 *
 * Extracted from MainViewModel.kt to reduce file size. All public signatures
 * are preserved as extension functions; existing call sites compile unchanged.
 */

// ─── Disk cache factory ─────────────────────────────────────────────────────

fun MainViewModel.getCacheDataSourceFactory(
    referer: String,
    extensionClient: OkHttpClient? = null,
    extensionHeaders: Map<String, String> = emptyMap(),
) = cacheManager.getCacheDataSourceFactory(referer, extensionClient, extensionHeaders)

// ─── Download cache ─────────────────────────────────────────────────────────

fun MainViewModel.getDownloadCacheSize(): Long =
    episodeDownloadManager.getDownloadCacheSize()

fun MainViewModel.clearDownloadCache() {
    viewModelScope.launch {
        episodeDownloadManager.clearDownloadCache()
        // Re-initialize for future downloads
        episodeDownloadManager.initialize()
    }
}

// ─── Playback position ──────────────────────────────────────────────────────

fun MainViewModel.savePlaybackPosition(
    animeId: Int,
    episode: Int,
    position: Long,
    duration: Long = 0L,
    isOffline: Boolean = false,
) = cacheManager.savePlaybackPosition(animeId, episode, position, duration, isOffline)

fun MainViewModel.getPlaybackPosition(
    animeId: Int,
    episode: Int,
    isOffline: Boolean = false,
) = cacheManager.getPlaybackPosition(animeId, episode, isOffline)

fun MainViewModel.clearPlaybackPosition(animeId: Int, episode: Int) =
    cacheManager.clearPlaybackPosition(animeId, episode)

fun MainViewModel.removeContinueWatchingEntry(animeId: Int, episode: Int) =
    cacheManager.removeContinueWatchingEntry(animeId, episode)

// ─── Stream cache invalidation ──────────────────────────────────────────────

fun MainViewModel.invalidateStreamCache(animeId: Int, episode: Int, category: String) {
    cacheManager.invalidateStreamCache(animeId, episode, category)
}

fun MainViewModel.getCachedExtensionStream(animeId: Int, episode: Int): CachedExtensionStream? {
    return cacheManager.getCachedExtensionStream("${animeId}_$episode")
}

fun MainViewModel.cacheExtensionStream(animeId: Int, episode: Int, data: CachedExtensionStream) {
    cacheManager.cacheExtensionStream("${animeId}_$episode", data)
}

fun MainViewModel.invalidateExtensionStreamCache(animeId: Int, episode: Int) {
    cacheManager.invalidateExtensionStreamCache("${animeId}_$episode")
}

fun MainViewModel.clearAnimeExtensionStreamCaches(animeId: Int) {
    cacheManager.clearAnimeExtensionStreamCaches(animeId)
}

fun MainViewModel.clearAllExtensionStreamCaches() {
    cacheManager.clearAllExtensionStreamCaches()
}

fun MainViewModel.removeFromVideoCache(videoUrl: String) {
    cacheManager.removeFromVideoCache(videoUrl)
}

// ─── Video cache management ─────────────────────────────────────────────────

fun MainViewModel.getVideoCacheSize(context: Context): Long =
    cacheManager.getVideoCacheSize(context)

fun MainViewModel.clearNonEssentialCaches(context: Context) =
    cacheManager.clearNonEssentialCaches(context)
