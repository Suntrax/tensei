package com.blissless.tensei.ui.theme

import androidx.compose.ui.graphics.Color

val StatusCurrent = Color(0xFF2196F3)
val StatusPlanning = Color(0xFF9C27B0)
val StatusCompleted = Color(0xFF4CAF50)
val StatusPaused = Color(0xFFFFC107)
val StatusDropped = Color(0xFFF44336)

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

