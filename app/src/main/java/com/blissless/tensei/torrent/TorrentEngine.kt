package com.blissless.tensei.torrent

import android.content.Context
import android.util.Log
import org.libtorrent4j.*
import org.libtorrent4j.alerts.*
import org.libtorrent4j.swig.torrent_flags_t
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

class TorrentEngine(private val context: Context) {

    interface EngineListener {
        fun onMetadataReceived(meta: TorrentMeta)
        fun onProgress(downloaded: Long, total: Long)
        fun onFinished()
        fun onError(message: String)
    }

    private val sessionManager = SessionManager()
    private var handle: TorrentHandle? = null
    private var pendingStreamFileIndex = -1

    private var streamingFirstPiece = 0
    private var streamingLastPiece = 0
    private var streamingWindowStart = 0
    private var streamingWindowEnd = 0
    private var lastAdvancedPiece = -1
    private var streamingPrioritiesSet = false

    private val listeners = mutableListOf<EngineListener>()
    private val alertQueue = ConcurrentLinkedQueue<Alert<*>>()
    private var pollThread: Thread? = null
    val isRunning = AtomicBoolean(false)

    val saveDir: File
        get() = File(context.cacheDir, "torrent_stream").also { it.mkdirs() }

    private val sessionListener = object : AlertListener {
        override fun types(): IntArray? = null
        override fun alert(alert: Alert<*>) {
            alertQueue.offer(alert)
        }
    }

    fun start() {
        if (isRunning.getAndSet(true)) {
            Log.d(TAG, "start: already running, skipping")
            return
        }
        Log.d(TAG, "start: starting torrent engine, saveDir=${saveDir.absolutePath}")
        try {
            sessionManager.addListener(sessionListener)
            sessionManager.start()
            Log.d(TAG, "start: SessionManager started successfully")
            startPollLoop()
        } catch (e: Exception) {
            Log.e(TAG, "start: failed to start session", e)
            listeners.forEach { it.onError("Failed to start torrent engine: ${e.message}") }
        }
    }

    private fun startPollLoop() {
        pollThread?.interrupt()
        pollThread = Thread {
            Log.d(TAG, "Poll loop started")
            while (isRunning.get()) {
                try {
                    drainAlerts()
                    processProgress()
                    Thread.sleep(500)
                } catch (e: InterruptedException) { break }
                catch (e: Exception) {
                    if (isRunning.get()) Log.e(TAG, "Poll loop error", e)
                }
            }
            Log.d(TAG, "Poll loop exiting")
        }.apply {
            isDaemon = true
            name = "torrent-engine"
            start()
        }
    }

    private fun drainAlerts() {
        while (true) {
            val alert = alertQueue.poll() ?: break
            handleAlert(alert)
        }
    }

    private var lastProgressTime = 0L

