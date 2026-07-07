package com.blissless.tensei.ui.screens.player

import com.blissless.tensei.data.models.SubtitleProfileData
import com.blissless.tensei.data.models.SubtitleSettings
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for [SubtitleFullSettings] and [SubtitleProfileData].
 *
 * Tests the data class conversion and default value behavior.
 */
class SubtitleFullSettingsTest {

    // ─── Default values ───────────────────────────────────────────────────

    @Test
    fun `default SubtitleFullSettings has correct fontSize`() {
        assertThat(SubtitleFullSettings().fontSize).isEqualTo(22f)
    }

    @Test
    fun `default SubtitleFullSettings has white font color`() {
        assertThat(SubtitleFullSettings().fontColor).isEqualTo(0xFFFFFFFFL)
    }

    @Test
    fun `default SubtitleFullSettings has outline enabled`() {
        assertThat(SubtitleFullSettings().enableOutline).isTrue()
    }

    @Test
    fun `default SubtitleFullSettings has shadow disabled`() {
        assertThat(SubtitleFullSettings().enableShadow).isFalse()
    }

    @Test
    fun `default SubtitleFullSettings has zero rotation`() {
        assertThat(SubtitleFullSettings().rotation).isEqualTo(0f)
    }

    @Test
    fun `default SubtitleFullSettings has zero delay`() {
        assertThat(SubtitleFullSettings().delayMs).isEqualTo(0)
    }

    @Test
    fun `default SubtitleFullSettings has name Default`() {
        assertThat(SubtitleFullSettings().profileName).isEqualTo("Default")
    }

    // ─── toLegacy conversion ──────────────────────────────────────────────

    @Test
    fun `toLegacy preserves fontSize`() {
        val settings = SubtitleFullSettings(fontSize = 30f)
        assertThat(settings.toLegacy().fontSize).isEqualTo(30f)
    }

    @Test
    fun `toLegacy preserves fontColor`() {
        val settings = SubtitleFullSettings(fontColor = 0xFFFF0000L)
        assertThat(settings.toLegacy().fontColor).isEqualTo(0xFFFF0000L)
    }

    @Test
    fun `toLegacy preserves enableOutline`() {
        val settings = SubtitleFullSettings(enableOutline = false)
        assertThat(settings.toLegacy().enableOutline).isFalse()
    }

    @Test
    fun `toLegacy preserves enableShadow`() {
        val settings = SubtitleFullSettings(enableShadow = true)
        assertThat(settings.toLegacy().enableShadow).isTrue()
    }

    @Test
    fun `toLegacy preserves outlineWidth`() {
        val settings = SubtitleFullSettings(outlineWidth = 4f)
        assertThat(settings.toLegacy().outlineWidth).isEqualTo(4f)
    }

    @Test
    fun `toLegacy preserves shadowBlur`() {
        val settings = SubtitleFullSettings(shadowBlur = 6f)
        assertThat(settings.toLegacy().shadowBlur).isEqualTo(6f)
    }

    @Test
    fun `toLegacy preserves rotation`() {
        val settings = SubtitleFullSettings(rotation = 45f)
        assertThat(settings.toLegacy().rotation).isEqualTo(45f)
    }

    @Test
    fun `toLegacy preserves delayMs`() {
        val settings = SubtitleFullSettings(delayMs = 500)
        assertThat(settings.toLegacy().delayMs).isEqualTo(500)
    }

    @Test
    fun `toLegacy preserves verticalPosition`() {
        val settings = SubtitleFullSettings(verticalPosition = 0.8f)
        assertThat(settings.toLegacy().verticalPosition).isEqualTo(0.8f)
    }

    @Test
    fun `toLegacy preserves horizontalPosition`() {
        val settings = SubtitleFullSettings(horizontalPosition = 0.3f)
        assertThat(settings.toLegacy().horizontalPosition).isEqualTo(0.3f)
    }

    @Test
    fun `toLegacy preserves maxWidthRatio`() {
        val settings = SubtitleFullSettings(maxWidthRatio = 0.9f)
        assertThat(settings.toLegacy().maxWidthRatio).isEqualTo(0.9f)
    }

