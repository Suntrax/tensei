package com.blissless.tensei.torrent

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.openani.anitorrent.binding.*
import java.io.File
import java.util.BitSet
import java.util.concurrent.atomic.AtomicBoolean
import com.blissless.tensei.util.ErrorHandler

class TorrentEngine(private val context: Context) {

    interface EngineListener {
        fun onMetadataReceived(meta: TorrentMeta)
        fun onProgress(downloaded: Long, total: Long)
        fun onFinished()
        fun onError(message: String)
    }

    private var session: session_t? = null
    private var currentHandle: torrent_handle_t? = null
    private var pendingStreamFileIndex = -1

    private var streamingFirstPiece = 0
    private var streamingLastPiece = 0
    private var streamingWindowStart = 0
    private var streamingWindowEnd = 0
    private var lastAdvancedPiece = -1
    private var streamingPrioritiesSet = false

    private val listeners = mutableListOf<EngineListener>()
    val isRunning = AtomicBoolean(false)

    private var eventLoopJob: Job? = null
    private val eventChannel = Channel<Unit>(Channel.CONFLATED)
    private val mutex = Mutex()
    private val completedPieces = BitSet()

    val saveDir: File
        get() = File(context.cacheDir, "torrent_stream").also { it.mkdirs() }

    private lateinit var newEventListener: new_event_listener_t
    private lateinit var eventListener: event_listener_t

