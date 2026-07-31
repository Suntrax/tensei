package com.blissless.tensei.ui.screens.manga

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.blissless.tensei.MainViewModel
import com.blissless.tensei.data.models.MangaChapter
import com.blissless.tensei.data.models.MangaCharacterNode
import com.blissless.tensei.data.models.MangaDetail
import com.blissless.tensei.data.models.MangaMedia
import com.blissless.tensei.data.models.MangaRelation
import com.blissless.tensei.data.models.MangaStaffEdge
import com.blissless.tensei.data.models.MangaExploreMedia
import com.blissless.tensei.data.models.TagData
import com.blissless.tensei.viewmodel.clearMangaDetail
import com.blissless.tensei.viewmodel.fetchMangaDetail
import com.blissless.tensei.viewmodel.loadMangaChapters
import com.blissless.tensei.viewmodel.mangaChapters
import com.blissless.tensei.viewmodel.mangaDetail
import com.blissless.tensei.viewmodel.isLoadingManga
import com.blissless.tensei.viewmodel.isLoadingMangaChapters
import com.blissless.tensei.viewmodel.updateMangaStatus
import com.blissless.tensei.viewmodel.markMangaChapterRead
import com.blissless.tensei.viewmodel.isChapterDownloaded
import com.blissless.tensei.viewmodel.deleteMangaChapter
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@androidx.compose.runtime.Composable
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
    onUpdateStatus: (String?) -> Unit = {},
    onUpdateProgress: (Int) -> Unit = {},
    onRemove: () -> Unit = {},
    onRelationClick: (MangaRelation) -> Unit = {},
    onCharacterClick: (Int) -> Unit = {},
    onStaffClick: (Int) -> Unit = {},
    onStartReader: (chapterIndex: Int) -> Unit = {},
    navigateToMangaDetail: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    var showFullDescription by remember { mutableStateOf(false) }
    var showAllTags by remember { mutableStateOf(false) }

    val detail by viewModel.mangaDetail.collectAsState()
    val chapters by viewModel.mangaChapters.collectAsState()
    val isLoading by viewModel.isLoadingManga.collectAsState()
    val isLoadingChapters by viewModel.isLoadingMangaChapters.collectAsState()

    var showStatusDialog by remember { mutableStateOf(false) }
    var showChapterList by remember { mutableStateOf(false) }

    if (autoShowChapters) {
        LaunchedEffect(chapters, isLoadingChapters) {
            if (!isLoadingChapters && chapters.isNotEmpty() && !showChapterList) {
                showChapterList = true
            }
        }
    }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var selectedChapter by remember { mutableStateOf<MangaChapter?>(null) }

    var fullscreenImageUrl by remember { mutableStateOf<String?>(null) }

    var displayProgress by remember { mutableIntStateOf(currentProgress ?: manga.progress) }
    LaunchedEffect(currentProgress, manga.progress) {
        displayProgress = currentProgress ?: manga.progress
    }

    val slideOffset = remember { Animatable(1000f) }
    val dismissSlideOffset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(manga.id) {
        viewModel.fetchMangaDetail(manga.id)
        viewModel.loadMangaChapters(manga.id, manga.title)
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.clearMangaDetail() }
    }

    LaunchedEffect(Unit) {
        slideOffset.animateTo(targetValue = 0f, animationSpec = tween(200, easing = LinearEasing))
    }

    fun dismissWithAnimation() {
        scope.launch {
            dismissSlideOffset.snapTo(0f)
            dismissSlideOffset.animateTo(targetValue = 1000f, animationSpec = tween(150, easing = LinearEasing))
            onDismiss()
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (slideOffset.value > 0 || dismissSlideOffset.value > 0) 0f else 1f,
        animationSpec = tween(durationMillis = 200, easing = LinearEasing), label = "alpha"
    )

    val statusToCheck = currentStatus ?: manga.listStatus
    val statusProgress = displayProgress
    val totalCh = manga.totalChapters

    Dialog(
        onDismissRequest = { dismissWithAnimation() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, (slideOffset.value + dismissSlideOffset.value).roundToInt()) }
                .graphicsLayer { this.alpha = alpha },
            color = if (isOled) Color.Black else MaterialTheme.colorScheme.background
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val listState = rememberLazyListState()

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Header
                    item {
                        MangaDetailHeader(
                            detail = detail,
                            manga = manga,
                            isOled = isOled,
                            onCopyTitle = { text ->
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("manga_title", text))
                            },
                            onFullscreenImage = { url -> fullscreenImageUrl = url }
                        )
                    }

                    // Action Buttons
                    item {
                        MangaActionButtons(
                            detail = detail,
                            chapters = chapters,
                            isLoading = isLoading,
                            isLoadingChapters = isLoadingChapters,
                            currentStatus = statusToCheck,
                            currentProgress = statusProgress,
                            displayProgress = displayProgress,
                            totalChapters = totalCh,
                            isFavorite = isFavorite,
                            onStartReader = {
                                val startIndex = displayProgress.coerceAtLeast(1) - 1
                                onStartReader(startIndex.coerceAtMost(chapters.lastIndex).coerceAtLeast(0))
                            },
                            onShowStatusMenu = { showStatusDialog = true },
                            onShowChapterList = { showChapterList = true },
                            onFavoriteClick = { },
                            onShareClick = {
                                detail?.siteUrl?.let { url ->
                                    val share = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, url)
                                    }
                                    context.startActivity(Intent.createChooser(share, "Share"))
                                }
                            }
                        )
                    }

                    // Synopsis
                    detail?.description?.let { desc ->
                        item {
                            MangaSynopsisSection(desc, showFullDescription, { showFullDescription = !showFullDescription })
                        }
                    }

                    // Characters
                    val characters = detail?.characters?.nodes
                    if (characters != null && characters.isNotEmpty()) {
                        item {
                            MangaCharactersSection(
                                characters = characters,
                                onCharacterClick = onCharacterClick
                            )
                        }
                    }

                    // Staff
                    val staff = detail?.staff?.edges
                    if (staff != null && staff.isNotEmpty()) {
                        item {
                            MangaStaffSection(
                                staff = staff,
                                onStaffClick = onStaffClick
                            )
                        }
                    }

                    // Relations
                    val relations = detail?.relations
                    if (relations != null && relations.isNotEmpty()) {
                        item {
                            MangaRelationsSection(
                                relations = relations,
                                onRelationClick = onRelationClick
                            )
                        }
                    }

                    // Genres
                    val genres = detail?.genres ?: manga.genres
                    if (genres.isNotEmpty()) {
                        item {
                            MangaGenresSection(genres = genres, isOled = isOled)
                        }
                    }

                    // Tags
                    val tags = detail?.tags
                    if (tags != null && tags.isNotEmpty()) {
                        item {
                            MangaTagsSection(tags = tags, showAll = showAllTags, onToggle = { showAllTags = !showAllTags })
                        }
                    }

                    // Recommendations
                    val recommendations = detail?.recommendations
                    if (recommendations != null && recommendations.isNotEmpty()) {
                        item {
                            MangaRecommendationsSection(
                                recommendations = recommendations,
                                preferEnglishTitles = preferEnglishTitles,
                                onMangaClick = { rec -> navigateToMangaDetail(rec.id) }
                            )
                        }
                    }

                    // External Links
                    val extLinks = detail?.externalLinks
                    if (extLinks != null && extLinks.isNotEmpty()) {
                        item {
                            MangaExternalLinksSection(links = extLinks)
                        }
                    }

                    // Bottom spacer
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }

                // Dismiss button
                IconButton(
                    onClick = { dismissWithAnimation() },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 4.dp, start = 4.dp)
                        .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                        .size(40.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }
    }

    if (showStatusDialog) {
        MangaStatusAlertDialog(
            currentStatus = statusToCheck,
            onSelect = { status ->
                onUpdateStatus(status)
                viewModel.updateMangaStatus(manga.id, status)
                showStatusDialog = false
            },
            onDismiss = { showStatusDialog = false }
        )
    }

    if (showChapterList) {
        MangaFullChapterList(
            chapters = chapters,
            isLoading = isLoadingChapters,
            mangaId = manga.id,
            currentProgress = displayProgress,
            viewModel = viewModel,
            onSelectChapter = { chapter, index ->
                viewModel.markMangaChapterRead(manga.id, chapter)
                onStartReader(index)
                showChapterList = false
            },
            onDismiss = { showChapterList = false }
        )
    }

    if (fullscreenImageUrl != null) {
        Dialog(onDismissRequest = { fullscreenImageUrl = null }) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.fillMaxSize().clickable { fullscreenImageUrl = null })
                AsyncImage(
                    model = fullscreenImageUrl, contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(16.dp)
                )
            }
        }
    }
}

