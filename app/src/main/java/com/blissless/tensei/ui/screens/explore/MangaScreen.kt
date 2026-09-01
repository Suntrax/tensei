package com.blissless.tensei.ui.screens.explore

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.blissless.tensei.MainViewModel
import com.blissless.tensei.data.models.MangaExploreMedia
import com.blissless.tensei.ui.components.LoadingSkeleton
import com.blissless.tensei.ui.components.SectionTitle
import com.blissless.tensei.ui.components.appIconDrawable
import com.blissless.tensei.ui.screens.manga.MangaStatusDialog
import com.blissless.tensei.ui.theme.StatusColors
import com.blissless.tensei.util.ErrorHandler
import com.blissless.tensei.viewmodel.fetchMangaExplore
import com.blissless.tensei.viewmodel.isLoadingManga
import com.blissless.tensei.viewmodel.mangaCompleted
import com.blissless.tensei.viewmodel.mangaCurrentlyReading
import com.blissless.tensei.viewmodel.mangaExploreSections
import com.blissless.tensei.viewmodel.mangaPlanningToRead
import com.blissless.tensei.viewmodel.removeMangaTracking
import com.blissless.tensei.viewmodel.selectedExtensionAuthority
import com.blissless.tensei.viewmodel.updateMangaProgress
import com.blissless.tensei.viewmodel.updateMangaStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import java.util.Locale

