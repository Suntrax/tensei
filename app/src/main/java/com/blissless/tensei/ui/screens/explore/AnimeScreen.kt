package com.blissless.tensei.ui.screens.explore

import com.blissless.tensei.data.models.isAdultContent
import com.blissless.tensei.data.models.MangaMedia
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blissless.tensei.MainViewModel
import com.blissless.tensei.data.models.AnimeMedia
import com.blissless.tensei.data.models.ExploreAnime
import com.blissless.tensei.data.models.LocalAnimeEntry
import com.blissless.tensei.data.models.toDetailedAnimeData
import com.blissless.tensei.dialogs.HomeAnimeStatusDialog
import com.blissless.tensei.ui.components.AnimeCardBounds
import com.blissless.tensei.ui.components.ExploreAnimeHorizontalList
import com.blissless.tensei.ui.components.LoadingPlaceholder
import com.blissless.tensei.ui.components.LoadingSkeleton
import com.blissless.tensei.ui.screens.episode.EpisodeSelectionDialog
import com.blissless.tensei.ui.screens.episode.RichEpisodeScreen
import com.blissless.tensei.ui.components.SectionTitle
import com.blissless.tensei.ui.screens.details.DetailedAnimeScreen
import com.blissless.tensei.ui.screens.details.NoDefaultExtensionDialog
import com.blissless.tensei.ui.theme.StatusCompleted
import com.blissless.tensei.ui.theme.StatusCurrent
import com.blissless.tensei.ui.theme.StatusDropped
import com.blissless.tensei.ui.theme.StatusPaused
import com.blissless.tensei.ui.theme.StatusPlanning
import com.blissless.tensei.ui.theme.StatusColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope
import kotlin.time.Duration.Companion.milliseconds
import com.blissless.tensei.util.toast
import com.blissless.tensei.util.longToast
import java.util.Locale
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
fun AnimeScreen(
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
    onAnimeDetailMangaClick: (MangaMedia) -> Unit = {}
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
    val appIcon by viewModel.appIcon.collectAsState()
    val localAnimeStatus by viewModel.localAnimeStatus.collectAsState()
    
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

    // True once the AniList API has returned anything at all (anime batch or manga sections).
    val hasAnyExploreData = filteredFeaturedAnime.isNotEmpty() || filteredSeasonalAnime.isNotEmpty() ||
        filteredTopSeries.isNotEmpty() || filteredTopMovies.isNotEmpty() ||
        filteredActionAnime.isNotEmpty() || filteredRomanceAnime.isNotEmpty() ||
        filteredComedyAnime.isNotEmpty() || filteredFantasyAnime.isNotEmpty() ||
        filteredScifiAnime.isNotEmpty()

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

    // Derive currentStatus from lists dynamically to ensure immediate UI updates

    var selectedAnime by remember { mutableStateOf<ExploreAnime?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    var showEpisodeSelection by remember { mutableStateOf(false) }
    var showStatusDialog by remember { mutableStateOf(false) }
    var showNoExtensionDialog by remember { mutableStateOf(false) }
    
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

    // Category list screen state (mirrors home's status list screen overlay)
    var showCategoryList by remember { mutableStateOf(false) }
    var categoryListTitle by remember { mutableStateOf("") }
    var categoryListIcon by remember { mutableStateOf(Icons.Default.Star) }
    var categoryListAnime by remember { mutableStateOf<List<ExploreAnime>>(emptyList()) }

    // Hide the bottom navbar while the category list overlay is open (mirror home's status list).
    LaunchedEffect(showCategoryList) {
        viewModel.setHideNavbar(showCategoryList)
    }

    fun openAnimeCategory(title: String, icon: ImageVector, list: List<ExploreAnime>) {
        categoryListTitle = title
        categoryListIcon = icon
        categoryListAnime = list
        showCategoryList = true
    }

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
                val mangaFormats = listOf("MANGA", "NOVEL", "ONE_SHOT", "DOUJIN", "MANHWA", "MANHUA")
                if (relation.format == null || relation.format in mangaFormats) {
                    onAnimeDetailMangaClick(
                        MangaMedia(
                            id = relation.id,
                            title = relation.title,
                            cover = relation.cover,
                            totalChapters = 0,
                            averageScore = relation.averageScore,
                            format = relation.format
                        )
                    )
                } else {
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
                }
            },
            onRecommendationClick = { rec ->
                val mangaFormats = listOf("MANGA", "NOVEL", "ONE_SHOT", "DOUJIN", "MANHWA", "MANHUA")
                if (rec.format == null || rec.format in mangaFormats) {
                    onAnimeDetailMangaClick(
                        MangaMedia(
                            id = rec.id,
                            title = rec.title,
                            cover = rec.cover,
                            totalChapters = rec.episodes ?: 0,
                            averageScore = rec.averageScore,
                            format = rec.format
                        )
                    )
                } else {
                    try {
                        scope.launch {
                            try {
                                delay(100.milliseconds)
                                viewModel.clearExploreAnimeCardBounds()
                                currentCardBounds = null
                                val detailedData = viewModel.fetchDetailedAnimeData(rec.id)
                                if (detailedData != null) {
                                    selectedAnime = ExploreAnime(
                                        id = rec.id,
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
                                    context.toast("Anime not found - ID: ${rec.id}")
                                }
                            } catch (e: Exception) {
                                context.toast("Error: ${e.message}")
                            }
                        }
                    } catch (e: Exception) {
                        context.toast("Error: ${e.message}")
                    }
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
            preferEnglishTitles = preferEnglishTitles,
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

    val onPlayClickStable = remember<(ExploreAnime) -> Unit> {
        { anime ->
            selectedAnime = anime
            val hasDefault = streamMethod == "magnet" && defaultMagnetExt != null || streamMethod == "direct" && defaultExtPkg.isNotEmpty()
            if (simplifyEpisodeMenu || hasDefault) {
                showEpisodeSelection = true
            } else {
                showEpisodeSelection = false
                showNoExtensionDialog = true
            }
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

    // Debounce loading UI so quick failures (e.g. API down) don't flash placeholders
    var showLoadingUi by remember { mutableStateOf(false) }
    LaunchedEffect(isLoading) {
        if (isLoading) {
            delay(500)
            if (isLoading) showLoadingUi = true
        } else {
            showLoadingUi = false
        }
    }
    
    // Refresh data when screen becomes visible
    LaunchedEffect(isVisible, seasonalAnime) {
        if (isVisible && seasonalAnime.isEmpty()) {
            delay(100.milliseconds)
            viewModel.forceRefreshExplore()
        }
    }

    // Whole-screen skeleton: show it immediately on first open (before any fetch has
    // started) and keep it until the API returns anything or the fetch cycle concludes.
    var exploreFetchesStarted by remember { mutableStateOf(false) }
    LaunchedEffect(isLoading) {
        if (isLoading) exploreFetchesStarted = true
    }
    val showExploreSkeleton =
        !hasAnyExploreData && (!exploreFetchesStarted || isLoading)

    Box(modifier = Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                if (viewModel.tryManualRefresh("explore")) {
                    isRefreshing = true
                    viewModel.forceRefreshExplore()
                }
            },
            modifier = Modifier.fillMaxSize()
        ) {
            if (showExploreSkeleton) {
                // Loading skeleton while the AniList API is still fetching results.
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
                    appIcon = appIcon,
                    isDialogOpen = showDialog || showStatusDialog || showEpisodeSelection || showNoExtensionDialog,
                    autoScrollEnabled = isVisible && !showDialog
                )
            } else if (apiError == null && !isOffline && showLoadingUi) {
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
            if (filteredSeasonalAnime.isNotEmpty() || showLoadingUi) {
                SectionTitle("This Season", filteredSeasonalAnime.size, isOled, onClick = {
                    openAnimeCategory("This Season", Icons.Default.DateRange, filteredSeasonalAnime)
                })
            }
            if (filteredSeasonalAnime.isNotEmpty()) {
                ExploreAnimeHorizontalList(
                    animeList = filteredSeasonalAnime,
                    animeStatusMap = animeStatusMap,
                    showStatusColors = showStatusColors,
                    showAnimeCardButtons = showAnimeCardButtons,
                    preferEnglishTitles = preferEnglishTitles,
                    onAnimeClick = onAnimeClickStable,
                    onBookmarkClick = onBookmarkClickStable,
                    onPlayClick = onPlayClickStable,
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
            } else if (showLoadingUi) {
                LoadingPlaceholder(isOled)
            }

            // Top Rated Series
            if (filteredTopSeries.isNotEmpty() || showLoadingUi) {
                SectionTitle("Top Rated Series", filteredTopSeries.size, isOled, onClick = {
                    openAnimeCategory("Top Rated Series", Icons.Default.Star, filteredTopSeries)
                })
            }
            if (filteredTopSeries.isNotEmpty()) {
                ExploreAnimeHorizontalList(
                    animeList = filteredTopSeries,
                    animeStatusMap = animeStatusMap,
                    showStatusColors = showStatusColors,
                    showAnimeCardButtons = showAnimeCardButtons,
                    preferEnglishTitles = preferEnglishTitles,
                    onAnimeClick = onAnimeClickStable,
                    onBookmarkClick = onBookmarkClickStable,
                    onPlayClick = onPlayClickStable,
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
            } else if (showLoadingUi) {
                LoadingPlaceholder(isOled)
            }

            // Top Rated Movies
            if (filteredTopMovies.isNotEmpty() || showLoadingUi) {
                SectionTitle("Top Rated Movies", filteredTopMovies.size, isOled, onClick = {
                    openAnimeCategory("Top Rated Movies", Icons.Default.PlayCircle, filteredTopMovies)
                })
            }
            if (filteredTopMovies.isNotEmpty()) {
                ExploreAnimeHorizontalList(
                    animeList = filteredTopMovies,
                    animeStatusMap = animeStatusMap,
                    showStatusColors = showStatusColors,
                    showAnimeCardButtons = showAnimeCardButtons,
                    preferEnglishTitles = preferEnglishTitles,
                    onAnimeClick = onAnimeClickStable,
                    onBookmarkClick = onBookmarkClickStable,
                    onPlayClick = onPlayClickStable,
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
            } else if (showLoadingUi) {
                LoadingPlaceholder(isOled)
            }

            // Genre Sections (alphabetical order)
            val genreSections = listOf(
                Triple("Action", filteredActionAnime, Icons.Default.Whatshot),
                Triple("Comedy", filteredComedyAnime, Icons.Default.SentimentSatisfied),
                Triple("Fantasy", filteredFantasyAnime, Icons.Default.AutoAwesome),
                Triple("Romance", filteredRomanceAnime, Icons.Default.Favorite),
                Triple("Sci-Fi", filteredScifiAnime, Icons.Default.Explore)
            )
            genreSections.forEachIndexed { index, (genreTitle, genreList, genreIcon) ->
                GenreSection(
                    title = genreTitle,
                    animeList = genreList,
                    animeStatusMap = animeStatusMap,
                    showStatusColors = showStatusColors,
                    showAnimeCardButtons = showAnimeCardButtons,
                    isLoading = showLoadingUi,
                    isOled = isOled,
                    isLoggedIn = isLoggedIn,
                    onAnimeClick = onAnimeClickStable,
                    onBookmarkClick = onBookmarkClickStable,
                    onPlayClick = onPlayClickStable,
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
                    onSectionClick = {
                        openAnimeCategory(genreTitle, genreIcon, genreList)
                    },
                    preferEnglishTitles = preferEnglishTitles,
                    listIndex = 3 + index,
                    isVisible = isVisible,
                    viewModel = viewModel
                )
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
                animeList = categoryListAnime,
                animeStatusMap = animeStatusMap,
                showStatusColors = showStatusColors,
                preferEnglishTitles = preferEnglishTitles,
                onAnimeClick = onAnimeClickStable,
                onBookmarkClick = onBookmarkClickStable,
                onBackClick = { showCategoryList = false },
                onDismiss = { showCategoryList = false }
            )
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
    onPlayClick: (ExploreAnime) -> Unit = { _ -> },
    localAnimeStatus: Map<Int, LocalAnimeEntry> = emptyMap(),
    onAddToLocalPlanning: (ExploreAnime) -> Unit = {},
    onRemoveFromLocalStatus: (ExploreAnime) -> Unit = {},
    preferEnglishTitles: Boolean = true,
    listIndex: Int = 0,
    isVisible: Boolean = true,
    onSectionClick: (() -> Unit)? = null,
    viewModel: MainViewModel
) {
    if (animeList.isEmpty() && !isLoading) return

    Column {
        SectionTitle(title, animeList.size, isOled, onClick = onSectionClick)
        if (animeList.isNotEmpty()) {
            ExploreAnimeHorizontalList(
                animeList = animeList,
                animeStatusMap = animeStatusMap,
                showStatusColors = showStatusColors,
                showAnimeCardButtons = showAnimeCardButtons,
                preferEnglishTitles = preferEnglishTitles,
                onAnimeClick = onAnimeClick,
                onBookmarkClick = onBookmarkClick,
                onPlayClick = onPlayClick,
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
