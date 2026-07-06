package com.blissless.tensei.ui.components

import androidx.compose.ui.graphics.Color
import com.blissless.tensei.ui.theme.StatusColors

object HomeStatusColors {
    fun getColor(status: String?): Color {
        return StatusColors[status] ?: Color.Gray
    }

    fun getContainerColor(status: String?): Color {
        return getColor(status).copy(alpha = 0.15f)
    }
}

@Deprecated("Use getStatusColor from com.blissless.tensei.ui.theme instead", ReplaceWith("com.blissless.tensei.ui.theme.getStatusColor(status)"))
fun getStatusColor(status: String?): Color = HomeStatusColors.getColor(status)

@Deprecated("Use getStatusContainerColor from com.blissless.tensei.ui.theme instead", ReplaceWith("com.blissless.tensei.ui.theme.getStatusContainerColor(status)"))
fun getStatusContainerColor(status: String?): Color = HomeStatusColors.getContainerColor(status)

