package com.blissless.tensei.torrent

import android.content.Context
import android.util.Log
import org.libtorrent4j.AddTorrentParams
import org.libtorrent4j.AnnounceEntry
import org.libtorrent4j.Priority
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import org.libtorrent4j.swig.error_code
import org.libtorrent4j.swig.settings_pack
import java.io.File
import java.util.BitSet
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

class TorrentEngine(private val context: Context) {

    interface EngineListener {
        fun onMetadataReceived(meta: TorrentMeta)
        fun onProgress(downloaded: Long, total: Long)
        fun onFinished()
        fun onError(message: String)
    }

    private val sessionManager = SessionManager()
    private var rawHandle: org.libtorrent4j.swig.torrent_handle? = null
    private var handle: TorrentHandle? = null
    private val listeners = CopyOnWriteArrayList<EngineListener>()
    private var pollThread: Thread? = null
    private val completedPieces = BitSet()
    val isRunning = AtomicBoolean(false)

    private var streamingFirstPiece = 0
    private var streamingLastPiece = 0
    private var streamingWindowStart = 0
    private var streamingWindowEnd = 0
    private var lastAdvancedPiece = -1
    private var streamingPrioritiesSet = false
    private var pendingStreamFileIndex = -1
    private var selectedFileIndex = -1
    private var selectedFileSize = 0L
    private var finishedNotified = false
    private var pendingTi: TorrentInfo? = null

    val saveDir: File
        get() = File(context.cacheDir, "torrent_stream").also { it.mkdirs() }

    fun start() {
        if (isRunning.getAndSet(true)) {
            Log.d(TAG, "start: already running")
            return
        }
        Log.i(TAG, "start: initializing torrent engine (libtorrent4j)")
        val sp = SettingsPack()
        sp.setEnableDht(true)
        sp.setEnableLsd(true)
        sp.setBoolean(settings_pack.bool_types.enable_upnp.swigValue(), true)
        sp.setBoolean(settings_pack.bool_types.enable_natpmp.swigValue(), true)
        sessionManager.start(SessionParams(sp))
        sessionManager.startDht()
        Log.i(TAG, "start: engine ready")
    }

    fun addTorrentFromFile(file: File): TorrentMeta? {
        return try {
            val ti = TorrentInfo(file)
            pendingTi = ti
            buildMeta(ti)
        } catch (e: Exception) {
            Log.e(TAG, "addTorrentFromFile: failed", e)
            listeners.forEach { it.onError("Failed to parse torrent: ${e.message}") }
            null
        }
    }

    fun addTorrentFromMagnet(magnetUri: String) {
        Log.i(TAG, "addTorrentFromMagnet: ${magnetUri.take(60)}...")
        if (!isRunning.get()) {
            Log.w(TAG, "addTorrentFromMagnet: engine not running, starting now")
            start()
        }
        try {
            removeCurrentTorrent()
            val enhancedUri = enhanceMagnetWithTrackers(magnetUri)
            val atp = AddTorrentParams.parseMagnetUri(enhancedUri)
            atp.setSavePath(saveDir.absolutePath)
            val ec = error_code()
            val raw = sessionManager.swig().add_torrent(atp.swig(), ec)
            if (ec.failed()) {
                val msg = "Magnet add failed: ${ec.message()}"
                Log.e(TAG, "addTorrentFromMagnet: $msg")
                listeners.forEach { it.onError(msg) }
                return
            }
            rawHandle = raw
            handle = TorrentHandle(raw)
            Log.i(TAG, "addTorrentFromMagnet: handle valid=${raw.is_valid()}")
            raw?.set_sequential_range(0, Int.MAX_VALUE)
            handle?.resume()
            for (tracker in BACKUP_TRACKERS) {
                try { handle?.addTracker(AnnounceEntry(tracker)) }
                catch (e: Exception) { Log.w(TAG, "addTorrentFromMagnet: addTracker failed: ${e.message}") }
            }
            startPolling()
        } catch (e: Exception) {
            Log.e(TAG, "addTorrentFromMagnet: FAILED", e)
            listeners.forEach { it.onError("Failed to add magnet: ${e.message}") }
        }
    }

