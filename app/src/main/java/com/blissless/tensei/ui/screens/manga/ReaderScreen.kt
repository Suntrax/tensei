package com.blissless.tensei.ui.screens.manga

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.blissless.tensei.MainViewModel
import com.blissless.tensei.data.models.MangaChapter
import com.blissless.tensei.data.models.MangaMedia
import com.blissless.tensei.viewmodel.loadChapterImages
import com.blissless.tensei.viewmodel.loadMangaChapters
import com.blissless.tensei.viewmodel.mangaChapterImages
import com.blissless.tensei.viewmodel.mangaChapters
import com.blissless.tensei.viewmodel.markMangaChapterRead
import com.blissless.tensei.viewmodel.setMangaReaderMode
import com.blissless.tensei.viewmodel.updateMangaScrollProgress
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class ReaderMode { VERTICAL_SCROLL, LEFT_TO_RIGHT, RIGHT_TO_LEFT }

@Composable
fun MangaReaderScreen(
    manga: MangaMedia,
    initialChapterIndex: Int,
    viewModel: MainViewModel,
    isOled: Boolean,
    onClose: () -> Unit
) {
    val chapters by viewModel.mangaChapters.collectAsState()
    val chapterImages by viewModel.mangaChapterImages.collectAsState()
    var currentChapterIndex by remember { mutableIntStateOf(initialChapterIndex.coerceAtLeast(0)) }
    var readerMode by remember { mutableStateOf(
        when (viewModel.mangaReaderMode.value) {
            "left_to_right" -> ReaderMode.LEFT_TO_RIGHT
            "right_to_left" -> ReaderMode.RIGHT_TO_LEFT
            else -> ReaderMode.VERTICAL_SCROLL
        }
    ) }
    var showControls by remember { mutableStateOf(false) }
    var showChapterList by remember { mutableStateOf(initialChapterIndex < 0) }
    var scrollProgress by remember { mutableFloatStateOf(0f) }
    val readIndices = remember { mutableStateOf((0 until manga.progress.coerceAtLeast(0)).toSet()) }

    val currentChapter = chapters.getOrNull(currentChapterIndex)
    val isFirstChapter = currentChapterIndex == 0
    val isLastChapter = currentChapterIndex >= chapters.lastIndex
    val useDataSaver = viewModel.mangaDataSaver.value

    LaunchedEffect(currentChapter, showChapterList) {
        if (!showChapterList) {
            currentChapter?.let { chapter ->
                viewModel.loadChapterImages(chapter.chapterId, useDataSaver, manga.title)
            }
        }
    }

    // Auto-load chapters if the list is empty (e.g. user jumped straight here)
    LaunchedEffect(chapters, showChapterList) {
        if (chapters.isEmpty() && showChapterList) {
            viewModel.loadMangaChapters(manga.id, manga.title)
        }
    }

    fun selectChapter(index: Int) {
        val chapter = chapters.getOrNull(index) ?: return
        viewModel.markMangaChapterRead(manga.id, chapter)
        readIndices.value = readIndices.value + index
        currentChapterIndex = index
        showChapterList = false
        showControls = false
    }

    Box(modifier = Modifier.fillMaxSize().background(if (isOled) Color.Black else Color(0xFF1a1a1a))) {
        when {
            showChapterList -> {
                MangaChapterListWithGroups(
                    chapters = chapters,
                    readIndices = readIndices.value,
                    nextChapterToRead = manga.progress.coerceAtLeast(0),
                    onChapterClick = { selectChapter(it) },
                    onContinueReading = {
                        val next = manga.progress.coerceAtLeast(0)
                        if (next in chapters.indices) selectChapter(next)
                    },
                    onBack = onClose
                )
            }

            readerMode == ReaderMode.VERTICAL_SCROLL -> {
                VerticalScrollReader(
                    chapterImages = chapterImages ?: emptyList(),
                    chapter = currentChapter,
                    totalChapters = chapters.size,
                    currentIndex = currentChapterIndex,
                    scrollProgress = scrollProgress,
                    showControls = showControls,
                    onScrollProgress = { viewModel.updateMangaScrollProgress(manga.id, it); scrollProgress = it },
                    onToggleControls = { showControls = !showControls },
                    onPrevChapter = { if (!isFirstChapter) selectChapter(currentChapterIndex - 1) },
                    onNextChapter = { if (!isLastChapter) selectChapter(currentChapterIndex + 1) },
                    isFirstChapter = isFirstChapter,
                    isLastChapter = isLastChapter
                )
            }

            readerMode == ReaderMode.LEFT_TO_RIGHT || readerMode == ReaderMode.RIGHT_TO_LEFT -> {
                PagedMangaReader(
                    chapterImages = chapterImages ?: emptyList(),
                    chapter = currentChapter,
                    mode = readerMode,
                    showControls = showControls,
                    onToggleControls = { showControls = !showControls },
                    onPrevChapter = { if (!isFirstChapter) selectChapter(currentChapterIndex - 1) },
                    onNextChapter = { if (!isLastChapter) selectChapter(currentChapterIndex + 1) },
                    isFirstChapter = isFirstChapter,
                    isLastChapter = isLastChapter
                )
            }
        }

        // ─── Overlay: top bar (only when reading) ─────────────────────
        AnimatedVisibility(
            visible = showControls && !showChapterList,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(150)),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Column {
                Surface(
                    color = Color.Black.copy(alpha = 0.95f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onClose) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = manga.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (currentChapter != null) {
                                Text(
                                    text = currentChapter.title,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.7f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        IconButton(
                            onClick = { if (currentChapterIndex > 0) selectChapter(currentChapterIndex - 1) },
                            enabled = currentChapterIndex > 0
                        ) {
                            Icon(
                                Icons.Default.SkipPrevious,
                                contentDescription = "Previous Chapter",
                                tint = if (currentChapterIndex > 0) Color.White else Color.White.copy(alpha = 0.3f)
                            )
                        }
                        IconButton(
                            onClick = { if (currentChapterIndex < chapters.lastIndex) selectChapter(currentChapterIndex + 1) },
                            enabled = currentChapterIndex < chapters.lastIndex
                        ) {
                            Icon(
                                Icons.Default.SkipNext,
                                contentDescription = "Next Chapter",
                                tint = if (currentChapterIndex < chapters.lastIndex) Color.White else Color.White.copy(alpha = 0.3f)
                            )
                        }
                        IconButton(onClick = { showChapterList = true }) {
                            Icon(Icons.Default.List, contentDescription = "Chapters", tint = Color.White)
                        }
                    }
                }
                Box(modifier = Modifier.fillMaxWidth().height(3.dp).background(Color.Black.copy(alpha = 0.95f))) {
                    Box(modifier = Modifier.fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.2f)))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(scrollProgress.coerceIn(0f, 1f))
                            .height(3.dp)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }

        // ─── Overlay: page indicator (bottom-right) ───────────────────
        if (showControls && !showChapterList && chapterImages != null) {
            val total = chapterImages?.size ?: 0
            if (total > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp, bottom = 64.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${currentChapterIndex + 1}/$total",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White
                    )
                }
            }
        }

        // ─── Overlay: bottom bar (reader mode toggle) ────────────────
        AnimatedVisibility(
            visible = showControls && !showChapterList,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(150)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .background(Color.Black.copy(alpha = 0.95f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ReaderModeSegmentedToggle(
                    currentMode = readerMode,
                    onSelect = { mode ->
                        readerMode = mode
                        viewModel.setMangaReaderMode(
                            when (mode) {
                                ReaderMode.VERTICAL_SCROLL -> "vertical_scroll"
                                ReaderMode.LEFT_TO_RIGHT -> "left_to_right"
                                ReaderMode.RIGHT_TO_LEFT -> "right_to_left"
                            }
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun ReaderModeSegmentedToggle(
    currentMode: ReaderMode,
    onSelect: (ReaderMode) -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.1f))
            .padding(3.dp)
    ) {
        ReaderModeButton(ReaderMode.VERTICAL_SCROLL, "Scroll", currentMode, onSelect)
        ReaderModeButton(ReaderMode.LEFT_TO_RIGHT, "LTR", currentMode, onSelect)
        ReaderModeButton(ReaderMode.RIGHT_TO_LEFT, "RTL", currentMode, onSelect)
    }
}

@Composable
private fun ReaderModeButton(
    mode: ReaderMode,
    label: String,
    currentMode: ReaderMode,
    onSelect: (ReaderMode) -> Unit
) {
    val isSelected = mode == currentMode
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable { onSelect(mode) }
            .padding(horizontal = 16.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.White.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun VerticalScrollReader(
    chapterImages: List<String>,
    chapter: MangaChapter?,
    totalChapters: Int,
    currentIndex: Int,
    scrollProgress: Float,
    showControls: Boolean,
    onScrollProgress: (Float) -> Unit,
    onToggleControls: () -> Unit,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
    isFirstChapter: Boolean,
    isLastChapter: Boolean
) {
    val listState = rememberLazyListState()

    LaunchedEffect(chapter) {
        listState.scrollToItem(0)
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            if (layoutInfo.totalItemsCount > 0 && layoutInfo.visibleItemsInfo.isNotEmpty()) {
                val firstVisible = layoutInfo.visibleItemsInfo.first()
                val progress = (firstVisible.index + firstVisible.offset.toFloat() / firstVisible.size) / layoutInfo.totalItemsCount
                progress.coerceIn(0f, 1f)
            } else 0f
        }.collect { onScrollProgress(it) }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        itemsIndexed(chapterImages) { index, imageUrl ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTapGestures { onToggleControls() }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (imageUrl.isNotEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(imageUrl)
                            .crossfade(true)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .build(),
                        contentDescription = "Page ${index + 1}",
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    onClick = onPrevChapter,
                    enabled = !isFirstChapter
                ) { Text("Previous Chapter") }

                TextButton(
                    onClick = onNextChapter,
                    enabled = !isLastChapter
                ) { Text("Next Chapter") }
            }
        }
    }
}

@Composable
private fun PagedMangaReader(
    chapterImages: List<String>,
    chapter: MangaChapter?,
    mode: ReaderMode,
    showControls: Boolean,
    onToggleControls: () -> Unit,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
    isFirstChapter: Boolean,
    isLastChapter: Boolean
) {
    val isRtl = mode == ReaderMode.RIGHT_TO_LEFT
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { chapterImages.size }
    )

    LaunchedEffect(chapter) {
        pagerState.scrollToPage(0)
    }

    if (chapterImages.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
        return
    }

    HorizontalPager(
        state = pagerState,
        reverseLayout = isRtl,
        modifier = Modifier.fillMaxSize()
    ) { page ->
        val displayPage = if (isRtl) chapterImages.lastIndex - page else page
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val width = size.width
                        when {
                            offset.x < width * 0.3f -> {
                                if (isRtl) {
                                    if (page > 0) { }
                                    else if (!isLastChapter) onNextChapter()
                                    else onToggleControls()
                                } else {
                                    if (page > 0) { }
                                    else if (!isFirstChapter) onPrevChapter()
                                    else onToggleControls()
                                }
                            }
                            offset.x > width * 0.7f -> {
                                if (isRtl) {
                                    if (page < pagerState.pageCount - 1) { }
                                    else if (!isFirstChapter) onPrevChapter()
                                    else onToggleControls()
                                } else {
                                    if (page < pagerState.pageCount - 1) { }
                                    else if (!isLastChapter) onNextChapter()
                                    else onToggleControls()
                                }
                            }
                            else -> onToggleControls()
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(chapterImages.getOrNull(displayPage) ?: "")
                    .crossfade(true)
                    .build(),
                contentDescription = "Page ${displayPage + 1}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
    }
}

// ─── Chapter list (Oni-style) ──────────────────────────────────────────

@Composable
fun MangaChapterListWithGroups(
    chapters: List<MangaChapter>,
    readIndices: Set<Int>,
    nextChapterToRead: Int,
    onChapterClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onContinueReading: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null
) {
    if (chapters.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Loading chapters...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
            }
        }
    } else {
        var searchQuery by remember { mutableStateOf("") }
        val groupedChapters = remember(chapters) { groupChaptersByMainChapter(chapters) }

        val filteredGroups = remember(groupedChapters, searchQuery) {
            if (searchQuery.isBlank()) groupedChapters
            else groupedChapters.mapNotNull { (key, list) ->
                val filtered = list.filter { (_, chapter) ->
                    chapter.title.contains(searchQuery, ignoreCase = true)
                }
                if (filtered.isNotEmpty()) key to filtered else null
            }
        }

        val listState = rememberLazyListState()
        val integerChapterCount = chapters.count { ch ->
            val num = ch.title.removePrefix("Chapter ").trim().toFloatOrNull()
            num != null && num == num.toInt().toFloat()
        }
        val readCount = readIndices.size
        val totalCount = integerChapterCount.coerceAtLeast(chapters.size)
        val progress = if (totalCount > 0) readCount.toFloat() / totalCount else 0f
        val statusBarPadding = WindowInsets.statusBars.asPaddingValues()

        LaunchedEffect(chapters, nextChapterToRead) {
            if (nextChapterToRead >= 0) {
                val targetGroupIndex = filteredGroups.indexOfFirst { (_, groupList) ->
                    groupList.any { it.first == nextChapterToRead }
                }
                if (targetGroupIndex >= 0) {
                    delay(100)
                    listState.animateScrollToItem(maxOf(0, targetGroupIndex + 2))
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = statusBarPadding.calculateTopPadding() + 12.dp,
                bottom = statusBarPadding.calculateBottomPadding() + 12.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "header") {
                MangaChapterListHeader(
                    readCount = readCount,
                    totalCount = totalCount,
                    progress = progress,
                    nextChapterToRead = nextChapterToRead,
                    chapters = chapters,
                    onContinueReading = onContinueReading ?: {
                        if (nextChapterToRead in chapters.indices) onChapterClick(nextChapterToRead)
                    },
                    onBack = onBack
                )
            }

            item(key = "search") {
                MangaChapterSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it }
                )
            }

            if (filteredGroups.isEmpty() && searchQuery.isNotBlank()) {
                item(key = "no_results") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No chapters match \"$searchQuery\"",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                filteredGroups.forEachIndexed { index, (groupKey, groupList) ->
                    val containsTarget = groupList.any { it.first == nextChapterToRead }
                    item(key = "chapter_group_$index") {
                        MangaChapterGroup(
                            groupKey = groupKey,
                            groupChapters = groupList,
                            readIndices = readIndices,
                            nextChapterToRead = nextChapterToRead,
                            initiallyExpanded = containsTarget,
                            onChapterClick = onChapterClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MangaChapterListHeader(
    readCount: Int,
    totalCount: Int,
    progress: Float,
    nextChapterToRead: Int,
    chapters: List<MangaChapter>,
    onContinueReading: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
                Column {
                    Text(
                        text = "Chapters",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "$readCount of $totalCount read",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.2.sp
                    )
                }
            }

            if (progress > 0f) {
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary
                            )
                        ),
                        RoundedCornerShape(2.dp)
                    )
            )
        }

        if (nextChapterToRead < totalCount) {
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onContinueReading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Continue Reading",
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.3.sp
                )
                Spacer(modifier = Modifier.width(4.dp))
                val nextChapterDisplay = chapters.getOrNull(nextChapterToRead)?.let { extractChapterNum(it.title) }
                    ?: "${nextChapterToRead + 1}"
                Text(
                    text = "\u00B7 Ch. $nextChapterDisplay",
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun MangaChapterSearchBar(
    query: String,
    onQueryChange: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .then(
                if (query.isNotEmpty()) Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))

            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 14.sp
                ),
                decorationBox = { innerTextField ->
                    Box {
                        if (query.isEmpty()) {
                            Text(
                                "Search chapters...",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp
                            )
                        }
                        innerTextField()
                    }
                }
            )

            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MangaChapterGroup(
    groupKey: String,
    groupChapters: List<Pair<Int, MangaChapter>>,
    readIndices: Set<Int>,
    nextChapterToRead: Int,
    initiallyExpanded: Boolean = false,
    onChapterClick: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    LaunchedEffect(initiallyExpanded) {
        if (initiallyExpanded) expanded = true
    }

    val rotationAngle by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(250),
        label = "rotation"
    )

    val readInGroup = groupChapters.count { (index, _) -> index in readIndices }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { expanded = !expanded },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = groupKey,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.3.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${groupChapters.size} chapters",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (readInGroup > 0) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "\u00B7 $readInGroup read",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF34D399).copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${groupChapters.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.graphicsLayer { rotationZ = rotationAngle }
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = tween(250)) + fadeIn(animationSpec = tween(250)),
                exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(animationSpec = tween(150))
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    groupChapters.forEach { (absoluteIndex, chapter) ->
                        MangaChapterRow(
                            chapter = chapter,
                            isRead = absoluteIndex in readIndices,
                            isNextToRead = absoluteIndex == nextChapterToRead,
                            onClick = { onChapterClick(absoluteIndex) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MangaChapterRow(
    chapter: MangaChapter,
    isRead: Boolean,
    isNextToRead: Boolean,
    onClick: () -> Unit
) {
    val accentColor = when {
        isNextToRead && !isRead -> MaterialTheme.colorScheme.primaryContainer
        isRead -> Color(0xFF34D399)
        else -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(start = 0.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(32.dp)
                .background(accentColor, RoundedCornerShape(2.dp))
        )

        Spacer(modifier = Modifier.width(14.dp))

        val chNum = extractChapterNum(chapter.title)
        Text(
            text = "Ch. $chNum",
            style = MaterialTheme.typography.bodyMedium,
            color = if (isRead) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onBackground,
            fontWeight = if (isRead) FontWeight.Normal else FontWeight.Medium
        )

        Spacer(modifier = Modifier.weight(1f))

        if (isRead) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = "Read",
                tint = Color(0xFF34D399),
                modifier = Modifier.size(18.dp)
            )
        } else if (isNextToRead) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = "Next",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ─── Chapter grouping helpers (Oni-style) ─────────────────────────────

private fun groupChaptersByMainChapter(chapters: List<MangaChapter>): List<Pair<String, List<Pair<Int, MangaChapter>>>> {
    val indexedChapters = chapters.mapIndexed { index, chapter -> index to chapter }

    val byMainChapter = indexedChapters.groupBy { (_, chapter) ->
        extractMainChapterNumber(chapter.title)
    }.toSortedMap()

    val result = mutableListOf<Pair<String, List<Pair<Int, MangaChapter>>>>()
    val hasChapterZero = byMainChapter.containsKey(0)

    byMainChapter.forEach { (mainChapter, items) ->
        val rangeStart = if (hasChapterZero && mainChapter == 0) {
            0
        } else {
            ((mainChapter - 1) / 20) * 20 + 1
        }

        val existingGroup = result.find { (key, _) ->
            val existingRangeStart = key.substringAfter("Ch. ").substringBefore(" - ").toIntOrNull() ?: -1
            existingRangeStart == rangeStart
        }

        if (existingGroup != null) {
            val (existingKey, existingItems) = existingGroup
            val index = result.indexOf(existingGroup)
            val updatedItems = existingItems + items
            val maxChapter = updatedItems.maxOfOrNull { extractMainChapterNumber(it.second.title) } ?: rangeStart
            val displayStart = existingKey.substringAfter("Ch. ").substringBefore(" - ").toIntOrNull() ?: rangeStart
            val displayEnd = if (displayStart == 0) maxChapter.coerceAtMost(20) else maxChapter
            val displayKey = "Ch. $displayStart - $displayEnd"
            result[index] = displayKey to updatedItems
        } else {
            val displayStart = if (hasChapterZero && mainChapter == 0) 0 else rangeStart
            val displayEnd = if (displayStart == 0) 0 else rangeStart + 19
            val displayKey = "Ch. $displayStart - $displayEnd"
            result.add(displayKey to items)
        }
    }

    return result.sortedBy {
        it.first.substringAfter("Ch. ").substringBefore(" - ").toIntOrNull() ?: 0
    }
}

private fun extractMainChapterNumber(title: String): Int {
    val patterns = listOf(
        Regex("Chapter\\s*(\\d+)"),
        Regex("Ch\\s*\\.\\s*(\\d+)"),
        Regex("^(\\d+)"),
        Regex("(\\d+)(?:\\.\\d+)?")
    )

    for (pattern in patterns) {
        val match = pattern.find(title)
        if (match != null) {
            val numStr = match.groupValues[1]
            return numStr.toIntOrNull() ?: 0
        }
    }
    return 0
}

private fun extractChapterNum(title: String): String {
    val patterns = listOf(
        Regex("Chapter\\s*(\\d+(?:\\.\\d+)?)"),
        Regex("Ch\\s*\\.\\s*(\\d+(?:\\.\\d+)?)"),
        Regex("^(\\d+(?:\\.\\d+)?)"),
        Regex("(\\d+(?:\\.\\d+)?)")
    )
    for (pattern in patterns) {
        val match = pattern.find(title)
        if (match != null) {
            val numStr = match.groupValues[1].trimEnd('.')
            if (numStr.isNotBlank()) return numStr
        }
    }
    return "?"
}
