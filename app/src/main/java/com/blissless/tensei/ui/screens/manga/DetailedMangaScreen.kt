package com.blissless.tensei.ui.screens.manga

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import android.content.Intent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.blissless.tensei.MainViewModel
import com.blissless.tensei.data.models.MangaChapter
import com.blissless.tensei.data.models.MangaCharacterNode
import com.blissless.tensei.data.models.MangaDetail
import com.blissless.tensei.data.models.MangaMedia
import com.blissless.tensei.data.models.MangaRelation
import com.blissless.tensei.data.models.MangaStaffEdge
import com.blissless.tensei.data.models.MangaRankingEntry
import com.blissless.tensei.data.models.TagData
import com.blissless.tensei.ui.screens.details.GenresCard
import com.blissless.tensei.ui.screens.details.SynopsisCard
import com.blissless.tensei.ui.screens.details.TagsCard
import com.blissless.tensei.ui.screens.details.easeOut
import com.blissless.tensei.ui.theme.StatusColors
import com.blissless.tensei.ui.theme.StatusLabels
import com.blissless.tensei.ui.theme.MangaStatusLabels
import com.blissless.tensei.viewmodel.clearMangaDetail
import com.blissless.tensei.viewmodel.fetchMangaDetail
import com.blissless.tensei.viewmodel.loadMangaChapters
import com.blissless.tensei.viewmodel.mangaChapters
import com.blissless.tensei.viewmodel.mangaDetail
import com.blissless.tensei.viewmodel.isLoadingManga
import com.blissless.tensei.viewmodel.isLoadingMangaChapters
import com.blissless.tensei.viewmodel.toggleMangaFavorite
import com.blissless.tensei.viewmodel.favoritedMangaIds
import com.blissless.tensei.viewmodel.updateMangaStatus
import com.blissless.tensei.viewmodel.updateMangaProgress
import com.blissless.tensei.viewmodel.removeMangaTracking
import com.blissless.tensei.viewmodel.selectedExtensionAuthority
import com.blissless.tensei.viewmodel.mangaCurrentlyReading
import com.blissless.tensei.viewmodel.mangaPlanningToRead
import com.blissless.tensei.viewmodel.mangaCompleted
import com.blissless.tensei.viewmodel.mangaPaused
import com.blissless.tensei.viewmodel.mangaDropped
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailedMangaScreen(
    manga: MangaMedia,
    viewModel: MainViewModel,
    isOled: Boolean = false,
    currentStatus: String? = null,
    currentProgress: Int? = null,
    isLoggedIn: Boolean = false,
    isFavorite: Boolean = false,
    preferEnglishTitles: Boolean = true,
    autoShowChapters: Boolean = false,
    onDismiss: () -> Unit,
    onSwipeToClose: () -> Unit = {},
    onUpdateStatus: (String?, Int?) -> Unit = { _, _ -> },
    onUpdateProgress: (Int) -> Unit = {},
    onRemove: () -> Unit = {},
    onRelationClick: (MangaRelation) -> Unit = {},
    onCharacterClick: (Int) -> Unit = {},
    onStaffClick: (Int) -> Unit = {},
    onViewAllCharacters: () -> Unit = {},
    onViewAllStaff: () -> Unit = {},
    onViewAllRelations: () -> Unit = {},
    onViewAllRecommendations: () -> Unit = {},
    onStartReader: (chapterIndex: Int) -> Unit = {},
    navigateToMangaDetail: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    var showFullDescription by remember { mutableStateOf(false) }
    var showAllTags by remember { mutableStateOf(false) }
    DisposableEffect(manga.id) {
        android.util.Log.d("MangaDetail", "DETAIL COMPOSED (dialog) manga.id=${manga.id} autoShowChapters=$autoShowChapters")
        onDispose {
            android.util.Log.d("MangaDetail", "DETAIL DISPOSED / LEAVING COMPOSITION manga.id=${manga.id}")
        }
    }

    val detail by viewModel.mangaDetail.collectAsState()
    val chapters by viewModel.mangaChapters.collectAsState()
    val isLoading by viewModel.isLoadingManga.collectAsState()
    val isLoadingChapters by viewModel.isLoadingMangaChapters.collectAsState()
    val selectedExtension by viewModel.selectedExtensionAuthority.collectAsState()
    val favoritedMangaIds by viewModel.favoritedMangaIds.collectAsState()
    // Reactive favorite state from AniList (overrides the static isFavorite parameter)
    val isMangaFavorited = manga.id in favoritedMangaIds

    // Live status/progress from the local tracking lists so the status dialog and
    // status chip update immediately after a change (no need to reopen the screen).
    val currentlyReadingManga by viewModel.mangaCurrentlyReading.collectAsState()
    val planningToReadManga by viewModel.mangaPlanningToRead.collectAsState()
    val completedManga by viewModel.mangaCompleted.collectAsState()
    val pausedManga by viewModel.mangaPaused.collectAsState()
    val droppedManga by viewModel.mangaDropped.collectAsState()
    val liveTrack = (currentlyReadingManga + planningToReadManga + completedManga + pausedManga + droppedManga)
        .find { it.id == manga.id }
    val liveStatus = liveTrack?.listStatus
    val liveProgress = liveTrack?.progress ?: currentProgress ?: manga.progress

    var showStatusDialog by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var selectedTagForDescription by remember { mutableStateOf<TagData?>(null) }
    var fullscreenImageUrl by remember { mutableStateOf<String?>(null) }

    var autoOpenedChapters by remember { mutableStateOf(false) }
    if (autoShowChapters) {
        LaunchedEffect(chapters, isLoadingChapters) {
            android.util.Log.d("MangaDetail", "autoShowChapters effect: isLoadingChapters=$isLoadingChapters chapters=${chapters.size} autoOpenedChapters=$autoOpenedChapters")
            if (!isLoadingChapters && chapters.isNotEmpty() && !autoOpenedChapters) {
                autoOpenedChapters = true
                android.util.Log.d("MangaDetail", "autoShowChapters: calling onStartReader(-1)")
                onStartReader(-1)
            }
        }
    }

    val slideOffset = remember { Animatable(1000f) }
    val dismissSlideOffset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(manga.id, selectedExtension) {
        android.util.Log.d("MangaDetail", "LaunchedEffect(manga.id=${manga.id}, ext=${selectedExtension != null}): fetching detail + chapters, title='${manga.title}'")
        viewModel.fetchMangaDetail(manga.id)
        android.util.Log.d("MangaDetail", "fetchMangaDetail returned; loading chapters for ${manga.id}")
        viewModel.loadMangaChapters(manga.id, manga.title)
        android.util.Log.d("MangaDetail", "loadMangaChapters done for ${manga.id}")
    }

    // NOTE: We intentionally do NOT call viewModel.clearMangaDetail() on dispose here.
    // When the user taps "Read Now", the detail screen closes (leaving composition) and
    // the reader opens. If we cleared manga detail/chapters here, the reader would lose
    // the already-loaded chapters and have to reload them (race condition with LaunchedEffect
    // key = manga.id, which won't re-fire). Instead, clearMangaDetail is called by
    // MainActivity when the reader is fully dismissed (onClose).

    LaunchedEffect(Unit) {
        slideOffset.animateTo(targetValue = 0f, animationSpec = tween(200, easing = LinearEasing))
    }

    fun dismissWithAnimation() {
        scope.launch {
            dismissSlideOffset.snapTo(0f)
            dismissSlideOffset.animateTo(targetValue = 1000f, animationSpec = tween(150, easing = LinearEasing))
            onDismiss()
            onSwipeToClose()
        }
    }

    // Handle system back button — dismiss the detail screen
    BackHandler { dismissWithAnimation() }

    val alpha by animateFloatAsState(
        targetValue = if (slideOffset.value > 0 || dismissSlideOffset.value > 0) 0f else 1f,
        animationSpec = tween(durationMillis = 200, easing = LinearEasing), label = "alpha"
    )

    // Blank listStatus (default) means the user has no status — normalize to null so the
    // "Add to List" section doesn't show a stale chip or "0 / x" progress.
    val statusToCheck = (liveStatus ?: currentStatus ?: manga.listStatus).takeIf { it.isNotBlank() }
    val statusProgress = liveProgress
    val totalCh = manga.totalChapters

    val displayData = detail ?: manga.asDetail()

    var lastDetailSig by remember { mutableStateOf("") }
    val detailSig = "${detail != null}|${displayData.description != null}|${displayData.genres.size}|${displayData.tags.size}|" +
        "${displayData.characters?.nodes?.size ?: 0}|${displayData.staff?.edges?.size ?: 0}|${displayData.relations.size}|" +
        "${displayData.recommendations.size}|${displayData.popularity}|${displayData.favourites}|${displayData.year}|" +
        "${displayData.format}|${displayData.source}|${displayData.volumes}|${displayData.chapters}|${displayData.status}|$isLoading"
    if (lastDetailSig != detailSig) {
        lastDetailSig = detailSig
        android.util.Log.d("MangaDetail", "Detail render: mangaId=${manga.id} detailLoaded=${detail != null} " +
            "usingFallback=${detail == null} " +
            "desc=${displayData.description != null} genres=${displayData.genres.size} tags=${displayData.tags.size} " +
            "chars=${displayData.characters?.nodes?.size ?: 0} staff=${displayData.staff?.edges?.size ?: 0} " +
            "relations=${displayData.relations.size} recs=${displayData.recommendations.size} " +
            "popularity=${displayData.popularity} favourites=${displayData.favourites} year=${displayData.year} " +
            "format=${displayData.format} source=${displayData.source} volumes=${displayData.volumes} " +
            "chapters=${displayData.chapters} status=${displayData.status} isLoading=$isLoading")
    }

    val statusDisplay = when (displayData.status) {
        "RELEASING" -> "Publishing"
        "FINISHED" -> "Finished"
        "NOT_YET_RELEASED" -> "Not Yet Published"
        "CANCELLED" -> "Cancelled"
        "HIATUS" -> "Hiatus"
        else -> displayData.status ?: "Unknown"
    }

    val formatDisplay = when (displayData.format) {
        "MANGA" -> "Manga"
        "NOVEL" -> "Light Novel"
        "ONE_SHOT" -> "One Shot"
        "DOUJIN" -> "Doujin"
        "MANHWA" -> "Manhwa"
        "MANHUA" -> "Manhua"
        "OEL" -> "OEL Manga"
        else -> displayData.format ?: "Unknown"
    }

    val windowInfo = LocalWindowInfo.current
    val screenHeightPx = windowInfo.containerSize.height.toFloat()
    val dismissThreshold = screenHeightPx / 3f
    val offsetY = remember { Animatable(0f) }

    val lazyListState = rememberLazyListState(
        initialFirstVisibleItemIndex = 0,
        initialFirstVisibleItemScrollOffset = 0
    )

    var isAtTop by remember { mutableStateOf(true) }

    LaunchedEffect(lazyListState) {
        snapshotFlow { lazyListState.firstVisibleItemIndex to lazyListState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                isAtTop = index == 0 && offset == 0
            }
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val currentOffset = offsetY.value
                if (currentOffset > 0) {
                    if (available.y < 0) {
                        scope.launch { offsetY.snapTo((currentOffset + available.y).coerceAtLeast(0f)) }
                        return available
                    }
                    if (available.y > 0) {
                        scope.launch { offsetY.snapTo(currentOffset + available.y) }
                        return available
                    }
                }
                if (isAtTop && currentOffset <= 10f && available.y > 0) {
                    scope.launch { offsetY.snapTo(available.y) }
                    return available
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val currentOffset = offsetY.value
                if (currentOffset == 0f) return Velocity.Zero
                // The sheet can only reach offsetY > 0 while the list is at the top (see
                // onPreScroll), so the old "isAtTop" gate here was redundant AND flaky: right
                // after scrolling back to the top the flag lags a frame, so a dismissal fling
                // was silently ignored and the sheet snapped back. Treat any release with the
                // sheet past a third of the screen, or a brisk downward fling, as a dismissal.
                val shouldDismiss = currentOffset > dismissThreshold || available.y > 300f
                if (shouldDismiss) {
                    dismissWithAnimation()
                } else {
                    scope.launch { offsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                }
                return available
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnBackPress = true, dismissOnClickOutside = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(0, (slideOffset.value + dismissSlideOffset.value).roundToInt()) }
                    .graphicsLayer { this.alpha = alpha }
                    .offset { IntOffset(0, offsetY.value.roundToInt()) }
                    .background(if (isOled) Color.Black else MaterialTheme.colorScheme.background)
                    .nestedScroll(nestedScrollConnection)
                    // Release-settle: a low-velocity release never produces a fling, so onPreFling
                    // is never invoked and the sheet would stay stuck half-translated; and a fling
                    // dismissal can be interrupted mid-animation, leaving the sheet faded but open.
                    // On pointer-up, wait for any fling handling to start, then settle the sheet:
                    // finish a stalled dismissal, dismiss past the threshold, or spring back.
                    .pointerInput(dismissThreshold) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            var anyPressed = true
                            while (anyPressed) {
                                val event = awaitPointerEvent()
                                anyPressed = event.changes.any { it.pressed }
                            }
                            // Defer the settle check to the composition scope: the gesture scope is
                            // a restricted coroutine scope (can't delay() from inside it), and this
                            // gives any fling-handling a moment to win first.
                            scope.launch {
                                delay(120)
                                if (offsetY.isRunning || dismissSlideOffset.isRunning) return@launch
                                if (dismissSlideOffset.value > 0f || offsetY.value > dismissThreshold) {
                                    // A fling dismissal started but stalled, or the sheet is past the
                                    // dismiss threshold — close it so it never stays stuck half-open.
                                    dismissWithAnimation()
                                } else if (offsetY.value > 0f) {
                                    offsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                }
                            }
                        }
                    }
            ) {
                // Banner
                if (!displayData.banner.isNullOrEmpty() || displayData.cover.isNotEmpty()) {
                val bannerImage = displayData.banner ?: displayData.cover
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clickable { fullscreenImageUrl = bannerImage }
                ) {
                    AsyncImage(
                        model = bannerImage, contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().graphicsLayer { this.alpha = 0.4f }
                    )
                    Box(
                        modifier = Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent, Color.Black.copy(alpha = 0.2f), Color.Black.copy(alpha = 0.85f)
                                )
                            )
                        )
                    )
                }
            }

            // Share button
            IconButton(
                onClick = {
                    val shareText = buildString {
                        append(displayData.title)
                        append("\n\n")
                        append("https://anilist.co/manga/${displayData.id}")
                    }
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, shareText)
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(sendIntent, null))
                },
                modifier = Modifier
                    .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp, end = 16.dp)
                    .align(Alignment.TopEnd)
                    .size(40.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    .zIndex(10f)
            ) {
                Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White, modifier = Modifier.size(24.dp))
            }

            // Close button
            IconButton(
                onClick = { dismissWithAnimation() },
                modifier = Modifier
                    .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp, start = 16.dp)
                    .align(Alignment.TopStart)
                    .size(40.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    .zIndex(10f)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(24.dp))
            }

            // Top bar pill
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp)
                    .width(40.dp).height(4.dp)
                    .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(2.dp)).zIndex(5f)
            )

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 60.dp)
                        .zIndex(10f),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            val statusBarsPadding = WindowInsets.statusBars.asPaddingValues()
            val navigationBarsPadding = WindowInsets.navigationBars.asPaddingValues()
            val density = LocalDensity.current

            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = 140.dp + statusBarsPadding.calculateTopPadding(),
                    bottom = 32.dp + navigationBarsPadding.calculateBottomPadding()
                )
            ) {
                // Cover + Title section
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box {
                            Box(
                                modifier = Modifier
                                    .width(140.dp).height(200.dp)
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable { fullscreenImageUrl = displayData.cover },
                                    shape = RoundedCornerShape(16.dp),
                                    shadowElevation = 16.dp,
                                    color = Color.Transparent
                                ) {
                                    AsyncImage(
                                        model = displayData.cover, contentDescription = displayData.title,
                                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clickable {
                                            fullscreenImageUrl = displayData.cover
                                        }
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Manga Title", displayData.title))
                            }.padding(vertical = 4.dp)) {
                                Text(
                                    text = displayData.title, style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold, maxLines = 10, overflow = TextOverflow.Clip,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp).padding(start = 4.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                            }
                            if (!displayData.titleEnglish.isNullOrEmpty() && displayData.titleEnglish != displayData.title) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Manga Title", displayData.titleEnglish))
                                }.padding(vertical = 4.dp)) {
                                    Text(
                                        text = displayData.titleEnglish, style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 10, overflow = TextOverflow.Clip,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            if (!displayData.titleNative.isNullOrEmpty()) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Manga Title", displayData.titleNative))
                                }.padding(vertical = 4.dp)) {
                                    Text(
                                        text = displayData.titleNative, style = MaterialTheme.typography.bodySmall,
                                        maxLines = 10, overflow = TextOverflow.Clip,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                displayData.averageScore?.let { score ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.background(Color(0xFFFFD700).copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Star, null, tint = Color(0xFFFFD700), modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            String.format(Locale.US, "%.1f", score / 10.0),
                                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFFFFD700)
                                        )
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = when (displayData.status) {
                                        "RELEASING" -> Color(0xFF4CAF50).copy(alpha = 0.2f)
                                        "FINISHED" -> Color(0xFF2196F3).copy(alpha = 0.2f)
                                        "NOT_YET_RELEASED" -> Color(0xFFFFC107).copy(alpha = 0.2f)
                                        "CANCELLED" -> Color(0xFFF44336).copy(alpha = 0.2f)
                                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    }
                                ) {
                                    Text(
                                        statusDisplay, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                                        color = when (displayData.status) {
                                            "RELEASING" -> Color(0xFF4CAF50)
                                            "FINISHED" -> Color(0xFF2196F3)
                                            "NOT_YET_RELEASED" -> Color(0xFFFFC107)
                                            "CANCELLED" -> Color(0xFFF44336)
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (displayData.year != null) {
                                    Text(displayData.year.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (displayData.year != null && displayData.format != null) {
                                    Text("\u2022", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                }
                                displayData.format?.let {
                                    Text(formatDisplay, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                val chapterCount = displayData.chapters.takeIf { it > 0 }
                                if (chapterCount != null) {
                                    Text("\u2022", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                    Text("$chapterCount ch.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                // Chapter Button (replaces WatchNowButton)
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    ) {
                        Box(modifier = Modifier.padding(12.dp)) {
                            Button(
                                onClick = {
                                    android.util.Log.d("MangaDetail", "Read Now tapped: chapters.size=${chapters.size} isLoadingChapters=$isLoadingChapters")
                                    onStartReader(-1)
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                enabled = chapters.isNotEmpty() && !isLoadingChapters,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                )
                            ) {
                                Icon(Icons.Default.PlayArrow, null, Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    if (liveProgress > 0) "Read Now" else "Start Reading",
                                    fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }

                // Status Card (Add to List / Change + Favorite)
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                            Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        "Add to List",
                                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                if (statusToCheck != null) {
                                    val statusColor = StatusColors[statusToCheck] ?: MaterialTheme.colorScheme.primary
                                    Column(horizontalAlignment = Alignment.End) {
                                        Surface(shape = RoundedCornerShape(6.dp), color = statusColor.copy(alpha = 0.12f)) {
                                            Text(
                                                text = MangaStatusLabels[statusToCheck] ?: statusToCheck,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = statusColor, fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                            )
                                        }
                                        if (totalCh > 0) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(text = "$statusProgress", style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                                Text(text = " / $totalCh", style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Light, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            if (statusToCheck != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Button(
                                        onClick = { showStatusDialog = true },
                                        modifier = Modifier.weight(1f).height(44.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Change", fontWeight = FontWeight.SemiBold)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    OutlinedButton(
                                        onClick = {
                                            if (isLoggedIn) {
                                                viewModel.toggleMangaFavorite(manga.id)
                                            } else {
                                                viewModel.toggleOfflineFavorite(manga.id, manga.title, manga.cover, manga.banner, manga.year, manga.averageScore)
                                            }
                                        },
                                        modifier = Modifier.height(44.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (isMangaFavorited) Color(0xFFFF1744).copy(alpha = 0.15f) else Color.Transparent,
                                            contentColor = if (isMangaFavorited) Color(0xFFFF1744) else MaterialTheme.colorScheme.onSurface
                                        ),
                                        border = BorderStroke(1.5.dp,
                                            if (isMangaFavorited) Color(0xFFFF1744) else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                        )
                                    ) {
                                        Icon(
                                            if (isMangaFavorited) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                            null, Modifier.size(20.dp),
                                            tint = if (isMangaFavorited) Color(0xFFFF1744) else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Button(
                                        onClick = { showStatusDialog = true },
                                        modifier = Modifier.weight(1f).height(44.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Add to List", fontWeight = FontWeight.SemiBold)
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    OutlinedButton(
                                        onClick = {
                                            if (isLoggedIn) {
                                                viewModel.toggleMangaFavorite(manga.id)
                                            } else {
                                                viewModel.toggleOfflineFavorite(manga.id, manga.title, manga.cover, manga.banner, manga.year, manga.averageScore)
                                            }
                                        },
                                        modifier = Modifier.height(44.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (isMangaFavorited) Color(0xFFFF1744).copy(alpha = 0.15f) else Color.Transparent,
                                            contentColor = if (isMangaFavorited) Color(0xFFFF1744) else MaterialTheme.colorScheme.onSurface
                                        ),
                                        border = BorderStroke(1.5.dp,
                                            if (isMangaFavorited) Color(0xFFFF1744) else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                        )
                                    ) {
                                        Icon(
                                            if (isMangaFavorited) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                            null, Modifier.size(20.dp),
                                            tint = if (isMangaFavorited) Color(0xFFFF1744) else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Info Card (manga specs)
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                    MangaInfoCard(displayData = displayData, statusDisplay = statusDisplay)
                }

                // Genres
                if (displayData.genres.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(20.dp))
                        GenresCard(genres = displayData.genres)
                    }
                }

                // Tags
                if (displayData.tags.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(20.dp))
                        TagsCard(
                            tags = displayData.tags,
                            showAllTags = showAllTags,
                            onTagClick = { selectedTagForDescription = it },
                            onToggleShowAll = { showAllTags = !showAllTags },
                        )
                    }
                }

                // Synopsis
                if (!displayData.description.isNullOrEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(20.dp))
                        SynopsisCard(
                            description = displayData.description,
                            showFullDescription = showFullDescription,
                            onToggleShowFull = { showFullDescription = !showFullDescription },
                        )
                    }
                }

                // Relations
                val relations = displayData.relations
                if (relations.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(20.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                Brush.linearGradient(
                                                    listOf(
                                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
                                                    )
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text("Relations", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                        Text("Connected series", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), letterSpacing = 0.5.sp)
                                    }
                                    Spacer(modifier = Modifier.weight(1f))
                                    TextButton(onClick = onViewAllRelations) {
                                        Text("View All", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    itemsIndexed(items = relations, key = { _, r -> r.id }) { _, relation ->
                                        Column(
                                            modifier = Modifier
                                                .width(110.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable { onRelationClick(relation) }
                                                .padding(4.dp)
                                        ) {
                                            Box(modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f)) {
                                                Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxSize(),
                                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A))
                                                ) {
                                                    AsyncImage(model = relation.cover, contentDescription = relation.title,
                                                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                                }
                                                Surface(modifier = Modifier.padding(6.dp).align(Alignment.TopStart),
                                                    shape = RoundedCornerShape(6.dp), color = Color.Black.copy(alpha = 0.8f)
                                                ) {
                                                    Text(relation.relationType.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() },
                                                        style = MaterialTheme.typography.labelSmall, color = Color.White,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                                }
                                                val chapterText = when {
                                                    relation.chapters != null && relation.chapters > 0 -> "${relation.chapters} ch."
                                                    else -> null
                                                }
                                                chapterText?.let {
                                                    Surface(modifier = Modifier.padding(6.dp).align(Alignment.BottomStart),
                                                        shape = RoundedCornerShape(6.dp), color = Color.Black.copy(alpha = 0.8f)
                                                    ) {
                                                        Text(it, style = MaterialTheme.typography.labelSmall, color = Color.White,
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(relation.title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium,
                                                maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onBackground,
                                                modifier = Modifier.height(32.dp))
                                            relation.format?.let { format ->
                                                val fmtDisplay = when (format) {
                                                    "MANGA" -> "Manga"; "NOVEL" -> "Novel"; "ONE_SHOT" -> "One Shot"
                                                    "MANHWA" -> "Manhwa"; "MANHUA" -> "Manhua"; else -> format
                                                }
                                                Text(fmtDisplay, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Recommendations
                val recommendations = displayData.recommendations
                if (recommendations.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(20.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Box(
                                        modifier = Modifier.size(38.dp).clip(RoundedCornerShape(12.dp))
                                            .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f), MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)))),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("Recommendations", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    Spacer(modifier = Modifier.weight(1f))
                                    TextButton(onClick = onViewAllRecommendations) {
                                        Text("View All", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))

                                val recListState = rememberLazyListState()
                                val isRecScrolling by remember {
                                    derivedStateOf { recListState.isScrollInProgress }
                                }
                                val recCameraDistancePx = with(density) { 12.dp.toPx() }
                                val recIntro = ((1000f - slideOffset.value) / 1000f).coerceIn(0f, 1f)

                                LazyRow(
                                    state = recListState,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    itemsIndexed(
                                        items = recommendations.take(20),
                                        key = { _, rec -> rec.id }
                                    ) { index, rec ->
                                        val title = if (preferEnglishTitles && !rec.titleEnglish.isNullOrBlank()) rec.titleEnglish else rec.title

                                        val recLayoutInfo by remember { derivedStateOf { recListState.layoutInfo } }
                                        val recVisibleItems = recLayoutInfo.visibleItemsInfo
                                        val recItemInfo = recVisibleItems.find { it.index == index }

                                        val recCenterOffset = if (recItemInfo != null) {
                                            val itemCenter = recItemInfo.offset + recItemInfo.size / 2
                                            val screenCenter = (recLayoutInfo.viewportSize.width / 2).toFloat()
                                            (itemCenter - screenCenter) / screenCenter
                                        } else {
                                            0f
                                        }

                                        val recAnimatedOffset by animateFloatAsState(
                                            targetValue = if (isRecScrolling) recCenterOffset.coerceIn(-1.5f, 1.5f) else 0f,
                                            animationSpec = if (isRecScrolling) {
                                                spring(
                                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                                    stiffness = Spring.StiffnessMedium
                                                )
                                            } else {
                                                spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessLow
                                                )
                                            },
                                            label = "recCenterOffset"
                                        )

                                        val recScrollScale = 1f - (recAnimatedOffset.absoluteValue * 0.25f).coerceAtMost(0.25f)
                                        val recScrollAlpha = 1f - (recAnimatedOffset.absoluteValue * 0.4f).coerceAtMost(0.6f)
                                        val recScrollTranslationX = recAnimatedOffset * -20f
                                        val recScrollRotationY = (recAnimatedOffset * 15f).coerceIn(-15f, 15f)

                                        val recIndexFloat = index.toFloat()
                                        val recStaggeredProgress = ((recIntro * 1000f - (recIndexFloat * 40f)) / 1000f).coerceIn(0f, 1f)
                                        val recEasedProgress = easeOut(recStaggeredProgress)
                                        val recIntroScale = if (recIntro >= 1f) 1f else 0.85f + recEasedProgress * 0.15f
                                        val recIntroAlpha = if (recIntro >= 1f) 1f else recEasedProgress

                                        Column(
                                            modifier = Modifier
                                                .width(110.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable { navigateToMangaDetail(rec.id) }
                                                .graphicsLayer {
                                                    scaleX = recIntroScale * recScrollScale
                                                    scaleY = recIntroScale * recScrollScale
                                                    this.alpha = recIntroAlpha * recScrollAlpha
                                                    translationX = recScrollTranslationX
                                                    rotationY = recScrollRotationY
                                                    cameraDistance = recCameraDistancePx
                                                }
                                                .padding(4.dp)
                                        ) {
                                            Box(modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f)) {
                                                Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxSize(),
                                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A))
                                                ) {
                                                    AsyncImage(model = rec.cover, contentDescription = title,
                                                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                                }
                                                rec.averageScore?.let { score ->
                                                    Surface(
                                                        modifier = Modifier.padding(6.dp).align(Alignment.TopEnd),
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = Color.Black.copy(alpha = 0.8f)
                                                    ) {
                                                        Text("${(score / 10.0).toString().take(3)}",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = Color(0xFFFFD700),
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium,
                                                maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onBackground,
                                                modifier = Modifier.height(32.dp))
                                            Box(modifier = Modifier.fillMaxWidth().height(16.dp), contentAlignment = Alignment.CenterStart) {
                                                rec.format?.let { format ->
                                                    val fmtDisplay = when (format) {
                                                        "MANGA" -> "Manga"; "NOVEL" -> "Novel"; "ONE_SHOT" -> "One Shot"
                                                        "MANHWA" -> "Manhwa"; "MANHUA" -> "Manhua"
                                                        "TV" -> "TV"; "MOVIE" -> "Movie"
                                                        else -> format
                                                    }
                                                    Text(fmtDisplay, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Characters
                val characters = displayData.characters?.nodes
                if (!characters.isNullOrEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(20.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Box(
                                        modifier = Modifier.size(38.dp).clip(RoundedCornerShape(12.dp))
                                            .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f), MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)))),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Group, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text("Characters", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                        Text("Appearing characters", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), letterSpacing = 0.5.sp)
                                    }
                                    Spacer(modifier = Modifier.weight(1f))
                                    TextButton(onClick = onViewAllCharacters) {
                                        Text("View All", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    itemsIndexed(characters, key = { _, c -> c.id }) { _, character ->
                                        Column(
                                            modifier = Modifier.width(80.dp).clip(RoundedCornerShape(12.dp))
                                                .clickable { character.id.let(onCharacterClick) }.padding(4.dp)
                                        ) {
                                            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                                                Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxSize(),
                                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A))
                                                ) {
                                                    AsyncImage(model = character.image?.large ?: "", contentDescription = character.name?.full,
                                                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(character.name?.full ?: "Unknown", style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                                color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.height(28.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Staff
                val staff = displayData.staff?.edges
                if (!staff.isNullOrEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(20.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Box(
                                        modifier = Modifier.size(38.dp).clip(RoundedCornerShape(12.dp))
                                            .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f), MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)))),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text("Staff", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                        Text("Production crew", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), letterSpacing = 0.5.sp)
                                    }
                                    Spacer(modifier = Modifier.weight(1f))
                                    TextButton(onClick = onViewAllStaff) {
                                        Text("View All", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    itemsIndexed(staff, key = { index, _ -> "staff_$index" }) { _, staffEdge ->
                                        staffEdge.node?.let { staffNode ->
                                            Column(
                                                modifier = Modifier.width(80.dp).clip(RoundedCornerShape(12.dp))
                                                    .clickable { staffNode.id.let(onStaffClick) }.padding(4.dp)
                                            ) {
                                                Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                                                    Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxSize(),
                                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A))
                                                    ) {
                                                        AsyncImage(model = staffNode.image?.large ?: "", contentDescription = staffNode.name?.full,
                                                            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(staffNode.name?.full ?: "Unknown", style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                                    color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.height(28.dp))
                                                staffEdge.role?.let { role ->
                                                    Text(role.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() },
                                                        style = MaterialTheme.typography.labelSmall, maxLines = 1,
                                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Rankings
                val rankings = displayData.rankings
                if (rankings.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(20.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier.size(38.dp).clip(RoundedCornerShape(12.dp))
                                            .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f), MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)))),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("Rankings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                rankings.forEach { ranking ->
                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text("#${ranking.rank ?: "-"}", fontWeight = FontWeight.Bold, fontSize = 18.sp,
                                            color = if (ranking.allTime == true) Color(0xFFfbbf24) else MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(12.dp))
                                        Column {
                                            Text("${ranking.context ?: ""} ${ranking.type ?: ""}".trim(),
                                                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                            if (ranking.allTime == true) {
                                                Text("All Time", style = MaterialTheme.typography.labelSmall, color = Color(0xFFfbbf24))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Synonyms
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }

    if (selectedTagForDescription != null) {
        val tag: TagData = selectedTagForDescription!!
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { selectedTagForDescription = null },
            sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 40.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(tag.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
                tag.rank?.let { rank ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Rank: $rank%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                Spacer(modifier = Modifier.height(16.dp))
                val description = tag.description ?: "No description available."
                val cleanDescription = description.replace("<br>", "\n").replace("<br/>", "\n")
                    .replace("<b>", "").replace("</b>", "").replace("<i>", "").replace("</i>", "")
                    .replace("&quot;", "\"").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                Text(cleanDescription, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 24.sp)
            }
        }
    }

    if (fullscreenImageUrl != null) {
        Dialog(onDismissRequest = { fullscreenImageUrl = null }) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { fullscreenImageUrl = null } })
                AsyncImage(model = fullscreenImageUrl, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(16.dp))
            }
        }
    }

    if (showStatusDialog) {
        MangaStatusDialog(
            title = manga.title,
            coverUrl = manga.cover,
            currentStatus = statusToCheck,
            currentProgress = statusProgress,
            totalChapters = totalCh,
            onUpdate = { status, progress ->
                android.util.Log.d("MangaSyncDebug", "MangaStatusDialog onUpdate: mangaId=${manga.id} status='$status' progress=$progress")
                onUpdateStatus(status, progress)
                if (progress != null) {
                    onUpdateProgress(progress)
                    viewModel.updateMangaProgress(manga.id, progress.toFloat())
                }
                showStatusDialog = false
            },
            onRemove = {
                android.util.Log.d("MangaSyncDebug", "MangaStatusDialog onRemove: mangaId=${manga.id}")
                onRemove()
                viewModel.removeMangaTracking(manga.id)
                showStatusDialog = false
            },
            onDismiss = { showStatusDialog = false }
        )
    }
}

@Composable
private fun MangaInfoCard(
    displayData: MangaDetail,
    statusDisplay: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(38.dp).clip(RoundedCornerShape(12.dp))
                        .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f), MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("Information", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text("Overview & details", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), letterSpacing = 0.5.sp)
                }
            }
            Spacer(modifier = Modifier.height(18.dp))

            // Hero stats: Score / Chapters / Volumes / Popularity / Favorites
            val heroStats = buildList {
                displayData.averageScore?.takeIf { it > 0 }?.let { add("Score" to String.format(Locale.US, "%.1f", it / 10.0)) }
                displayData.chapters.takeIf { it > 0 }?.let { add("Chapters" to it.toString()) }
                displayData.volumes?.takeIf { it > 0 }?.let { add("Volumes" to it.toString()) }
                displayData.popularity?.takeIf { it > 0 }?.let { add("Popularity" to formatNumber(it)) }
                displayData.favourites?.takeIf { it > 0 }?.let { add("Favorites" to formatNumber(it)) }
            }

            if (heroStats.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    heroStats.forEachIndexed { index, (label, value) ->
                        val accent = when (label) {
                            "Score" -> Color(0xFFFFB300)
                            "Chapters" -> MaterialTheme.colorScheme.primary
                            "Volumes" -> MaterialTheme.colorScheme.tertiary
                            "Popularity" -> MaterialTheme.colorScheme.primary
                            "Favorites" -> Color(0xFFEC4899)
                            else -> MaterialTheme.colorScheme.primary
                        }
                        val icon = when (label) {
                            "Score" -> Icons.Default.Star
                            "Favorites" -> Icons.Default.Favorite
                            else -> null
                        }
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                icon?.let {
                                    Icon(it, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                                    color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium,
                                letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f))
                        }
                        if (index < heroStats.lastIndex) {
                            Box(modifier = Modifier.width(1.dp).height(28.dp)
                                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Bento spec grid
            val specs = buildList {
                displayData.format?.let {
                    add(SpecEntry(label = "Format", value = it.replace("_", " ").lowercase().replaceFirstChar { c -> c.uppercase() }, icon = Icons.Default.Category))
                }
                displayData.status?.let {
                    add(SpecEntry(label = "Status", value = statusDisplay, icon = Icons.Default.PlayArrow))
                }
                displayData.source?.let {
                    add(SpecEntry(label = "Source", value = it.replace("_", " ").lowercase().replaceFirstChar { c -> c.uppercase() }, icon = Icons.Default.Description))
                }
                if (displayData.chapters > 0) {
                    add(SpecEntry(label = "Chapters", value = displayData.chapters.toString()))
                }
                displayData.volumes?.let {
                    add(SpecEntry(label = "Volumes", value = it.toString()))
                }
                displayData.year?.let {
                    add(SpecEntry(label = "Year", value = it.toString()))
                }
            }

            var i = 0
            while (i < specs.size) {
                val current = specs[i]
                val next = specs.getOrNull(i + 1)
                if (next != null && !current.fullSpan) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BentoSpecCell(current, modifier = Modifier.weight(1f))
                        BentoSpecCell(next, modifier = Modifier.weight(1f))
                    }
                    i += 2
                } else {
                    BentoSpecCell(current, modifier = Modifier.fillMaxWidth())
                    i += 1
                }
                if (i < specs.size) {
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }
}

private data class SpecEntry(
    val label: String,
    val value: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    val fullSpan: Boolean = false
)

@Composable
private fun BentoSpecCell(spec: SpecEntry, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f))
            .padding(14.dp)
    ) {
        Text(spec.label.uppercase(), style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium, letterSpacing = 1.2.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f))
        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            spec.icon?.let { ic ->
                Icon(ic, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(spec.value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun formatNumber(n: Int): String = when {
    n >= 1_000_000 -> "${n / 1_000_000}M"
    n >= 1_000 -> "${n / 1_000}K"
    else -> n.toString()
}

private fun MangaMedia.asDetail(): MangaDetail = MangaDetail(
    id = id,
    title = title,
    titleEnglish = titleEnglish,
    cover = cover,
    banner = banner,
    chapters = totalChapters,
    volumes = totalVolumes,
    status = status,
    averageScore = averageScore,
    genres = genres,
    year = year,
    format = format,
    siteUrl = siteUrl,
    malId = malId
)
