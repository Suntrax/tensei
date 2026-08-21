package com.blissless.tensei.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun SkipIconButton(
    icon: ImageVector,
    label: String,
    backgroundColor: Color,
    iconTint: Color,
    isCompact: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (isCompact) 2.dp else 4.dp)
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(if (isCompact) 36.dp else 48.dp).background(backgroundColor, shape = MaterialTheme.shapes.small)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(if (isCompact) 20.dp else 28.dp))
        }
        Text(text = label, style = if (isCompact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelSmall.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize), color = Color.White, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