@Composable
private fun MangaDetailHeader(
    detail: MangaDetail?,
    manga: MangaMedia,
    isOled: Boolean,
    onCopyTitle: (String) -> Unit,
    onFullscreenImage: (String) -> Unit
) {
    val bannerUrl = detail?.banner ?: manga.banner
    Box(modifier = Modifier.fillMaxWidth()) {
        if (!bannerUrl.isNullOrEmpty()) {
            AsyncImage(
                model = bannerUrl, contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(240.dp),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth().height(240.dp)
                    .background(Brush.verticalGradient(
                        colors = listOf(Color.Transparent, if (isOled) Color.Black else MaterialTheme.colorScheme.background)
                    ))
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (bannerUrl.isNullOrEmpty()) 0.dp else 180.dp)
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                AsyncImage(
                    model = detail?.cover ?: manga.cover,
                    contentDescription = null,
                    modifier = Modifier
                        .width(120.dp).aspectRatio(0.7f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { detail?.cover?.let(onFullscreenImage) },
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = detail?.title ?: manga.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold, maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            color = if (bannerUrl.isNullOrEmpty()) MaterialTheme.colorScheme.onSurface else Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { onCopyTitle(detail?.title ?: manga.title) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                        }
                    }
                    detail?.titleEnglish?.let { eng ->
                        if (eng != (detail?.title ?: manga.title)) {
                            Text(eng, style = MaterialTheme.typography.bodyMedium,
                                color = if (bannerUrl.isNullOrEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else Color.White.copy(alpha = 0.8f),
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        detail?.averageScore?.let { score ->
                            Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFF4CAF50).copy(alpha = 0.8f)) {
                                Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Star, null, Modifier.size(14.dp), tint = Color.White)
                                    Spacer(Modifier.width(2.dp))
                                    Text("${score / 10}", style = MaterialTheme.typography.labelSmall, color = Color.White)
                                }
                            }
                        }
                        detail?.status?.let { st ->
                            Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFF666666).copy(alpha = 0.8f)) {
                                Text(st.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() },
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall, color = Color.White)
                            }
                        }
                        detail?.format?.let { fmt ->
                            Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFF666666).copy(alpha = 0.8f)) {
                                Text(fmt.replace("_", " "), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MangaActionButtons(
    detail: MangaDetail?,
    chapters: List<MangaChapter>,
    isLoading: Boolean,
    isLoadingChapters: Boolean,
    currentStatus: String?,
    currentProgress: Int,
    displayProgress: Int,
    totalChapters: Int,
    isFavorite: Boolean,
    onStartReader: () -> Unit,
    onShowStatusMenu: () -> Unit,
    onShowChapterList: () -> Unit,
    onFavoriteClick: () -> Unit,
    onShareClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onStartReader,
                    modifier = Modifier.weight(1f).height(48.dp),
                    enabled = chapters.isNotEmpty() && !isLoadingChapters
                ) {
                    Icon(Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (displayProgress > 0) "Ch. ${displayProgress}" else "Start Reading")
                }
                OutlinedButton(onClick = onShowChapterList, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.List, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${chapters.size} Ch.")
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onShowStatusMenu, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.BookmarkBorder, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(mangaStatusLabel(currentStatus))
                }
                OutlinedButton(onClick = onShareClick, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Share, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Share")
                }
            }
            Spacer(Modifier.height(12.dp))
            // Stats
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                MangaStatItem("Chapters", "${detail?.chapters ?: chapters.size}")
                detail?.volumes?.let { MangaStatItem("Volumes", "$it") }
                detail?.popularity?.let { MangaStatItem("Popularity", formatMangaNumber(it)) }
                detail?.favourites?.let { MangaStatItem("Favorites", formatMangaNumber(it)) }
            }
        }
    }
}

