package com.blissless.tensei.ui.screens.settings

import android.content.Context

/**
 * Utility functions for the Settings screen.
 *
 * Extracted from SettingsScreen.kt. Pure functions with no Compose
 * dependencies, used by various settings pages.
 */

internal fun compareVersions(v1: String, v2: String): Int {
    val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
    val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
    val maxLen = maxOf(parts1.size, parts2.size)
    for (i in 0 until maxLen) {
        val p1 = parts1.getOrElse(i) { 0 }
        val p2 = parts2.getOrElse(i) { 0 }
        if (p1 != p2) return p1 - p2
    }
    return 0
}

internal fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> String.format(java.util.Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}

internal fun checkBatteryOpt(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
    return pm?.isIgnoringBatteryOptimizations(context.packageName) == true
}
