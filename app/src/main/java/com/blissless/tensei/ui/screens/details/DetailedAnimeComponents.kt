package com.blissless.tensei.ui.screens.details

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Helper Composables and utility functions for [DetailedAnimeScreen].
 *
 * Extracted from DetailedAnimeScreen.kt to reduce file size.
 * These are pure presentation helpers with no business logic.
 */

// ─── Utility functions ─────────────────────────────────────────────────────

internal fun formatDate(dateStr: String): String {
    return try {
        val parts = dateStr.split("-")
        if (parts.size == 3) {
            val date = LocalDate.of(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
            date.format(DateTimeFormatter.ofPattern("d MMMM, yyyy"))
        } else dateStr
    } catch (_: Exception) {
        dateStr
    }
}

internal fun easeOut(t: Float): Float {
    val t1 = t - 1f
    return t1 * t1 * t1 + 1f
}

// ─── Data classes ──────────────────────────────────────────────────────────

internal data class SpecEntry(
    val label: String,
    val value: String,
    val icon: ImageVector? = null,
    val fullSpan: Boolean = false
)

// ─── Composables ───────────────────────────────────────────────────────────

/**
 * A single stat cell used in the hero section: large value on top, small
 * uppercase label below. Optionally prefixed with an icon.
 */
@Composable
internal fun HeroStatCell(
    value: String,
    label: String,
    accent: Color,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon?.let {
                Icon(
                    it,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
        )
    }
}

/**
 * A bento-style spec cell: small uppercase label on top, value (with optional
 * icon) below. Used in the spec grid on the details screen.
 */
@Composable
internal fun BentoSpecCell(spec: SpecEntry, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f))
            .padding(14.dp)
    ) {
        Text(
            spec.label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.2.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            spec.icon?.let { ic ->
                Icon(
                    ic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                spec.value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * "Watch Now" button card shown on the detailed anime screen.
 *
 * Displays a prominent play button that opens the episode selection
 * dialog. Disabled when the anime hasn't aired yet.
 *
 * @param status            The anime's airing status (e.g. "RELEASING", "NOT_YET_RELEASED")
 * @param simplifyEpisodeMenu Whether to use the simplified episode menu
 * @param streamMethod      The configured stream method ("magnet", "direct", etc.)
 * @param hasDefaultMagnetExt Whether a default magnet extension is configured
 * @param hasDefaultExtPkg Whether a default extension package is configured
 * @param onNoDefaultExtension Called when no default extension is set
 * @param onShowEpisodeSelection Called when the button is clicked and an extension is configured
 */
@Composable
internal fun WatchNowButton(
    status: String?,
    simplifyEpisodeMenu: Boolean,
    streamMethod: String,
    hasDefaultMagnetExt: Boolean,
    hasDefaultExtPkg: Boolean,
    onNoDefaultExtension: () -> Unit,
    onShowEpisodeSelection: () -> Unit,
) {
    val notYetAired = status == "NOT_YET_RELEASED"
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
    ) {
        Box(modifier = Modifier.padding(12.dp)) {
            Button(
                onClick = {
                    val hasDefault = simplifyEpisodeMenu ||
                        streamMethod == "magnet" && hasDefaultMagnetExt ||
                        streamMethod == "direct" && hasDefaultExtPkg
                    if (!hasDefault) {
                        onNoDefaultExtension()
                    } else {
                        onShowEpisodeSelection()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = !notYetAired,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                )
            ) {
                Icon(Icons.Default.PlayArrow, null, Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Watch Now", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/**
 * Trailer card shown on the detailed anime screen.
 *
 * Displays a 16:9 thumbnail with a play button overlay. Clicking the
 * card or the play button opens the trailer URL (usually YouTube) in
 * an external app via ACTION_VIEW intent.
 *
 * @param trailerUrl       The URL to open when clicked
 * @param trailerThumbnail The thumbnail image URL to display
 */
@Composable
internal fun TrailerCard(
    trailerUrl: String?,
    trailerThumbnail: String?,
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable {
                val intent = Intent(Intent.ACTION_VIEW, trailerUrl?.toUri())
                context.startActivity(intent)
            },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PlayCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        "Trailer",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Watch the trailer",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        letterSpacing = 0.5.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                AsyncImage(
                    model = trailerThumbnail ?: "",
                    contentDescription = "Trailer Thumbnail",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    FilledIconButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, trailerUrl?.toUri())
                            context.startActivity(intent)
                        },
                        modifier = Modifier.size(60.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color(0xFF1A1A1A).copy(alpha = 0.9f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Play Trailer",
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Watch on YouTube", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * "No Default Extension" alert dialog.
 *
 * Shown when the user tries to watch an episode but no default
 * extension or magnet extension is configured. Offers to navigate
 * to settings or cancel.
 *
 * @param onDismiss  Called when the dialog is dismissed (Cancel or backdrop)
 * @param onGoToSettings Called when the user taps "Go to Settings"
 */
@Composable
internal fun NoDefaultExtensionDialog(
    onDismiss: () -> Unit,
    onGoToSettings: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("No Default Extension") },
        text = { Text("Set a default extension in Settings to enable streaming.") },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onGoToSettings()
            }) {
                Text("Go to Settings")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
