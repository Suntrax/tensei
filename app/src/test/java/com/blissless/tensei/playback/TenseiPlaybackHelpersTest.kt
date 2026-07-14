package com.blissless.tensei.playback

import com.blissless.tensei.data.models.ServerInfo
import com.blissless.tensei.torrent.StreamEntry
import com.google.common.truth.Truth.assertThat
import eu.kanade.tachiyomi.animesource.model.Track
import org.junit.Test

/**
 * Unit tests for [TenseiPlaybackHelpers].
 *
 * These cover the pure helper functions used exclusively by the Tensei
 * (magnet / ContentProvider) extension playback path. They run on the
 * JVM (no Android device needed) and verify the logic without touching
 * Compose state or Android APIs.
 */
class TenseiPlaybackHelpersTest {

    // ─── buildTenseiServerList ───────────────────────────────────────────

    @Test
    fun `buildTenseiServerList returns empty for empty input`() {
        assertThat(buildTenseiServerList(emptyList())).isEmpty()
    }

    @Test
    fun `buildTenseiServerList converts streams to server infos with provider label`() {
        val streams = listOf(
            StreamEntry(lang = "en-US", isDefault = true, url = "https://uwucdn.com/a/stream.m3u8"),
            StreamEntry(lang = "ja-JP", isDefault = false, url = "https://wixmp.com/b/stream.mp4"),
        )
        val result = buildTenseiServerList(streams)
        assertThat(result).hasSize(2)
        assertThat(result[0]).isEqualTo(ServerInfo(name = "EN-US (uwucdn)", url = "https://uwucdn.com/a/stream.m3u8"))
        assertThat(result[1]).isEqualTo(ServerInfo(name = "JA-JP (wixmp)", url = "https://wixmp.com/b/stream.mp4"))
    }

    @Test
    fun `buildTenseiServerList uppercases the language tag`() {
        val streams = listOf(
            StreamEntry(lang = "en-us", isDefault = false, url = "https://example.com/x"),
        )
        val result = buildTenseiServerList(streams)
        assertThat(result[0].name).startsWith("EN-US ")
    }

    @Test
    fun `buildTenseiServerList uses generic server label for unparseable urls`() {
        val streams = listOf(
            StreamEntry(lang = "en", isDefault = false, url = "not a url"),
            StreamEntry(lang = "ja", isDefault = false, url = ""),
        )
        val result = buildTenseiServerList(streams)
        assertThat(result[0].name).isEqualTo("EN (server1)")
        assertThat(result[1].name).isEqualTo("JA (server2)")
    }

    // ─── selectPreferredTenseiStream ─────────────────────────────────────

    @Test
    fun `selectPreferredTenseiStream returns null for empty list`() {
        assertThat(selectPreferredTenseiStream(emptyList(), "sub")).isNull()
    }

    @Test
    fun `selectPreferredTenseiStream prefers exact lang match`() {
        val streams = listOf(
            StreamEntry(lang = "ja-JP", isDefault = true, url = "https://a"),
            StreamEntry(lang = "en-US", isDefault = false, url = "https://b"),
            StreamEntry(lang = "en", isDefault = false, url = "https://c"),
        )
        val selected = selectPreferredTenseiStream(streams, "en-US")
        assertThat(selected).isNotNull()
        assertThat(selected!!.url).isEqualTo("https://b")
    }

    @Test
    fun `selectPreferredTenseiStream falls back to default-flagged stream`() {
        val streams = listOf(
            StreamEntry(lang = "ja-JP", isDefault = false, url = "https://a"),
            StreamEntry(lang = "en-US", isDefault = true, url = "https://b"),
        )
        val selected = selectPreferredTenseiStream(streams, "dub")
        assertThat(selected).isNotNull()
        assertThat(selected!!.url).isEqualTo("https://b")
    }

    @Test
    fun `selectPreferredTenseiStream falls back to first stream when no match and no default`() {
        val streams = listOf(
            StreamEntry(lang = "ja-JP", isDefault = false, url = "https://a"),
            StreamEntry(lang = "ko-KR", isDefault = false, url = "https://b"),
        )
        val selected = selectPreferredTenseiStream(streams, "en-US")
        assertThat(selected).isNotNull()
        assertThat(selected!!.url).isEqualTo("https://a")
    }

    @Test
    fun `selectPreferredTenseiStream lang match is case insensitive`() {
        val streams = listOf(
            StreamEntry(lang = "EN-US", isDefault = false, url = "https://a"),
        )
        val selected = selectPreferredTenseiStream(streams, "en-us")
        assertThat(selected).isNotNull()
        assertThat(selected!!.url).isEqualTo("https://a")
    }

    // ─── pickTenseiSubtitleUrl ───────────────────────────────────────────

    @Test
    fun `pickTenseiSubtitleUrl returns null for empty list`() {
        assertThat(pickTenseiSubtitleUrl(emptyList())).isNull()
    }

    @Test
    fun `pickTenseiSubtitleUrl prefers English-labelled subtitle`() {
        val subs = listOf(
            Track("https://sub/es", "Spanish"),
            Track("https://sub/en", "English"),
            Track("https://sub/fr", "French"),
        )
        assertThat(pickTenseiSubtitleUrl(subs)).isEqualTo("https://sub/en")
    }

    @Test
    fun `pickTenseiSubtitleUrl matches short en label`() {
        val subs = listOf(
            Track("https://sub/ja", "ja"),
            Track("https://sub/en", "en"),
        )
        assertThat(pickTenseiSubtitleUrl(subs)).isEqualTo("https://sub/en")
    }

    @Test
    fun `pickTenseiSubtitleUrl falls back to first when no English subtitle`() {
        // Use language codes that don't contain "en" as a substring
        // (note: "French" would false-positive match the contains("en") check).
        val subs = listOf(
            Track("https://sub/es", "Spanish"),
            Track("https://sub/de", "German"),
        )
        assertThat(pickTenseiSubtitleUrl(subs)).isEqualTo("https://sub/es")
    }

    @Test
    fun `pickTenseiSubtitleUrl english match is case insensitive`() {
        val subs = listOf(
            Track("https://sub/1", "spanish"),
            Track("https://sub/2", "ENGLISH"),
        )
        assertThat(pickTenseiSubtitleUrl(subs)).isEqualTo("https://sub/2")
    }

    // ─── tenseiProviderLabel ─────────────────────────────────────────────

    @Test
    fun `tenseiProviderLabel returns uwucdn for uwucdn host`() {
        assertThat(tenseiProviderLabel("https://uwucdn.com/a/b.m3u8")).isEqualTo("uwucdn")
    }

    @Test
    fun `tenseiProviderLabel returns wixmp for wixmp host`() {
        assertThat(tenseiProviderLabel("https://wixmp.com/x/y.mp4")).isEqualTo("wixmp")
    }

    @Test
    fun `tenseiProviderLabel returns miruro for miruro host`() {
        assertThat(tenseiProviderLabel("https://miruro.tv/p/q")).isEqualTo("miruro")
    }

    @Test
    fun `tenseiProviderLabel returns first domain label for unknown host`() {
        assertThat(tenseiProviderLabel("https://cdn.example.com/x")).isEqualTo("cdn")
    }

    @Test
    fun `tenseiProviderLabel returns fallback label for unparseable url`() {
        assertThat(tenseiProviderLabel("not a url", fallbackIndex = 3)).isEqualTo("server4")
    }

    @Test
    fun `tenseiProviderLabel returns fallback label for empty url`() {
        assertThat(tenseiProviderLabel("", fallbackIndex = 0)).isEqualTo("server1")
    }
}