    @Test
    fun `toLegacy preserves backgroundColor`() {
        val settings = SubtitleFullSettings(backgroundColor = 0x80000000L)
        assertThat(settings.toLegacy().backgroundColor).isEqualTo(0x80000000L)
    }

    @Test
    fun `toLegacy preserves profileName`() {
        val settings = SubtitleFullSettings(profileName = "My Profile")
        assertThat(settings.toLegacy().profileName).isEqualTo("My Profile")
    }

    @Test
    fun `toLegacy with all custom values preserves everything`() {
        val settings = SubtitleFullSettings(
            fontSize = 18f,
            fontColor = 0xFF00FF00L,
            enableOutline = false,
            outlineWidth = 1f,
            outlineColor = 0xFFFFFFFFL,
            enableShadow = true,
            shadowBlur = 5f,
            shadowOffsetX = 3f,
            shadowOffsetY = 3f,
            shadowColor = 0xFF0000FFL,
            backgroundColor = 0x40000000L,
            verticalPosition = 0.85f,
            horizontalPosition = 0.5f,
            maxWidthRatio = 0.9f,
            delayMs = 200,
            rotation = 15f,
            profileName = "Custom"
        )
        val legacy = settings.toLegacy()
        assertThat(legacy.fontSize).isEqualTo(18f)
        assertThat(legacy.fontColor).isEqualTo(0xFF00FF00L)
        assertThat(legacy.enableOutline).isFalse()
        assertThat(legacy.outlineWidth).isEqualTo(1f)
        assertThat(legacy.outlineColor).isEqualTo(0xFFFFFFFFL)
        assertThat(legacy.enableShadow).isTrue()
        assertThat(legacy.shadowBlur).isEqualTo(5f)
        assertThat(legacy.shadowOffsetX).isEqualTo(3f)
        assertThat(legacy.shadowOffsetY).isEqualTo(3f)
        assertThat(legacy.shadowColor).isEqualTo(0xFF0000FFL)
        assertThat(legacy.backgroundColor).isEqualTo(0x40000000L)
        assertThat(legacy.verticalPosition).isEqualTo(0.85f)
        assertThat(legacy.horizontalPosition).isEqualTo(0.5f)
        assertThat(legacy.maxWidthRatio).isEqualTo(0.9f)
        assertThat(legacy.delayMs).isEqualTo(200)
        assertThat(legacy.rotation).isEqualTo(15f)
        assertThat(legacy.profileName).isEqualTo("Custom")
    }

    // ─── SubtitleProfileData ──────────────────────────────────────────────

    @Test
    fun `default SubtitleProfileData has 5 profiles`() {
        assertThat(SubtitleProfileData().profiles).hasSize(5)
    }

    @Test
    fun `default SubtitleProfileData has active index 0`() {
        assertThat(SubtitleProfileData().activeProfileIndex).isEqualTo(0)
    }

    @Test
    fun `default profiles are named Profile 1 through 5`() {
        val data = SubtitleProfileData()
        assertThat(data.profiles[0].profileName).isEqualTo("Profile 1")
        assertThat(data.profiles[1].profileName).isEqualTo("Profile 2")
        assertThat(data.profiles[2].profileName).isEqualTo("Profile 3")
        assertThat(data.profiles[3].profileName).isEqualTo("Profile 4")
        assertThat(data.profiles[4].profileName).isEqualTo("Profile 5")
    }

    // ─── SubtitleSettings defaults ────────────────────────────────────────

    @Test
    fun `SubtitleSettings DEFAULT has correct fontSize`() {
        assertThat(SubtitleSettings.DEFAULT.fontSize).isEqualTo(22f)
    }

    @Test
    fun `SubtitleSettings DEFAULT has white font color`() {
        assertThat(SubtitleSettings.DEFAULT.fontColor).isEqualTo(0xFFFFFFFFL)
    }

    @Test
    fun `SubtitleSettings DEFAULT has outline enabled`() {
        assertThat(SubtitleSettings.DEFAULT.enableOutline).isTrue()
    }

    @Test
    fun `SubtitleSettings DEFAULT has shadow disabled`() {
        assertThat(SubtitleSettings.DEFAULT.enableShadow).isFalse()
    }
}
