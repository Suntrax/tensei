package com.blissless.tensei.ui.screens.explore

import com.blissless.tensei.data.models.isAdultContent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blissless.tensei.MainViewModel
import com.blissless.tensei.data.models.AnimeMedia
import com.blissless.tensei.data.models.ExploreAnime
import com.blissless.tensei.data.models.LocalAnimeEntry
import com.blissless.tensei.data.models.MangaExploreMedia
import com.blissless.tensei.data.models.toDetailedAnimeData
import com.blissless.tensei.dialogs.HomeAnimeStatusDialog
import com.blissless.tensei.ui.components.AnimeCardBounds
import com.blissless.tensei.ui.components.ExploreAnimeHorizontalList
import com.blissless.tensei.ui.components.LoadingPlaceholder
import com.blissless.tensei.ui.screens.episode.EpisodeSelectionDialog
import com.blissless.tensei.ui.screens.episode.RichEpisodeScreen
import com.blissless.tensei.ui.components.SectionTitle
import com.blissless.tensei.ui.screens.details.DetailedAnimeScreen
import com.blissless.tensei.ui.screens.details.NoDefaultExtensionDialog
import com.blissless.tensei.ui.screens.manga.MangaStatusDialog
import com.blissless.tensei.ui.theme.StatusCompleted
import com.blissless.tensei.ui.theme.StatusCurrent
import com.blissless.tensei.ui.theme.StatusDropped
import com.blissless.tensei.ui.theme.StatusPaused
import com.blissless.tensei.ui.theme.StatusPlanning
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope
import kotlin.time.Duration.Companion.milliseconds
import com.blissless.tensei.util.toast
import com.blissless.tensei.util.longToast
import com.blissless.tensei.viewmodel.mangaExploreSections
import com.blissless.tensei.viewmodel.isLoadingManga
import com.blissless.tensei.viewmodel.fetchMangaExplore
import com.blissless.tensei.viewmodel.mangaCurrentlyReading
import com.blissless.tensei.viewmodel.mangaPlanningToRead
import com.blissless.tensei.viewmodel.mangaCompleted
import com.blissless.tensei.viewmodel.selectedExtensionAuthority
import com.blissless.tensei.viewmodel.removeMangaTracking
import com.blissless.tensei.viewmodel.updateMangaStatus
import com.blissless.tensei.viewmodel.updateMangaProgress
import coil.compose.AsyncImage
import java.util.Locale
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import coil.request.ImageRequest
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.text.style.TextAlign
import com.blissless.tensei.util.ErrorHandler
import kotlinx.coroutines.Job

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    viewModel: MainViewModel,
    isLoggedIn: Boolean = false,
    isOled: Boolean = false,
    showStatusColors: Boolean = true,
    showAnimeCardButtons: Boolean = true,
    preferEnglishTitles: Boolean = true,
    hideAdultContent: Boolean = true,
    favoriteIds: Set<Int> = emptySet(),
    onPlayEpisode: (AnimeMedia, Int, String?) -> Unit = { _, _, _ -> },
    currentlyWatching: List<AnimeMedia> = emptyList(),
    planningToWatch: List<AnimeMedia> = emptyList(),
    completed: List<AnimeMedia> = emptyList(),
    onHold: List<AnimeMedia> = emptyList(),
    dropped: List<AnimeMedia> = emptyList(),
    isVisible: Boolean = true,
    onClearAnimeStack: () -> Unit = {},
    onCharacterClick: (Int) -> Unit = {},
    onStaffClick: (Int) -> Unit = {},
    onViewAllCast: (Int, String) -> Unit = { _, _ -> },
    onViewAllStaff: (Int, String) -> Unit = { _, _ -> },
    onViewAllRelations: (Int, String) -> Unit = { _, _ -> },
    onViewAllRecommendations: (Int, String) -> Unit = { _, _ -> },
    onSearchClick: () -> Unit = {},
    onNoExtension: () -> Unit = {},
    onMangaClick: (MangaExploreMedia) -> Unit = {},
    onMangaReadClick: (MangaExploreMedia) -> Unit = {},
    onMangaNoExtension: () -> Unit = {}
) {
    val context = LocalContext.current
    val featuredAnime by viewModel.featuredAnime.collectAsState()
    val seasonalAnime by viewModel.seasonalAnime.collectAsState()
    val topSeries by viewModel.topSeries.collectAsState()
    val topMovies by viewModel.topMovies.collectAsState()
    val actionAnime by viewModel.actionAnime.collectAsState()
    val romanceAnime by viewModel.romanceAnime.collectAsState()
    val comedyAnime by viewModel.comedyAnime.collectAsState()
    val fantasyAnime by viewModel.fantasyAnime.collectAsState()
    val scifiAnime by viewModel.scifiAnime.collectAsState()
    val isLoading by viewModel.isLoadingExplore.collectAsState()
    val apiError by viewModel.apiError.collectAsState()
    val isOffline by viewModel.isOffline.collectAsState()
    val simplifyEpisodeMenu by viewModel.simplifyEpisodeMenu.collectAsState(initial = true)
    val defaultMagnetExt by viewModel.defaultMagnetExtension.collectAsState()
    val streamMethod by viewModel.streamMethod.collectAsState()
    val defaultExtPkg by viewModel.defaultExtensionPackage.collectAsState()
    val localAnimeStatus by viewModel.localAnimeStatus.collectAsState()
    val mangaExploreSections by viewModel.mangaExploreSections.collectAsState()
    val isLoadingManga by viewModel.isLoadingManga.collectAsState()
    val mangaCurrentlyReading by viewModel.mangaCurrentlyReading.collectAsState()
    val mangaPlanningToRead by viewModel.mangaPlanningToRead.collectAsState()
    val mangaCompleted by viewModel.mangaCompleted.collectAsState()
    val selectedMangaExtension by viewModel.selectedExtensionAuthority.collectAsState()
    
    val filteredFeaturedAnime = remember(featuredAnime, hideAdultContent) {
        if (hideAdultContent) featuredAnime.filter { !isAdultContent(it.isAdult, it.genres) } else featuredAnime
    }
    val filteredSeasonalAnime = remember(seasonalAnime, hideAdultContent) {
        if (hideAdultContent) seasonalAnime.filter { !isAdultContent(it.isAdult, it.genres) } else seasonalAnime
    }
    val filteredTopSeries = remember(topSeries, hideAdultContent) { if (hideAdultContent) topSeries.filter { !isAdultContent(it.isAdult, it.genres) } else topSeries }
    val filteredTopMovies = remember(topMovies, hideAdultContent) { if (hideAdultContent) topMovies.filter { !isAdultContent(it.isAdult, it.genres) } else topMovies }
    val filteredActionAnime = remember(actionAnime, hideAdultContent) { if (hideAdultContent) actionAnime.filter { !isAdultContent(it.isAdult, it.genres) } else actionAnime }
    val filteredRomanceAnime = remember(romanceAnime, hideAdultContent) { if (hideAdultContent) romanceAnime.filter { !isAdultContent(it.isAdult, it.genres) } else romanceAnime }
    val filteredComedyAnime = remember(comedyAnime, hideAdultContent) { if (hideAdultContent) comedyAnime.filter { !isAdultContent(it.isAdult, it.genres) } else comedyAnime }
    val filteredFantasyAnime = remember(fantasyAnime, hideAdultContent) { if (hideAdultContent) fantasyAnime.filter { !isAdultContent(it.isAdult, it.genres) } else fantasyAnime }
    val filteredScifiAnime = remember(scifiAnime, hideAdultContent) { if (hideAdultContent) scifiAnime.filter { !isAdultContent(it.isAdult, it.genres) } else scifiAnime }

    // Create a map of animeId -> status for quick lookup
    val animeStatusMap = remember(currentlyWatching, planningToWatch, completed, onHold, dropped) {
        buildMap {
            currentlyWatching.forEach { put(it.id, "CURRENT") }
            planningToWatch.forEach { put(it.id, "PLANNING") }
            completed.forEach { put(it.id, "COMPLETED") }
            onHold.forEach { put(it.id, "PAUSED") }
            dropped.forEach { put(it.id, "DROPPED") }
        }
    }

    val animeProgressMap = remember(currentlyWatching, planningToWatch, completed, onHold, dropped) {
        buildMap {
            currentlyWatching.forEach { if (it.progress > 0) put(it.id, it.progress) }
            planningToWatch.forEach { if (it.progress > 0) put(it.id, it.progress) }
            completed.forEach { if (it.progress > 0) put(it.id, it.progress) }
            onHold.forEach { if (it.progress > 0) put(it.id, it.progress) }
            dropped.forEach { if (it.progress > 0) put(it.id, it.progress) }
        }
    }

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

    // Derive currentStatus from lists dynamically to ensure immediate UI updates

    var selectedAnime by remember { mutableStateOf<ExploreAnime?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    var showEpisodeSelection by remember { mutableStateOf(false) }
    var showStatusDialog by remember { mutableStateOf(false) }
    var showNoExtensionDialog by remember { mutableStateOf(false) }
    var showMangaNoExtensionDialog by remember { mutableStateOf(false) }
    var showMangaStatusDialog by remember { mutableStateOf(false) }
    var selectedMangaForStatus by remember { mutableStateOf<MangaExploreMedia?>(null) }
    
    // Force recomposition when lists change by tracking a version counter
    var listVersion by remember { mutableIntStateOf(0) }
    
    // Update listVersion when lists change to trigger recomposition
    LaunchedEffect(currentlyWatching, planningToWatch, completed, onHold, dropped) {
        listVersion++
    }
    
    // Track navigation history for back button
    var firstAnime by remember { mutableStateOf<ExploreAnime?>(null) }
    
    // Card bounds for shared element transition
    var currentCardBounds by remember { mutableStateOf<MainViewModel.CardBounds?>(null) }
    
    // Scope for coroutines - must be at composition level
    val scope = rememberCoroutineScope()

    // Show anime dialog
    if (showDialog && selectedAnime != null) {
        // Set first anime on first open
        if (firstAnime == null) {
            firstAnime = selectedAnime
        }
        
        val anime = selectedAnime!!
        val isAnimeFavorite = favoriteIds.contains(anime.id)
        // Use derived status that updates immediately when lists change
        // Key on listVersion to force recomposition when lists update
        val animeStatus by remember(listVersion, anime.id) {
            derivedStateOf { animeStatusMap[anime.id] }
        }
        val animeProgress by remember(listVersion, anime.id) {
            derivedStateOf { animeProgressMap[anime.id] }
        }

        DetailedAnimeScreen(
            anime = anime.toDetailedAnimeData(),
            viewModel = viewModel,
            isOled = isOled,
            currentStatus = animeStatus,
            currentProgress = animeProgress,
            isFavorite = isAnimeFavorite,
            initialCardBounds = currentCardBounds,
            onDismiss = {
                currentCardBounds = null
                if (firstAnime != null && selectedAnime?.id != firstAnime?.id) {
                    selectedAnime = firstAnime
                } else {
                    showDialog = false
                    firstAnime = null
                    onClearAnimeStack()
                }
            },
            onSwipeToClose = {
                currentCardBounds = null
                showDialog = false
                onClearAnimeStack()
            },
            onPlayEpisode = { episode, _ ->
                val animeMedia = AnimeMedia(
                    id = anime.id,
                    title = anime.title,
                    cover = anime.cover,
                    banner = anime.banner,
                    progress = 0,
                    totalEpisodes = anime.episodes,
                    latestEpisode = anime.latestEpisode,
                    status = "",
                    averageScore = anime.averageScore,
                    genres = anime.genres,
                    listStatus = "",
                    listEntryId = 0
                )
                onPlayEpisode(animeMedia, episode, null)
                showDialog = false
            },
            onUpdateStatus = { status ->
                if (status != null) {
                    viewModel.addExploreAnimeToList(anime, status)
                }
            },
            onRemove = {
                viewModel.removeAnimeFromList(anime.id)
            },
            isLoggedIn = isLoggedIn,
            onRelationClick = { relation ->
                try {
                    scope.launch {
                        try {
                            delay(100.milliseconds)
                            viewModel.clearExploreAnimeCardBounds()
                            currentCardBounds = null
                            val detailedData = viewModel.fetchDetailedAnimeData(relation.id)
                            if (detailedData != null) {
                                selectedAnime = ExploreAnime(
                                    id = relation.id,
                                    title = detailedData.title,
                                    titleEnglish = detailedData.titleEnglish,
                                    cover = detailedData.cover,
                                    banner = detailedData.banner,
                                    episodes = detailedData.episodes,
                                    latestEpisode = detailedData.latestEpisode,
                                    averageScore = detailedData.averageScore,
                                    genres = detailedData.genres,
                                    year = detailedData.year,
                                    format = detailedData.format
                                )
                            } else {
                                context.toast("Anime not found - ID: ${relation.id}")
                            }
                        } catch (e: Exception) {
                            context.toast("Error: ${e.message}")
                        }
                    }
                } catch (e: Exception) {
                    context.toast("Error: ${e.message}")
                }
            },
            onCharacterClick = onCharacterClick,
            onStaffClick = onStaffClick,
            onViewAllCast = { onViewAllCast(selectedAnime!!.id, selectedAnime!!.title) },
            onViewAllStaff = { onViewAllStaff(selectedAnime!!.id, selectedAnime!!.title) },
            onViewAllRelations = { animeId, title ->
                onViewAllRelations(animeId, title)
            },
            onViewAllRecommendations = { animeId, title ->
                onViewAllRecommendations(animeId, title)
            },
            onNoExtension = {
                showDialog = false
                onNoExtension()
            }
        )
    }

    // Episode selection dialog for Watch Now button
    if (showEpisodeSelection && selectedAnime != null) {
        val anime = selectedAnime!!
        val animeMedia = AnimeMedia(
            id = anime.id,
            title = anime.title,
            titleEnglish = anime.titleEnglish,
            cover = anime.cover,
            banner = anime.banner,
            totalEpisodes = anime.episodes,
            latestEpisode = anime.latestEpisode,
            status = "",
            averageScore = anime.averageScore,
            genres = anime.genres,
            listStatus = "",
            listEntryId = 0
        )
        if (simplifyEpisodeMenu) {
            EpisodeSelectionDialog(
                anime = animeMedia,
                isOled = isOled,
                onDismiss = { showEpisodeSelection = false },
                onEpisodeSelect = { episode, _ ->
                    onPlayEpisode(animeMedia, episode, null)
                    showEpisodeSelection = false
                }
            )
        } else {
            RichEpisodeScreen(
                anime = animeMedia,
                viewModel = viewModel,
                isOled = isOled,
                preferEnglishTitles = preferEnglishTitles,
                onDismiss = { showEpisodeSelection = false },
                onEpisodeSelect = { episode, _ ->
                    onPlayEpisode(animeMedia, episode, null)
                    showEpisodeSelection = false
                }
            )
        }
    }

    // Status dialog for carousel Save button
    if (showStatusDialog && selectedAnime != null) {
        val anime = selectedAnime!!
        val animeMedia = AnimeMedia(
            id = anime.id,
            title = anime.title,
            titleEnglish = anime.titleEnglish,
            cover = anime.cover,
            banner = anime.banner,
            totalEpisodes = anime.episodes,
            latestEpisode = anime.latestEpisode,
            listStatus = animeStatusMap[anime.id] ?: "",
            status = animeStatusMap[anime.id] ?: "",
            averageScore = anime.averageScore,
            genres = anime.genres
        )
        HomeAnimeStatusDialog(
            anime = animeMedia,
            isOled = isOled,
            onDismiss = { showStatusDialog = false },
            onRemove = {
                viewModel.removeAnimeFromList(anime.id)
                showStatusDialog = false
            },
            onUpdate = { status, _ ->
                viewModel.addExploreAnimeToList(anime, status)
                showStatusDialog = false
            }
        )
    }

    // No-default-extension dialog for the Watch Now button. Instead of immediately
    // redirecting to Settings, ask the user first — they can cancel or continue.
    if (showNoExtensionDialog) {
        NoDefaultExtensionDialog(
            onDismiss = { showNoExtensionDialog = false },
            onGoToSettings = {
                showNoExtensionDialog = false
                onNoExtension()
            }
        )
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
            coverUrl = manga.coverImage?.extraLarge ?: manga.coverImage?.large ?: "",
            currentStatus = mangaStatusMap[manga.id],
            currentProgress = mangaProgressMap[manga.id] ?: 0,
            totalChapters = manga.chapters ?: 0,
            onDismiss = { showMangaStatusDialog = false },
            onRemove = {
                viewModel.removeMangaTracking(manga.id)
                showMangaStatusDialog = false
            },
            onUpdate = { status, progress ->
                viewModel.updateMangaStatus(manga.id, status, progress)
                if (progress != null) {
                    viewModel.updateMangaProgress(manga.id, progress.toFloat())
                }
                showMangaStatusDialog = false
            }
        )
    }

    // Stable callbacks to avoid recomposition
    val onAnimeClickStable = remember<(ExploreAnime, AnimeCardBounds?) -> Unit> {
        { anime, bounds ->
            val cardBounds = bounds?.let {
                MainViewModel.CardBounds(anime.id, anime.cover, it.bounds)
            }
            currentCardBounds = cardBounds
            viewModel.clearExploreAnimeCardBounds()
            selectedAnime = anime
            showDialog = true
        }
    }

    val onBookmarkClickStable = remember<(ExploreAnime) -> Unit> {
        { anime ->
            selectedAnime = anime
            showStatusDialog = true
        }
    }

    val onFeaturedAnimeClickStable = remember<(ExploreAnime) -> Unit> {
        { anime ->
            currentCardBounds = null
            selectedAnime = anime
            showDialog = true
        }
    }

    val scrollState = rememberScrollState()
    var isRefreshing by remember { mutableStateOf(false) }

    // Reset pull-to-refresh when loading completes
    LaunchedEffect(isLoading) {
        if (!isLoading) {
            isRefreshing = false
        }
    }
    
    // Refresh data when screen becomes visible
    LaunchedEffect(isVisible, seasonalAnime) {
        if (isVisible && seasonalAnime.isEmpty()) {
            delay(100.milliseconds)
            viewModel.forceRefreshExplore()
        }
    }

    LaunchedEffect(isVisible, isLoadingManga) {
        android.util.Log.d("MangaExplore", "LaunchedEffect: isVisible=$isVisible mangaExploreSections.isEmpty=${mangaExploreSections.isEmpty()} isLoadingManga=$isLoadingManga")
        if (isVisible && mangaExploreSections.isEmpty() && !isLoadingManga) {
            android.util.Log.d("MangaExplore", "Triggering fetchMangaExplore()")
            viewModel.fetchMangaExplore()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                viewModel.forceRefreshExplore()
            },
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(bottom = 80.dp)
            ) {
            if (apiError != null || isOffline) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = if (isOffline) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.errorContainer,
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
                            tint = if (isOffline) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isOffline) "No internet connection" else "AniList is currently unavailable",
                            color = if (isOffline) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            // Featured Carousel with HorizontalPager
            if (filteredFeaturedAnime.isNotEmpty()) {
                FeaturedCarousel(
                    animeList = filteredFeaturedAnime,
                    onStatusClick = { anime ->
                        selectedAnime = anime
                        showStatusDialog = true
                    },
                    onPlayClick = { anime ->
                        selectedAnime = anime
                        val hasDefault = streamMethod == "magnet" && defaultMagnetExt != null || streamMethod == "direct" && defaultExtPkg.isNotEmpty()
                        if (simplifyEpisodeMenu || hasDefault) {
                            showEpisodeSelection = true
                        } else {
                            showEpisodeSelection = false
                            showNoExtensionDialog = true
                        }
                    },
                    onInfoClick = onFeaturedAnimeClickStable,
                    onSearchClick = onSearchClick,
                    animeStatusMap = animeStatusMap,
                    preferEnglishTitles = preferEnglishTitles,
                    isDialogOpen = showDialog || showStatusDialog || showEpisodeSelection || showNoExtensionDialog,
                    autoScrollEnabled = isVisible && !showDialog
                )
            } else if (apiError == null && !isOffline) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            // This Season
            SectionTitle("This Season", filteredSeasonalAnime.size, isOled)
            if (filteredSeasonalAnime.isNotEmpty()) {
                ExploreAnimeHorizontalList(
                    animeList = filteredSeasonalAnime,
                    animeStatusMap = animeStatusMap,
                    showStatusColors = showStatusColors,
                    showAnimeCardButtons = showAnimeCardButtons,
                    preferEnglishTitles = preferEnglishTitles,
                    onAnimeClick = onAnimeClickStable,
                    onBookmarkClick = onBookmarkClickStable,
                    isLoggedIn = isLoggedIn,
                    isOled = isOled,
                    localAnimeStatus = localAnimeStatus,
                    onAddToLocalPlanning = { anime ->
                        viewModel.setLocalAnimeStatus(
                            anime.id,
                            LocalAnimeEntry(
                                id = anime.id,
                                status = "PLANNING",
                                progress = 0,
                                totalEpisodes = anime.episodes,
                                title = anime.title,
                                cover = anime.cover,
                                banner = anime.banner,
                                year = anime.year,
                                averageScore = anime.averageScore
                            )
                        )
                    },
                    onRemoveFromLocalStatus = { anime ->
                        viewModel.setLocalAnimeStatus(anime.id, null)
                    },
                    listIndex = 0,
                    isVisible = isVisible,
                    viewModel = viewModel
                )
            } else if (isLoading) {
                LoadingPlaceholder(isOled)
            }

            // Top Rated Series
            SectionTitle("Top Rated Series", filteredTopSeries.size, isOled)
            if (filteredTopSeries.isNotEmpty()) {
                ExploreAnimeHorizontalList(
                    animeList = filteredTopSeries,
                    animeStatusMap = animeStatusMap,
                    showStatusColors = showStatusColors,
                    showAnimeCardButtons = showAnimeCardButtons,
                    preferEnglishTitles = preferEnglishTitles,
                    onAnimeClick = onAnimeClickStable,
                    onBookmarkClick = onBookmarkClickStable,
                    isLoggedIn = isLoggedIn,
                    isOled = isOled,
                    localAnimeStatus = localAnimeStatus,
                    onAddToLocalPlanning = { anime ->
                        viewModel.setLocalAnimeStatus(
                            anime.id,
                            LocalAnimeEntry(
                                id = anime.id,
                                status = "PLANNING",
                                progress = 0,
                                totalEpisodes = anime.episodes,
                                title = anime.title,
                                cover = anime.cover,
                                banner = anime.banner,
                                year = anime.year,
                                averageScore = anime.averageScore
                            )
                        )
                    },
                    onRemoveFromLocalStatus = { anime ->
                        viewModel.setLocalAnimeStatus(anime.id, null)
                    },
                    listIndex = 1,
                    isVisible = isVisible,
                    viewModel = viewModel
                )
            } else if (isLoading) {
                LoadingPlaceholder(isOled)
            }

            // Top Rated Movies
            SectionTitle("Top Rated Movies", filteredTopMovies.size, isOled)
            if (filteredTopMovies.isNotEmpty()) {
                ExploreAnimeHorizontalList(
                    animeList = filteredTopMovies,
                    animeStatusMap = animeStatusMap,
                    showStatusColors = showStatusColors,
                    showAnimeCardButtons = showAnimeCardButtons,
                    preferEnglishTitles = preferEnglishTitles,
                    onAnimeClick = onAnimeClickStable,
                    onBookmarkClick = onBookmarkClickStable,
                    isLoggedIn = isLoggedIn,
                    isOled = isOled,
                    localAnimeStatus = localAnimeStatus,
                    onAddToLocalPlanning = { anime ->
                        viewModel.setLocalAnimeStatus(
                            anime.id,
                            LocalAnimeEntry(
                                id = anime.id,
                                status = "PLANNING",
                                progress = 0,
                                totalEpisodes = anime.episodes,
                                title = anime.title,
                                cover = anime.cover,
                                banner = anime.banner,
                                year = anime.year,
                                averageScore = anime.averageScore
                            )
                        )
                    },
                    onRemoveFromLocalStatus = { anime ->
                        viewModel.setLocalAnimeStatus(anime.id, null)
                    },
                    listIndex = 2,
                    isVisible = isVisible,
                    viewModel = viewModel
                )
            } else if (isLoading) {
                LoadingPlaceholder(isOled)
            }

            // Genre Sections
            GenreSection(
                title = "Action",
                animeList = filteredActionAnime,
                animeStatusMap = animeStatusMap,
                showStatusColors = showStatusColors,
                showAnimeCardButtons = showAnimeCardButtons,
                isLoading = isLoading,
                isOled = isOled,
                isLoggedIn = isLoggedIn,
                onAnimeClick = onAnimeClickStable,
                onBookmarkClick = onBookmarkClickStable,
                localAnimeStatus = localAnimeStatus,
                onAddToLocalPlanning = { anime ->
                    viewModel.setLocalAnimeStatus(
                        anime.id,
                        LocalAnimeEntry(
                            id = anime.id,
                            status = "PLANNING",
                            progress = 0,
                            totalEpisodes = anime.episodes,
                            title = anime.title,
                            cover = anime.cover,
                            banner = anime.banner,
                            year = anime.year,
                            averageScore = anime.averageScore
                        )
                    )
                },
                onRemoveFromLocalStatus = { anime ->
                    viewModel.setLocalAnimeStatus(anime.id, null)
                },
                preferEnglishTitles = preferEnglishTitles,
                listIndex = 3,
                isVisible = isVisible,
                viewModel = viewModel
            )

            GenreSection(
                title = "Romance",
                animeList = filteredRomanceAnime,
                animeStatusMap = animeStatusMap,
                showStatusColors = showStatusColors,
                showAnimeCardButtons = showAnimeCardButtons,
                isLoading = isLoading,
                isOled = isOled,
                isLoggedIn = isLoggedIn,
                onAnimeClick = onAnimeClickStable,
                onBookmarkClick = onBookmarkClickStable,
                localAnimeStatus = localAnimeStatus,
                onAddToLocalPlanning = { anime ->
                    viewModel.setLocalAnimeStatus(
                        anime.id,
                        LocalAnimeEntry(
                            id = anime.id,
                            status = "PLANNING",
                            progress = 0,
                            totalEpisodes = anime.episodes,
                            title = anime.title,
                            cover = anime.cover,
                            banner = anime.banner,
                            year = anime.year,
                            averageScore = anime.averageScore
                        )
                    )
                },
                onRemoveFromLocalStatus = { anime ->
                    viewModel.setLocalAnimeStatus(anime.id, null)
                },
                preferEnglishTitles = preferEnglishTitles,
                listIndex = 4,
                isVisible = isVisible,
                viewModel = viewModel
            )

            GenreSection(
                title = "Comedy",
                animeList = filteredComedyAnime,
                animeStatusMap = animeStatusMap,
                showStatusColors = showStatusColors,
                showAnimeCardButtons = showAnimeCardButtons,
                isLoading = isLoading,
                isOled = isOled,
                isLoggedIn = isLoggedIn,
                onAnimeClick = onAnimeClickStable,
                onBookmarkClick = onBookmarkClickStable,
                localAnimeStatus = localAnimeStatus,
                onAddToLocalPlanning = { anime ->
                    viewModel.setLocalAnimeStatus(
                        anime.id,
                        LocalAnimeEntry(
                            id = anime.id,
                            status = "PLANNING",
                            progress = 0,
                            totalEpisodes = anime.episodes,
                            title = anime.title,
                            cover = anime.cover,
                            banner = anime.banner,
                            year = anime.year,
                            averageScore = anime.averageScore
                        )
                    )
                },
                onRemoveFromLocalStatus = { anime ->
                    viewModel.setLocalAnimeStatus(anime.id, null)
                },
                preferEnglishTitles = preferEnglishTitles,
                listIndex = 5,
                isVisible = isVisible,
                viewModel = viewModel
            )

            GenreSection(
                title = "Fantasy",
                animeList = filteredFantasyAnime,
                animeStatusMap = animeStatusMap,
                showStatusColors = showStatusColors,
                showAnimeCardButtons = showAnimeCardButtons,
                isLoading = isLoading,
                isOled = isOled,
                isLoggedIn = isLoggedIn,
                onAnimeClick = onAnimeClickStable,
                onBookmarkClick = onBookmarkClickStable,
                localAnimeStatus = localAnimeStatus,
                onAddToLocalPlanning = { anime ->
                    viewModel.setLocalAnimeStatus(
                        anime.id,
                        LocalAnimeEntry(
                            id = anime.id,
                            status = "PLANNING",
                            progress = 0,
                            totalEpisodes = anime.episodes,
                            title = anime.title,
                            cover = anime.cover,
                            banner = anime.banner,
                            year = anime.year,
                            averageScore = anime.averageScore
                        )
                    )
                },
                onRemoveFromLocalStatus = { anime ->
                    viewModel.setLocalAnimeStatus(anime.id, null)
                },
                preferEnglishTitles = preferEnglishTitles,
                listIndex = 6,
                isVisible = isVisible,
                viewModel = viewModel
            )

            GenreSection(
                title = "Sci-Fi",
                animeList = filteredScifiAnime,
                animeStatusMap = animeStatusMap,
                showStatusColors = showStatusColors,
                showAnimeCardButtons = showAnimeCardButtons,
                isLoading = isLoading,
                isOled = isOled,
                isLoggedIn = isLoggedIn,
                onAnimeClick = onAnimeClickStable,
                onBookmarkClick = onBookmarkClickStable,
                localAnimeStatus = localAnimeStatus,
                onAddToLocalPlanning = { anime ->
                    viewModel.setLocalAnimeStatus(
                        anime.id,
                        LocalAnimeEntry(
                            id = anime.id,
                            status = "PLANNING",
                            progress = 0,
                            totalEpisodes = anime.episodes,
                            title = anime.title,
                            cover = anime.cover,
                            banner = anime.banner,
                            year = anime.year,
                            averageScore = anime.averageScore
                        )
                    )
                },
                onRemoveFromLocalStatus = { anime ->
                    viewModel.setLocalAnimeStatus(anime.id, null)
                },
                preferEnglishTitles = preferEnglishTitles,
                listIndex = 7,
                isVisible = isVisible,
                viewModel = viewModel
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ─── Manga Explore Sections ──────────────────────────────
            // No "Manga" header — sections flow directly after anime sections.
            if (mangaExploreSections.isNotEmpty()) {
                val sectionLabelMap = mapOf(
                    "trending" to "Trending Now",
                    "popular" to "Most Popular",
                    "topRated" to "Top Rated",
                    "favourites" to "Most Favourited",
                    "action" to "Action",
                    "romance" to "Romance",
                    "fantasy" to "Fantasy",
                    "seinen" to "Seinen"
                )
                // Explicit render order so newly-added sections always land in the right spot.
                // Trending is rendered as the featured carousel above the rows.
                val mangaSectionOrder = listOf(
                    "trending", "popular", "topRated", "favourites",
                    "action", "romance", "fantasy", "seinen"
                )

                // Featured carousel fed by the trending section (mirrors anime FeaturedCarousel,
                // which is limited to 10 items via perPage: 10)
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
                        autoScrollEnabled = isVisible,
                        isVisible = isVisible,
                        isDialogOpen = showMangaStatusDialog || showMangaNoExtensionDialog
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Remaining section rows in fixed order (trending is rendered as carousel above)
                mangaSectionOrder.forEach { key ->
                    if (key == "trending") return@forEach // already shown in the carousel
                    val list = mangaExploreSections[key].orEmpty()
                    if (list.isNotEmpty()) {
                        val label = sectionLabelMap[key] ?: key.replaceFirstChar { it.uppercase() }
                        SectionTitle(label, list.size, isOled)
                        MangaExploreHorizontalRow(
                            mangaList = list,
                            isOled = isOled,
                            preferEnglishTitles = preferEnglishTitles,
                            onMangaClick = onMangaClick
                        )
                    }
                }
            } else if (isLoadingManga) {
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
                // Fetch completed but returned empty — show retry button with explanatory text
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
                            text = "Couldn't load manga sections",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "AniList may be experiencing issues. Tap retry to try again.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = {
                            viewModel.viewModelScope.launch {
                                viewModel.fetchMangaExplore()
                            }
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
}

@Composable
private fun MangaExploreHorizontalRow(
    mangaList: List<MangaExploreMedia>,
    isOled: Boolean,
    preferEnglishTitles: Boolean,
    onMangaClick: (MangaExploreMedia) -> Unit
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

@Composable
private fun GenreSection(
    title: String,
    animeList: List<ExploreAnime>,
    animeStatusMap: Map<Int, String>,
    showStatusColors: Boolean,
    showAnimeCardButtons: Boolean,
    isLoading: Boolean,
    isOled: Boolean,
    isLoggedIn: Boolean,
    onAnimeClick: (ExploreAnime, AnimeCardBounds?) -> Unit,
    onBookmarkClick: (ExploreAnime) -> Unit,
    localAnimeStatus: Map<Int, LocalAnimeEntry> = emptyMap(),
    onAddToLocalPlanning: (ExploreAnime) -> Unit = {},
    onRemoveFromLocalStatus: (ExploreAnime) -> Unit = {},
    preferEnglishTitles: Boolean = true,
    listIndex: Int = 0,
    isVisible: Boolean = true,
    viewModel: MainViewModel
) {
    if (animeList.isEmpty() && !isLoading) return

    Column {
        SectionTitle(title, animeList.size, isOled)
        if (animeList.isNotEmpty()) {
            ExploreAnimeHorizontalList(
                animeList = animeList,
                animeStatusMap = animeStatusMap,
                showStatusColors = showStatusColors,
                showAnimeCardButtons = showAnimeCardButtons,
                preferEnglishTitles = preferEnglishTitles,
                onAnimeClick = onAnimeClick,
                onBookmarkClick = onBookmarkClick,
                isLoggedIn = isLoggedIn,
                isOled = isOled,
                localAnimeStatus = localAnimeStatus,
                onAddToLocalPlanning = onAddToLocalPlanning,
                onRemoveFromLocalStatus = onRemoveFromLocalStatus,
                listIndex = listIndex,
                isVisible = isVisible,
                viewModel = viewModel
            )
        } else if (isLoading) {
            LoadingPlaceholder(isOled)
        }
    }
}

/**
 * Featured carousel for manga, mirroring the anime [FeaturedCarousel] visual pattern.
 * Renders a ~400dp tall pager with title, score, format and a "Read Now" button.
 * Uses the portrait cover image (coverImage.extraLarge / large), matching oni's explore screen.
 */
@Composable
private fun MangaFeaturedCarousel(
    mangaList: List<MangaExploreMedia>,
    onStatusClick: (MangaExploreMedia) -> Unit,
    onReadClick: (MangaExploreMedia) -> Unit,
    onInfoClick: (MangaExploreMedia) -> Unit,
    mangaStatusMap: Map<Int, String> = emptyMap(),
    preferEnglishTitles: Boolean = true,
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
                    val displayTitle = if (preferEnglishTitles && !currentManga.title.english.isNullOrBlank())
                        currentManga.title.english!!
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
                        (currentManga.seasonYear ?: currentManga.startDate?.year)?.let { year ->
                            Text(text = year.toString(), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium)
                            Text(text = " • ", color = Color.White.copy(alpha = 0.4f), style = MaterialTheme.typography.bodyMedium)
                        }
                        val formatText = when (currentManga.format?.uppercase()) {
                            "MANGA" -> "Manga"
                            "NOVEL" -> "Novel"
                            "ONE_SHOT" -> "One-Shot"
                            else -> "Manga"
                        }
                        Text(text = formatText, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium)
                        currentManga.averageScore?.let { score ->
                            Text(text = " • ", color = Color.White.copy(alpha = 0.4f), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "★ ${"%.1f".format(score / 10.0)}",
                                color = Color(0xFFFFD700),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val currentStatus = mangaStatusMap[currentManga.id]
                        val isSaved = currentStatus != null
                        val statusColor = when (currentStatus) {
                            "COMPLETED" -> StatusCompleted
                            "CURRENT" -> StatusCurrent
                            "PLANNING" -> StatusPlanning
                            "PAUSED" -> StatusPaused
                            "DROPPED" -> StatusDropped
                            else -> Color.White
                        }

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
                                        if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
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


