package com.blissless.tensei.ui.screens.status

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.blissless.tensei.data.models.AnimeMedia
import com.blissless.tensei.data.models.MangaMedia
import com.blissless.tensei.ui.components.HomeAnimeCardBounds
import com.blissless.tensei.ui.components.HomeStatusColors
import com.blissless.tensei.ui.components.rememberCinematicAnimation
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

enum class SortOption(val label: String, val icon: ImageVector) {
    ALPHABETICAL_A_Z("A-Z", Icons.AutoMirrored.Filled.Sort),
    ALPHABETICAL_Z_A("Z-A", Icons.AutoMirrored.Filled.Sort),
    YEAR_NEWEST("Year \u2193", Icons.Default.DateRange),
    YEAR_OLDEST("Year \u2191", Icons.Default.DateRange),
    EPISODES_MOST("Eps \u2193", Icons.Default.PlayArrow),
    EPISODES_LEAST("Eps \u2191", Icons.Default.PlayArrow),
    SCORE_HIGH("Score \u2193", Icons.Default.Star),
    SCORE_LOW("Score \u2191", Icons.Default.Star)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusListScreen(
    title: String,
    icon: ImageVector,
    animeList: List<AnimeMedia> = emptyList(),
    mangaList: List<MangaMedia> = emptyList(),
    listType: String,
    isManga: Boolean = false,
    showStatusColors: Boolean = true,
    preferEnglishTitles: Boolean = true,
    onAnimeClick: (AnimeMedia, HomeAnimeCardBounds?) -> Unit = { _, _ -> },
    onPlayClick: (AnimeMedia) -> Unit = {},
    onInfoClick: (AnimeMedia, HomeAnimeCardBounds?) -> Unit = { _, _ -> },
    onStatusClick: (AnimeMedia) -> Unit = {},
    onMangaClick: (MangaMedia) -> Unit = {},
    onMangaInfoClick: (MangaMedia) -> Unit = {},
    onMangaContinueReadingClick: (MangaMedia) -> Unit = {},
    onMangaStatusClick: (MangaMedia) -> Unit = {},
    onBackClick: () -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    val iconTint = HomeStatusColors.getColor(listType)
    val focusManager = LocalFocusManager.current

    BackHandler(onBack = onBackClick)

    var offsetY by remember { mutableFloatStateOf(0f) }
    val animatedOffset by animateFloatAsState(
        targetValue = offsetY,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "offsetY"
    )

    var selectedSort by remember { mutableStateOf(SortOption.ALPHABETICAL_A_Z) }
    var showSortSheet by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val displayAnimeList = remember(animeList, searchQuery, selectedSort) {
        val filtered = if (searchQuery.isBlank()) animeList
        else animeList.filter {
            val t = if (preferEnglishTitles && !it.titleEnglish.isNullOrEmpty()) it.titleEnglish else it.title
            t.contains(searchQuery, ignoreCase = true)
        }
        when (selectedSort) {
            SortOption.ALPHABETICAL_A_Z -> filtered.sortedBy {
                (if (preferEnglishTitles && !it.titleEnglish.isNullOrEmpty()) it.titleEnglish else it.title).lowercase()
            }
            SortOption.ALPHABETICAL_Z_A -> filtered.sortedByDescending {
                (if (preferEnglishTitles && !it.titleEnglish.isNullOrEmpty()) it.titleEnglish else it.title).lowercase()
            }
            SortOption.YEAR_NEWEST -> filtered.sortedByDescending { it.year ?: Int.MIN_VALUE }
            SortOption.YEAR_OLDEST -> filtered.sortedBy { it.year ?: Int.MAX_VALUE }
            SortOption.EPISODES_MOST -> filtered.sortedByDescending {
                if (it.totalEpisodes > 0) it.totalEpisodes else it.latestEpisode ?: 0
            }
            SortOption.EPISODES_LEAST -> filtered.sortedBy {
                if (it.totalEpisodes > 0) it.totalEpisodes else it.latestEpisode ?: 0
            }
            SortOption.SCORE_HIGH -> filtered.sortedByDescending { it.averageScore ?: Int.MIN_VALUE }
            SortOption.SCORE_LOW -> filtered.sortedBy { it.averageScore ?: Int.MAX_VALUE }
        }
    }

    val displayMangaList = remember(mangaList, searchQuery, selectedSort) {
        val filtered = if (searchQuery.isBlank()) mangaList
        else mangaList.filter { it.title.contains(searchQuery, ignoreCase = true) }
        when (selectedSort) {
            SortOption.ALPHABETICAL_A_Z -> filtered.sortedBy { it.title.lowercase() }
            SortOption.ALPHABETICAL_Z_A -> filtered.sortedByDescending { it.title.lowercase() }
            SortOption.YEAR_NEWEST -> filtered.sortedByDescending { it.year ?: Int.MIN_VALUE }
            SortOption.YEAR_OLDEST -> filtered.sortedBy { it.year ?: Int.MAX_VALUE }
            SortOption.EPISODES_MOST -> filtered.sortedByDescending {
                if (it.totalChapters > 0) it.totalChapters else it.latestChapter?.toInt() ?: 0
            }
            SortOption.EPISODES_LEAST -> filtered.sortedBy {
                if (it.totalChapters > 0) it.totalChapters else it.latestChapter?.toInt() ?: 0
            }
            SortOption.SCORE_HIGH -> filtered.sortedByDescending { it.averageScore ?: Int.MIN_VALUE }
            SortOption.SCORE_LOW -> filtered.sortedBy { it.averageScore ?: Int.MAX_VALUE }
        }
    }

    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val scrollToTop = {
        scope.launch { gridState.animateScrollToItem(0) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .graphicsLayer {
                translationY = animatedOffset
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (offsetY > 150f) {
                            onDismiss()
                        }
                        offsetY = 0f
                    },
                    onVerticalDrag = { _, dragAmount ->
                        offsetY = (offsetY + dragAmount).coerceAtLeast(0f)
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures { focusManager.clearFocus() }
            }
    ) {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { scrollToTop() }
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isManga) "${mangaList.size} manga" else "${animeList.size} anime",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { focusManager.clearFocus(); showSortSheet = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = "Sort",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                windowInsets = WindowInsets(0, 0, 0, 0)
            )

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("Search in this list...") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Clear search"
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            if ((if (isManga) mangaList.size else animeList.size) == 0) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = iconTint.copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (isManga) "No manga in this list" else "No anime in this list",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                val density = LocalDensity.current
                val translationYOffset = with(density) { (-30).dp.toPx() }

                val isScrolling by remember {
                    derivedStateOf { gridState.isScrollInProgress }
                }

                val cinematicProgress = rememberCinematicAnimation("statusList_$listType",
                    isVisible = true,
                    playOncePerSession = true
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    state = gridState,
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (isManga) {
                        itemsIndexed(items = displayMangaList, key = { _, manga -> "${listType}_${manga.id}" }) { index, manga ->
                            val staggerDelay = minOf(index, 20) * 30f
                            val staggerMs = staggerDelay / 1000f
                            val rawProgress = ((cinematicProgress - staggerMs) / (1f - staggerMs))
                            val easedProgress = easeOutCubic(rawProgress.coerceAtLeast(0f).coerceAtMost(1f))

                            val introScale = 0.3f + easedProgress * 0.7f
                            val introAlpha = easedProgress.coerceAtLeast(0f)
                            val introTranslationY = translationYOffset * (1f - easedProgress)

                            val centerOffset by remember {
                                derivedStateOf {
                                    val layoutInfo = gridState.layoutInfo
                                    val visibleItems = layoutInfo.visibleItemsInfo
                                    val itemInfo = visibleItems.find { it.index == index }
                                    if (itemInfo != null) {
                                        val itemCenter = itemInfo.offset.y + itemInfo.size.height / 2
                                        val screenCenter = (layoutInfo.viewportSize.height / 2).toFloat()
                                        (itemCenter - screenCenter) / screenCenter
                                    } else 0f
                                }
                            }

                            val animatedOffset by animateFloatAsState(
                                targetValue = if (isScrolling) centerOffset.coerceIn(-2f, 2f) else 0f,
                                animationSpec = if (isScrolling) {
                                    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
                                } else {
                                    spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
                                },
                                label = "centerOffset"
                            )

                            val scrollScale = 1f - (animatedOffset.absoluteValue * 0.15f).coerceAtMost(0.15f)
                            val scrollAlpha = 1f - (animatedOffset.absoluteValue * 0.3f).coerceAtMost(0.4f)
                            val scrollParallax = animatedOffset * 20f
                            val finalScale = scrollScale * introScale
                            val finalAlpha = (scrollAlpha * introAlpha).coerceIn(0f, 1f)
                            val finalTranslationY = scrollParallax + introTranslationY

                            Box(
                                modifier = Modifier
                                    .animateItem()
                                    .graphicsLayer {
                                        scaleX = finalScale
                                        scaleY = finalScale
                                        alpha = finalAlpha
                                        translationY = finalTranslationY
                                    }
                            ) {
                                StatusListMangaCard(
                                    manga = manga,
                                    listType = listType,
                                    showStatusColors = showStatusColors,
                                    onClick = { onMangaClick(manga) },
                                    onInfoClick = { onMangaInfoClick(manga) },
                                    onContinueReadingClick = { onMangaContinueReadingClick(manga) },
                                    onStatusClick = { onMangaStatusClick(manga) }
                                )
                            }
                        }
                    } else {
                        itemsIndexed(items = displayAnimeList, key = { _, anime -> "${listType}_${anime.id}" }) { index, anime ->
                            val staggerDelay = minOf(index, 20) * 30f
                            val staggerMs = staggerDelay / 1000f
                            val rawProgress = ((cinematicProgress - staggerMs) / (1f - staggerMs))
                            val easedProgress = easeOutCubic(rawProgress.coerceAtLeast(0f).coerceAtMost(1f))

                            val introScale = 0.3f + easedProgress * 0.7f
                            val introAlpha = easedProgress.coerceAtLeast(0f)
                            val introTranslationY = translationYOffset * (1f - easedProgress)

                            val centerOffset by remember {
                                derivedStateOf {
                                    val layoutInfo = gridState.layoutInfo
                                    val visibleItems = layoutInfo.visibleItemsInfo
                                    val itemInfo = visibleItems.find { it.index == index }
                                    if (itemInfo != null) {
                                        val itemCenter = itemInfo.offset.y + itemInfo.size.height / 2
                                        val screenCenter = (layoutInfo.viewportSize.height / 2).toFloat()
                                        (itemCenter - screenCenter) / screenCenter
                                    } else 0f
                                }
                            }

                            val animatedOffset by animateFloatAsState(
                                targetValue = if (isScrolling) centerOffset.coerceIn(-2f, 2f) else 0f,
                                animationSpec = if (isScrolling) {
                                    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
                                } else {
                                    spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
                                },
                                label = "centerOffset"
                            )

                            val scrollScale = 1f - (animatedOffset.absoluteValue * 0.15f).coerceAtMost(0.15f)
                            val scrollAlpha = 1f - (animatedOffset.absoluteValue * 0.3f).coerceAtMost(0.4f)
                            val scrollParallax = animatedOffset * 20f
                            val finalScale = scrollScale * introScale
                            val finalAlpha = (scrollAlpha * introAlpha).coerceIn(0f, 1f)
                            val finalTranslationY = scrollParallax + introTranslationY

                            Box(
                                modifier = Modifier
                                    .animateItem()
                                    .graphicsLayer {
                                        scaleX = finalScale
                                        scaleY = finalScale
                                        alpha = finalAlpha
                                        translationY = finalTranslationY
                                    }
                            ) {
                                StatusListAnimeCard(
                                    anime = anime,
                                    listType = listType,
                                    showStatusColors = showStatusColors,
                                    preferEnglishTitles = preferEnglishTitles,
                                    onClick = { bounds -> onAnimeClick(anime, bounds) },
                                    onPlayClick = { onPlayClick(anime) },
                                    onInfoClick = { bounds -> onInfoClick(anime, bounds) },
                                    onStatusClick = { onStatusClick(anime) }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showSortSheet) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showSortSheet = false },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(bottom = 32.dp)) {
                    Text(
                        "Sort by",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                    )
                    HorizontalDivider()
                    SortOption.entries.forEach { option ->
                        val isSelected = option == selectedSort
                        val displayLabel = if (isManga) {
                            when (option) {
                                SortOption.EPISODES_MOST -> "Chapters \u2193"
                                SortOption.EPISODES_LEAST -> "Chapters \u2191"
                                else -> option.label
                            }
                        } else option.label
                        Surface(
                            onClick = { focusManager.clearFocus(); selectedSort = option; showSortSheet = false; scrollToTop() },
                            color = if (isSelected) iconTint.copy(alpha = 0.12f) else Color.Transparent,
                            shape = RoundedCornerShape(0.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = null,
                                    colors = RadioButtonDefaults.colors(selectedColor = iconTint)
                                )
                                Icon(
                                    imageVector = option.icon,
                                    contentDescription = null,
                                    tint = if (isSelected) iconTint else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    displayLabel,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun easeOutCubic(t: Float): Float {
    val t1 = t - 1
    return t1 * t1 * t1 + 1
}

@Composable
private fun StatusListAnimeCard(
    anime: AnimeMedia,
    listType: String,
    showStatusColors: Boolean,
    preferEnglishTitles: Boolean,
    onClick: (HomeAnimeCardBounds?) -> Unit,
    onPlayClick: () -> Unit,
    onInfoClick: (HomeAnimeCardBounds?) -> Unit,
    onStatusClick: () -> Unit
) {
    val context = LocalContext.current
    var cardBounds by remember { mutableStateOf<android.graphics.RectF?>(null) }
    val statusColor = HomeStatusColors.getColor(listType)

    val total = anime.totalEpisodes
    val released = anime.latestEpisode ?: total

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

    Column(modifier = Modifier.fillMaxWidth()) {
        Card(
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier
                .height(220.dp)
                .clip(RoundedCornerShape(4.dp))
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
                AsyncImage(
                    model = imageRequest,
                    contentDescription = anime.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(80.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                            )
                        )
                )

                // Top Row: Episode Counter (left) + Rating (right)
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color.Black.copy(alpha = 0.7f)
                    ) {
                        Text(
                            text = progressText,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }

                    if (anime.averageScore != null && anime.averageScore > 0) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color.Black.copy(alpha = 0.65f)
                        ) {
                            Text(
                                text = "★ ${String.format(java.util.Locale.US, "%.1f", anime.averageScore / 10.0)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFFFD700),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                if (showStatusColors) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(3.dp)
                            .padding(top = 52.dp)
                            .background(statusColor)
                    )
                }

                // Bottom Row: Edit Status Button (left) + Info Button (right)
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(
                        onClick = onStatusClick,
                        modifier = Modifier.size(32.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.6f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Edit Status",
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    FilledTonalIconButton(
                        onClick = { onInfoClick(cardBounds?.let { HomeAnimeCardBounds(anime.id, anime.cover, it) }) },
                        modifier = Modifier.size(32.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.6f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = "Info",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        val displayTitle = if (preferEnglishTitles && !anime.titleEnglish.isNullOrEmpty()) anime.titleEnglish else anime.title
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .padding(top = 6.dp)
        ) {
            Text(
                text = displayTitle,
                maxLines = 2,
                style = MaterialTheme.typography.labelMedium,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun StatusListMangaCard(
    manga: MangaMedia,
    listType: String,
    showStatusColors: Boolean,
    onClick: () -> Unit,
    onInfoClick: () -> Unit,
    onContinueReadingClick: () -> Unit,
    onStatusClick: () -> Unit
) {
    val context = LocalContext.current
    val statusColor = HomeStatusColors.getColor(listType)
    val progressText = when (listType) {
        "CURRENT" -> if (manga.totalChapters > 0) "${manga.progress} / ${manga.totalChapters}" else "Ch. ${manga.progress}"
        "COMPLETED" -> if (manga.totalChapters > 0) "${manga.totalChapters} ch." else "Ch. ${manga.progress}"
        else -> if (manga.totalChapters > 0) "${manga.totalChapters} ch." else null
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Card(
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier
                .height(220.dp)
                .clip(RoundedCornerShape(4.dp))
                .clickable(onClick = onClick)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(manga.cover).crossfade(true).build(),
                    contentDescription = manga.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(80.dp)
                        .background(Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))))
                )

                // Top Row: Chapter Counter (left) + Rating (right)
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    if (progressText != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color.Black.copy(alpha = 0.7f)
                        ) {
                            Text(
                                text = progressText,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }

                    if (manga.averageScore != null && manga.averageScore > 0) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color.Black.copy(alpha = 0.65f)
                        ) {
                            Text(
                                text = "★ ${String.format(java.util.Locale.US, "%.1f", manga.averageScore / 10.0)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFFFD700),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                if (showStatusColors) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(3.dp)
                            .padding(top = 52.dp)
                            .background(statusColor)
                    )
                }

                // Bottom Row: Edit Status Button (left) + Info Button (right)
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(
                        onClick = onStatusClick,
                        modifier = Modifier.size(32.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.6f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Edit Status",
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    FilledTonalIconButton(
                        onClick = onInfoClick,
                        modifier = Modifier.size(32.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.6f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = "Info",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
        Box(
            modifier = Modifier.fillMaxWidth().height(40.dp).padding(top = 6.dp)
        ) {
            Text(
                text = manga.title,
                maxLines = 2,
                style = MaterialTheme.typography.labelMedium,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