    fun startDownload(fileIndex: Int) {
        Log.i(TAG, "startDownload: fileIndex=$fileIndex handle=${handle != null} pendingTi=${pendingTi != null}")
        finishedNotified = false

        if (handle == null) {
            val saved = pendingTi
            if (saved != null) {
                Log.i(TAG, "startDownload: .torrent path — numFiles=${saved.numFiles()} name='${saved.name()}'")
                val priorities = Array(saved.numFiles()) { Priority.IGNORE }
                priorities[fileIndex] = Priority.DEFAULT
                Log.i(TAG, "startDownload: setting file priorities: [$fileIndex]=DEFAULT, all others IGNORE (${saved.numFiles()} files)")
                val atp = AddTorrentParams()
                atp.setSavePath(saveDir.absolutePath)
                atp.setTorrentInfo(saved)
                atp.filePriorities(priorities)
                val ec = error_code()
                val raw = sessionManager.swig().add_torrent(atp.swig(), ec)
                if (ec.failed()) {
                    val msg = "Torrent add failed: ${ec.message()}"
                    Log.e(TAG, "startDownload: $msg")
                    listeners.forEach { it.onError(msg) }
                    return
                }
                rawHandle = raw
                handle = TorrentHandle(raw)
                pendingTi = null
                Log.i(TAG, "startDownload: torrent added, handle valid=${raw.is_valid()}")
            } else {
                Log.e(TAG, "startDownload: no handle AND no pendingTi — cannot start")
            }
        } else {
            Log.d(TAG, "startDownload: using existing handle from magnet, applying file priorities")
        }

        val h = handle ?: run {
            Log.e(TAG, "startDownload: handle is null after setup")
            return
        }

        applyFilePriorities(h, fileIndex)

        val ti = h.torrentFile()
        if (ti != null) {
            val allFiles = (0 until ti.numFiles()).map { i ->
                "[${i}] ${ti.files().fileName(i)} (${ti.files().fileSize(i)} bytes)"
            }
            Log.i(TAG, "startDownload: metadata available — ${ti.numPieces()} pieces, ${ti.numFiles()} files:")
            allFiles.forEach { Log.i(TAG, "  $it") }
            setupStreamingPriorities(h, fileIndex)
        } else {
            pendingStreamFileIndex = fileIndex
            Log.d(TAG, "startDownload: metadata not yet available, deferred (will setup on metadata)")
        }

        startPolling()
    }

    private fun setupStreamingPriorities(h: TorrentHandle, fileIndex: Int) {
        val ti = h.torrentFile() ?: run {
            Log.w(TAG, "setupStreamingPriorities: no torrent info, deferring")
            pendingStreamFileIndex = fileIndex
            return
        }

        val totalPieces = ti.numPieces()
        if (totalPieces == 0) {
            Log.w(TAG, "setupStreamingPriorities: no pieces")
            return
        }

        val pieceRange = getFilePieceRange(ti, fileIndex)
        if (pieceRange != null) {
            streamingFirstPiece = pieceRange.first
            streamingLastPiece = pieceRange.second
        } else {
            Log.w(TAG, "setupStreamingPriorities: could not determine piece range, using full")
            streamingFirstPiece = 0
            streamingLastPiece = totalPieces - 1
        }

        val totalFilePieces = streamingLastPiece - streamingFirstPiece + 1
        streamingWindowStart = streamingFirstPiece
        streamingWindowEnd = minOf(streamingFirstPiece + STREAMING_WINDOW_SIZE, streamingLastPiece + 1)
        lastAdvancedPiece = streamingFirstPiece - 1

        Log.i(TAG, "setupStreamingPriorities: file=$fileIndex pieceRange=$streamingFirstPiece-$streamingLastPiece ($totalFilePieces pieces)")

        rawHandle?.set_sequential_range(streamingFirstPiece, streamingLastPiece)

        for (i in streamingFirstPiece..streamingLastPiece) {
            val priority = if (i < streamingWindowEnd) Priority.TOP_PRIORITY else Priority.DEFAULT
            try { h.piecePriority(i, priority) }
            catch (e: Exception) { Log.w(TAG, "setupStreamingPriorities: piecePriority($i) failed: ${e.message}") }
        }

        val headDeadlineCount = minOf(STREAMING_DEADLINE_SIZE, totalFilePieces)
        for (i in 0 until headDeadlineCount) {
            val piece = streamingFirstPiece + i
            try { h.setPieceDeadline(piece, i + 1) }
            catch (e: Exception) { Log.w(TAG, "setupStreamingPriorities: deadline($piece) failed: ${e.message}") }
        }

        if (totalFilePieces > headDeadlineCount + 2) {
            for (i in maxOf(streamingFirstPiece, streamingLastPiece - 1)..streamingLastPiece) {
                try { h.setPieceDeadline(i, headDeadlineCount + 50) }
                catch (e: Exception) { Log.w(TAG, "setupStreamingPriorities: tail deadline($i) failed: ${e.message}") }
            }
        }

        streamingPrioritiesSet = true
        pendingStreamFileIndex = -1
        selectedFileIndex = fileIndex
        selectedFileSize = ti.files().fileSize(fileIndex)
        Log.i(TAG, "setupStreamingPriorities: complete, selectedFile=$fileIndex size=$selectedFileSize")
    }

