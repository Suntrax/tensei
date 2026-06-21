package com.blissless.tensei.ui.screens.search

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.blissless.tensei.MainViewModel
import com.blissless.tensei.data.models.AnimeMedia
import com.blissless.tensei.data.models.ExploreAnime
import com.blissless.tensei.data.models.LocalAnimeEntry
import com.blissless.tensei.data.models.MediaTag
import com.blissless.tensei.data.models.isAdultContent
import com.blissless.tensei.data.models.toDetailedAnimeData
import com.blissless.tensei.ui.screens.details.DetailedAnimeScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import java.util.Locale

val ALL_GENRES = listOf(
    "Action", "Adventure", "Comedy", "Drama", "Fantasy", "Horror",
    "Mystery", "Romance", "Sci-Fi", "Slice of Life", "Sports",
    "Thriller", "Supernatural", "Music", "Psychological",
    "Military", "Mecha", "School", "Seinen", "Shoujo", "Shounen"
)

val ALL_FORMATS = listOf("TV", "TV_SHORT", "MOVIE", "OVA", "ONA", "SPECIAL", "MUSIC")
val ALL_STATUSES = listOf("FINISHED", "RELEASING", "NOT_YET_RELEASED", "CANCELLED", "HIATUS")
val ALL_SEASONS = listOf("WINTER", "SPRING", "SUMMER", "FALL")
val ALL_SORTS = listOf(
    "POPULARITY_DESC" to "Popularity",
    "SCORE_DESC" to "Score",
    "TRENDING_DESC" to "Trending",
    "UPDATED_AT_DESC" to "Recently Updated",
    "START_DATE_DESC" to "Release Date",
    "FAVOURITES_DESC" to "Favorites",
    "EPISODES_DESC" to "Episode Count",
    "TITLE_ROMAJI" to "Title"
)

data class SearchFilters(
    val query: String = "",
    val genres: Set<String> = emptySet(),
    val tags: List<String> = emptyList(),
    val format: String? = null,
    val status: String? = null,
    val season: String? = null,
    val seasonYear: String = "",
    val sort: String = "POPULARITY_DESC"
)

