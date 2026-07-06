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
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Star
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

/**
 * Information card showing anime specs (format, status, season, studio, dates).
 *
 * Displays a hero stats strip (episodes/duration/score) and a bento-style
 * spec grid built from the anime's metadata.
 *
 * @param displayData  The detailed anime data to show
 * @param statusDisplay The human-readable status string (e.g. "Airing", "Released")
 */
@Composable
internal fun InfoCard(
    displayData: com.blissless.tensei.data.models.DetailedAnimeData,
    statusDisplay: String,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // ----- Section header: gradient icon tile + title + subtitle -----
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
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        "Information",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Overview & details",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // ----- Hero stats strip (Episodes / Duration / Score) -----
            val latestEp = displayData.latestEpisode?.takeIf { it > 0 }
            val totalEp = displayData.episodes.takeIf { it > 0 }
            val epDisplay = when {
                latestEp != null && totalEp != null -> "$latestEp / $totalEp"
                latestEp != null -> "$latestEp"
                totalEp != null -> "$totalEp"
                else -> null
            }
            val durationMin = displayData.duration?.takeIf { it > 0 }
            val scoreValue = displayData.averageScore?.takeIf { it > 0 }

            val heroStats = listOfNotNull<Pair<String, String>>(
                epDisplay?.let { "Episodes" to it },
                durationMin?.let { "Duration" to "${it}m" },
                scoreValue?.let { "Score" to String.format(java.util.Locale.US, "%.1f", it / 10.0) }
            )

            if (heroStats.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    heroStats.forEachIndexed { index, (label, value) ->
                        val accent = when (label) {
                            "Episodes" -> MaterialTheme.colorScheme.primary
                            "Duration" -> MaterialTheme.colorScheme.tertiary
                            "Score"    -> Color(0xFFFFB300)
                            else       -> MaterialTheme.colorScheme.primary
                        }
                        val icon = when (label) {
                            "Score" -> Icons.Default.Star
                            else    -> null
                        }
                        HeroStatCell(
                            value = value,
                            label = label,
                            accent = accent,
                            icon = icon,
                            modifier = Modifier.weight(1f)
                        )
                        if (index < heroStats.lastIndex) {
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(28.dp)
                                    .background(
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                    )
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // ----- Bento spec grid (2-column, responsive full-span rows) -----
            val specs = buildList {
                displayData.format?.let {
                    add(
                        SpecEntry(
                            label = "Format",
                            value = it.replace("_", " ")
                                .lowercase()
                                .replaceFirstChar { c -> c.uppercase() },
                            icon = Icons.Default.Category
                        )
                    )
                }
                displayData.status?.let {
                    add(
                        SpecEntry(
                            label = "Status",
                            value = statusDisplay,
                            icon = Icons.Default.PlayCircle
                        )
                    )
                }
                if (displayData.season != null && displayData.year != null) {
                    add(
                        SpecEntry(
                            label = "Season",
                            value = "${displayData.season.replace("_", " ").lowercase().replaceFirstChar { c -> c.uppercase() }} ${displayData.year}"
                        )
                    )
                }
                displayData.source?.let {
                    add(
                        SpecEntry(
                            label = "Source",
                            value = it.replace("_", " ")
                                .lowercase()
                                .replaceFirstChar { c -> c.uppercase() },
                            icon = Icons.Default.Description
                        )
                    )
                }
                if (displayData.studios.isNotEmpty()) {
                    val studio = displayData.studios
                        .filter { it.isAnimationStudio }
                        .joinToString(", ") { it.name }
                    if (studio.isNotEmpty()) {
                        add(
                            SpecEntry(
                                label = "Studio",
                                value = studio,
                                icon = Icons.Default.Group,
                                fullSpan = true
                            )
                        )
                    }
                }
                displayData.startDate?.let {
                    add(SpecEntry(label = "Started", value = formatDate(it)))
                }
                if (displayData.status != "RELEASING" && displayData.status != "NOT_YET_RELEASED") {
                    displayData.endDate?.let {
                        add(SpecEntry(label = "Ended", value = formatDate(it)))
                    }
                }
            }

            var i = 0
            while (i < specs.size) {
                val current = specs[i]
                val next = specs.getOrNull(i + 1)

                if (next != null && !current.fullSpan) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)
                    ) {
                        BentoSpecCell(current, modifier = Modifier.weight(1f))
                        BentoSpecCell(next, modifier = Modifier.weight(1f))
                    }
                    i += 2
                } else {
                    BentoSpecCell(current, modifier = Modifier.fillMaxWidth())
                    i += 1
                }
                if (i < specs.size) {
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }
}

/**
 * Genres card showing anime genres as chips.
 *
 * @param genres List of genre name strings to display
 */
@Composable
internal fun GenresCard(
    genres: List<String>,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
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
                        Icons.Default.Category,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        "Genres",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Categories & themes",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        letterSpacing = 0.5.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
            ) {
                genres.forEach { genre ->
                    androidx.compose.material3.Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    ) {
                        Text(
                            genre,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Tags card showing anime tags as clickable chips with show more/less.
 *
 * @param tags            List of TagData to display
 * @param showAllTags     Whether to show all tags or just the first 2
 * @param onTagClick      Called when a tag chip is clicked
 * @param onToggleShowAll Called when "Show More"/"Show Less" is clicked
 */
@Composable
internal fun TagsCard(
    tags: List<com.blissless.tensei.data.models.TagData>,
    showAllTags: Boolean,
    onTagClick: (com.blissless.tensei.data.models.TagData) -> Unit,
    onToggleShowAll: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
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
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f),
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.06f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Label,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        "Tags",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Labels & descriptors",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        letterSpacing = 0.5.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            val nonSpoilerTags = tags.filter { !it.isMediaSpoiler }
            val displayedTags = if (showAllTags) nonSpoilerTags else nonSpoilerTags.take(2)
            val remainingCount = nonSpoilerTags.size - 2

            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
            ) {
                displayedTags.forEach { tag ->
                    androidx.compose.material3.Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)),
                        modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable {
                            onTagClick(tag)
                        }
                    ) {
                        Text(
                            tag.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
                if (remainingCount > 0 && !showAllTags) {
                    Text(
                        "+$remainingCount more",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f),
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }
            }
            if (remainingCount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onToggleShowAll,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (showAllTags) "Show Less" else "Show More",
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

/**
 * Synopsis card showing the anime description with read more/less.
 *
 * @param description        The raw description string (may contain HTML tags)
 * @param showFullDescription Whether to show the full description or truncate
 * @param onToggleShowFull   Called when "Read More"/"Show Less" is clicked
 */
@Composable
internal fun SynopsisCard(
    description: String,
    showFullDescription: Boolean,
    onToggleShowFull: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
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
                        Icons.Default.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        "Synopsis",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Story summary",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        letterSpacing = 0.5.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            val cleanDescription = description.replace("<br>", "\n").replace("<br/>", "\n")
                .replace("<b>", "").replace("</b>", "").replace("<i>", "").replace("</i>", "")
            Text(cleanDescription, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (showFullDescription) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis,
                lineHeight = 22.sp)
            if (cleanDescription.length > 250) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onToggleShowFull,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (showFullDescription) "Show Less" else "Read More",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
