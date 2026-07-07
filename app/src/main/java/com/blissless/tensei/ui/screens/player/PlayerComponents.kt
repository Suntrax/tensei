package com.blissless.tensei.ui.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Reusable UI components for the Player screen.
 *
 * Extracted from PlayerScreen.kt to reduce file size.
 */

/**
 * Stream error overlay shown when playback fails.
 *
 * Displays the error message with an optional "Try Next Server" button
 * that cycles through available servers.
 *
 * @param errorMessage     The error message to display
 * @param showTryNextServer Whether to show the "Try Next Server" button
 * @param onTryNextServer  Called when the user taps "Try Next Server"
 */
@Composable
internal fun StreamErrorOverlay(
    errorMessage: String,
    showTryNextServer: Boolean,
    onTryNextServer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Refresh,
                null,
                tint = Color(0xFFFFA726),
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("Stream Error", color = Color.White, style = MaterialTheme.typography.titleMedium)
            Text(errorMessage, color = Color.Gray, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))

            Spacer(modifier = Modifier.height(12.dp))

            if (showTryNextServer) {
                Button(
                    onClick = onTryNextServer,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Try Next Server")
                }
            }
        }
    }
}

/**
 * Loading indicator shown when the stream is loading or changing servers.
 *
 * @param modifier Modifier for positioning (typically align to center)
 */
@Composable
internal fun PlayerLoadingIndicator(
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.CircularProgressIndicator(
        modifier = modifier,
        color = Color.White
    )
}

/**
 * Volume overlay indicator shown when the user adjusts volume via swipe.
 *
 * Displays a vertical progress bar with a volume icon and percentage.
 *
 * @param visible            Whether the overlay is shown
 * @param volume             Volume level (0.0 to 1.0)
 * @param disableMaterialColors Whether to use white instead of Material primary
 */
@Composable
internal fun VolumeOverlay(
    visible: Boolean,
    volume: Float,
    disableMaterialColors: Boolean,
    modifier: Modifier = Modifier,
) {
    val accentColor = if (disableMaterialColors) Color.White else MaterialTheme.colorScheme.primary
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(200)) + slideInVertically { it / 4 },
        exit = fadeOut(animationSpec = tween(300)),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .padding(start = 16.dp)
                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = "Volume",
                tint = accentColor,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = "${(volume * 100).toInt()}%",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium
            )
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(80.dp)
                    .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(2.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(volume)
                        .align(Alignment.BottomCenter)
                        .background(accentColor, RoundedCornerShape(2.dp))
                )
            }
        }
    }
}

/**
 * Brightness overlay indicator shown when the user adjusts brightness via swipe.
 *
 * Displays a vertical progress bar with a brightness icon and percentage.
 *
 * @param visible            Whether the overlay is shown
 * @param brightness         Brightness level (0.01 to 1.0)
 * @param disableMaterialColors Whether to use white instead of amber
 */
@Composable
internal fun BrightnessOverlay(
    visible: Boolean,
    brightness: Float,
    disableMaterialColors: Boolean,
    modifier: Modifier = Modifier,
) {
    val accentColor = if (disableMaterialColors) Color.White else Color(0xFFFFD54F)
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(200)) + slideInVertically { it / 4 },
        exit = fadeOut(animationSpec = tween(300)),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .padding(end = 16.dp)
                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.BrightnessHigh,
                contentDescription = "Brightness",
                tint = accentColor,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = "${(brightness * 100).toInt()}%",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium
            )
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(80.dp)
                    .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(2.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(brightness)
                        .align(Alignment.BottomCenter)
                        .background(accentColor, RoundedCornerShape(2.dp))
                )
            }
        }
    }
}