    /**
     * Prioritizes pieces around a seek position (in ms) for smooth playback resume.
     * Calculates which file-relative piece contains the seek byte offset,
     * then sets high priority + tight deadlines on that piece and nearby pieces.
     */
    fun prioritizeForSeek(positionMs: Long, fileSize: Long, durationMs: Long) {
        val h = handle ?: run { Log.w(TAG, "prioritizeForSeek: no handle"); return }
        val ti = h.torrentFile() ?: run { Log.w(TAG, "prioritizeForSeek: no torrentFile"); return }
        val fileIndex = findSelectedFileIndex(h, ti) ?: run { Log.w(TAG, "prioritizeForSeek: no selected file"); return }
        val seekByteOffset = if (durationMs > 0) (positionMs * fileSize) / durationMs else 0L
        val pieceLength = ti.pieceLength().toLong()
        val filePieceRange = getFilePieceRange(ti, fileIndex) ?: run { Log.w(TAG, "prioritizeForSeek: no piece range"); return }
        val fileOffset = run {
            var off = 0L
            for (i in 0 until fileIndex) { off += ti.files().fileSize(i) }
            off
        }
        val globalPieceOfSeek = ((fileOffset + seekByteOffset) / pieceLength).toInt()
            .coerceIn(filePieceRange.first, filePieceRange.second)

        val seekWindowSize = 20
        val seekDeadlineSize = 10

        Log.i(TAG, "prioritizeForSeek: posMs=$positionMs durMs=$durationMs fileSize=$fileSize seekByte=$seekByteOffset piece=$globalPieceOfSeek range=$filePieceRange")

        for (i in globalPieceOfSeek until minOf(globalPieceOfSeek + seekWindowSize, filePieceRange.second + 1)) {
            try { h.piecePriority(i, Priority.TOP_PRIORITY) }
            catch (_: Exception) {}
        }
        for (i in maxOf(filePieceRange.first, globalPieceOfSeek - 3) until minOf(globalPieceOfSeek + seekDeadlineSize, filePieceRange.second + 1)) {
            val deadline = (i - globalPieceOfSeek + 1).coerceAtLeast(1)
            try { h.setPieceDeadline(i, deadline) }
            catch (_: Exception) {}
        }

        streamingWindowStart = globalPieceOfSeek
        streamingWindowEnd = minOf(globalPieceOfSeek + seekWindowSize, filePieceRange.second + 1)
        lastAdvancedPiece = globalPieceOfSeek - 1

        Log.d(TAG, "prioritizeForSeek: posMs=$positionMs globalPiece=$globalPieceOfSeek range=$filePieceRange")
    }