@OptIn(UnstableApi::class)
@Composable
fun SearchScreen(
    viewModel: MainViewModel,
    isOled: Boolean,
    isLoggedIn: Boolean,
    preferEnglishTitles: Boolean,
    hideAdultContent: Boolean,
    currentlyWatching: List<AnimeMedia>,
    planningToWatch: List<AnimeMedia>,
    completed: List<AnimeMedia>,
    onHold: List<AnimeMedia>,
    dropped: List<AnimeMedia>,
    localAnimeStatus: Map<Int, LocalAnimeEntry>,
    favoriteIds: Set<Int>,
    onClose: () -> Unit,
    onPlayEpisode: (AnimeMedia, Int, String?) -> Unit,
    onCharacterClick: (Int) -> Unit = {},
    onStaffClick: (Int) -> Unit = {},
    onViewAllCast: (Int, String) -> Unit = { _, _ -> },
    onViewAllStaff: (Int, String) -> Unit = { _, _ -> },
    onViewAllRelations: (Int, String) -> Unit = { _, _ -> },
    onNoExtension: () -> Unit = {}
) {
    var filters by remember { mutableStateOf(SearchFilters()) }
    var results by remember { mutableStateOf<List<ExploreAnime>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var showFormatDropdown by remember { mutableStateOf(false) }
    var showStatusDropdown by remember { mutableStateOf(false) }
    var showSeasonDropdown by remember { mutableStateOf(false) }
    var showSortDropdown by remember { mutableStateOf(false) }
    var currentPage by remember { mutableIntStateOf(1) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(true) }

    var selectedAnime by remember { mutableStateOf<ExploreAnime?>(null) }
    var firstAnime by remember { mutableStateOf<ExploreAnime?>(null) }
    var showDetailDialog by remember { mutableStateOf(false) }

    var allTags by remember { mutableStateOf<List<MediaTag>>(emptyList()) }
    var tagsLoading by remember { mutableStateOf(false) }
    var showGenreSheet by remember { mutableStateOf(false) }
    var showTagSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        tagsLoading = true
        allTags = viewModel.getAllTags()
        tagsLoading = false
    }

    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()

    val savedAnimeMap = remember(currentlyWatching, planningToWatch, completed, onHold, dropped, localAnimeStatus) {
        val map = mutableMapOf<Int, String>()
        currentlyWatching.forEach { map[it.id] = "CURRENT" }
        planningToWatch.forEach { map[it.id] = "PLANNING" }
        completed.forEach { map[it.id] = "COMPLETED" }
        onHold.forEach { map[it.id] = "PAUSED" }
        dropped.forEach { map[it.id] = "DROPPED" }
        localAnimeStatus.forEach { (id, entry) -> if (!map.containsKey(id)) map[id] = entry.status }
        map
    }

    val savedAnimeProgressMap = remember(currentlyWatching, planningToWatch, completed, onHold, dropped, localAnimeStatus) {
        val map = mutableMapOf<Int, Int>()
        currentlyWatching.forEach { if (it.progress > 0) map[it.id] = it.progress }
        planningToWatch.forEach { if (it.progress > 0) map[it.id] = it.progress }
        completed.forEach { if (it.progress > 0) map[it.id] = it.progress }
        onHold.forEach { if (it.progress > 0) map[it.id] = it.progress }
        dropped.forEach { if (it.progress > 0) map[it.id] = it.progress }
        localAnimeStatus.forEach { (id, entry) -> if (entry.progress > 0 && !map.containsKey(id)) map[id] = entry.progress }
        map
    }

    var listVersion by remember { mutableIntStateOf(0) }
    LaunchedEffect(currentlyWatching, planningToWatch, completed, onHold, dropped, localAnimeStatus) { listVersion++ }

    BackHandler { onClose() }

    fun performSearch(page: Int = 1) {
        scope.launch {
            if (page == 1) {
                isSearching = true
                hasSearched = true
            } else {
                isLoadingMore = true
            }
            val seasonYearVal = filters.seasonYear.toIntOrNull()
            val genreList = filters.genres.toList().ifEmpty { null }
            val tagList = filters.tags.toList().ifEmpty { null }
            val newResults = viewModel.searchAnimeAdvanced(
                search = filters.query.ifBlank { null },
                genres = genreList,
                tags = tagList,
                format = filters.format,
                status = filters.status,
                season = filters.season,
                seasonYear = seasonYearVal,
                sort = filters.sort,
                isAdult = null,
                page = page,
                perPage = 30
            )
            if (page == 1) {
                results = newResults
                hasMore = newResults.size >= 30
            } else {
                results = results + newResults
                hasMore = newResults.size >= 30
            }
            currentPage = page
            isSearching = false
            isLoadingMore = false
        }
    }

    var autoSearchSkippedInitial by remember { mutableStateOf(false) }
    LaunchedEffect(
        filters.genres, filters.tags,
        filters.format, filters.status, filters.season, filters.seasonYear,
        filters.sort
    ) {
        if (autoSearchSkippedInitial) {
            performSearch()
        } else {
            autoSearchSkippedInitial = true
        }
    }

    LaunchedEffect(filters.query) {
        if (filters.query.isNotBlank()) {
            delay(400.milliseconds)
            performSearch()
        }
    }

    val activeFilterCount = listOfNotNull(
        filters.genres.takeIf { it.isNotEmpty() }?.let { 1 },
        filters.tags.takeIf { it.isNotEmpty() }?.let { 1 },
        filters.format,
        filters.status,
        filters.season,
        filters.seasonYear.takeIf { it.isNotBlank() }?.let { 1 },
        filters.sort.takeIf { it != "POPULARITY_DESC" }?.let { 1 }
    ).size

    fun clearAllFilters() {
        filters = SearchFilters(query = filters.query)
        results = emptyList()
        hasSearched = false
    }

    LaunchedEffect(Unit) {
        delay(200.milliseconds)
        focusRequester.requestFocus()
    }

    val filteredResults = remember(results, hideAdultContent) {
        if (hideAdultContent) results.filter { !isAdultContent(it.isAdult, it.genres) } else results
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.96f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { keyboardController?.hide(); onClose() }) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(28.dp))
                }
                Text("Search", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.titleLarge)
            }

            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = if (isOled) Color(0xFF1A1A1A) else Color(0xFF2A2A2A))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    BasicTextField(
                        value = filters.query,
                        onValueChange = { filters = filters.copy(query = it) },
                        modifier = Modifier.weight(1f).focusRequester(focusRequester),
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide(); performSearch() }),
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (filters.query.isEmpty()) Text("Search anime...", color = Color.White.copy(alpha = 0.35f), fontSize = 16.sp)
                                innerTextField()
                            }
                        }
                    )
                    IconButton(
                        onClick = { filters = filters.copy(query = ""); results = emptyList(); hasSearched = false },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.White.copy(alpha = if (filters.query.isNotEmpty()) 0.5f else 0f), modifier = Modifier.size(18.dp))
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showFilters = !showFilters }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.FilterList, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Filters", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.titleSmall)
                    if (activeFilterCount > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primary) {
                            Text("$activeFilterCount", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        if (showFilters) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                        contentDescription = "Toggle filters",
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Row {
                    if (activeFilterCount > 0) {
                        TextButton(onClick = { clearAllFilters() }, modifier = Modifier.height(32.dp)) {
                            Text("Reset", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Button(
                        onClick = { keyboardController?.hide(); performSearch() },
                        modifier = Modifier.height(34.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        enabled = filters.query.isNotBlank() || activeFilterCount > 0
                    ) {
                        Text("Search", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            AnimatedVisibility(visible = showFilters) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                ) {
                    Spacer(modifier = Modifier.height(4.dp))

                    FilterRow(
                        label = "Genres",
                        count = filters.genres.size,
                        onClick = { showGenreSheet = true }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    FilterRow(
                        label = "Tags",
                        count = filters.tags.size,
                        onClick = { showTagSheet = true }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DropdownFilter("Format", filters.format, ALL_FORMATS, showFormatDropdown, { showFormatDropdown = !showFormatDropdown; showStatusDropdown = false; showSeasonDropdown = false; showSortDropdown = false }, { showFormatDropdown = false }, { filters = filters.copy(format = it) })
                        DropdownFilter("Status", filters.status, ALL_STATUSES, showStatusDropdown, { showStatusDropdown = !showStatusDropdown; showFormatDropdown = false; showSeasonDropdown = false; showSortDropdown = false }, { showStatusDropdown = false }, { filters = filters.copy(status = it) })
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DropdownFilter("Season", filters.season, ALL_SEASONS, showSeasonDropdown, { showSeasonDropdown = !showSeasonDropdown; showFormatDropdown = false; showStatusDropdown = false; showSortDropdown = false }, { showSeasonDropdown = false }, { filters = filters.copy(season = it) })
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Season Year", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
                            BasicTextField(
                                value = filters.seasonYear,
                                onValueChange = { if (it.all { c -> c.isDigit() } && it.length <= 4) filters = filters.copy(seasonYear = it) },
                                modifier = Modifier.fillMaxWidth().height(34.dp).background(Color(0xFF2A2A2A), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp),
                                singleLine = true,
                                textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                decorationBox = { innerTextField ->
                                    Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.fillMaxSize()) {
                                        if (filters.seasonYear.isEmpty()) Text("e.g. 2024", color = Color.White.copy(alpha = 0.3f), fontSize = 13.sp)
                                        innerTextField()
                                    }
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        DropdownFilter("Sort", ALL_SORTS.find { it.first == filters.sort }?.second ?: "Popularity", ALL_SORTS.map { it.second }, showSortDropdown, { showSortDropdown = !showSortDropdown; showFormatDropdown = false; showStatusDropdown = false; showSeasonDropdown = false }, { showSortDropdown = false }) { selectedLabel ->
                            val pair = ALL_SORTS.find { it.second == selectedLabel } ?: ALL_SORTS.first()
                            filters = filters.copy(sort = pair.first)
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            if (isSearching) {
                Box(modifier = Modifier.fillMaxSize().padding(top = 128.dp), contentAlignment = Alignment.TopCenter) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (hasSearched && filteredResults.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 128.dp)) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No results found", color = Color.White.copy(alpha = 0.4f), style = MaterialTheme.typography.titleMedium)
                        Text("Try adjusting your filters", color = Color.White.copy(alpha = 0.3f), style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else if (!hasSearched) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 128.dp)) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.15f), modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Discover Anime", color = Color.White.copy(alpha = 0.35f), style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("Search by name or use filters above", color = Color.White.copy(alpha = 0.25f), style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    state = gridState,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredResults, key = { it.id }) { anime ->
                        SearchResultCard(
                            anime = anime,
                            preferEnglishTitles = preferEnglishTitles,
                            onClick = {
                                keyboardController?.hide()
                                selectedAnime = anime
                                firstAnime = anime
                                showDetailDialog = true
                            }
                        )
                    }
                    if (hasMore) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                if (isLoadingMore) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                } else {
                                    TextButton(onClick = { performSearch(currentPage + 1) }) {
                                        Text("Load More", color = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDetailDialog && selectedAnime != null) {
        val currentStatus by remember(listVersion, selectedAnime!!.id) { derivedStateOf { savedAnimeMap[selectedAnime!!.id] } }
        val currentProgress by remember(listVersion, selectedAnime!!.id) { derivedStateOf { savedAnimeProgressMap[selectedAnime!!.id] } }
        val isAnimeFavorite by remember(listVersion, favoriteIds, selectedAnime!!.id) { derivedStateOf { favoriteIds.contains(selectedAnime!!.id) } }

        DetailedAnimeScreen(
            anime = selectedAnime!!.toDetailedAnimeData(),
            viewModel = viewModel,
            isOled = isOled,
            isLoggedIn = isLoggedIn,
            currentStatus = currentStatus,
            currentProgress = currentProgress,
            isFavorite = isAnimeFavorite,
            onDismiss = {
                if (firstAnime != null && selectedAnime!!.id != firstAnime!!.id) {
                    scope.launch {
                        val detailedData = viewModel.fetchDetailedAnimeData(firstAnime!!.id)
                        if (detailedData != null) {
                            selectedAnime = ExploreAnime(
                                id = detailedData.id, title = detailedData.title,
                                titleEnglish = detailedData.titleEnglish, cover = detailedData.cover,
                                banner = detailedData.banner, episodes = detailedData.episodes,
                                latestEpisode = detailedData.latestEpisode, averageScore = detailedData.averageScore,
                                genres = detailedData.genres, year = detailedData.year, format = detailedData.format
                            )
                        }
                    }
                } else { showDetailDialog = false; firstAnime = null }
            },
            onPlayEpisode = { episode, _ ->
                onPlayEpisode(AnimeMedia(id = selectedAnime!!.id, title = selectedAnime!!.title, cover = selectedAnime!!.cover, banner = selectedAnime!!.banner, progress = 0, totalEpisodes = selectedAnime!!.episodes, latestEpisode = selectedAnime!!.latestEpisode, status = "", averageScore = selectedAnime!!.averageScore, genres = selectedAnime!!.genres, listStatus = "", listEntryId = 0), episode, null)
                showDetailDialog = false
            },
            onUpdateStatus = { status -> if (status != null) viewModel.addExploreAnimeToList(selectedAnime!!, status) },
            onRemove = { viewModel.removeAnimeFromList(selectedAnime!!.id); showDetailDialog = false },
            onRelationClick = { relation ->
                scope.launch {
                    val detailedData = viewModel.fetchDetailedAnimeData(relation.id)
                    if (detailedData != null) {
                        selectedAnime = ExploreAnime(id = relation.id, title = detailedData.title, titleEnglish = detailedData.titleEnglish, cover = detailedData.cover, banner = detailedData.banner, episodes = detailedData.episodes, latestEpisode = detailedData.latestEpisode, averageScore = detailedData.averageScore, genres = detailedData.genres, year = detailedData.year, format = detailedData.format)
                    } else Toast.makeText(context, "Anime not found", Toast.LENGTH_SHORT).show()
                }
            },
            onCharacterClick = onCharacterClick, onStaffClick = onStaffClick,
            onViewAllCast = { onViewAllCast(selectedAnime!!.id, selectedAnime!!.title) },
            onViewAllStaff = { onViewAllStaff(selectedAnime!!.id, selectedAnime!!.title) },
            onViewAllRelations = { animeId, title -> onViewAllRelations(animeId, title) },
            onNoExtension = {
                showDetailDialog = false
                onNoExtension()
            }
        )
    }

    val visibleTags = remember(allTags, hideAdultContent) {
        allTags.filter { !hideAdultContent || !it.isAdult }.sortedBy { it.name }
    }

    if (showGenreSheet) {
        MultiSelectSheet(
            options = ALL_GENRES,
            selected = filters.genres,
            onToggle = { genre ->
                filters = if (genre in filters.genres) filters.copy(genres = filters.genres - genre)
                else filters.copy(genres = filters.genres + genre)
            },
            onDismiss = { showGenreSheet = false }
        )
    }

    if (showTagSheet) {
        TagSelectSheet(
            allTags = visibleTags,
            selected = filters.tags.toSet(),
            onToggle = { tagName ->
                filters = if (tagName in filters.tags) filters.copy(tags = filters.tags - tagName)
                else filters.copy(tags = filters.tags + tagName)
            },
            onDismiss = { showTagSheet = false }
        )
    }
}

@Composable
private fun SearchResultCard(
    anime: ExploreAnime,
    preferEnglishTitles: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val displayTitle = if (preferEnglishTitles && !anime.titleEnglish.isNullOrEmpty()) anime.titleEnglish else anime.title
    val displayScore = anime.averageScore?.let { it / 10.0 }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column {
            Box {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(anime.cover).memoryCacheKey(anime.cover).diskCacheKey(anime.cover).crossfade(false).build(),
                    contentDescription = anime.title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().aspectRatio(2f/3f).clip(RoundedCornerShape(12.dp))
                )
                if (displayScore != null) {
                    Row(
                        modifier = Modifier.align(Alignment.BottomStart).padding(6.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(String.format(Locale.getDefault(), "%.1f", displayScore), color = Color(0xFFFFD700), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(displayTitle, color = Color.White, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
            Row(
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                anime.format?.let { fmt ->
                    Text(fmt, color = Color.White.copy(alpha = 0.4f), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    if (anime.year != null || anime.episodes > 0) Text(" • ", color = Color.White.copy(alpha = 0.3f), style = MaterialTheme.typography.labelSmall)
                }
                anime.year?.let {
                    Text("$it", color = Color.White.copy(alpha = 0.4f), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
                if (anime.episodes > 0 && anime.format?.uppercase() != "MOVIE") {
                    if (anime.year != null) Text(" • ", color = Color.White.copy(alpha = 0.3f), style = MaterialTheme.typography.labelSmall)
                    Text("${anime.episodes} eps", color = Color.White.copy(alpha = 0.4f), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun FilterRow(
    label: String,
    count: Int,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF2A2A2A),
        modifier = Modifier.fillMaxWidth().height(42.dp).clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (count > 0) {
                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primary) {
                    Text("$count", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun MultiSelectSheet(
    options: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filtered = remember(options, searchQuery) {
        if (searchQuery.isBlank()) options
        else options.filter { it.contains(searchQuery, ignoreCase = true) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF1A1A1A),
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.75f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Select Genres", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.6f))
                    }
                }
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp).height(36.dp).background(Color(0xFF2A2A2A), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp),
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (searchQuery.isEmpty()) Text("Search...", color = Color.White.copy(alpha = 0.3f), fontSize = 14.sp)
                            innerTextField()
                        }
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (filtered.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No matches", color = Color.White.copy(alpha = 0.3f))
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
                    ) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            filtered.forEach { option ->
                                FilterChip(
                                    selected = option in selected,
                                    onClick = { onToggle(option) },
                                    label = { Text(option, style = MaterialTheme.typography.labelSmall) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = Color(0xFF2A2A2A),
                                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                        labelColor = Color.White.copy(alpha = 0.7f),
                                        selectedLabelColor = MaterialTheme.colorScheme.primary
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        borderColor = Color.Transparent,
                                        selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                        enabled = true,
                                        selected = option in selected
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TagSelectSheet(
    allTags: List<MediaTag>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filtered = remember(allTags, searchQuery) {
        val q = searchQuery.lowercase().trim()
        if (q.isEmpty()) allTags
        else allTags.filter { it.name.lowercase().contains(q) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF1A1A1A),
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.75f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Select Tags", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.6f))
                    }
                }
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp).height(36.dp).background(Color(0xFF2A2A2A), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp),
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (searchQuery.isEmpty()) Text("Search tags...", color = Color.White.copy(alpha = 0.3f), fontSize = 14.sp)
                            innerTextField()
                        }
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (filtered.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No tags match", color = Color.White.copy(alpha = 0.3f))
                    }
                } else {
                    val selectedCount = selected.size
                    Text(
                        "${filtered.size} tags${if (selectedCount > 0) " • $selectedCount selected" else ""}",
                        color = Color.White.copy(alpha = 0.4f),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
                    ) {
                        items(filtered, key = { it.id }) { tag ->
                            val isSelected = tag.name in selected
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { onToggle(tag.name) }.padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.tertiary else Color(0xFF2A2A2A),
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    if (isSelected) Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.padding(3.dp))
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    tag.name,
                                    color = Color.White.copy(alpha = if (isSelected) 0.9f else 0.6f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.DropdownFilter(
    label: String,
    currentValue: String?,
    options: List<String>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit
) {
    Column(modifier = Modifier.weight(1f)) {
        Text(label, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
        Box {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF2A2A2A),
                modifier = Modifier.fillMaxWidth().height(34.dp).clickable(onClick = onToggle)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(currentValue ?: "Any", color = if (currentValue != null) Color.White else Color.White.copy(alpha = 0.4f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
                DropdownMenuItem(text = { Text("Any") }, onClick = { onSelect(null); onDismiss() })
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.replace("_", " ").replaceFirstChar { it.uppercase() }) },
                        onClick = { onSelect(option); onDismiss() }
                    )
                }
            }
        }
    }
}


