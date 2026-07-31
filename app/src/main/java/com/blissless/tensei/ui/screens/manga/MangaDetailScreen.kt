package com.blissless.tensei.ui.screens.manga

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.blissless.tensei.MainViewModel
import com.blissless.tensei.data.models.MangaChapter
import com.blissless.tensei.data.models.MangaDetail
import com.blissless.tensei.data.models.MangaMedia
import com.blissless.tensei.viewmodel.clearChapterImages
import com.blissless.tensei.viewmodel.clearMangaDetail
import com.blissless.tensei.viewmodel.downloadMangaChapter
import com.blissless.tensei.viewmodel.fetchMangaDetail
import com.blissless.tensei.viewmodel.isChapterDownloaded
import com.blissless.tensei.viewmodel.isLoadingManga
import com.blissless.tensei.viewmodel.isLoadingMangaChapters
import com.blissless.tensei.viewmodel.loadMangaChapters
import com.blissless.tensei.viewmodel.loadChapterImages
import com.blissless.tensei.viewmodel.mangaChapterImages
import com.blissless.tensei.viewmodel.mangaChapters
import com.blissless.tensei.viewmodel.mangaDetail
import com.blissless.tensei.viewmodel.markMangaChapterRead
import com.blissless.tensei.viewmodel.updateMangaStatus

@Composable
fun MangaDetailScreen(
    manga: MangaMedia,
    viewModel: MainViewModel,
    isOled: Boolean,
    preferEnglishTitles: Boolean,
    onDismiss: () -> Unit,
    onStartReader: (chapterIndex: Int) -> Unit
) {
    val detail by viewModel.mangaDetail.collectAsState()
    val chapters by viewModel.mangaChapters.collectAsState()
    val isLoading by viewModel.isLoadingManga.collectAsState()
    val isLoadingChapters by viewModel.isLoadingMangaChapters.collectAsState()
    val chapterImages by viewModel.mangaChapterImages.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(manga.id) {
        viewModel.fetchMangaDetail(manga.id)
        viewModel.loadMangaChapters(manga.id, manga.title)
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.clearMangaDetail()
        }
    }

    var showStatusMenu by remember { mutableStateOf(false) }
    var showChapterList by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var selectedChapter by remember { mutableStateOf<MangaChapter?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = if (isOled) Color.Black else MaterialTheme.colorScheme.background
        ) {
            Box {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Header Section
                    item {
                        HeaderSection(
                            detail = detail,
                            manga = manga,
                            isOled = isOled,
                            onCopyTitle = { text ->
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("manga_title", text))
                            }
                        )
                    }

                    // Action Buttons
                    item {
                        ActionButtonsCard(
                            detail = detail,
                            chapters = chapters,
                            isLoading = isLoading,
                            isLoadingChapters = isLoadingChapters,
                            onStartReader = {
                                val startIndex = manga.progress.coerceAtLeast(1) - 1
                                onStartReader(startIndex.coerceAtMost(chapters.lastIndex).coerceAtLeast(0))
                            },
                            onShowStatusMenu = { showStatusMenu = true },
                            onShowChapterList = { showChapterList = true },
                            currentStatus = manga.listStatus,
                            currentProgress = manga.progress
                        )
                    }

                    // Synopsis
                    detail?.description?.let { desc ->
                        item { ExpandableSection(title = "Synopsis", content = desc) }
                    }

                    // Genres
                    item {
                        val genres = detail?.genres ?: manga.genres
                        if (genres.isNotEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isOled) Color(0xFF111111) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("Genres", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(8.dp))
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        genres.forEach { genre ->
                                            SuggestionChip(
                                                onClick = {},
                                                label = { Text(genre, style = MaterialTheme.typography.labelSmall) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Relations
                    val relations = detail?.relations
                    if (relations != null && relations.isNotEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isOled) Color(0xFF111111) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("Relations", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(8.dp))
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        items(relations) { relation ->
                                            RelationCard(relation = relation)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Tags
                    val tags = detail?.tags
                    if (tags != null && tags.isNotEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isOled) Color(0xFF111111) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("Tags", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(8.dp))
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        tags.take(20).forEach { tag ->
                                            AssistChip(
                                                onClick = {},
                                                label = { Text(tag.name, style = MaterialTheme.typography.labelSmall) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Dismiss button
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp).statusBarsPadding()
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }
    }

    if (showStatusMenu) {
        MangaStatusDialog(
            currentStatus = manga.listStatus,
            onSelect = { status ->
                viewModel.updateMangaStatus(manga.id, status)
                showStatusMenu = false
            },
            onDismiss = { showStatusMenu = false }
        )
    }

    if (showChapterList) {
        MangaChapterListDialog(
            chapters = chapters,
            isLoading = isLoadingChapters,
            mangaId = manga.id,
            mangaTitle = detail?.title ?: manga.title,
            coverUrl = detail?.cover ?: manga.cover,
            currentProgress = manga.progress,
            viewModel = viewModel,
            onSelectChapter = { chapter, index ->
                onStartReader(index)
                showChapterList = false
            },
            onDismiss = { showChapterList = false }
        )
    }
}

@Composable
private fun HeaderSection(
    detail: MangaDetail?,
    manga: MangaMedia,
    isOled: Boolean,
    onCopyTitle: (String) -> Unit
) {
    val bannerUrl = detail?.banner ?: manga.banner
    Box(modifier = Modifier.fillMaxWidth()) {
        if (!bannerUrl.isNullOrEmpty()) {
            AsyncImage(
                model = bannerUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(240.dp),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, if (isOled) Color.Black else MaterialTheme.colorScheme.background)
                        )
                    )
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
                        .width(120.dp)
                        .aspectRatio(0.7f)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = detail?.title ?: manga.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        color = if (bannerUrl.isNullOrEmpty()) MaterialTheme.colorScheme.onSurface else Color.White
                    )
                    detail?.titleEnglish?.let { engTitle ->
                        if (engTitle != (detail?.title ?: manga.title)) {
                            Text(
                                text = engTitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (bannerUrl.isNullOrEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else Color.White.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        detail?.averageScore?.let { score ->
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF4CAF50).copy(alpha = 0.8f)
                            ) {
                                Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                                    Spacer(Modifier.width(2.dp))
                                    Text("${score / 10}", style = MaterialTheme.typography.labelSmall, color = Color.White)
                                }
                            }
                        }
                        detail?.status?.let { status ->
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF666666).copy(alpha = 0.8f)
                            ) {
                                Text(
                                    text = status.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() },
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White
                                )
                            }
                        }
                        detail?.format?.let { format ->
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF666666).copy(alpha = 0.8f)
                            ) {
                                Text(
                                    text = format.replace("_", " "),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionButtonsCard(
    detail: MangaDetail?,
    chapters: List<MangaChapter>,
    isLoading: Boolean,
    isLoadingChapters: Boolean,
    onStartReader: () -> Unit,
    onShowStatusMenu: () -> Unit,
    onShowChapterList: () -> Unit,
    currentStatus: String,
    currentProgress: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Button(
                onClick = onStartReader,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                enabled = chapters.isNotEmpty() && !isLoadingChapters
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (currentProgress > 0) "Continue Ch. ${currentProgress + 1}" else "Start Reading")
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onShowStatusMenu,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.BookmarkBorder, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(statusLabel(currentStatus))
                }
                OutlinedButton(
                    onClick = onShowChapterList,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${chapters.size} Ch.")
                }
            }
            // Stats
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatItem("Chapters", "${detail?.chapters ?: chapters.size}")
                detail?.volumes?.let { StatItem("Volumes", "$it") }
                detail?.popularity?.let { StatItem("Popularity", formatNumber(it)) }
                detail?.favourites?.let { StatItem("Favorites", formatNumber(it)) }
            }
        }
    }
}

