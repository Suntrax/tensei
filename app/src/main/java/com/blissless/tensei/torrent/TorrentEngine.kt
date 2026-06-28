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
            Log.d(TAG, "Already running")
            return
        }
        Log.d(TAG, "Starting torrent engine")
        try {
            sessionManager.addListener(sessionListener)
            sessionManager.start()
            startPollLoop()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start session", e)
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
                listeners.forEach { it.onProgress(st.totalWantedDone(), st.totalWanted()) }
                if (st.isFinished() || st.isSeeding()) {
                    listeners.forEach { it.onFinished() }
                }
                if (now % 2000 < 500) {
                    advanceStreamingWindow()
                }
            }
        } catch (_: Exception) {}
    }

    private fun handleAlert(alert: Alert<*>) {
        try {
            when (alert) {
                is AddTorrentAlert -> {
                    val th = alert.handle()
                    if (th.isValid()) {
                        handle = th
                        Log.d(TAG, "Torrent added")
                    }
                }
                is MetadataReceivedAlert -> {
                    Log.d(TAG, "Metadata received")
                    val h = handle ?: return
                    if (!h.isValid()) return
                    val ti = try { h.torrentFile() } catch (_: Exception) { null }
                    if (ti != null) {
                        val meta = buildMeta(ti)
                        listeners.forEach { it.onMetadataReceived(meta) }
                        if (!streamingPrioritiesSet && pendingStreamFileIndex >= 0) {
                            Log.d(TAG, "Setting up streaming priorities for pending file $pendingStreamFileIndex")
                            try { setupStreamingPriorities(h, pendingStreamFileIndex) } catch (_: Exception) {}
                        }
                    }
                }
                is MetadataFailedAlert -> {
                    Log.e(TAG, "Metadata download failed")
                    listeners.forEach { it.onError("Failed to download torrent metadata") }
                }
                is TorrentFinishedAlert -> {
                    Log.d(TAG, "Torrent finished")
                    listeners.forEach { it.onFinished() }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling alert", e)
        }
    }

    fun addListener(l: EngineListener) = listeners.add(l)
    fun removeListener(l: EngineListener) = listeners.remove(l)

    fun addTorrentFromMagnet(magnetUri: String) {
        Log.d(TAG, "Adding magnet: ${magnetUri.take(80)}...")
        try {
            sessionManager.download(magnetUri, saveDir, torrent_flags_t())
            Log.d(TAG, "Magnet download initiated")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add magnet", e)
            listeners.forEach { it.onError("Failed to add magnet: ${e.message}") }
        }
    }

    fun startDownload(fileIndex: Int) {
        val h = handle ?: run { Log.e(TAG, "handle is null in startDownload"); return }

        applyFilePriorities(h, fileIndex)

        val ti = try { h.torrentFile() } catch (_: Exception) { null }
        if (ti != null) {
            setupStreamingPriorities(h, fileIndex)
        } else {
            pendingStreamFileIndex = fileIndex
            Log.d(TAG, "Metadata not yet available, will set streaming priorities later")
        }
    }

    private fun setupStreamingPriorities(h: TorrentHandle, fileIndex: Int) {
        val ti = try { h.torrentFile() } catch (_: Exception) { null } ?: run {
            Log.w(TAG, "No torrent info yet, deferring streaming priorities")
            pendingStreamFileIndex = fileIndex
            return
        }

        val totalPieces = ti.numPieces()
        if (totalPieces == 0) { Log.w(TAG, "No pieces in torrent"); return }

        val fs = ti.files()
        if (fs == null) { Log.w(TAG, "No file storage"); return }

        streamingFirstPiece = fs.pieceIndexAtFile(fileIndex)
        streamingLastPiece = fs.lastPieceIndexAtFile(fileIndex)

        val totalFilePieces = streamingLastPiece - streamingFirstPiece + 1
        streamingWindowStart = streamingFirstPiece
        streamingWindowEnd = minOf(streamingFirstPiece + STREAMING_WINDOW_SIZE, streamingLastPiece + 1)
        lastAdvancedPiece = streamingFirstPiece - 1

        Log.d(TAG, "File $fileIndex piece range: ${streamingFirstPiece}-${streamingLastPiece} ($totalFilePieces pieces)")

        for (i in streamingFirstPiece..streamingLastPiece) {
            val priority = if (i < streamingWindowEnd) Priority.TOP_PRIORITY else Priority.DEFAULT
            try { h.piecePriority(i, priority) } catch (_: Exception) {}
        }

        val headDeadlineCount = minOf(STREAMING_DEADLINE_SIZE, totalFilePieces)
        for (i in 0 until headDeadlineCount) {
            val piece = streamingFirstPiece + i
            try { h.setPieceDeadline(piece, i + 1) } catch (_: Exception) {}
        }

        if (totalFilePieces > headDeadlineCount + 2) {
            for (i in maxOf(streamingFirstPiece, streamingLastPiece - 1)..streamingLastPiece) {
                try { h.setPieceDeadline(i, headDeadlineCount + 50) } catch (_: Exception) {}
            }
        }

        streamingPrioritiesSet = true
        pendingStreamFileIndex = -1
        Log.d(TAG, "Streaming priorities set")
    }

    fun advanceStreamingWindow() {
        if (!streamingPrioritiesSet) return
        val h = handle ?: return

        var lastConsecutive = streamingWindowStart - 1
        while (lastConsecutive < streamingLastPiece) {
            if (havePiece(lastConsecutive + 1)) {
                lastConsecutive++
            } else break
        }

        if (lastConsecutive > lastAdvancedPiece) {
            lastAdvancedPiece = lastConsecutive
            streamingWindowStart = lastConsecutive + 1
            val newWindowEnd = minOf(streamingWindowStart + STREAMING_WINDOW_SIZE, streamingLastPiece + 1)
            if (newWindowEnd > streamingWindowEnd) {
                for (i in streamingWindowEnd until newWindowEnd) {
                    try { h.piecePriority(i, Priority.TOP_PRIORITY) } catch (_: Exception) {}
                }
                streamingWindowEnd = newWindowEnd
            }
            val deadlineStart = streamingWindowStart
            val deadlineEnd = minOf(streamingWindowStart + STREAMING_DEADLINE_SIZE, streamingWindowEnd)
            for (i in deadlineStart until deadlineEnd) {
                try { h.setPieceDeadline(i, (i - streamingWindowStart) + 1) } catch (_: Exception) {}
            }
        }
    }

    fun getFileSize(fileIndex: Int): Long {
        val h = handle ?: return 0L
        return try { h.torrentFile()?.files()?.fileSize(fileIndex) ?: 0L } catch (_: Exception) { 0L }
    }

    fun getContiguousDownloadedBytes(): Long {
        val h = handle ?: return 0L
        val ti = try { h.torrentFile() } catch (_: Exception) { null } ?: return 0L
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
        if (contiguousPieces == 0) return 0L
        val totalFilePieces = lastPiece - firstPiece + 1
        if (contiguousPieces >= totalFilePieces) return fileSize
        return contiguousPieces.toLong() * pieceLength
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
        val h = handle ?: return null
        return try {
            val ti = h.torrentFile() ?: return null
            ti.files()?.filePath(fileIndex, saveDir.absolutePath)
        } catch (_: Exception) { null }
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
        handle?.let {
            try { sessionManager.remove(it) } catch (_: Exception) {}
        }
        handle = null
        resetStreamingState()
    }

    fun clearCache() {
        try { saveDir.listFiles()?.forEach { it.deleteRecursively() } } catch (_: Exception) {}
    }

    fun stop() {
        isRunning.set(false)
        pollThread?.interrupt()
        handle?.let {
            try { sessionManager.remove(it) } catch (_: Exception) {}
        }
        sessionManager.removeListener(sessionListener)
        sessionManager.stop()
    }

    private fun resetStreamingState() {
        streamingFirstPiece = 0; streamingLastPiece = 0
        streamingWindowStart = 0; streamingWindowEnd = 0
        lastAdvancedPiece = -1; streamingPrioritiesSet = false; pendingStreamFileIndex = -1
    }

    private fun applyFilePriorities(h: TorrentHandle, selectedIndex: Int) {
        try {
            val ti = h.torrentFile()
            if (ti != null) {
                val fs = ti.files()
                if (fs != null) {
                    val nf = fs.numFiles()
                    for (i in 0 until nf) {
                        h.filePriority(i, if (i == selectedIndex) Priority.DEFAULT else Priority.IGNORE)
                    }
                    return
                }
            }
        } catch (_: Exception) {}
        h.filePriority(selectedIndex, Priority.DEFAULT)
    }

    fun getLargestVideoFileIndex(): Int {
        return try {
            val ti = handle?.torrentFile() ?: return 0
            val fs = ti.files() ?: return 0
            var bestIdx = 0; var bestSize = 0L
            val videoExts = setOf("mkv", "mp4", "webm", "avi", "mov", "m4v")
            for (i in 0 until fs.numFiles()) {
                val name = fs.fileName(i)
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext in videoExts) {
                    val size = fs.fileSize(i)
                    if (size > bestSize) { bestSize = size; bestIdx = i }
                }
            }
            bestIdx
        } catch (_: Exception) { 0 }
    }

    fun findVideoFileIndex(fileNameHint: String): Int {
        return try {
            val ti = handle?.torrentFile() ?: return 0
            val fs = ti.files() ?: return 0
            for (i in 0 until fs.numFiles()) {
                val name = fs.fileName(i)
                if (name.contains(fileNameHint, ignoreCase = true)) return i
            }
            getLargestVideoFileIndex()
        } catch (_: Exception) { 0 }
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
