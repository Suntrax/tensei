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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.blissless.tensei.ui.screens.episode.EpisodeSelectionDialog
import com.blissless.tensei.ui.screens.episode.RichEpisodeScreen
import com.blissless.tensei.ui.components.SectionTitle
import com.blissless.tensei.ui.screens.details.DetailedAnimeScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

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
    onSearchClick: () -> Unit = {},
    onNoExtension: () -> Unit = {}
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
                                Toast.makeText(
                                    context,
                                    "Anime not found - ID: ${relation.id}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT)
                                .show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            },
            onCharacterClick = onCharacterClick,
            onStaffClick = onStaffClick,
            onViewAllCast = { onViewAllCast(selectedAnime!!.id, selectedAnime!!.title) },
            onViewAllStaff = { onViewAllStaff(selectedAnime!!.id, selectedAnime!!.title) },
            onViewAllRelations = { animeId, title ->
                onViewAllRelations(animeId, title)
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
            // Error/Offline Banner
            if (apiError != null || isOffline) {
                Surface(
                    modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars),
                    color = if (isOffline) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.errorContainer,
                    tonalElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (isOffline) Icons.Default.SignalWifiOff else Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = if (isOffline) Color.White else MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isOffline) "No internet connection" else "AniList is currently unavailable",
                            color = if (isOffline) Color.White else MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
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
                        showEpisodeSelection = true
                    },
                    onInfoClick = onFeaturedAnimeClickStable,
                    onSearchClick = onSearchClick,
                    animeStatusMap = animeStatusMap,
                    preferEnglishTitles = preferEnglishTitles,
                    isDialogOpen = showDialog || showStatusDialog || showEpisodeSelection,
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


