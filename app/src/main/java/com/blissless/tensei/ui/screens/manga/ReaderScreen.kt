package com.blissless.tensei.ui.screens.manga

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.Surface
import android.view.View
import android.view.Window
import androidx.activity.ComponentDialog
import androidx.activity.OnBackPressedCallback
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalDensity
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
import com.blissless.tensei.viewmodel.flushMangaSync
import com.blissless.tensei.viewmodel.hasLoadedMangaChapters
import com.blissless.tensei.viewmodel.isLoadingMangaChapters
import com.blissless.tensei.viewmodel.loadChapterImages
import com.blissless.tensei.viewmodel.loadMangaChapters
import com.blissless.tensei.viewmodel.mangaChapterImages
import com.blissless.tensei.viewmodel.mangaChapterImagesError
import com.blissless.tensei.viewmodel.mangaChapters
import com.blissless.tensei.viewmodel.mangaDownloads
import com.blissless.tensei.viewmodel.onMangaScrollProgress
import com.blissless.tensei.viewmodel.prefetchMangaChapterImages
import com.blissless.tensei.viewmodel.refreshMangaTracking
import com.blissless.tensei.viewmodel.selectedExtensionAuthority
import com.blissless.tensei.viewmodel.setMangaAutoAdvance
import com.blissless.tensei.viewmodel.setMangaFullscreen
import com.blissless.tensei.viewmodel.setMangaPageIndicator
import com.blissless.tensei.viewmodel.setMangaReaderMode
import com.blissless.tensei.viewmodel.setMangaLockRotation
import com.blissless.tensei.viewmodel.updateMangaChapterPages
import com.blissless.tensei.viewmodel.updateMangaScrollProgress
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

enum class ReaderMode { VERTICAL_SCROLL, LEFT_TO_RIGHT, RIGHT_TO_LEFT }