/**
 * Manga explore screen. Mirrors the anime [AnimeScreen] layout but renders
 * only the manga categories pulled from the selected manga extension.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaScreen(
    viewModel: MainViewModel,
    isOled: Boolean = false,
    showMangaCardButtons: Boolean = false,
    showMangaStatusColors: Boolean = false,
    preferEnglishTitles: Boolean = true,
    isVisible: Boolean = true,
    onSearchClick: () -> Unit = {},
    onMangaClick: (MangaExploreMedia) -> Unit = {},
    onMangaReadClick: (MangaExploreMedia) -> Unit = {},
    onMangaNoExtension: () -> Unit = {}
) {
    val mangaExploreSections by viewModel.mangaExploreSections.collectAsState()
    val isLoadingManga by viewModel.isLoadingManga.collectAsState()
    val mangaCurrentlyReading by viewModel.mangaCurrentlyReading.collectAsState()
    val mangaPlanningToRead by viewModel.mangaPlanningToRead.collectAsState()
    val mangaCompleted by viewModel.mangaCompleted.collectAsState()
    val selectedMangaExtension by viewModel.selectedExtensionAuthority.collectAsState()
    val apiError by viewModel.apiError.collectAsState()
    val isOffline by viewModel.isOffline.collectAsState()
    val appIcon by viewModel.appIcon.collectAsState()

    // Create a map of mangaId -> status for quick lookup
    val mangaStatusMap = remember(mangaCurrentlyReading, mangaPlanningToRead, mangaCompleted) {
        buildMap {
            mangaCurrentlyReading.forEach { put(it.id, "CURRENT") }
            mangaPlanningToRead.forEach { put(it.id, "PLANNING") }
            mangaCompleted.forEach { put(it.id, "COMPLETED") }
        }
    }
    val mangaProgressMap = remember(mangaCurrentlyReading, mangaPlanningToRead, mangaCompleted) {
        buildMap {
            (mangaCurrentlyReading + mangaPlanningToRead + mangaCompleted)
                .forEach { if (it.progress > 0) put(it.id, it.progress) }
        }
    }

    var showMangaNoExtensionDialog by remember { mutableStateOf(false) }
    var showMangaStatusDialog by remember { mutableStateOf(false) }
    var selectedMangaForStatus by remember { mutableStateOf<MangaExploreMedia?>(null) }

    // Category list screen state (mirrors home's status list screen overlay)
    var showCategoryList by remember { mutableStateOf(false) }
    var categoryListTitle by remember { mutableStateOf("") }
    var categoryListIcon by remember { mutableStateOf(Icons.Default.MenuBook) }
    var categoryListManga by remember { mutableStateOf<List<MangaExploreMedia>>(emptyList()) }

    // Hide the bottom navbar while the category list overlay is open (mirror home's status list).
    LaunchedEffect(showCategoryList) {
        viewModel.setHideNavbar(showCategoryList)
    }

    fun openMangaCategory(title: String, icon: ImageVector, list: List<MangaExploreMedia>) {
        categoryListTitle = title
        categoryListIcon = icon
        categoryListManga = list
        showCategoryList = true
    }

    // No-default-manga-extension dialog for the Read Now button. The user is taken to
    // the chapter selection only after a manga extension has been chosen.
    if (showMangaNoExtensionDialog) {
        AlertDialog(
            onDismissRequest = { showMangaNoExtensionDialog = false },
            title = { Text("No Extension Selected") },
            text = { Text("Select a default manga extension in Settings to load chapters for this title.") },
            confirmButton = {
                TextButton(onClick = {
                    showMangaNoExtensionDialog = false
                    onMangaNoExtension()
                }) {
                    Text("Go to Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMangaNoExtensionDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Status dialog for manga carousel Save button
    if (showMangaStatusDialog && selectedMangaForStatus != null) {
        val manga = selectedMangaForStatus!!
        MangaStatusDialog(
            title = manga.title.romaji ?: manga.title.english ?: "Unknown",
            titleEnglish = manga.title.english,
            preferEnglishTitles = preferEnglishTitles,
            coverUrl = manga.coverImage?.extraLarge ?: manga.coverImage?.large ?: "",
            currentStatus = mangaStatusMap[manga.id],
            currentProgress = mangaProgressMap[manga.id] ?: 0,
            totalChapters = manga.chapters ?: 0,
            isOled = isOled,
            onDismiss = { showMangaStatusDialog = false },
            onRemove = {
                viewModel.removeMangaTracking(manga.id)
                showMangaStatusDialog = false
            },
            onUpdate = { status, progress ->
                viewModel.updateMangaStatus(manga.id, status, progress, null)
                if (progress != null) {
                    viewModel.updateMangaProgress(manga.id, progress.toFloat())
                }
                showMangaStatusDialog = false
            }
        )
    }

    val scrollState = rememberScrollState()
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Reset pull-to-refresh when loading completes
    LaunchedEffect(isLoadingManga) {
        if (!isLoadingManga) {
            isRefreshing = false
        }
    }

    // True once the manga API has returned anything at all.
    val hasAnyMangaData = mangaExploreSections.values.any { it.isNotEmpty() }

    // Manga explore load cycle: show a loading screen until data arrives or the fetch concludes.
    var mangaTimedOut by remember { mutableStateOf(false) }

    // Whole-screen skeleton: show it immediately on first open (before any fetch has
    // started) and keep it until the fetch returns anything or the fetch cycle concludes.
    var exploreFetchesStarted by remember { mutableStateOf(false) }
    LaunchedEffect(isLoadingManga) {
        if (isLoadingManga) exploreFetchesStarted = true
    }
    val showMangaSkeleton =
        !hasAnyMangaData && (!exploreFetchesStarted || isLoadingManga)

    // Fetch trigger — keyed only on visibility/timeout, so a failed fetch can't re-trigger
    // itself into a request loop when the API is down.
    LaunchedEffect(isVisible, mangaTimedOut) {
        if (isVisible && mangaExploreSections.values.all { it.isEmpty() } && !isLoadingManga && !mangaTimedOut) {
            viewModel.fetchMangaExplore()
        }
    }

    // When the manga fetch concludes without data, surface the API banner immediately.
    LaunchedEffect(isLoadingManga, mangaExploreSections, mangaTimedOut) {
        if (!isLoadingManga && mangaExploreSections.values.all { it.isEmpty() }) mangaTimedOut = true
    }

    // Quit the failure state as soon as data arrives.
    LaunchedEffect(mangaExploreSections) {
        if (mangaExploreSections.values.any { it.isNotEmpty() }) mangaTimedOut = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                if (viewModel.tryManualRefresh("manga")) {
                    isRefreshing = true
                    scope.launch {
                        viewModel.fetchMangaExplore()
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        ) {
            if (showMangaSkeleton) {
                // Loading skeleton while the manga API is still fetching results.
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(bottom = 80.dp)
                ) {
                    Spacer(modifier = Modifier.height(24.dp))
                    LoadingSkeleton()
                    Spacer(modifier = Modifier.height(80.dp))
                }
            } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(bottom = 80.dp)
            ) {
            if (apiError != null || isOffline) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).windowInsetsPadding(WindowInsets.statusBars),
                    shape = RoundedCornerShape(14.dp),
                    color = if (isOffline) Color(0xFF1A1A1A) else if (isOled) Color(0xFF93000A) else MaterialTheme.colorScheme.errorContainer,
                    tonalElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (isOffline) Icons.Default.SignalWifiOff else Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = if (isOffline) Color.White.copy(alpha = 0.7f) else if (isOled) Color(0xFFFFDAD6).copy(alpha = 0.7f) else MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isOffline) "No internet connection" else "AniList is currently unavailable",
                            color = if (isOffline) Color.White.copy(alpha = 0.8f) else if (isOled) Color(0xFFFFDAD6) else MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // No "Manga" header — sections flow directly.
            if (mangaExploreSections.isNotEmpty()) {
                val sectionLabelMap = mapOf(
                    "trending" to "Trending Now",
                    "popular" to "Most Popular",
                    "topRated" to "Top Rated",
                    "favourites" to "Most Favourited",
                    "action" to "Action",
                    "romance" to "Romance",
                    "comedy" to "Comedy",
                    "fantasy" to "Fantasy",
                    "sci-fi" to "Sci-Fi",
                    "seinen" to "Seinen"
                )
                val mangaSectionOrder = listOf(
                    "trending", "popular", "topRated", "favourites",
                    "action", "comedy", "fantasy", "romance", "sci-fi", "seinen"
                )
                val mangaSectionIconMap = mapOf(
                    "trending" to Icons.Default.Whatshot,
                    "popular" to Icons.Default.Favorite,
                    "topRated" to Icons.Default.Star,
                    "favourites" to Icons.Default.Bookmark,
                    "action" to Icons.Default.Whatshot,
                    "romance" to Icons.Default.Favorite,
                    "comedy" to Icons.Default.SentimentSatisfied,
                    "fantasy" to Icons.Default.AutoAwesome,
                    "sci-fi" to Icons.Default.Explore,
                    "seinen" to Icons.Default.MenuBook
                )

                // Featured carousel fed by the trending section (limited to 10 items).
                val trendingList = mangaExploreSections["trending"].orEmpty().take(10)
                if (trendingList.isNotEmpty()) {
                    MangaFeaturedCarousel(
                        mangaList = trendingList,
                        onStatusClick = { manga ->
                            selectedMangaForStatus = manga
                            showMangaStatusDialog = true
                        },
                        onReadClick = { manga ->
                            if (selectedMangaExtension == null) {
                                showMangaNoExtensionDialog = true
                            } else {
                                onMangaReadClick(manga)
                            }
                        },
                        onInfoClick = onMangaClick,
                        mangaStatusMap = mangaStatusMap,
                        preferEnglishTitles = preferEnglishTitles,
                        appIcon = appIcon,
                        onSearchClick = onSearchClick,
                        autoScrollEnabled = isVisible,
                        isVisible = isVisible,
                        isDialogOpen = showMangaStatusDialog || showMangaNoExtensionDialog
                    )
                }

                // Section rows in fixed order. Trending now also renders as a row with the full
                // 50 manga below the carousel (which only shows the first 10).
                mangaSectionOrder.forEach { key ->
                    val list = mangaExploreSections[key].orEmpty()
                    if (list.isNotEmpty()) {
                        val label = sectionLabelMap[key] ?: key.replaceFirstChar { it.uppercase() }
                        SectionTitle(
                            label,
                            list.size,
                            isOled,
                            onClick = {
                                openMangaCategory(
                                    label,
                                    mangaSectionIconMap[key] ?: Icons.Default.MenuBook,
                                    list
                                )
                            }
                        )
                        MangaExploreHorizontalRow(
                            mangaList = list,
                            isOled = isOled,
                            preferEnglishTitles = preferEnglishTitles,
                            showMangaCardButtons = showMangaCardButtons,
                            showMangaStatusColors = showMangaStatusColors,
                            mangaStatusMap = mangaStatusMap,
                            onMangaClick = onMangaClick,
                            onStatusClick = { manga ->
                                selectedMangaForStatus = manga
                                showMangaStatusDialog = true
                            },
                            onReadClick = { manga ->
                                if (selectedMangaExtension == null) {
                                    showMangaNoExtensionDialog = true
                                } else {
                                    onMangaReadClick(manga)
                                }
                            }
                        )
                    }
                }
            } else if (!mangaTimedOut) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Loading manga...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // Manga fetch concluded with nothing returned — show the AniList unavailable banner.
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.CloudOff,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "AniList is currently unavailable",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Nothing was returned. Tap retry to try again.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = {
                            mangaTimedOut = false
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Retry")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            }
        }
        }

        // Full-screen category list overlay (mirrors home's status list screen)
        AnimatedVisibility(
            visible = showCategoryList,
            enter = slideInVertically(animationSpec = tween(300, easing = FastOutSlowInEasing)) { (it * 0.15f).toInt() } + fadeIn(animationSpec = tween(300)),
            exit = slideOutVertically(animationSpec = tween(250, easing = FastOutSlowInEasing)) { (it * 0.15f).toInt() } + fadeOut(animationSpec = tween(250))
        ) {
                ExploreCategoryListScreen(
                    title = categoryListTitle,
                    icon = categoryListIcon,
                    mangaList = categoryListManga,
                    isManga = true,
                    preferEnglishTitles = preferEnglishTitles,
                    onMangaClick = onMangaClick,
                    onMangaBookmarkClick = { manga ->
                        selectedMangaForStatus = manga
                        showMangaStatusDialog = true
                    },
                    onBackClick = { showCategoryList = false },
                    onDismiss = { showCategoryList = false }
                )
        }

    }
}

@Composable
private fun MangaExploreHorizontalRow(
    mangaList: List<MangaExploreMedia>,
    isOled: Boolean,
    preferEnglishTitles: Boolean,
    showMangaCardButtons: Boolean = false,
    showMangaStatusColors: Boolean = false,
    mangaStatusMap: Map<Int, String> = emptyMap(),
    onMangaClick: (MangaExploreMedia) -> Unit,
    onStatusClick: (MangaExploreMedia) -> Unit = {},
    onReadClick: (MangaExploreMedia) -> Unit = {}
) {
    val context = LocalContext.current
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        itemsIndexed(mangaList) { _, manga ->
            val title = if (preferEnglishTitles && !manga.title.english.isNullOrBlank()) manga.title.english!!
                       else manga.title.romaji ?: "Unknown"
            val coverUrl = manga.coverImage?.extraLarge ?: manga.coverImage?.large ?: manga.coverImage?.medium ?: ""
            val status = mangaStatusMap[manga.id]
            val hasStatus = status != null
            val statusIndicatorColor = if (showMangaStatusColors && status != null) {
                (StatusColors[status] ?: Color.Transparent)
            } else {
                Color.Transparent
            }
            val buttonContainerColor = if (showMangaStatusColors && status != null) {
                (StatusColors[status] ?: Color.Black).copy(alpha = 0.8f)
            } else {
                Color.Black.copy(alpha = 0.6f)
            }
            // Match anime card dimensions exactly: 120dp wide, 170dp tall, RoundedCornerShape(4.dp)
            Column(modifier = Modifier.width(120.dp)) {
                Card(
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.height(170.dp).clip(RoundedCornerShape(4.dp)).clickable { onMangaClick(manga) }
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (coverUrl.isNotEmpty()) {
                            AsyncImage(
                                model = ImageRequest.Builder(context).data(coverUrl).crossfade(true).build(),
                                contentDescription = title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) { Text("N/A", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        Box(modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().height(50.dp)
                            .background(Brush.verticalGradient(colors = listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent))))
                        Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(80.dp)
                            .background(Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)))))

                        if (statusIndicatorColor != Color.Transparent) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .background(statusIndicatorColor)
                            )
                        }

                        manga.averageScore?.let { score ->
                            Surface(
                                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = Color.Black.copy(alpha = 0.65f)
                            ) {
                                Text(
                                    "★ ${String.format(Locale.US, "%.1f", score / 10.0)}",
                                    color = Color(0xFFFFD700),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        manga.chapters?.let { ch ->
                            Surface(
                                modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = Color.Black.copy(alpha = 0.65f)
                            ) {
                                Text(
                                    text = "Ch. $ch",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        if (showMangaCardButtons) {
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilledTonalIconButton(
                                    onClick = { onStatusClick(manga) },
                                    modifier = Modifier.size(34.dp),
                                    shape = RoundedCornerShape(4.dp),
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = buttonContainerColor,
                                        contentColor = Color.White
                                    )
                                ) {
                                    AnimatedContent(
                                        targetState = hasStatus,
                                        transitionSpec = {
                                            (scaleIn(animationSpec = tween(200)) + fadeIn())
                                                .togetherWith(scaleOut(animationSpec = tween(200)) + fadeOut())
                                        },
                                        label = "mangaBookmarkIcon"
                                    ) { statusExists ->
                                        Icon(
                                            imageVector = if (statusExists) Icons.Filled.Bookmark else Icons.Outlined.BookmarkAdd,
                                            contentDescription = if (statusExists) "Change status" else "Add status",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.weight(1f))

                                FilledTonalIconButton(
                                    onClick = { onReadClick(manga) },
                                    modifier = Modifier.size(34.dp),
                                    shape = RoundedCornerShape(4.dp),
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = Color.Black.copy(alpha = 0.5f),
                                        contentColor = Color.White
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Read",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                Box(modifier = Modifier.width(120.dp).height(36.dp)) {
                    Text(
                        text = title,
                        modifier = Modifier.padding(top = 8.dp),
                        maxLines = 2,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/**
 * Featured carousel for manga, mirroring the anime featured carousel visual pattern.
 * Renders a tall pager with title, score, format and a "Read Now" button.
 * Uses the portrait cover image (coverImage.extraLarge / large).
 */
