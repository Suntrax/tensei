package com.blissless.tensei.ui.screens.manga

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.blissless.tensei.ui.theme.StatusColors
import com.blissless.tensei.ui.theme.MangaStatusLabels

@Composable
fun MangaStatusDialog(
    title: String,
    coverUrl: String?,
    currentStatus: String?,
    currentProgress: Int,
    totalChapters: Int,
    onDismiss: () -> Unit,
    onRemove: () -> Unit,
    onUpdate: (String, Int?) -> Unit
) {
    var selectedStatus by remember { mutableStateOf(currentStatus ?: "") }
    var selectedProgress by remember { mutableStateOf(if (currentProgress > 0) currentProgress.toString() else "") }
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
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.width(60.dp).height(85.dp).clip(RoundedCornerShape(10.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val progressText = if (totalChapters > 0) {
                            "$currentProgress / $totalChapters"
                        } else {
                            "$currentProgress"
                        }
                        Text(
                            "Chapter: $progressText",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusButton(icon = Icons.Default.PlayArrow, label = MangaStatusLabels["CURRENT"] ?: "Reading", selected = selectedStatus == "CURRENT" && !markedForRemoval, onClick = { selectedStatus = "CURRENT"; markedForRemoval = false; showAnimation = true }, modifier = Modifier.weight(1f).scale(if (selectedStatus == "CURRENT" && showAnimation && !markedForRemoval) scale else 1f), StatusColors["CURRENT"] ?: MaterialTheme.colorScheme.primary)
                    StatusButton(icon = Icons.Default.Bookmark, label = MangaStatusLabels["PLANNING"] ?: "Planning", selected = selectedStatus == "PLANNING" && !markedForRemoval, onClick = { selectedStatus = "PLANNING"; markedForRemoval = false; showAnimation = true }, modifier = Modifier.weight(1f).scale(if (selectedStatus == "PLANNING" && showAnimation && !markedForRemoval) scale else 1f), StatusColors["PLANNING"] ?: MaterialTheme.colorScheme.primary)
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusButton(icon = Icons.Default.Check, label = MangaStatusLabels["COMPLETED"] ?: "Completed", selected = selectedStatus == "COMPLETED" && !markedForRemoval, onClick = { selectedStatus = "COMPLETED"; markedForRemoval = false; showAnimation = true }, modifier = Modifier.weight(1f).scale(if (selectedStatus == "COMPLETED" && showAnimation && !markedForRemoval) scale else 1f), StatusColors["COMPLETED"] ?: MaterialTheme.colorScheme.primary)
                    StatusButton(icon = Icons.Default.Pause, label = MangaStatusLabels["PAUSED"] ?: "On Hold", selected = selectedStatus == "PAUSED" && !markedForRemoval, onClick = { selectedStatus = "PAUSED"; markedForRemoval = false; showAnimation = true }, modifier = Modifier.weight(1f).scale(if (selectedStatus == "PAUSED" && showAnimation && !markedForRemoval) scale else 1f), StatusColors["PAUSED"] ?: MaterialTheme.colorScheme.primary)
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusButton(icon = Icons.Default.Close, label = MangaStatusLabels["DROPPED"] ?: "Dropped", selected = selectedStatus == "DROPPED" && !markedForRemoval, onClick = { selectedStatus = "DROPPED"; markedForRemoval = false; showAnimation = true }, modifier = Modifier.weight(1f).scale(if (selectedStatus == "DROPPED" && showAnimation && !markedForRemoval) scale else 1f), StatusColors["DROPPED"] ?: MaterialTheme.colorScheme.primary)
                    Button(
                        onClick = { markedForRemoval = !markedForRemoval; showAnimation = true },
                        modifier = Modifier.weight(1f).height(44.dp).scale(if (markedForRemoval && showAnimation) scale else 1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (markedForRemoval) MaterialTheme.colorScheme.error.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            contentColor = if (markedForRemoval) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Remove", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.labelMedium, maxLines = 1, color = if (markedForRemoval) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Chapter Progress", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = selectedProgress,
                    onValueChange = { newValue ->
                        val filtered = newValue.filter { c -> c.isDigit() || c == '.' }
                        val clamped = if (totalChapters > 0) {
                            filtered.toDoubleOrNull()?.coerceIn(0.0, totalChapters.toDouble())?.toString() ?: filtered
                        } else {
                            filtered
                        }
                        selectedProgress = clamped
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Enter last read chapter", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = if (selectedProgress.isEmpty() || selectedProgress == "0") Color.Transparent else MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (markedForRemoval) {
                            onRemove()
                        } else {
                            val progress = selectedProgress.toDoubleOrNull()?.let {
                                if (it % 1.0 == 0.0) it.toInt() else it.toInt()
                            }
                            onUpdate(selectedStatus, progress)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (markedForRemoval) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        if (markedForRemoval) "Remove from List" else "Save Changes",
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun StatusButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectedColor: Color = MaterialTheme.colorScheme.primary
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) selectedColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = if (selected) 4.dp else 0.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}
