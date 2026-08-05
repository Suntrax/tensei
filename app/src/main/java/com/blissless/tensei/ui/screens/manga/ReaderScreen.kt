package com.blissless.tensei.ui.screens.manga

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.Surface
import android.view.View
import android.view.Window
import androidx.activity.compose.BackHandler
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import com.blissless.tensei.MainViewModel
import com.blissless.tensei.data.models.MangaChapter
import com.blissless.tensei.data.models.MangaMedia
import com.blissless.tensei.viewmodel.clearMangaChapterImagesCache
import com.blissless.tensei.viewmodel.fetchMangaDetail
import com.blissless.tensei.viewmodel.hasLoadedMangaChapters
import com.blissless.tensei.viewmodel.isLoadingMangaChapters
import com.blissless.tensei.viewmodel.loadChapterImages
import com.blissless.tensei.viewmodel.loadMangaChapters
import com.blissless.tensei.viewmodel.mangaChapterImages
import com.blissless.tensei.viewmodel.mangaChapterImagesError
import com.blissless.tensei.viewmodel.mangaChapters
import com.blissless.tensei.viewmodel.onMangaScrollProgress
import com.blissless.tensei.viewmodel.prefetchMangaChapterImages
import com.blissless.tensei.viewmodel.refreshMangaTracking
import com.blissless.tensei.viewmodel.selectedExtensionAuthority
import com.blissless.tensei.viewmodel.setMangaAutoAdvance
import com.blissless.tensei.viewmodel.setMangaFullscreen
import com.blissless.tensei.viewmodel.setMangaPageIndicator
import com.blissless.tensei.viewmodel.setMangaReaderMode
import com.blissless.tensei.viewmodel.setMangaLockRotation
import com.blissless.tensei.viewmodel.startMangaChapter
import com.blissless.tensei.viewmodel.updateMangaChapterPages
import com.blissless.tensei.viewmodel.updateMangaScrollProgress
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

enum class ReaderMode { VERTICAL_SCROLL, LEFT_TO_RIGHT, RIGHT_TO_LEFT }