    fun advanceStreamingWindow() {
        if (!streamingPrioritiesSet) return
        val h = handle ?: return

        var lastConsecutive = streamingWindowStart - 1
        while (lastConsecutive < streamingLastPiece) {
            val nextPiece = lastConsecutive + 1
            if (havePiece(nextPiece)) { lastConsecutive = nextPiece } else { break }
        }

        if (lastConsecutive > lastAdvancedPiece) {
            lastAdvancedPiece = lastConsecutive
            streamingWindowStart = lastConsecutive + 1

            val newWindowEnd = minOf(streamingWindowStart + STREAMING_WINDOW_SIZE, streamingLastPiece + 1)
            if (newWindowEnd > streamingWindowEnd) {
                for (i in streamingWindowEnd until newWindowEnd) {
                    try { h.piecePriority(i, Priority.TOP_PRIORITY) }
                    catch (e: Exception) { Log.w(TAG, "advanceWindow: priority($i) failed: ${e.message}") }
                }
                streamingWindowEnd = newWindowEnd
            }

            val deadlineStart = streamingWindowStart
            val deadlineEnd = minOf(streamingWindowStart + STREAMING_DEADLINE_SIZE, streamingWindowEnd)
            for (i in deadlineStart until deadlineEnd) {
                try { h.setPieceDeadline(i, (i - streamingWindowStart) + 1) }
                catch (e: Exception) { Log.w(TAG, "advanceWindow: deadline($i) failed: ${e.message}") }
            }
        }
    }

    fun getFileSize(fileIndex: Int): Long {
        val h = handle ?: return 0L
        return try {
            h.torrentFile()?.files()?.fileSize(fileIndex) ?: 0L
        } catch (e: Exception) { 0L }
    }

    fun getContiguousDownloadedBytes(): Long {
        val h = handle ?: return 0L
        val ti = h.torrentFile() ?: return 0L
        val fileIndex = findSelectedFileIndex(h, ti) ?: return 0L
        val fileSize = ti.files().fileSize(fileIndex)
        if (fileSize <= 0) return 0L
        val range = getFilePieceRange(ti, fileIndex) ?: return 0L
        val pieceLength = ti.pieceLength().toLong()

        var contiguousPieces = 0
        for (i in range.first..range.second) {
            if (havePiece(i)) { contiguousPieces++ } else { break }
        }
        if (contiguousPieces == 0) return 0L
        val totalFilePieces = range.second - range.first + 1
        if (contiguousPieces >= totalFilePieces) return fileSize
        return contiguousPieces.toLong() * pieceLength
    }

    private fun findSelectedFileIndex(h: TorrentHandle, ti: TorrentInfo): Int? {
        val fs = ti.files()
        for (i in 0 until fs.numFiles()) {
            try {
                val p = h.filePriority(i)
                if (p != Priority.IGNORE) return i
            } catch (_: Exception) {}
        }
        var maxSize = 0L; var maxIdx = 0
        for (i in 0 until fs.numFiles()) {
            val size = fs.fileSize(i)
            if (size > maxSize) { maxSize = size; maxIdx = i }
        }
        return if (maxSize > 0) maxIdx else null
    }

    fun getLastPieceForFile(): Int {
        val h = handle ?: return -1
        val ti = h.torrentFile() ?: return -1
        val fileIndex = findSelectedFileIndex(h, ti) ?: return -1
        val file = ti.files().fileSize(fileIndex)
        if (file <= 0) return -1
        val range = getFilePieceRange(ti, fileIndex) ?: return -1
        return range.second
    }

    fun getFileSavePath(fileIndex: Int): String? {
        val h = handle ?: return null
        return try {
            val ti = h.torrentFile() ?: return null
            ti.files().filePath(fileIndex, saveDir.absolutePath)
        } catch (_: Exception) { null }
    }

    fun getFileFirstPiece(fileIndex: Int): Int {
        val h = handle ?: return 0
        val ti = h.torrentFile() ?: return 0
        return getFilePieceRange(ti, fileIndex)?.first ?: 0
    }

    fun getNumPieces(): Int = try { handle?.torrentFile()?.numPieces() ?: 1 } catch (_: Exception) { 1 }

    fun getPieceSize(): Long {
        val ti = try { handle?.torrentFile() } catch (_: Exception) { null } ?: return 4L * 1024 * 1024
        val total = ti.totalSize(); val np = ti.numPieces()
        return (total + np - 1) / np
    }