@Composable
private fun MangaFeaturedCarousel(
    mangaList: List<MangaExploreMedia>,
    onStatusClick: (MangaExploreMedia) -> Unit,
    onReadClick: (MangaExploreMedia) -> Unit,
    onInfoClick: (MangaExploreMedia) -> Unit,
    mangaStatusMap: Map<Int, String> = emptyMap(),
    preferEnglishTitles: Boolean = true,
    appIcon: String = "default",
    onSearchClick: () -> Unit = {},
    autoScrollEnabled: Boolean = true,
    isVisible: Boolean = true,
    isDialogOpen: Boolean = false
) {
    if (mangaList.isEmpty()) return

    val actualCount = mangaList.size
    val pagerState = rememberPagerState(
        initialPage = actualCount * 100,
        pageCount = { actualCount * 200 }
    )

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDragged by pagerState.interactionSource.collectIsDraggedAsState()
    var autoScrollJob by remember { mutableStateOf<Job?>(null) }
    var headerVisible by remember { mutableStateOf(true) }
    var pageWhenScrollStarted by remember { mutableIntStateOf(pagerState.currentPage) }
    var timerResetSignal by remember { mutableIntStateOf(0) }

    val currentPageOffsetFraction by remember { derivedStateOf { pagerState.currentPageOffsetFraction } }

    LaunchedEffect(pagerState.isScrollInProgress, currentPageOffsetFraction) {
        if (pagerState.isScrollInProgress) {
            pageWhenScrollStarted = pagerState.currentPage
        } else if (pagerState.currentPage != pageWhenScrollStarted) {
            headerVisible = false
            delay(80.milliseconds)
            headerVisible = true
            pageWhenScrollStarted = pagerState.currentPage
        }
    }

    LaunchedEffect(isDragged) {
        if (isDragged) {
            timerResetSignal++
        }
    }

    LaunchedEffect(autoScrollEnabled, isVisible, isDialogOpen, timerResetSignal) {
        if (autoScrollEnabled && isVisible && !isDialogOpen) {
            while (true) {
                delay(4500.milliseconds)

                headerVisible = false
                delay(80.milliseconds)
                headerVisible = true

                autoScrollJob = scope.launch {
                    try {
                        val targetPage = pagerState.currentPage + 1
                        pagerState.animateScrollToPage(targetPage)
                    } catch (e: Exception) {
                        ErrorHandler.ignore("MangaFeaturedCarousel", "best-effort operation failed", e)
                    }
                }
                autoScrollJob?.join()

                delay(300.milliseconds)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { autoScrollJob?.cancel() }
    }

    Box(modifier = Modifier.fillMaxWidth().height(560.dp)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            pageSpacing = 0.dp,
            userScrollEnabled = true,
            beyondViewportPageCount = 0
        ) { page ->
            val manga = mangaList[page % actualCount]
            val coverUrl = manga.coverImage?.extraLarge
                ?: manga.coverImage?.large
                ?: ""

            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                if (coverUrl.isNotEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(coverUrl)
                            .memoryCacheKey(coverUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.35f),
                                Color.Black.copy(alpha = 0.05f),
                                Color.Black.copy(alpha = 0.65f),
                                Color.Black.copy(alpha = 0.95f)
                            )
                        )
                    )
                )
            }
        }

        // Top header with app logo and search icon
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.7f),
                            Color.Transparent
                        )
                    )
                )
                .padding(start = 20.dp, end = 20.dp, top = 32.dp)
                .align(Alignment.TopCenter)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.12f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            AsyncImage(
                                model = appIconDrawable(appIcon),
                                contentDescription = "App",
                                modifier = Modifier.size(32.dp).clip(CircleShape)
                            )
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val currentPage = pagerState.currentPage % actualCount
                        repeat(actualCount) { index ->
                            Box(
                                modifier = Modifier
                                    .size(if (index == currentPage) 16.dp else 5.dp, 5.dp)
                                    .background(
                                        if (index == currentPage) Color.White
                                        else Color.White.copy(alpha = 0.4f),
                                        RoundedCornerShape(3.dp)
                                    )
                            )
                        }
                    }
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.12f),
                        modifier = Modifier.size(40.dp),
                        onClick = onSearchClick
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).align(Alignment.BottomCenter),
            contentAlignment = Alignment.BottomCenter
        ) {
            val currentManga by remember {
                derivedStateOf { mangaList[pagerState.currentPage % actualCount] }
            }

            AnimatedVisibility(
                visible = headerVisible,
                enter = fadeIn(animationSpec = tween(400, easing = FastOutSlowInEasing)) +
                        slideInVertically(
                            animationSpec = tween(400, easing = FastOutSlowInEasing),
                            initialOffsetY = { it / 2 }
                        ),
                exit = fadeOut(animationSpec = tween(150, easing = FastOutSlowInEasing)) +
                        slideOutVertically(
                            animationSpec = tween(150, easing = FastOutSlowInEasing),
                            targetOffsetY = { it / 2 }
                        )
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val displayTitle =
                        if (preferEnglishTitles && !currentManga.title.english.isNullOrBlank()) currentManga.title.english!!
                        else currentManga.title.romaji ?: "Unknown"
                    Text(
                        text = displayTitle,
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val avgScore = currentManga.averageScore
                        val format = currentManga.format
                        val releaseYear = currentManga.seasonYear ?: currentManga.startDate?.year
                        releaseYear?.let { year ->
                            Text(text = year.toString(), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium)
                            Text(text = " • ", color = Color.White.copy(alpha = 0.4f), style = MaterialTheme.typography.bodyMedium)
                        }
                        if (avgScore != null) {
                            val scoreValue = avgScore / 10.0
                            Text(
                                text = "★ ${"%.1f".format(scoreValue)}",
                                color = Color(0xFFFFD700),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(text = " • ", color = Color.White.copy(alpha = 0.4f), style = MaterialTheme.typography.bodyMedium)
                        }
                        val formatText = when (format?.uppercase()) {
                            "NOVEL" -> "Novel"
                            "ONE_SHOT" -> "One Shot"
                            "DOUJIN" -> "Doujin"
                            "MANHWA" -> "Manhwa"
                            "MANHUA" -> "Manhua"
                            else -> "Manga"
                        }
                        Text(text = formatText, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium)
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val currentStatus = mangaStatusMap[currentManga.id]
                        val isSaved = currentStatus != null
                        val statusColor = if (isSaved) (StatusColors[currentStatus] ?: Color.White) else Color.White

                        IconButton(
                            onClick = { onStatusClick(currentManga) },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = (if (isSaved) statusColor else Color.White).copy(alpha = 0.15f),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        if (isSaved) Icons.Default.Bookmark else Icons.Outlined.BookmarkAdd,
                                        contentDescription = "Save",
                                        tint = if (isSaved) statusColor else Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = { onReadClick(currentManga) },
                            modifier = Modifier.height(50.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.15f),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                Icons.Outlined.PlayArrow,
                                contentDescription = "Read",
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Read Now", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        }

                        IconButton(
                            onClick = { onInfoClick(currentManga) },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.12f),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Outlined.Info,
                                        contentDescription = "Info",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
