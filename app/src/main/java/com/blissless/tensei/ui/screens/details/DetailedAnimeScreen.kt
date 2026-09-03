package com.blissless.tensei.ui.screens.details

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.core.net.toUri
import coil.compose.AsyncImage
import com.blissless.tensei.MainViewModel
import com.blissless.tensei.data.models.AnimeMedia
import com.blissless.tensei.data.models.AnimeRelation
import com.blissless.tensei.data.models.DetailedAnimeData
import com.blissless.tensei.data.models.ExploreAnime
import com.blissless.tensei.data.models.LocalAnimeEntry
import com.blissless.tensei.data.models.TagData
import com.blissless.tensei.dialogs.AnimeRatingSheet
import com.blissless.tensei.dialogs.HomeAnimeStatusDialog
import com.blissless.tensei.dialogs.userScoreToDisplay
import com.blissless.tensei.ui.components.rememberCinematicAnimation
import com.blissless.tensei.ui.theme.StatusColors
import com.blissless.tensei.ui.theme.StatusLabels
import com.blissless.tensei.ui.screens.episode.EpisodeSelectionDialog
import com.blissless.tensei.ui.screens.episode.RichEpisodeScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
// Extension functions on MainViewModel (defined in com.blissless.tensei.viewmodel)
import com.blissless.tensei.viewmodel.loadAvailableMagnetExtensions
import com.blissless.tensei.viewmodel.toggleAniListFavorite


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailedAnimeScreen(
    anime: DetailedAnimeData,
    viewModel: MainViewModel,
    isOled: Boolean = false,
    currentStatus: String? = null,
    currentProgress: Int? = null,
    isLoggedIn: Boolean = false,
    isFavorite: Boolean = false,
    simplifyEpisodeMenu: Boolean = false,
    onDismiss: () -> Unit,
    onNavigateBack: () -> Unit = onDismiss,
    onSwipeToClose: () -> Unit = {},
    stackDepth: Int = 1,
    onPlayEpisode: (Int, String?) -> Unit = { _, _ -> },
    onUpdateStatus: (String?) -> Unit = {},
    onUpdateProgress: (Int) -> Unit = {},
    onRemove: () -> Unit = {},
    onUpdateLocalStatus: (String?) -> Unit = {},
    onRemoveLocalStatus: () -> Unit = {},
    onRelationClick: (AnimeRelation) -> Unit = {},
    onRecommendationClick: (ExploreAnime) -> Unit = {},
    onCharacterClick: (Int) -> Unit = {},
    onStaffClick: (Int) -> Unit = {},
    onViewAllCast: () -> Unit = {},
    onViewAllStaff: () -> Unit = {},
    onViewAllRelations: (Int, String, String?) -> Unit = { _, _, _ -> },
    onViewAllRecommendations: (Int, String, String?) -> Unit = { _, _, _ -> },
    preferEnglishTitles: Boolean = true,
    onNavigateToSettings: (() -> Unit)? = null,
    onNoExtension: () -> Unit = {},
    initialCardBounds: MainViewModel.CardBounds? = null
) {
    val context = LocalContext.current
    var showFullDescription by remember { mutableStateOf(false) }
    var showAllTags by remember { mutableStateOf(false) }

    var detailedData by remember { mutableStateOf<DetailedAnimeData?>(null) }
    var isLoadingDetails by remember { mutableStateOf(true) }
    var relations by remember { mutableStateOf<List<AnimeRelation>>(emptyList()) }

    var isVisible by remember { mutableStateOf(false) }
    var previousAnimeId by remember { mutableIntStateOf(anime.id) }
    var isTransitioning by remember { mutableStateOf(false) }

    var selectedTagForDescription by remember { mutableStateOf<TagData?>(null) }
    var fullscreenImageUrl by remember { mutableStateOf<String?>(null) }
    var showEpisodeSelection by remember { mutableStateOf(false) }
    var showNoDefaultExtDialog by remember { mutableStateOf(false) }
    var showStatusDialog by remember { mutableStateOf(false) }
    var showRatingSheet by remember { mutableStateOf(false) }

    val defaultExtPkg by viewModel.defaultExtensionPackage.collectAsState()
    val defaultMagnetExt by viewModel.defaultMagnetExtension.collectAsState()
    val streamMethod by viewModel.streamMethod.collectAsState()
    val magnetExtensions by viewModel.availableMagnetExtensions.collectAsState()
    val localFavorites by viewModel.localFavorites.collectAsState()
    val aniListFavorites by viewModel.aniListFavorites.collectAsState()
    val localAnimeStatus by viewModel.localAnimeStatus.collectAsState()
    val currentlyWatching by viewModel.currentlyWatching.collectAsState()
    val planningToWatch by viewModel.planningToWatch.collectAsState()
    val completed by viewModel.completed.collectAsState()
    val onHold by viewModel.onHold.collectAsState()
    val dropped by viewModel.dropped.collectAsState()
    val localFavExists = localFavorites.containsKey(anime.id)
    val aniListIsFavorite = aniListFavorites.any { it.id == anime.id }
    val effectiveIsFavorite = when {
        isLoggedIn -> isFavorite || aniListIsFavorite
        else -> localFavExists
    }
    val effectiveLocalStatus = if (isLoggedIn) null else localAnimeStatus[anime.id]?.status
    val effectiveLocalProgress = if (isLoggedIn) null else localAnimeStatus[anime.id]?.progress

    var displayProgress by remember { mutableIntStateOf(currentProgress ?: effectiveLocalProgress ?: 0) }
    LaunchedEffect(currentProgress, effectiveLocalProgress) {
        displayProgress = currentProgress ?: effectiveLocalProgress ?: 0
    }

    val effectiveOnUpdateStatus = if (isLoggedIn) onUpdateStatus else onUpdateLocalStatus
    val effectiveOnUpdateProgress = if (isLoggedIn) onUpdateProgress else { progress ->
        viewModel.setLocalAnimeStatus(
            anime.id,
            localAnimeStatus[anime.id]?.copy(progress = progress)
                ?: LocalAnimeEntry(
                    id = anime.id,
                    status = effectiveLocalStatus ?: "CURRENT",
                    progress = progress,
                    totalEpisodes = anime.episodes,
                            title = anime.title,
                            cover = anime.cover,
                    banner = anime.banner,
                    year = anime.year,
                    averageScore = anime.averageScore
                )
        )
    }
    val effectiveOnRemove = if (isLoggedIn) onRemove else onRemoveLocalStatus

    val statusToCheck = if (isLoggedIn) currentStatus else effectiveLocalStatus
    val statusProgress = displayProgress
    val totalEps = anime.episodes

    val listUserScore = (currentlyWatching + planningToWatch + completed + onHold + dropped)
        .firstOrNull { it.id == anime.id }?.userScore
    val effectiveUserScore = (if (isLoggedIn) listUserScore else localAnimeStatus[anime.id]?.score)
        ?.takeIf { it > 0 }

    val slideOffset = remember { Animatable(1000f) }
    val dismissSlideOffset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(Unit) {
        viewModel.loadAvailableMagnetExtensions()
    }

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
            onSwipeToClose()
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (slideOffset.value > 0 || dismissSlideOffset.value > 0) 0f else 1f,
        animationSpec = tween(durationMillis = 200, easing = LinearEasing),
        label = "alpha"
    )

    var transitionProgress by remember { mutableFloatStateOf(0f) }

    val coverAnimationProgress = remember(anime.id) { Animatable(0f) }

    LaunchedEffect(initialCardBounds) {
        if (initialCardBounds != null) {
            transitionProgress = 1f
            coverAnimationProgress.snapTo(0f)
            coverAnimationProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
            )
            isVisible = true
        } else {
            isVisible = true
            coverAnimationProgress.snapTo(0f)
            transitionProgress = 0f
        }
    }

    LaunchedEffect(anime.id, initialCardBounds) {
        isLoadingDetails = true

        if (previousAnimeId != 0 && previousAnimeId != anime.id) {
            isTransitioning = true
            coverAnimationProgress.snapTo(0f)
            delay(150.milliseconds)
            isTransitioning = false
        }
        previousAnimeId = anime.id

        // Try to fetch detailed data
        try {
            detailedData = viewModel.fetchDetailedAnimeData(anime.id, anime.malId)
            // If fetch returns null (not found or error), keep using original anime data
            if (detailedData == null) {
                detailedData = anime
            }
            relations = detailedData?.relations ?: anime.relations
        } catch (_: Exception) {
            detailedData = anime
        } finally {
            isLoadingDetails = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            isLoadingDetails = false
        }
    }

    val displayData = detailedData ?: anime

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

    val statusDisplay = when (displayData.status) {
        "RELEASING" -> "Airing"
        "FINISHED" -> "Released"
        "NOT_YET_RELEASED" -> "Not Yet Aired"
        "CANCELLED" -> "Cancelled"
        "HIATUS" -> "Hiatus"
        else -> displayData.status ?: "Unknown"
    }

    val formatDisplay = when (displayData.format) {
        "TV" -> "TV Series"
        "TV_SHORT" -> "TV Short"
        "MOVIE" -> "Movie"
        "SPECIAL" -> "Special"
        "OVA" -> "OVA"
        "ONA" -> "ONA"
        "MUSIC" -> "Music"
        else -> displayData.format ?: "Unknown"
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
                    scope.launch {
                        offsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                    }
                }

                return available
            }
        }
    }

    Dialog(
        onDismissRequest = onNavigateBack,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        val animeMedia = AnimeMedia(
            id = anime.id,
            title = anime.title,
            titleEnglish = anime.titleEnglish,
            cover = anime.cover,
            banner = anime.banner,
            progress = displayProgress,
            totalEpisodes = anime.episodes,
            latestEpisode = anime.latestEpisode,
            status = anime.status ?: "",
            averageScore = anime.averageScore,
            genres = anime.genres,
            listStatus = ""
        )
        if (showEpisodeSelection) {
            if (simplifyEpisodeMenu) {
                EpisodeSelectionDialog(
                    anime = animeMedia,
                    isOled = isOled,
                    onDismiss = { showEpisodeSelection = false },
                    onEpisodeSelect = { episode, _ ->
                        showEpisodeSelection = false
                        onPlayEpisode(episode, null)
                    }
                )
            } else {
                RichEpisodeScreen(
                    anime = animeMedia,
                    viewModel = viewModel,
                    isOled = isOled,
                    preferEnglishTitles = preferEnglishTitles,
                    onDismiss = { showEpisodeSelection = false },
                    onEpisodeSelect = { episode, _ ->
                        showEpisodeSelection = false
                        onPlayEpisode(episode, null)
                    }
                )
            }
        }

        if (showNoDefaultExtDialog) {
            NoDefaultExtensionDialog(
                onDismiss = { showNoDefaultExtDialog = false },
                onGoToSettings = onNoExtension,
            )
        }

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
                // Release-settle: a low-velocity release never produces a fling, so onPreFling is
                // never invoked and the sheet would stay stuck half-translated; and a fling
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
                        // Defer the settle check to the composition scope: the gesture scope is a
                        // restricted coroutine scope (can't delay() from inside it), and this
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
            if (!displayData.banner.isNullOrEmpty() || displayData.cover.isNotEmpty()) {
                val bannerImage = displayData.banner ?: displayData.cover

                Box(modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clickable { fullscreenImageUrl = bannerImage }
                ) {
                    AsyncImage(
                        model = bannerImage,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                this.alpha = 0.4f
                            }
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.2f),
                                        Color.Black.copy(alpha = 0.85f)
                                    )
                                )
                            )
                    )
                }
            }

            IconButton(
                onClick = {
                    val shareText = buildString {
                        append(displayData.title)
                        append("\n\n")
                        append(com.blissless.tensei.network.Endpoints.AniList.animePageUrl(displayData.id))
                    }
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, shareText)
                        type = "text/plain"
                    }
                    val shareIntent = Intent.createChooser(sendIntent, null)
                    context.startActivity(shareIntent)
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

            IconButton(
                onClick = { if (stackDepth > 2) onDismiss() else onNavigateBack() },
                modifier = Modifier
                    .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp, start = 16.dp)
                    .align(Alignment.TopStart)
                    .size(40.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    .zIndex(10f)
            ) {
                Icon(
                    if (stackDepth > 2) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = if (stackDepth > 2) "Close" else "Back",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp)
                    .width(40.dp).height(4.dp)
                    .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(2.dp)).zIndex(5f)
            )

            if (isLoadingDetails) {
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
            val cinematicProgress = rememberCinematicAnimation("detailed_anime")
            val density = LocalDensity.current

            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = 140.dp + statusBarsPadding.calculateTopPadding(),
                    bottom = 32.dp + navigationBarsPadding.calculateBottomPadding()
                )
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box {
                            val cardWidth = 140.dp
                            val cardHeight = 200.dp
                            val targetX = 0.dp
                            val targetY = 0.dp

                            Box(
                                modifier = Modifier
                                    .width(cardWidth)
                                    .height(cardHeight)
                                    .zIndex(if (initialCardBounds != null && coverAnimationProgress.value < 1f) 100f else 0f)
                                    .graphicsLayer {
                                        if (initialCardBounds != null) {
                                            val progress = FastOutSlowInEasing.transform(coverAnimationProgress.value)
                                            val startX = initialCardBounds.bounds.left
                                            val startY = initialCardBounds.bounds.top
                                            val startWidth = initialCardBounds.bounds.width()
                                            val startHeight = initialCardBounds.bounds.height()

                                            val currentWidth = startWidth + (cardWidth.toPx() - startWidth) * progress
                                            val currentHeight = startHeight + (cardHeight.toPx() - startHeight) * progress
                                            val currentX = startX + (targetX.toPx() - startX) * progress
                                            val currentY = startY + (targetY.toPx() - startY) * progress

                                            scaleX = currentWidth / size.width
                                            scaleY = currentHeight / size.height
                                            translationX = currentX
                                            translationY = currentY
                                        }
                                    }
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
                                clipboard.setPrimaryClip(ClipData.newPlainText("Anime Title", displayData.title))
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
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Anime Title", displayData.titleEnglish))
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
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Anime Title", displayData.titleNative))
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
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
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
                                Spacer(modifier = Modifier.weight(1f))
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (displayData.year != null) {
                                    Text(
                                        displayData.year.toString(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (displayData.year != null && displayData.format != null) {
                                    Text(
                                        "•",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                }
                                displayData.format?.let { _ ->
                                    Text(
                                        formatDisplay,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(20.dp))
                    WatchNowButton(
                        status = displayData.status,
                        simplifyEpisodeMenu = simplifyEpisodeMenu,
                        streamMethod = streamMethod,
                        hasDefaultMagnetExt = defaultMagnetExt != null,
                        hasDefaultExtPkg = defaultExtPkg.isNotEmpty(),
                        onNoDefaultExtension = { showNoDefaultExtDialog = true },
                        onShowEpisodeSelection = { showEpisodeSelection = true },
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            val totalEps = anime.episodes.takeIf { it > 0 } ?: anime.episodes

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
                                            Icon(
                                                Icons.Outlined.BookmarkAdd,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        if (isLoggedIn) "Add to List" else "Local List",
                                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (!isLoggedIn) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "(Offline)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                                if (statusToCheck != null || effectiveUserScore != null) {
                                    val statusColor = StatusColors[statusToCheck] ?: MaterialTheme.colorScheme.primary
                                    Column(horizontalAlignment = Alignment.End) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (effectiveUserScore != null) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        Icons.Default.Star,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(13.dp),
                                                        tint = Color(0xFFFBBF24)
                                                    )
                                                    Spacer(modifier = Modifier.width(2.dp))
                                                    Text(
                                                        text = "${userScoreToDisplay(effectiveUserScore)}",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFFFBBF24)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(6.dp))
                                            }
                                            if (statusToCheck != null) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = statusColor.copy(alpha = 0.12f)
                                                ) {
                                                    Text(
                                                        text = StatusLabels[statusToCheck] ?: statusToCheck,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = statusColor,
                                                        fontWeight = FontWeight.SemiBold,
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                                    )
                                                }
                                            }
                                        }
                                        if (totalEps > 0) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "$statusProgress",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = " / $totalEps",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Light,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                                )
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
                                        Icon(Icons.Outlined.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Change", fontWeight = FontWeight.SemiBold)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    OutlinedButton(
                                        onClick = { showRatingSheet = true },
                                        modifier = Modifier.height(44.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (effectiveUserScore != null && effectiveUserScore > 0) Color(0xFFFFD700).copy(alpha = 0.15f) else Color.Transparent,
                                            contentColor = if (effectiveUserScore != null && effectiveUserScore > 0) Color(0xFFFFD700) else MaterialTheme.colorScheme.onSurface
                                        ),
                                        border = BorderStroke(
                                            1.5.dp,
                                            if (effectiveUserScore != null && effectiveUserScore > 0) Color(0xFFFFD700) else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                        )
                                    ) {
                                        Icon(
                                            Icons.Default.Star,
                                            contentDescription = "Rate",
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    OutlinedButton(
                                        onClick = {
                                            if (isLoggedIn) {
                                                val animeMedia = AnimeMedia(
                                                    id = anime.id,
                                                    title = anime.title,
                                                    titleEnglish = anime.titleEnglish,
                                                    cover = anime.cover,
                                                    banner = anime.banner,
                                                    totalEpisodes = anime.episodes,
                                                    averageScore = anime.averageScore,
                                                    genres = anime.genres,
                                                    year = anime.year
                                                )
                                                viewModel.toggleAniListFavorite(anime.id, animeMedia)
                                            } else {
                                                viewModel.toggleOfflineFavorite(
                                                    anime.id,
                                                    anime.title,
                                                    anime.cover,
                                                    anime.banner,
                                                    anime.year,
                                                    anime.averageScore
                                                )
                                            }
                                        },
                                        modifier = Modifier.height(44.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (effectiveIsFavorite) Color(0xFFFF1744).copy(alpha = 0.15f) else Color.Transparent,
                                            contentColor = if (effectiveIsFavorite) Color(0xFFFF1744) else MaterialTheme.colorScheme.onSurface
                                        ),
                                        border = BorderStroke(
                                            1.5.dp,
                                            if (effectiveIsFavorite) Color(0xFFFF1744) else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                        )
                                    ) {
                                        Icon(
                                            if (effectiveIsFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                            null,
                                            Modifier.size(20.dp),
                                            tint = if (effectiveIsFavorite) Color(0xFFFF1744) else MaterialTheme.colorScheme.onSurface
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
                                        Icon(Icons.Outlined.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Add to List", fontWeight = FontWeight.SemiBold)
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    OutlinedButton(
                                        onClick = {
                                            if (isLoggedIn) {
                                                val animeMedia = AnimeMedia(
                                                    id = anime.id,
                                                    title = anime.title,
                                                    titleEnglish = anime.titleEnglish,
                                                    cover = anime.cover,
                                                    banner = anime.banner,
                                                    totalEpisodes = anime.episodes,
                                                    averageScore = anime.averageScore,
                                                    genres = anime.genres,
                                                    year = anime.year
                                                )
                                                viewModel.toggleAniListFavorite(anime.id, animeMedia)
                                            } else {
                                                viewModel.toggleOfflineFavorite(
                                                    anime.id,
                                                    anime.title,
                                                    anime.cover,
                                                    anime.banner,
                                                    anime.year,
                                                    anime.averageScore
                                                )
                                            }
                                        },
                                        modifier = Modifier.height(44.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (effectiveIsFavorite) Color(0xFFFF1744).copy(alpha = 0.15f) else Color.Transparent,
                                            contentColor = if (effectiveIsFavorite) Color(0xFFFF1744) else MaterialTheme.colorScheme.onSurface
                                        ),
                                        border = BorderStroke(
                                            1.5.dp,
                                            if (effectiveIsFavorite) Color(0xFFFF1744) else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                        )
                                    ) {
                                        Icon(
                                            if (effectiveIsFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                            null,
                                            Modifier.size(20.dp),
                                            tint = if (effectiveIsFavorite) Color(0xFFFF1744) else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(20.dp))
                    InfoCard(
                        displayData = displayData,
                        statusDisplay = statusDisplay,
                    )
                }


                if (displayData.trailerUrl != null) {
                    item {
                        Spacer(modifier = Modifier.height(20.dp))
                        TrailerCard(
                            trailerUrl = displayData.trailerUrl,
                            trailerThumbnail = displayData.trailerThumbnail,
                        )
                    }
                }

                if (displayData.genres.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(20.dp))
                        GenresCard(genres = displayData.genres)
                    }
                }

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

                // Show all relations including manga — manga relations are clickable and
                // will open the manga detail screen (wired via onRelationClick in MainActivity).
                val filteredRelations = displayData.relations

                if (filteredRelations.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(20.dp))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Link,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            "Relations",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            "Connected series",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            letterSpacing = 0.5.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.weight(1f))
                                    if (filteredRelations.isNotEmpty()) {
                                        TextButton(onClick = { 
                                            onViewAllRelations(displayData.id, displayData.title, displayData.titleEnglish) 
                                        }) {
                                            Text("View All", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))

                                val relationListState = rememberLazyListState()
                                val isRelationScrolling by remember {
                                    derivedStateOf { relationListState.isScrollInProgress }
                                }
                                val cameraDistancePx = with(density) { 12.dp.toPx() }

                                LazyRow(
                                    state = relationListState,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    itemsIndexed(
                                        items = filteredRelations,
                                        key = { _, relation -> relation.id }
                                    ) { index, relation ->
                                        val layoutInfo by remember { derivedStateOf { relationListState.layoutInfo } }
                                        val visibleItems = layoutInfo.visibleItemsInfo
                                        val itemInfo = visibleItems.find { it.index == index }

                                        val centerOffset = if (itemInfo != null) {
                                            val itemCenter = itemInfo.offset + itemInfo.size / 2
                                            val screenCenter = (layoutInfo.viewportSize.width / 2).toFloat()
                                            (itemCenter - screenCenter) / screenCenter
                                        } else {
                                            0f
                                        }

                                        val animatedOffset by animateFloatAsState(
                                            targetValue = if (isRelationScrolling) centerOffset.coerceIn(-1.5f, 1.5f) else 0f,
                                            animationSpec = if (isRelationScrolling) {
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
                                            label = "relationCenterOffset"
                                        )

                                        val scrollScale = 1f - (animatedOffset.absoluteValue * 0.25f).coerceAtMost(0.25f)
                                        val scrollAlpha = 1f - (animatedOffset.absoluteValue * 0.4f).coerceAtMost(0.6f)
                                        val scrollTranslationX = animatedOffset * -20f
                                        val scrollRotationY = (animatedOffset * 15f).coerceIn(-15f, 15f)

                                        val indexFloat = index.toFloat()
                                        val staggeredProgress = ((cinematicProgress * 1000f - (indexFloat * 40f)) / 1000f).coerceIn(0f, 1f)
                                        val easedProgress = easeOut(staggeredProgress)

                                        val introScale = if (cinematicProgress >= 1f) 1f else 0.85f + easedProgress * 0.15f
                                        val introAlpha = if (cinematicProgress >= 1f) 1f else easedProgress

                                        Column(
                                            modifier = Modifier
                                                .width(110.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .graphicsLayer {
                                                    scaleX = introScale * scrollScale
                                                    scaleY = introScale * scrollScale
                                                    this.alpha = introAlpha * scrollAlpha
                                                    translationX = scrollTranslationX
                                                    rotationY = scrollRotationY
                                                    cameraDistance = cameraDistancePx
                                                }
                                                .clickable {
                                                    onRelationClick(relation)
                                                }
                                                .padding(4.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .aspectRatio(3f / 4f)
                                            ) {
                                                Card(
                                                    shape = RoundedCornerShape(12.dp),
                                                    modifier = Modifier.fillMaxSize(),
                                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A))
                                                ) {
                                                    AsyncImage(
                                                        model = relation.cover,
                                                        contentDescription = relation.title,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                }
                                                Surface(
                                                    modifier = Modifier
                                                        .padding(6.dp)
                                                        .align(Alignment.TopStart),
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = Color.Black.copy(alpha = 0.8f)
                                                ) {
                                                    Text(
                                                        relation.relationType.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() },
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Color.White,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                                // Episode badge
                                                val episodeText = when {
                                                    relation.episodes != null && relation.episodes > 0 -> "${relation.episodes} ${if (relation.episodes == 1) "ep" else "eps"}"
                                                    relation.latestEpisode != null && relation.latestEpisode > 0 -> "Ep ${relation.latestEpisode}"
                                                    else -> null
                                                }
                                                episodeText?.let { text ->
                                                    Surface(
                                                        modifier = Modifier
                                                            .padding(6.dp)
                                                            .align(Alignment.BottomStart),
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = Color.Black.copy(alpha = 0.8f)
                                                    ) {
                                                        Text(
                                                            text,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = Color.White,
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                relation.title,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                color = MaterialTheme.colorScheme.onBackground,
                                                modifier = Modifier.height(32.dp)
                                            )
                                            relation.format?.let { format ->
                                                val formatDisplay = when (format) {
                                                    "TV" -> "TV"
                                                    "TV_SHORT" -> "TV Short"
                                                    "MOVIE" -> "Movie"
                                                    "SPECIAL" -> "Special"
                                                    "OVA" -> "OVA"
                                                    "ONA" -> "ONA"
                                                    "MANGA" -> "Manga"
                                                    "NOVEL" -> "Novel"
                                                    "ONE_SHOT" -> "One Shot"
                                                    "MUSIC" -> "Music"
                                                    else -> format
                                                }
                                                Text(
                                                    formatDisplay,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                                )
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
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text("Recommendations", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                        Text("You might also enjoy", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), letterSpacing = 0.5.sp)
                                    }
                                    Spacer(modifier = Modifier.weight(1f))
                                    TextButton(onClick = { onViewAllRecommendations(displayData.id, displayData.title, displayData.titleEnglish) }) {
                                        Text("View All", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))

                                val recListState = rememberLazyListState()
                                val isRecScrolling by remember {
                                    derivedStateOf { recListState.isScrollInProgress }
                                }
                                val recCameraDistancePx = with(density) { 12.dp.toPx() }

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
                                        val recStaggeredProgress = ((cinematicProgress * 1000f - (recIndexFloat * 40f)) / 1000f).coerceIn(0f, 1f)
                                        val recEasedProgress = easeOut(recStaggeredProgress)

                                        val recIntroScale = if (cinematicProgress >= 1f) 1f else 0.85f + recEasedProgress * 0.15f
                                        val recIntroAlpha = if (cinematicProgress >= 1f) 1f else recEasedProgress

                                        Column(
                                            modifier = Modifier
                                                .width(110.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .graphicsLayer {
                                                    scaleX = recIntroScale * recScrollScale
                                                    scaleY = recIntroScale * recScrollScale
                                                    this.alpha = recIntroAlpha * recScrollAlpha
                                                    translationX = recScrollTranslationX
                                                    rotationY = recScrollRotationY
                                                    cameraDistance = recCameraDistancePx
                                                }
                                                .clickable {
                                                    onRecommendationClick(rec)
                                                }
                                                .padding(4.dp)
                                        ) {
                                            Box(modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f)) {
                                                Card(
                                                    shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxSize(),
                                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A))
                                                ) {
                                                    AsyncImage(model = rec.cover, contentDescription = title,
                                                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                                }
                                                rec.averageScore?.let { score ->
                                                    Surface(
                                                        modifier = Modifier
                                                            .padding(6.dp)
                                                            .align(Alignment.TopEnd),
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = Color.Black.copy(alpha = 0.8f)
                                                    ) {
                                                        Text(
                                                            "${(score / 10.0).toString().take(3)}",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = Color(0xFFFFD700),
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium,
                                                maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onBackground,
                                                modifier = Modifier.height(32.dp))
                                            Box(modifier = Modifier.fillMaxWidth().height(16.dp), contentAlignment = Alignment.CenterStart) {
                                                rec.format?.let { format ->
                                                    val formatDisplay = when (format) {
                                                        "TV" -> "TV"
                                                        "TV_SHORT" -> "TV Short"
                                                        "MOVIE" -> "Movie"
                                                        "SPECIAL" -> "Special"
                                                        "OVA" -> "OVA"
                                                        "ONA" -> "ONA"
                                                        "MANGA" -> "Manga"
                                                        "NOVEL" -> "Novel"
                                                        "ONE_SHOT" -> "One Shot"
                                                        "MUSIC" -> "Music"
                                                        else -> format
                                                    }
                                                    Text(
                                                        formatDisplay,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Cast Section
                val castList = displayData.characters?.nodes
                if (!castList.isNullOrEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(20.dp))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Group,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            "Cast",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            "Characters & voice actors",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            letterSpacing = 0.5.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.weight(1f))
                                    if (castList.isNotEmpty()) {
                                        TextButton(onClick = onViewAllCast) {
                                            Text("View All", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))

                                val castListState = rememberLazyListState()
                                val isCastScrolling by remember {
                                    derivedStateOf { castListState.isScrollInProgress }
                                }
                                val cameraDistancePx = with(density) { 12.dp.toPx() }

                                LazyRow(
                                    state = castListState,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    itemsIndexed(
                                        items = castList,
                                        key = { _, character -> character.id }
                                    ) { index, character ->
                                        val layoutInfo by remember { derivedStateOf { castListState.layoutInfo } }
                                        val visibleItems = layoutInfo.visibleItemsInfo
                                        val itemInfo = visibleItems.find { it.index == index }

                                        val centerOffset = if (itemInfo != null) {
                                            val itemCenter = itemInfo.offset + itemInfo.size / 2
                                            val screenCenter = (layoutInfo.viewportSize.width / 2).toFloat()
                                            (itemCenter - screenCenter) / screenCenter
                                        } else {
                                            0f
                                        }

                                        val animatedOffset by animateFloatAsState(
                                            targetValue = if (isCastScrolling) centerOffset.coerceIn(-1.5f, 1.5f) else 0f,
                                            animationSpec = if (isCastScrolling) {
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
                                            label = "castCenterOffset"
                                        )

                                        val scrollScale = 1f - (animatedOffset.absoluteValue * 0.25f).coerceAtMost(0.25f)
                                        val scrollAlpha = 1f - (animatedOffset.absoluteValue * 0.4f).coerceAtMost(0.6f)
                                        val scrollTranslationX = animatedOffset * -20f
                                        val scrollRotationY = (animatedOffset * 15f).coerceIn(-15f, 15f)

                                        val indexFloat = index.toFloat()
                                        val staggeredProgress = ((cinematicProgress * 1000f - (indexFloat * 40f)) / 1000f).coerceIn(0f, 1f)
                                        val easedProgress = easeOut(staggeredProgress)

                                        val introScale = if (cinematicProgress >= 1f) 1f else 0.85f + easedProgress * 0.15f
                                        val introAlpha = if (cinematicProgress >= 1f) 1f else easedProgress

                                        Column(
                                            modifier = Modifier
                                                .width(80.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .graphicsLayer {
                                                    scaleX = introScale * scrollScale
                                                    scaleY = introScale * scrollScale
                                                    this.alpha = introAlpha * scrollAlpha
                                                    translationX = scrollTranslationX
                                                    rotationY = scrollRotationY
                                                    cameraDistance = cameraDistancePx
                                                }
                                                .clickable {
                                                    val id = character.id
                                                    onCharacterClick(id)
                                                }
                                                .padding(4.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .aspectRatio(1f)
                                            ) {
                                                Card(
                                                    shape = RoundedCornerShape(12.dp),
                                                    modifier = Modifier.fillMaxSize(),
                                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A))
                                                ) {
                                                    AsyncImage(
                                                        model = character.image?.large,
                                                        contentDescription = character.name?.full,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                character.name?.full ?: "Unknown",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                color = MaterialTheme.colorScheme.onBackground,
                                                modifier = Modifier.height(28.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Staff Section
                val staffList = displayData.staff?.edges
                if (!staffList.isNullOrEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(20.dp))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Person,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            "Staff",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            "Production crew",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            letterSpacing = 0.5.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.weight(1f))
                                    if (staffList.isNotEmpty()) {
                                        TextButton(onClick = onViewAllStaff) {
                                            Text("View All", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))

                                val staffListState = rememberLazyListState()
                                val isStaffScrolling by remember {
                                    derivedStateOf { staffListState.isScrollInProgress }
                                }
                                val cameraDistancePx = with(density) { 12.dp.toPx() }

                                LazyRow(
                                    state = staffListState,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    itemsIndexed(
                                        items = staffList,
                                        key = { index, _ -> "staff_$index" }
                                    ) { index, staffEdge ->
                                        staffEdge.node?.let { staff ->
                                            val layoutInfo = staffListState.layoutInfo
                                            val visibleItems = layoutInfo.visibleItemsInfo
                                            val itemInfo = visibleItems.find { it.index == index }

                                            val centerOffset = if (itemInfo != null) {
                                                val itemCenter = itemInfo.offset + itemInfo.size / 2
                                                val screenCenter = (layoutInfo.viewportSize.width / 2).toFloat()
                                                (itemCenter - screenCenter) / screenCenter
                                            } else {
                                                0f
                                            }

                                            val animatedOffset by animateFloatAsState(
                                                targetValue = if (isStaffScrolling) centerOffset.coerceIn(-1.5f, 1.5f) else 0f,
                                                animationSpec = if (isStaffScrolling) {
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
                                                label = "staffCenterOffset"
                                            )

                                            val scrollScale = 1f - (animatedOffset.absoluteValue * 0.25f).coerceAtMost(0.25f)
                                            val scrollAlpha = 1f - (animatedOffset.absoluteValue * 0.4f).coerceAtMost(0.6f)
                                            val scrollTranslationX = animatedOffset * -20f
                                            val scrollRotationY = (animatedOffset * 15f).coerceIn(-15f, 15f)

                                            val indexFloat = index.toFloat()
                                            val staggeredProgress = ((cinematicProgress * 1000f - (indexFloat * 40f)) / 1000f).coerceIn(0f, 1f)
                                            val easedProgress = easeOut(staggeredProgress)

                                            val introScale = if (cinematicProgress >= 1f) 1f else 0.85f + easedProgress * 0.15f
                                            val introAlpha = if (cinematicProgress >= 1f) 1f else easedProgress

                                            Column(
                                                modifier = Modifier
                                                    .width(80.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .clickable { staffEdge.node.id.let { id -> onStaffClick(id) } }
                                                    .graphicsLayer {
                                                        scaleX = introScale * scrollScale
                                                        scaleY = introScale * scrollScale
                                                        this.alpha = introAlpha * scrollAlpha
                                                        translationX = scrollTranslationX
                                                        rotationY = scrollRotationY
                                                        cameraDistance = cameraDistancePx
                                                    }
                                                    .padding(4.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .aspectRatio(1f)
                                                ) {
                                                    Card(
                                                        shape = RoundedCornerShape(12.dp),
                                                        modifier = Modifier.fillMaxSize(),
                                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A))
                                                    ) {
                                                        AsyncImage(
                                                            model = staff.image?.large,
                                                            contentDescription = staff.name?.full,
                                                            contentScale = ContentScale.Crop,
                                                            modifier = Modifier.fillMaxSize()
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(
                                                    staff.name?.full ?: "Unknown",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Medium,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                    color = MaterialTheme.colorScheme.onBackground,
                                                    modifier = Modifier.height(28.dp)
                                                )
                                                staffEdge.role?.let { role ->
                                                    Text(
                                                        role.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() },
                                                        style = MaterialTheme.typography.labelSmall,
                                                        maxLines = 1,
                                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }

    if (selectedTagForDescription != null) {
        val tag: TagData = selectedTagForDescription!!
        ModalBottomSheet(
            onDismissRequest = { selectedTagForDescription = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 40.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.AutoMirrored.Filled.Label,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        tag.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                tag.rank?.let { rank ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Rank: $rank%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                Spacer(modifier = Modifier.height(16.dp))
                val description = tag.description ?: "No description available."
                val cleanDescription = description.replace("<br>", "\n").replace("<br/>", "\n")
                    .replace("<b>", "").replace("</b>", "").replace("<i>", "").replace("</i>", "")
                    .replace("&quot;", "\"").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                Text(
                    cleanDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 24.sp
                )
            }
        }
    }

    if (fullscreenImageUrl != null) {
        Dialog(onDismissRequest = { fullscreenImageUrl = null }) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                        detectTapGestures { fullscreenImageUrl = null }
                    }
                )
                AsyncImage(
                    model = fullscreenImageUrl,
                    contentDescription = "Fullscreen image",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(16.dp)
                )
            }
        }
    }

    if (showStatusDialog) {
        val animeMedia = AnimeMedia(
            id = anime.id,
            title = anime.title,
            titleEnglish = anime.titleEnglish,
            cover = anime.cover,
            banner = anime.banner,
            progress = statusProgress,
            totalEpisodes = totalEps,
            latestEpisode = displayData.latestEpisode,
            listStatus = statusToCheck ?: "",
            averageScore = anime.averageScore,
            userScore = effectiveUserScore,
            year = anime.year
        )
        HomeAnimeStatusDialog(
            anime = animeMedia,
            isOled = isOled,
            preferEnglishTitles = preferEnglishTitles,
            onDismiss = { showStatusDialog = false },
            onRemove = {
                effectiveOnRemove()
                showStatusDialog = false
            },
            onUpdate = { status: String, progress: Int? ->
                if (isLoggedIn) {
                    viewModel.updateAnimeStatus(anime.id, status, progress, null)
                } else {
                    effectiveOnUpdateStatus(status)
                    if (progress != null) effectiveOnUpdateProgress(progress)
                    viewModel.setLocalAnimeStatus(
                        anime.id,
                        LocalAnimeEntry(
                            id = anime.id,
                            status = status,
                            progress = progress ?: localAnimeStatus[anime.id]?.progress ?: 0,
                            totalEpisodes = anime.episodes,
                            title = anime.title,
                            cover = anime.cover,
                            banner = anime.banner,
                            year = anime.year,
                            averageScore = anime.averageScore,
                            score = localAnimeStatus[anime.id]?.score
                        )
                    )
                }
                if (progress != null) displayProgress = progress
                showStatusDialog = false
            }
        )
    }

    if (showRatingSheet) {
        val animeMedia = AnimeMedia(
            id = anime.id,
            title = anime.title,
            titleEnglish = anime.titleEnglish,
            cover = anime.cover,
            banner = anime.banner,
            totalEpisodes = anime.episodes,
            averageScore = anime.averageScore,
            userScore = effectiveUserScore,
            year = anime.year
        )
        AnimeRatingSheet(
            anime = animeMedia,
            isOled = isOled,
            onDismiss = { showRatingSheet = false },
            onScoreSaved = { score ->
                if (isLoggedIn) {
                    viewModel.updateAnimeStatus(anime.id, statusToCheck ?: "CURRENT", null, score)
                } else {
                    viewModel.setLocalAnimeStatus(
                        anime.id,
                        LocalAnimeEntry(
                            id = anime.id,
                            status = statusToCheck ?: "CURRENT",
                            progress = statusProgress,
                            totalEpisodes = anime.episodes,
                            title = anime.title,
                            cover = anime.cover,
                            banner = anime.banner,
                            year = anime.year,
                            averageScore = anime.averageScore,
                            score = score
                        )
                    )
                }
                showRatingSheet = false
            }
        )
    }
}