@Composable
fun MangaReaderScreen(
    manga: MangaMedia,
    initialChapterIndex: Int,
    viewModel: MainViewModel,
    isOled: Boolean,
    onClose: () -> Unit,
    onOpenSettings: () -> Unit = {}
) {
    android.util.Log.d("MangaReader", "MangaReaderScreen compose: manga.id=${manga.id} title='${manga.title}' initialChapterIndex=$initialChapterIndex")
    val context = LocalContext.current
    // The reader is hosted in a Dialog, so LocalContext is the dialog's context (a
    // ContextThemeWrapper), NOT an Activity. Walk the wrapper chain to find the real
    // Activity so requestedOrientation changes actually take effect.
    val activity = context.findActivity()
    // The reader is hosted in a Dialog, so LocalView is a view inside the dialog's own
    // window. System-bar control must target THAT window (not the activity's), otherwise
    // hiding the bars has no visible effect. See applyReaderFullscreen.
    val view = LocalView.current
    DisposableEffect(Unit) {
        android.util.Log.d("MangaReader", "READER COMPOSED (disposable effect) manga.id=${manga.id}")
        onDispose {
            android.util.Log.d("MangaReader", "READER DISPOSED / LEAVING COMPOSITION manga.id=${manga.id} — screen is being removed (navigation close OR crash recovery)")
            // Release any rotation lock applied by the reader so the rest of the app
            // goes back to following the system orientation setting.
            applyReaderRotationLock(activity, false)
            // Restore the system bars if the reader hid them.
            applyReaderFullscreen(view, false)
            // Drop the prefetch cache so it doesn't hold stale image lists in memory.
            viewModel.clearMangaChapterImagesCache()
            // Refresh the home Continue Reading card with the latest scroll progress
            // when leaving the reader mid-chapter.
            viewModel.refreshMangaTracking()
        }
    }
    val chapters by viewModel.mangaChapters.collectAsState()
    val chapterImages by viewModel.mangaChapterImages.collectAsState()
    val chapterImagesError by viewModel.mangaChapterImagesError.collectAsState()
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
    var currentPageIndex by remember { mutableIntStateOf(0) }
    // True while the "Next Chapter" button is shown (reader settled at the end of a chapter).
    var showNextChapterButton by remember { mutableStateOf(false) }
    // True when the current chapter was opened via the next-chapter button — suppresses restoring
    // the saved (stale) scroll position so the new chapter always opens at the top.
    var suppressResumeRestore by remember { mutableStateOf(false) }
    val readIndices = remember { mutableStateOf((0 until manga.progress.coerceAtLeast(0)).toSet()) }
    val scope = rememberCoroutineScope()

    val currentChapter = chapters.getOrNull(currentChapterIndex)
    val isFirstChapter = currentChapterIndex == 0
    val isLastChapter = currentChapterIndex >= chapters.lastIndex
    val useDataSaver = viewModel.mangaDataSaver.value
    val isLoadingChapters by viewModel.isLoadingMangaChapters.collectAsState()
    val hasLoadedChapters by viewModel.hasLoadedMangaChapters.collectAsState()
    val selectedExtension by viewModel.selectedExtensionAuthority.collectAsState()
    val showPageIndicator by viewModel.mangaPageIndicator.collectAsState()
    val syncThreshold by viewModel.mangaSyncThreshold.collectAsState()
    val lockRotation by viewModel.mangaLockRotation.collectAsState()
    val fullscreen by viewModel.mangaFullscreen.collectAsState()
    val nextChapterButton by viewModel.mangaAutoAdvance.collectAsState()

    // Lock/unlock the screen rotation to the current orientation while reading.
    // Runs on entry (with the persisted setting) and every time the toggle changes.
    LaunchedEffect(lockRotation) {
        android.util.Log.d("MangaReader", "ROTATION LOCK ${if (lockRotation) "ON" else "OFF"}")
        applyReaderRotationLock(activity, lockRotation)
    }

    // Hide/show the system bars (status bar + navigation) on the DIALOG's window while
    // reading, matching the anime player's fullscreen behavior. The dialog window may not
    // be attached yet at first composition, so re-apply once shortly after.
    LaunchedEffect(fullscreen) {
        android.util.Log.d("MangaReader", "FULLSCREEN ${if (fullscreen) "ON" else "OFF"}")
        applyReaderFullscreen(view, fullscreen)
        delay(250)
        applyReaderFullscreen(view, fullscreen)
    }

    // Prefetch the NEXT chapter's image list once the reader nears the end of the current
    // chapter, so the next-chapter button (and manual next-chapter) switches instantly instead
    // of waiting on a scrape round-trip.
    LaunchedEffect(currentPageIndex, scrollProgress, currentChapterIndex, chapterImages, readerMode) {
        val images = chapterImages ?: return@LaunchedEffect
        if (images.isEmpty()) return@LaunchedEffect
        val nearEnd = if (readerMode == ReaderMode.VERTICAL_SCROLL) {
            scrollProgress >= 0.9f
        } else {
            currentPageIndex >= images.lastIndex - 1
        }
        if (nearEnd) {
            val next = chapters.getOrNull(currentChapterIndex + 1) ?: return@LaunchedEffect
            viewModel.prefetchMangaChapterImages(next, mangaTitle = manga.title, mangaId = manga.id)
        }
    }

    android.util.Log.d("MangaReader", "MangaReaderScreen state: chapters.size=${chapters.size} showChapterList=$showChapterList currentChapterIndex=$currentChapterIndex chapterImages=${chapterImages?.size ?: "null"}")

    LaunchedEffect(currentChapter, showChapterList) {
        android.util.Log.d("MangaReader", "LaunchedEffect(currentChapter, showChapterList): showChapterList=$showChapterList currentChapter=${currentChapter != null}")
        if (!showChapterList) {
            currentChapter?.let { chapter ->
                android.util.Log.d("MangaReader", "Loading chapter images for chapterId='${chapter.chapterId}' title='${chapter.title}'")
                viewModel.loadChapterImages(
                    chapterId = chapter.chapterId,
                    useDataSaver = useDataSaver,
                    mangaTitle = manga.title,
                    chapterTitle = chapter.title,
                    mangaId = manga.id
                )
            }
        }
    }

    // Auto-load chapters if the list is empty — always fetch, regardless of showChapterList.
    // The reader needs chapters both for the chapter list view AND for direct reading.
    // Skipped while no manga extension is selected: without a source there's no real chapter
    // list, and the synthetic fallback produces wrong chapter numbers for releasing manga.
    LaunchedEffect(manga.id, selectedExtension) {
        android.util.Log.d("MangaReader", "LaunchedEffect(manga.id=${manga.id}, selectedExtension=${selectedExtension != null}): chapters.isEmpty()=${chapters.isEmpty()}")
        if (chapters.isEmpty() && selectedExtension != null) {
            android.util.Log.d("MangaReader", "Fetching manga detail + chapters for manga.id=${manga.id}")
            viewModel.fetchMangaDetail(manga.id)
            viewModel.loadMangaChapters(manga.id, manga.title)
        }
    }

    // Persist the page count of the loaded chapter so home's Continue Reading card can show
    // "pages left" for the manga being read.
    LaunchedEffect(chapterImages) {
        val images = chapterImages
        if (images != null && images.isNotEmpty()) {
            viewModel.updateMangaChapterPages(manga.id, images.size)
        }
    }

    // Handle system back button — if chapter list is open, close it first; otherwise close reader
    BackHandler {
        android.util.Log.d("MangaReader", "BACK pressed: showChapterList=$showChapterList chapters.size=${chapters.size} — " +
            if (!showChapterList && chapters.isNotEmpty()) "closing to chapter list" else "closing reader via onClose()")
        if (!showChapterList && chapters.isNotEmpty()) {
            // Was reading — go back to chapter list
            showChapterList = true
            showControls = false
        } else {
            // Was on chapter list — close reader
            onClose()
        }
    }

    fun selectChapter(index: Int, startAtTop: Boolean = false) {
        android.util.Log.d("MangaReader", "selectChapter(index=$index startAtTop=$startAtTop) chapters.size=${chapters.size}")
        val chapter = chapters.getOrNull(index) ?: run {
            android.util.Log.w("MangaReader", "selectChapter: index $index out of range, IGNORED")
            return
        }
        android.util.Log.d("MangaReader", "selectChapter: opening chapterId='${chapter.chapterId}' title='${chapter.title}'")
        // Create the local track on open so the manga appears in "Continue Reading" even if the
        // user exits before reaching the sync threshold. Chapter is NOT marked read here and
        // nothing is pushed to AniList — that only happens once the user scrolls past the
        // threshold (see onMangaScrollProgress).
        viewModel.startMangaChapter(manga.id, manga.title, manga.cover)
        readIndices.value = readIndices.value + index
        currentChapterIndex = index
        currentPageIndex = 0
        scrollProgress = 0f
        suppressResumeRestore = startAtTop
        showNextChapterButton = false
        // Advancing to the next chapter (via the next-chapter button): clear the saved scroll
        // position so the new chapter starts at the top (both in-reader and for a later Continue
        // Reading resume) instead of restoring the bottom of the just-finished chapter.
        if (startAtTop) {
            viewModel.updateMangaScrollProgress(manga.id, 0f)
        }
        showChapterList = false
        showControls = false
    }

    // Show a "Next Chapter" button when the reader settles at the end of the current chapter,
    // in BOTH vertical-scroll and single-page (LTR/RTL) modes. This replaces auto-advance:
    // nothing is opened automatically — the user taps the button. Debounced so rapid swipes
    // don't flash it, and hidden whenever the reader leaves the end of the chapter or the
    // setting is off.
    LaunchedEffect(currentPageIndex, scrollProgress, chapterImages, showChapterList, nextChapterButton, readerMode, isLastChapter) {
        if (!nextChapterButton || showChapterList || isLastChapter) {
            showNextChapterButton = false
            return@LaunchedEffect
        }
        val images = chapterImages
        if (images == null || images.isEmpty()) {
            showNextChapterButton = false
            return@LaunchedEffect
        }
        val atEnd = if (readerMode == ReaderMode.VERTICAL_SCROLL) {
            currentPageIndex >= images.lastIndex && scrollProgress >= 0.95f
        } else {
            currentPageIndex >= images.lastIndex
        }
        if (!atEnd) {
            showNextChapterButton = false
            return@LaunchedEffect
        }
        android.util.Log.d("MangaReader", "NEXT-BUTTON: end of chapter $currentChapterIndex reached, showing next-chapter button")
        delay(700)
        // Re-check after the debounce: still at the end of the same chapter.
        val stillAtEnd = if (readerMode == ReaderMode.VERTICAL_SCROLL) {
            currentPageIndex >= images.lastIndex && scrollProgress >= 0.95f
        } else {
            currentPageIndex >= images.lastIndex
        }
        showNextChapterButton = stillAtEnd
    }

    // Retry the current chapter image load (used by the error UI)
    fun retryChapterLoad() {
        currentChapter?.let { chapter ->
            viewModel.loadChapterImages(chapter.chapterId, useDataSaver, manga.title)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(if (isOled) Color.Black else Color(0xFF1a1a1a))) {
        val branch = when {
            showChapterList || chapters.isEmpty() -> "chapter_list"
            readerMode == ReaderMode.VERTICAL_SCROLL -> "vertical_scroll"
            else -> "paged"
        }
        // Log only when the significant render state actually changes (avoids spam during scroll)
        var lastRenderSig by remember { mutableStateOf("") }
        val renderSig = "$branch|${chapters.size}|$currentChapterIndex|$readerMode|${chapterImages?.size ?: "null"}"
        if (lastRenderSig != renderSig) {
            lastRenderSig = renderSig
            android.util.Log.d("MangaReader", "RENDER branch=$branch showChapterList=$showChapterList chapters=${chapters.size} " +
                "currentChapterIndex=$currentChapterIndex readerMode=$readerMode chapterImages=${chapterImages?.size ?: "null"} error=${chapterImagesError != null}")
        }
        when {
            // Show chapter list when explicitly requested OR when chapters haven't loaded yet
            // (shows a loading state inside MangaChapterListWithGroups)
            showChapterList || chapters.isEmpty() -> {
                MangaChapterListWithGroups(
                    chapters = chapters,
                    isLoadingChapters = isLoadingChapters,
                    hasLoadedChapters = hasLoadedChapters,
                    readIndices = readIndices.value,
                    nextChapterToRead = manga.progress.coerceAtLeast(0),
                    onChapterClick = { selectChapter(it) },
                    onContinueReading = {
                        val next = manga.progress.coerceAtLeast(0)
                        if (next in chapters.indices) selectChapter(next)
                    },
                    onRetryLoadChapters = {
                        scope.launch {
                            viewModel.fetchMangaDetail(manga.id)
                            viewModel.loadMangaChapters(manga.id, manga.title)
                        }
                    },
                    onBack = {
                        android.util.Log.d("MangaReader", "Chapter list back arrow tapped — calling onClose()")
                        onClose()
                    }
                )
            }

            readerMode == ReaderMode.VERTICAL_SCROLL -> {
                VerticalScrollReader(
                    chapterImages = chapterImages ?: emptyList(),
                    chapterImagesError = chapterImagesError,
                    chapter = currentChapter,
                    totalChapters = chapters.size,
                    currentIndex = currentChapterIndex,
                    scrollProgress = scrollProgress,
                    // Restore target for the vertical reader: prefer the LIVE scroll position (so
                    // switching from a single-page mode back to vertical resumes where you were),
                    // and fall back to the saved resume position only on the initial entry.
                    restoreProgress = when {
                        scrollProgress > 0f -> scrollProgress
                        !suppressResumeRestore && currentChapterIndex == manga.progress -> manga.scrollProgress
                        else -> -1f
                    },
                    showControls = showControls,
                    onScrollProgress = {
                        viewModel.onMangaScrollProgress(
                            mangaId = manga.id,
                            chapter = currentChapter,
                            scrollPercent = it,
                            mangaTitle = manga.title,
                            mangaCover = manga.cover
                        )
                        scrollProgress = it
                    },
                    onCurrentPage = { page -> currentPageIndex = page },
                    onToggleControls = { showControls = !showControls },
                    onRetry = { retryChapterLoad() }
                )
            }

            readerMode == ReaderMode.LEFT_TO_RIGHT || readerMode == ReaderMode.RIGHT_TO_LEFT -> {
                PagedMangaReader(
                    chapterImages = chapterImages ?: emptyList(),
                    chapterImagesError = chapterImagesError,
                    mode = readerMode,
                    initialPage = currentPageIndex.coerceIn(0, (chapterImages?.size ?: 1) - 1),
                    onToggleControls = { showControls = !showControls },
                    onPrevChapter = { if (!isFirstChapter) selectChapter(currentChapterIndex - 1) },
                    onNextChapter = { if (!isLastChapter) selectChapter(currentChapterIndex + 1) },
                    onRetry = { retryChapterLoad() },
                    onPageChanged = { page, total ->
                        currentPageIndex = page
                        if (total > 1) {
                            val progress = page.toFloat() / (total - 1).toFloat()
                            viewModel.onMangaScrollProgress(
                                mangaId = manga.id,
                                chapter = currentChapter,
                                scrollPercent = progress,
                                mangaTitle = manga.title,
                                mangaCover = manga.cover
                            )
                            scrollProgress = progress
                        }
                    }
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
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = manga.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (currentChapter != null) {
                                Text(
                                    text = currentChapter.title,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
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
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black.copy(alpha = 0.95f),
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    )
                )
                Box(modifier = Modifier.fillMaxWidth().height(3.dp).background(Color.Black.copy(alpha = 0.95f))) {
                    Box(modifier = Modifier.fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.2f)))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(scrollProgress.coerceIn(0f, 1f))
                            .height(3.dp)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(syncThreshold / 100f)
                            .fillMaxHeight()
                            .background(Color.Transparent)
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .width(2.dp)
                                .fillMaxHeight()
                                .background(Color.White.copy(alpha = 0.8f))
                        )
                    }
                }
            }
        }

        // ─── Overlay: "Next Chapter" button ──────────────────────────
        // Appears after the debounce once the reader settles at the end of a chapter (all modes)
        // when the Next Chapter Button setting is on. Tapping it opens the next chapter from the
        // top; scrolling away from the end hides it.
        AnimatedVisibility(
            visible = showNextChapterButton && !showChapterList && !isLastChapter,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(150)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Button(
                onClick = {
                    android.util.Log.d("MangaReader", "NEXT-BUTTON: tapped, opening chapter ${currentChapterIndex + 1}")
                    selectChapter(currentChapterIndex + 1, startAtTop = true)
                },
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.95f),
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .padding(bottom = 72.dp)
                    .height(48.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Next Chapter")
            }
        }

        // ─── Overlay: page indicator + bottom bar ─────────────────────
        // The page indicator sits directly above the bottom controls bar instead of
        // floating independently. It stays visible whenever the setting is on (independent
        // of the controls overlay); when the controls bar is hidden it rests at the bottom
        // edge above the navigation bar.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        ) {
            val totalPages = chapterImages?.size ?: 0
            if (showPageIndicator && !showChapterList && totalPages > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Box(
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${currentPageIndex + 1} / $totalPages",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                    }
                }
            }
            AnimatedVisibility(
                visible = showControls && !showChapterList,
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(150))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.95f))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    IconButton(
                        onClick = { viewModel.setMangaLockRotation(!lockRotation) },
                        modifier = Modifier.align(Alignment.CenterStart).size(32.dp)
                    ) {
                        Icon(
                            if (lockRotation) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = "Toggle rotation lock",
                            tint = if (lockRotation) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
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
                        },
                        modifier = Modifier.align(Alignment.Center)
                    )
                    Row(
                        modifier = Modifier.align(Alignment.CenterEnd),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = { viewModel.setMangaFullscreen(!fullscreen) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                if (fullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                contentDescription = "Toggle fullscreen",
                                tint = if (fullscreen) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = { viewModel.setMangaPageIndicator(!showPageIndicator) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Article,
                                contentDescription = "Toggle page indicator",
                                tint = if (showPageIndicator) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // No manga extension selected — replace the entire reader content (including the
        // chapter selection screen) so the user is forced to pick a source first.
        if (selectedExtension == null) {
            MangaNoExtensionScreen(
                isOled = isOled,
                onClose = onClose,
                onOpenSettings = onOpenSettings
            )
        }
    }
}

@Composable
private fun MangaNoExtensionScreen(
    isOled: Boolean,
    onClose: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isOled) Color.Black else Color(0xFF1a1a1a)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Icon(
                Icons.Default.Extension,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "No extension selected",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Select a manga extension in Reader Settings to load chapters for this title.",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onOpenSettings,
                modifier = Modifier.height(48.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Go to Settings")
            }
            TextButton(onClick = onClose) {
                Text("Close", color = Color.White.copy(alpha = 0.8f))
            }
        }
    }
}

