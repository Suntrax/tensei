package com.blissless.tensei.ui.screens.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for [SubtitleStyleHelpers.formatTime].
 *
 * Tests the time formatting logic that displays playback position
 * in the player UI (seekbar, remaining time, etc.).
 */
class SubtitleStyleHelpersTest {

    // ─── formatTime ───────────────────────────────────────────────────────

    @Test
    fun `formatTime returns 0_00 for zero milliseconds`() {
        assertThat(formatTime(0)).isEqualTo("0:00")
    }

    @Test
    fun `formatTime formats seconds only`() {
        assertThat(formatTime(5000)).isEqualTo("0:05")
        assertThat(formatTime(59000)).isEqualTo("0:59")
    }

    @Test
    fun `formatTime formats minutes and seconds`() {
        assertThat(formatTime(65000)).isEqualTo("1:05")
        assertThat(formatTime(125000)).isEqualTo("2:05")
        assertThat(formatTime(599000)).isEqualTo("9:59")
    }

    @Test
    fun `formatTime formats hours minutes and seconds`() {
        // 1 hour, 2 minutes, 5 seconds = 3725000 ms
        assertThat(formatTime(3725000)).isEqualTo("1:02:05")
    }

    @Test
    fun `formatTime formats multiple hours`() {
        // 2 hours, 0 minutes, 0 seconds = 7200000 ms
        assertThat(formatTime(7200000)).isEqualTo("2:00:00")
    }

    @Test
    fun `formatTime pads seconds with leading zero`() {
        assertThat(formatTime(1000)).isEqualTo("0:01")
        assertThat(formatTime(9000)).isEqualTo("0:09")
    }

    @Test
    fun `formatTime pads minutes with leading zero when hours present`() {
        // 1 hour, 0 minutes, 5 seconds
        assertThat(formatTime(3605000)).isEqualTo("1:00:05")
        // 1 hour, 9 minutes, 5 seconds
        assertThat(formatTime(4145000)).isEqualTo("1:09:05")
    }

    @Test
    fun `formatTime handles negative input gracefully`() {
        // Negative time shouldn't crash; behavior is implementation-defined
        // but we verify it doesn't throw
        val result = formatTime(-1000)
        // The formula produces negative numbers; just verify no exception
        assertThat(result).isNotNull()
    }

    @Test
    fun `formatTime handles very large values`() {
        // 24 hours = 86400000 ms
        val result = formatTime(86400000)
        assertThat(result).contains("24")
    }

    @Test
    fun `formatTime truncates fractional milliseconds`() {
        // 1500 ms should be 0:01 (truncates the 500ms)
        assertThat(formatTime(1500)).isEqualTo("0:01")
    }

    @Test
    fun `formatTime handles exactly one minute`() {
        assertThat(formatTime(60000)).isEqualTo("1:00")
    }

    @Test
    fun `formatTime handles exactly one hour`() {
        assertThat(formatTime(3600000)).isEqualTo("1:00:00")
    }
}
