package com.blissless.tensei.download

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import androidx.core.content.edit

@UnstableApi
class EpisodeDownloadManager(private val context: Context) {

    companion object {
        private const val TAG = "AnimeDownload"
        private const val PREFS_NAME = "episode_downloads"
        private const val METADATA_KEY = "download_metadata"
        private const val NOTIFICATION_CHANNEL = "anime_downloads"
    }

    data class DownloadInfo(
        val animeId: Int,
        val animeName: String,
        val episode: Int,
        val videoUrl: String,
        val cacheKey: String,
        val referer: String,
        val videoTitle: String,
        val subtitleUrl: String?,
        val subtitlePath: String?,
        val state: Int,
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val failureReason: Int,
        val malId: Int? = null,
        val year: Int? = null,
        val category: String = "sub",
        val downloadTimestamp: Long = 0L,
    )

    @Serializable
    data class SubtitleTrackData(
        val url: String,
        val lang: String,
        val cachedPath: String? = null,
    )

    @Serializable
    data class DownloadMetadata(
        val animeId: Int,
        val animeName: String,
        val episode: Int,
        val videoUrl: String,
        val cacheKey: String,
        val referer: String,
        val videoTitle: String,
        val subtitleUrl: String?,
        val subtitlePath: String? = null,
        val subtitleTracks: List<SubtitleTrackData> = emptyList(),
        val malId: Int? = null,
        val year: Int? = null,
        val totalBytes: Long = 0L,
        val category: String = "sub",
        val downloadTimestamp: Long = 0L,
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private var downloadManager: DownloadManager? = null
    private var downloadCache: SimpleCache? = null

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val innerFactory = OkHttpDataSource.Factory(okHttpClient)

    private val _downloadsInfo = MutableStateFlow<Map<String, DownloadInfo>>(emptyMap())
    val downloadsInfo: StateFlow<Map<String, DownloadInfo>> = _downloadsInfo.asStateFlow()

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    private var metadataStore: MutableMap<String, DownloadMetadata> = mutableMapOf()
    private val headersStore: MutableMap<String, Map<String, String>> = mutableMapOf()

    private val prefs: SharedPreferences
        get() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val batchResults = mutableMapOf<String, MutableList<String>>()
    private val batchTotals = mutableMapOf<String, Int>()
    private val batchEpisodeNumbers = mutableMapOf<String, Set<Int>>()
    private val batchCancelledFlags = mutableMapOf<String, Boolean>()
    private val _activeBatches = MutableStateFlow<Set<String>>(emptySet())
    val activeBatches: StateFlow<Set<String>> = _activeBatches.asStateFlow()

    private fun createNotificationChannel() {
        try {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL,
                "Episode Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Episode download progress and completion"
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
            Log.d(TAG, "createNotificationChannel: channel created")
        } catch (e: Exception) {
            Log.w(TAG, "createNotificationChannel: failed", e)
        }
    }

    private fun notifyWithPermission(notificationId: Int, notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                NotificationManagerCompat.from(context).notify(notificationId, notification)
            }
        } else {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }
    }

    private fun sendNotification(episodeKey: String, title: String, content: String, showProgress: Boolean = false, progress: Int = 0, maxProgress: Int = 0, icon: Int = android.R.drawable.stat_sys_download) {
        try {
            val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL)
                .setSmallIcon(icon)
                .setContentTitle(title)
                .setContentText(content)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
            if (showProgress && maxProgress > 0) {
                builder.setProgress(maxProgress, progress, false)
                    .setOngoing(true)
                    .setAutoCancel(false)
            }
            notifyWithPermission(episodeKey.hashCode(), builder.build())
        } catch (e: Exception) {
            Log.w(TAG, "sendNotification: failed", e)
        }
    }

    fun startBatchNotification(animeName: String, total: Int, episodes: Set<Int> = emptySet()) {
        try {
            val batchId = "batch_${animeName.hashCode()}"
            batchResults[batchId] = mutableListOf()
            batchTotals[animeName] = total
            batchEpisodeNumbers[animeName] = episodes
            batchCancelledFlags.remove(animeName)
            _activeBatches.value += animeName
            val tapIntent = Intent(context, com.blissless.tensei.MainActivity::class.java).apply {
                putExtra("notification_anime", animeName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context, animeName.hashCode(), tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(animeName)
                .setContentText("Downloading $total episodes...")
                .setStyle(NotificationCompat.InboxStyle()
                    .setBigContentTitle(animeName)
                    .setSummaryText("0/$total"))
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .build()
            notifyWithPermission(batchId.hashCode(), notification)
        } catch (e: Exception) {
            Log.w(TAG, "startBatchNotification: failed", e)
        }
    }

    fun updateBatchNotification(animeName: String, episode: Int, success: Boolean, completed: Int, failed: Int, total: Int) {
        try {
            val batchId = "batch_${animeName.hashCode()}"
            val results = batchResults.getOrPut(batchId) { mutableListOf() }
            results.add(if (success) "Ep $episode: ✓" else "Ep $episode: ✗")

            val done = completed + failed
            val remaining = total - done

            val tapIntent = Intent(context, com.blissless.tensei.MainActivity::class.java).apply {
                putExtra("notification_anime", animeName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context, animeName.hashCode(), tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val inboxStyle = NotificationCompat.InboxStyle().setBigContentTitle(animeName)
            val showLines = results.takeLast(6)
            for (line in showLines) {
                inboxStyle.addLine(line)
            }
            val summaryText = if (remaining == 0) {
                "$completed/$total downloaded${if (failed > 0) ", $failed failed" else ""}"
            } else {
                "$done/$total episodes${if (failed > 0) ", $failed failed" else ""}"
            }
            inboxStyle.setSummaryText(summaryText)

            val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL)
                .setSmallIcon(if (remaining == 0) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_sys_download)
                .setContentTitle(if (remaining == 0) "Download Complete" else "Downloading")
                .setContentText("$animeName - $summaryText")
                .setStyle(inboxStyle)
                .setOngoing(remaining > 0)
                .setAutoCancel(remaining == 0)
                .setContentIntent(pendingIntent)
                .build()
            notifyWithPermission(batchId.hashCode(), notification)

            if (remaining == 0) {
                batchResults.remove(batchId)
                _activeBatches.value -= animeName
                batchEpisodeNumbers.remove(animeName)
            }
        } catch (e: Exception) {
            Log.w(TAG, "updateBatchNotification: failed", e)
        }
    }

    fun cancelBatchNotification(animeName: String, completed: Int = 0, total: Int = 0) {
        _activeBatches.value -= animeName
        batchCancelledFlags.remove(animeName)
        try {
            val batchId = "batch_${animeName.hashCode()}"
            val summary = if (total > 0) "Download stopped — $completed/$total Episodes downloaded"
                          else "Download stopped — $animeName canceled"
            val tapIntent = Intent(context, com.blissless.tensei.MainActivity::class.java).apply {
                putExtra("notification_anime", animeName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context, animeName.hashCode(), tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Downloads Stopped")
                .setContentText(summary)
                .setStyle(NotificationCompat.InboxStyle()
                    .setBigContentTitle(animeName)
                    .setSummaryText(summary))
                .setOngoing(false)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()
            notifyWithPermission(batchId.hashCode(), notification)
        } catch (e: Exception) {
            Log.w(TAG, "cancelBatchNotification: failed", e)
        }
    }

    fun initialize() {
        if (downloadManager != null) {
            Log.i(TAG, "initialize: already initialized, skipping")
            return
        }
        Log.i(TAG, "initialize: starting download manager initialization")
        createNotificationChannel()
        try {
            val databaseProvider = StandaloneDatabaseProvider(context)
            Log.d(TAG, "initialize: database provider created")

            val cacheDir = File(context.cacheDir, "download_cache")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
                Log.d(TAG, "initialize: created cache directory: $cacheDir")
            }
            downloadCache = SimpleCache(cacheDir, LeastRecentlyUsedCacheEvictor(Long.MAX_VALUE), databaseProvider)
            Log.d(TAG, "initialize: SimpleCache created at $cacheDir")

            val upstreamFactory = createHeaderAwareDataSourceFactory()
            val executor = Executors.newSingleThreadExecutor()

            downloadManager = DownloadManager(
                context, databaseProvider, downloadCache!!, upstreamFactory, executor
            ).apply {
                maxParallelDownloads = 1
                Log.d(TAG, "initialize: DownloadManager created, maxParallelDownloads=1")
                addListener(object : DownloadManager.Listener {
                    override fun onDownloadChanged(
                        downloadManager: DownloadManager,
                        download: Download,
                        exception: Exception?,
                    ) {
                        val id = download.request.id
                        val stateName = when (download.state) {
                            Download.STATE_QUEUED -> "QUEUED"
                            Download.STATE_STOPPED -> "STOPPED"
                            Download.STATE_DOWNLOADING -> "DOWNLOADING"
                            Download.STATE_COMPLETED -> "COMPLETED"
                            Download.STATE_FAILED -> "FAILED"
                            Download.STATE_REMOVING -> "REMOVING"
                            Download.STATE_RESTARTING -> "RESTARTING"
                            else -> "UNKNOWN(${download.state})"
                        }
                        if (exception != null) {
                            Log.w(TAG, "onDownloadChanged: $id -> $stateName, reason=${download.failureReason}", exception)
                        } else {
                            Log.d(TAG, "onDownloadChanged: $id -> $stateName, progress=${download.bytesDownloaded}/${download.contentLength}")
                        }
                        when (download.state) {
                            Download.STATE_DOWNLOADING -> {
                                val meta = metadataStore[id]
                                if (meta != null) {
                                    val batchId = "batch_${meta.animeName.hashCode()}"
                                    if (batchId !in batchResults) {
                                        sendNotification(id, "Downloading", "${meta.animeName} - Ep ${meta.episode} starting...", showProgress = true, icon = android.R.drawable.stat_sys_download)
                                    }
                                }
                            }
                            Download.STATE_COMPLETED -> {
                                val meta = metadataStore[id]
                                if (meta != null) {
                                    val bytes = download.bytesDownloaded
                                    val contentLen = download.contentLength
                                    Log.i(TAG, "onDownloadChanged: $id COMPLETED, bytes=$bytes contentLength=$contentLen url=${download.request.uri.toString().take(80)}")
                                    if (bytes in 1..<1_000_000L) {
                                        Log.w(TAG, "onDownloadChanged: $id completed but only $bytes bytes, likely a stub file")
                                    }
                                    val now = System.currentTimeMillis()
                                    metadataStore[id] = meta.copy(
                                        totalBytes = if (bytes > 0) bytes else meta.totalBytes,
                                        downloadTimestamp = if (meta.downloadTimestamp == 0L) now else meta.downloadTimestamp,
                                    )
                                    saveMetadataToPrefs()
                                    val batchId = "batch_${meta.animeName.hashCode()}"
                                    if (batchId !in batchResults) {
                                        sendNotification(id, "Successfully downloaded", "${meta.animeName} - Ep ${meta.episode}", icon = android.R.drawable.stat_sys_download_done)
                                    }
                                }
                            }
                            Download.STATE_FAILED -> {
                                val meta = metadataStore[id]
                                if (meta != null) {
                                    val exMsg = if (exception != null) "${exception.message}" else "Unknown error"
                                    val failureReason = download.failureReason
                                    Log.e(TAG, "onDownloadChanged: $id FAILED: $exMsg (reason=$failureReason)")
                                    _errors.tryEmit("Download failed for ${id}: $exMsg")
                                    val batchId = "batch_${meta.animeName.hashCode()}"
                                    if (batchId !in batchResults) {
                                        sendNotification(id, "Failed download for", "${meta.animeName} - Ep ${meta.episode}", icon = android.R.drawable.stat_sys_warning)
                                    }
                                }
                            }

                            Download.STATE_QUEUED -> {
                                TODO()
                            }

                            Download.STATE_REMOVING -> {
                                TODO()
                            }

                            Download.STATE_RESTARTING -> {
                                TODO()
                            }

                            Download.STATE_STOPPED -> {
                                TODO()
                            }
                        }
                        updateDownloadInfo(download)
                    }

                    override fun onDownloadRemoved(
                        downloadManager: DownloadManager,
                        download: Download,
                    ) {
                        val id = download.request.id
                        Log.i(TAG, "onDownloadRemoved: $id")
                        val updated = _downloadsInfo.value.toMutableMap()
                        updated.remove(id)
                        _downloadsInfo.value = updated
                        metadataStore.remove(id)
                        saveMetadataToPrefs()
                    }
                })
                resumeDownloads()
                Log.d(TAG, "initialize: resumeDownloads called")
            }

            loadMetadataFromPrefs()
            loadHeadersFromPrefs()
            loadExistingDownloads()
            reconcileState()
            Log.i(TAG, "initialize: complete, ${_downloadsInfo.value.size} tracked downloads")
        } catch (e: Exception) {
            Log.e(TAG, "initialize: failed to initialize download manager", e)
        }
    }

    private fun createHeaderAwareDataSourceFactory(): DataSource.Factory {
        return innerFactory
    }

    private fun updateActiveHeaders() {
        val allHeaders = mutableMapOf<String, String>()
        for (headers in headersStore.values) {
            allHeaders.putAll(headers)
        }
        innerFactory.setDefaultRequestProperties(allHeaders)
        Log.d(TAG, "updateActiveHeaders: set ${allHeaders.size} default headers: ${allHeaders.keys}")
    }

    private fun loadExistingDownloads() {
        val dm = downloadManager ?: return
        val downloadIndex = dm.downloadIndex
        try {
            val cursor = downloadIndex.getDownloads()
            var count = 0
            while (cursor.moveToNext()) {
                updateDownloadInfo(cursor.download)
                count++
            }
            cursor.close()
            Log.d(TAG, "loadExistingDownloads: loaded $count existing downloads")
        } catch (e: Exception) {
            Log.w(TAG, "loadExistingDownloads: failed", e)
        }
    }

    private fun updateDownloadInfo(download: Download) {
        val id = download.request.id
        val meta = metadataStore[id]
        val storedTotal = meta?.totalBytes ?: 0L
        val totalBytes = when {
            storedTotal > 0L -> storedTotal
            download.contentLength > 0 -> download.contentLength
            download.state == Download.STATE_COMPLETED -> download.bytesDownloaded
            else -> 0L
        }
        val progress = when {
            download.state == Download.STATE_COMPLETED -> 1f
            download.contentLength > 0 -> (download.bytesDownloaded.toFloat() / download.contentLength).coerceIn(0f, 1f)
            totalBytes > 0 -> (download.bytesDownloaded.toFloat() / totalBytes).coerceIn(0f, 1f)
            else -> 0f
        }
        val info = DownloadInfo(
            animeId = meta?.animeId ?: 0,
            animeName = meta?.animeName ?: "",
            episode = meta?.episode ?: 0,
            videoUrl = meta?.videoUrl ?: download.request.uri.toString(),
            cacheKey = download.request.customCacheKey ?: "",
            referer = meta?.referer ?: "",
            videoTitle = meta?.videoTitle ?: "",
            subtitleUrl = meta?.subtitleUrl,
            subtitlePath = meta?.subtitlePath,
            state = download.state,
            progress = progress,
            downloadedBytes = download.bytesDownloaded,
            totalBytes = totalBytes,
            failureReason = download.failureReason,
            malId = meta?.malId,
            year = meta?.year,
            category = meta?.category ?: "sub",
            downloadTimestamp = meta?.downloadTimestamp ?: 0L,
        )
        _downloadsInfo.value += (id to info)
    }

    private fun reconcileState() {
        val dm = downloadManager ?: return
        val currentIds = mutableSetOf<String>()
        var querySucceeded = false
        try {
            val cursor = dm.downloadIndex.getDownloads()
            while (cursor.moveToNext()) {
                currentIds.add(cursor.download.request.id)
            }
            cursor.close()
            querySucceeded = true
        } catch (e: Exception) {
            Log.w(TAG, "reconcileState: failed to get download index", e)
        }
        if (querySucceeded) {
            val staleKeys = metadataStore.keys.filter { it !in currentIds }
            if (staleKeys.isNotEmpty()) {
                Log.d(TAG, "reconcileState: removing ${staleKeys.size} stale metadata entries: $staleKeys")
                staleKeys.forEach { metadataStore.remove(it) }
                saveMetadataToPrefs()
            }
        } else {
            Log.w(TAG, "reconcileState: download index query failed, skipping metadata reconciliation to avoid data loss")
        }
        Log.d(TAG, "reconcileState: ${metadataStore.size} metadata entries, ${_downloadsInfo.value.size} tracked downloads")
    }

    fun startDownload(
        animeId: Int,
        animeName: String,
        episode: Int,
        videoUrl: String,
        referer: String,
        videoTitle: String,
        subtitleUrl: String?,
        subtitleTracks: List<eu.kanade.tachiyomi.animesource.model.Track> = emptyList(),
        videoHeaders: Map<String, String>,
        mimeType: String,
        malId: Int? = null,
        year: Int? = null,
        category: String = "sub",
    ): Boolean {
        val id = "${animeId}_$episode"
        val cacheKey = "download_$id"
        Log.i(TAG, "startDownload: id=$id anime=$animeName ep=$episode url=${videoUrl.take(80)} mime=$mimeType")

        if (videoUrl.isBlank()) {
            val msg = "startDownload: id=$id - blank video URL, cannot download"
            Log.e(TAG, msg)
            _errors.tryEmit("Download failed for Ep $episode: blank video URL")
            return false
        }

        if (videoHeaders.isNotEmpty()) {
            headersStore.clear()
            headersStore[id] = videoHeaders
            saveHeadersForDownload(id, videoHeaders)
            updateActiveHeaders()
            Log.d(TAG, "startDownload: saved ${videoHeaders.size} headers for $id")
        } else if (headersStore.isNotEmpty()) {
            headersStore.clear()
            updateActiveHeaders()
        }

        val isStreaming = mimeType == "application/x-mpegurl" || mimeType == "application/dash+xml"
        val request = try {
            DownloadRequest.Builder(id, videoUrl.toUri())
                .apply {
                    if (!isStreaming) {
                        setCustomCacheKey(cacheKey)
                    } else {
                        Log.d(TAG, "startDownload: $id is HLS/DASH, skipping customCacheKey")
                    }
                }
                .setMimeType(mimeType)
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "startDownload: failed to build DownloadRequest for $id", e)
            _errors.tryEmit("Download failed for Ep $episode: invalid request - ${e.message}")
            return false
        }

        Log.d(TAG, "startDownload: id=$id videoUrl=${videoUrl.take(80)} subtitleUrl=${subtitleUrl?.take(80)}")

        val localSubtitlePath = if (!subtitleUrl.isNullOrBlank()) {
            val p = cacheSubtitle(id, subtitleUrl)
            Log.d(TAG, "startDownload: subtitle cached path=$p")
            p
        } else {
            Log.d(TAG, "startDownload: no subtitle URL provided")
            null
        }

        val cachedSubtitleTracks = subtitleTracks.mapNotNull { track ->
            val path = cacheSubtitle(id, track.url, track.lang)
            if (path != null) SubtitleTrackData(track.url, track.lang, path) else null
        }
        if (cachedSubtitleTracks.isNotEmpty()) {
            Log.d(TAG, "startDownload: cached ${cachedSubtitleTracks.size} subtitle tracks for $id")
        }

        metadataStore[id] = DownloadMetadata(
            animeId = animeId,
            animeName = animeName,
            episode = episode,
            videoUrl = videoUrl,
            cacheKey = cacheKey,
            referer = referer,
            videoTitle = videoTitle,
            subtitleUrl = subtitleUrl,
            subtitlePath = localSubtitlePath,
            subtitleTracks = cachedSubtitleTracks,
            malId = malId,
            year = year,
            category = category,
        )
        saveMetadataToPrefs()

        val dm = downloadManager
        if (dm == null) {
            Log.e(TAG, "startDownload: downloadManager is null, not initialized")
            _errors.tryEmit("Download failed for Ep $episode: download manager not initialized")
            return false
        }

        try {
            dm.addDownload(request)
            dm.resumeDownloads()
            Log.i(TAG, "startDownload: successfully added download for $id (resumeDownloads called)")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "startDownload: addDownload failed for $id", e)
            _errors.tryEmit("Download failed for Ep $episode: ${e.message}")
            metadataStore.remove(id)
            saveMetadataToPrefs()
            return false
        }
    }

    fun removeDownload(id: String) {
        Log.i(TAG, "removeDownload: $id")
        try {
            downloadManager?.removeDownload(id)
        } catch (e: Exception) {
            Log.w(TAG, "removeDownload: failed to remove $id from DownloadManager", e)
        }
        metadataStore.remove(id)
        headersStore.remove(id)
        updateActiveHeaders()
        saveMetadataToPrefs()
        prefs.edit {remove("headers_$id") }
        Log.d(TAG, "removeDownload: $id removed successfully")
    }

    fun removeAnime(animeName: String) {
        Log.i(TAG, "removeAnime: $animeName")
        val ids = _downloadsInfo.value.filter { it.value.animeName == animeName }.keys.toList()
        Log.d(TAG, "removeAnime: found ${ids.size} downloads for $animeName")
        for (id in ids) {
            removeDownload(id)
        }
    }

    fun getBatchEpisodes(animeName: String): Set<Int> {
        return batchEpisodeNumbers[animeName] ?: emptySet()
    }

    fun isBatchCancelled(animeName: String): Boolean {
        return batchCancelledFlags[animeName] == true
    }

    fun cancelBatch(animeName: String) {
        batchCancelledFlags[animeName] = true
        _activeBatches.value -= animeName
        val completed = _downloadsInfo.value.values.count {
            it.animeName == animeName && it.state == Download.STATE_COMPLETED
        }
        val total = batchTotals[animeName] ?: (completed + _downloadsInfo.value.values.count {
            it.animeName == animeName && (it.state == Download.STATE_QUEUED || it.state == Download.STATE_DOWNLOADING)
        })
        for ((id, info) in _downloadsInfo.value) {
            if (info.animeName == animeName &&
                (info.state == Download.STATE_QUEUED || info.state == Download.STATE_DOWNLOADING)
            ) {
                removeDownload(id)
            }
        }
        cancelBatchNotification(animeName, completed, total)
    }

    data class GroupedDownload(
        val animeName: String,
        val episodes: List<DownloadInfo>,
        val totalSize: Long,
    )

    fun getOfflineDataSourceFactory(): DataSource.Factory? {
        val cache = downloadCache ?: return null
        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory { FileDataSource() }
            .setFlags(0)
    }

    fun getSubtitleTracks(id: String): List<SubtitleTrackData> {
        val meta = metadataStore[id] ?: return emptyList()
        var changed = false

        val fromTracks = meta.subtitleTracks.map { track ->
            if (track.cachedPath != null && File(track.cachedPath).exists()) {
                track
            } else {
                val path = cacheSubtitle(id, track.url, track.lang)
                if (path != null) {
                    changed = true
                    track.copy(cachedPath = path)
                } else track
            }
        }

        val fromLegacy = if (fromTracks.isEmpty() && !meta.subtitleUrl.isNullOrBlank()) {
            val path = if (meta.subtitlePath != null && File(meta.subtitlePath).exists()) {
                meta.subtitlePath
            } else {
                val p = cacheSubtitle(id, meta.subtitleUrl)
                if (p != null) { changed = true; p } else null
            }
            if (path != null) listOf(SubtitleTrackData(meta.subtitleUrl, "en", path)) else emptyList()
        } else {
            emptyList()
        }

        if (changed) {
            metadataStore[id] = meta.copy(subtitleTracks = fromTracks, subtitlePath = fromTracks.firstOrNull()?.cachedPath ?: meta.subtitlePath)
            saveMetadataToPrefs()
        }
        return fromTracks + fromLegacy
    }

    private fun cacheSubtitle(id: String, subtitleUrl: String, lang: String = ""): String? {
        return kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            try {
                var builder = okhttp3.Request.Builder().url(subtitleUrl)
                val downloadHeaders = headersStore[id]
                if (downloadHeaders != null) {
                    for ((key, value) in downloadHeaders) {
                        builder = builder.header(key, value)
                    }
                }
                val request = builder.build()
                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    Log.w(TAG, "cacheSubtitle: HTTP ${response.code} for $id, headers=$downloadHeaders")
                    return@runBlocking null
                }
                val bodyBytes = response.body.bytes()
                val subDir = File(context.cacheDir, "subtitles")
                subDir.mkdirs()
                val suffix = if (lang.isNotEmpty()) "_${lang.replace(Regex("\\W+"), "_")}" else ""
                val subFile = File(subDir, "$id$suffix.vtt")
                subFile.writeBytes(bodyBytes)
                val path = subFile.absolutePath
                Log.d(TAG, "cacheSubtitle: cached subtitle ($id$suffix) to $path (${bodyBytes.size} bytes)")
                return@runBlocking path
            } catch (e: Exception) {
                Log.w(TAG, "cacheSubtitle: failed for $id", e)
                return@runBlocking null
            }
        }
    }

    fun probeVideoMimeType(url: String, headers: Map<String, String>): String {
        return kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            try {
                var builder = okhttp3.Request.Builder().url(url)
                for ((key, value) in headers) {
                    builder = builder.header(key, value)
                }
                val request = builder.build()
                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    Log.d(TAG, "probeVideoMimeType: HTTP ${response.code} for ${url.take(60)}")
                    response.close()
                    return@runBlocking fallbackVideoMimeType(url)
                }
                val contentType = response.body.contentType()
                if (contentType != null) {
                    val ct = contentType.type.lowercase() + "/" + contentType.subtype.lowercase()
                    if (ct.contains("mpegurl") || ct == "application/vnd.apple.mpegurl") {
                        Log.d(TAG, "probeVideoMimeType: detected HLS via Content-Type: $ct for ${url.take(60)}")
                        response.close()
                        return@runBlocking "application/x-mpegurl"
                    }
                }
                val body = response.body
                val stream = body.byteStream()
                val first = ByteArray(20)
                val read = stream.use { stream ->
                    stream.read(first)
                }
                response.close()
                if (read >= 7) {
                    val header = String(first, 0, read, Charsets.UTF_8)
                    if (header.startsWith("#EXTM3U")) {
                        Log.d(TAG, "probeVideoMimeType: detected HLS via #EXTM3U signature for ${url.take(60)}")
                        return@runBlocking "application/x-mpegurl"
                    }
                }
                Log.d(TAG, "probeVideoMimeType: no HLS signature detected for ${url.take(60)}, firstBytes=${String(first, 0, minOf(read, 20), Charsets.UTF_8).replace('\n', '|')}")
                return@runBlocking fallbackVideoMimeType(url)
            } catch (e: Exception) {
                Log.w(TAG, "probeVideoMimeType: failed for ${url.take(60)}", e)
                return@runBlocking fallbackVideoMimeType(url)
            }
        }
    }

    private fun fallbackVideoMimeType(url: String): String = when {
        url.contains(".m3u8") -> "application/x-mpegurl"
        url.contains(".mp4") -> "video/mp4"
        url.contains(".webm") -> "video/webm"
        else -> "video/mp4"
    }

    private fun saveHeadersForDownload(id: String, headers: Map<String, String>) {
        try {
            prefs.edit {putString("headers_$id", json.encodeToString(headers))}
            Log.d(TAG, "saveHeadersForDownload: saved headers for $id")
        } catch (e: Exception) {
            Log.w(TAG, "saveHeadersForDownload: failed for $id", e)
        }
    }

    private fun saveMetadataToPrefs() {
        try {
            prefs.edit { putString(METADATA_KEY, json.encodeToString(metadataStore)) }
            Log.d(TAG, "saveMetadataToPrefs: saved ${metadataStore.size} entries")
        } catch (e: Exception) {
            Log.w(TAG, "saveMetadataToPrefs: failed", e)
        }
    }

    private fun loadMetadataFromPrefs() {
        try {
            val str = prefs.getString(METADATA_KEY, null) ?: return
            metadataStore = json.decodeFromString<Map<String, DownloadMetadata>>(str).toMutableMap()
            Log.d(TAG, "loadMetadataFromPrefs: loaded ${metadataStore.size} entries")
        } catch (e: Exception) {
            Log.w(TAG, "loadMetadataFromPrefs: failed", e)
        }
    }

    private fun loadHeadersFromPrefs() {
        try {
            val all = prefs.all
            var count = 0
            for ((key, value) in all) {
                if (key.startsWith("headers_") && value is String) {
                    val id = key.removePrefix("headers_")
                    headersStore[id] = json.decodeFromString<Map<String, String>>(value)
                    count++
                }
            }
            Log.d(TAG, "loadHeadersFromPrefs: loaded headers for $count downloads")
        } catch (e: Exception) {
            Log.w(TAG, "loadHeadersFromPrefs: failed", e)
        }
    }

    @SuppressLint("UseKtx")
    fun clearDownloadCache(): Long {
        Log.i(TAG, "clearDownloadCache: clearing all downloads")
        var bytesCleared = 0L
        try {
            downloadManager?.removeAllDownloads()
            Log.d(TAG, "clearDownloadCache: removed all downloads from manager")
            val cache = downloadCache
            if (cache != null) {
                cache.release()
                downloadCache = null
                Log.d(TAG, "clearDownloadCache: cache released")
            }
            val cacheDir = File(context.cacheDir, "download_cache")
            if (cacheDir.exists()) {
                cacheDir.listFiles()?.forEach { file ->
                    bytesCleared += file.length()
                    file.delete()
                }
                Log.d(TAG, "clearDownloadCache: cleared $bytesCleared bytes from disk")
            }
            downloadManager = null
            _downloadsInfo.value = emptyMap()
            metadataStore.clear()
            headersStore.clear()
            prefs.edit { clear() }
            Log.i(TAG, "clearDownloadCache: complete, cleared $bytesCleared bytes")
        } catch (e: Exception) {
            Log.e(TAG, "clearDownloadCache: failed", e)
        }
        return bytesCleared
    }

    fun getDownloadCacheSize(): Long {
        try {
            val cacheDir = File(context.cacheDir, "download_cache")
            if (cacheDir.exists()) {
                val size = cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                Log.d(TAG, "getDownloadCacheSize: $size bytes")
                return size
            }
        } catch (e: Exception) {
            Log.w(TAG, "getDownloadCacheSize: failed to calculate", e)
        }
        return 0L
    }
}