@Composable
private fun ExpandableSection(title: String, content: String) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            val displayText = if (expanded) content else content.take(300) + if (content.length > 300) "..." else ""
            Text(
                text = displayText.replace(Regex("<[^>]*>"), ""),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (content.length > 300) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Show Less" else "Read More")
                }
            }
        }
    }
}

@Composable
private fun RelationCard(relation: com.blissless.tensei.data.models.MangaRelation) {
    Card(
        modifier = Modifier.width(140.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            AsyncImage(
                model = relation.cover,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(180.dp),
                contentScale = ContentScale.Crop
            )
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = relation.title,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = relation.relationType.replace("_", " "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun MangaStatusDialog(
    currentStatus: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val statuses = listOf(
        "CURRENT" to "Reading",
        "PLANNING" to "Plan to Read",
        "COMPLETED" to "Completed",
        "PAUSED" to "Paused",
        "DROPPED" to "Dropped"
    )
    val icons = mapOf(
        "CURRENT" to Icons.Default.PlayArrow,
        "PLANNING" to Icons.Default.Schedule,
        "COMPLETED" to Icons.Default.CheckCircle,
        "PAUSED" to Icons.Default.Pause,
        "DROPPED" to Icons.Default.Cancel
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Status") },
        text = {
            Column {
                statuses.forEach { (key, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(key) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            icons[key] ?: Icons.Default.BookmarkBorder,
                            contentDescription = null,
                            tint = if (currentStatus == key) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = label,
                            fontWeight = if (currentStatus == key) FontWeight.Bold else FontWeight.Normal,
                            color = if (currentStatus == key) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        if (currentStatus == key) {
                            Spacer(Modifier.weight(1f))
                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun MangaChapterListDialog(
    chapters: List<MangaChapter>,
    isLoading: Boolean,
    mangaId: Int,
    mangaTitle: String,
    coverUrl: String,
    currentProgress: Int,
    viewModel: MainViewModel,
    onSelectChapter: (MangaChapter, Int) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Chapters", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                HorizontalDivider()
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(chapters) { index, chapter ->
                            val isDownloaded = viewModel.isChapterDownloaded(mangaId, chapter.chapterNumber)
                            val isRead = index < currentProgress
                            ListItem(
                                headlineContent = {
                                    Text(
                                        text = chapter.title,
                                        fontWeight = if (isRead) FontWeight.Normal else FontWeight.Medium,
                                        color = if (isRead) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                supportingContent = chapter.groups.take(2).let {
                                    if (it.isNotEmpty()) ({ Text(it.joinToString(", ")) }) else null
                                },
                                leadingContent = {
                                    if (isDownloaded) {
                                        Icon(Icons.Default.DownloadDone, contentDescription = "Downloaded", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                    } else if (isRead) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = "Read", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                    }
                                },
                                modifier = Modifier.clickable { onSelectChapter(chapter, index) }
                            )
                            if (index < chapters.lastIndex) HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun statusLabel(status: String): String = when (status) {
    "CURRENT" -> "Reading"
    "PLANNING" -> "Plan to Read"
    "COMPLETED" -> "Completed"
    "PAUSED" -> "Paused"
    "DROPPED" -> "Dropped"
    else -> "Add to List"
}

private fun formatNumber(n: Int): String = when {
    n >= 1_000_000 -> "${n / 1_000_000}M"
    n >= 1_000 -> "${n / 1_000}K"
    else -> n.toString()
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