@Composable
private fun ReaderModeSegmentedToggle(
    currentMode: ReaderMode,
    onSelect: (ReaderMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(2.dp)
    ) {
        ReaderModeToggleItem(
            icon = Icons.Default.ViewAgenda,
            contentDescription = "Vertical scroll mode",
            selected = currentMode == ReaderMode.VERTICAL_SCROLL,
            onClick = { onSelect(ReaderMode.VERTICAL_SCROLL) }
        )
        ReaderModeToggleItem(
            icon = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = "Left to right paged mode",
            selected = currentMode == ReaderMode.LEFT_TO_RIGHT,
            onClick = { onSelect(ReaderMode.LEFT_TO_RIGHT) }
        )
        ReaderModeToggleItem(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Right to left paged mode",
            selected = currentMode == ReaderMode.RIGHT_TO_LEFT,
            onClick = { onSelect(ReaderMode.RIGHT_TO_LEFT) }
        )
    }
}

@Composable
private fun ReaderModeToggleItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun ChapterLoadErrorView(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = message,
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Button(
                onClick = onRetry,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Retry")
            }
        }
    }
}

@Composable
private fun ChapterLoadingView(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = Color.White)
    }
}

@Composable
private fun VerticalScrollReader(
    chapterImages: List<String>,
    chapterImagesError: String?,
    chapter: MangaChapter?,
    totalChapters: Int,
    currentIndex: Int,
    scrollProgress: Float,
    restoreProgress: Float = -1f,
    showControls: Boolean,
    onScrollProgress: (Float) -> Unit,
    onCurrentPage: (Int) -> Unit = {},
    onToggleControls: () -> Unit,
    onRetry: () -> Unit
) {
    val listState = rememberLazyListState()
    var restored by remember { mutableStateOf(false) }

    LaunchedEffect(chapter) {
        listState.scrollToItem(0)
        restored = false
    }

    // Restore the saved reading position once images are available (oni-style resume).
    // Only applies to the chapter that was last being read (see caller's restoreProgress).
    LaunchedEffect(chapter, chapterImages) {
        if (!restored && restoreProgress > 0f && chapterImages.isNotEmpty()) {
            delay(150)
            val targetIndex = (restoreProgress * chapterImages.size)
                .toInt().coerceIn(0, chapterImages.size - 1)
            listState.scrollToItem(targetIndex)
            restored = true
        }
    }

    // Reading progress (0..1) — pixel-based scroll fraction, matching oni.
    // Writes are done in the COLLECT lambda (outside snapshotFlow's read-only
    // snapshot); writing state inside the snapshotFlow block itself would throw
    // "Cannot modify a state object in a read-only snapshot".
    LaunchedEffect(listState) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            if (totalItems == 0) return@snapshotFlow 0f
            if (totalItems <= 1) return@snapshotFlow 1f

            val firstVisibleItem = layoutInfo.visibleItemsInfo.firstOrNull() ?: return@snapshotFlow 0f
            val itemSize = firstVisibleItem.size
            val viewportHeight = layoutInfo.viewportSize.height

            val currentScroll = listState.firstVisibleItemIndex.toFloat() * itemSize.toFloat() + listState.firstVisibleItemScrollOffset.toFloat()
            val maxScroll = (totalItems.toFloat() * itemSize.toFloat() - viewportHeight.toFloat()).coerceAtLeast(0f)
            if (maxScroll <= 0f) 0f else (currentScroll / maxScroll).coerceIn(0f, 1f)
        }.collect { progress: Float ->
            onScrollProgress(progress)
        }
    }

    // Track the current PAGE (the one the user is actually looking at) by
    // watching the LazyColumn's layout, using the item with the largest
    // viewport overlap (oni's approach). Runs in the collect lambda so state
    // writes are legal.
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo }
            .collect { layoutInfo ->
                val visibleItems = layoutInfo.visibleItemsInfo
                if (visibleItems.isEmpty()) return@collect
                val best = visibleItems.maxByOrNull { item ->
                    val overlapTop = maxOf(item.offset, layoutInfo.viewportStartOffset)
                    val overlapBottom = minOf(item.offset + item.size, layoutInfo.viewportEndOffset)
                    maxOf(0, overlapBottom - overlapTop)
                }
                if (best != null) {
                    onCurrentPage(best.index)
                }
            }
    }

    // Error state — show error message + retry button instead of empty list
    if (chapterImagesError != null) {
        ChapterLoadErrorView(
            message = chapterImagesError,
            onRetry = onRetry,
            modifier = Modifier.fillMaxSize()
        )
        return
    }

    // Loading state — chapter just started loading
    if (chapterImages.isEmpty()) {
        ChapterLoadingView(modifier = Modifier.fillMaxSize())
        return
    }

    // Vertical scroll mode — one zoomable page per row (7:10 box, edge-to-edge
    // width), matching oni's MihonZoomableImage. Pinch/double-tap zoom works per
    // page; single-finger drag at 1x passes through so the LazyColumn scrolls.
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(chapterImages, key = { index, _ -> "page_$index" }) { index, imageUrl ->
            MihonZoomableImage(
                imageUrl = imageUrl,
                contentDescription = "Page ${index + 1}",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(7f / 10f),
                fillWidth = true,
                onSingleTap = { onToggleControls() }
            )
        }
    }
}

