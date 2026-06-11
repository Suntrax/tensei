package com.blissless.tensei.ui.screens.episode

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.blissless.tensei.stream.SourceManager
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import android.util.Log
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

data class ExtensionStreamParams(
    val videoUrl: String,
    val referer: String,
    val subtitleUrl: String?,
    val animeName: String,
    val episodeNumber: Int,
    val extensionClient: OkHttpClient?,
    val extensionHeaders: Map<String, String>,
    val allHosters: List<Hoster> = emptyList(),
    val allVideos: List<Video> = emptyList(),
    val sourcePackageName: String = "",
    val episodeUrl: String = "",
    val extensionName: String = "",
)

private data class SearchResult(
    val anime: SAnime,
    val source: SourceManager.SourceWithExt,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionEpisodeScreen(
    onPlayVideo: (ExtensionStreamParams) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    sourceManager: SourceManager? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var localSourceManager by remember { mutableStateOf<SourceManager?>(null) }
    var isInitialized by remember { mutableStateOf(false) }
    var sources by remember { mutableStateOf<List<SourceManager.SourceWithExt>>(emptyList()) }
    var selectedExtIndex by remember { mutableIntStateOf(-1) }
    var searchText by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var selectedAnime by remember { mutableStateOf<SAnime?>(null) }
    var selectedSource by remember { mutableStateOf<SourceManager.SourceWithExt?>(null) }
    var episodes by remember { mutableStateOf<List<SEpisode>>(emptyList()) }
    var isLoadingEpisodes by remember { mutableStateOf(false) }
    var selectedEpisode by remember { mutableStateOf<SEpisode?>(null) }
    var isLoadingVideos by remember { mutableStateOf(false) }
    var hosters by remember { mutableStateOf<List<Hoster>?>(null) }
    var selectedHoster by remember { mutableStateOf<Hoster?>(null) }
    var pendingVideos by remember { mutableStateOf<List<Video>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var videoAnimeName by remember { mutableStateOf("") }

    val effectiveSm = sourceManager ?: localSourceManager

    LaunchedEffect(Unit) {
        if (effectiveSm == null) {
            val sm = SourceManager(context)
            sm.loadSources()
            localSourceManager = sm
            sources = sm.getSources()
        } else {
            if (effectiveSm.getSources().isEmpty()) {
                effectiveSm.loadSources()
            }
            sources = effectiveSm.getSources()
        }
        isInitialized = true
    }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            error = null
        }
    }

    val activeSource = remember(selectedExtIndex, sources) {
        if (selectedExtIndex in sources.indices) sources[selectedExtIndex] else null
    }

    fun extractReferer(video: Video): String {
        return video.headers?.let { h ->
            (0 until h.size).firstOrNull { h.name(it).equals("Referer", ignoreCase = true) }
                ?.let { h.value(it) }
        } ?: ""
    }

    fun extractHeaders(video: Video): Map<String, String> {
        return video.headers?.let { h ->
            (0 until h.size).associate { h.name(it) to h.value(it) }
        } ?: emptyMap()
    }

    fun playVideo(video: Video, source: AnimeCatalogueSource) {
        val referer = extractReferer(video)
        val sourceName = source.name.ifEmpty { activeSource?.extension?.name?.removePrefix("Aniyomi: ") ?: "" }
        onPlayVideo(
            ExtensionStreamParams(
                videoUrl = video.videoUrl,
                referer = referer,
                subtitleUrl = video.subtitleTracks.firstOrNull()?.url,
                animeName = videoAnimeName,
                episodeNumber = selectedEpisode?.episode_number?.toInt() ?: 1,
                extensionClient = (source as? AnimeHttpSource)?.client,
                extensionHeaders = extractHeaders(video),
                extensionName = sourceName,
            )
        )
    }

    fun handleVideos(videos: List<Video>, source: AnimeCatalogueSource) {
        when {
            videos.isEmpty() -> error = "No video sources found"
            videos.size == 1 -> playVideo(videos.first(), source)
            else -> pendingVideos = videos
        }
    }

    fun performSearch() {
        if (searchText.isBlank()) return
        isSearching = true
        searchResults = emptyList()
        selectedAnime = null
        selectedSource = null
        episodes = emptyList()
        selectedEpisode = null
        hosters = null
        pendingVideos = null
        scope.launch {
            val sm = effectiveSm
            if (sm == null) {
                isSearching = false
                return@launch
            }
            val results = mutableListOf<SearchResult>()
            sm.search(searchText, activeSource) { sw, animes ->
                animes.forEach { anime ->
                    results.add(SearchResult(anime, sw))
                }
                Log.i("ExtensionSearch", "${sw.source.name}: found ${animes.size} results")
                animes.forEach { anime ->
                    Log.i("ExtensionSearch", "  -> [${sw.source.name}] ${anime.title} (${anime.url})")
                }
            }
            searchResults = results.toList()
            Log.i("ExtensionSearch", "Total: ${results.size} results for \"$searchText\"")
            isSearching = false
        }
    }

    fun onAnimeSelect(result: SearchResult) {
        selectedAnime = result.anime
        selectedSource = result.source
        videoAnimeName = result.anime.title
        isLoadingEpisodes = true
        episodes = emptyList()
        selectedEpisode = null
        hosters = null
        pendingVideos = null
        error = null
        scope.launch {
            try {
                val sm = effectiveSm
                if (sm == null) {
                    error = "SourceManager not ready"
                    isLoadingEpisodes = false
                    return@launch
                }
                val source = result.source.source
                var currentAnime = result.anime
                var episodeList = sm.getEpisodes(source, currentAnime)
                if (episodeList.isEmpty()) {
                    try {
                        currentAnime = sm.getAnimeDetails(source, currentAnime)
                        episodeList = sm.getEpisodes(source, currentAnime)
                    } catch (e: Exception) {
                    }
                }
                if (episodeList.isEmpty()) {
                    error = "No episodes found for '${currentAnime.title}'"
                }
                episodes = episodeList
            } catch (e: Exception) {
                error = "Failed to load episodes: ${e.message}"
            }
            isLoadingEpisodes = false
        }
    }

    fun onEpisodeSelect(episode: SEpisode) {
        val source = selectedSource ?: return
        val currentAnime = selectedAnime
        selectedEpisode = episode
        isLoadingVideos = true
        hosters = null
        selectedHoster = null
        pendingVideos = null
        scope.launch {
            val sm = effectiveSm ?: return@launch
            val catalogueSource = source.source
            val hosterList = sm.getHosters(catalogueSource, episode, currentAnime)
            isLoadingVideos = false
            hosters = hosterList
            when {
                hosterList == null -> {
                    val videos = sm.getVideosDirect(catalogueSource, episode, currentAnime)
                    handleVideos(videos, catalogueSource)
                }
                hosterList.size == 1 -> {
                    selectedHoster = hosterList.first()
                    val videos = sm.getVideosFromHoster(catalogueSource, hosterList.first())
                    handleVideos(videos, catalogueSource)
                }
                hosterList.isEmpty() -> {
                    val videos = sm.getVideosDirect(catalogueSource, episode, currentAnime)
                    handleVideos(videos, catalogueSource)
                }
            }
        }
    }

    fun onHosterSelect(hoster: Hoster) {
        val source = selectedSource ?: return
        val episode = selectedEpisode ?: return
        val currentAnime = selectedAnime
        selectedHoster = hoster
        isLoadingVideos = true
        scope.launch {
            val sm = effectiveSm ?: return@launch
            val catalogueSource = source.source
            val videos = try {
                sm.getVideosFromHoster(catalogueSource, hoster)
            } catch (_: Throwable) {
                sm.getVideosDirect(catalogueSource, episode, currentAnime)
            }
            isLoadingVideos = false
            handleVideos(videos, catalogueSource)
        }
    }

    fun onVideoSelect(index: Int) {
        val videos = pendingVideos ?: return
        val source = selectedSource ?: return
        val episode = selectedEpisode ?: return
        val currentAnime = selectedAnime
        isLoadingVideos = true
        scope.launch {
            val sm = effectiveSm ?: return@launch
            val catalogueSource = source.source
            try {
                val freshVideos = sm.getVideosDirect(catalogueSource, episode, currentAnime)
                val freshVideo = freshVideos.getOrNull(index)
                if (freshVideo != null) {
                    playVideo(freshVideo, catalogueSource)
                } else if (videos.isNotEmpty()) {
                    playVideo(videos[index.coerceIn(videos.indices)], catalogueSource)
                } else {
                    error = "No video available"
                }
            } catch (_: Throwable) {
                if (videos.isNotEmpty()) {
                    playVideo(videos[index.coerceIn(videos.indices)], catalogueSource)
                } else {
                    error = "Failed to load video"
                }
            }
            pendingVideos = null
            isLoadingVideos = false
        }
    }

    // Hoster picker dialog
    if (hosters != null && hosters!!.size > 1 && selectedHoster == null) {
        AlertDialog(
            onDismissRequest = { hosters = null },
            title = { Text("Select Server") },
            text = {
                Column {
                    hosters!!.forEach { hoster ->
                        TextButton(
                            onClick = { onHosterSelect(hoster) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(hoster.hosterName.ifEmpty { "Server" })
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { hosters = null }) { Text("Cancel") }
            }
        )
    }

    // Video quality picker dialog
    if (pendingVideos != null && pendingVideos!!.size > 1) {
        AlertDialog(
            onDismissRequest = { pendingVideos = null },
            title = { Text("Select Quality") },
            text = {
                Column {
                    pendingVideos!!.forEachIndexed { idx, video ->
                        val label = if (video.resolution != null && video.resolution > 0) "${video.videoTitle} (${video.resolution}p)"
                        else video.videoTitle.ifEmpty { "Source ${idx + 1}" }
                        TextButton(
                            onClick = { onVideoSelect(idx) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pendingVideos = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (selectedAnime != null) selectedAnime!!.title else "Extension Search",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedAnime != null) {
                            selectedAnime = null
                            selectedSource = null
                            episodes = emptyList()
                            selectedEpisode = null
                            hosters = null
                            pendingVideos = null
                        } else {
                            onBackClick()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            if (selectedAnime == null) {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    label = { Text("Anime name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    trailingIcon = {
                        if (isSearching) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else if (!isInitialized) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            Button(onClick = { performSearch() }) {
                                Text("Search")
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (sources.isNotEmpty()) {
                    Text(
                        text = if (activeSource != null) "Using: ${activeSource.extension.name.removePrefix("Aniyomi: ")}"
                        else "No extension selected — searches all",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    sources.forEachIndexed { index, sw ->
                        FilterChip(
                            selected = selectedExtIndex == index,
                            onClick = {
                                selectedExtIndex = if (selectedExtIndex == index) -1 else index
                            },
                            label = {
                                Text(
                                    sw.extension.name.removePrefix("Aniyomi: ").take(16),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
            }

            when {
                !isInitialized -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                isSearching -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Searching...")
                        }
                    }
                }
                selectedAnime != null -> {
                    EpisodeListContent(
                        episodes = episodes,
                        isLoading = isLoadingEpisodes,
                        animeName = selectedAnime!!.title,
                        sourceName = selectedSource?.extension?.name?.removePrefix("Aniyomi: ") ?: "",
                        selectedEpisodeUrl = selectedEpisode?.url,
                        isLoadingVideo = isLoadingVideos,
                        onEpisodeClick = { onEpisodeSelect(it) },
                        onBackClick = {
                            selectedAnime = null
                            selectedSource = null
                            episodes = emptyList()
                            selectedEpisode = null
                            hosters = null
                            pendingVideos = null
                        }
                    )
                }
                searchResults.isNotEmpty() -> {
                    SearchResultsList(
                        results = searchResults,
                        onAnimeClick = { onAnimeSelect(it) }
                    )
                }
                searchText.isNotEmpty() && !isSearching -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No results found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                else -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Select an extension, enter an anime name, and tap Search",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultsList(
    results: List<SearchResult>,
    onAnimeClick: (SearchResult) -> Unit,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(results, key = { "${it.source.extension.packageName}_${it.anime.url}" }) { result ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAnimeClick(result) },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = result.anime.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = result.source.extension.name.removePrefix("Aniyomi: "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeListContent(
    episodes: List<SEpisode>,
    isLoading: Boolean,
    animeName: String,
    sourceName: String,
    selectedEpisodeUrl: String?,
    isLoadingVideo: Boolean,
    onEpisodeClick: (SEpisode) -> Unit,
    onBackClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = animeName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onBackClick) {
                Text("Back")
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = sourceName,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (episodes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No episodes found", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(episodes, key = { it.url }) { episode ->
                    val isSelected = episode.url == selectedEpisodeUrl
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEpisodeClick(episode) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = String.format("%.0f", episode.episode_number),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = episode.name.ifEmpty { "Episode ${episode.episode_number}" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (episode.date_upload > 0) {
                                    Text(
                                        text = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                                            .format(java.util.Date(episode.date_upload * 1000)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                val scanlator = episode.scanlator
                                if (scanlator != null) {
                                    Text(
                                        text = scanlator,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (episode.url == selectedEpisodeUrl && isLoadingVideo) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}


