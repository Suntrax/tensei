package com.blissless.tensei.ui.screens.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