@Composable
private fun PagedMangaReader(
    chapterImages: List<String>,
    chapterImagesError: String?,
    mode: ReaderMode,
    initialPage: Int,
    onToggleControls: () -> Unit,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onRetry: () -> Unit,
    onPageChanged: (page: Int, total: Int) -> Unit = { _, _ -> }
) {
    // Error state — show error message + retry button
    if (chapterImagesError != null) {
        ChapterLoadErrorView(
            message = chapterImagesError,
            onRetry = onRetry,
            modifier = Modifier.fillMaxSize()
        )
        return
    }

    if (chapterImages.isEmpty()) {
        ChapterLoadingView(modifier = Modifier.fillMaxSize())
        return
    }

    val isRtl = mode == ReaderMode.RIGHT_TO_LEFT
    // The pager is torn down and recreated whenever a chapter's images change
    // (chapterImages is cleared to null during loading), so `initialPage` here
    // both resumes the current page on mode switches AND starts new chapters at
    // page 0 — matching oni.
    val pagerState = rememberPagerState(
        initialPage = initialPage.coerceIn(0, chapterImages.lastIndex),
        pageCount = { chapterImages.size }
    )
    val scope = rememberCoroutineScope()

    // Report the current page upward so the reader can show a page indicator
    // and feed scroll progress back to the ViewModel for AniList sync. Fires
    // once per page transition via distinctUntilChanged, matching oni.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page -> onPageChanged(page, chapterImages.size) }
    }

    // Track the current page's zoom state. While zoomed in, the pager's swipe
    // is disabled so the user can pan without flipping pages.
    var currentPageZoomed by remember { mutableStateOf(false) }

    // Per-page double-tap zoom trigger. Incremented when a double-tap is
    // detected at the pager level (before HorizontalPager consumes the event).
    // MihonZoomableImage watches this and toggles zoom accordingly.
    val doubleTapTriggers = remember { mutableStateMapOf<Int, Int>() }

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            reverseLayout = isRtl,
            userScrollEnabled = !currentPageZoomed,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                // Detect double-taps at the pager level using the Initial pass,
                // which runs BEFORE HorizontalPager's own gesture detection.
                .pointerInput(pagerState.currentPage) {
                    awaitEachGesture {
                        val down1 = awaitFirstDown(requireUnconsumed = false)
                        val t1 = down1.uptimeMillis
                        waitForUpOrCancellation() ?: return@awaitEachGesture
                        val down2 = awaitFirstDown(requireUnconsumed = false)
                        val t2 = down2.uptimeMillis
                        if (t2 - t1 < 300) {
                            val page = pagerState.currentPage
                            doubleTapTriggers[page] = (doubleTapTriggers[page] ?: 0) + 1
                            down2.consume()
                        }
                    }
                }
        ) { pageIndex ->
            // Reset the zoom flag when the user lands on a new page: the new
            // page starts at 1x, so the pager should be swipeable until pinched.
            var isZoomed by remember(pageIndex) { mutableStateOf(false) }
            var pageLayoutSize by remember(pageIndex) { mutableStateOf(Size.Zero) }

            LaunchedEffect(pagerState.currentPage, isZoomed) {
                currentPageZoomed = if (pagerState.currentPage == pageIndex) isZoomed else false
            }

            MihonZoomableImage(
                imageUrl = chapterImages[pageIndex],
                contentDescription = "Page ${pageIndex + 1}",
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { pageLayoutSize = it.toSize() },
                fillWidth = false,
                doubleTapTrigger = doubleTapTriggers[pageIndex] ?: 0,
                onSingleTap = { tap ->
                    if (isZoomed) return@MihonZoomableImage

                    val width = pageLayoutSize.width
                    if (width <= 0f) return@MihonZoomableImage

                    // Middle zone (~40%) toggles the chrome; left/right zones
                    // flip pages (handling chapter boundaries at the edges).
                    val isMiddleZone = tap.x > width * 0.3f && tap.x < width * 0.7f
                    if (isMiddleZone) {
                        onToggleControls()
                        return@MihonZoomableImage
                    }

                    val isPrevZone = if (isRtl) tap.x > width * 0.7f else tap.x < width * 0.3f

                    scope.launch {
                        when {
                            isPrevZone && pagerState.currentPage > 0 ->
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            !isPrevZone && pagerState.currentPage < chapterImages.lastIndex ->
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            !isPrevZone && pagerState.currentPage == chapterImages.lastIndex ->
                                onNextChapter()
                            isPrevZone && pagerState.currentPage == 0 ->
                                onPrevChapter()
                        }
                    }
                },
                onZoomChanged = { zoomed -> isZoomed = zoomed }
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
    isLoadingChapters: Boolean = true,
    hasLoadedChapters: Boolean = false,
    onContinueReading: (() -> Unit)? = null,
    onRetryLoadChapters: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null
) {
    if (chapters.isEmpty()) {
        var lastEmptySig by remember { mutableStateOf("") }
        val emptySig = "empty|$isLoadingChapters|$hasLoadedChapters"
        if (lastEmptySig != emptySig) {
            lastEmptySig = emptySig
            android.util.Log.d("MangaChapterList", "RENDER empty-state: isLoadingChapters=$isLoadingChapters " +
                "hasLoadedChapters=$hasLoadedChapters onRetryLoadChapters=${onRetryLoadChapters != null} onBack=${onBack != null}")
        }
        Box(
            modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Show the loading state immediately on first open (before loadMangaChapters
                // has completed) instead of flashing the "No chapters found" empty state.
                if (isLoadingChapters || !hasLoadedChapters) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Loading chapters...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
                } else {
                    Text("No chapters found", color = MaterialTheme.colorScheme.onSurface, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Could not load chapters for this manga. It may not be available on the configured source, or the source may be down.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    if (onRetryLoadChapters != null) {
                        Button(onClick = onRetryLoadChapters) {
                            Text("Retry")
                        }
                    }
                }
            }
        }
    } else {
        var searchQuery by remember { mutableStateOf("") }
        val groupedChapters = remember(chapters) {
            runCatching {
                groupChaptersByMainChapter(chapters)
            }.onFailure { e ->
                android.util.Log.e("MangaChapterList", "groupChaptersByMainChapter CRASHED: ${e.message}", e)
            }.getOrElse { emptyList() }
        }
        var lastListSig by remember { mutableStateOf("") }
        val listSig = "list|${chapters.size}|${groupedChapters.size}|${readIndices.size}|$nextChapterToRead"
        if (lastListSig != listSig) {
            lastListSig = listSig
            android.util.Log.d("MangaChapterList", "RENDER list: chapters=${chapters.size} grouped=${groupedChapters.size} " +
                "readIndices=${readIndices.size} nextChapterToRead=$nextChapterToRead")
        }

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

// Locks the screen to the current orientation family while the reader is open, and
// releases the lock (back to system auto-rotate) when disabled or on exit.
private fun applyReaderRotationLock(activity: Activity?, enabled: Boolean) {
    if (activity == null) return
    if (!enabled) {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        return
    }
    @Suppress("DEPRECATION")
    val rotation = activity.windowManager.defaultDisplay.rotation
    activity.requestedOrientation = when (rotation) {
        Surface.ROTATION_90, Surface.ROTATION_270 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    }
}

// Hides/shows the system bars (status bar + navigation) on the DIALOG's window. The reader
// runs inside a Dialog, whose window is separate from the activity's — controlling the
// activity window has no visible effect while the dialog is on top. Falls back to the
// activity window only if the dialog window can't be resolved (e.g. pre-attachment).
private fun applyReaderFullscreen(view: View, fullscreen: Boolean) {
    val window = view.findDialogWindow()
        ?: (view.context.findActivity()?.window)
        ?: return
    val controller = WindowCompat.getInsetsController(window, window.decorView)
    if (fullscreen) {
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        WindowCompat.setDecorFitsSystemWindows(window, false)
    } else {
        controller.show(WindowInsetsCompat.Type.systemBars())
        WindowCompat.setDecorFitsSystemWindows(window, true)
    }
}

// Walks the view parent chain looking for the DialogWindowProvider Compose installs in
// dialogs, returning its window (the dialog's own window).
private tailrec fun View.findDialogWindow(): Window? = when (val parent = parent) {
    is DialogWindowProvider -> parent.window
    is View -> parent.findDialogWindow()
    else -> null
}

// Walks the ContextWrapper chain (e.g. a Dialog's ContextThemeWrapper) up to the host
// Activity. Returns null when no Activity is reachable.
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