@Composable
private fun MangaSynopsisSection(description: String, showFull: Boolean, onToggle: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Description, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Synopsis", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            val text = if (showFull) description else description.take(400).replace(Regex("<[^>]*>"), "") + if (description.length > 400) "..." else ""
            Text(text.replace(Regex("<[^>]*>"), ""), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (description.length > 400) {
                TextButton(onClick = onToggle) { Text(if (showFull) "Show Less" else "Read More") }
            }
        }
    }
}

@Composable
private fun MangaCharactersSection(
    characters: List<MangaCharacterNode>,
    onCharacterClick: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Group, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Characters", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(characters.take(20)) { _, char ->
                    Column(modifier = Modifier.width(80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        AsyncImage(
                            model = char.image?.large ?: "",
                            contentDescription = char.name?.full,
                            modifier = Modifier
                                .size(72.dp).clip(CircleShape)
                                .clickable { char.id.let(onCharacterClick) },
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(char.name?.full ?: "", style = MaterialTheme.typography.labelSmall,
                            maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun MangaStaffSection(
    staff: List<MangaStaffEdge>,
    onStaffClick: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Staff", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(staff.take(20)) { _, edge ->
                    Column(modifier = Modifier.width(80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        AsyncImage(
                            model = edge.node?.image?.large ?: "",
                            contentDescription = edge.node?.name?.full,
                            modifier = Modifier
                                .size(72.dp).clip(CircleShape)
                                .clickable { edge.node?.id?.let(onStaffClick) },
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(edge.node?.name?.full ?: "", style = MaterialTheme.typography.labelSmall,
                            maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                        Text(edge.role ?: "", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun MangaRelationsSection(
    relations: List<MangaRelation>,
    onRelationClick: (MangaRelation) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Link, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Relations", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(relations) { _, relation ->
                    MangaRelationCard(relation = relation, onClick = { onRelationClick(relation) })
                }
            }
        }
    }
}

@Composable
private fun MangaRelationCard(relation: MangaRelation, onClick: () -> Unit) {
    Card(
        modifier = Modifier.width(130.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            AsyncImage(
                model = relation.cover, contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(170.dp),
                contentScale = ContentScale.Crop
            )
            Column(modifier = Modifier.padding(8.dp)) {
                Text(relation.title, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(relation.relationType.replace("_", " "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun MangaGenresSection(genres: List<String>, isOled: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = if (isOled) Color(0xFF111111) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Genres", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                genres.forEach { genre ->
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)) {
                        Text(genre, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun MangaTagsSection(tags: List<TagData>, showAll: Boolean, onToggle: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Tags", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val displayTags = if (showAll) tags else tags.take(15)
                displayTags.forEach { tag ->
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                    ) {
                        Text(tag.name, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (tags.size > 15) {
                TextButton(onClick = onToggle) { Text(if (showAll) "Show Less" else "Show All (${tags.size})") }
            }
        }
    }
}

@Composable
private fun MangaRecommendationsSection(
    recommendations: List<MangaMedia>,
    preferEnglishTitles: Boolean,
    onMangaClick: (MangaMedia) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Recommendations", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(recommendations.take(20)) { _, rec ->
                    val title = if (preferEnglishTitles && !rec.titleEnglish.isNullOrBlank()) rec.titleEnglish else rec.title
                    Card(
                        modifier = Modifier.width(110.dp).clickable { onMangaClick(rec) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column {
                            Box(modifier = Modifier.height(150.dp).fillMaxWidth()) {
                                AsyncImage(
                                    model = rec.cover, contentDescription = null,
                                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                                )
                            }
                            Text(title, modifier = Modifier.padding(6.dp), style = MaterialTheme.typography.labelSmall,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MangaExternalLinksSection(
    links: List<com.blissless.tensei.data.models.MangaExternalLink>
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("External Links", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            val ctx = LocalContext.current
            links.forEach { link ->
                link.url?.let { url ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                            try { ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) } catch (_: Exception) { }
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Link, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(link.site ?: url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun MangaStatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MangaStatusAlertDialog(
    currentStatus: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val statuses = listOf(
        "CURRENT" to "Reading", "PLANNING" to "Plan to Read",
        "COMPLETED" to "Completed", "PAUSED" to "Paused", "DROPPED" to "Dropped"
    )
    val icons = mapOf(
        "CURRENT" to Icons.Default.PlayArrow, "PLANNING" to Icons.Default.Schedule,
        "COMPLETED" to Icons.Default.CheckCircle, "PAUSED" to Icons.Default.Pause,
        "DROPPED" to Icons.Default.Cancel
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Status") },
        text = {
            Column {
                statuses.forEach { (key, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(key) }.padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(icons[key] ?: Icons.Default.BookmarkBorder, null,
                            tint = if (currentStatus == key) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.width(12.dp))
                        Text(label, fontWeight = if (currentStatus == key) FontWeight.Bold else FontWeight.Normal,
                            color = if (currentStatus == key) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        if (currentStatus == key) {
                            Spacer(Modifier.weight(1f))
                            Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun MangaFullChapterList(
    chapters: List<MangaChapter>,
    isLoading: Boolean,
    mangaId: Int,
    currentProgress: Int,
    viewModel: MainViewModel,
    onSelectChapter: (MangaChapter, Int) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp).padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Chapters", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close") }
                }
                HorizontalDivider()
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(chapters) { index, chapter ->
                            val isDownloaded = viewModel.isChapterDownloaded(mangaId, chapter.chapterNumber)
                            val isRead = index < currentProgress
                            var showDeleteConfirm by remember { mutableStateOf(false) }
                            androidx.compose.material3.ListItem(
                                headlineContent = {
                                    Text(chapter.title,
                                        fontWeight = if (isRead) FontWeight.Normal else FontWeight.Medium,
                                        color = if (isRead) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                                },
                                supportingContent = chapter.groups.take(2).let {
                                    if (it.isNotEmpty()) ({ Text(it.joinToString(", ")) }) else null
                                },
                                leadingContent = {
                                    if (isDownloaded) {
                                        IconButton(onClick = { showDeleteConfirm = true }) {
                                            Icon(Icons.Default.DownloadDone, "Downloaded", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                        }
                                    } else if (isRead) {
                                        Icon(Icons.Default.CheckCircle, "Read", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                    }
                                },
                                modifier = Modifier.clickable { onSelectChapter(chapter, index) }
                            )
                            if (index < chapters.lastIndex) HorizontalDivider(modifier = Modifier.padding(start = 72.dp))

                            if (showDeleteConfirm) {
                                AlertDialog(
                                    onDismissRequest = { showDeleteConfirm = false },
                                    title = { Text("Delete Download") },
                                    text = { Text("Delete downloaded chapter ${chapter.title}?") },
                                    confirmButton = {
                                        TextButton(onClick = { viewModel.deleteMangaChapter(mangaId, chapter.chapterNumber); showDeleteConfirm = false }) {
                                            Text("Delete", color = MaterialTheme.colorScheme.error)
                                        }
                                    },
                                    dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun mangaStatusLabel(status: String?): String = when (status) {
    "CURRENT" -> "Reading"
    "PLANNING" -> "Plan to Read"
    "COMPLETED" -> "Completed"
    "PAUSED" -> "Paused"
    "DROPPED" -> "Dropped"
    else -> "Add to List"
}

private fun formatMangaNumber(n: Int): String = when {
    n >= 1_000_000 -> "${n / 1_000_000}M"
    n >= 1_000 -> "${n / 1_000}K"
    else -> n.toString()
}
