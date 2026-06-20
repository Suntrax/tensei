package com.blissless.tensei.ui.screens.episode

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.media3.exoplayer.offline.Download
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.blissless.tensei.MainViewModel
import com.blissless.tensei.data.models.AnimeMedia
import com.blissless.tensei.data.models.TmdbEpisode
import com.blissless.tensei.download.EpisodeDownloadManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun EpisodeSelectionDialog(
    anime: AnimeMedia,
    isOled: Boolean,
    disableMaterialColors: Boolean = false,
    onDismiss: () -> Unit,
    onEpisodeSelect: (Int, String?) -> Unit
) {
    val context = LocalContext.current
    val total = anime.totalEpisodes
    val released = anime.latestEpisode ?: total
    val episodeCount = if (total > 0) total else released.coerceAtLeast(1)
    val currentProgress = anime.progress
    val gridState = rememberLazyGridState()
    val imageRequest = remember(anime.cover) {
        ImageRequest.Builder(context).data(anime.cover).memoryCacheKey(anime.cover).diskCacheKey(anime.cover).crossfade(false).build()
    }

    // Auto-scroll to current episode in the simple dialog smoothly
    LaunchedEffect(currentProgress) {
        if (currentProgress > 0) {
            delay(300.milliseconds)
            // Scroll to current episode, keeping it somewhat centered
            val target = (currentProgress - 5).coerceAtLeast(0)
            gridState.animateScrollToItem(target)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth().height(450.dp).padding(16.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = if (isOled) Color.Black else MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    AsyncImage(model = imageRequest, contentDescription = anime.title, contentScale = ContentScale.Crop, modifier = Modifier.width(50.dp).height(70.dp).clip(RoundedCornerShape(8.dp)))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(anime.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Progress: $currentProgress / ${if (total > 0) total else "??"}", style = MaterialTheme.typography.bodySmall, color = if (isOled) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant)
                        anime.year?.let { Text("Released: $it", style = MaterialTheme.typography.bodySmall, color = if (isOled) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)) }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                LazyVerticalGrid(state = gridState, columns = GridCells.Fixed(5), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    items(episodeCount) { index ->
                        val episodeNum = index + 1
                        val isWatched = episodeNum <= currentProgress
                        val isCurrent = episodeNum == currentProgress + 1
                        val hasAired = episodeNum <= released
                        EpisodeButton(
                            episodeNumber = episodeNum,
                            isWatched = isWatched,
                            isCurrent = isCurrent,
                            hasAired = hasAired,
                            isOled = isOled,
                            disableMaterialColors = disableMaterialColors,
                            onClick = {
                                if (hasAired) {
                                    onEpisodeSelect(episodeNum, null)
                                } else {
                                    Toast.makeText(context, "Episode has not aired yet", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    val nextEp = currentProgress + 1
                    if (nextEp <= released) {
                        Button(onClick = { onEpisodeSelect(nextEp, null) }, shape = RoundedCornerShape(10.dp)) { Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(modifier = Modifier.width(4.dp)); Text("Resume Ep $nextEp", color = if (isOled || disableMaterialColors) Color.Black else Color.White) }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }
}

@Composable
private fun EpisodeButton(
    episodeNumber: Int,
    isWatched: Boolean,
    isCurrent: Boolean,
    hasAired: Boolean,
    isOled: Boolean,
    disableMaterialColors: Boolean = false,
    onClick: () -> Unit
) {
    // Current episode now has same badge styling as other aired episodes, but keeps outline indicator
    val backgroundColor = when {
        isWatched -> MaterialTheme.colorScheme.primary
        hasAired -> if (isOled) Color(0xFF1A1A1A) else Color(0xFF2A2A2A)
        else -> if (isOled) Color(0xFF333333) else Color(0xFFD0D0D0) // More visible unaired background
    }
    val contentColor = when {
        isWatched -> if (disableMaterialColors) Color.Black else Color.White
        hasAired -> Color.White
        else -> if (isOled) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.6f)
    }
    // Keep outline indicator for current episode
    val borderColor = when {
        isWatched -> MaterialTheme.colorScheme.primary
        isCurrent -> MaterialTheme.colorScheme.secondary
        hasAired -> Color.White.copy(alpha = 0.1f)
        else -> Color.Gray.copy(alpha = 0.4f)
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor,
        contentColor = contentColor,
        modifier = Modifier.size(48.dp).alpha(if (hasAired) 1f else 0.9f),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("$episodeNumber", style = MaterialTheme.typography.labelLarge, fontWeight = if (isWatched || isCurrent) FontWeight.Bold else FontWeight.Medium)
            if (isWatched) {
                Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.align(Alignment.BottomEnd).size(12.dp).padding(2.dp), tint = contentColor)
            } else if (isCurrent) {
                // Play icon uses primary color to indicate it's the current episode
                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.align(Alignment.BottomEnd).size(12.dp).padding(2.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RichEpisodeScreen(
    anime: AnimeMedia,
    viewModel: MainViewModel,
    isOled: Boolean,
    onDismiss: () -> Unit,
    onEpisodeSelect: (Int, String?) -> Unit,
    onDownloadClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val total = anime.totalEpisodes
    val released = anime.latestEpisode ?: total
    val episodeCount = if (total > 0) total else released.coerceAtLeast(1)
    val currentProgress = anime.progress
    val playbackPositions by viewModel.playbackPositions.collectAsState()
    val playbackDurations by viewModel.playbackDurations.collectAsState()

    var tmdbEpisodes by remember { mutableStateOf<List<TmdbEpisode>>(emptyList()) }
    var isLoadingEpisodes by remember { mutableStateOf(true) }
    var selectedEpisode by remember { mutableStateOf<Int?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val downloadsInfo by viewModel.episodeDownloadManager.downloadsInfo.collectAsState()

    // Extension matching state
    val availableExtensions by viewModel.availableExtensions.collectAsState()
    val defaultPkg by viewModel.defaultExtensionPackage.collectAsState()
    var selectedExtensionPkg by remember { mutableStateOf(defaultPkg.ifEmpty { null }) }
    var extensionEpisodesNumbers by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var hasExtensionData by remember { mutableStateOf(false) }
    var isLoadingExtensionEpisodes by remember { mutableStateOf(false) }

    val sortedExtensions = remember(availableExtensions, selectedExtensionPkg) {
        val selected = selectedExtensionPkg
        availableExtensions.sortedWith(compareBy<Pair<String, String>> { if (it.second == selected) 0 else 1 }.thenBy { it.first })
    }

    // Sync selectedExtensionPkg with default when it becomes available
    LaunchedEffect(defaultPkg) {
        if (defaultPkg.isNotEmpty() && selectedExtensionPkg == null) {
            selectedExtensionPkg = defaultPkg
        }
    }

    // Animation states for entry
    var isVisible by remember { mutableStateOf(false) }
    val slideOffset = remember { Animatable(1000f) }
    val dismissSlideOffset = remember { Animatable(0f) }
    
    LaunchedEffect(Unit) {
        slideOffset.animateTo(
            targetValue = 0f,
            animationSpec = tween(200, easing = LinearEasing)
        )
    }
    
    fun dismissWithAnimation() {
        scope.launch {
            dismissSlideOffset.snapTo(0f)
            dismissSlideOffset.animateTo(
                targetValue = 1000f,
                animationSpec = tween(150, easing = LinearEasing)
            )
            onDismiss()
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (slideOffset.value > 0 || dismissSlideOffset.value > 0) 0f else 1f,
        animationSpec = tween(durationMillis = 200, easing = LinearEasing),
        label = "alpha"
    )

    LaunchedEffect(Unit) {
        isVisible = true
    }

    // Filter episodes to match the AniList progress count and extension availability
    val displayEpisodes = remember(tmdbEpisodes, episodeCount, extensionEpisodesNumbers, hasExtensionData) {
        val base = tmdbEpisodes.filter { it.episode <= episodeCount }
        if (hasExtensionData) {
            base.filter { it.episode in extensionEpisodesNumbers }
        } else {
            base
        }
    }

    val availableEpisodeNumbers = remember(episodeCount, extensionEpisodesNumbers, hasExtensionData) {
        if (hasExtensionData) {
            (1..episodeCount).filter { it in extensionEpisodesNumbers }
        } else {
            (1..episodeCount).toList()
        }
    }

    val windowInfo = LocalWindowInfo.current
    val containerSize = windowInfo.containerSize
    val screenHeightPx = containerSize.height.toFloat()
    val dismissThreshold = screenHeightPx / 2f

    val offsetY = remember { Animatable(0f) }

    val isAtTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 &&
                    listState.firstVisibleItemScrollOffset == 0
        }
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val currentOffset = offsetY.value

                if (currentOffset > 0) {
                    if (available.y < 0) {
                        scope.launch {
                            offsetY.snapTo((currentOffset + available.y).coerceAtLeast(0f))
                        }
                        return available
                    }
                    if (available.y > 0) {
                        scope.launch {
                            offsetY.snapTo(currentOffset + available.y)
                        }
                        return available
                    }
                }

                if (isAtTop && currentOffset == 0f && available.y > 0) {
                    scope.launch { offsetY.snapTo(available.y) }
                    return available
                }

                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val currentOffset = offsetY.value

                if (currentOffset == 0f) return Velocity.Zero

                val shouldDismiss = currentOffset > dismissThreshold || available.y > 2000f

                if (shouldDismiss) {
                    dismissWithAnimation()
                } else {
                    scope.launch {
                        offsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                    }
                }

                return available
            }
        }
    }

    suspend fun handleDragEnd() {
        val currentOffset = offsetY.value
        if (currentOffset == 0f) return

        val shouldDismiss = currentOffset > dismissThreshold
        if (shouldDismiss) {
            dismissWithAnimation()
        } else {
            offsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
        }
    }

    // Fetch TMDB episodes when screen opens
    LaunchedEffect(anime.id) {
        val cached = viewModel.getCachedTmdbEpisodes(anime.id, anime.status)
        if (cached != null) {
            tmdbEpisodes = cached
            isLoadingEpisodes = false
        } else {
            try {
                val episodes = viewModel.fetchTmdbEpisodes(anime.title, anime.id, anime.year, anime.format)
                viewModel.cacheTmdbEpisodes(anime.id, episodes)
                tmdbEpisodes = episodes
            } catch (_: Exception) {}
            isLoadingEpisodes = false
        }
    }

    // Load available extensions and pre-fetch extension episodes for faster playback
    LaunchedEffect(anime.id) {
        viewModel.loadAvailableExtensions()
    }

    // Scroll to current episode (next to watch or last watched) with smooth animation
    LaunchedEffect(currentProgress, episodeCount, isLoadingEpisodes) {
        if (!isLoadingEpisodes && currentProgress > 0) {
            delay(300.milliseconds)
            val scrollIndex = if (currentProgress < episodeCount) currentProgress else currentProgress - 1
            listState.animateScrollToItem(scrollIndex + 1)
        }
    }

    // Watch for extension episode numbers
    val preFetchedNumbers by viewModel.preFetchedEpisodeNumbers.collectAsState()
    LaunchedEffect(preFetchedNumbers, anime.id) {
        val numbers = preFetchedNumbers[anime.id]
        if (numbers != null) {
            extensionEpisodesNumbers = numbers
            hasExtensionData = true
            isLoadingExtensionEpisodes = false
        }
    }

    // Re-fetch when user selects a specific extension
    LaunchedEffect(selectedExtensionPkg) {
        if (selectedExtensionPkg != null) {
            isLoadingExtensionEpisodes = true
            viewModel.preFetchExtensionEpisodes(anime, selectedExtensionPkg)
        } else {
            extensionEpisodesNumbers = emptySet()
            hasExtensionData = false
            isLoadingExtensionEpisodes = false
        }
    }

    val statusBarsPadding = WindowInsets.statusBars.asPaddingValues()
    val navigationBarsPadding = WindowInsets.navigationBars.asPaddingValues()

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnBackPress = true, dismissOnClickOutside = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, (slideOffset.value + dismissSlideOffset.value).roundToInt()) }
                .graphicsLayer {
                    this.alpha = alpha
                }
                .offset { IntOffset(0, offsetY.value.roundToInt()) }
                .background(if (isOled) Color.Black else MaterialTheme.colorScheme.background)
                .nestedScroll(nestedScrollConnection)
        ) {
            // Compact banner overlay with anime info
            val hasBanner = !anime.banner.isNullOrEmpty() || anime.cover.isNotEmpty()
            if (hasBanner) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragEnd = { scope.launch { handleDragEnd() } },
                                onVerticalDrag = { _, dragAmount ->
                                    scope.launch {
                                        val multiplier = 3.0f
                                        offsetY.snapTo((offsetY.value + (dragAmount * multiplier)).coerceAtLeast(0f))
                                    }
                                }
                            )
                        }
                ) {
                    AsyncImage(
                        model = anime.banner.takeIf { !it.isNullOrEmpty() } ?: anime.cover,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(modifier = Modifier.fillMaxWidth().height(170.dp).background(Brush.verticalGradient(colors = listOf(Color.Transparent, if (isOled) Color.Black else MaterialTheme.colorScheme.background))))
                    Row(
                        modifier = Modifier.align(Alignment.BottomStart).padding(start = 14.dp, end = 14.dp, bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = anime.cover,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.width(40.dp).height(56.dp).clip(RoundedCornerShape(6.dp))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = anime.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, color = Color.White)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(text = "Progress: $currentProgress / ${if (total > 0) total else "??"}", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
                            if (released > 0) {
                                Text(text = if (released == 1) "$released episode" else "$released episodes", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                            }
                        }
                    }
                }
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = statusBarsPadding.calculateTopPadding() + 12.dp, start = 16.dp)
                    .size(40.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    .zIndex(10f)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(24.dp))
            }

            if (onDownloadClick != null) {
                IconButton(
                    onClick = onDownloadClick,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = statusBarsPadding.calculateTopPadding() + 12.dp, end = 16.dp)
                        .size(40.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        .zIndex(10f)
                ) {
                    Icon(Icons.Default.Download, contentDescription = "Download", tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }

            Box(modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = statusBarsPadding.calculateTopPadding() + 12.dp)
                .width(36.dp).height(4.dp)
                .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(2.dp)).zIndex(5f))

            if (isLoadingEpisodes) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = statusBarsPadding.calculateTopPadding() + 64.dp).zIndex(10f),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Scrollable content: chips + episodes in one LazyColumn
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(
                    top = (if (hasBanner) 150.dp + statusBarsPadding.calculateTopPadding() else statusBarsPadding.calculateTopPadding()),
                    bottom = navigationBarsPadding.calculateBottomPadding()
                ),
                state = listState
            ) {
                // Header section (scrolls away)
                item {
                    Column {
                        Spacer(modifier = Modifier.height(8.dp))
                        // Extension selector
                        if (availableExtensions.isNotEmpty()) {
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("Source:", style = MaterialTheme.typography.labelSmall, color = if (isOled) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant)
                                sortedExtensions.forEach { (extName, extPkg) ->
                                    FilterChip(
                                        selected = extPkg == selectedExtensionPkg,
                                        onClick = { if (extPkg != selectedExtensionPkg) selectedExtensionPkg = extPkg },
                                        label = { Text(extName, maxLines = 1, style = MaterialTheme.typography.labelSmall) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                            containerColor = if (isOled) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surface,
                                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        // Navigation chips
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val nextEp = currentProgress + 1
                            if (nextEp <= released) {
                                FilterChip(
                                    selected = true,
                                    onClick = { onEpisodeSelect(nextEp, null) },
                                    label = { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.PlayArrow, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Resume Ep $nextEp") } },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        containerColor = if (isOled) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surface,
                                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                            FilterChip(
                                selected = false,
                                onClick = { scope.launch { listState.animateScrollToItem(1) } },
                                label = { Text("Ep 1") },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = if (isOled) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surface,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                            if (released > 1) {
                                FilterChip(
                                    selected = false,
                                    onClick = { scope.launch { listState.animateScrollToItem(released) } },
                                    label = { Text("Latest: Ep $released") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = if (isOled) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surface,
                                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = if (isOled) Color.White.copy(alpha = 0.1f) else MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                    }
                }

                // Episode cards
                if (displayEpisodes.isNotEmpty()) {
                    items(displayEpisodes.size) { index ->
                        val ep = displayEpisodes[index]
                        val episodeNum = ep.episode
                        val isWatched = episodeNum <= currentProgress
                        val isCurrent = episodeNum == currentProgress + 1
                        val hasAired = episodeNum <= released
                        val downloadInfo = downloadsInfo["${anime.id}_$episodeNum"]
                        RichTmdbEpisodeCard(
                            episodeNumber = episodeNum,
                            title = ep.title,
                            description = ep.description,
                            image = ep.image,
                            isWatched = isWatched,
                            isCurrent = isCurrent,
                            hasAired = hasAired,
                            isOled = isOled,
                            isSelected = selectedEpisode == episodeNum,
                            playbackPositions = playbackPositions,
                            playbackDurations = playbackDurations,
                            animeId = anime.id,
                            downloadInfo = downloadInfo,
                            onSelect = { selectedEpisode = episodeNum },
                            onPlay = {
                                if (hasAired) {
                                    val title = if (ep.title.isNotEmpty() && !ep.title.startsWith("Episode", ignoreCase = true)) ep.title else "Episode $episodeNum"
                                    onEpisodeSelect(episodeNum, title)
                                } else {
                                    Toast.makeText(context, "Episode not aired yet", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                } else if (!isLoadingEpisodes) {
                    items(availableEpisodeNumbers.size) { index ->
                        val episodeNum = availableEpisodeNumbers[index]
                        val isWatched = episodeNum <= currentProgress
                        val isCurrent = episodeNum == currentProgress + 1
                        val hasAired = episodeNum <= released
                        SimpleRichEpisodeCard(
                            episodeNumber = episodeNum,
                            isWatched = isWatched,
                            isCurrent = isCurrent,
                            hasAired = hasAired,
                            isOled = isOled,
                            isSelected = selectedEpisode == episodeNum,
                            playbackPositions = playbackPositions,
                            playbackDurations = playbackDurations,
                            animeId = anime.id,
                            onSelect = { selectedEpisode = episodeNum },
                            onPlay = {
                                if (hasAired) {
                                    onEpisodeSelect(episodeNum, "Episode $episodeNum")
                                } else {
                                    Toast.makeText(context, "Episode not aired yet", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun SimpleRichEpisodeCard(
    episodeNumber: Int,
    isWatched: Boolean,
    isCurrent: Boolean,
    hasAired: Boolean,
    isOled: Boolean,
    isSelected: Boolean,
    playbackPositions: Map<String, Long> = emptyMap(),
    playbackDurations: Map<String, Long> = emptyMap(),
    animeId: Int = 0,
    onSelect: () -> Unit,
    onPlay: () -> Unit
) {
    val context = LocalContext.current
    // Current episode has same background as other aired episodes
    val backgroundColor = when {
        isWatched -> if (isOled) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        hasAired -> if (isOled) Color(0xFF121212) else MaterialTheme.colorScheme.surface
        else -> Color.Transparent
    }

    // Keep outline indicator for current episode
    val borderColor = when {
        isCurrent -> MaterialTheme.colorScheme.secondary
        isSelected -> MaterialTheme.colorScheme.secondary
        else -> Color.Transparent
    }

    val contentAlpha = if (hasAired) 1f else 0.4f

    // Consistent dark style for badges and icons
    val badgeBg = Color.Black.copy(alpha = 0.7f)
    val badgeText = Color.White

    AnimatedVisibility(visible = true, enter = fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.95f), exit = fadeOut(tween(200))) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).then(if (borderColor != Color.Transparent) Modifier.border(1.dp, borderColor, RoundedCornerShape(12.dp)) else Modifier),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = backgroundColor),
            onClick = {
                if (hasAired) {
                    onSelect()
                    onPlay()
                } else {
                    Toast.makeText(context, "Episode has not aired yet", Toast.LENGTH_SHORT).show()
                }
            }
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp).alpha(contentAlpha),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(44.dp).background(badgeBg, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            isWatched -> Icon(Icons.Default.Check, contentDescription = "Watched", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            isCurrent -> Icon(Icons.Default.PlayArrow, contentDescription = "Current", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            else -> Text(
                                text = "$episodeNumber",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = badgeText
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Episode $episodeNumber",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = when {
                                !hasAired -> "Not yet aired"
                                isCurrent -> "Up next"
                                isWatched -> "Watched"
                                else -> "Available"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = when {
                                !hasAired -> Color.Gray
                                isCurrent -> MaterialTheme.colorScheme.primary
                                isWatched -> MaterialTheme.colorScheme.primary
                                else -> if (isOled) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    if (hasAired) {
                        FilledTonalIconButton(
                            onClick = onPlay,
                            modifier = Modifier.size(40.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Play", modifier = Modifier.size(20.dp))
                        }
                    }
                }
                // Progress bar and remaining time
                val epPlaybackKey = "${animeId}_$episodeNumber"
                val savedPos = playbackPositions[epPlaybackKey] ?: 0L
                val epDuration = playbackDurations[epPlaybackKey] ?: 0L
                val progressRatio = if (savedPos > 0 && epDuration > 0) (savedPos.toFloat() / epDuration).coerceIn(0f, 1f) else 0f
                val remainingText = if (savedPos in 1..<epDuration) {
                    val remaining = epDuration - savedPos
                    val mins = (remaining / 60000).toInt()
                    val secs = ((remaining % 60000) / 1000).toInt()
                    "${mins}:${"%02d".format(secs)} left"
                } else null
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
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RichTmdbEpisodeCard(
    episodeNumber: Int,
    title: String?,
    description: String?,
    image: String?,
    isWatched: Boolean,
    isCurrent: Boolean,
    hasAired: Boolean,
    isOled: Boolean,
    isSelected: Boolean,
    playbackPositions: Map<String, Long> = emptyMap(),
    playbackDurations: Map<String, Long> = emptyMap(),
    animeId: Int = 0,
    downloadInfo: EpisodeDownloadManager.DownloadInfo? = null,
    onSelect: () -> Unit,
    onPlay: () -> Unit
) {
    val context = LocalContext.current
    // Current episode has same background as other aired episodes
    val backgroundColor = when {
        isWatched -> if (isOled) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        hasAired -> if (isOled) Color(0xFF121212) else MaterialTheme.colorScheme.surface
        else -> Color.Transparent
    }

    // Keep outline indicator for current episode
    val borderColor = when {
        isCurrent -> MaterialTheme.colorScheme.secondary
        isSelected -> MaterialTheme.colorScheme.secondary
        else -> Color.Transparent
    }

    val contentAlpha = if (hasAired) 1f else 0.4f

    // Consistent dark style for badges and icons in rich menu
    val badgeBg = Color.Black.copy(alpha = 0.7f)
    val badgeText = Color.White

    AnimatedVisibility(visible = true, enter = fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.95f), exit = fadeOut(tween(200))) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).then(if (borderColor != Color.Transparent) Modifier.border(1.dp, borderColor, RoundedCornerShape(12.dp)) else Modifier),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = backgroundColor),
            onClick = {
                if (hasAired) {
                    onSelect()
                    onPlay()
                } else {
                    Toast.makeText(context, "Episode has not aired yet", Toast.LENGTH_SHORT).show()
                }
            }
        ) {
            Column(modifier = Modifier.fillMaxWidth().alpha(contentAlpha)) {
                if (!image.isNullOrEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(180.dp)) {
                        AsyncImage(model = image, contentDescription = "Episode $episodeNumber", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        Box(
                            modifier = Modifier.fillMaxWidth().height(60.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                        )
                        // Episode Badge - Standardized Dark Style
                        Surface(
                            modifier = Modifier.padding(8.dp).align(Alignment.TopStart),
                            shape = RoundedCornerShape(6.dp),
                            color = badgeBg
                        ) {
                            Text(
                                text = "EP $episodeNumber",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = badgeText,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        // Watched Icon - Standardized Dark Style with primary color check
                        if (isWatched) {
                            Surface(
                                modifier = Modifier.padding(8.dp).align(Alignment.TopEnd),
                                shape = CircleShape,
                                color = badgeBg
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Watched",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(4.dp).size(16.dp)
                                )
                            }
                        }
                        if (hasAired) {
                            FilledTonalIconButton(
                                onClick = onPlay,
                                modifier = Modifier.align(Alignment.Center).size(56.dp),
                                shape = CircleShape,
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = Color.Black.copy(alpha = 0.6f),
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Play", modifier = Modifier.size(32.dp))
                            }
                        }
                    }
                }
                // Progress bar and remaining time between image and info
                val epPlaybackKey = "${animeId}_$episodeNumber"
                val savedPos = playbackPositions[epPlaybackKey] ?: 0L
                val epDuration = playbackDurations[epPlaybackKey] ?: 0L
                val progressRatio = if (savedPos > 0 && epDuration > 0) (savedPos.toFloat() / epDuration).coerceIn(0f, 1f) else 0f
                val remainingText = if (savedPos in 1..<epDuration) {
                    val remaining = epDuration - savedPos
                    val mins = (remaining / 60000).toInt()
                    val secs = ((remaining % 60000) / 1000).toInt()
                    "${mins}:${"%02d".format(secs)} left"
                } else null
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
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (image.isNullOrEmpty()) {
                            Box(
                                modifier = Modifier.size(44.dp).background(badgeBg, RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                when {
                                    isWatched -> Icon(Icons.Default.Check, contentDescription = "Watched", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                    isCurrent -> Icon(Icons.Default.PlayArrow, contentDescription = "Current", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                    else -> Text(
                                        text = "$episodeNumber",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = badgeText
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title?.ifEmpty { "Episode $episodeNumber" } ?: "Episode $episodeNumber",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = when {
                                    !hasAired -> "Not yet aired"
                                    isCurrent -> "Up next"
                                    isWatched -> "Watched"
                                    else -> "Available"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = when {
                                    !hasAired -> Color.Gray
                                    isCurrent -> MaterialTheme.colorScheme.primary
                                    isWatched -> MaterialTheme.colorScheme.primary
                                    else -> if (isOled) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        if (image.isNullOrEmpty() && hasAired) {
                            FilledTonalIconButton(
                                onClick = onPlay,
                                modifier = Modifier.size(40.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Play", modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                    if (!description.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isOled) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (downloadInfo != null && downloadInfo.state == Download.STATE_COMPLETED) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (downloadInfo.category == "dub") "DUB" else "SUB",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isOled) Color.White.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "·",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isOled) Color.White.copy(alpha = 0.25f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Text(
                                text = formatDownloadTimestamp(downloadInfo.downloadTimestamp),
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

private fun formatDownloadTimestamp(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}

private fun formatTimeFromMs(ms: Long): String {
    val seconds = (ms / 1000) % 60
    val minutes = (ms / (1000 * 60)) % 60
    val hours = ms / (1000 * 60 * 60)
    return if (hours > 0) String.format(java.util.Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    else String.format(java.util.Locale.ROOT, "%d:%02d", minutes, seconds)
}


