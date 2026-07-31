package com.blissless.tensei.ui.screens.manga

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.blissless.tensei.MainViewModel
import com.blissless.tensei.data.models.MangaChapter
import com.blissless.tensei.data.models.MangaMedia
import com.blissless.tensei.viewmodel.loadChapterImages
import com.blissless.tensei.viewmodel.mangaChapterImages
import com.blissless.tensei.viewmodel.mangaChapters
import com.blissless.tensei.viewmodel.markMangaChapterRead
import com.blissless.tensei.viewmodel.updateMangaScrollProgress

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
    var currentChapterIndex by remember { mutableIntStateOf(initialChapterIndex) }
    var readerMode by remember { mutableStateOf(
        when (viewModel.mangaReaderMode.value) {
            "left_to_right" -> ReaderMode.LEFT_TO_RIGHT
            "right_to_left" -> ReaderMode.RIGHT_TO_LEFT
            else -> ReaderMode.VERTICAL_SCROLL
        }
    ) }
    var showControls by remember { mutableStateOf(true) }
    var showChapterList by remember { mutableStateOf(false) }
    var scrollProgress by remember { mutableFloatStateOf(0f) }

    val currentChapter = chapters.getOrNull(currentChapterIndex)
    val isFirstChapter = currentChapterIndex == 0
    val isLastChapter = currentChapterIndex >= chapters.lastIndex
    val useDataSaver = viewModel.mangaDataSaver.value

    LaunchedEffect(currentChapter) {
        currentChapter?.let { chapter ->
            viewModel.loadChapterImages(chapter.chapterId, useDataSaver)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(if (isOled) Color.Black else Color(0xFF1a1a1a))) {
        when {
            showChapterList -> {
                Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { showChapterList = false }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                        Text("Chapters", style = MaterialTheme.typography.titleLarge)
                    }
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(chapters) { index, chapter ->
                            ListItem(
                                headlineContent = { Text(chapter.title) },
                                leadingContent = {
                                    if (index < manga.progress) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = "Read", tint = MaterialTheme.colorScheme.primary)
                                    }
                                },
                                modifier = Modifier.pointerInput(Unit) {
                                    detectTapGestures { viewModel.markMangaChapterRead(manga.id, chapter); currentChapterIndex = index; showChapterList = false }
                                }
                            )
                        }
                    }
                }
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
                    onPrevChapter = { if (!isFirstChapter) { viewModel.markMangaChapterRead(manga.id, currentChapter!!); currentChapterIndex-- } },
                    onNextChapter = { if (!isLastChapter) { viewModel.markMangaChapterRead(manga.id, currentChapter!!); currentChapterIndex++ } },
                    isFirstChapter = isFirstChapter,
                    isLastChapter = isLastChapter
                )

                OverlayControls(
                    showControls = showControls,
                    chapter = currentChapter,
                    totalChapters = chapters.size,
                    currentIndex = currentChapterIndex,
                    mangaTitle = manga.title,
                    readerMode = readerMode,
                    isOled = isOled,
                    onToggleControls = { showControls = !showControls },
                    onClose = onClose,
                    onReaderModeChange = { readerMode = it },
                    onShowChapterList = { showChapterList = true },
                    onPrevChapter = { if (!isFirstChapter) { viewModel.markMangaChapterRead(manga.id, currentChapter!!); currentChapterIndex-- } },
                    onNextChapter = { if (!isLastChapter) { viewModel.markMangaChapterRead(manga.id, currentChapter!!); currentChapterIndex++ } },
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
                    onPrevChapter = { if (!isFirstChapter) { viewModel.markMangaChapterRead(manga.id, currentChapter!!); currentChapterIndex-- } },
                    onNextChapter = { if (!isLastChapter) { viewModel.markMangaChapterRead(manga.id, currentChapter!!); currentChapterIndex++ } },
                    isFirstChapter = isFirstChapter,
                    isLastChapter = isLastChapter
                )

                OverlayControls(
                    showControls = showControls,
                    chapter = currentChapter,
                    totalChapters = chapters.size,
                    currentIndex = currentChapterIndex,
                    mangaTitle = manga.title,
                    readerMode = readerMode,
                    isOled = isOled,
                    onToggleControls = { showControls = !showControls },
                    onClose = onClose,
                    onReaderModeChange = { readerMode = it },
                    onShowChapterList = { showChapterList = true },
                    onPrevChapter = { if (!isFirstChapter) { viewModel.markMangaChapterRead(manga.id, currentChapter!!); currentChapterIndex-- } },
                    onNextChapter = { if (!isLastChapter) { viewModel.markMangaChapterRead(manga.id, currentChapter!!); currentChapterIndex++ } },
                    isFirstChapter = isFirstChapter,
                    isLastChapter = isLastChapter
                )
            }
        }
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

        // Navigation buttons at end
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
            androidx.compose.material3.CircularProgressIndicator(color = Color.White)
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

@Composable
private fun OverlayControls(
    showControls: Boolean,
    chapter: MangaChapter?,
    totalChapters: Int,
    currentIndex: Int,
    mangaTitle: String,
    readerMode: ReaderMode,
    isOled: Boolean,
    onToggleControls: () -> Unit,
    onClose: () -> Unit,
    onReaderModeChange: (ReaderMode) -> Unit,
    onShowChapterList: () -> Unit,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
    isFirstChapter: Boolean,
    isLastChapter: Boolean
) {
    if (!showControls) return

    Box(modifier = Modifier.fillMaxSize()) {
        // Top bar
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            color = Color.Black.copy(alpha = 0.8f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = mangaTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        maxLines = 1
                    )
                    Text(
                        text = chapter?.title ?: "Loading...",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
                Text(
                    text = "${currentIndex + 1}/$totalChapters",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
                IconButton(onClick = onShowChapterList) {
                    Icon(Icons.Default.List, contentDescription = "Chapters", tint = Color.White)
                }
            }
        }

        // Bottom bar
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            color = Color.Black.copy(alpha = 0.8f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPrevChapter, enabled = !isFirstChapter) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Previous Chapter", tint = if (isFirstChapter) Color.Gray else Color.White)
                }
                Row {
                    ReaderModeButton(ReaderMode.VERTICAL_SCROLL, readerMode, onReaderModeChange)
                    Spacer(Modifier.width(8.dp))
                    ReaderModeButton(ReaderMode.LEFT_TO_RIGHT, readerMode, onReaderModeChange)
                    Spacer(Modifier.width(8.dp))
                    ReaderModeButton(ReaderMode.RIGHT_TO_LEFT, readerMode, onReaderModeChange)
                }
                IconButton(onClick = onNextChapter, enabled = !isLastChapter) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next Chapter", tint = if (isLastChapter) Color.Gray else Color.White)
                }
            }
        }
    }
}

@Composable
private fun ReaderModeButton(
    mode: ReaderMode,
    currentMode: ReaderMode,
    onSelect: (ReaderMode) -> Unit
) {
    val icon = when (mode) {
        ReaderMode.VERTICAL_SCROLL -> Icons.Default.ViewStream
        ReaderMode.LEFT_TO_RIGHT -> Icons.Default.ChevronRight
        ReaderMode.RIGHT_TO_LEFT -> Icons.Default.ChevronLeft
    }
    val label = when (mode) {
        ReaderMode.VERTICAL_SCROLL -> "Scroll"
        ReaderMode.LEFT_TO_RIGHT -> "LTR"
        ReaderMode.RIGHT_TO_LEFT -> "RTL"
    }
    val isSelected = mode == currentMode
    FilterChip(
        selected = isSelected,
        onClick = { onSelect(mode) },
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
        )
    )
}
