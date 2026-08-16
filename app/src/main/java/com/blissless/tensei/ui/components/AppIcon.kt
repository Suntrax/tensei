package com.blissless.tensei.ui.components

import com.blissless.tensei.R

/**
 * Resolves the drawable resource for the currently selected app icon.
 * Mirrors the launcher activity-alias mapping in [com.blissless.tensei.viewmodel.setAppIcon].
 */
fun appIconDrawable(appIcon: String): Int = when (appIcon) {
    "wob" -> R.drawable.ic_icon_wob
    "bow" -> R.drawable.ic_icon_bow
    else -> R.drawable.ic_icon_default
}