    fun havePiece(pieceIndex: Int): Boolean = try {
        rawHandle?.have_piece(pieceIndex) ?: false
    } catch (_: Exception) { false }

    fun getLargestVideoFileIndex(): Int {
        return try {
            val h = handle ?: return 0
            val ti = h.torrentFile() ?: return 0
            var bestIdx = 0; var bestSize = 0L
            val videoExts = setOf("mkv", "mp4", "webm", "avi", "mov", "m4v")
            for (i in 0 until ti.numFiles()) {
                val name = ti.files().fileName(i)
                val ext = name.substringAfterLast('.').lowercase()
                if (ext in videoExts) {
                    val size = ti.files().fileSize(i)
                    if (size > bestSize) { bestSize = size; bestIdx = i }
                }
            }
            bestIdx
        } catch (e: Exception) { 0 }
    }

    fun findVideoFileIndex(fileNameHint: String): Int {
        return try {
            val h = handle ?: return 0
            val ti = h.torrentFile() ?: return 0
            for (i in 0 until ti.numFiles()) {
                val name = ti.files().fileName(i)
                if (name.contains(fileNameHint, ignoreCase = true)) return i
            }
            getLargestVideoFileIndex()
        } catch (e: Exception) { 0 }
    }

    fun addListener(l: EngineListener) = listeners.add(l)
    fun removeListener(l: EngineListener) = listeners.remove(l)

    fun removeCurrentTorrent() {
        Log.d(TAG, "removeCurrentTorrent: cleaning up")
        pollThread?.interrupt(); pollThread = null
        rawHandle?.let {
            try { sessionManager.swig().remove_torrent(it) } catch (_: Exception) {}
        }
        handle = null; rawHandle = null; pendingTi = null
        resetStreamingState()
    }

    fun clearCache() {
        Log.d(TAG, "clearCache: deleting ${saveDir.absolutePath}")
        try { saveDir.listFiles()?.forEach { it.deleteRecursively() } } catch (_: Exception) {}
    }

    fun stop() {
        Log.i(TAG, "stop: shutting down")
        isRunning.set(false)
        pollThread?.interrupt()
        removeCurrentTorrent()
        sessionManager.stop()
    }

    private fun resetStreamingState() {
        streamingFirstPiece = 0; streamingLastPiece = 0
        streamingWindowStart = 0; streamingWindowEnd = 0
        lastAdvancedPiece = -1; streamingPrioritiesSet = false; pendingStreamFileIndex = -1
        selectedFileIndex = -1; selectedFileSize = 0L
        finishedNotified = false
        completedPieces.clear()
    }

    private fun applyFilePriorities(h: TorrentHandle, selectedIndex: Int) {
        Log.d(TAG, "applyFilePriorities: selectedIndex=$selectedIndex")
        try {
            val ti = h.torrentFile()
            if (ti != null) {
                val nf = ti.numFiles()
                for (i in 0 until nf) {
                    val newP = if (i == selectedIndex) Priority.DEFAULT else Priority.IGNORE
                    val oldP = try { h.filePriority(i).toString() } catch (_: Exception) { "?" }
                    h.filePriority(i, newP)
                    Log.d(TAG, "applyFilePriorities: file[$i] '${ti.files().fileName(i)}' $oldP -> $newP")
                }
                return
            }
        } catch (e: Exception) { Log.e(TAG, "applyFilePriorities: error", e) }
        h.filePriority(selectedIndex, Priority.DEFAULT)
        Log.w(TAG, "applyFilePriorities: fallback — only set file[$selectedIndex]")
    }

    private fun getFilePieceRange(ti: TorrentInfo, fileIndex: Int): Pair<Int, Int>? {
        val fs = ti.files()
        val fileSize = fs.fileSize(fileIndex)
        if (fileSize <= 0) return null
        val pieceLength = ti.pieceLength()
        var offset = 0L
        for (i in 0 until fileIndex) { offset += fs.fileSize(i) }
        val firstPiece = (offset / pieceLength).toInt()
        val lastPiece = ((offset + fileSize - 1) / pieceLength).toInt()
        return Pair(firstPiece, lastPiece)
    }

