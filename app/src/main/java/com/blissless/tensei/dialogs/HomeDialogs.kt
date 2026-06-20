package com.blissless.tensei.dialogs

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.blissless.tensei.data.models.AnimeMedia
import com.blissless.tensei.ui.components.HomeStatusColors
import com.blissless.tensei.ui.components.StatusButton

@Composable
fun HomeAnimeStatusDialog(
    anime: AnimeMedia,
    isOled: Boolean,
    onDismiss: () -> Unit,
    onRemove: () -> Unit,
    onUpdate: (String, Int?) -> Unit
) {
    var selectedStatus by remember { mutableStateOf(anime.listStatus) }
    var selectedProgress by remember { mutableStateOf(if (anime.progress > 0) anime.progress.toString() else "") }
    var markedForRemoval by remember { mutableStateOf(false) }
    var showAnimation by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (showAnimation) 1.05f else 1f,
        animationSpec = tween(150),
        finishedListener = { if (showAnimation) showAnimation = false },
        label = "statusScale"
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = if (isOled) Color.Black else Color(0xFF1A1A1A))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(model = anime.cover, contentDescription = anime.title, contentScale = ContentScale.Crop, modifier = Modifier.width(60.dp).height(85.dp).clip(RoundedCornerShape(10.dp)))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(anime.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(modifier = Modifier.height(4.dp))
                        val latestEp = anime.latestEpisode?.takeIf { it > 0 }
                        val totalEp = anime.totalEpisodes.takeIf { it > 0 }
                        val progressText = when {
                            latestEp != null && latestEp > 0 && totalEp != null -> "${anime.progress} / $latestEp / $totalEp"
                            latestEp != null && latestEp > 0 -> "${anime.progress} / $latestEp"
                            totalEp != null -> "${anime.progress} / $totalEp"
                            else -> "${anime.progress}"
                        }
                        Text("Progress: $progressText", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
                        anime.year?.let { Text("Released: $it", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.5f)) }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                // Row 1: Watching and Planning
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusButton(
                        icon = Icons.Default.PlayArrow,
                        label = "Watching",
                        selected = selectedStatus == "CURRENT" && !markedForRemoval,
                        onClick = {
                            selectedStatus = "CURRENT"
                            markedForRemoval = false
                            showAnimation = true
                        },
                        modifier = Modifier.weight(1f).scale(if (selectedStatus == "CURRENT" && showAnimation && !markedForRemoval) scale else 1f),
                        HomeStatusColors.getColor("CURRENT")
                    )
                    StatusButton(
                        icon = Icons.Default.Bookmark,
                        label = "Planning",
                        selected = selectedStatus == "PLANNING" && !markedForRemoval,
                        onClick = {
                            selectedStatus = "PLANNING"
                            markedForRemoval = false
                            showAnimation = true
                        },
                        modifier = Modifier.weight(1f).scale(if (selectedStatus == "PLANNING" && showAnimation && !markedForRemoval) scale else 1f),
                        HomeStatusColors.getColor("PLANNING")
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Row 2: Completed and On Hold
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusButton(
                        icon = Icons.Default.Check,
                        label = "Completed",
                        selected = selectedStatus == "COMPLETED" && !markedForRemoval,
                        onClick = {
                            selectedStatus = "COMPLETED"
                            markedForRemoval = false
                            showAnimation = true
                        },
                        modifier = Modifier.weight(1f).scale(if (selectedStatus == "COMPLETED" && showAnimation && !markedForRemoval) scale else 1f),
                        HomeStatusColors.getColor("COMPLETED")
                    )
                    StatusButton(
                        icon = Icons.Default.Pause,
                        label = "On Hold",
                        selected = selectedStatus == "PAUSED" && !markedForRemoval,
                        onClick = {
                            selectedStatus = "PAUSED"
                            markedForRemoval = false
                            showAnimation = true
                        },
                        modifier = Modifier.weight(1f).scale(if (selectedStatus == "PAUSED" && showAnimation && !markedForRemoval) scale else 1f),
                        HomeStatusColors.getColor("PAUSED")
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Row 3: Dropped and Remove
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusButton(
                        icon = Icons.Default.Close,
                        label = "Dropped",
                        selected = selectedStatus == "DROPPED" && !markedForRemoval,
                        onClick = {
                            selectedStatus = "DROPPED"
                            markedForRemoval = false
                            showAnimation = true
                        },
                        modifier = Modifier.weight(1f).scale(if (selectedStatus == "DROPPED" && showAnimation && !markedForRemoval) scale else 1f),
                        HomeStatusColors.getColor("DROPPED")
                    )
                    Button(
                        onClick = {
                            markedForRemoval = !markedForRemoval
                            showAnimation = true
                        },
                        modifier = Modifier.weight(1f).height(44.dp).scale(if (markedForRemoval && showAnimation) scale else 1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (markedForRemoval) Color.Red.copy(alpha = 0.3f) else Color.Gray.copy(alpha = 0.15f),
                            contentColor = if (markedForRemoval) Color.Red else Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Remove", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.labelMedium, maxLines = 1, color = if (markedForRemoval) Color.Red else Color.White.copy(alpha = 0.7f))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Episode Progress", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
                Spacer(modifier = Modifier.height(8.dp))

                val maxEp = anime.latestEpisode?.takeIf { it > 0 } ?: anime.totalEpisodes.takeIf { it > 0 }

                OutlinedTextField(
                    value = selectedProgress,
                    onValueChange = { newValue ->
                        val filtered = newValue.filter { c -> c.isDigit() }
                        val clamped = if (maxEp != null) {
                            filtered.toIntOrNull()?.coerceIn(0, maxEp)?.toString() ?: filtered
                        } else {
                            filtered
                        }
                        selectedProgress = clamped
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Enter last watched episode", color = Color.White.copy(alpha = 0.4f)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = if (selectedProgress.isEmpty() || selectedProgress == "0") Color.Transparent else Color.White,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (markedForRemoval) { onRemove() }
                        else { val progress = selectedProgress.toIntOrNull(); onUpdate(selectedStatus, progress) }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (markedForRemoval) Color.Red else MaterialTheme.colorScheme.primary, contentColor = if (markedForRemoval) Color.White else MaterialTheme.colorScheme.onPrimary)
                ) { Text(if (markedForRemoval) "Remove from List" else "Save Changes", fontWeight = FontWeight.Bold) }

                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancel", color = Color.White.copy(alpha = 0.6f)) }
            }
        }
    }
}
