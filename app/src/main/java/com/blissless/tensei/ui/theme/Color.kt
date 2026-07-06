package com.blissless.tensei.ui.theme

import androidx.compose.ui.graphics.Color

val StatusCurrent = Color(0xFF60A5FA)
val StatusPlanning = Color(0xFFC084FC)
val StatusCompleted = Color(0xFF34D399)
val StatusPaused = Color(0xFFFBBF24)
val StatusDropped = Color(0xFFF87171)

val StatusColors = mapOf(
    "CURRENT" to StatusCurrent,
    "PLANNING" to StatusPlanning,
    "COMPLETED" to StatusCompleted,
    "PAUSED" to StatusPaused,
    "DROPPED" to StatusDropped
)

val StatusLabels = mapOf(
    "CURRENT" to "Watching",
    "PLANNING" to "Planning",
    "COMPLETED" to "Completed",
    "PAUSED" to "On Hold",
    "DROPPED" to "Dropped"
)

val SurfaceWhite = Color(0xFFF8F8F8)

val GlassWhite = Color(0x1AFFFFFF)
val GlassBlack = Color(0x1A000000)

val SurfaceElevatedLight = Color(0xFFFAFAFA)
val SurfaceElevatedDark = Color(0xFF1E1E1E)

