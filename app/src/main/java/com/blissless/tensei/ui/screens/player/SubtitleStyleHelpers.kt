package com.blissless.tensei.ui.screens.player

import android.content.Context
import android.view.View
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import com.blissless.tensei.data.models.SubtitleProfileData
import com.blissless.tensei.data.models.SubtitleSettings

/**
 * Subtitle styling and profile persistence helpers.
 *
 * Extracted from PlayerScreen.kt. These functions are shared between
 * PlayerScreen and OfflinePlayerScreen for applying subtitle visual
 * settings (font size, color, outline, shadow, position, rotation)
 * to a Media3 SubtitleView, and for loading/saving subtitle profiles
 * from SharedPreferences.
 */

/**
 * Loads subtitle profile data from SharedPreferences.
 *
 * Reads the serialized SubtitleProfileData JSON and the active profile
 * index. Returns a default SubtitleProfileData if nothing is saved or
 * if deserialization fails.
 *
 * @param context Any context (uses SharedPreferences)
 * @return The loaded profile data, or defaults if none saved
 */
fun loadSubtitleProfileData(context: Context): SubtitleProfileData {
    val prefs = context.getSharedPreferences("anilist_prefs", Context.MODE_PRIVATE)
    val saved = prefs.getString("subtitle_profiles", null)
    val activeIndex = prefs.getInt("subtitle_active_profile", 0)
    if (saved != null) {
        try {
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            val data = json.decodeFromString(SubtitleProfileData.serializer(), saved)
            return if (activeIndex in data.profiles.indices) data.copy(activeProfileIndex = activeIndex)
            else data
        } catch (_: Exception) { }
    }
    return SubtitleProfileData()
}

/**
 * Applies subtitle visual settings to a Media3 SubtitleView.
 *
 * Configures:
 *   - Edge type (none, outline, or drop shadow) and color
 *   - Font color and background color
 *   - Font size (in SP)
 *   - Rotation
 *   - Horizontal/vertical translation (relative to parent dimensions)
 *
 * If the parent view hasn't been laid out yet (dimensions are 0),
 * the translation is deferred via [android.view.View.post] to run
 * after the next layout pass.
 *
 * @param subtitleView The Media3 SubtitleView to style
 * @param settings     The subtitle settings to apply
 */
@OptIn(UnstableApi::class)
fun applySubtitleStyle(subtitleView: androidx.media3.ui.SubtitleView, settings: SubtitleSettings) {
    val edgeType = when {
        settings.enableShadow -> CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
        settings.enableOutline -> CaptionStyleCompat.EDGE_TYPE_OUTLINE
        else -> CaptionStyleCompat.EDGE_TYPE_NONE
    }
    val edgeColor = when (edgeType) {
        CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW -> (settings.shadowColor and 0xFFFFFFFFL).toInt()
        else -> (settings.outlineColor and 0xFFFFFFFFL).toInt()
    }
    val style = CaptionStyleCompat(
        (settings.fontColor and 0xFFFFFFFFL).toInt(),
        (settings.backgroundColor and 0xFFFFFFFFL).toInt(),
        android.graphics.Color.TRANSPARENT,
        edgeType,
        edgeColor,
        null
    )
    subtitleView.setStyle(style)
    subtitleView.setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, settings.fontSize)
    subtitleView.rotation = settings.rotation

    val parent = subtitleView.parent as? View
    val pw = parent?.width?.toFloat() ?: 0f
    val ph = parent?.height?.toFloat() ?: 0f
    if (pw > 0 && ph > 0) {
        subtitleView.translationX = (settings.horizontalPosition - 0.5f) * pw
        subtitleView.translationY = (settings.verticalPosition - 0.95f) * ph
    } else {
        subtitleView.post {
            val p = subtitleView.parent as? View ?: return@post
            val w = p.width.toFloat().coerceAtLeast(1f)
            val h = p.height.toFloat().coerceAtLeast(1f)
            subtitleView.translationX = (settings.horizontalPosition - 0.5f) * w
            subtitleView.translationY = (settings.verticalPosition - 0.95f) * h
        }
    }
}

/**
 * Formats a duration in milliseconds as a time string.
 *
 * Examples:
 *   - 0 ms     -> "0:00"
 *   - 65000 ms -> "1:05"
 *   - 3725000  -> "1:02:05"
 *
 * @param ms Duration in milliseconds
 * @return Formatted time string (H:MM:SS if hours > 0, otherwise M:SS)
 */
fun formatTime(ms: Long): String {
    val seconds = (ms / 1000) % 60
    val minutes = (ms / (1000 * 60)) % 60
    val hours = ms / (1000 * 60 * 60)
    return if (hours > 0) {
        String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(java.util.Locale.US, "%d:%02d", minutes, seconds)
    }
}
