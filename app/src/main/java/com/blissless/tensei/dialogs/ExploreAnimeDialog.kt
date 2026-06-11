package com.blissless.tensei.dialogs

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.blissless.tensei.MainViewModel
import com.blissless.tensei.data.models.AnimeRelation
import com.blissless.tensei.data.models.ExploreAnime
import com.blissless.tensei.ui.components.HomeStatusColors
import java.util.Locale

@Composable
fun ExploreAnimeDialog(
    anime: ExploreAnime,
    viewModel: MainViewModel,
    isOled: Boolean = false,
    currentStatus: String?,
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    onDismiss: () -> Unit,
    onAddToPlanning: () -> Unit,
    onAddToDropped: () -> Unit = {},
    onAddToOnHold: () -> Unit = {},
    onRemoveFromList: () -> Unit = {},
    onStartWatching: (Int) -> Unit,
    isLoggedIn: Boolean = false,
    onLoginClick: () -> Unit = {},
    onRelationClick: (AnimeRelation) -> Unit = {}
) {
    val context = LocalContext.current
    val displayScore = anime.averageScore?.let { it / 10.0 }
    var selectedStatus by remember { mutableStateOf(currentStatus ?: "") }
    var markedForRemoval by remember { mutableStateOf(false) }
    var showAnimation by remember { mutableStateOf(false) }
    var statusSubmitted by remember { mutableStateOf(currentStatus != null) }

    val scale by animateFloatAsState(
        targetValue = if (showAnimation) 1.05f else 1f,
        animationSpec = tween(150),
        finishedListener = { if (showAnimation) showAnimation = false },
        label = "statusScale"
    )

    val hasStatusChanged = selectedStatus != (currentStatus ?: "")

    // Cached image request
    val imageRequest = remember(anime.cover) {
        ImageRequest.Builder(context)
            .data(anime.cover)
            .memoryCacheKey(anime.cover)
            .diskCacheKey(anime.cover)
            .crossfade(false)
            .build()
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = if (isOled) Color.Black else Color(0xFF1A1A1A))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    AsyncImage(model = imageRequest, contentDescription = anime.title, contentScale = ContentScale.Crop, modifier = Modifier.width(90.dp).height(130.dp).clip(RoundedCornerShape(12.dp)))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(anime.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis, color = Color.White)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            displayScore?.let { score ->
                                Text("★ ${String.format(Locale.US, "%.1f", score)}", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFFFD700), fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(12.dp))
                            }
                            when {
                                anime.episodes > 0 -> Text("${anime.episodes} eps", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f))
                                anime.latestEpisode != null && anime.latestEpisode > 0 -> Text("Ep ${anime.latestEpisode}", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f))
                                else -> Text("? eps", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f))
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        if (anime.genres.isNotEmpty()) { Text(anime.genres.take(3).joinToString(" • "), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.5f), maxLines = 2, overflow = TextOverflow.Ellipsis) }
                        Spacer(modifier = Modifier.height(8.dp))
                        anime.year?.let { Text("Released: $it", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Medium) }
                        Spacer(modifier = Modifier.height(6.dp))

                        val currentDisplayStatus = if (statusSubmitted) (selectedStatus.ifEmpty { currentStatus ?: "" }) else (currentStatus ?: "")
                        if (currentDisplayStatus.isNotEmpty() && !markedForRemoval) {
                            ExploreStatusBadge(status = currentDisplayStatus)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                // Favorite Button
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (isFavorite) {
                        Button(
                            onClick = {
                                onToggleFavorite()
                            },
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFF1744).copy(alpha = 0.3f),
                                contentColor = Color(0xFFFF1744)
                            )
                        ) {
                            Icon(Icons.Filled.Favorite, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Favorited", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                onToggleFavorite()
                            },
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Outlined.FavoriteBorder, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Favorite", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { onStartWatching(1) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Start Watching", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Close", color = Color.White.copy(alpha = 0.6f)) }
            }
        }
    }
}

@Composable
private fun ExploreStatusBadge(status: String) {
    Surface(shape = RoundedCornerShape(6.dp), color = HomeStatusColors.getContainerColor(status)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)) {
            Icon(
                imageVector = when(status) { "CURRENT" -> Icons.Default.PlayArrow; "PLANNING" -> Icons.Default.Bookmark; "COMPLETED" -> Icons.Default.Check; "PAUSED" -> Icons.Default.Pause; "DROPPED" -> Icons.Default.Close; else -> Icons.Default.Info },
                contentDescription = null, modifier = Modifier.size(14.dp), tint = HomeStatusColors.getColor(status)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = when(status) { "CURRENT" -> "Watching"; "PLANNING" -> "Planning"; "COMPLETED" -> "Completed"; "PAUSED" -> "On Hold"; "DROPPED" -> "Dropped"; else -> status },
                style = MaterialTheme.typography.labelMedium, color = HomeStatusColors.getColor(status)
            )
        }
    }
}

