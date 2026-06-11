package com.blissless.tensei.stream

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

import com.blissless.tensei.ui.screens.episode.ExtensionStreamParams
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onPlayVideo: (ExtensionStreamParams) -> Unit = {},
    viewModel: StreamViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var searchText by remember { mutableStateOf("") }
    var selectedExtIndex by remember { mutableIntStateOf(-1) }
    var showExtPicker by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val sources = viewModel.getSources()

    LaunchedEffect(Unit) {
        viewModel.loadSources()
    }

    if (showExtPicker && sources.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showExtPicker = false },
            title = { Text("Choose extension") },
            text = {
                Column {
                    sources.forEachIndexed { index, sw ->
                        TextButton(
                            onClick = {
                                showExtPicker = false
                                selectedExtIndex = index
                                viewModel.search(searchText, sw)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(sw.extension.name.removePrefix("Aniyomi: "))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showExtPicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Auto-play when videos are ready: auto-select best video (prefer 1080p)
    val pendingVideos = uiState.pendingVideos
    LaunchedEffect(pendingVideos) {
        if (pendingVideos != null && pendingVideos.isNotEmpty()) {
            val idx = pendingVideos.indexOfLast { it.videoTitle.contains("1080", ignoreCase = true) || it.videoTitle.contains("1080p", ignoreCase = true) }
                .let { if (it < 0) pendingVideos.indices.last else it }
            val video = pendingVideos[idx]
            val source = uiState.selectedSource?.source
            val referer = video.headers?.let { h ->
                (0 until h.size).firstOrNull { h.name(it).equals("Referer", ignoreCase = true) }
                    ?.let { h.value(it) }
            } ?: ""
            val headers = video.headers?.let { h ->
                (0 until h.size).associate { h.name(it) to h.value(it) }
            } ?: emptyMap()
            PlayerData.extensionSource = source
            PlayerData.extensionEpisode = uiState.selectedEpisode
            PlayerData.allHosters = uiState.hosters ?: emptyList()
            onPlayVideo(ExtensionStreamParams(
                videoUrl = video.videoUrl,
                referer = referer,
                subtitleUrl = video.subtitleTracks.firstOrNull()?.url,
                animeName = uiState.selectedAnime?.title ?: "",
                episodeNumber = uiState.selectedEpisode?.episode_number?.toInt() ?: 1,
                extensionClient = (source as? AnimeHttpSource)?.client,
                extensionHeaders = headers,
                allHosters = uiState.hosters ?: emptyList(),
                allVideos = pendingVideos,
                sourcePackageName = uiState.selectedSource?.extension?.packageName ?: "",
                episodeUrl = uiState.selectedEpisode?.url ?: "",
            ))
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.error) {
        uiState.error?.let { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Search Anime") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                label = { Text("Anime name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    if (uiState.isSearching) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else if (!uiState.isInitialized) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        Button(onClick = {
                            if (selectedExtIndex >= 0) {
                                viewModel.search(searchText, sources[selectedExtIndex])
                            } else {
                                showExtPicker = true
                            }
                        }) {
                            Text("Search")
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (sources.isNotEmpty()) {
                Text(
                    text = if (selectedExtIndex >= 0) "Using: ${sources[selectedExtIndex].extension.name.removePrefix("Aniyomi: ")}"
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

            when {
                !uiState.isInitialized -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                uiState.isSearching -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Searching...")
                        }
                    }
                }
                uiState.searchResults.isEmpty() && uiState.query.isNotEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No results found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                uiState.selectedAnime != null -> {
                    EpisodeListContent(viewModel, uiState, context)
                }
                uiState.searchResults.isNotEmpty() -> {
                    AnimeResults(viewModel, uiState)
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
private fun AnimeResults(
    viewModel: StreamViewModel,
    uiState: StreamUiState,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(uiState.searchResults, key = { "${it.source.extension.packageName}_${it.anime.url}" }) { result ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        viewModel.selectAnime(result.source, result.anime)
                    }
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
                        SuggestionChip(
                            onClick = {},
                            label = {
                                Text(
                                    result.source.extension.name.removePrefix("Aniyomi: "),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeListContent(
    viewModel: StreamViewModel,
    uiState: StreamUiState,
    context: android.content.Context,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = uiState.selectedAnime!!.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { viewModel.backToResults() }) {
                Text("Back")
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = uiState.selectedSource?.extension?.name?.removePrefix("Aniyomi: ") ?: "",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))

        if (uiState.isLoadingEpisodes) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.episodes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No episodes found", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(uiState.episodes, key = { it.url }) { episode ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.selectEpisode(episode) }
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
                                color = MaterialTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = episode.name.ifEmpty { "Episode ${episode.episode_number}" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (uiState.selectedEpisode?.url == episode.url && uiState.isLoadingVideos) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}


