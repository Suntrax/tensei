package com.blissless.tensei.data.models

import kotlinx.serialization.Serializable

@Serializable
data class SubtitleSettings(
    val fontSize: Float = 22f,
    val fontColor: Long = 0xFFFFFFFFL,
    val enableShadow: Boolean = false,
    val enableOutline: Boolean = true,
    val outlineWidth: Float = 2f,
    val outlineColor: Long = 0xFF000000L,
    val delayMs: Int = 0,
    val verticalPosition: Float = 0.9f,
    val horizontalPosition: Float = 0.5f,
    val maxWidthRatio: Float = 0.95f,
    val backgroundColor: Long = 0x00000000L,
    val profileName: String = "Default"
) {
    companion object {
        val DEFAULT = SubtitleSettings()
    }
}

@Serializable
data class SubtitleProfileData(
    val profiles: List<SubtitleSettings> = List(5) { defaultProfile(it) },
    val activeProfileIndex: Int = 0
) {
    companion object {
        private fun defaultProfile(index: Int) = SubtitleSettings(
            profileName = "Profile ${index + 1}"
        )
    }
}
