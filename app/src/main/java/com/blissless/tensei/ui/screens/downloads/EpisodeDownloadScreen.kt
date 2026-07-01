package com.blissless.tensei.ui.screens.downloads

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import com.blissless.tensei.MainViewModel
import com.blissless.tensei.data.models.AnimeMedia
import com.blissless.tensei.download.EpisodeDownloadManager
import com.blissless.tensei.extensions.ExtensionsViewModel
import com.blissless.tensei.TenseiApplication
import com.blissless.tensei.torrent.TorrentEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds
import java.io.File
import kotlin.coroutines.resume

private const val TAG = "AnimeDownload"

private enum class DownloadMode { ALL, UNDOWNLOADED, SELECTIVE, RANGE }
private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${"%.1f".format(bytes.toFloat() / (1024 * 1024))} MB"
}

@OptIn(UnstableApi::class)
@Composable

fun EpisodeDownloadDialog(
    anime: AnimeMedia,
    viewModel: MainViewModel,
    downloadManager: EpisodeDownloadManager,
    isOled: Boolean,
    preferEnglishTitles: Boolean = true,
    onDismiss: () -> Unit,
    onNavigateToSettings: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val extViewModel: ExtensionsViewModel = viewModel()
    val extUiState by extViewModel.uiState.collectAsState()
    val total = anime.totalEpisodes.coerceAtLeast(1)
    val released = anime.latestEpisode ?: total
    val displayTitle = if (preferEnglishTitles && !anime.titleEnglish.isNullOrEmpty()) anime.titleEnglish else anime.title

    val downloadsInfo by downloadManager.downloadsInfo.collectAsState()
    val completedEpisodes = downloadsInfo.values.filter {
        it.animeId == anime.id && it.state == Download.STATE_COMPLETED
    }.map { it.episode }.toSet()

    val inProgressEps = downloadsInfo.values.filter {
        it.animeId == anime.id && it.state == Download.STATE_DOWNLOADING
    }.associateBy { it.episode }

    var mode by remember { mutableStateOf(DownloadMode.ALL) }
    var selectedEpisodes by remember { mutableStateOf(setOf<Int>()) }
    var rangeFrom by remember { mutableIntStateOf(1) }
    var rangeTo by remember { mutableIntStateOf(released) }
    var showRangePicker by remember { mutableStateOf<String?>(null) }

    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var currentEpisode by remember { mutableIntStateOf(0) }
    var completedCount by remember { mutableIntStateOf(0) }
    var failedCount by remember { mutableIntStateOf(0) }
    var totalToDownload by remember { mutableIntStateOf(0) }
    var requestedDownloadIds by remember { mutableStateOf(setOf<String>()) }
    var showNoExtDialog by remember { mutableStateOf(false) }
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorDialogMessage by remember { mutableStateOf("") }
    var showFailureDetail by remember { mutableStateOf<String?>(null) }
    var showBatteryOptDialog by remember { mutableStateOf(false) }
    var batteryOptDismissed by remember { mutableStateOf(false) }
    var downloadActive by remember { mutableStateOf(true) }
    var batchCancelled by remember { mutableStateOf(false) }
    val isIgnoringBatteryOptimizations = remember {
        val pm = context.getSystemService(PowerManager::class.java)
        pm?.isIgnoringBatteryOptimizations(context.packageName) == true
    }

    DisposableEffect(Unit) {
        onDispose { downloadActive = false }
    }

    LaunchedEffect(Unit) {
        if (!isIgnoringBatteryOptimizations && !batteryOptDismissed) {
            showBatteryOptDialog = true
        }
        downloadManager.errors.collect { errorMsg ->
            errorDialogMessage = errorMsg
            showErrorDialog = true
            Log.w(TAG, "Download error received: $errorMsg")
        }
    }

    fun getEpisodesToDownload(): List<Int> = when (mode) {
        DownloadMode.ALL -> (1..released).toList()
        DownloadMode.UNDOWNLOADED -> (1..released).filter { it !in completedEpisodes }
        DownloadMode.SELECTIVE -> selectedEpisodes.sorted()
        DownloadMode.RANGE -> (rangeFrom..rangeTo).filter { it in 1..released }
    }

    suspend fun downloadExtensionEpisode(ep: Int, displayTitle: String, resolvedCategory: String, extensionPkg: String): Boolean {
        val result = withContext(Dispatchers.IO) {
            try {
                viewModel.playEpisodeWithExtension(anime, ep, extensionPkg)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "playEpisodeWithExtension failed for Ep $ep", e)
                null
            }
        }
        if (result == null || result.videos.isEmpty()) {
            Log.w(TAG, "startDownload: no result or empty videos for ep $ep")
            return false
        }
        val sortedVideos = result.videos.sortedByDescending { it.resolution ?: 0 }
        var downloadSucceeded = false
        for ((videoIndex, video) in sortedVideos.withIndex()) {
            Log.d(TAG, "startDownload: ep $ep trying video ${videoIndex + 1}/${sortedVideos.size}: ${video.videoTitle} (${video.resolution}p) url=${video.videoUrl.take(100)}")
            val mimeType = downloadManager.probeVideoMimeType(video.videoUrl, result.videoHeaders)
            val started = downloadManager.startDownload(
                animeId = anime.id, animeName = displayTitle, episode = ep,
                videoUrl = video.videoUrl, referer = result.referer, videoTitle = video.videoTitle,
                subtitleUrl = result.subtitleUrl, subtitleTracks = result.subtitleTrackList,
                videoHeaders = result.videoHeaders, mimeType = mimeType,
                malId = anime.malId, year = anime.year, category = resolvedCategory,
            )
            if (!started) {
                Log.w(TAG, "startDownload: ep $ep video ${videoIndex + 1} failed to start, trying next source...")
                continue
            }
            Log.i(TAG, "startDownload: ep $ep video ${videoIndex + 1} initiated, waiting...")
            val downloadId = "${anime.id}_$ep"
            var finalInfo: EpisodeDownloadManager.DownloadInfo?
            while (true) {
                if (batchCancelled || downloadManager.isBatchCancelled(displayTitle)) return false
                val d = downloadManager.downloadsInfo.value[downloadId]
                if (d != null && d.state != Download.STATE_QUEUED && d.state != Download.STATE_DOWNLOADING) {
                    finalInfo = d; break
                }
                delay(500.milliseconds)
            }
            val completed = finalInfo.state == Download.STATE_COMPLETED
            val totalBytes = finalInfo.totalBytes
            val tooSmall = completed && totalBytes > 0L && totalBytes < 1_000_000L
            if (tooSmall) {
                Log.w(TAG, "startDownload: ep $ep video ${videoIndex + 1} completed but only ${formatBytes(totalBytes)}, likely a stub file, treating as failure")
                downloadManager.removeDownload(downloadId)
            } else if (completed) {
                downloadSucceeded = true
                Log.i(TAG, "startDownload: ep $ep download succeeded with video ${videoIndex + 1} (${formatBytes(totalBytes)})")
            } else {
                Log.w(TAG, "startDownload: ep $ep video ${videoIndex + 1} failed, trying next source...")
                downloadManager.removeDownload(downloadId)
            }
        }
        if (!downloadSucceeded) Log.w(TAG, "startDownload: all video sources failed for ep $ep")
        return downloadSucceeded
    }

    suspend fun downloadTorrentEpisode(ep: Int, displayTitle: String): Boolean {
        Log.i(TAG, "downloadTorrentEpisode: fetching magnet for ep $ep...")
        val magnetUri = withContext(Dispatchers.IO) {
            viewModel.fetchMagnetForEpisode(anime, ep)
        }
        if (magnetUri.isNullOrBlank()) {
            Log.w(TAG, "downloadTorrentEpisode: no magnet link for ep $ep")
            return false
        }
        Log.i(TAG, "downloadTorrentEpisode: got magnet for ep $ep, starting torrent download...")

        val engine = (context.applicationContext as TenseiApplication).torrentEngine
        if (!engine.isRunning.get()) engine.start()
        engine.removeCurrentTorrent()

        val videoExts = setOf("mkv", "mp4", "webm", "avi", "mov", "m4v")
        val epPattern = Regex("(?:^|[._ \\[\\]()-])0*$ep(?:\$|[._ \\[\\]()-])", RegexOption.IGNORE_CASE)
        var fileIndex = -1
        var fileName = ""
        var filePath = ""

        var torrentListener: TorrentEngine.EngineListener? = null
        val result = suspendCancellableCoroutine<Boolean> { cont ->
            val listener = object : TorrentEngine.EngineListener {
                override fun onMetadataReceived(meta: com.blissless.tensei.torrent.TorrentMeta) {
                    Log.i(TAG, "downloadTorrentEpisode: metadata received, name='${meta.name}' files=${meta.files.size}")
                    val videoFiles = meta.files.filter { f ->
                        f.name.substringAfterLast('.', "").lowercase() in videoExts
                    }
                    val matched = videoFiles.filter { f ->
                        epPattern.containsMatchIn(f.name) || epPattern.containsMatchIn(f.path)
                    }
                    val idx = if (matched.isNotEmpty()) {
                        matched.maxBy { it.size }.index
                    } else {
                        videoFiles.maxByOrNull { it.size }?.index ?: -1
                    }
                    if (idx < 0) {
                        Log.w(TAG, "downloadTorrentEpisode: no video file found in torrent")
                        engine.removeCurrentTorrent()
                        if (!cont.isCompleted) cont.resume(false)
                        return
                    }
                    fileIndex = idx
                    filePath = engine.getFileSavePath(fileIndex) ?: ""
                    fileName = filePath.substringAfterLast(File.separator)
                    Log.i(TAG, "downloadTorrentEpisode: selected fileIndex=$fileIndex file='$fileName'")
                    engine.startDownload(fileIndex)
                }

                override fun onProgress(downloaded: Long, total: Long) {
                    if (total > 0) {
                        val progress = downloaded.toFloat() / total
                        val epProgress = (completedCount.toFloat() + progress) / totalToDownload.coerceAtLeast(1)
                        downloadProgress = epProgress
                    }
                }

                override fun onFinished() {
                    Log.i(TAG, "downloadTorrentEpisode: download finished for ep $ep")
                    engine.removeCurrentTorrent()
                    if (!cont.isCompleted) cont.resume(true)
                }

                override fun onError(message: String) {
                    Log.e(TAG, "downloadTorrentEpisode: error for ep $ep: $message")
                    engine.removeCurrentTorrent()
                    if (!cont.isCompleted) cont.resume(false)
                }
            }
            torrentListener = listener
            engine.addListener(listener)
            engine.addTorrentFromMagnet(magnetUri)
            cont.invokeOnCancellation {
                engine.removeListener(listener)
                engine.removeCurrentTorrent()
            }
        }
        torrentListener?.let { engine.removeListener(it) }
        if (!result) {
            Log.w(TAG, "downloadTorrentEpisode: torrent download failed for ep $ep")
            return false
        }

        if (filePath.isBlank()) {
            Log.w(TAG, "downloadTorrentEpisode: save path is blank, cannot copy file")
            return false
        }
        val srcFile = File(filePath)
        if (!srcFile.exists() || srcFile.length() < 1_000_000L) {
            Log.w(TAG, "downloadTorrentEpisode: file missing or too small: $filePath")
            return false
        }

        val ext = fileName.substringAfterLast('.', "mkv")

        // Always cache locally
        val cacheDir = File(context.cacheDir, "torrent_downloads").also { it.mkdirs() }
        val cachedFile = File(cacheDir, "${anime.id}_$ep.$ext")
        try {
            if (cachedFile.exists()) cachedFile.delete()
            srcFile.copyTo(cachedFile)
            Log.i(TAG, "downloadTorrentEpisode: cached to ${cachedFile.absolutePath}")
        } catch (e: Exception) {
            Log.w(TAG, "downloadTorrentEpisode: failed to cache file", e)
        }

        // Also try to save to the user's custom download directory if set
        val treeUriStr = viewModel.downloadDirectoryUri.value
        if (!treeUriStr.isNullOrBlank()) {
            try {
                val treeUri = Uri.parse(treeUriStr)
                context.contentResolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            } catch (_: Exception) {}
            try {
                val treeUri = Uri.parse(treeUriStr)
                val cr = context.contentResolver
                val sanitizedAnime = displayTitle.replace(Regex("[:\\\\/*?\"<>|]"), "_").trim()
                val episodeStr = ep.toString().padStart(2, '0')
                val outName = "${sanitizedAnime} E$episodeStr.$ext"
                val animeDirId = findOrCreateDirectory(cr, treeUri, sanitizedAnime)
                if (animeDirId != null) {
                    val dirUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, animeDirId)
                    val existingId = findDocument(cr, dirUri, outName)
                    if (existingId != null) {
                        Log.i(TAG, "downloadTorrentEpisode: $outName already exists, skipping copy")
                    } else {
                        val newFileUri = DocumentsContract.createDocument(cr, dirUri, "video/$ext", outName)
                        if (newFileUri != null) {
                            cr.openOutputStream(newFileUri)?.use { out ->
                                srcFile.inputStream().use { it.copyTo(out) }
                            }
                            Log.i(TAG, "downloadTorrentEpisode: copied $outName to download directory")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "downloadTorrentEpisode: failed to copy to download dir", e)
                // Fallback: save to Downloads folder via MediaStore
                try {
                    val sanitizedAnime = displayTitle.replace(Regex("[:\\\\/*?\"<>|]"), "_").trim()
                    val episodeStr = ep.toString().padStart(2, '0')
                    val outName = "${sanitizedAnime} E$episodeStr.$ext"
                    val values = ContentValues().apply {
                        put(MediaStore.Files.FileColumns.DISPLAY_NAME, outName)
                        put(MediaStore.Files.FileColumns.MIME_TYPE, "video/$ext")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(MediaStore.Files.FileColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Tensei")
                            put(MediaStore.Files.FileColumns.IS_PENDING, 1)
                        }
                    }
                    val uri = context.contentResolver.insert(MediaStore.Files.getContentUri("external"), values)
                    if (uri != null) {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            srcFile.inputStream().use { it.copyTo(out) }
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            values.clear()
                            values.put(MediaStore.Files.FileColumns.IS_PENDING, 0)
                            context.contentResolver.update(uri, values, null, null)
                        }
                        Log.i(TAG, "downloadTorrentEpisode: saved to Downloads/Tensei/$outName")
                    }
                } catch (e2: Exception) {
                    Log.w(TAG, "downloadTorrentEpisode: MediaStore fallback also failed", e2)
                }
            }
        }

        Log.i(TAG, "downloadTorrentEpisode: ep $ep download complete")
        return true
    }

    fun startDownload() {
        val streamMethod = viewModel.streamMethod.value
        val isMagnet = streamMethod == "magnet"
        val extPkg = if (isMagnet) {
            viewModel.defaultMagnetExtension.value ?: ""
        } else {
            viewModel.defaultExtensionPackage.value
        }
        Log.i(TAG, "startDownload: anime=${anime.id} streamMethod=$streamMethod isMagnet=$isMagnet extPkg='$extPkg'")
        if (extPkg.isEmpty()) {
            Log.w(TAG, "startDownload: no default extension set")
            showNoExtDialog = true
            return
        }

        val existingActive = downloadsInfo.values.any {
            it.state == Download.STATE_DOWNLOADING || it.state == Download.STATE_QUEUED
        }
        if (existingActive || isDownloading) {
            Log.w(TAG, "startDownload: a download batch is already active")
            Toast.makeText(context, "A download is already in progress", Toast.LENGTH_SHORT).show()
            return
        }

        val downloadCategoryPref = viewModel.downloadPreferredCategory.value
        val streamCategoryPref = viewModel.preferredCategory.value
        val resolvedCategory = if (downloadCategoryPref == "same_as_stream") streamCategoryPref else downloadCategoryPref
        val episodes = getEpisodesToDownload()
        if (episodes.isEmpty()) {
            Log.w(TAG, "startDownload: no episodes selected")
            Toast.makeText(context, "No episodes selected", Toast.LENGTH_SHORT).show()
            return
        }
        Log.i(TAG, "startDownload: downloading ${episodes.size} episodes: $episodes")

        isDownloading = true
        completedCount = 0
        failedCount = 0
        totalToDownload = episodes.size
        downloadProgress = 0f
        requestedDownloadIds = episodes.map { "${anime.id}_$it" }.toSet()
        Log.d(TAG, "startDownload: tracking ${requestedDownloadIds.size} download IDs: $requestedDownloadIds")

        downloadManager.startBatchNotification(displayTitle, episodes.size, episodes.toSet())

        val capturedEpisodes = episodes.toList()

        viewModel.viewModelScope.launch {
            if (isMagnet) requestedDownloadIds = emptySet()
            try {
                downloadManager.downloadDirectoryUri = viewModel.downloadDirectoryUri.value
                downloadManager.keepDownloadedFiles = viewModel.keepDownloadedFiles.value

                for (ep in capturedEpisodes) {
                    if (downloadManager.isBatchCancelled(displayTitle)) return@launch
                    currentEpisode = ep
                    Log.d(TAG, "startDownload: resolving episode $ep... (isMagnet=$isMagnet)")

                    val epSuccess = if (isMagnet) {
                        downloadTorrentEpisode(ep, displayTitle)
                    } else {
                        downloadExtensionEpisode(ep, displayTitle, resolvedCategory, extPkg)
                    }

                    completedCount += if (epSuccess) 1 else 0
                    failedCount += if (!epSuccess) 1 else 0
                    downloadManager.updateBatchNotification(
                        displayTitle, ep, epSuccess,
                        completedCount, failedCount, capturedEpisodes.size
                    )
                }
                if (isMagnet) {
                    isDownloading = false
                    requestedDownloadIds = emptySet()
                    if (failedCount > 0) {
                        val msg = "Downloaded $completedCount episodes, $failedCount failed."
                        Log.w(TAG, "Torrent download batch complete: $msg")
                        showFailureDetail = msg
                    } else {
                        Log.i(TAG, "Torrent download batch complete: all $completedCount episodes done")
                        Toast.makeText(context, "Downloaded $completedCount episodes", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (_: CancellationException) {
                Log.i(TAG, "startDownload: cancelled")
                if (isMagnet) { isDownloading = false; requestedDownloadIds = emptySet() }
            } catch (e: Exception) {
                Log.e(TAG, "startDownload: unexpected error", e)
                if (isMagnet) { isDownloading = false; requestedDownloadIds = emptySet() }
            }
        }
    }

    // Track overall progress from downloadManager state using the originally-requested IDs
    if (isDownloading && requestedDownloadIds.isNotEmpty()) {
        val relevant = downloadsInfo.filter { it.key in requestedDownloadIds }
        val queued = relevant.values.count { it.state == Download.STATE_QUEUED }
        val downloading = relevant.values.count { it.state == Download.STATE_DOWNLOADING }
        val done = relevant.values.count { it.state == Download.STATE_COMPLETED }
        val failed = relevant.values.count { it.state == Download.STATE_FAILED }
        val total = requestedDownloadIds.size
        val inProgress = queued + downloading + done + failed
        if (total > 0) {
            val sum = relevant.values.sumOf {
                when (it.state) {
                    Download.STATE_COMPLETED -> 1.0
                    Download.STATE_FAILED -> 1.0
                    else -> it.progress.toDouble().coerceIn(0.0, 1.0)
                }
            }
            downloadProgress = (sum / total).toFloat()
            completedCount = done
            failedCount = failed
        }
        Log.d(TAG, "progress: $done done, $failed failed, $queued queued, $downloading downloading, $inProgress/$total accounted")
        if ((done + failed) >= total && (done + failed) > 0) {
            isDownloading = false
            if (failed > 0) {
                val msg = "Downloaded $done episodes, $failed failed. Check logcat with 'AnimeDownload' filter for details."
                Log.w(TAG, "Download batch complete: $done success, $failed failed out of $total")
                showFailureDetail = msg
            } else {
                Log.i(TAG, "Download batch complete: all $done episodes downloaded successfully")
                Toast.makeText(context, "Downloaded $done episodes", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Dialog(
        onDismissRequest = { onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = if (isOled) Color.Black else MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = if (isOled) Color(0xFF0A0A0A) else MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                            .statusBarsPadding(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { onDismiss() }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = displayTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "$released episodes available",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isOled) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = mode == DownloadMode.ALL,
                        onClick = { mode = DownloadMode.ALL },
                        label = { Text("All") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    FilterChip(
                        selected = mode == DownloadMode.UNDOWNLOADED,
                        onClick = { mode = DownloadMode.UNDOWNLOADED },
                        label = { Text("Undownloaded") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    FilterChip(
                        selected = mode == DownloadMode.SELECTIVE,
                        onClick = { mode = DownloadMode.SELECTIVE },
                        label = { Text("Selective") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    FilterChip(
                        selected = mode == DownloadMode.RANGE,
                        onClick = { mode = DownloadMode.RANGE },
                        label = { Text("Range") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }

                AnimatedVisibility(visible = mode == DownloadMode.RANGE) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { showRangePicker = "from" },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("From: $rangeFrom", fontWeight = FontWeight.SemiBold)
                        }
                        Text("to", color = if (isOled) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(
                            onClick = { showRangePicker = "to" },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("To: $rangeTo", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                if (completedEpisodes.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.DownloadDone, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("${completedEpisodes.size} episodes already downloaded", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed((1..released).toList()) { _, ep ->
                        val isDownloaded = ep in completedEpisodes
                        val isDownloadingNow = ep in inProgressEps
                        val isSelected = when (mode) {
                            DownloadMode.ALL -> true
                            DownloadMode.UNDOWNLOADED -> ep !in completedEpisodes
                            DownloadMode.SELECTIVE -> ep in selectedEpisodes
                            DownloadMode.RANGE -> ep in rangeFrom..rangeTo
                        }

                        val bgColor = when {
                            isDownloaded -> if (isOled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            isSelected -> if (isOled) Color(0xFF1A1A2E) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            else -> if (isOled) Color(0xFF111111) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(if (isSelected || isDownloaded) 1f else 0.45f)
                                .then(
                                    if (isSelected)
                                        Modifier.border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                    else Modifier
                                ),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = bgColor),
                            onClick = {
                                if (mode == DownloadMode.SELECTIVE && !isDownloading) {
                                    selectedEpisodes = if (ep in selectedEpisodes)
                                        selectedEpisodes - ep
                                    else
                                        selectedEpisodes + ep
                                }
                            }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(
                                            if (isDownloaded) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                            else if (isSelected && mode == DownloadMode.SELECTIVE) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                            else Color.White.copy(alpha = 0.08f),
                                            RoundedCornerShape(10.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    when {
                                        isDownloaded -> Icon(Icons.Default.DownloadDone, contentDescription = "Downloaded", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                                        isSelected && mode == DownloadMode.SELECTIVE -> Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                                        mode == DownloadMode.SELECTIVE -> Icon(Icons.Default.RadioButtonUnchecked, contentDescription = "Not selected", tint = Color.White.copy(alpha = 0.35f), modifier = Modifier.size(22.dp))
                                        else -> Text("$ep", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isDownloadingNow) {
                                            val info = inProgressEps[ep]!!
                                            if (info.totalBytes > 0) "Episode $ep (${(info.progress * 100).toInt()}%)"
                                            else "Episode $ep (${formatBytes(info.downloadedBytes)})"
                                        } else "Episode $ep",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = when {
                                            isDownloaded -> MaterialTheme.colorScheme.primary
                                            isSelected -> if (isOled) Color.White else MaterialTheme.colorScheme.onSurface
                                            else -> if (isOled) Color.White.copy(alpha = 0.35f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                                        }
                                    )
                                }
                                if (isDownloaded) {
                                    Text("Saved", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = isDownloading) {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = if (isOled) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Episode $currentEpisode... ($completedCount done)",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isOled) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = if (isOled) Color(0xFF0A0A0A) else MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (isDownloading) {
                                    downloadActive = false
                                    batchCancelled = true
                                    val doneCount = requestedDownloadIds.count { id ->
                                        val d = downloadManager.downloadsInfo.value[id]
                                        d != null && d.state == Download.STATE_COMPLETED
                                    }
                                    requestedDownloadIds.forEach { id ->
                                        downloadManager.removeDownload(id)
                                    }
                                    downloadManager.cancelBatchNotification(displayTitle, doneCount, totalToDownload)
                                }
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            enabled = true
                        ) { Text(if (isDownloading) "Cancel" else "Close") }
                        Button(
                            onClick = { startDownload() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isDownloading
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isDownloading) "Downloading..." else "Download")
                        }
                    }
                }
            }
        }
    }

    if (showNoExtDialog) {
        AlertDialog(
            onDismissRequest = { showNoExtDialog = false },
            containerColor = if (isOled) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surface,
            title = { Text("No Default Extension", color = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface) },
            text = { Text("Set a default extension in Settings to enable downloads.", color = if (isOled) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = {
                    showNoExtDialog = false
                    onNavigateToSettings?.invoke()
                    onDismiss()
                }) {
                    Text(if (extUiState.extensions.isEmpty()) "Go to Extensions" else "Go to Stream Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNoExtDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showBatteryOptDialog) {
        AlertDialog(
            onDismissRequest = {
                showBatteryOptDialog = false
                batteryOptDismissed = true
            },
            containerColor = if (isOled) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surface,
            title = { Text("Disable Battery Optimization?", color = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface) },
            text = {
                Text(
                    "Downloads may pause or fail when the app is in the background due to battery optimization. " +
                    "Would you like to disable battery optimization for this app?",
                    color = if (isOled) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showBatteryOptDialog = false
                    batteryOptDismissed = true
                    try {
                        val intent = Intent(
                            Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
                        )
                        context.startActivity(intent)
                    } catch (_: Exception) {}
                }) { Text("Disable") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showBatteryOptDialog = false
                    batteryOptDismissed = true
                }) { Text("Continue Anyway") }
            }
        )
    }

    if (showErrorDialog) {
        AlertDialog(
            onDismissRequest = { showErrorDialog = false },
            containerColor = if (isOled) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surface,
            title = { Text("Download Error", color = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface) },
            text = { Text(errorDialogMessage, color = if (isOled) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = { showErrorDialog = false }) { Text("OK") }
            }
        )
    }

    if (showFailureDetail != null) {
        AlertDialog(
            onDismissRequest = { showFailureDetail = null },
            containerColor = if (isOled) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surface,
            title = { Text("Download Issues", color = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface) },
            text = { Text(showFailureDetail ?: "", color = if (isOled) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = { showFailureDetail = null }) { Text("OK") }
            }
        )
    }

    if (showRangePicker != null) {
        val isFrom = showRangePicker == "from"
        val available = if (isFrom) (1..rangeTo).toList() else (rangeFrom..released).toList()
        Dialog(
            onDismissRequest = { showRangePicker = null },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                shape = RoundedCornerShape(20.dp),
                color = if (isOled) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isFrom) "Select Start Episode" else "Select End Episode",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.height(300.dp)
                    ) {
                        items(available) { ep ->
                            val selected = if (isFrom) ep == rangeFrom else ep == rangeTo
                            Surface(
                                onClick = {
                                    if (isFrom) rangeFrom = ep else rangeTo = ep
                                    showRangePicker = null
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) MaterialTheme.colorScheme.primary
                                    else if (isOled) Color(0xFF2A2A2A) else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (selected) (if (isOled) Color.Black else Color.White)
                                    else if (isOled) Color.White else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(52.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Text(
                                        "$ep",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(
                        onClick = { showRangePicker = null },
                        modifier = Modifier.align(Alignment.End)
                    ) { Text("Cancel") }
                }
            }
        }
    }
}

private fun findOrCreateDirectory(cr: android.content.ContentResolver, treeUri: Uri, name: String): String? {
    val docId = DocumentsContract.getTreeDocumentId(treeUri)
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
    val projection = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
    try {
        cr.query(childrenUri, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val displayName = cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME))
                if (displayName == name) {
                    return cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID))
                }
            }
        }
    } catch (_: Exception) {}
    val dirUri = DocumentsContract.createDocument(cr, DocumentsContract.buildDocumentUriUsingTree(treeUri, docId), DocumentsContract.Document.MIME_TYPE_DIR, name)
    if (dirUri != null) return DocumentsContract.getDocumentId(dirUri)
    return null
}

private fun findDocument(cr: android.content.ContentResolver, parentUri: Uri, name: String): String? {
    val docId = DocumentsContract.getTreeDocumentId(parentUri)
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(parentUri, docId)
    val projection = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
    try {
        cr.query(childrenUri, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val displayName = cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME))
                if (displayName == name) {
                    return cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID))
                }
            }
        }
    } catch (_: Exception) {}
    return null
}

