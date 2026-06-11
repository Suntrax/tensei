package com.blissless.tensei.ui.components

import androidx.compose.ui.graphics.Color

object HomeStatusColors {
    fun getColor(status: String?): Color {
        return when (status) {
            "CURRENT" -> Color(0xFF2196F3)
            "PLANNING" -> Color(0xFF9C27B0)
            "COMPLETED" -> Color(0xFF4CAF50)
            "PAUSED" -> Color(0xFFFFC107)
            "DROPPED" -> Color(0xFFF44336)
            else -> Color.Gray
        }
    }

    fun getContainerColor(status: String?): Color {
        return getColor(status).copy(alpha = 0.2f)
    }
}

@Deprecated("Use getStatusColor from com.blissless.tensei.ui.theme instead", ReplaceWith("com.blissless.tensei.ui.theme.getStatusColor(status)"))
fun getStatusColor(status: String?): Color = HomeStatusColors.getColor(status)

@Deprecated("Use getStatusContainerColor from com.blissless.tensei.ui.theme instead", ReplaceWith("com.blissless.tensei.ui.theme.getStatusContainerColor(status)"))
fun getStatusContainerColor(status: String?): Color = HomeStatusColors.getContainerColor(status)

