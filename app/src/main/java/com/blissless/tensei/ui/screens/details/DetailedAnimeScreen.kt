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
import androidx.compose.material.icons.filled.Category
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
import androidx.compose.material.icons.filled.Timer
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.blissless.tensei.data.models.LocalAnimeEntry
import com.blissless.tensei.data.models.TagData
import com.blissless.tensei.dialogs.HomeAnimeStatusDialog
import com.blissless.tensei.ui.components.rememberCinematicAnimation
import com.blissless.tensei.ui.screens.downloads.EpisodeDownloadDialog
import com.blissless.tensei.ui.screens.episode.EpisodeSelectionDialog
import com.blissless.tensei.ui.screens.episode.RichEpisodeScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

private fun formatDate(dateStr: String): String {
    return try {
        val parts = dateStr.split("-")
        if (parts.size == 3) {
            val date = LocalDate.of(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
            date.format(DateTimeFormatter.ofPattern("d MMMM, yyyy"))
        } else dateStr
    } catch (_: Exception) {
        dateStr
    }
}

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
    onPlayEpisode: (Int, String?) -> Unit = { _, _ -> },
    onUpdateStatus: (String?) -> Unit = {},
    onUpdateProgress: (Int) -> Unit = {},
    onRemove: () -> Unit = {},
    onUpdateLocalStatus: (String?) -> Unit = {},
    onRemoveLocalStatus: () -> Unit = {},
    onRelationClick: (AnimeRelation) -> Unit = {},
    onCharacterClick: (Int) -> Unit = {},
    onStaffClick: (Int) -> Unit = {},
    onViewAllCast: () -> Unit = {},
    onViewAllStaff: () -> Unit = {},
    onViewAllRelations: (Int, String) -> Unit = { _, _ -> },
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
    var showDownloadDialog by remember { mutableStateOf(false) }

    var isVisible by remember { mutableStateOf(false) }
    var previousAnimeId by remember { mutableIntStateOf(anime.id) }
    var isTransitioning by remember { mutableStateOf(false) }

    var selectedTagForDescription by remember { mutableStateOf<TagData?>(null) }
    var fullscreenImageUrl by remember { mutableStateOf<String?>(null) }
    var showEpisodeSelection by remember { mutableStateOf(false) }
    var showNoDefaultExtDialog by remember { mutableStateOf(false) }
    var showStatusDialog by remember { mutableStateOf(false) }

    val defaultExtPkg by viewModel.defaultExtensionPackage.collectAsState()
    val localFavorites by viewModel.localFavorites.collectAsState()
    val aniListFavorites by viewModel.aniListFavorites.collectAsState()
    val localAnimeStatus by viewModel.localAnimeStatus.collectAsState()
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

    val slideOffset = remember { Animatable(1000f) }
    val dismissSlideOffset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    
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
    val dismissThreshold = screenHeightPx / 2f

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

                val shouldDismiss = currentOffset > dismissThreshold || available.y > 500f

                if (shouldDismiss && isAtTop) {
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
                    onDismiss = { showEpisodeSelection = false },
                    onEpisodeSelect = { episode, _ ->
                        showEpisodeSelection = false
                        onPlayEpisode(episode, null)
                    },
                    onDownloadClick = {
                        showDownloadDialog = true
                    }
                )
            }
        }

        if (showDownloadDialog) {
            Dialog(
                onDismissRequest = { showDownloadDialog = false },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false
                )
            ) {
                EpisodeDownloadDialog(
                    anime = animeMedia,
                    viewModel = viewModel,
                    downloadManager = viewModel.episodeDownloadManager,
                    isOled = isOled,
                    preferEnglishTitles = preferEnglishTitles,
                    onDismiss = { showDownloadDialog = false },
                    onNavigateToSettings = onNavigateToSettings
                )
            }
        }

        if (showNoDefaultExtDialog) {
            AlertDialog(
                onDismissRequest = { showNoDefaultExtDialog = false },
                title = { Text("No Default Extension") },
                text = { Text("Set a default extension in Settings to enable streaming.") },
                confirmButton = {
                    TextButton(onClick = {
                        showNoDefaultExtDialog = false
                        onNoExtension()
                    }) {
                        Text("Go to Settings")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showNoDefaultExtDialog = false }) {
                        Text("Cancel")
                    }
                }
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
                .background(MaterialTheme.colorScheme.background)
                .nestedScroll(nestedScrollConnection)
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
                        append("https://anilist.co/anime/${displayData.id}")
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
                onClick = onDismiss,
                modifier = Modifier
                    .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp, start = 16.dp)
                    .align(Alignment.TopStart)
                    .size(40.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    .zIndex(10f)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(24.dp))
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
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                    ) {
                        Box(modifier = Modifier.padding(12.dp)) {
                            val notYetAired = displayData.status == "NOT_YET_RELEASED"

                            Button(
                                onClick = {
                                    if (defaultExtPkg.isEmpty()) {
                                        showNoDefaultExtDialog = true
                                    } else {
                                        showEpisodeSelection = true
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                enabled = !notYetAired,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                )
                            ) {
                                Icon(Icons.Default.PlayArrow, null, Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Watch Now", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
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
                                                Icons.AutoMirrored.Filled.PlaylistAdd,
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
                                if (statusToCheck != null) {
                                    val statusColor = when (statusToCheck) {
                                        "CURRENT" -> Color(0xFF2196F3)
                                        "PLANNING" -> Color(0xFF9C27B0)
                                        "COMPLETED" -> Color(0xFF4CAF50)
                                        "PAUSED" -> Color(0xFFFFC107)
                                        "DROPPED" -> Color(0xFFF44336)
                                        else -> MaterialTheme.colorScheme.primary
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = statusColor.copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                text = when (statusToCheck) {
                                                    "CURRENT" -> "Watching"
                                                    "PLANNING" -> "Planning"
                                                    "COMPLETED" -> "Completed"
                                                    "PAUSED" -> "On Hold"
                                                    "DROPPED" -> "Dropped"
                                                    else -> statusToCheck
                                                },
                                                style = MaterialTheme.typography.labelMedium,
                                                color = statusColor,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                            )
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
                                        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Change", fontWeight = FontWeight.SemiBold)
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
                                        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, modifier = Modifier.size(18.dp))
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
                    Spacer(modifier = Modifier.height(24.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        Icon(
                                            Icons.Default.Info,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Information", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground)
                            }
                            Spacer(modifier = Modifier.height(16.dp))

                            // Main stats row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                val latestEp = displayData.latestEpisode?.takeIf { it > 0 }
                                val totalEp = displayData.episodes.takeIf { it > 0 }
                                val epDisplay = when {
                                    latestEp != null && totalEp != null -> "$latestEp / $totalEp"
                                    latestEp != null -> "$latestEp"
                                    totalEp != null -> "$totalEp"
                                    else -> null
                                }
                                epDisplay?.let {
                                    InfoStat("Episodes", it, Icons.Default.PlayCircle, MaterialTheme.colorScheme.primary)
                                }
                                displayData.duration?.let {
                                    InfoStat("Duration", "$it min", Icons.Default.Timer, MaterialTheme.colorScheme.primary)
                                }
                                displayData.averageScore?.let { score ->
                                    InfoStat("Score", String.format(Locale.US, "%.1f", score / 10.0), Icons.Default.Star, Color(0xFFFFD700))
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                            Spacer(modifier = Modifier.height(12.dp))

                            // Info grid - 2 columns
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    displayData.format?.let { format ->
                                        InfoItemRow("Format", format.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }, modifier = Modifier.weight(1f)) 
                                    }
                                    displayData.status?.let { 
                                        InfoItemRow("Status", statusDisplay, modifier = Modifier.weight(1f)) 
                                    }
                                }
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    if (displayData.season != null && displayData.year != null) {
                                        InfoItemRow("Season", "${displayData.season.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }} ${displayData.year}", modifier = Modifier.weight(1f))
                                    }
                                    displayData.source?.let { 
                                        InfoItemRow("Source", it.replace("_", " ").lowercase().replaceFirstChar { c -> c.uppercase() }, modifier = Modifier.weight(1f)) 
                                    }
                                }
                                if (displayData.studios.isNotEmpty()) {
                                    val studio = displayData.studios.filter { it.isAnimationStudio }.joinToString(", ") { it.name }
                                    if (studio.isNotEmpty()) {
                                        InfoItemRow("Studio", studio)
                                    }
                                }
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    displayData.startDate?.let { 
                                        InfoItemRow("Started", formatDate(it), modifier = Modifier.weight(1f)) 
                                    }
                                    if (displayData.status != "RELEASING" && displayData.status != "NOT_YET_RELEASED") {
                                        displayData.endDate?.let { 
                                            InfoItemRow("Ended", formatDate(it), modifier = Modifier.weight(1f)) 
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (displayData.trailerUrl != null) {
                    item {
                        Spacer(modifier = Modifier.height(20.dp))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    val intent = Intent(Intent.ACTION_VIEW, displayData.trailerUrl.toUri())
                                    context.startActivity(intent)
                                },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                            Icon(
                                                Icons.Default.PlayCircle,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("Trailer", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onBackground)
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(16f / 9f)
                                        .clip(RoundedCornerShape(12.dp))
                                ) {
                                    AsyncImage(
                                        model = displayData.trailerThumbnail ?: "",
                                        contentDescription = "Trailer Thumbnail",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        FilledIconButton(
                                            onClick = {
                                                val intent = Intent(Intent.ACTION_VIEW, displayData.trailerUrl.toUri())
                                                context.startActivity(intent)
                                            },
                                            modifier = Modifier.size(60.dp),
                                            colors = IconButtonDefaults.filledIconButtonColors(
                                                containerColor = Color(0xFF1A1A1A).copy(alpha = 0.9f),
                                                contentColor = Color.White
                                            )
                                        ) {
                                            Icon(
                                                Icons.Default.PlayArrow,
                                                contentDescription = "Play Trailer",
                                                modifier = Modifier.size(34.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Watch on YouTube", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                if (displayData.genres.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(20.dp))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                            Icon(
                                                Icons.Default.Category,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("Genres", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onBackground)
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    displayData.genres.forEach { genre ->
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                                        ) {
                                            Text(
                                                genre,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (displayData.tags.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(20.dp))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.Label,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("Tags", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onBackground)
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                val nonSpoilerTags = displayData.tags.filter { !it.isMediaSpoiler }
                                val displayedTags = if (showAllTags) nonSpoilerTags else nonSpoilerTags.take(2)
                                val remainingCount = nonSpoilerTags.size - 2

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    displayedTags.forEach { tag ->
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)),
                                            modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable {
                                                selectedTagForDescription = tag
                                            }
                                        ) {
                                            Text(
                                                tag.name,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                            )
                                        }
                                    }
                                    if (remainingCount > 0 && !showAllTags) {
                                        Text(
                                            "+$remainingCount more",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f),
                                            modifier = Modifier.align(Alignment.CenterVertically)
                                        )
                                    }
                                }
                                if (remainingCount > 0) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    TextButton(
                                        onClick = { showAllTags = !showAllTags },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            if (showAllTags) "Show Less" else "Show More",
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (!displayData.description.isNullOrEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(20.dp))
                        // Redesigned Synopsis Section
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                            Icon(
                                                Icons.Default.Description,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("Synopsis", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onBackground)
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                val cleanDescription = displayData.description.replace("<br>", "\n").replace("<br/>", "\n")
                                    .replace("<b>", "").replace("</b>", "").replace("<i>", "").replace("</i>", "")
                                Text(cleanDescription, style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = if (showFullDescription) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis,
                                    lineHeight = 22.sp)
                                if (cleanDescription.length > 250) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    TextButton(
                                        onClick = { showFullDescription = !showFullDescription },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            if (showFullDescription) "Show Less" else "Read More",
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                val filteredRelations = displayData.relations.filter { relation ->
                    relation.format != "MANGA" && relation.format != "NOVEL" && relation.format != "ONE_SHOT"
                }

                if (filteredRelations.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(20.dp))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                            Icon(
                                                Icons.Default.Link,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("Relations", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onBackground)
                                    Spacer(modifier = Modifier.weight(1f))
                                    if (filteredRelations.isNotEmpty()) {
                                        TextButton(onClick = { 
                                            onViewAllRelations(displayData.id, displayData.title) 
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
                                                .clickable {
                                                    onRelationClick(relation)
                                                }
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

                // Cast Section
                val castList = displayData.characters?.nodes
                if (!castList.isNullOrEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(20.dp))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                            Icon(
                                                Icons.Default.Group,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("Cast", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onBackground)
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
                                                .clickable {
                                                    val id = character.id
                                                    onCharacterClick(id)
                                                }
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
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                            Icon(
                                                Icons.Default.Person,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("Staff", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onBackground)
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
            year = anime.year
        )
        HomeAnimeStatusDialog(
            anime = animeMedia,
            isOled = isOled,
            onDismiss = { showStatusDialog = false },
            onRemove = {
                effectiveOnRemove()
                showStatusDialog = false
            },
            onUpdate = { status: String, progress: Int? ->
                effectiveOnUpdateStatus(status)
                if (progress != null) {
                    effectiveOnUpdateProgress(progress)
                    displayProgress = progress
                }
                if (!isLoggedIn && progress != null) {
                    viewModel.setLocalAnimeStatus(
                        anime.id,
                        LocalAnimeEntry(
                            id = anime.id,
                            status = status,
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
                showStatusDialog = false
            }
        )
    }
}

@Composable
private fun InfoStat(label: String, value: String, icon: ImageVector, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = CircleShape,
            color = color.copy(alpha = 0.15f),
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InfoItemRow(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(end = 8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun easeOut(t: Float): Float {
    val t1 = t - 1f
    return t1 * t1 * t1 + 1f
}