    private fun createEventListeners() {
        newEventListener = object : new_event_listener_t() {
            override fun on_new_events() {
                try {
                    eventChannel.trySend(Unit)
                } catch (e: Exception) {
                    Log.w(TAG, "newEventListener.on_new_events: trySend failed: ${e.message}")
                }
            }
        }

        eventListener = object : event_listener_t() {
        override fun on_checked(handle_id: Long) {
            Log.d(TAG, "event: on_checked handle_id=$handle_id")
        }

        override fun on_metadata_received(handle_id: Long) {
            Log.i(TAG, "event: on_metadata_received handle_id=$handle_id")
            scope.launch {
                mutex.withLock {
                    handleMetadataReceived(handle_id)
                }
            }
        }

        override fun on_torrent_added(handle_id: Long) {
            Log.i(TAG, "event: on_torrent_added handle_id=$handle_id")
        }

        override fun on_save_resume_data(handle_id: Long, data: torrent_resume_data_t?) {
            Log.d(TAG, "event: on_save_resume_data handle_id=$handle_id")
            data?.delete()
        }

        override fun on_torrent_state_changed(handle_id: Long, state: torrent_state_t?) {
            Log.d(TAG, "event: on_torrent_state_changed handle_id=$handle_id state=$state")
        }

        override fun on_block_downloading(handle_id: Long, piece_index: Int, block_index: Int) {
            Log.v(TAG, "event: on_block_downloading handle_id=$handle_id piece=$piece_index block=$block_index")
        }

        override fun on_piece_finished(handle_id: Long, piece_index: Int) {
            Log.v(TAG, "event: on_piece_finished handle_id=$handle_id piece=$piece_index")
            completedPieces.set(piece_index)
            scope.launch {
                advanceStreamingWindow()
            }
        }

        override fun on_status_update(handle_id: Long, stats: torrent_stats_t?) {
            stats ?: return
            val downloaded = stats.total_done
            val total = stats.total
            val dlRate = stats.download_payload_rate / 1024
            Log.d(TAG, "event: on_status_update progress=${stats.progress} " +
                    "downloaded=$downloaded/$total dl=${dlRate}KB/s")
            listeners.forEach { it.onProgress(downloaded, total) }
            if (stats.progress >= 1.0f && total > 0) {
                if (!finishedNotified) {
                    finishedNotified = true
                    Log.i(TAG, "event: torrent finished — pausing to avoid seeding")
                    currentHandle?.ignore_all_files()
                    listeners.forEach { it.onFinished() }
                }
            }
        }

        override fun on_file_completed(handle_id: Long, file_index: Int) {
            Log.d(TAG, "event: on_file_completed handle_id=$handle_id file=$file_index")
        }

        override fun on_torrent_removed(handle_id: Long, torrent_name: String) {
            Log.i(TAG, "event: on_torrent_removed handle_id=$handle_id name=$torrent_name")
        }

        override fun on_session_stats(handle_id: Long, stats: session_stats_t?) {
            stats?.delete()
        }
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var lastProgressTime = 0L
    private var lastLoggedState = -1
    private var finishedNotified = false
    private var metadataWaitLogged = false
    private var lastMetadataLogTime = 0L
    private var torrentAddedTime = 0L

    fun start() {
        if (isRunning.getAndSet(true)) {
            Log.d(TAG, "start: already running, skipping")
            return
        }
        Log.i(TAG, "start: initializing torrent engine (anitorrent 0.2.0)")
        Log.d(TAG, "start: saveDir=${saveDir.absolutePath}")
        try {
            System.loadLibrary("anitorrent")
            Log.d(TAG, "start: native library loaded")
            createEventListeners()
            val s = session_t()
            val settings = session_settings_t().apply {
                download_rate_limit = 20 * 1024 * 1024
                upload_rate_limit = 1 * 1024 * 1024
                connections_limit = 200
                active_downloads = 4
                active_seeds = 4
                user_agent = "anilt/3.0.0"
                peer_fingerprint = "anilt/3.0.0"
                dht_bootstrap_nodes_extra_add("router.utorrent.com:6881")
                dht_bootstrap_nodes_extra_add("router.bittorrent.com:6881")
                dht_bootstrap_nodes_extra_add("dht.transmissionbt.com:6881")
                dht_bootstrap_nodes_extra_add("router.bitcomet.com:6881")
                dht_bootstrap_nodes_extra_add("router.silotis.us:6881")
                dht_bootstrap_nodes_extra_add("dht.aelitis.com:6881")
                dht_bootstrap_nodes_extra_add("router.openbittorrent.com:6881")
                dht_bootstrap_nodes_extra_add("open.stealth.si:6969")
            }
            s.start(settings)
            settings.delete()
            s.resume()
            session = s
            Log.i(TAG, "start: session started")
            s.set_new_event_listener(newEventListener)
            startEventLoop()
            Log.i(TAG, "start: engine ready")
        } catch (e: Exception) {
            Log.e(TAG, "start: FAILED to start session", e)
            listeners.forEach { it.onError("Failed to start torrent engine: ${e.message}") }
        }
    }

    private fun startEventLoop() {
        eventLoopJob?.cancel()
        eventLoopJob = scope.launch {
            Log.d(TAG, "eventLoop: started")
            while (isActive) {
                try {
                    val s = session ?: break
                    s.wait_for_alert(1)
                    s.process_events(eventListener)
                    currentHandle?.let { h ->
                        if (h.is_valid()) h.post_status_updates()
                    }
                    delay(1000)
                } catch (e: CancellationException) {
                    Log.d(TAG, "eventLoop: cancelled")
                    break
                } catch (t: Throwable) {
                    if (isActive) Log.e(TAG, "eventLoop: error", t)
                }
            }
            Log.d(TAG, "eventLoop: exiting")
        }
    }

    private fun startMetadataWaitLog() {
        scope.launch {
            while (isActive) {
                delay(10_000)
                val handle = currentHandle ?: continue
                if (!handle.is_valid()) continue
                val elapsed = System.currentTimeMillis() - torrentAddedTime
                val state = handle.get_state()
                Log.w(TAG, "METADATA WAIT: elapsed=${elapsed}ms state=$state")
            }
        }
    }

    private suspend fun handleMetadataReceived(handleId: Long) {
        val handle = currentHandle ?: run {
            Log.e(TAG, "handleMetadataReceived: handle is null")
            return
        }
        if (!handle.is_valid()) {
            Log.e(TAG, "handleMetadataReceived: handle invalid")
            return
        }
        val reloadResult = handle.reload_file()
        if (reloadResult != torrent_handle_t.reload_file_result_t.kReloadFileSuccess) {
            Log.e(TAG, "handleMetadataReceived: reload_file failed with code $reloadResult")
            return
        }
        val info = handle.get_info_view()
        if (info == null) {
            Log.e(TAG, "handleMetadataReceived: get_info_view returned null")
            return
        }
        Log.i(TAG, "handleMetadataReceived: name='${info.name}' " +
                "files=${info.file_count()} pieces=${info.num_pieces} " +
                "pieceLen=${info.piece_length} totalSize=${info.total_size}")
        val meta = buildMeta(info)
        listeners.forEach { it.onMetadataReceived(meta) }
        if (!streamingPrioritiesSet && pendingStreamFileIndex >= 0) {
            Log.d(TAG, "handleMetadataReceived: setting up streaming priorities for pending file $pendingStreamFileIndex")
            try {
                setupStreamingPriorities(handle, info, pendingStreamFileIndex)
            } catch (e: Exception) {
                Log.e(TAG, "handleMetadataReceived: setupStreamingPriorities failed", e)
            }
        }
        handle.post_status_updates()
    }

    fun addListener(l: EngineListener) = listeners.add(l)
    fun removeListener(l: EngineListener) = listeners.remove(l)

    private val BACKUP_TRACKERS = listOf(
        "udp://open.demonii.com:1337/announce",
        "udp://tracker.opentrackr.org:1337/announce",
        "udp://exodus.desync.com:6969/announce",
        "udp://tracker.openbittorrent.com:6969/announce",
        "udp://tracker.cyberia.is:6969/announce",
        "udp://tracker.moeking.me:6969/announce",
        "udp://opentracker.i2p.rocks:6969/announce",
        "udp://tracker.zerobytes.xyz:1337/announce"
    )

    private fun enhanceMagnetWithTrackers(magnetUri: String): String {
        var enhanced = magnetUri
        for (tracker in BACKUP_TRACKERS) {
            val encoded = java.net.URLEncoder.encode(tracker, "UTF-8")
            if (enhanced.contains("tr=$encoded") || enhanced.contains("tr=$tracker")) continue
            enhanced += "&tr=$encoded"
        }
        if (enhanced != magnetUri) {
            Log.d(TAG, "enhanceMagnetWithTrackers: appended ${BACKUP_TRACKERS.size} backup trackers")
        }
        return enhanced
    }

    fun addTorrentFromMagnet(magnetUri: String) {
        val enhancedUri = enhanceMagnetWithTrackers(magnetUri)
        Log.i(TAG, "addTorrentFromMagnet: $enhancedUri")
        if (!isRunning.get()) {
            Log.w(TAG, "addTorrentFromMagnet: engine not running, starting it now")
            start()
        }
        try {
            removeCurrentTorrent()
            val s = session ?: run {
                Log.e(TAG, "addTorrentFromMagnet: session is null")
                listeners.forEach { it.onError("Torrent session not initialized") }
                return
            }
            Log.d(TAG, "addTorrentFromMagnet: creating torrent_add_info_t")
            val addInfo = torrent_add_info_t().apply {
                magnet_uri = enhancedUri
                kind = torrent_add_info_t.kKindMagnetUri
            }
            val handle = torrent_handle_t()
            Log.d(TAG, "addTorrentFromMagnet: calling session.start_download()")
            val success = s.start_download(handle, addInfo, saveDir.absolutePath)
            addInfo.delete()
            if (!success) {
                handle.delete()
                val msg = "Magnet add failed: start_download returned false"
                Log.e(TAG, "addTorrentFromMagnet: $msg")
                listeners.forEach { it.onError(msg) }
                return
            }
            currentHandle = handle
            torrentAddedTime = System.currentTimeMillis()
            Log.i(TAG, "addTorrentFromMagnet: magnet added, handle_id=${handle.id} valid=${handle.is_valid()}")
            handle.resume()
            Log.d(TAG, "addTorrentFromMagnet: handle resumed")
            for (tracker in BACKUP_TRACKERS) {
                try { handle.add_tracker(tracker, 0, 0) }
                catch (e: Exception) { Log.w(TAG, "addTorrentFromMagnet: add_tracker($tracker) failed: ${e.message}") }
            }
            Log.d(TAG, "addTorrentFromMagnet: added ${BACKUP_TRACKERS.size} trackers explicitly")
            handle.post_status_updates()
            startMetadataWaitLog()
        } catch (e: Exception) {
            Log.e(TAG, "addTorrentFromMagnet: FAILED", e)
            listeners.forEach { it.onError("Failed to add magnet: ${e.message}") }
        }
    }

    fun startDownload(fileIndex: Int) {
        Log.i(TAG, "startDownload: fileIndex=$fileIndex")
        finishedNotified = false
        val handle = currentHandle ?: run {
            Log.e(TAG, "startDownload: handle is null, aborting")
            return
        }
        if (!handle.is_valid()) {
            Log.e(TAG, "startDownload: handle is invalid, aborting")
            return
        }

        Log.d(TAG, "startDownload: applying file priorities")
        applyFilePriorities(handle, fileIndex)

        val reloadResult = handle.reload_file()
        val info = if (reloadResult == torrent_handle_t.reload_file_result_t.kReloadFileSuccess) handle.get_info_view() else null
        if (info != null) {
            Log.d(TAG, "startDownload: metadata available, setting up streaming priorities")
            setupStreamingPriorities(handle, info, fileIndex)
        } else {
            pendingStreamFileIndex = fileIndex
            Log.d(TAG, "startDownload: metadata not yet available, will set streaming priorities on MetadataReceived")
        }
        handle.post_status_updates()
    }

    private fun setupStreamingPriorities(h: torrent_handle_t, info: torrent_info_t, fileIndex: Int) {
        if (!h.is_valid()) {
            Log.w(TAG, "setupStreamingPriorities: handle invalid, deferring")
            pendingStreamFileIndex = fileIndex
            return
        }
        Log.d(TAG, "setupStreamingPriorities: fileIndex=$fileIndex")

        val totalPieces = info.num_pieces
        if (totalPieces == 0) {
            Log.w(TAG, "setupStreamingPriorities: no pieces in torrent")
            return
        }

        val file = info.file_at(fileIndex)
        if (file == null) {
            Log.w(TAG, "setupStreamingPriorities: file $fileIndex not found, deferring")
            pendingStreamFileIndex = fileIndex
            return
        }

        val pieceLength = info.piece_length.toLong()
        streamingFirstPiece = (file.offset / pieceLength).toInt()
        streamingLastPiece = ((file.offset + file.size - 1) / pieceLength).toInt().coerceAtMost(totalPieces - 1)

        val totalFilePieces = streamingLastPiece - streamingFirstPiece + 1
        streamingWindowStart = streamingFirstPiece
        streamingWindowEnd = minOf(streamingFirstPiece + STREAMING_WINDOW_SIZE, streamingLastPiece + 1)
        lastAdvancedPiece = streamingFirstPiece - 1

        Log.i(TAG, "setupStreamingPriorities: file '${file.name}' (idx=$fileIndex, size=${file.size} bytes)" +
                " piece range $streamingFirstPiece-$streamingLastPiece ($totalFilePieces pieces)" +
                " pieceLen=$pieceLength window=$streamingFirstPiece-${streamingWindowEnd - 1}")

        h.clear_piece_deadlines()
        Log.d(TAG, "setupStreamingPriorities: cleared existing deadlines")

        val headDeadlineCount = minOf(STREAMING_DEADLINE_SIZE, totalFilePieces)
        var deadlineSet = 0
        for (i in 0 until headDeadlineCount) {
            val piece = streamingFirstPiece + i
            try {
                h.set_piece_deadline(piece, -(i + 1))
                deadlineSet++
            } catch (e: Exception) {
                Log.w(TAG, "setupStreamingPriorities: set_piece_deadline($piece) failed: ${e.message}")
            }
        }
        Log.d(TAG, "setupStreamingPriorities: set $deadlineSet head deadlines (pieces $streamingFirstPiece-${streamingFirstPiece + headDeadlineCount - 1})")

        val tailDeadlineCount = minOf(STREAMING_DEADLINE_SIZE, totalFilePieces)
        val tailDeadlineStart = maxOf(streamingFirstPiece, streamingLastPiece - tailDeadlineCount + 1)
        if (totalFilePieces > tailDeadlineCount + 2) {
            for (i in tailDeadlineStart..streamingLastPiece) {
                val deadline = (i - tailDeadlineStart).coerceAtMost(headDeadlineCount - 1) + 1
                try { h.set_piece_deadline(i, -deadline) }
                catch (e: Exception) { Log.w(TAG, "setupStreamingPriorities: tail deadline($i) failed: ${e.message}") }
            }
            Log.d(TAG, "setupStreamingPriorities: set tail deadlines for MKV cue data (pieces $tailDeadlineStart-$streamingLastPiece)")
        }

        streamingPrioritiesSet = true
        pendingStreamFileIndex = -1
        Log.i(TAG, "setupStreamingPriorities: complete — ready for streaming")
    }

    fun advanceStreamingWindow() {
        if (!streamingPrioritiesSet) return
        val handle = currentHandle ?: return
        if (!handle.is_valid()) return

        var lastConsecutive = streamingWindowStart - 1
        while (lastConsecutive < streamingLastPiece) {
            if (completedPieces.get(lastConsecutive + 1)) {
                lastConsecutive++
            } else break
        }

        if (lastConsecutive > lastAdvancedPiece) {
            lastAdvancedPiece = lastConsecutive
            streamingWindowStart = lastConsecutive + 1
            val newWindowEnd = minOf(streamingWindowStart + STREAMING_WINDOW_SIZE, streamingLastPiece + 1)
            Log.d(TAG, "advanceWindow: completed up to piece $lastConsecutive, window -> $streamingWindowStart-${newWindowEnd - 1}")
            streamingWindowEnd = newWindowEnd
            val deadlineStart = streamingWindowStart
            val deadlineEnd = minOf(streamingWindowStart + STREAMING_DEADLINE_SIZE, streamingWindowEnd)
            for (i in deadlineStart until deadlineEnd) {
                try { handle.set_piece_deadline(i, -((i - streamingWindowStart) + 1)) }
                catch (e: Exception) { Log.w(TAG, "advanceWindow: deadline($i) failed: ${e.message}") }
            }
        }
    }

    fun getFileSize(fileIndex: Int): Long {
        val handle = currentHandle ?: return 0L
        return try {
            val reloadResult = handle.reload_file()
            if (reloadResult != torrent_handle_t.reload_file_result_t.kReloadFileSuccess) return 0L
            val info = handle.get_info_view() ?: return 0L
            val file = info.file_at(fileIndex) ?: return 0L
            val size = file.size
            Log.d(TAG, "getFileSize: fileIndex=$fileIndex size=$size")
            size
        } catch (e: Exception) { ErrorHandler.report("TorrentEngine", "getFileSize failed", e); 0L }
    }

    fun getLastPieceForFile(): Int {
        val handle = currentHandle ?: return -1
        val reloadResult = handle.reload_file()
        if (reloadResult != torrent_handle_t.reload_file_result_t.kReloadFileSuccess) return -1
        val info = handle.get_info_view() ?: return -1
        val fileIndex = findSelectedFileIndex(handle, info) ?: return -1
        val file = info.file_at(fileIndex) ?: return -1
        val pieceLength = info.piece_length.toLong()
        return ((file.offset + file.size - 1) / pieceLength).toInt().coerceAtMost(info.num_pieces - 1)
    }

    fun getContiguousDownloadedBytes(): Long {
        val handle = currentHandle ?: return 0L
        val reloadResult = handle.reload_file()
        if (reloadResult != torrent_handle_t.reload_file_result_t.kReloadFileSuccess) return 0L
        val info = handle.get_info_view() ?: return 0L
        val fileIndex = findSelectedFileIndex(handle, info) ?: return 0L
        val file = info.file_at(fileIndex) ?: return 0L
        val fileSize = file.size
        if (fileSize <= 0) return 0L
        val pieceLength = info.piece_length.toLong()
        val firstPiece = (file.offset / pieceLength).toInt()
        val lastPiece = ((file.offset + file.size - 1) / pieceLength).toInt().coerceAtMost(info.num_pieces - 1)
        var contiguousPieces = 0
        for (i in firstPiece..lastPiece) {
            if (completedPieces.get(i)) contiguousPieces++ else break
        }
        if (contiguousPieces == 0) return 0L
        val totalFilePieces = lastPiece - firstPiece + 1
        if (contiguousPieces >= totalFilePieces) return fileSize
        val contiguousBytes = contiguousPieces.toLong() * pieceLength
        Log.d(TAG, "getContiguousDownloadedBytes: $contiguousPieces/$totalFilePieces pieces = $contiguousBytes bytes (of $fileSize)")
        return contiguousBytes
    }

    private fun findSelectedFileIndex(h: torrent_handle_t, info: torrent_info_t): Int? {
        for (i in 0 until info.file_count().toInt()) {
            try {
                val reloadResult = h.reload_file()
                if (reloadResult == torrent_handle_t.reload_file_result_t.kReloadFileSuccess) {
                    val currentInfo = h.get_info_view() ?: continue
                    val file = currentInfo.file_at(i) ?: continue
                    if (file.size > 0) return i
                }
            } catch (e: Exception) { ErrorHandler.ignore("TorrentEngine", "findSelectedFileIndex iteration failed", e) }
        }
        var maxSize = 0L; var maxIdx = 0
        for (i in 0 until info.file_count().toInt()) {
            val file = info.file_at(i) ?: continue
            if (file.size > maxSize) { maxSize = file.size; maxIdx = i }
        }
        return if (maxSize > 0) maxIdx else null
    }

    fun getFileSavePath(fileIndex: Int): String? {
        val handle = currentHandle ?: return null
        return try {
            val reloadResult = handle.reload_file()
            if (reloadResult != torrent_handle_t.reload_file_result_t.kReloadFileSuccess) return null
            val info = handle.get_info_view() ?: return null
            val file = info.file_at(fileIndex) ?: return null
            val path = if (file.path.isNotEmpty()) {
                File(saveDir, file.path).absolutePath
            } else {
                File(saveDir, file.name).absolutePath
            }
            Log.d(TAG, "getFileSavePath: fileIndex=$fileIndex path=$path")
            path
        } catch (e: Exception) { ErrorHandler.report("TorrentEngine", "getFileSavePath failed", e); null }
    }

    fun getNumPieces(): Int {
        val handle = currentHandle ?: return 1
        val reloadResult = handle.reload_file()
        if (reloadResult != torrent_handle_t.reload_file_result_t.kReloadFileSuccess) return 1
        val info = handle.get_info_view() ?: return 1
        return info.num_pieces
    }

    fun getPieceSize(): Long {
        val handle = currentHandle ?: return 4L * 1024 * 1024
        val reloadResult = handle.reload_file()
        if (reloadResult != torrent_handle_t.reload_file_result_t.kReloadFileSuccess) return 4L * 1024 * 1024
        val info = handle.get_info_view() ?: return 4L * 1024 * 1024
        return info.piece_length.toLong()
    }

    fun havePiece(pieceIndex: Int): Boolean {
        return completedPieces.get(pieceIndex)
    }

    fun removeCurrentTorrent() {
        Log.d(TAG, "removeCurrentTorrent: cleaning up previous torrent")
        currentHandle?.let { handle ->
            try {
                session?.release_handle(handle)
            } catch (e: Exception) {
                Log.w(TAG, "removeCurrentTorrent: release_handle failed: ${e.message}")
            }
            try { handle.delete() } catch (e: Exception) {
                Log.w(TAG, "removeCurrentTorrent: handle.delete() failed: ${e.message}")
            }
        }
        currentHandle = null
        resetStreamingState()
        Log.d(TAG, "removeCurrentTorrent: done")
    }

    fun clearCache() {
        Log.d(TAG, "clearCache: deleting ${saveDir.absolutePath}")
        try { saveDir.listFiles()?.forEach { it.deleteRecursively() } } catch (e: Exception) { ErrorHandler.ignore("TorrentEngine", "clearCache failed", e) }
    }

    fun stop() {
        Log.i(TAG, "stop: shutting down engine")
        isRunning.set(false)
        eventLoopJob?.cancel()
        eventLoopJob = null
        removeCurrentTorrent()
        session?.let { s ->
            try { s.remove_listener() } catch (e: Exception) { Log.w(TAG, "stop: remove_listener failed: ${e.message}") }
            try { s.delete() } catch (e: Exception) { Log.w(TAG, "stop: session.delete() failed: ${e.message}") }
        }
        session = null
        Log.d(TAG, "stop: engine stopped")
    }

    private fun resetStreamingState() {
        streamingFirstPiece = 0; streamingLastPiece = 0
        streamingWindowStart = 0; streamingWindowEnd = 0
        lastAdvancedPiece = -1; streamingPrioritiesSet = false; pendingStreamFileIndex = -1
        lastLoggedState = -1; metadataWaitLogged = false; lastMetadataLogTime = 0L
        lastProgressTime = 0L; finishedNotified = false; torrentAddedTime = 0L
        completedPieces.clear()
    }

    private fun applyFilePriorities(h: torrent_handle_t, selectedIndex: Int) {
        Log.d(TAG, "applyFilePriorities: selectedIndex=$selectedIndex, others=IGNORE")
        try {
            h.ignore_all_files()
            h.set_file_priority(selectedIndex, 7)
            Log.d(TAG, "applyFilePriorities: file $selectedIndex -> HIGH, all others -> IGNORE")
        } catch (e: Exception) {
            Log.w(TAG, "applyFilePriorities: error: ${e.message}")
        }
    }

    fun getLargestVideoFileIndex(): Int {
        return try {
            val handle = currentHandle ?: return 0
            val reloadResult = handle.reload_file()
            if (reloadResult != torrent_handle_t.reload_file_result_t.kReloadFileSuccess) return 0
            val info = handle.get_info_view() ?: return 0
            var bestIdx = 0; var bestSize = 0L
            val videoExts = setOf("mkv", "mp4", "webm", "avi", "mov", "m4v")
            Log.d(TAG, "getLargestVideoFileIndex: scanning ${info.file_count()} files for video")
            for (i in 0 until info.file_count().toInt()) {
                val file = info.file_at(i) ?: continue
                val name = file.name
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext in videoExts) {
                    val size = file.size
                    Log.d(TAG, "getLargestVideoFileIndex: file $i '$name' ($size bytes, .$ext)")
                    if (size > bestSize) { bestSize = size; bestIdx = i }
                } else {
                    Log.d(TAG, "getLargestVideoFileIndex: file $i '$name' — skipped (not video)")
                }
            }
            Log.i(TAG, "getLargestVideoFileIndex: selected=$bestIdx ($bestSize bytes)")
            bestIdx
        } catch (e: Exception) { ErrorHandler.report("TorrentEngine", "getLargestVideoFileIndex failed", e); 0 }
    }

    fun findVideoFileIndex(fileNameHint: String): Int {
        return try {
            val handle = currentHandle ?: return 0
            val reloadResult = handle.reload_file()
            if (reloadResult != torrent_handle_t.reload_file_result_t.kReloadFileSuccess) return 0
            val info = handle.get_info_view() ?: return 0
            for (i in 0 until info.file_count().toInt()) {
                val file = info.file_at(i) ?: continue
                if (file.name.contains(fileNameHint, ignoreCase = true)) return i
            }
            getLargestVideoFileIndex()
        } catch (e: Exception) { ErrorHandler.report("TorrentEngine", "findVideoFileIndex failed", e); 0 }
    }

    private fun buildMeta(info: torrent_info_t): TorrentMeta {
        val entries = (0 until info.file_count().toInt()).mapNotNull { i ->
            val file = info.file_at(i) ?: return@mapNotNull null
            TorrentFileEntry(i, file.name, file.size, file.path)
        }
        return TorrentMeta(info.name, entries)
    }

    companion object {
        private const val TAG = "TorrentEngine"
        private const val STREAMING_WINDOW_SIZE = 30
        private const val STREAMING_DEADLINE_SIZE = 15
    }
}
