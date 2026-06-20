package com.blissless.tensei.ui.screens.downloads

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.blissless.tensei.MainViewModel
import com.blissless.tensei.data.models.AnimeMedia
import com.blissless.tensei.data.models.TmdbEpisode
import com.blissless.tensei.download.EpisodeDownloadManager
import com.blissless.tensei.ui.components.ContinueWatchingRow
import com.blissless.tensei.ui.components.SectionHeader
import com.blissless.tensei.ui.screens.player.OfflinePlayerScreen

@OptIn(UnstableApi::class)
@SuppressLint("UnstableApiUsage")
@Composable
fun DownloadsScreen(
    viewModel: MainViewModel,
    downloadManager: EpisodeDownloadManager,
    isOled: Boolean,
    onNavbarHidden: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val downloadsInfo by downloadManager.downloadsInfo.collectAsState()
    val groupedDownloads = remember(downloadsInfo) {
        val completed = downloadsInfo.values.filter { it.state == Download.STATE_COMPLETED }
        completed.groupBy { it.animeName }.map { (name, infos) ->
            EpisodeDownloadManager.GroupedDownload(
                animeName = name,
                episodes = infos.sortedBy { it.episode },
                totalSize = infos.sumOf { it.totalBytes },
            )
        }.sortedBy { it.animeName }
    }

    val activeBatches by downloadManager.activeBatches.collectAsState()
    val inProgressDownloads = remember(downloadsInfo, activeBatches) {
        val fromDownloads = downloadsInfo.values.filter {
            it.state == Download.STATE_QUEUED || it.state == Download.STATE_DOWNLOADING
        }.groupBy { it.animeName }
        val result = fromDownloads.toMutableMap()
        for (animeName in activeBatches) {
            if (animeName !in result) {
                result[animeName] = emptyList()
            }
        }
        result
    }
    val failedDownloads = remember(downloadsInfo) {
        downloadsInfo.values.filter {
            it.state == Download.STATE_FAILED
        }.groupBy { it.animeName }
    }

    var searchQuery by remember { mutableStateOf("") }
    val filteredGroupedDownloads = remember(groupedDownloads, searchQuery) {
        if (searchQuery.isBlank()) groupedDownloads
        else groupedDownloads.filter { group ->
            group.animeName.contains(searchQuery, ignoreCase = true) ||
            group.episodes.any { it.episode.toString() == searchQuery }
        }
    }
    val filteredInProgressDownloads = remember(inProgressDownloads, searchQuery) {
        if (searchQuery.isBlank()) inProgressDownloads
        else inProgressDownloads.filter { (name, _) ->
            name.contains(searchQuery, ignoreCase = true)
        }
    }
    val filteredFailedDownloads = remember(failedDownloads, searchQuery) {
        if (searchQuery.isBlank()) failedDownloads
        else failedDownloads.filter { (name, _) ->
            name.contains(searchQuery, ignoreCase = true)
        }
    }

    var showDeleteConfirm by remember { mutableStateOf<String?>(null) }
    var selectedAnime by remember { mutableStateOf<EpisodeDownloadManager.GroupedDownload?>(null) }
    var showDownloadDialogFor by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var playingDownload by remember { mutableStateOf<EpisodeDownloadManager.DownloadInfo?>(null) }
    var playerEpisodes by remember { mutableStateOf<List<EpisodeDownloadManager.DownloadInfo>>(emptyList()) }
    val useMonochrome by viewModel.disableMaterialColors.collectAsState()
    val defaultSubtitleLang by viewModel.defaultSubtitleLang.collectAsState()
    val tmdbEpisodeCache by viewModel.tmdbEpisodeCache.collectAsState()
    val swipeVolume by viewModel.swipeVolume.collectAsState()
    val swipeBrightness by viewModel.swipeBrightness.collectAsState()
    val swipeSwap by viewModel.swipeSwap.collectAsState()
    val autoSkipOpening by viewModel.autoSkipOpening.collectAsState()
    val autoSkipEnding by viewModel.autoSkipEnding.collectAsState()
    val autoPlayNextEpisode by viewModel.autoPlayNextEpisode.collectAsState()
    val playbackPositions by viewModel.playbackPositions.collectAsState()
    val playbackDurations by viewModel.playbackDurations.collectAsState()

    val continueWatchingAnime = remember(groupedDownloads, playbackPositions) {
        groupedDownloads.mapNotNull { group ->
            val animeId = group.episodes.firstOrNull()?.animeId ?: return@mapNotNull null
            val watchedEpisodes = group.episodes.filter { ep ->
                val key = "${animeId}_${ep.episode}_offline"
                (playbackPositions[key] ?: 0L) > 0L
            }
            if (watchedEpisodes.isEmpty()) return@mapNotNull null
            val maxWatchedEp = watchedEpisodes.maxOf { it.episode }
            AnimeMedia(
                id = animeId,
                title = group.animeName,
                cover = "",
                progress = maxOf(0, maxWatchedEp - 1),
                totalEpisodes = group.episodes.maxOf { it.episode },
                latestEpisode = group.episodes.maxOf { it.episode },
                listStatus = "CURRENT"
            )
        }.sortedBy { it.title }
    }

    LaunchedEffect(Unit) {
        viewModel.notificationAnimeTaps.collect { animeName ->
            if (downloadManager.getBatchEpisodes(animeName).isNotEmpty() ||
                downloadsInfo.values.any { it.animeName == animeName && (it.state == Download.STATE_QUEUED || it.state == Download.STATE_DOWNLOADING) }
            ) {
                val animeId = downloadsInfo.values.firstOrNull { it.animeName == animeName }?.animeId
                    ?: (groupedDownloads.find { it.animeName == animeName }?.episodes?.firstOrNull()?.animeId ?: 0)
                showDownloadDialogFor = animeId to animeName
            } else {
                selectedAnime = groupedDownloads.find { it.animeName == animeName }
            }
        }
    }

    LaunchedEffect(Unit) {
        val downloadedIds = (groupedDownloads.flatMap { it.episodes.map { d -> d.animeId } } +
                inProgressDownloads.flatMap { (_, infos) -> infos.map { it.animeId } } +
                failedDownloads.flatMap { (_, infos) -> infos.map { it.animeId } }).toSet()
        viewModel.pruneTmdbEpisodeCache(downloadedIds)
    }

    val hasContent = groupedDownloads.isNotEmpty() || inProgressDownloads.isNotEmpty() || failedDownloads.isNotEmpty()
    val batteryOptDismissed = remember { mutableStateOf(false) }
    val pm = remember { context.getSystemService(android.os.PowerManager::class.java) }
    val isIgnoringBattery = remember { pm?.isIgnoringBatteryOptimizations(context.packageName) == true }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .background(if (isOled) Color.Black else MaterialTheme.colorScheme.background)
        ) {
        if (!hasContent) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            if (isOled) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(20.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Storage,
                        contentDescription = null,
                        tint = if (isOled) Color.White.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "No Downloads",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isOled) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Downloaded episodes will appear here.\nOpen an anime to start downloading.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isOled) Color.White.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 48.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Stored in app cache",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isOled) Color.White.copy(alpha = 0.3f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.navigationBarsPadding()
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Downloads",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Stored in app cache",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isOled) Color.White.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search downloads...", color = if (isOled) Color.White.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurfaceVariant) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = if (isOled) Color.White.copy(alpha = 0.2f) else MaterialTheme.colorScheme.outline,
                            cursorColor = MaterialTheme.colorScheme.primary,
                            focusedTextColor = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface,
                        )
                    )
                }

                // Battery optimization hint
                if (!isIgnoringBattery && !batteryOptDismissed.value) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isOled) Color(0xFF1A1A1A) else Color(0xFFFFF3E0)
                            )
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Storage,
                                    contentDescription = null,
                                    tint = Color(0xFFF57C00),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Battery Optimization",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isOled) Color.White else Color(0xFFE65100)
                                    )
                                    Text(
                                        "Disable battery optimization for better download reliability",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isOled) Color.White.copy(alpha = 0.7f) else Color(0xFFBF360C)
                                    )
                                }
                                TextButton(onClick = {
                                    try {
                                        val intent = android.content.Intent(
                                            android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
                                        )
                                        context.startActivity(intent)
                                    } catch (_: Exception) {}
                                }) {
                                    Text("Fix", fontWeight = FontWeight.Bold)
                                }
                                IconButton(onClick = { batteryOptDismissed.value = true }) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Dismiss",
                                        tint = if (isOled) Color.White.copy(alpha = 0.5f) else Color(0xFFBF360C).copy(alpha = 0.5f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Continue Watching section
                if (continueWatchingAnime.isNotEmpty()) {
                    item(key = "continue_watching_header") {
                        Spacer(modifier = Modifier.height(8.dp))
                        SectionHeader(
                            title = "Continue Watching",
                            icon = Icons.Default.PlayArrow,
                            count = continueWatchingAnime.size,
                        )
                    }
                    item(key = "continue_watching_row") {
                        ContinueWatchingRow(
                            animeList = continueWatchingAnime,
                            playbackPositions = playbackPositions,
                            playbackDurations = playbackDurations,
                            tmdbEpisodeCache = tmdbEpisodeCache,
                            preferEnglishTitles = true,
                            disableMaterialColors = useMonochrome,
                            playbackKeySuffix = "_offline",
                            onPlayClick = { anime, episode ->
                                val group = groupedDownloads.find { it.animeName == anime.title }
                                val download = group?.episodes?.find { it.episode == episode }
                                if (download != null) {
                                    playerEpisodes = group.episodes
                                    playingDownload = download
                                }
                            }
                        )
                    }
                }

                // In-progress downloads section
                if (filteredInProgressDownloads.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Downloading",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isOled) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.primary
                        )
                    }
                    filteredInProgressDownloads.forEach { (animeName, infos) ->
                        item(key = "in_progress_$animeName") {
                            AnimatedVisibility(
                                visible = true,
                                enter = fadeIn(tween(300)) + slideInHorizontally(tween(300)) { it },
                                exit = fadeOut(tween(200)) + slideOutHorizontally(tween(200)) { -it }
                            ) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isOled) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = animeName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.clickable {
                                            val animeId = infos.firstOrNull()?.animeId ?: (downloadManager.getBatchEpisodes(animeName).hashCode())
                                            showDownloadDialogFor = animeId to animeName
                                        }
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    val batchEps = downloadManager.getBatchEpisodes(animeName)
                                    if (infos.isNotEmpty()) {
                                        infos.sortedBy { it.episode }.forEach { info ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Ep ${info.episode}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = if (isOled) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.width(48.dp)
                                                )
                                                LinearProgressIndicator(
                                                    modifier = Modifier.weight(1f).height(4.dp).padding(end = 8.dp),
                                                    color = MaterialTheme.colorScheme.primary,
                                                    trackColor = if (isOled) Color(0xFF333333) else MaterialTheme.colorScheme.surfaceVariant,
                                                )
                                            }
                                        }
                                    } else if (batchEps.isNotEmpty()) {
                                        Text(
                                            text = "${batchEps.first()}-${batchEps.last()} queued",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isOled) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        TextButton(
                                            onClick = { downloadManager.cancelBatch(animeName) }
                                        ) { Text("Cancel All") }
                                        TextButton(
                                            onClick = {
                                                val animeId = infos.firstOrNull()?.animeId ?: (downloadManager.getBatchEpisodes(animeName).hashCode())
                                                showDownloadDialogFor = animeId to animeName
                                            }
                                        ) { Text("View Progress") }
                                    }
                                }
                            }
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }

                // Failed downloads section
                if (filteredFailedDownloads.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Failed",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFEF5350)
                        )
                    }
                    filteredFailedDownloads.forEach { (animeName, infos) ->
                        item(key = "failed_$animeName") {
                            AnimatedVisibility(
                                visible = true,
                                enter = fadeIn(tween(300)) + slideInHorizontally(tween(300)) { it },
                                exit = fadeOut(tween(200)) + slideOutHorizontally(tween(200)) { -it }
                            ) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isOled) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = animeName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    infos.sortedBy { it.episode }.forEach { info ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Ep ${info.episode}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (isOled) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.width(48.dp)
                                            )
                                            Text(
                                                text = "Failed",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFFEF5350),
                                                modifier = Modifier.weight(1f)
                                            )
                                            IconButton(onClick = {
                                                viewModel.retryDownload(info.animeId, info.episode)
                                            }) {
                                                Icon(Icons.Default.Refresh, contentDescription = "Retry", tint = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    }
                                }
                            }
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }

                // Completed downloads
                if (filteredGroupedDownloads.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Saved",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isOled) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.primary
                        )
                    }
                    items(filteredGroupedDownloads, key = { it.animeName }) { group ->
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(tween(300)) + slideInHorizontally(tween(300)) { it },
                            exit = fadeOut(tween(200)) + slideOutHorizontally(tween(200)) { -it }
                        ) {
                            DownloadsAnimeCard(
                                anime = group,
                                isOled = isOled,
                                onClick = { selectedAnime = group },
                                onDelete = { showDeleteConfirm = group.animeName },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            containerColor = if (isOled) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surface,
            title = { Text("Delete Downloads?", color = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface) },
            text = { Text("All downloaded episodes for this anime will be removed.", color = if (isOled) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm?.let { name ->
                        val animeIds = groupedDownloads.find { it.animeName == name }?.episodes?.map { it.animeId }?.toSet() ?: emptySet()
                        downloadManager.removeAnime(name)
                        animeIds.forEach { viewModel.clearTmdbEpisodeCache(it) }
                        showDeleteConfirm = null
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) { Text("Cancel") }
            }
        )
    }

    if (selectedAnime != null) {
        val animeId = selectedAnime!!.episodes.firstOrNull()?.animeId ?: 0
        LaunchedEffect(animeId) {
            if (animeId > 0 && viewModel.getCachedTmdbEpisodes(animeId) == null) {
                try {
                    val episodes = viewModel.fetchTmdbEpisodes(selectedAnime!!.animeName, animeId)
                    viewModel.cacheTmdbEpisodes(animeId, episodes)
                } catch (_: Exception) {}
            }
        }
        BackHandler { if (playingDownload == null) selectedAnime = null }
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            DownloadedEpisodesDialog(
                anime = selectedAnime!!,
                downloadManager = downloadManager,
                isOled = isOled,
                onDismiss = { selectedAnime = null },
                onPlay = { info ->
                    playerEpisodes = selectedAnime?.episodes ?: emptyList()
                    playingDownload = info
                },
                onClearTmdbCache = { animeId -> viewModel.clearTmdbEpisodeCache(animeId) },
                tmdbEpisodes = tmdbEpisodeCache[animeId]?.associate { it.episode to it } ?: emptyMap(),
                playbackPositions = playbackPositions,
                playbackDurations = playbackDurations,
            )
        }
    }

    if (playingDownload != null) {
        BackHandler { playingDownload = null }
    }
    } // end inner padded Box

    if (playingDownload != null) {
        val savedPosition = remember(playingDownload) {
            viewModel.getPlaybackPosition(playingDownload!!.animeId, playingDownload!!.episode, isOffline = true)
        }
        Box(modifier = Modifier.fillMaxSize()) {
            OfflinePlayerScreen(
                downloadInfo = playingDownload!!,
                downloadManager = downloadManager,
                useMonochrome = useMonochrome,
                defaultSubtitleLang = defaultSubtitleLang,
                swipeVolume = swipeVolume,
                swipeBrightness = swipeBrightness,
                swipeSwap = swipeSwap,
                onSwipeVolumeChange = { viewModel.setSwipeVolume(it) },
                onSwipeBrightnessChange = { viewModel.setSwipeBrightness(it) },
                onSwipeSwapChange = { viewModel.setSwipeSwap(it) },
                autoSkipOpening = autoSkipOpening,
                autoSkipEnding = autoSkipEnding,
                autoPlayNextEpisode = autoPlayNextEpisode,
                onAutoPlayNextEpisodeChanged = { viewModel.setAutoPlayNextEpisode(it) },
                onDismiss = { playingDownload = null },
                allEpisodes = playerEpisodes,
                onNavbarHidden = onNavbarHidden,
                tmdbEpisodes = tmdbEpisodeCache[playingDownload!!.animeId]?.associate { it.episode to it } ?: emptyMap(),
                onSavePlaybackPosition = { animeId, episode, pos, dur ->
                    viewModel.savePlaybackPosition(animeId, episode, pos, dur, isOffline = true)
                },
                initialPosition = savedPosition,
            )
        }
    }

    val downloadDialogInfo = showDownloadDialogFor
    if (downloadDialogInfo != null) {
        val (_, animeName) = downloadDialogInfo
        val batchEps = downloadManager.getBatchEpisodes(animeName)
        val sortedBatchEps = if (batchEps.isNotEmpty()) batchEps.sorted() else {
            downloadsInfo.values.filter { it.animeName == animeName }.map { it.episode }.sorted()
        }
        val anyActive = sortedBatchEps.any { ep ->
            val info = downloadsInfo.values.find { it.animeName == animeName && it.episode == ep }
            info != null && (info.state == Download.STATE_QUEUED || info.state == Download.STATE_DOWNLOADING)
        }
        Dialog(
            onDismissRequest = { showDownloadDialogFor = null },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = if (isOled) Color.Black else MaterialTheme.colorScheme.background
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = if (isOled) Color(0xFF0A0A0A) else MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp).statusBarsPadding(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = {
                                showDownloadDialogFor = null
                            }) {
                                Icon(Icons.Default.Close, "Close", tint = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = animeName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${sortedBatchEps.size} episodes selected",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isOled) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        itemsIndexed(sortedBatchEps, key = { _, ep -> "${animeName}_batch_$ep" }) { _, ep ->
                            val info = downloadsInfo.values.find { it.animeName == animeName && it.episode == ep }
                            val state = info?.state
                            val isDone = state == Download.STATE_COMPLETED
                            val isFailed = state == Download.STATE_FAILED
                            val isDownloading = state == Download.STATE_DOWNLOADING
                            val isQueued = state == Download.STATE_QUEUED
                            val bgColor = when {
                                isDone -> if (isOled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                isFailed -> if (isOled) Color(0xFF2E1A1A) else Color(0xFFFFEBEE)
                                else -> if (isOled) Color(0xFF1A1A2E) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            }
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = bgColor)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(
                                                if (isDone) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                else if (isFailed) Color(0xFFEF5350).copy(alpha = 0.15f)
                                                else Color.White.copy(alpha = 0.08f),
                                                RoundedCornerShape(10.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        when {
                                            isDone -> Icon(Icons.Default.DownloadDone, "Done", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                                            isFailed -> Icon(Icons.Default.Close, "Failed", tint = Color(0xFFEF5350), modifier = Modifier.size(22.dp))
                                            else -> Text("$ep", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Episode $ep",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = when {
                                                isDone -> MaterialTheme.colorScheme.primary
                                                isFailed -> Color(0xFFEF5350)
                                                else -> if (isOled) Color.White else MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                    }
                                    Text(
                                        text = when {
                                            isDone -> "Saved"
                                            isFailed -> "Failed"
                                            isDownloading -> "Downloading"
                                            isQueued -> "Queued"
                                            else -> "Waiting"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = when {
                                            isDone -> MaterialTheme.colorScheme.primary
                                            isFailed -> Color(0xFFEF5350)
                                            isDownloading -> if (isOled) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurface
                                            else -> if (isOled) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = if (isOled) Color(0xFF0A0A0A) else MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val allDone = !anyActive
                            OutlinedButton(
                                onClick = {
                                    if (allDone) {
                                        showDownloadDialogFor = null
                                    } else {
                                        downloadManager.cancelBatch(animeName)
                                        showDownloadDialogFor = null
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) { Text(if (allDone) "Close" else "Cancel") }
                            val doneCount = sortedBatchEps.count { ep ->
                                val d = downloadsInfo.values.find { it.animeName == animeName && it.episode == ep }
                                d != null && d.state == Download.STATE_COMPLETED
                            }
                            Text(
                                text = "$doneCount/${sortedBatchEps.size} episodes",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isOled) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.CenterVertically)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@SuppressLint("UnstableApiUsage")
@Composable
private fun DownloadedEpisodesDialog(
    anime: EpisodeDownloadManager.GroupedDownload,
    downloadManager: EpisodeDownloadManager,
    isOled: Boolean,
    onDismiss: () -> Unit,
    onPlay: (EpisodeDownloadManager.DownloadInfo) -> Unit,
    onClearTmdbCache: (Int) -> Unit = {},
    tmdbEpisodes: Map<Int, TmdbEpisode> = emptyMap(),
    playbackPositions: Map<String, Long> = emptyMap(),
    playbackDurations: Map<String, Long> = emptyMap(),
) {
    var episodes by remember(anime) { mutableStateOf(anime.episodes) }
    var episodeToDelete by remember { mutableStateOf<EpisodeDownloadManager.DownloadInfo?>(null) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = if (isOled) Color.Black else MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = if (isOled) Color(0xFF0A0A0A) else MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                        .windowInsetsPadding(WindowInsets.statusBars),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = anime.animeName.replace("_", " ").replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (anime.totalSize > 0) "Stored in app cache — ${formatFileSize(anime.totalSize)}" else "Stored in app cache",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isOled) Color.White.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }

                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(episodes.sortedBy { it.episode }) { ep ->
                        val tmdbEp = tmdbEpisodes[ep.episode]
                        val epPlaybackKey = "${ep.animeId}_${ep.episode}_offline"
                        val savedPos = playbackPositions[epPlaybackKey] ?: 0L
                        val epDuration = playbackDurations[epPlaybackKey] ?: 0L
                        val displayBytes = if (ep.totalBytes > 0) ep.totalBytes else ep.downloadedBytes
                        val progressRatio = if (savedPos > 0 && epDuration > 0) (savedPos.toFloat() / epDuration).coerceIn(0f, 1f) else 0f
                        val remainingText = if (savedPos in 1..<epDuration) {
                            val remaining = epDuration - savedPos
                            val mins = (remaining / 60000).toInt()
                            val secs = ((remaining % 60000) / 1000).toInt()
                            "${mins}:${"%02d".format(secs)} left"
                        } else null

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = if (isOled) Color(0xFF111111) else MaterialTheme.colorScheme.surface)
                        ) {
                            Column {
                                // Image section
                                if (tmdbEp?.image != null) {
                                    Box(modifier = Modifier.fillMaxWidth().height(160.dp)) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(LocalContext.current)
                                                .data(tmdbEp.image)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = tmdbEp.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        Box(
                                            modifier = Modifier.fillMaxWidth().height(60.dp).align(Alignment.BottomCenter)
                                                .background(Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                                        )
                                        // EP Badge
                                        Surface(
                                            modifier = Modifier.padding(8.dp).align(Alignment.TopStart),
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color.Black.copy(alpha = 0.7f)
                                        ) {
                                            Text(
                                                text = "EP ${ep.episode}",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                        // Play button overlay
                                        androidx.compose.material3.FilledTonalIconButton(
                                            onClick = { onPlay(ep) },
                                            modifier = Modifier.align(Alignment.Center).size(52.dp),
                                            shape = CircleShape,
                                            colors = androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors(
                                                containerColor = Color.Black.copy(alpha = 0.6f),
                                                contentColor = Color.White
                                            )
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = "Play", modifier = Modifier.size(28.dp))
                                        }
                                        // Delete button top-right
                                        Box(
                                            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(32.dp)
                                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                                .clickable { episodeToDelete = ep },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF5350), modifier = Modifier.size(16.dp))
                                        }
                                    }
                                } else {
                                    // Placeholder when no image
                                    Box(modifier = Modifier.fillMaxWidth().height(100.dp).background(if (isOled) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))) {
                                        Box(
                                            modifier = Modifier.size(44.dp).align(Alignment.Center)
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                        }
                                        Surface(
                                            modifier = Modifier.padding(8.dp).align(Alignment.TopStart),
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color.Black.copy(alpha = 0.7f)
                                        ) {
                                            Text(
                                                text = "EP ${ep.episode}",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                                // Progress bar and remaining time
                                if (savedPos > 5000L) {
                                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                                        LinearProgressIndicator(
                                            progress = { progressRatio },
                                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                            color = MaterialTheme.colorScheme.primary,
                                            trackColor = if (isOled) Color(0xFF333333) else MaterialTheme.colorScheme.surfaceVariant,
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = formatTimeFromMs(savedPos),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isOled) Color.White.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (remainingText != null) {
                                                Text(
                                                    text = remainingText,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                }
                                // Info section
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = if (tmdbEp != null) "Ep ${ep.episode} - ${tmdbEp.title}" else "Episode ${ep.episode}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        color = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (tmdbEp != null && tmdbEp.description.isNotEmpty()) {
                                        Text(
                                            text = tmdbEp.description,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isOled) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = if (ep.category == "dub") "DUB" else "SUB",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isOled) Color.White.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "·",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isOled) Color.White.copy(alpha = 0.25f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                            )
                                            if (ep.downloadTimestamp > 0L) {
                                                Text(
                                                    text = formatDownloadTimestamp(ep.downloadTimestamp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (isOled) Color.White.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = "·",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (isOled) Color.White.copy(alpha = 0.25f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                                )
                                            }
                                            Text(
                                                text = formatFileSize(displayBytes),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isOled) Color.White.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(16.dp)
                ) { Text("Close") }
            }
        }

    if (episodeToDelete != null) {
        AlertDialog(
            onDismissRequest = { episodeToDelete = null },
            containerColor = if (isOled) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surface,
            title = { Text("Delete Episode?", color = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface) },
            text = { Text("Episode ${episodeToDelete!!.episode} of ${anime.animeName.replace("_", " ")} will be removed.", color = if (isOled) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = {
                    val ep = episodeToDelete!!
                    val id = "${ep.animeId}_${ep.episode}"
                    downloadManager.removeDownload(id)
                    episodes = episodes.filter { it.episode != ep.episode }
                    episodeToDelete = null
                    if (episodes.isEmpty()) {
                        onClearTmdbCache(ep.animeId)
                        onDismiss()
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { episodeToDelete = null }) { Text("Cancel") }
            }
        )
    }

}

private fun formatDownloadTimestamp(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${"%.1f".format(bytes / (1024.0 * 1024.0 * 1024.0)).replace('.', ',')} GB"
    }
}

private fun formatTimeFromMs(ms: Long): String {
    val seconds = (ms / 1000) % 60
    val minutes = (ms / (1000 * 60)) % 60
    val hours = ms / (1000 * 60 * 60)
    return if (hours > 0) String.format(java.util.Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    else String.format(java.util.Locale.ROOT, "%d:%02d", minutes, seconds)
}

@OptIn(UnstableApi::class)
@Composable
private fun DownloadsAnimeCard(
    anime: EpisodeDownloadManager.GroupedDownload,
    isOled: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isOled) Color(0xFF111111) else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        if (isOled) Color(0xFF1A1A2E) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = anime.animeName.replace("_", " ").replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${anime.episodes.size} episode${if (anime.episodes.size != 1) "s" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isOled) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF5350))
            }
        }
    }
}


