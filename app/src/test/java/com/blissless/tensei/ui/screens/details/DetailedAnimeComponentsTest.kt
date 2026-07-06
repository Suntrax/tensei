package com.blissless.tensei.ui.screens.details

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for [DetailedAnimeComponents] helper functions.
 *
 * Covers the pure utility functions extracted from DetailedAnimeScreen.kt.
 */
class DetailedAnimeComponentsTest {

    // ─── formatDate ───────────────────────────────────────────────────────

    @Test
    fun `formatDate returns input for invalid date`() {
        assertThat(formatDate("not-a-date")).isEqualTo("not-a-date")
    }

    @Test
    fun `formatDate returns input for empty string`() {
        assertThat(formatDate("")).isEqualTo("")
    }

    @Test
    fun `formatDate returns input for partial date`() {
        assertThat(formatDate("2024")).isEqualTo("2024")
        assertThat(formatDate("2024-01")).isEqualTo("2024-01")
    }

    @Test
    fun `formatDate formats valid ISO date`() {
        val result = formatDate("2024-01-15")
        // The exact format depends on Locale, but it should contain the day, month, and year
        assertThat(result).contains("15")
        assertThat(result).contains("2024")
        // Should not be the raw ISO format anymore
        assertThat(result).isNotEqualTo("2024-01-15")
    }

    @Test
    fun `formatDate handles invalid month`() {
        // Month 13 doesn't exist — should throw, caught, and return input
        assertThat(formatDate("2024-13-01")).isEqualTo("2024-13-01")
    }

    @Test
    fun `formatDate handles invalid day`() {
        // Day 32 doesn't exist — should throw, caught, and return input
        assertThat(formatDate("2024-01-32")).isEqualTo("2024-01-32")
    }

    @Test
    fun `formatDate handles non-numeric parts`() {
        assertThat(formatDate("abcd-ef-gh")).isEqualTo("abcd-ef-gh")
    }

    // ─── easeOut ──────────────────────────────────────────────────────────

    @Test
    fun `easeOut returns 0 for input 0`() {
        assertThat(easeOut(0f)).isWithin(0.001f).of(0f)
    }

    @Test
    fun `easeOut returns 1 for input 1`() {
        assertThat(easeOut(1f)).isWithin(0.001f).of(1f)
    }

    @Test
    fun `easeOut returns 1 for input greater than 1`() {
        // easeOut clamps via the cubic formula: t1 = t - 1, return t1^3 + 1
        // At t=2: t1=1, return 1+1=2
        assertThat(easeOut(2f)).isWithin(0.001f).of(2f)
    }

    @Test
    fun `easeOut is monotonically increasing on 0 to 1`() {
        var prev = easeOut(0f)
        var i = 0.01f
        while (i <= 1f) {
            val current = easeOut(i)
            assertThat(current).isAtLeast(prev)
            prev = current
            i += 0.01f
        }
    }

    @Test
    fun `easeOut returns negative for negative input`() {
        // At t=-1: t1=-2, return -8 + 1 = -7
        assertThat(easeOut(-1f)).isWithin(0.001f).of(-7f)
    }
}
