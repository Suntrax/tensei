package com.blissless.tensei.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Dialog shown when the user logs in after having tracked anime offline.
 *
 * Extracted from MainActivity.kt. Offers three choices:
 *   1. Discard all local changes
 *   2. Add only new anime (don't overwrite existing entries)
 *   3. Overwrite AniList entries with local changes
 *
 * @param localAnimeCount Number of anime tracked offline (shown in the message)
 * @param onDismiss       Called when the dialog should close (e.g. on backdrop tap)
 * @param onDiscard       User chose to discard local changes
 * @param onAddNewOnly    User chose to add only new anime
 * @param onOverwrite     User chose to overwrite AniList with local data
 */
@Composable
fun LocalSyncDialog(
    localAnimeCount: Int,
    onDismiss: () -> Unit,
    onDiscard: () -> Unit,
    onAddNewOnly: () -> Unit,
    onOverwrite: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A1A),
        title = {
            Text(
                "Sync Local Changes",
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column {
                Text(
                    "You have $localAnimeCount anime tracked offline.",
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "Choose how to sync:",
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    "1. Discard Local Changes",
                    color = Color(0xFFF44336),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "Remove all offline changes. AniList data will remain unchanged.",
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    "2. Add New Anime Only",
                    color = Color(0xFF4CAF50),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "Add new anime from offline to AniList. Won't overwrite existing entries.",
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    "3. Overwrite AniList",
                    color = Color(0xFF2196F3),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "Replace all matching anime on AniList with your offline changes.",
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDiscard) {
                Text("Discard", color = Color(0xFFF44336))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onAddNewOnly) {
                    Text("Add New Only", color = Color(0xFF4CAF50))
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onOverwrite) {
                    Text("Overwrite", color = Color(0xFF2196F3))
                }
            }
        }
    )
}
