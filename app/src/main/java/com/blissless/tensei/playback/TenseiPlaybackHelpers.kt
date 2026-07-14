package com.blissless.tensei.playback

import com.blissless.tensei.data.models.ServerInfo
import com.blissless.tensei.torrent.StreamEntry
import eu.kanade.tachiyomi.animesource.model.Track
import java.net.URL

/**
 * Pure helper functions for the **Tensei** (magnet / ContentProvider-based)
 * extension playback path.
 *
 * These functions are intentionally kept separate from [com.blissless.tensei.ui.screens.player.PlaybackHelpers]
 * (which serves the aniyomi extension path) so that each extension system has
 * its own isolated, debuggable helper module. A bug or behaviour change in
 * one path cannot affect the other.
 *
 * These helpers do NOT touch Compose state — they are pure transformations
 * that can be unit-tested in isolation without an Android device.
 */

/**
 * Builds the server-selector list shown in the player UI for a Tensei stream.
 *
 * Each [StreamEntry] becomes one [ServerInfo] row. The server name combines
 * the stream's language tag (e.g. `EN-US`) with a short provider label
 * derived from the stream URL's host (e.g. `uwucdn`, `wixmp`, `miruro`).
 *
 * This mirrors the inline logic that previously lived inside
 * `MainActivity.loadAndPlayEpisode` and `PlaybackStateHolder.loadAndPlayEpisode`
 * for the `streamMethod == "magnet"` branch.
 */
fun buildTenseiServerList(streams: List<StreamEntry>): List<ServerInfo> {
    if (streams.isEmpty()) return emptyList()
    return streams.mapIndexed { idx, entry ->
        ServerInfo(
            name = "${entry.lang.uppercase()} (${tenseiProviderLabel(entry.url, idx)})",
            url = entry.url,
        )
    }
}

/**
 * Picks the best [StreamEntry] to play first given the user's preferred
 * language category ("sub" or "dub" — though Tensei streams use free-form
 * lang tags like `en-US`, `ja-JP`).
 *
 * Selection order:
 *   1. Exact lang match against [preferredLang]
 *   2. The stream flagged `isDefault` by the extension
 *   3. The first stream in the list
 *
 * Returns `null` only when [streams] is empty.
 */
fun selectPreferredTenseiStream(
    streams: List<StreamEntry>,
    preferredLang: String,
): StreamEntry? {
    if (streams.isEmpty()) return null
    return streams.firstOrNull { it.lang.equals(preferredLang, ignoreCase = true) }
        ?: streams.firstOrNull { it.isDefault }
        ?: streams.first()
}

/**
 * Picks the subtitle URL to surface as the default subtitle track.
 *
 * Prefers English-labelled subtitles, then falls back to the first
 * available subtitle. Returns `null` when the list is empty.
 */
fun pickTenseiSubtitleUrl(subtitles: List<Track>): String? {
    if (subtitles.isEmpty()) return null
    return subtitles
        .firstOrNull { s ->
            s.lang.contains("english", ignoreCase = true) ||
                s.lang.contains("en", ignoreCase = true)
        }
        ?.url
        ?: subtitles.first().url
}

/**
 * Derives a short, human-readable provider label from a Tensei stream URL.
 *
 * Known CDNs are mapped to friendly names (`uwucdn`, `wixmp`, `miruro`).
 * For any other host, the first label of the domain is used (e.g.
 * `cdn.example.com` -> `cdn`). Falls back to `server{idx+1}` when the URL
 * cannot be parsed.
 *
 * The [fallbackIndex] is the 0-based position of the stream in the original
 * list, used only to generate the fallback label.
 */
fun tenseiProviderLabel(url: String, fallbackIndex: Int = 0): String {
    val host = try {
        URL(url).host
    } catch (_: Exception) {
        return "server${fallbackIndex + 1}"
    }
    return when {
        host.contains("uwucdn") -> "uwucdn"
        host.contains("wixmp") -> "wixmp"
        host.contains("miruro") -> "miruro"
        host.isNotEmpty() -> host.substringBefore(".")
        else -> "server${fallbackIndex + 1}"
    }
}
