package com.blissless.tensei.ui.screens.downloads

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.blissless.tensei.MainViewModel
import com.blissless.tensei.data.manga.DownloadedManga
import com.blissless.tensei.data.manga.MangaDownloadStatus
import com.blissless.tensei.data.manga.MangaDownloadTask
import com.blissless.tensei.data.models.MangaChapter
import com.blissless.tensei.ui.components.SectionHeader
import com.blissless.tensei.ui.screens.manga.MangaChapterListWithGroups
import com.blissless.tensei.util.ErrorHandler
import com.blissless.tensei.viewmodel.deleteMangaDownload
import com.blissless.tensei.viewmodel.mangaActiveDownloads
import com.blissless.tensei.viewmodel.mangaDownloadDirectoryUri
import com.blissless.tensei.viewmodel.mangaDownloads
import com.blissless.tensei.viewmodel.mangaLocationPermissionDenied
import com.blissless.tensei.viewmodel.refreshMangaDownloads
import com.blissless.tensei.viewmodel.setMangaDownloadLocation
import kotlinx.coroutines.delay

/** Extracts a human-readable path like `/Download/Anime` from a SAF tree URI. */
fun mangaLocationDisplayPath(uri: String?): String? {
    if (uri == null) return null
    return try {
        java.net.URLDecoder.decode(
            uri.substringAfter("%3A").substringAfter(":"),
            "UTF-8"
        ).let { path ->
            if (path.isNotEmpty()) "/$path" else null
        }
    } catch (e: Exception) {
        ErrorHandler.ignore("MangaDownloadsScreen", "best-effort operation failed", e)
        null
    }
}