    private fun startPolling() {
        if (pollThread?.isAlive == true) {
            Log.d(TAG, "startPolling: already running")
            return
        }
        pollThread?.interrupt()
        Log.i(TAG, "startPolling: starting poll thread")
        pollThread = Thread {
            var metaNotified = false
            var lastAdvanceTime = 0L
            var lastStatusLogTime = 0L
            while (isRunning.get()) {
                try {
                    Thread.sleep(500)
                    val h = handle ?: continue
                    val st = h.status()
                    if (!metaNotified && st.hasMetadata()) {
                        metaNotified = true
                        val ti = h.torrentFile()
                        if (ti != null) {
                            Log.i(TAG, "poll: metadata received — '${ti.name()}' ${ti.numPieces()} pieces, ${ti.numFiles()} files")
                            val meta = buildMeta(ti)
                            listeners.forEach { it.onMetadataReceived(meta) }
                            if (!streamingPrioritiesSet && pendingStreamFileIndex >= 0) {
                                setupStreamingPriorities(h, pendingStreamFileIndex)
                            }
                        }
                    }
                    if (metaNotified) {
                        val downloaded = st.totalWantedDone()
                        val total = st.totalWanted()
                        val seeds = st.numSeeds()
                        val peers = st.numPeers()
                        val downloadRate = st.downloadRate()
                        listeners.forEach { it.onProgress(downloaded, total) }
                        if ((st.isFinished || st.isSeeding) && !finishedNotified) {
                            finishedNotified = true
                            Log.i(TAG, "poll: torrent FINISHED — seeding")
                            listeners.forEach { it.onFinished() }
                        }
                        val now = System.currentTimeMillis()
                        if (now - lastStatusLogTime > 10000) {
                            val pct = if (total > 0) downloaded * 100 / total else 0
                            Log.i(TAG, "poll: ${pct}% ($downloaded/$total) seeds=$seeds peers=$peers rate=${downloadRate / 1024}KB/s window=$streamingWindowStart-$streamingWindowEnd")
                            lastStatusLogTime = now
                        }
                        if (now - lastAdvanceTime > 2000) {
                            advanceStreamingWindow()
                            lastAdvanceTime = now
                        }
                    }
                } catch (e: InterruptedException) { Log.d(TAG, "poll: interrupted, exiting"); break }
                catch (e: Exception) { Log.e(TAG, "poll error", e) }
            }
            Log.d(TAG, "poll: thread exiting")
        }.apply { isDaemon = true; name = "torrent-poll"; start() }
    }

    private fun buildMeta(ti: TorrentInfo): TorrentMeta {
        val fs = ti.files()
        val entries = (0 until fs.numFiles()).map { i ->
            TorrentFileEntry(i, fs.fileName(i), fs.fileSize(i), fs.filePath(i))
        }
        return TorrentMeta(ti.name(), entries)
    }

    private fun enhanceMagnetWithTrackers(magnetUri: String): String {
        var enhanced = magnetUri
        for (tracker in BACKUP_TRACKERS) {
            val encoded = java.net.URLEncoder.encode(tracker, "UTF-8")
            if (enhanced.contains("tr=$encoded") || enhanced.contains("tr=$tracker")) continue
            enhanced += "&tr=$encoded"
        }
        return enhanced
    }

    companion object {
        private const val TAG = "TorrentEngine"
        private const val STREAMING_WINDOW_SIZE = 30
        private const val STREAMING_DEADLINE_SIZE = 15

        private val BACKUP_TRACKERS = listOf(
            "udp://open.demonii.com:1337/announce",
            "udp://tracker.opentrackr.org:1337/announce",
            "udp://exodus.desync.com:6969/announce",
            "udp://tracker.openbittorrent.com:6969/announce",
            "udp://tracker.cyberia.is:6969/announce",
            "udp://tracker.moeking.me:6969/announce",
            "udp://opentracker.i2p.rocks:6969/announce",
            "udp://tracker.zerobytes.xyz:1337/announce",
            "udp://open.stealth.si:6969/announce",
            "udp://tracker.openbittorrent.com:6881/announce"
        )
    }
}