@Composable
fun MangaReaderScreen(
    manga: MangaMedia,
    initialChapterIndex: Int,
    initialChapterNumber: Float? = null,
    viewModel: MainViewModel,
    isOled: Boolean,
    onClose: () -> Unit,
    onOpenSettings: () -> Unit = {}
) {
    android.util.Log.d("MangaReader", "MangaReaderScreen compose: manga.id=${manga.id} title='${manga.title}' initialChapterIndex=$initialChapterIndex initialChapterNumber=$initialChapterNumber")
    val context = LocalContext.current
    // The reader is hosted in a Dialog, so LocalContext is the dialog's context (a
    // ContextThemeWrapper), NOT an Activity. Walk the wrapper chain to find the real
    // Activity so requestedOrientation changes actually take effect.
    val activity = context.findActivity()
    // The reader is hosted in a Dialog, so LocalView is a view inside the dialog's own
    // window. System-bar control must target THAT window (not the activity's), otherwise
    // hiding the bars has no visible effect. See applyReaderFullscreen.
    val view = LocalView.current
    // True when the reader was opened from the Downloads tab (offline). Offline reading must
    // never touch tracking or AniList: no progress push, no sync-threshold mark-as-read, no
    // sync marker, and no lag from the threshold-crossing work.
    val offlineOpen = initialChapterNumber != null
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
            // Push any debounced progress/status changes to AniList immediately so chapters
            // read in the last few seconds aren't lost if the app is killed during the debounce.
            // Offline reading never queues syncs, so there is nothing to flush.
            if (!offlineOpen) viewModel.flushMangaSync()
            // Refresh the home Continue Reading card with the latest scroll progress
            // when leaving the reader mid-chapter.
            viewModel.refreshMangaTracking()
            // Clear Discord Rich Presence when leaving the reader.
            com.blissless.tensei.discord.DiscordRichPresence.clearPresence()
        }
    }
    val chapters by viewModel.mangaChapters.collectAsState()
    val chapterImages by viewModel.mangaChapterImages.collectAsState()
    val chapterImagesError by viewModel.mangaChapterImagesError.collectAsState()
    val downloadedManga by viewModel.mangaDownloads.collectAsState()
    // Chapter numbers that exist on disk for this manga. Used to mark downloaded chapters
    // in the chapter list and to allow reading without an extension/network.
    val downloadedChapterNumbers = remember(downloadedManga, manga.id) {
        downloadedManga.firstOrNull { it.mangaId == manga.id }?.chapters
            ?.map { it.chapterNumber }?.toSet() ?: emptySet()
    }
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
    // Pending resume progress for horizontal reader modes: set in selectChapter() when resuming,
    // consumed after images load to compute the correct initial page index.
    var pendingResumeProgress by remember { mutableFloatStateOf(-1f) }
    // True after a chapter is tapped in the chapter list, while its images are still loading.
    // The list stays open with a loading overlay on top, then dismisses once the images arrive.
    var pendingChapterLoad by remember { mutableStateOf(false) }
    // The chapter index whose load is being awaited (set in selectChapter). -1 when idle.
    var pendingChapterIndex by remember { mutableIntStateOf(-1) }
    // The images/error actually on screen, and which chapter they belong to. Kept unchanged
    // while a new chapter loads so the current screen (chapter list or current chapter) stays
    // visible behind the loading overlay; it swaps to the new chapter only once its images are
    // ready (or it failed).
    var displayedImages by remember { mutableStateOf<List<String>?>(null) }
    var displayedImagesError by remember { mutableStateOf<String?>(null) }
    var displayedChapterIndex by remember { mutableIntStateOf(-1) }
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
    // Fullscreen only applies while actually reading a chapter — the chapter selection
    // screen keeps the system bars visible.
    LaunchedEffect(fullscreen, showChapterList) {
        val effectiveFullscreen = fullscreen && !showChapterList
        android.util.Log.d("MangaReader", "FULLSCREEN ${if (effectiveFullscreen) "ON" else "OFF"} (showChapterList=$showChapterList)")
        applyReaderFullscreen(view, effectiveFullscreen)
        delay(250)
        applyReaderFullscreen(view, effectiveFullscreen)
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
            viewModel.prefetchMangaChapterImages(
                next,
                mangaTitle = manga.title,
                mangaId = manga.id,
                downloadedChapterNumbers = downloadedChapterNumbers
            )
        }
    }

    android.util.Log.d("MangaReader", "MangaReaderScreen state: chapters.size=${chapters.size} showChapterList=$showChapterList currentChapterIndex=$currentChapterIndex chapterImages=${chapterImages?.size ?: "null"}")

    LaunchedEffect(currentChapter, showChapterList, pendingChapterLoad) {
        android.util.Log.d("MangaReader", "LaunchedEffect(currentChapter, showChapterList, pendingChapterLoad): showChapterList=$showChapterList pendingChapterLoad=$pendingChapterLoad currentChapter=${currentChapter != null}")
        if (!showChapterList || pendingChapterLoad) {
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

    // Once the chapter images finish loading (or fail), put them on screen. The VM cancels any
    // earlier chapter load when a new one starts, so the settled value is always for the
    // currently-selected chapter. During the load the PREVIOUS screen (chapter list or current
    // chapter) stays visible behind the loading overlay instead of flashing a blank loading view.
    LaunchedEffect(chapterImages, chapterImagesError) {
        if (chapterImages != null || chapterImagesError != null) {
            android.util.Log.d("MangaReader", "Chapter images settled (images=${chapterImages?.size ?: "null"} error=${chapterImagesError != null}) — swapping display")
            displayedImages = chapterImages
            displayedImagesError = chapterImagesError
            displayedChapterIndex = currentChapterIndex
            if (pendingResumeProgress < 0f && manga.scrollProgress > 0f && currentChapterIndex == manga.progress) {
                pendingResumeProgress = manga.scrollProgress
                android.util.Log.d("MangaReader", "Initial entry resume: set pendingResumeProgress=${manga.scrollProgress}")
            }
            // Update Discord Rich Presence with current manga/chapter info
            val chapter = currentChapter
            if (chapterImages != null && chapter != null) {
                val drm = viewModel.discordRichPresence.value
                if (drm) {
                    com.blissless.tensei.discord.DiscordRichPresence.connect()
                    com.blissless.tensei.discord.DiscordRichPresence.setMangaPresence(
                        mangaTitle = manga.title,
                        chapterLabel = chapter.title.ifEmpty { chapter.chapterId },
                    )
                }
            }
        }
    }

    // If a chapter load was started from the chapter list (or a chapter transition), dismiss the
    // loading overlay once the NEW chapter's images are on screen, and close the list so the
    // reader takes over. Guarded by displayedChapterIndex == pendingChapterIndex so stale
    // displayed images from a previously-read chapter never close the list prematurely.
    LaunchedEffect(displayedImages, displayedImagesError, displayedChapterIndex, pendingChapterLoad, pendingChapterIndex) {
        if (pendingChapterLoad &&
            displayedChapterIndex == pendingChapterIndex &&
            (displayedImages != null || displayedImagesError != null)
        ) {
            android.util.Log.d("MangaReader", "Pending chapter load settled (images=${displayedImages?.size ?: "null"} error=${displayedImagesError != null}) — clearing overlay")
            pendingChapterLoad = false
            pendingChapterIndex = -1
            showChapterList = false
        }
    }

    // Load chapters when the list is empty (needed both for the chapter list view and for
    // direct reading), AND refetch the fresh extension list every time the chapter selection
    // screen opens (showChapterList -> true) so it always reflects the extension's current
    // chapters instead of a stale or downloaded-only fallback. Skipped while no manga
    // extension is selected: without a source there's no real chapter list, and the synthetic
    // fallback produces wrong chapter numbers for releasing manga. Exception: if this manga
    // has downloaded chapters, load them so the reader works offline.
    LaunchedEffect(manga.id, selectedExtension, showChapterList, downloadedChapterNumbers.isNotEmpty()) {
        val hasDownloads = downloadedChapterNumbers.isNotEmpty()
        val shouldLoad = chapters.isEmpty() || showChapterList
        android.util.Log.d("MangaReader", "LaunchedEffect(manga.id=${manga.id}, selectedExtension=${selectedExtension != null}, hasDownloads=$hasDownloads, showChapterList=$showChapterList): chapters.isEmpty()=${chapters.isEmpty()} shouldLoad=$shouldLoad")
        if (shouldLoad && (selectedExtension != null || hasDownloads)) {
            android.util.Log.d("MangaReader", "Fetching manga detail + chapters for manga.id=${manga.id}")
            // Best-effort detail fetch — when offline it may fail, but downloaded chapters
            // still load so the reader keeps working.
            runCatching { viewModel.fetchMangaDetail(manga.id) }
            viewModel.loadMangaChapters(manga.id, manga.title)
        }
    }

    // When the reader is opened from the Downloads tab (offline), the target chapter is
    // identified by its NUMBER, not its index in the online chapter list. Once chapters are
    // available, resolve the matching index and jump straight to it (the chapter list stays
    // closed — the caller already knows exactly which chapter to open).
    if (initialChapterNumber != null) {
        LaunchedEffect(chapters) {
            val target = initialChapterNumber
            val idx = chapters.indexOfFirst { it.chapterNumber == target }
            if (idx >= 0 && currentChapterIndex != idx) {
                android.util.Log.d("MangaReader", "Resolving initialChapterNumber=$target -> index $idx")
                currentChapterIndex = idx
                currentPageIndex = 0
                scrollProgress = 0f
                suppressResumeRestore = true
                showNextChapterButton = false
            }
        }
    }

    // Persist the page count of the loaded chapter so home's Continue Reading card can show
    // "pages left" for the manga being read. Skipped for offline reading, which must not write
    // to local tracking.
    LaunchedEffect(chapterImages) {
        val images = chapterImages
        if (!offlineOpen && images != null && images.isNotEmpty()) {
            viewModel.updateMangaChapterPages(manga.id, images.size)
        }
    }

    // When a chapter is opened that isn't the Continue-Reading resume target (index == progress),
    // clear the saved scroll position so backing out of a merely-opened chapter never leaves a
    // stale Continue Reading card (the page count is set on load, but scrollProgress 0 means the
    // card won't show). Covers direct opens (Read Now / home card) that skip selectChapter.
    // Skipped for offline reading — no local tracking writes.
    LaunchedEffect(currentChapterIndex) {
        if (!offlineOpen && currentChapterIndex != manga.progress) {
            viewModel.updateMangaScrollProgress(manga.id, 0f)
        }
    }

    // Handle the system back button — if a chapter is open, go back to the chapter list
    // (which stays open in the background); back on the chapter list closes the reader.
    // Exception: when opened from the Downloads tab (offline), back always closes the reader —
    // the offline chapter selection screen already provides the list, so landing on the
    // reader's own (online) chapter list with download icons would be wrong.
    fun handleReaderBack() {
        android.util.Log.d("MangaReader", "BACK pressed: showChapterList=$showChapterList chapters.size=${chapters.size} offlineOpen=$offlineOpen — " +
            if (!showChapterList && chapters.isNotEmpty() && !offlineOpen) "closing to chapter list" else "closing reader via onClose()")
        if (!showChapterList && chapters.isNotEmpty() && !offlineOpen) {
            // Was reading — go back to chapter list
            showChapterList = true
            showControls = false
        } else {
            // Was on chapter list (or opened offline) — close reader
            onClose()
        }
    }
    // The reader lives in a Compose Dialog, whose content does NOT inherit the dialog's back
    // dispatcher (Compose Dialog never provides LocalOnBackPressedDispatcherOwner), so a plain
    // BackHandler registers on the ACTIVITY's dispatcher and never fires — the dialog's own
    // back handling wins and dismisses the whole reader (landing on the home screen instead of
    // the chapter list). Instead we register the callback directly on the DIALOG's
    // OnBackPressedDispatcher. Compose hosts the dialog as an androidx.activity.ComponentDialog
    // (an OnBackPressedDispatcherOwner on every API level), reachable as the window callback.
    // This works both with predictive back (API 33+) and classic key events, so back always
    // returns to the chapter list first. The window may not be attached at first composition,
    // so the dialog is re-resolved shortly after; the callback is removed on dispose.
    val backCallback = remember {
        object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = handleReaderBack()
        }
    }
    LaunchedEffect(backCallback) {
        var dialog = view.findDialogWindow()?.callback as? ComponentDialog
        if (dialog == null) {
            delay(250)
            dialog = view.findDialogWindow()?.callback as? ComponentDialog
        }
        if (dialog != null) {
            dialog.onBackPressedDispatcher.addCallback(backCallback)
        }
        try {
            awaitCancellation()
        } finally {
            backCallback.remove()
        }
    }

    fun selectChapter(index: Int, startAtTop: Boolean = false) {
        android.util.Log.d("MangaReader", "selectChapter(index=$index startAtTop=$startAtTop) chapters.size=${chapters.size}")
        val chapter = chapters.getOrNull(index) ?: run {
            android.util.Log.w("MangaReader", "selectChapter: index $index out of range, IGNORED")
            return
        }
        android.util.Log.d("MangaReader", "selectChapter: opening chapterId='${chapter.chapterId}' title='${chapter.title}'")
        val resuming = !startAtTop && index == manga.progress
        currentChapterIndex = index
        currentPageIndex = 0
        scrollProgress = 0f
        suppressResumeRestore = !resuming
        showNextChapterButton = false
        if (resuming && manga.scrollProgress > 0f) {
            pendingResumeProgress = manga.scrollProgress
            android.util.Log.d("MangaReader", "selectChapter: resuming with scrollProgress=${manga.scrollProgress}")
        } else {
            pendingResumeProgress = -1f
        }
        if (!resuming) {
            viewModel.updateMangaScrollProgress(manga.id, 0f)
        }
        pendingChapterLoad = true
        pendingChapterIndex = index
        showControls = false
    }

    // A chapter only counts as read once it is actually read (scrolled past the sync
    // threshold). Opening a chapter does NOT mark it read in the list — that happens here,
    // triggered from the scroll callbacks when the threshold is crossed.
    fun markChapterReadInListUi() {
        if (currentChapterIndex in 0 until chapters.size) {
            readIndices.value = readIndices.value + currentChapterIndex
        }
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
        // Clear the stale error so the reader shows its loading state while retrying.
        displayedImagesError = null
        currentChapter?.let { chapter ->
            viewModel.loadChapterImages(chapter.chapterId, useDataSaver, manga.title, chapter.title, manga.id)
        }
    }

    // The reader is hosted in a Dialog; make that window's background transparent so the
    // chapter-loading overlays can be genuinely see-through (like the anime stream-loading
    // overlay) and reveal the screen behind instead of a solid black box.
    LaunchedEffect(Unit) {
        view.findDialogWindow()?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        delay(250)
        view.findDialogWindow()?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
    }

    // True while there is genuinely nothing to display (initial chapter-list or chapter-image
    // loading). Once a screen is showing (chapter list, a chapter, or an error), the background
    // stays opaque so that screen remains visible behind the translucent loading overlay during
    // chapter transitions instead of showing through to whatever is behind the dialog.
    val isLoadingAny = (chapters.isEmpty() && (isLoadingChapters || !hasLoadedChapters)) ||
        (!showChapterList && displayedImages == null && displayedImagesError == null)

    Box(
        modifier = Modifier.fillMaxSize().background(
            when {
                isLoadingAny -> Color.Transparent
                isOled -> Color.Black
                else -> Color(0xFF1a1a1a)
            }
        )
    ) {
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
                    // The online chapter list never shows download badges — the manga may have
                    // downloaded chapters, but this is the online source list (the Downloads
                    // screen's own list is the place for download UI).
                    downloadedChapterNumbers = emptySet(),
                    onChapterClick = { selectChapter(it) },
                    onContinueReading = {
                        val next = manga.progress.coerceAtLeast(0)
                        if (next in chapters.indices) selectChapter(next)
                    },
                    onRetryLoadChapters = {
                        scope.launch {
                            runCatching { viewModel.fetchMangaDetail(manga.id) }
                            viewModel.loadMangaChapters(manga.id, manga.title)
                        }
                    },
                    onBack = {
                        android.util.Log.d("MangaReader", "Chapter list back arrow tapped — handling like system back")
                        handleReaderBack()
                    }
                )
            }

            readerMode == ReaderMode.VERTICAL_SCROLL -> {
                // Keyed by chapter so a chapter change resets the scroll/pager state to the top;
                // displayedImages (possibly still the previous chapter's) stays on screen behind
                // the loading overlay until the new chapter's images arrive.
                key(currentChapter?.chapterId) {
                    VerticalScrollReader(
                        chapterImages = displayedImages ?: emptyList(),
                        chapterImagesError = displayedImagesError,
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
                            if (!offlineOpen) {
                                if (viewModel.onMangaScrollProgress(
                                        mangaId = manga.id,
                                        chapter = currentChapter,
                                        scrollPercent = it,
                                        mangaTitle = manga.title,
                                        mangaCover = manga.cover
                                    )
                                ) markChapterReadInListUi()
                            }
                            scrollProgress = it
                        },
                        onCurrentPage = { page -> currentPageIndex = page },
                        onToggleControls = { showControls = !showControls },
                        onRetry = { retryChapterLoad() }
                    )
                }
            }

            readerMode == ReaderMode.LEFT_TO_RIGHT || readerMode == ReaderMode.RIGHT_TO_LEFT -> {
                key(currentChapter?.chapterId) {
                    PagedMangaReader(
                        chapterImages = displayedImages ?: emptyList(),
                        chapterImagesError = displayedImagesError,
                        mode = readerMode,
                        initialPage = currentPageIndex.coerceIn(0, (displayedImages?.size ?: 1) - 1),
                        restorePage = if (pendingResumeProgress >= 0f && displayedImages != null && displayedImages!!.size > 1) {
                            val page = (pendingResumeProgress * (displayedImages!!.size - 1)).toInt().coerceIn(0, displayedImages!!.size - 1)
                            pendingResumeProgress = -1f
                            page
                        } else -1,
                        onToggleControls = { showControls = !showControls },
                        onPrevChapter = { if (!isFirstChapter) selectChapter(currentChapterIndex - 1) },
                        onNextChapter = { if (!isLastChapter) selectChapter(currentChapterIndex + 1) },
                        onRetry = { retryChapterLoad() },
                        onPageChanged = { page, total ->
                            currentPageIndex = page
                            if (total > 1) {
                                val progress = page.toFloat() / (total - 1).toFloat()
                                if (!offlineOpen) {
                                    if (viewModel.onMangaScrollProgress(
                                            mangaId = manga.id,
                                            chapter = currentChapter,
                                            scrollPercent = progress,
                                            mangaTitle = manga.title,
                                            mangaCover = manga.cover
                                        )
                                    ) markChapterReadInListUi()
                                }
                                scrollProgress = progress
                            }
                        }
                    )
                }
            }
        }

        // Loading overlay shown over whatever screen is currently displayed (the chapter list,
        // or the current chapter while a next/prev transition loads). Drawn after the branch
        // content so it sits on top, and dismissed once the new chapter's images are ready.
        if (pendingChapterLoad) {
            ChapterLoadingView()
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
                        IconButton(onClick = { handleReaderBack() }) {
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
                    // AniList sync-threshold marker — only meaningful when progress is pushed to
                    // AniList, so it is hidden while reading offline.
                    if (!offlineOpen) {
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
        }

        // ─── Overlay: "Next Chapter" button ──────────────────────────
        // Appears after the debounce once the reader settles at the end of a chapter (all modes)
        // when the Next Chapter Button setting is on. Tapping it opens the next chapter from the
        // top; scrolling away from the end hides it.
        AnimatedVisibility(
            visible = showNextChapterButton && !showChapterList && !isLastChapter,
            enter = fadeIn(tween(150)) + slideInVertically(tween(200)) { it / 2 },
            exit = fadeOut(tween(150)) + slideOutVertically(tween(200)) { it / 2 },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val nextChapter = chapters.getOrNull(currentChapterIndex + 1)
            val nextLabel = nextChapter?.let { ch ->
                val num = extractChapterNum(ch.title)
                if (num != "?") "Ch. $num" else ch.title
            } ?: ""
            Button(
                onClick = {
                    android.util.Log.d("MangaReader", "NEXT-BUTTON: tapped, opening chapter ${currentChapterIndex + 1}")
                    selectChapter(currentChapterIndex + 1, startAtTop = true)
                },
                shape = RoundedCornerShape(28.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 10.dp, pressedElevation = 14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 0.dp),
                modifier = Modifier
                    .padding(start = 16.dp, end = 16.dp, bottom = 84.dp)
                    .height(60.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(Color.White.copy(alpha = 0.22f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        text = "Next Chapter",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (nextLabel.isNotBlank()) {
                        Text(
                            text = nextLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
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
        // chapter selection screen) so the user is forced to pick a source first. Exception:
        // when this manga has downloaded chapters, keep reading offline without a source.
        if (selectedExtension == null && downloadedChapterNumbers.isEmpty()) {
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
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color.White)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Loading chapter...", color = Color.White)
        }
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
    restorePage: Int = -1,
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

    // Restore to a specific page after initial composition (for "Continue Reading" in horizontal modes).
    // The pager is already created by the time this fires, so scrollToPage jumps to the correct position.
    LaunchedEffect(restorePage, chapterImages.size) {
        if (restorePage in 0 until chapterImages.size && restorePage != pagerState.currentPage) {
            pagerState.scrollToPage(restorePage)
        }
    }

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

// Estimated heights (dp) used to center the current chapter inside its expanded group:
// the group header (title + subtitle + paddings) and one chapter row.
private val MangaChapterRowHeightDp = 56.dp

@Composable
fun MangaChapterListWithGroups(
    chapters: List<MangaChapter>,
    readIndices: Set<Int>,
    nextChapterToRead: Int,
    onChapterClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    isLoadingChapters: Boolean = true,
    hasLoadedChapters: Boolean = false,
    downloadedChapterNumbers: Set<Float> = emptySet(),
    onContinueReading: (() -> Unit)? = null,
    onRetryLoadChapters: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    onDeleteChapter: ((Int) -> Unit)? = null,
    onDeleteAll: (() -> Unit)? = null
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
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (isLoadingChapters || !hasLoadedChapters) {
                // Same transparent overlay design as the stream-fetching loading state
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Loading chapters...", color = Color.White)
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
        val listDensity = LocalDensity.current

        LaunchedEffect(chapters, nextChapterToRead) {
            if (nextChapterToRead < 0) return@LaunchedEffect
            val targetGroupIndex = filteredGroups.indexOfFirst { (_, groupList) ->
                groupList.any { it.first == nextChapterToRead }
            }
            if (targetGroupIndex < 0) {
                android.util.Log.w("MangaChapterList", "AUTOSCROLL no group holds index $nextChapterToRead (chapters=${chapters.size})")
                return@LaunchedEffect
            }
            val groupKey = filteredGroups[targetGroupIndex].first
            val groupList = filteredGroups[targetGroupIndex].second
            val targetChapterTitle = chapters.getOrNull(nextChapterToRead)?.title ?: "?"
            // Let the list finish its first layout before jumping.
            delay(100)
            // LazyColumn item index: 0=header, 1=search, then one item per group.
            val targetItem = targetGroupIndex + 2
            val rowInGroup = groupList.indexOfFirst { it.first == nextChapterToRead }
            if (rowInGroup < 0) {
                android.util.Log.w("MangaChapterList", "AUTOSCROLL row not found in group $groupKey for index $nextChapterToRead")
                return@LaunchedEffect
            }
            // Jump straight to the target chapter's group (instant, single frame — no
            // scroll-down-then-scroll-back-up dance), then glide it to roughly the
            // vertical center. The target row sits inside the auto-expanded group, so
            // measure the group's REAL viewport offset and size, then derive the row's
            // offset from the measured group height (header + N rows). contentPadding is
            // already included in the measured offset, so the centering is accurate.
            listState.scrollToItem(targetItem)
            // Wait until the jump is reflected in the layout before measuring.
            var attempts = 0
            while (attempts < 10 && listState.layoutInfo.visibleItemsInfo.none { it.index == targetItem }) {
                attempts++
                delay(16)
            }
            val layoutInfo = listState.layoutInfo
            val viewportHeight = layoutInfo.viewportSize.height
            val groupItem = layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetItem }
            if (viewportHeight <= 0 || groupItem == null) {
                android.util.Log.w("MangaChapterList", "AUTOSCROLL measure failed viewport=$viewportHeight item=${groupItem?.index}")
                return@LaunchedEffect
            }
            val rowHeightPx = with(listDensity) { MangaChapterRowHeightDp.toPx() }
            // Non-row portion of the group (header + padding) comes straight from the
            // MEASURED group height, so a wrong header constant can't throw it off.
            val nonRowHeightPx = (groupItem.size - groupList.size * rowHeightPx).coerceAtLeast(0f)
            val rowCenterInGroup = nonRowHeightPx + rowInGroup * rowHeightPx + rowHeightPx / 2f
            // scrollBy() is forward-positive: content moves UP the screen as delta grows, so
            // to bring the row center UP to the viewport center we scroll forward by the
            // row's current distance below center.
            val delta = (groupItem.offset + rowCenterInGroup) - viewportHeight / 2f
            android.util.Log.d("MangaChapterList", "AUTOSCROLL next=$nextChapterToRead ch=$targetChapterTitle " +
                "group=$groupKey item=$targetItem row=$rowInGroup/${groupList.size} " +
                "groupTop=${groupItem.offset} groupH=${groupItem.size} viewportH=$viewportHeight " +
                "rowH=$rowHeightPx nonRow=${nonRowHeightPx.toInt()} delta=${delta.toInt()}")
            if (delta != 0f) listState.animateScrollBy(delta)
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
                    onBack = onBack,
                    onDeleteAll = onDeleteAll
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
                            downloadedChapterNumbers = downloadedChapterNumbers,
                            initiallyExpanded = containsTarget,
                            onChapterClick = onChapterClick,
                            onDeleteChapter = onDeleteChapter?.let { delete ->
                                { absoluteIndex -> delete(absoluteIndex) }
                            }
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
    onBack: (() -> Unit)? = null,
    onDeleteAll: (() -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Title row
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
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$readCount of $totalCount read",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (onDeleteAll != null) {
                IconButton(onClick = onDeleteAll, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete all",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Progress section — ring + bar + continue button in a card-like surface
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Circular progress ring
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.size(44.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                    )
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }

                Spacer(Modifier.width(14.dp))

                // Linear progress bar
                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress.coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .background(
                                    Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)),
                                    RoundedCornerShape(2.dp)
                                )
                        )
                    }
                }

                if (nextChapterToRead < totalCount) {
                    Spacer(Modifier.width(14.dp))
                    FilledTonalButton(
                        onClick = onContinueReading,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        val nextChapterDisplay = chapters.getOrNull(nextChapterToRead)?.let { extractChapterNum(it.title) }
                            ?: "${nextChapterToRead + 1}"
                        Text("Ch. $nextChapterDisplay", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun MangaChapterSearchBar(
    query: String,
    onQueryChange: (String) -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (query.isNotEmpty()) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        tween(200),
        label = "searchBorder"
    )

    Surface(
        modifier = Modifier.fillMaxWidth().height(44.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(if (query.isNotEmpty()) 1.dp else 0.5.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
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
                    modifier = Modifier.size(24.dp)
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
    downloadedChapterNumbers: Set<Float> = emptySet(),
    onChapterClick: (Int) -> Unit,
    onDeleteChapter: ((Int) -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    LaunchedEffect(initiallyExpanded) {
        expanded = initiallyExpanded
    }

    val rotationAngle by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(250),
        label = "rotation"
    )

    val readInGroup = groupChapters.count { (index, _) -> index in readIndices }
    val readRatio = if (groupChapters.isNotEmpty()) readInGroup.toFloat() / groupChapters.size else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {
            // Group header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Compact circular progress ring
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { readRatio },
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                    )
                    Text(
                        text = "${(readRatio * 100).toInt()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                }

                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = groupKey,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (readInGroup > 0) "${groupChapters.size} chapters \u00B7 $readInGroup read"
                               else "${groupChapters.size} chapters",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (readInGroup > 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

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
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                    groupChapters.forEachIndexed { localIndex, (absoluteIndex, chapter) ->
                        MangaChapterRow(
                            chapter = chapter,
                            isRead = absoluteIndex in readIndices,
                            isNextToRead = absoluteIndex == nextChapterToRead,
                            isDownloaded = chapter.chapterNumber in downloadedChapterNumbers,
                            isLast = localIndex == groupChapters.lastIndex,
                            onClick = { onChapterClick(absoluteIndex) },
                            onDelete = onDeleteChapter?.let { delete -> { delete(absoluteIndex) } }
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
    isDownloaded: Boolean = false,
    isLast: Boolean = false,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val chNum = extractChapterNum(chapter.title)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = when {
            isNextToRead && !isRead -> MaterialTheme.colorScheme.primary.copy(alpha = 0.07f)
            else -> Color.Transparent
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Inline status indicator
            Box(
                modifier = Modifier.size(8.dp).clip(CircleShape).then(
                    when {
                        isRead -> Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                        isNextToRead -> Modifier.background(MaterialTheme.colorScheme.primary)
                        else -> Modifier.border(1.2.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), CircleShape)
                    }
                )
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Chapter number badge
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = when {
                    isNextToRead -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    isRead -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                },
                modifier = Modifier.size(width = 38.dp, height = 24.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = chNum,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        color = when {
                            isNextToRead -> MaterialTheme.colorScheme.primary
                            isRead -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (chapter.title.length > 10 && !chapter.title.startsWith("Chapter")) chapter.title else "Chapter $chNum",
                    style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        isNextToRead -> MaterialTheme.colorScheme.onBackground
                        isRead -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        else -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                    },
                    fontWeight = if (isNextToRead) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isDownloaded) {
                Icon(
                    Icons.Default.Download,
                    contentDescription = "Downloaded",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }

            if (isNextToRead && !isRead) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Next",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }

            if (onDelete != null) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.55f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
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
