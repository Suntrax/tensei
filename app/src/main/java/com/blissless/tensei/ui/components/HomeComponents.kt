package com.blissless.tensei.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.blissless.tensei.MainViewModel
import com.blissless.tensei.data.models.AnimeMedia
import com.blissless.tensei.data.models.TmdbEpisode
import kotlin.math.absoluteValue

data class HomeAnimeCardBounds(
    val animeId: Int,
    val coverUrl: String,
    val bounds: android.graphics.RectF
)

@Composable
fun LoadingSkeleton() {
    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    )

    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        repeat(3) { sectionIndex ->
            // Section header skeleton
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Box(
                    modifier = Modifier.width(20.dp).height(20.dp).background(
                        shimmerColors[0], RoundedCornerShape(4.dp)
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier.width(140.dp).height(18.dp).background(
                        shimmerColors[0], RoundedCornerShape(4.dp)
                    )
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier.width(24.dp).height(18.dp).background(
                        shimmerColors[0], RoundedCornerShape(12.dp)
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                items(4, key = { "skeleton_${sectionIndex}_$it" }) {
                    Column(modifier = Modifier.width(130.dp)) {
                        // Image area
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(185.dp)
                                .background(
                                    shimmerColors[0],
                                    RoundedCornerShape(8.dp)
                                )
                        )
                        // Title area
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .padding(horizontal = 4.dp)
                                .background(
                                    shimmerColors[0],
                                    RoundedCornerShape(4.dp)
                                )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        // Progress bar skeleton
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .padding(horizontal = 4.dp)
                                .background(
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f),
                                    RoundedCornerShape(2.dp)
                                )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.5f)
                                    .height(3.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                        RoundedCornerShape(2.dp)
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    icon: ImageVector,
    count: Int,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit = {}
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.weight(1f))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            ) {
                Text(
                    "$count",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
fun HomeAnimeHorizontalList(
    animeList: List<AnimeMedia>,
    listType: String,
    showStatusColors: Boolean = false,
    preferEnglishTitles: Boolean = true,
    playbackPositions: Map<String, Long> = emptyMap(),
    playbackDurations: Map<String, Long> = emptyMap(),
    disableMaterialColors: Boolean = false,
    showProgressBar: Boolean = true,
    onAnimeClick: (AnimeMedia, HomeAnimeCardBounds?) -> Unit,
    onInfoClick: (AnimeMedia, HomeAnimeCardBounds?) -> Unit = { _, _ -> },
    listIndex: Int = 0,
    screenKey: String = "home",
    isVisible: Boolean = true,
    viewModel: MainViewModel? = null
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val cameraDistancePx = with(density) { 12.dp.toPx() }
    val translationYOffset = with(density) { (-40).dp.toPx() }

    val isScrolling by remember {
        derivedStateOf { listState.isScrollInProgress }
    }

    val cinematicProgress = rememberCinematicAnimation(screenKey, isVisible, true)
    val staggerDelay = listIndex * 50f
    val effectiveProgress = ((cinematicProgress * 1000f - staggerDelay) / 1000f).coerceIn(0f, 1f)
    val easedProgress = easeOutCubic(effectiveProgress)

    Box(modifier = Modifier.fillMaxWidth()) {
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            itemsIndexed(items = animeList, key = { _, anime -> "${listType}_${anime.id}" }) { index, anime ->
                val layoutInfo by remember { derivedStateOf { listState.layoutInfo } }
                val visibleItems = layoutInfo.visibleItemsInfo
                val itemInfo = visibleItems.find { it.index == index }

                val centerOffset = if (itemInfo != null) {
                    val itemCenter = itemInfo.offset + itemInfo.size / 2
                    val screenCenter = (layoutInfo.viewportSize.width / 2).toFloat()
                    (itemCenter - screenCenter) / screenCenter
                } else {
                    0f
                }

                val animatedOffset by animateFloatAsState(
                    targetValue = if (isScrolling) centerOffset.coerceIn(-1.5f, 1.5f) else 0f,
                    animationSpec = if (isScrolling) {
                        androidx.compose.animation.core.spring(
                            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                        )
                    } else {
                        androidx.compose.animation.core.spring(
                            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                            stiffness = androidx.compose.animation.core.Spring.StiffnessLow
                        )
                    },
                    label = "centerOffset"
                )

                val baseScale = 1f - (animatedOffset.absoluteValue * 0.25f).coerceAtMost(0.25f)
                val baseAlpha = 1f - (animatedOffset.absoluteValue * 0.4f).coerceAtMost(0.6f)
                val translationXVal = animatedOffset * -20f
                val rotationYVal = (animatedOffset * 15f).coerceIn(-15f, 15f)

                val introScale = 0.3f + easedProgress * 0.7f
                val introTranslationY = translationYOffset * (1f - easedProgress)

                val finalScale = baseScale * introScale
                val finalAlpha = baseAlpha * easedProgress

                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = finalScale
                            scaleY = finalScale
                            alpha = finalAlpha
                            translationX = translationXVal
                            translationY = introTranslationY
                            rotationY = rotationYVal
                            cameraDistance = cameraDistancePx
                        }
                        .onGloballyPositioned { coordinates ->
                            val position = coordinates.positionInRoot()
                            val size = coordinates.size
                            val bounds = android.graphics.RectF(
                                position.x,
                                position.y,
                                position.x + size.width,
                                position.y + size.height
                            )
                            viewModel?.setHomeAnimeCardBounds(anime.id, anime.cover, bounds)
                        }
                ) {
                    HomeAnimeCard(
                        anime = anime,
                        listType = listType,
                        showStatusColors = showStatusColors,
                        preferEnglishTitles = preferEnglishTitles,
                        playbackPositions = playbackPositions,
                        playbackDurations = playbackDurations,
                        disableMaterialColors = disableMaterialColors,
                        showProgressBar = showProgressBar,
                        onClick = { bounds ->
                            viewModel?.setHomeAnimeCardBounds(anime.id, anime.cover, bounds?.bounds)
                            onAnimeClick(anime, bounds)
                        },
                        onInfoClick = { bounds ->
                            viewModel?.setHomeAnimeCardBounds(anime.id, anime.cover, bounds?.bounds)
                            onInfoClick(anime, bounds)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun HomeAnimeCard(
    anime: AnimeMedia,
    listType: String,
    showStatusColors: Boolean = false,
    preferEnglishTitles: Boolean = true,
    playbackPositions: Map<String, Long> = emptyMap(),
    playbackDurations: Map<String, Long> = emptyMap(),
    disableMaterialColors: Boolean = false,
    showProgressBar: Boolean = true,
    onClick: (HomeAnimeCardBounds?) -> Unit,
    onInfoClick: (HomeAnimeCardBounds?) -> Unit = {}
) {
    val context = LocalContext.current
    var cardBounds by remember { mutableStateOf<android.graphics.RectF?>(null) }
    val statusColor = HomeStatusColors.getColor(listType)

    // Progress bar color: bright white for monochrome, bright user-defined primary for material colors
    val progressColor = if (disableMaterialColors) Color.White else MaterialTheme.colorScheme.primary

    val total = anime.totalEpisodes
    val released = anime.latestEpisode ?: total

    val nextEpisode = anime.progress + 1
    val playbackKey = "${anime.id}_$nextEpisode"
    val savedPosition = playbackPositions[playbackKey] ?: 0L
    val savedDuration = playbackDurations[playbackKey] ?: 0L
    val effectiveDuration = if (savedDuration > 0L) savedDuration else 24 * 60 * 1000L
    val progressPercent = if (savedPosition > 0) (savedPosition.toFloat() / effectiveDuration.toFloat()).coerceIn(0f, 1f) else 0f

    val progressText = when (listType) {
        "CURRENT" -> {
            when {
                total > 0 && released < total -> "${anime.progress} / $released / $total"
                total > 0 -> "${anime.progress} / $total"
                released > 0 -> "${anime.progress} / $released"
                else -> "${anime.progress}"
            }
        }
        "COMPLETED" -> { if (total > 0) "$total ${if (total == 1) "ep" else "eps"}" else "${anime.progress} ${if (anime.progress == 1) "ep" else "eps"}" }
        "PAUSED", "DROPPED" -> {
            when {
                total > 0 && released < total -> "${anime.progress} / $released / $total"
                total > 0 -> "${anime.progress} / $total"
                released > 0 -> "${anime.progress} / $released"
                else -> if (anime.progress > 0) "${anime.progress}" else "??"
            }
        }
        else -> {
            when {
                total > 0 -> "$released / $total"
                released > 0 -> "$released"
                else -> "??"
            }
        }
    }

    val imageRequest = remember(anime.cover) {
        ImageRequest.Builder(context)
            .data(anime.cover)
            .memoryCacheKey(anime.cover)
            .diskCacheKey(anime.cover)
            .crossfade(false)
            .build()
    }

    Column(modifier = Modifier.width(130.dp)) {
        Card(shape = RoundedCornerShape(12.dp), modifier = Modifier
            .height(185.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                onClick(
                    if (cardBounds != null && cardBounds!!.width() > 0 && cardBounds!!.height() > 0) {
                        HomeAnimeCardBounds(anime.id, anime.cover, cardBounds!!)
                    } else null
                )
            }
            .onGloballyPositioned { coordinates ->
                val position = coordinates.positionInRoot()
                val size = coordinates.size
                cardBounds = android.graphics.RectF(
                    position.x,
                    position.y,
                    position.x + size.width,
                    position.y + size.height
                )
            }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(model = imageRequest, contentDescription = anime.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())

                // Gradient at bottom
                Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(70.dp)
                    .background(Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f)))))

                // Top Row: Episode Counter (left) + Info Button (right)
                Row(
                    modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    // Episode Counter with background
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color.Black.copy(alpha = 0.7f)
                    ) {
                        Text(
                            text = progressText,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }

                    // Info Button (top right)
                    FilledTonalIconButton(
                        onClick = { onInfoClick(cardBounds?.let { HomeAnimeCardBounds(anime.id, anime.cover, it) }) },
                        modifier = Modifier.size(32.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color.Black.copy(alpha = 0.6f), contentColor = Color.White)
                    ) { Icon(imageVector = Icons.Outlined.Info, contentDescription = "Anime Info", modifier = Modifier.size(18.dp)) }
                }

                // Status indicator bar at top (under the text/buttons)
                if (showStatusColors) {
                    Box(modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().height(3.dp).padding(top = 44.dp).background(statusColor))
                }

                // Progress bar at bottom (continue watching indicator)
                if (showProgressBar && progressPercent > 0f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.3f))
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progressPercent)
                                .background(progressColor)
                        )
                    }
                }

            }
        }
        // Title - use English if preferred and available, otherwise use romaji title
        val displayTitle = when {
            preferEnglishTitles && !anime.titleEnglish.isNullOrEmpty() -> anime.titleEnglish
            anime.title.isNotEmpty() -> anime.title
            !anime.titleEnglish.isNullOrEmpty() -> anime.titleEnglish
            else -> "Unknown"
        }
        Box(modifier = Modifier.width(130.dp).height(36.dp)) {
            Text(text = displayTitle, modifier = Modifier.padding(top = 6.dp), maxLines = 2, style = MaterialTheme.typography.labelMedium, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun ContinueWatchingRow(
    animeList: List<AnimeMedia>,
    playbackPositions: Map<String, Long>,
    playbackDurations: Map<String, Long>,
    tmdbEpisodeCache: Map<Int, List<TmdbEpisode>> = emptyMap(),
    preferEnglishTitles: Boolean = true,
    disableMaterialColors: Boolean = false,
    playbackKeySuffix: String = "",
    onPlayClick: (AnimeMedia, Int) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        itemsIndexed(items = animeList, key = { _, anime -> "continue_${anime.id}" }) { _, anime ->
            ContinueWatchingCard(
                anime = anime,
                playbackPositions = playbackPositions,
                playbackDurations = playbackDurations,
                tmdbEpisodeCache = tmdbEpisodeCache,
                preferEnglishTitles = preferEnglishTitles,
                disableMaterialColors = disableMaterialColors,
                playbackKeySuffix = playbackKeySuffix,
                onPlayClick = {
                    val nextEp = anime.progress + 1
                    onPlayClick(anime, nextEp)
                }
            )
        }
    }
}

@Composable
fun ContinueWatchingCard(
    anime: AnimeMedia,
    playbackPositions: Map<String, Long>,
    playbackDurations: Map<String, Long>,
    tmdbEpisodeCache: Map<Int, List<TmdbEpisode>> = emptyMap(),
    preferEnglishTitles: Boolean = true,
    disableMaterialColors: Boolean = false,
    playbackKeySuffix: String = "",
    onPlayClick: () -> Unit
) {
    val context = LocalContext.current

    val nextEpisode = anime.progress + 1
    val playbackKey = "${anime.id}_${nextEpisode}$playbackKeySuffix"
    val savedPosition = playbackPositions[playbackKey] ?: 0L
    val savedDuration = playbackDurations[playbackKey] ?: 0L
    val effectiveDuration = if (savedDuration > 0L) savedDuration else 24 * 60 * 1000L
    val progressPercent = (savedPosition.toFloat() / effectiveDuration.toFloat()).coerceIn(0f, 1f)
    val remainingMs = effectiveDuration - savedPosition

    val tmdbStillUrl = tmdbEpisodeCache[anime.id]?.find { it.episode == nextEpisode }?.image
    val backgroundImageModel: Any = when {
        tmdbStillUrl != null -> tmdbStillUrl
        anime.banner != null -> anime.banner
        else -> anime.cover
    }

    val progressColor = if (disableMaterialColors) Color.White else MaterialTheme.colorScheme.primary

    val displayTitle = when {
        preferEnglishTitles && !anime.titleEnglish.isNullOrEmpty() -> anime.titleEnglish
        anime.title.isNotEmpty() -> anime.title
        !anime.titleEnglish.isNullOrEmpty() -> anime.titleEnglish
        else -> "Unknown"
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .width(220.dp)
            .height(130.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onPlayClick() }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(backgroundImageModel)
                    .crossfade(true)
                    .build(),
                contentDescription = displayTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.3f),
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.7f)
                ) {
                    Text(
                        text = "Ep. $nextEpisode",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.White.copy(alpha = 0.2f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progressPercent)
                                .clip(RoundedCornerShape(3.dp))
                                .background(progressColor)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    if (remainingMs > 0 && savedDuration > 0L) {
                        Text(
                            text = formatTimeRemaining(remainingMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }

                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun formatTimeRemaining(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val min = totalSec / 60
    val sec = totalSec % 60
    return "${min}:${sec.toString().padStart(2, '0')} remaining"
}

@Composable
fun StatusButton(
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
            containerColor = if (selected) selectedColor else Color.White.copy(alpha = 0.08f),
            contentColor = if (selected) Color.White else Color.White.copy(alpha = 0.8f)
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = if (selected) 4.dp else 0.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

private fun easeOutCubic(t: Float): Float {
    val t1 = t - 1
    return t1 * t1 * t1 + 1
}

