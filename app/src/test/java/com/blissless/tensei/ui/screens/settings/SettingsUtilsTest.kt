package com.blissless.tensei.ui.screens.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for [SettingsUtils].
 *
 * Covers the pure utility functions extracted from SettingsScreen.kt.
 * These run on the JVM without any Android dependencies.
 */
class SettingsUtilsTest {

    // ─── compareVersions ──────────────────────────────────────────────────

    @Test
    fun `compareVersions returns 0 for equal versions`() {
        assertThat(compareVersions("1.0.0", "1.0.0")).isEqualTo(0)
        assertThat(compareVersions("2.5.3", "2.5.3")).isEqualTo(0)
    }

    @Test
    fun `compareVersions returns positive when first is greater`() {
        assertThat(compareVersions("2.0.0", "1.0.0")).isGreaterThan(0)
        assertThat(compareVersions("1.2.0", "1.1.0")).isGreaterThan(0)
        assertThat(compareVersions("1.0.1", "1.0.0")).isGreaterThan(0)
    }

    @Test
    fun `compareVersions returns negative when first is less`() {
        assertThat(compareVersions("1.0.0", "2.0.0")).isLessThan(0)
        assertThat(compareVersions("1.1.0", "1.2.0")).isLessThan(0)
        assertThat(compareVersions("1.0.0", "1.0.1")).isLessThan(0)
    }

    @Test
    fun `compareVersions handles different segment counts`() {
        // "1.0" vs "1.0.0" — missing segment treated as 0
        assertThat(compareVersions("1.0", "1.0.0")).isEqualTo(0)
        assertThat(compareVersions("1.0.1", "1.0")).isGreaterThan(0)
    }

    @Test
    fun `compareVersions handles non-numeric segments as zero`() {
        assertThat(compareVersions("1.0.abc", "1.0.0")).isEqualTo(0)
        assertThat(compareVersions("1.x.0", "1.0.0")).isEqualTo(0)
    }

    @Test
    fun `compareVersions handles empty strings`() {
        assertThat(compareVersions("", "")).isEqualTo(0)
        assertThat(compareVersions("1.0", "")).isGreaterThan(0)
    }

    @Test
    fun `compareVersions handles single segment`() {
        assertThat(compareVersions("2", "1")).isGreaterThan(0)
        assertThat(compareVersions("1", "2")).isLessThan(0)
    }

    // ─── formatFileSize ───────────────────────────────────────────────────

    @Test
    fun `formatFileSize formats bytes`() {
        assertThat(formatFileSize(0)).isEqualTo("0 B")
        assertThat(formatFileSize(1)).isEqualTo("1 B")
        assertThat(formatFileSize(500)).isEqualTo("500 B")
        assertThat(formatFileSize(1023)).isEqualTo("1023 B")
    }

    @Test
    fun `formatFileSize formats kilobytes`() {
        assertThat(formatFileSize(1024)).isEqualTo("1 KB")
        assertThat(formatFileSize(2048)).isEqualTo("2 KB")
        assertThat(formatFileSize(1024 * 1024 - 1)).isEqualTo("1023 KB")
    }

    @Test
    fun `formatFileSize formats megabytes`() {
        assertThat(formatFileSize(1024 * 1024)).isEqualTo("1 MB")
        assertThat(formatFileSize(1024 * 1024 * 5)).isEqualTo("5 MB")
        assertThat(formatFileSize(1024 * 1024 * 1024 - 1)).isEqualTo("1023 MB")
    }

    @Test
    fun `formatFileSize formats gigabytes`() {
        val oneGb = 1024L * 1024 * 1024
        val result = formatFileSize(oneGb)
        // Format is "%.2f GB" — locale-dependent decimal separator
        assertThat(result).endsWith(" GB")
        assertThat(result).contains("1.0")
    }

    @Test
    fun `formatFileSize handles large values`() {
        val tenGb = 10L * 1024 * 1024 * 1024
        val result = formatFileSize(tenGb)
        assertThat(result).endsWith(" GB")
        assertThat(result).contains("10.0")
    }
}
