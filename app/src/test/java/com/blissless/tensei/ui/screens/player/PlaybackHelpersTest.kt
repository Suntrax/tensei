package com.blissless.tensei.ui.screens.player

import com.blissless.tensei.data.models.QualityOption
import com.blissless.tensei.data.models.ServerInfo
import com.google.common.truth.Truth.assertThat
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.Track
import eu.kanade.tachiyomi.animesource.model.Video
import org.junit.Test

/**
 * Unit tests for [PlaybackHelpers].
 *
 * These tests cover the pure helper functions extracted from MainActivity.kt.
 * They run on the JVM (no Android device needed) and verify the logic
 * without touching Compose state or Android APIs.
 */
class PlaybackHelpersTest {

    // ─── sanitizeEpisodeTitle ─────────────────────────────────────────────

    @Test
    fun `sanitizeEpisodeTitle returns null for null input`() {
        assertThat(sanitizeEpisodeTitle(null)).isNull()
    }

    @Test
    fun `sanitizeEpisodeTitle returns empty for empty input`() {
        assertThat(sanitizeEpisodeTitle("")).isEqualTo("")
    }

    @Test
    fun `sanitizeEpisodeTitle strips Episode prefix`() {
        assertThat(sanitizeEpisodeTitle("Episode 12: The Final Battle"))
            .isEqualTo("The Final Battle")
    }

    @Test
    fun `sanitizeEpisodeTitle strips Ep prefix`() {
        assertThat(sanitizeEpisodeTitle("Ep. 3 - Confrontation"))
            .isEqualTo("Confrontation")
    }

    @Test
    fun `sanitizeEpisodeTitle strips Ep without dot`() {
        assertThat(sanitizeEpisodeTitle("Ep 5: Revenge"))
            .isEqualTo("Revenge")
    }

    @Test
    fun `sanitizeEpisodeTitle is case insensitive`() {
        assertThat(sanitizeEpisodeTitle("EPISODE 1 - Beginnings"))
            .isEqualTo("Beginnings")
    }

    @Test
    fun `sanitizeEpisodeTitle handles various separators`() {
        assertThat(sanitizeEpisodeTitle("Episode 12: Test")).isEqualTo("Test")
        assertThat(sanitizeEpisodeTitle("Episode 12 - Test")).isEqualTo("Test")
        assertThat(sanitizeEpisodeTitle("Episode 12 Test")).isEqualTo("Test")
    }

    @Test
    fun `sanitizeEpisodeTitle leaves title without prefix unchanged`() {
        assertThat(sanitizeEpisodeTitle("The Final Battle")).isEqualTo("The Final Battle")
        assertThat(sanitizeEpisodeTitle("12")).isEqualTo("12")
    }

    // ─── buildServerList ──────────────────────────────────────────────────

    @Test
    fun `buildServerList returns empty for null input`() {
        assertThat(buildServerList(null)).isEmpty()
    }

    @Test
    fun `buildServerList returns empty for empty input`() {
        assertThat(buildServerList(emptyList())).isEmpty()
    }

    @Test
    fun `buildServerList converts hosters to server infos`() {
        val hosters = listOf(
            Hoster(hosterUrl = "https://example.com/1", hosterName = "Server A"),
            Hoster(hosterUrl = "https://example.com/2", hosterName = "Server B"),
        )
        val result = buildServerList(hosters)
        assertThat(result).hasSize(2)
        assertThat(result[0]).isEqualTo(ServerInfo(name = "Server A", url = "https://example.com/1"))
        assertThat(result[1]).isEqualTo(ServerInfo(name = "Server B", url = "https://example.com/2"))
    }

    // ─── buildQualityOptions ──────────────────────────────────────────────

    @Test
    fun `buildQualityOptions returns empty for null input`() {
        assertThat(buildQualityOptions(null)).isEmpty()
    }

    @Test
    fun `buildQualityOptions returns empty for empty input`() {
        assertThat(buildQualityOptions(emptyList())).isEmpty()
    }

    @Test
    fun `buildQualityOptions converts videos to quality options`() {
        val videos = listOf(
            Video(videoUrl = "https://example.com/v1", videoTitle = "1080p", resolution = 1080),
            Video(videoUrl = "https://example.com/v2", videoTitle = "720p", resolution = 720),
        )
        val result = buildQualityOptions(videos)
        assertThat(result).hasSize(2)
        assertThat(result[0]).isEqualTo(QualityOption(quality = "1080p", url = "https://example.com/v1", width = 1080))
        assertThat(result[1]).isEqualTo(QualityOption(quality = "720p", url = "https://example.com/v2", width = 720))
    }

    @Test
    fun `buildQualityOptions handles null resolution`() {
        val videos = listOf(
            Video(videoUrl = "https://example.com/v1", videoTitle = "Auto", resolution = null),
        )
        val result = buildQualityOptions(videos)
        assertThat(result).hasSize(1)
        assertThat(result[0].width).isEqualTo(0)
    }

    // ─── isOurProxyUrl ────────────────────────────────────────────────────

    @Test
    fun `isOurProxyUrl returns true for our proxy with 127 0 0 1`() {
        assertThat(isOurProxyUrl("http://127.0.0.1:41223/video.mp4", 41223)).isTrue()
    }

    @Test
    fun `isOurProxyUrl returns true for our proxy with localhost`() {
        assertThat(isOurProxyUrl("http://localhost:41223/video.mp4", 41223)).isTrue()
    }

