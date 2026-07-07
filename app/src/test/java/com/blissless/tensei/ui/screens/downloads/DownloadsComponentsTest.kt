package com.blissless.tensei.ui.screens.downloads

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for [DownloadsComponents] helper functions.
 */
class DownloadsComponentsTest {

    // ─── formatFileSize ───────────────────────────────────────────────────

    @Test
    fun `formatFileSize formats bytes`() {
        assertThat(formatFileSize(0)).isEqualTo("0 B")
        assertThat(formatFileSize(500)).isEqualTo("500 B")
    }

    @Test
    fun `formatFileSize formats kilobytes`() {
        assertThat(formatFileSize(1024)).isEqualTo("1 KB")
    }

    @Test
    fun `formatFileSize formats megabytes`() {
        assertThat(formatFileSize(1024 * 1024)).isEqualTo("1 MB")
    }

    // ─── formatTimeFromMs ─────────────────────────────────────────────────

    @Test
    fun `formatTimeFromMs returns zero for zero input`() {
        assertThat(formatTimeFromMs(0L)).isEqualTo("0:00")
    }

    @Test
    fun `formatTimeFromMs formats seconds`() {
        assertThat(formatTimeFromMs(5000L)).isEqualTo("0:05")
    }

    @Test
    fun `formatTimeFromMs formats minutes`() {
        assertThat(formatTimeFromMs(65000L)).isEqualTo("1:05")
    }

    @Test
    fun `formatTimeFromMs formats hours`() {
        assertThat(formatTimeFromMs(3725000L)).isEqualTo("1:02:05")
    }
}
