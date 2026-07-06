package com.blissless.tensei.ui.screens.player

import com.blissless.tensei.data.models.AnimeMedia
import com.blissless.tensei.data.models.QualityOption
import com.blissless.tensei.data.models.ServerInfo
import com.blissless.tensei.MainViewModel
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.Video

/**
 * Pure helper functions for episode playback.
 *
 * Extracted from MainActivity.kt. These functions do NOT touch Compose
 * state — they are pure transformations or data builders that can be
 * unit-tested in isolation.
 *
 * The stateful playback methods (loadAndPlayEpisode, playTorrent, etc.)
 * remain in MainActivity because they close over ~30 remember{} state
 * holders and would require a state-holder class extraction to move.
 * That extraction is a larger architectural change tracked separately.
 */

/**
 * Strips the "Episode N:" or "Ep. N:" prefix from an episode title.
 *
 * Returns null if the input is null. Returns the cleaned title otherwise.
 *
 * Example: "Episode 12: The Final Battle" -> "The Final Battle"
 *          "Ep. 3 - Confrontation" -> "Confrontation"
 *          "12" -> "12" (no prefix to strip)
 */
fun sanitizeEpisodeTitle(title: String?): String? {
    if (title == null) return null
    return title.replaceFirst(
        Regex("^Ep\\.?(?:isode)?\\s*\\d+[\\s:\\-–—]+", RegexOption.IGNORE_CASE),
        ""
    ).trim()
}

/**
 * Builds a list of [ServerInfo] from a list of [Hoster] objects.
 * Each hoster becomes a server entry with its name and URL.
 */
fun buildServerList(hosters: List<Hoster>?): List<ServerInfo> {
    if (hosters.isNullOrEmpty()) return emptyList()
    return hosters.map { hoster ->
        ServerInfo(name = hoster.hosterName, url = hoster.hosterUrl)
    }
}

/**
 * Builds a list of [QualityOption] from a list of [Video] objects.
 * Each video becomes a quality option with its title, URL, and resolution.
 */
fun buildQualityOptions(videos: List<Video>?): List<QualityOption> {
    if (videos.isNullOrEmpty()) return emptyList()
    return videos.map { v ->
        QualityOption(
            quality = v.videoTitle,
            url = v.videoUrl,
            width = v.resolution ?: 0
        )
    }
}

/**
 * Determines whether a video URL points to our local proxy server.
 * Used to decide whether to pass the extension's OkHttpClient to ExoPlayer.
 */
fun isOurProxyUrl(videoUrl: String, proxyPort: Int): Boolean {
    return videoUrl.contains("127.0.0.1:$proxyPort") ||
           videoUrl.contains("localhost:$proxyPort")
}

/**
 * Rewrites a localhost URL to use our proxy port instead of whatever
 * port the extension's internal server used.
 *
 * Example: "http://127.0.0.1:8080/video.mp4" -> "http://127.0.0.1:41223/video.mp4"
 */
fun rewriteToOurProxy(videoUrl: String, proxyPort: Int): String {
    return videoUrl
        .replace(Regex("127\\.0\\.0\\.1:\\d+"), "127.0.0.1:$proxyPort")
        .replace(Regex("localhost:\\d+"), "127.0.0.1:$proxyPort")
}

/**
 * Picks the best video from a list based on resolution.
 * Falls back to extracting digits from the title if resolution is null/zero.
 * Returns the last video if no resolution info is available.
 */
fun pickBestVideo(videos: List<Video>): Video {
    return videos.maxByOrNull {
        val res = it.resolution ?: 0
        if (res == 0) it.videoTitle.filter { c -> c.isDigit() }.toIntOrNull() ?: 0
        else res
    } ?: videos.last()
}

/**
 * Sorts subtitle tracks by language preference.
 * Preferred language first, then English, then everything else.
 */
fun sortSubtitleTracks(
    tracks: List<eu.kanade.tachiyomi.animesource.model.Track>,
    preferredLang: String,
): List<eu.kanade.tachiyomi.animesource.model.Track> {
    return tracks.sortedByDescending { t ->
        when {
            t.lang.equals(preferredLang, ignoreCase = true) -> 2
            t.lang.equals("English", ignoreCase = true) -> 1
            else -> 0
        }
    }
}