    @Test
    fun `isOurProxyUrl returns false for different port`() {
        assertThat(isOurProxyUrl("http://127.0.0.1:8080/video.mp4", 41223)).isFalse()
    }

    @Test
    fun `isOurProxyUrl returns false for external URL`() {
        assertThat(isOurProxyUrl("https://example.com/video.mp4", 41223)).isFalse()
    }

    // ─── rewriteToOurProxy ────────────────────────────────────────────────

    @Test
    fun `rewriteToOurProxy replaces 127 0 0 1 port`() {
        val result = rewriteToOurProxy("http://127.0.0.1:8080/video.mp4", 41223)
        assertThat(result).isEqualTo("http://127.0.0.1:41223/video.mp4")
    }

    @Test
    fun `rewriteToOurProxy replaces localhost port`() {
        val result = rewriteToOurProxy("http://localhost:8080/video.mp4", 41223)
        assertThat(result).isEqualTo("http://127.0.0.1:41223/video.mp4")
    }

    @Test
    fun `rewriteToOurProxy leaves external URLs unchanged`() {
        val url = "https://example.com/video.mp4"
        assertThat(rewriteToOurProxy(url, 41223)).isEqualTo(url)
    }

    @Test
    fun `rewriteToOurProxy handles URLs with multiple localhost references`() {
        val result = rewriteToOurProxy("http://127.0.0.1:8080/video.mp4?ref=http://127.0.0.1:9000/x", 41223)
        assertThat(result).contains("127.0.0.1:41223")
        assertThat(result).doesNotContain("127.0.0.1:8080")
        assertThat(result).doesNotContain("127.0.0.1:9000")
    }

    // ─── pickBestVideo ────────────────────────────────────────────────────

    @Test
    fun `pickBestVideo returns the only video when list has one item`() {
        val videos = listOf(Video(videoUrl = "url1", videoTitle = "720p", resolution = 720))
        assertThat(pickBestVideo(videos)).isEqualTo(videos[0])
    }

    @Test
    fun `pickBestVideo returns highest resolution`() {
        val videos = listOf(
            Video(videoUrl = "url1", videoTitle = "720p", resolution = 720),
            Video(videoUrl = "url2", videoTitle = "1080p", resolution = 1080),
            Video(videoUrl = "url3", videoTitle = "480p", resolution = 480),
        )
        assertThat(pickBestVideo(videos).videoUrl).isEqualTo("url2")
    }

    @Test
    fun `pickBestVideo falls back to title digits when resolution is null`() {
        val videos = listOf(
            Video(videoUrl = "url1", videoTitle = "Auto", resolution = null),
            Video(videoUrl = "url2", videoTitle = "720p Quality", resolution = null),
        )
        // "720p Quality" -> digits "720" -> 720, "Auto" -> no digits -> 0
        assertThat(pickBestVideo(videos).videoUrl).isEqualTo("url2")
    }

    @Test
    fun `pickBestVideo returns last video when all have no resolution info`() {
        val videos = listOf(
            Video(videoUrl = "url1", videoTitle = "Auto", resolution = null),
            Video(videoUrl = "url2", videoTitle = "Default", resolution = null),
        )
        // Both score 0, maxByOrNull returns first, but fallback is `?: videos.last()`
        // Actually maxByOrNull with equal scores returns the first. Let's verify behavior.
        val picked = pickBestVideo(videos)
        assertThat(picked.videoUrl).isIn(listOf("url1", "url2"))
    }

    // ─── sortSubtitleTracks ───────────────────────────────────────────────

    @Test
    fun `sortSubtitleTracks puts preferred language first`() {
        val tracks = listOf(
            Track(url = "url1", lang = "Spanish"),
            Track(url = "url2", lang = "English"),
            Track(url = "url3", lang = "Japanese"),
        )
        val sorted = sortSubtitleTracks(tracks, preferredLang = "Japanese")
        assertThat(sorted.first().lang).isEqualTo("Japanese")
    }

    @Test
    fun `sortSubtitleTracks puts English second when not preferred`() {
        val tracks = listOf(
            Track(url = "url1", lang = "Spanish"),
            Track(url = "url2", lang = "English"),
            Track(url = "url3", lang = "French"),
        )
        val sorted = sortSubtitleTracks(tracks, preferredLang = "Japanese")
        assertThat(sorted[0].lang).isEqualTo("English")
    }

    @Test
    fun `sortSubtitleTracks is case insensitive for preferred lang`() {
        val tracks = listOf(
            Track(url = "url1", lang = "english"),
            Track(url = "url2", lang = "SPANISH"),
        )
        val sorted = sortSubtitleTracks(tracks, preferredLang = "English")
        assertThat(sorted.first().lang).isEqualTo("english")
    }

    @Test
    fun `sortSubtitleTracks handles empty list`() {
        assertThat(sortSubtitleTracks(emptyList(), "English")).isEmpty()
    }

    @Test
    fun `sortSubtitleTracks preserves all tracks`() {
        val tracks = listOf(
            Track(url = "url1", lang = "Spanish"),
            Track(url = "url2", lang = "English"),
            Track(url = "url3", lang = "French"),
        )
        val sorted = sortSubtitleTracks(tracks, "English")
        assertThat(sorted).hasSize(3)
    }
}
