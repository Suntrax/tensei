package com.blissless.tensei.extensions

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for [Repo] URL resolution functions.
 *
 * These functions resolve relative APK and icon paths against a repo URL,
 * handling both absolute URLs (returned as-is) and relative paths
 * (resolved against the base repo URL).
 */
class RepoTest {

    // ─── resolveApkUrl ────────────────────────────────────────────────────

    @Test
    fun `resolveApkUrl returns absolute URL unchanged`() {
        val result = resolveApkUrl(
            "https://example.com/repo.json",
            "https://cdn.example.com/extension.apk"
        )
        assertThat(result).isEqualTo("https://cdn.example.com/extension.apk")
    }

    @Test
    fun `resolveApkUrl returns absolute http URL unchanged`() {
        val result = resolveApkUrl(
            "https://example.com/repo.json",
            "http://cdn.example.com/extension.apk"
        )
        assertThat(result).isEqualTo("http://cdn.example.com/extension.apk")
    }

    @Test
    fun `resolveApkUrl resolves simple filename against base`() {
        val result = resolveApkUrl(
            "https://example.com/repo.json",
            "extension.apk"
        )
        assertThat(result).isEqualTo("https://example.com/apk/extension.apk")
    }

    @Test
    fun `resolveApkUrl resolves path with slashes against base`() {
        val result = resolveApkUrl(
            "https://example.com/repo.json",
            "extensions/v1/extension.apk"
        )
        assertThat(result).isEqualTo("https://example.com/extensions/v1/extension.apk")
    }

    @Test
    fun `resolveApkUrl resolves against GitHub raw URL`() {
        val result = resolveApkUrl(
            "https://raw.githubusercontent.com/user/repo/main/repo.json",
            "extension.apk"
        )
        assertThat(result).isEqualTo("https://raw.githubusercontent.com/user/repo/main/apk/extension.apk")
    }

    // ─── resolveIconUrl ───────────────────────────────────────────────────

    @Test
    fun `resolveIconUrl returns empty for blank input`() {
        assertThat(resolveIconUrl("https://example.com/repo.json", "")).isEmpty()
        assertThat(resolveIconUrl("https://example.com/repo.json", "   ")).isEmpty()
    }

    @Test
    fun `resolveIconUrl returns absolute URL unchanged`() {
        val result = resolveIconUrl(
            "https://example.com/repo.json",
            "https://cdn.example.com/icon.png"
        )
        assertThat(result).isEqualTo("https://cdn.example.com/icon.png")
    }

    @Test
    fun `resolveIconUrl resolves simple filename against base`() {
        val result = resolveIconUrl(
            "https://example.com/repo.json",
            "icon.png"
        )
        assertThat(result).isEqualTo("https://example.com/icon/icon.png")
    }

    @Test
    fun `resolveIconUrl resolves path with slashes against base`() {
        val result = resolveIconUrl(
            "https://example.com/repo.json",
            "icons/extension.png"
        )
        assertThat(result).isEqualTo("https://example.com/icons/extension.png")
    }

    @Test
    fun `resolveIconUrl resolves against GitHub raw URL`() {
        val result = resolveIconUrl(
            "https://raw.githubusercontent.com/user/repo/main/repo.json",
            "icon.png"
        )
        assertThat(result).isEqualTo("https://raw.githubusercontent.com/user/repo/main/icon/icon.png")
    }
}
