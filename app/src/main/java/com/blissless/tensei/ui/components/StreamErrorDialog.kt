package com.blissless.tensei.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * Stream error dialog with auto-dismiss.
 *
 * Extracted from MainActivity.kt. Shows a stream error message with an OK
 * button. Auto-dismisses after 3.5 seconds.
 *
 * @param error     The error message to display.
 * @param onDismiss Called when the dialog is dismissed (by OK button, backdrop,
 *                  or auto-dismiss timeout).
 */
@Composable
fun StreamErrorDialog(
    error: String,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(error) {
        delay(3500.milliseconds)
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = "Stream Error",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error
            )
        },
        text = {
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                modifier = Modifier.width(250.dp)
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = null
    )
}