    private fun processProgress() {
        val h = handle ?: return
        try {
            val st = h.status()
            val now = System.currentTimeMillis()
            if (now - lastProgressTime > 500) {
                lastProgressTime = now
                val downloaded = st.totalWantedDone()
                val total = st.totalWanted()
                val progress = if (total > 0) (downloaded * 100 / total) else 0
                val dlRate = st.downloadRate() / 1024
                val peers = st.numPeers()
                Log.d(TAG, "progress: ${downloaded}/${total} (${progress}%), ${dlRate}KB/s, ${peers} peers, state=${st.state().name}")
                listeners.forEach { it.onProgress(downloaded, total) }
                if (st.isFinished() || st.isSeeding()) {
                    Log.d(TAG, "processProgress: torrent finished/seeding")
                    listeners.forEach { it.onFinished() }
                }
                if (now % 2000 < 500) {
                    advanceStreamingWindow()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "processProgress: exception", e)
        }
    }

    private fun handleAlert(alert: Alert<*>) {
        try {
            when (alert) {
                is AddTorrentAlert -> {
                    val th = alert.handle()
                    Log.d(TAG, "handleAlert: AddTorrentAlert, handle valid=${th.isValid()}, error=${alert.message()}")
                    if (th.isValid()) {
                        handle = th
                        Log.d(TAG, "handleAlert: torrent added, name=${th.name}, infoHash=${th.infoHash()?.toString() ?: "unknown"}")
                        Log.d(TAG, "handleAlert: torrent flags=${th.flags}, savePath=${th.savePath()}")
                    } else {
                        Log.e(TAG, "handleAlert: AddTorrentAlert with invalid handle")
                    }
                }
                is MetadataReceivedAlert -> {
                    Log.d(TAG, "handleAlert: MetadataReceivedAlert")
                    val h = handle ?: run { Log.w(TAG, "handleAlert: no handle for metadata"); return }
                    if (!h.isValid()) { Log.w(TAG, "handleAlert: invalid handle for metadata"); return }
                    val ti = try { h.torrentFile() } catch (e: Exception) { Log.e(TAG, "handleAlert: failed to get torrentFile", e); null }
                    if (ti != null) {
                        Log.d(TAG, "handleAlert: metadata received, torrent name=${ti.name()}, total size=${ti.totalSize()}, num pieces=${ti.numPieces()}, piece length=${ti.pieceLength()}")
                        val fs = ti.files()
                        if (fs != null) {
                            Log.d(TAG, "handleAlert: ${fs.numFiles()} files in torrent:")
                            for (i in 0 until minOf(fs.numFiles(), 20)) {
                                Log.d(TAG, "handleAlert:   file[$i]: ${fs.fileName(i)} (${fs.fileSize(i)} bytes)")
                            }
                            if (fs.numFiles() > 20) Log.d(TAG, "handleAlert:   ... and ${fs.numFiles() - 20} more files")
                        }
                        val meta = buildMeta(ti)
                        Log.d(TAG, "handleAlert: notifying listeners of metadata")
                        listeners.forEach { it.onMetadataReceived(meta) }
                        if (!streamingPrioritiesSet && pendingStreamFileIndex >= 0) {
                            Log.d(TAG, "handleAlert: setting streaming priorities for pending file $pendingStreamFileIndex")
                            try { setupStreamingPriorities(h, pendingStreamFileIndex) } catch (e: Exception) { Log.e(TAG, "handleAlert: failed to set streaming priorities", e) }
                        } else {
                            Log.d(TAG, "handleAlert: streamingPrioritiesSet=$streamingPrioritiesSet, pendingStreamFileIndex=$pendingStreamFileIndex")
                        }
                    } else {
                        Log.w(TAG, "handleAlert: MetadataReceivedAlert but torrentFile() returned null")
                    }
                }
                is MetadataFailedAlert -> {
                    Log.e(TAG, "handleAlert: MetadataFailedAlert, error=${alert.message()}")
                    listeners.forEach { it.onError("Failed to download torrent metadata: ${alert.message()}") }
                }
                is TorrentFinishedAlert -> {
                    Log.d(TAG, "handleAlert: TorrentFinishedAlert, handle valid=${alert.handle()?.isValid()}")
                    listeners.forEach { it.onFinished() }
                }
                else -> {
                    Log.v(TAG, "handleAlert: unhandled alert type=${alert.type()?.name ?: alert.javaClass.simpleName}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "handleAlert: error handling alert", e)
        }
    }

    fun addListener(l: EngineListener) = listeners.add(l)
    fun removeListener(l: EngineListener) = listeners.remove(l)

    fun addTorrentFromMagnet(magnetUri: String) {
        val truncated = magnetUri.take(120)
        Log.d(TAG, "addTorrentFromMagnet: adding magnet: $truncated...")
        Log.d(TAG, "addTorrentFromMagnet: saveDir=${saveDir.absolutePath}, saveDir exists=${saveDir.exists()}")
        try {
            val startTime = System.currentTimeMillis()
            sessionManager.download(magnetUri, saveDir, torrent_flags_t())
            val elapsed = System.currentTimeMillis() - startTime
            Log.d(TAG, "addTorrentFromMagnet: sessionManager.download() returned in ${elapsed}ms")
            Log.d(TAG, "addTorrentFromMagnet: stats=${sessionManager.stats()}")
        } catch (e: Exception) {
            Log.e(TAG, "addTorrentFromMagnet: failed", e)
            listeners.forEach { it.onError("Failed to add magnet: ${e.message}") }
        }
    }

    fun startDownload(fileIndex: Int) {
        Log.d(TAG, "startDownload: fileIndex=$fileIndex")
        val h = handle ?: run { Log.e(TAG, "startDownload: handle is null"); return }

        applyFilePriorities(h, fileIndex)

        val ti = try { h.torrentFile() } catch (e: Exception) { Log.e(TAG, "startDownload: error getting torrentFile", e); null }
        if (ti != null) {
            Log.d(TAG, "startDownload: metadata available, setting up streaming priorities immediately")
            setupStreamingPriorities(h, fileIndex)
        } else {
            pendingStreamFileIndex = fileIndex
            Log.d(TAG, "startDownload: metadata not yet available, deferred (pendingStreamFileIndex=$fileIndex)")
        }
    }

    private fun setupStreamingPriorities(h: TorrentHandle, fileIndex: Int) {
        Log.d(TAG, "setupStreamingPriorities: fileIndex=$fileIndex")
        val ti = try { h.torrentFile() } catch (e: Exception) { Log.e(TAG, "setupStreamingPriorities: error getting torrentFile", e); null } ?: run {
            Log.w(TAG, "setupStreamingPriorities: no torrent info yet, deferring")
            pendingStreamFileIndex = fileIndex
            return
        }

        val totalPieces = ti.numPieces()
        if (totalPieces == 0) { Log.w(TAG, "setupStreamingPriorities: no pieces in torrent"); return }

        val fs = ti.files()
        if (fs == null) { Log.w(TAG, "setupStreamingPriorities: no file storage"); return }

        streamingFirstPiece = fs.pieceIndexAtFile(fileIndex)
        streamingLastPiece = fs.lastPieceIndexAtFile(fileIndex)

        val totalFilePieces = streamingLastPiece - streamingFirstPiece + 1
        streamingWindowStart = streamingFirstPiece
        streamingWindowEnd = minOf(streamingFirstPiece + STREAMING_WINDOW_SIZE, streamingLastPiece + 1)
        lastAdvancedPiece = streamingFirstPiece - 1

        Log.d(TAG, "setupStreamingPriorities: file=$fileIndex pieceRange=[${streamingFirstPiece}-${streamingLastPiece}] totalPieces=$totalFilePieces")
        Log.d(TAG, "setupStreamingPriorities: window=[${streamingWindowStart}-${streamingWindowEnd}) windowSize=$STREAMING_WINDOW_SIZE deadlineSize=$STREAMING_DEADLINE_SIZE")
        Log.d(TAG, "setupStreamingPriorities: torrent totalPieces=$totalPieces, pieceLength=${ti.pieceLength()}")

        for (i in streamingFirstPiece..streamingLastPiece) {
            val priority = if (i < streamingWindowEnd) Priority.TOP_PRIORITY else Priority.DEFAULT
            try { h.piecePriority(i, priority) } catch (e: Exception) { Log.w(TAG, "setupStreamingPriorities: failed to set priority for piece $i", e) }
        }

        val headDeadlineCount = minOf(STREAMING_DEADLINE_SIZE, totalFilePieces)
        for (i in 0 until headDeadlineCount) {
            val piece = streamingFirstPiece + i
            try { h.setPieceDeadline(piece, i + 1) } catch (e: Exception) { Log.w(TAG, "setupStreamingPriorities: failed to set deadline for piece $piece", e) }
        }

        if (totalFilePieces > headDeadlineCount + 2) {
            Log.d(TAG, "setupStreamingPriorities: setting late-piece deadlines for pieces ${maxOf(streamingFirstPiece, streamingLastPiece - 1)} to $streamingLastPiece")
            for (i in maxOf(streamingFirstPiece, streamingLastPiece - 1)..streamingLastPiece) {
                try { h.setPieceDeadline(i, headDeadlineCount + 50) } catch (e: Exception) { Log.w(TAG, "setupStreamingPriorities: failed to set late deadline for piece $i", e) }
            }
        }

        streamingPrioritiesSet = true
        pendingStreamFileIndex = -1
        Log.d(TAG, "setupStreamingPriorities: completed successfully")
    }

    fun advanceStreamingWindow() {
        if (!streamingPrioritiesSet) {
            Log.v(TAG, "advanceStreamingWindow: streamingPrioritiesSet=false, skipping")
            return
        }
        val h = handle ?: run { Log.v(TAG, "advanceStreamingWindow: no handle, skipping"); return }

        var lastConsecutive = streamingWindowStart - 1
        while (lastConsecutive < streamingLastPiece) {
            if (havePiece(lastConsecutive + 1)) {
                lastConsecutive++
            } else break
        }

        if (lastConsecutive > lastAdvancedPiece) {
            Log.d(TAG, "advanceStreamingWindow: advancing from piece $lastAdvancedPiece to $lastConsecutive")
            lastAdvancedPiece = lastConsecutive
            streamingWindowStart = lastConsecutive + 1
            val newWindowEnd = minOf(streamingWindowStart + STREAMING_WINDOW_SIZE, streamingLastPiece + 1)
            if (newWindowEnd > streamingWindowEnd) {
                val newPieces = newWindowEnd - streamingWindowEnd
                Log.d(TAG, "advanceStreamingWindow: expanding window to $streamingWindowStart-$newWindowEnd ($newPieces new pieces)")
                for (i in streamingWindowEnd until newWindowEnd) {
                    try { h.piecePriority(i, Priority.TOP_PRIORITY) } catch (e: Exception) { Log.w(TAG, "advanceStreamingWindow: failed to set priority for piece $i", e) }
                }
                streamingWindowEnd = newWindowEnd
            }
            val deadlineStart = streamingWindowStart
            val deadlineEnd = minOf(streamingWindowStart + STREAMING_DEADLINE_SIZE, streamingWindowEnd)
            Log.d(TAG, "advanceStreamingWindow: setting deadlines for pieces $deadlineStart to ${deadlineEnd - 1}")
            for (i in deadlineStart until deadlineEnd) {
                try { h.setPieceDeadline(i, (i - streamingWindowStart) + 1) } catch (e: Exception) { Log.w(TAG, "advanceStreamingWindow: failed to set deadline for piece $i", e) }
            }
        } else {
            Log.v(TAG, "advanceStreamingWindow: no advancement needed (lastConsecutive=$lastConsecutive, lastAdvancedPiece=$lastAdvancedPiece)")
        }
    }

    fun getFileSize(fileIndex: Int): Long {
        val h = handle ?: run { Log.w(TAG, "getFileSize: no handle"); return 0L }
        val size = try { h.torrentFile()?.files()?.fileSize(fileIndex) ?: 0L } catch (e: Exception) { Log.e(TAG, "getFileSize: error", e); 0L }
        Log.d(TAG, "getFileSize: fileIndex=$fileIndex -> ${size} bytes")
        return size
    }

    fun getContiguousDownloadedBytes(): Long {
        val h = handle ?: run { Log.v(TAG, "getContiguousDownloadedBytes: no handle"); return 0L }
        val ti = try { h.torrentFile() } catch (e: Exception) { Log.v(TAG, "getContiguousDownloadedBytes: no torrentFile", e); return 0L } ?: return 0L
        val fs = ti.files() ?: return 0L
        val fileIndex = findSelectedFileIndex(h, fs) ?: return 0L
        val fileSize = fs.fileSize(fileIndex)
        if (fileSize <= 0) return 0L
        val firstPiece = fs.pieceIndexAtFile(fileIndex)
        val lastPiece = fs.lastPieceIndexAtFile(fileIndex)
        val pieceLength = ti.pieceLength().toLong()
        var contiguousPieces = 0
        for (i in firstPiece..lastPiece) {
            if (havePiece(i)) contiguousPieces++ else break
        }
        if (contiguousPieces == 0) {
            Log.v(TAG, "getContiguousDownloadedBytes: 0 contiguous pieces from $firstPiece")
            return 0L
        }
        val totalFilePieces = lastPiece - firstPiece + 1
        if (contiguousPieces >= totalFilePieces) {
            Log.v(TAG, "getContiguousDownloadedBytes: complete file ($fileSize bytes)")
            return fileSize
        }
        val result = contiguousPieces.toLong() * pieceLength
        Log.v(TAG, "getContiguousDownloadedBytes: $contiguousPieces/$totalFilePieces pieces = ${result} bytes")
        return result
    }

    private fun findSelectedFileIndex(h: TorrentHandle, fs: FileStorage): Int? {
        for (i in 0 until fs.numFiles()) {
            try { if (h.filePriority(i) != Priority.IGNORE) return i } catch (_: Exception) {}
        }
        var maxSize = 0L; var maxIdx = 0
        for (i in 0 until fs.numFiles()) {
            val size = fs.fileSize(i)
            if (size > maxSize) { maxSize = size; maxIdx = i }
        }
        return if (maxSize > 0) maxIdx else null
    }

    fun getFileSavePath(fileIndex: Int): String? {
        val h = handle ?: run { Log.w(TAG, "getFileSavePath: no handle"); return null }
        return try {
            val ti = h.torrentFile() ?: run { Log.w(TAG, "getFileSavePath: no torrentFile"); return null }
            val path = ti.files()?.filePath(fileIndex, saveDir.absolutePath)
            Log.d(TAG, "getFileSavePath: fileIndex=$fileIndex -> $path")
            path
        } catch (e: Exception) { Log.e(TAG, "getFileSavePath: error", e); null }
    }

    fun getNumPieces(): Int = try { handle?.torrentFile()?.numPieces() ?: 1 } catch (_: Exception) { 1 }

    fun getPieceSize(): Long {
        val ti = try { handle?.torrentFile() } catch (_: Exception) { null } ?: return 4L * 1024 * 1024
        return (ti.totalSize() + ti.numPieces() - 1) / ti.numPieces()
    }

    fun havePiece(pieceIndex: Int): Boolean {
        return try {
            handle?.havePiece(pieceIndex) ?: false
        } catch (_: Exception) { false }
    }

    fun removeCurrentTorrent() {
        Log.d(TAG, "removeCurrentTorrent: handle=${handle != null}")
        handle?.let {
            try {
                sessionManager.remove(it)
                Log.d(TAG, "removeCurrentTorrent: removed from session")
            } catch (e: Exception) { Log.e(TAG, "removeCurrentTorrent: error removing", e) }
        }
        handle = null
        resetStreamingState()
        Log.d(TAG, "removeCurrentTorrent: done")
    }

    fun clearCache() {
        try { saveDir.listFiles()?.forEach { it.deleteRecursively() } } catch (_: Exception) {}
    }

    fun stop() {
        Log.d(TAG, "stop: shutting down torrent engine")
        isRunning.set(false)
        pollThread?.interrupt()
        handle?.let {
            try {
                sessionManager.remove(it)
                Log.d(TAG, "stop: removed torrent handle")
            } catch (e: Exception) { Log.e(TAG, "stop: error removing handle", e) }
        }
        sessionManager.removeListener(sessionListener)
        sessionManager.stop()
        Log.d(TAG, "stop: engine stopped")
    }

    private fun resetStreamingState() {
        streamingFirstPiece = 0; streamingLastPiece = 0
        streamingWindowStart = 0; streamingWindowEnd = 0
        lastAdvancedPiece = -1; streamingPrioritiesSet = false; pendingStreamFileIndex = -1
    }

    private fun applyFilePriorities(h: TorrentHandle, selectedIndex: Int) {
        Log.d(TAG, "applyFilePriorities: selectedIndex=$selectedIndex")
        try {
            val ti = h.torrentFile()
            if (ti != null) {
                val fs = ti.files()
                if (fs != null) {
                    val nf = fs.numFiles()
                    Log.d(TAG, "applyFilePriorities: $nf total files, setting selected=$selectedIndex to DEFAULT, others to IGNORE")
                    for (i in 0 until nf) {
                        val prio = if (i == selectedIndex) Priority.DEFAULT else Priority.IGNORE
                        h.filePriority(i, prio)
                    }
                    Log.d(TAG, "applyFilePriorities: done")
                    return
                } else {
                    Log.w(TAG, "applyFilePriorities: no file storage")
                }
            } else {
                Log.w(TAG, "applyFilePriorities: no torrentFile, falling back to single file priority")
            }
        } catch (e: Exception) { Log.e(TAG, "applyFilePriorities: error", e) }
        h.filePriority(selectedIndex, Priority.DEFAULT)
    }

    fun getLargestVideoFileIndex(): Int {
        return try {
            val ti = handle?.torrentFile() ?: run { Log.w(TAG, "getLargestVideoFileIndex: no torrentFile"); return 0 }
            val fs = ti.files() ?: run { Log.w(TAG, "getLargestVideoFileIndex: no files"); return 0 }
            var bestIdx = 0; var bestSize = 0L
            val videoExts = setOf("mkv", "mp4", "webm", "avi", "mov", "m4v")
            for (i in 0 until fs.numFiles()) {
                val name = fs.fileName(i)
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext in videoExts) {
                    val size = fs.fileSize(i)
                    Log.v(TAG, "getLargestVideoFileIndex: file[$i] name=$name ext=$ext size=$size")
                    if (size > bestSize) { bestSize = size; bestIdx = i }
                }
            }
            Log.d(TAG, "getLargestVideoFileIndex: selected file[$bestIdx] with size=$bestSize")
            bestIdx
        } catch (e: Exception) { Log.e(TAG, "getLargestVideoFileIndex: error", e); 0 }
    }

    fun findVideoFileIndex(fileNameHint: String): Int {
        Log.d(TAG, "findVideoFileIndex: hint=$fileNameHint")
        return try {
            val ti = handle?.torrentFile() ?: run { Log.w(TAG, "findVideoFileIndex: no torrentFile"); return 0 }
            val fs = ti.files() ?: run { Log.w(TAG, "findVideoFileIndex: no files"); return 0 }
            for (i in 0 until fs.numFiles()) {
                val name = fs.fileName(i)
                Log.v(TAG, "findVideoFileIndex: checking file[$i] name=$name")
                if (name.contains(fileNameHint, ignoreCase = true)) {
                    Log.d(TAG, "findVideoFileIndex: found matching file[$i]: $name")
                    return i
                }
            }
            Log.d(TAG, "findVideoFileIndex: no match, falling back to largest video file")
            getLargestVideoFileIndex()
        } catch (e: Exception) { Log.e(TAG, "findVideoFileIndex: error", e); 0 }
    }

    private fun buildMeta(ti: TorrentInfo): TorrentMeta {
        val fs = ti.files()
        val entries = if (fs != null) {
            (0 until fs.numFiles()).map { i ->
                TorrentFileEntry(i, fs.fileName(i), fs.fileSize(i), fs.filePath(i))
            }
        } else emptyList()
        return TorrentMeta(ti.name(), entries)
    }

    companion object {
        private const val TAG = "TorrentEngine"
        private const val STREAMING_WINDOW_SIZE = 30
        private const val STREAMING_DEADLINE_SIZE = 15
    }
}