@Composable
fun MangaDownloadsContent(
    viewModel: MainViewModel,
    isOled: Boolean,
    locationPromptShown: Boolean,
    onLocationPromptShown: () -> Unit,
    onOpenMangaChapters: (DownloadedManga) -> Unit = {},
) {
    val context = LocalContext.current
    val mangaDownloads by viewModel.mangaDownloads.collectAsState()
    val activeTasks by viewModel.mangaActiveDownloads.collectAsState()
    val downloadUri by viewModel.mangaDownloadDirectoryUri.collectAsState()
    val permissionDenied by viewModel.mangaLocationPermissionDenied.collectAsState()
    val locationText = remember(downloadUri) {
        if (downloadUri == null) "App internal storage (default)" else mangaLocationDisplayPath(downloadUri) ?: "Custom folder"
    }

    var showLocationPrompt by remember { mutableStateOf(false) }
    var mangaDeleteConfirm by remember { mutableStateOf<DownloadedManga?>(null) }

    val directoryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) { ErrorHandler.ignore("MangaDownloadsScreen", "best-effort operation failed", e) }
            viewModel.setMangaDownloadLocation(uri.toString())
        }
    }

    // Re-scan on every open so files moved into the folder (or a new SAF location) appear
    // immediately instead of only at app startup.
    LaunchedEffect(Unit) {
        viewModel.refreshMangaDownloads()
    }

    // First time this screen is opened without a custom location, offer to set one.
    LaunchedEffect(downloadUri, locationPromptShown) {
        if (downloadUri == null && !locationPromptShown) {
            onLocationPromptShown()
            showLocationPrompt = true
        }
    }

    val groupedActive = remember(activeTasks) { activeTasks.groupBy { it.mangaId } }
    val hasContent = mangaDownloads.isNotEmpty() || groupedActive.isNotEmpty()

    Box(modifier = Modifier.fillMaxSize()) {
        if (!hasContent) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            if (isOled) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(20.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Storage,
                        contentDescription = null,
                        tint = if (isOled) Color.White.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "No Manga Downloads",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isOled) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (permissionDenied) {
                    Text(
                        "Tensei lost access to this folder.\nRe-select $locationText so downloaded chapters appear.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { directoryPickerLauncher.launch(null) }) {
                        Text("Re-select folder")
                    }
                } else {
                    Text(
                        "Downloaded chapters will appear here.\nOpen a manga and start downloading.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isOled) Color.White.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 48.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Stored in $locationText",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isOled) Color.White.copy(alpha = 0.3f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.navigationBarsPadding()
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Manga Downloads",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Stored in $locationText",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isOled) Color.White.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                        IconButton(onClick = { directoryPickerLauncher.launch(null) }) {
                            Icon(
                                Icons.Default.FolderOpen,
                                contentDescription = "Change download location",
                                tint = if (isOled) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (groupedActive.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Downloading",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isOled) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.primary
                        )
                    }
                    groupedActive.forEach { (mangaId, tasks) ->
                        val title = tasks.first().mangaTitle
                        item(key = "manga_in_progress_$mangaId") {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isOled) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    tasks.sortedBy { it.chapterNumber }.forEach { task ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Ch. ${formatChapterNumber(task.chapterNumber)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (isOled) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = when (task.status) {
                                                    MangaDownloadStatus.QUEUED -> "Queued"
                                                    MangaDownloadStatus.DOWNLOADING -> "${task.downloadedPages}/${task.pageCount} pages"
                                                    MangaDownloadStatus.COMPLETED -> "Saving"
                                                    MangaDownloadStatus.FAILED -> "Failed"
                                                    MangaDownloadStatus.CANCELLED -> "Cancelled"
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (task.status == MangaDownloadStatus.FAILED) Color(0xFFEF5350)
                                                else if (isOled) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }

                if (mangaDownloads.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        SectionHeader(
                            title = "Saved",
                            icon = Icons.Default.Storage,
                            count = mangaDownloads.size,
                        )
                    }
                    items(mangaDownloads, key = { it.mangaId }) { manga ->
                        MangaDownloadsCard(
                            manga = manga,
                            isOled = isOled,
                            onClick = { onOpenMangaChapters(manga) },
                            onDeleteManga = { mangaDeleteConfirm = manga },
                        )
                    }
                }
            }
        }
    }

    if (showLocationPrompt) {
        AlertDialog(
            onDismissRequest = { showLocationPrompt = false },
            containerColor = if (isOled) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surface,
            title = { Text("Manga Download Location", color = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface) },
            text = {
                Text(
                    "Choose where downloaded manga chapters are stored. You can pick any folder, or keep the default app storage.",
                    color = if (isOled) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showLocationPrompt = false
                    directoryPickerLauncher.launch(null)
                }) { Text("Choose Folder", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showLocationPrompt = false
                    viewModel.setMangaDownloadLocation(null)
                }) { Text("Use Default") }
            }
        )
    }

    if (mangaDeleteConfirm != null) {
        AlertDialog(
            onDismissRequest = { mangaDeleteConfirm = null },
            containerColor = if (isOled) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surface,
            title = { Text("Delete Downloads?", color = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface) },
            text = {
                Text(
                    "All downloaded chapters for ${mangaDeleteConfirm!!.mangaTitle} will be removed.",
                    color = if (isOled) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteMangaDownload(mangaDeleteConfirm!!.mangaId)
                    mangaDeleteConfirm = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { mangaDeleteConfirm = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun MangaDownloadsCard(
    manga: DownloadedManga,
    isOled: Boolean,
    onClick: () -> Unit,
    onDeleteManga: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isOled) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(manga.coverUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 48.dp, height = 68.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isOled) Color(0xFF2A2A2A) else MaterialTheme.colorScheme.surfaceVariant)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = manga.mangaTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${manga.chapters.size} chapter${if (manga.chapters.size == 1) "" else "s"} downloaded",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isOled) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDeleteManga) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete all",
                    tint = Color(0xFFEF5350),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Full-screen offline chapter selection for one downloaded manga. Reuses the exact same
 * chapter list UI as the reader (header + search + grouped collapsible chapters), showing
 * every chapter that exists on disk. Shows a brief loading screen on open (matching the
 * online chapter selection) before revealing the list. No chapter images are preloaded
 * here, so the list itself renders fast regardless of how many files are present; the
 * reader shows a loading indicator only once a chapter is actually opened. Chapter/manga
 * deletion both require a confirmation dialog.
 */
@Composable
fun OfflineMangaChaptersScreen(
    manga: DownloadedManga,
    isOled: Boolean,
    onDismiss: () -> Unit,
    onReadChapter: (Float) -> Unit,
    onDeleteChapter: (Float) -> Unit,
    onDeleteAll: () -> Unit,
) {
    var chapterDeleteConfirm by remember { mutableStateOf<Float?>(null) }
    var mangaDeleteConfirm by remember { mutableStateOf(false) }

    // Brief loading screen before the chapter list appears, matching the online chapter
    // selection. The chapter data is already in memory from the download scan, but the list
    // build (grouping hundreds of chapters) and the navbar hide happen in the same frame as
    // the screen opening, so a short loading state keeps the transition clean and consistent
    // with the online flow.
    var showLoading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(500)
        showLoading = false
    }

    // Every chapter shown here is already on disk — built from the scan result, no network.
    val sortedChapters = remember(manga) { manga.chapters.sortedBy { it.chapterNumber } }
    val chapterList = remember(sortedChapters) {
        sortedChapters.map { ch ->
            MangaChapter(
                url = "anilist_${manga.mangaId}_ch_${formatChapterNumber(ch.chapterNumber)}",
                title = if (ch.chapterTitle.isNotBlank()) ch.chapterTitle else "Chapter ${formatChapterNumber(ch.chapterNumber)}",
                chapterId = "anilist_${manga.mangaId}_ch_${formatChapterNumber(ch.chapterNumber)}",
                chapterNumber = ch.chapterNumber
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isOled) Color.Black else MaterialTheme.colorScheme.background)
    ) {
        if (showLoading) {
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
            MangaChapterListWithGroups(
                chapters = chapterList,
                readIndices = emptySet(),
                // Off-screen sentinel: nothing is "next to read" and the Continue button stays hidden.
                nextChapterToRead = chapterList.size,
                onChapterClick = { index ->
                    val num = chapterList.getOrNull(index)?.chapterNumber ?: return@MangaChapterListWithGroups
                    onReadChapter(num)
                },
                modifier = Modifier.fillMaxSize(),
                isLoadingChapters = false,
                hasLoadedChapters = true,
                downloadedChapterNumbers = emptySet(),
                onContinueReading = null,
                onRetryLoadChapters = null,
                onBack = onDismiss,
                onDeleteChapter = { index ->
                    val num = chapterList.getOrNull(index)?.chapterNumber ?: return@MangaChapterListWithGroups
                    chapterDeleteConfirm = num
                },
                onDeleteAll = { mangaDeleteConfirm = true },
            )
        }
    }

    if (chapterDeleteConfirm != null) {
        val num = chapterDeleteConfirm!!
        AlertDialog(
            onDismissRequest = { chapterDeleteConfirm = null },
            containerColor = if (isOled) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surface,
            title = { Text("Delete Chapter?", color = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface) },
            text = {
                Text(
                    "Ch. ${formatChapterNumber(num)} will be removed from ${manga.mangaTitle}. This cannot be undone.",
                    color = if (isOled) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteChapter(num)
                    chapterDeleteConfirm = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { chapterDeleteConfirm = null }) { Text("Cancel") }
            }
        )
    }

    if (mangaDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { mangaDeleteConfirm = false },
            containerColor = if (isOled) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surface,
            title = { Text("Delete Downloads?", color = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface) },
            text = {
                Text(
                    "All ${manga.chapters.size} downloaded chapters for ${manga.mangaTitle} will be removed. This cannot be undone.",
                    color = if (isOled) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteAll()
                    mangaDeleteConfirm = false
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { mangaDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

private fun formatChapterNumber(chapterNumber: Float): String {
    return if (chapterNumber % 1f == 0f) chapterNumber.toInt().toString() else chapterNumber.toString()
}
