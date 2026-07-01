package com.blissless.tensei.torrent

import android.content.Context
import android.util.Log
import org.libtorrent4j.*
import org.libtorrent4j.alerts.*
import org.libtorrent4j.swig.error_code
import org.libtorrent4j.swig.settings_pack
import java.io.File
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
    private var pendingStreamFileIndex = -1

    private var streamingFirstPiece = 0
    private var streamingLastPiece = 0
    private var streamingWindowStart = 0
    private var streamingWindowEnd = 0
    private var lastAdvancedPiece = -1
    private var streamingPrioritiesSet = false

    private val listeners = mutableListOf<EngineListener>()
    private var pollThread: Thread? = null
    val isRunning = AtomicBoolean(false)

    val saveDir: File
        get() = File(context.cacheDir, "torrent_stream").also { it.mkdirs() }

    // CRITICAL: Alert objects in libtorrent4j reference native data that may be
    // freed/reused AFTER this callback returns.  We MUST process alerts inline
    // here — never queue them for later, or we get use-after-free → native crash.
    private val sessionListener = object : AlertListener {
        override fun types(): IntArray? = null
        override fun alert(alert: Alert<*>) {
            try {
                handleAlert(alert)
            } catch (t: Throwable) {
                Log.e(TAG, "alert callback error: ${t.javaClass.simpleName}: ${t.message}")
            }
        }
    }

    fun start() {
        if (isRunning.getAndSet(true)) {
            Log.d(TAG, "start: already running, skipping")
            return
        }
        Log.i(TAG, "start: initializing torrent engine (libtorrent4j 2.1.0-39)")
        Log.d(TAG, "start: saveDir=${saveDir.absolutePath}")
        try {
            sessionManager.addListener(sessionListener)
            // CRITICAL: enable DHT/LSD/UPnP/NAT-PMP. Magnet links carry only an
            // infohash; without DHT libtorrent cannot discover peers, so metadata
            // never arrives and streaming never starts. Mirrors toram's config.
            Log.d(TAG, "start: configuring SettingsPack (DHT+LSD+UPnP+NAT-PMP, extended trackers)")
            val sp = SettingsPack()
            sp.setEnableDht(true)
            sp.setEnableLsd(true)
            sp.setBoolean(settings_pack.bool_types.enable_upnp.swigValue(), true)
            sp.setBoolean(settings_pack.bool_types.enable_natpmp.swigValue(), true)
            // Pause torrent when finished to avoid seeding
            // (stop_when_ready isn't exposed in this SWIG binding version)
            sessionManager.start(SessionParams(sp))
            Log.d(TAG, "start: session started, starting DHT")
            sessionManager.startDht()
            Log.d(TAG, "start: DHT started, starting poll loop")
            startPollLoop()
            Log.i(TAG, "start: engine ready")
        } catch (e: Exception) {
            Log.e(TAG, "start: FAILED to start session", e)
            listeners.forEach { it.onError("Failed to start torrent engine: ${e.message}") }
        }
    }

    private fun startPollLoop() {
        pollThread?.interrupt()
        pollThread = Thread {
            Log.d(TAG, "poll: loop started")
            while (isRunning.get()) {
                try {
                    processProgress()
                    Thread.sleep(500)
                } catch (e: InterruptedException) {
                    Log.d(TAG, "poll: interrupted, exiting")
                    break
                } catch (t: Throwable) {
                    if (isRunning.get()) Log.e(TAG, "poll: loop error", t)
                }
            }
            Log.d(TAG, "poll: loop exiting")
        }.apply {
            isDaemon = true
            name = "torrent-engine"
            start()
        }
    }

    private var lastProgressTime = 0L
    private var lastLoggedState = -1
    private var finishedNotified = false
    private var metadataWaitLogged = false
    private var torrentAddedTime = 0L

    private fun processProgress() {
        val h = handle ?: return
        // Guard: calling status() on an invalid handle causes a native crash
        // (SIGSEGV) that Java try/catch CANNOT intercept.
        if (!h.isValid()) {
            Log.w(TAG, "processProgress: handle is invalid, skipping")
            return
        }
        try {
            val st = h.status()
            val now = System.currentTimeMillis()
            // Throttle progress logs to once per 2s, but always log state changes
            val state = st.state()
            if (state.ordinal != lastLoggedState) {
                lastLoggedState = state.ordinal
                Log.i(TAG, "state: ${state.name} | downloaded=${st.totalWantedDone()}/${st.totalWanted()}" +
                        " | peers=${st.numPeers()} seeds=${st.numSeeds()} | dl=${st.downloadRate() / 1024}KB/s" +
                        " | hasMetadata=${st.hasMetadata()} | progress=${st.progress()}")
            }
            if (now - lastProgressTime > 2000) {
                lastProgressTime = now
                listeners.forEach { it.onProgress(st.totalWantedDone(), st.totalWanted()) }
                if (st.totalWanted() > 0) {
                    Log.d(TAG, "progress: ${st.totalWantedDone()}/${st.totalWanted()}" +
                            " (${(st.totalWantedDone() * 100 / st.totalWanted())}%)" +
                            " peers=${st.numPeers()} dl=${st.downloadRate() / 1024}KB/s")
                }
                if (!st.hasMetadata() && !metadataWaitLogged) {
                    metadataWaitLogged = true
                    val elapsed = if (torrentAddedTime > 0) now - torrentAddedTime else now - lastProgressTime
                    Log.e(TAG, "METADATA WAIT: elapsed=${elapsed}ms, state=${state.name}, peers=${st.numPeers()}, seeds=${st.numSeeds()}, progress=${st.progress()} — DHT may have no peers for this infohash")
                }
                if (st.isFinished() || st.isSeeding()) {
                    if (!finishedNotified) {
                        finishedNotified = true
                        Log.i(TAG, "progress: torrent finished/seeding — pausing to avoid seeding")
                        try { h.pause() } catch (_: Exception) {}
                        listeners.forEach { it.onFinished() }
                    }
                } else {
                    finishedNotified = false
                }
                advanceStreamingWindow()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "processProgress: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    private fun handleAlert(alert: Alert<*>) {
        try {
            when (alert) {
                is AddTorrentAlert -> {
                    // Do NOT overwrite `handle` from the alert.
                    // The handle was already set in addTorrentFromMagnet() and is a
                    // stable reference.  The alert's handle() may reference native
                    // data that becomes invalid after this callback returns,
                    // causing a SIGSEGV on the next status() call.
                    Log.i(TAG, "alert: AddTorrent — torrent added to session" +
                            " (handle already set, isValid=${handle?.isValid() ?: false})")
                }
                is MetadataReceivedAlert -> {
                    Log.i(TAG, "alert: MetadataReceived — metadata downloaded successfully!")
                    val h = handle ?: run {
                        Log.e(TAG, "alert: MetadataReceived but handle is null")
                        return
                    }
                    if (!h.isValid()) {
                        Log.e(TAG, "alert: MetadataReceived but handle invalid")
                        return
                    }
                    val ti = try { h.torrentFile() } catch (e: Exception) {
                        Log.e(TAG, "alert: MetadataReceived but torrentFile() threw", e); null
                    }
                    if (ti != null) {
                        Log.d(TAG, "alert: MetadataReceived — name='${ti.name()}'" +
                                " files=${ti.numFiles()} pieces=${ti.numPieces()} pieceLen=${ti.pieceLength()}" +
                                " totalSize=${ti.totalSize()}")
                        val meta = buildMeta(ti)
                        listeners.forEach { it.onMetadataReceived(meta) }
                        if (!streamingPrioritiesSet && pendingStreamFileIndex >= 0) {
                            Log.d(TAG, "alert: setting up streaming priorities for pending file $pendingStreamFileIndex")
                            try { setupStreamingPriorities(h, pendingStreamFileIndex) }
                            catch (e: Exception) { Log.e(TAG, "setupStreamingPriorities (pending) failed", e) }
                        }
                    } else {
                        Log.e(TAG, "alert: MetadataReceived but torrentFile() returned null")
                    }
                }
                is MetadataFailedAlert -> {
                    Log.e(TAG, "alert: MetadataFailed — could not download torrent metadata." +
                            " Magnet may have no peers, or DHT is not reachable.")
                    listeners.forEach { it.onError("Failed to download torrent metadata (no peers?)") }
                }
                is TorrentFinishedAlert -> {
                    if (!finishedNotified) {
                        finishedNotified = true
                        Log.i(TAG, "alert: TorrentFinished — pausing to avoid seeding")
                        try { handle?.pause() } catch (_: Exception) {}
                        listeners.forEach { it.onFinished() }
                    }
                }
                is TorrentErrorAlert -> {
                    val errMsg = alert.message() ?: alert.javaClass.simpleName
                    Log.e(TAG, "alert: TorrentError — $errMsg")
                    listeners.forEach { it.onError("Torrent error: $errMsg") }
                }
                is TrackerErrorAlert -> {
                    Log.e(TAG, "alert: TrackerError — ${alert.message()}")
                }
                is TrackerWarningAlert -> {
                    Log.w(TAG, "alert: TrackerWarning — ${alert.message()}")
                }
                is TrackerReplyAlert -> {
                    Log.d(TAG, "alert: TrackerReply — ${alert.message()}")
                }
                is PeerConnectAlert -> Log.d(TAG, "alert: PeerConnect — a peer connected")
                is PeerDisconnectedAlert -> {
                    val msg = alert.message() ?: "no reason"
                    Log.d(TAG, "alert: PeerDisconnect — $msg")
                }
                is DhtBootstrapAlert -> Log.d(TAG, "alert: DhtBootstrap — DHT is initializing")
                is DhtReplyAlert -> Log.v(TAG, "alert: DhtReply — DHT responded")
                is ExternalIpAlert -> Log.d(TAG, "alert: ExternalIp — external IP reported")
                is ListenSucceededAlert -> Log.d(TAG, "alert: ListenSucceeded — listening on a socket")
                is ListenFailedAlert -> Log.e(TAG, "alert: ListenFailed — could not listen on a socket")
                is PortmapErrorAlert -> Log.w(TAG, "alert: PortmapError — UPnP/NAT-PMP mapping failed")
                is PortmapAlert -> Log.d(TAG, "alert: Portmap — UPnP/NAT-PMP mapping succeeded")
                is StateChangedAlert -> Log.d(TAG, "alert: StateChanged — torrent state changed")
                is FastresumeRejectedAlert -> Log.d(TAG, "alert: FastresumeRejected")
                is PieceFinishedAlert -> Log.v(TAG, "alert: PieceFinished — a piece completed")
                is BlockFinishedAlert -> Log.v(TAG, "alert: BlockFinished — a block completed")
                is ReadPieceAlert -> Log.v(TAG, "alert: ReadPiece — a piece was read")
                else -> {
                    // Log alert type name for any unhandled alerts (verbose only)
                    Log.v(TAG, "alert: ${alert.javaClass.simpleName}")
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "handleAlert: error processing ${alert.javaClass.simpleName}", t)
        }
    }

    fun addListener(l: EngineListener) = listeners.add(l)
    fun removeListener(l: EngineListener) = listeners.remove(l)

    // Well-known public trackers appended as fallbacks when the magnet's own
    // trackers are unreachable or return no peers.
    private val BACKUP_TRACKERS = listOf(
        "udp://exodus.desync.com:6969/announce",
        "udp://tracker.cyberia.is:6969/announce",
        "udp://tracker.moeking.me:6969/announce",
        "udp://opentracker.i2p.rocks:6969/announce",
        "udp://tracker.zerobytes.xyz:1337/announce",
        "udp://tracker1.bt.moack.co.kr:80/announce",
        "udp://tracker.tiny-vps.com:6969/announce"
    )

    private fun enhanceMagnetWithTrackers(magnetUri: String): String {
        var enhanced = magnetUri
        for (tracker in BACKUP_TRACKERS) {
            // Skip if this tracker is already present (raw or URL-encoded)
            val encoded = java.net.URLEncoder.encode(tracker, "UTF-8")
            if (enhanced.contains("tr=$encoded") || enhanced.contains("tr=${tracker}")) continue
            enhanced += "&tr=$encoded"
        }
        if (enhanced != magnetUri) {
            val added = BACKUP_TRACKERS.size - (enhanced.length - magnetUri.length) / 100 // rough
            Log.d(TAG, "enhanceMagnetWithTrackers: appended ${BACKUP_TRACKERS.size} backup trackers")
        }
        return enhanced
    }

    fun addTorrentFromMagnet(magnetUri: String) {
        val enhancedUri = enhanceMagnetWithTrackers(magnetUri)
        Log.i(TAG, "addTorrentFromMagnet: ${enhancedUri.take(250)}...")
        if (!isRunning.get()) {
            Log.w(TAG, "addTorrentFromMagnet: engine not running, starting it now")
            start()
        }
        try {
            // Use the low-level swig().add_torrent() (like toram) instead of
            // sessionManager.download(), which is a blocking convenience call
            // that waits for the whole download — useless for streaming.
            // add_torrent returns a handle immediately so we can configure
            // streaming priorities before metadata arrives.
            Log.d(TAG, "addTorrentFromMagnet: parsing magnet URI")
            val atp = AddTorrentParams.parseMagnetUri(enhancedUri)
            atp.setSavePath(saveDir.absolutePath)
            Log.d(TAG, "addTorrentFromMagnet: savePath=${saveDir.absolutePath}")
            val ec = error_code()
            Log.d(TAG, "addTorrentFromMagnet: calling swig().add_torrent()")
            val raw = sessionManager.swig().add_torrent(atp.swig(), ec)
            if (ec.failed()) {
                val msg = "Magnet add failed: ${ec.message()}"
                Log.e(TAG, "addTorrentFromMagnet: $msg")
                listeners.forEach { it.onError(msg) }
                return
            }
            rawHandle = raw
            handle = TorrentHandle(raw)
            torrentAddedTime = System.currentTimeMillis()
            Log.i(TAG, "addTorrentFromMagnet: magnet added, handle valid=${raw.is_valid()}")

            // Enable sequential mode IMMEDIATELY when the handle is created.
            // Prevents libtorrent requesting random pieces before
            // setupStreamingPriorities() runs (after metadata arrives).
            rawHandle?.set_sequential_range(0, Int.MAX_VALUE)
            Log.d(TAG, "addTorrentFromMagnet: sequential mode enabled on magnet handle (0..MAX)")
        } catch (e: Exception) {
            Log.e(TAG, "addTorrentFromMagnet: FAILED", e)
            listeners.forEach { it.onError("Failed to add magnet: ${e.message}") }
        }
    }

    fun startDownload(fileIndex: Int) {
        Log.i(TAG, "startDownload: fileIndex=$fileIndex")
        finishedNotified = false
        val h = handle ?: run {
            Log.e(TAG, "startDownload: handle is null, aborting")
            return
        }
        if (!h.isValid()) {
            Log.e(TAG, "startDownload: handle is invalid, aborting")
            return
        }

        Log.d(TAG, "startDownload: applying file priorities")
        applyFilePriorities(h, fileIndex)

        val ti = try { h.torrentFile() } catch (_: Exception) { null }
        if (ti != null) {
            Log.d(TAG, "startDownload: metadata available, setting up streaming priorities")
            setupStreamingPriorities(h, fileIndex)
        } else {
            pendingStreamFileIndex = fileIndex
            Log.d(TAG, "startDownload: metadata not yet available, will set streaming priorities on MetadataReceived")
        }
    }

    private fun setupStreamingPriorities(h: TorrentHandle, fileIndex: Int) {
        if (!h.isValid()) {
            Log.w(TAG, "setupStreamingPriorities: handle invalid, deferring")
            pendingStreamFileIndex = fileIndex
            return
        }
        Log.d(TAG, "setupStreamingPriorities: fileIndex=$fileIndex")
        val ti = try { h.torrentFile() } catch (_: Exception) { null } ?: run {
            Log.w(TAG, "setupStreamingPriorities: no torrent info yet, deferring")
            pendingStreamFileIndex = fileIndex
            return
        }

        val totalPieces = ti.numPieces()
        if (totalPieces == 0) {
            Log.w(TAG, "setupStreamingPriorities: no pieces in torrent")
            return
        }

        val fs = ti.files()
        if (fs == null) {
            Log.w(TAG, "setupStreamingPriorities: no file storage")
            return
        }

        val fileName = try { fs.fileName(fileIndex) } catch (_: Exception) { "<unknown>" }
        val fileSize = fs.fileSize(fileIndex)
        streamingFirstPiece = fs.pieceIndexAtFile(fileIndex)
        streamingLastPiece = fs.lastPieceIndexAtFile(fileIndex)

        val totalFilePieces = streamingLastPiece - streamingFirstPiece + 1
        streamingWindowStart = streamingFirstPiece
        streamingWindowEnd = minOf(streamingFirstPiece + STREAMING_WINDOW_SIZE, streamingLastPiece + 1)
        lastAdvancedPiece = streamingFirstPiece - 1

        Log.i(TAG, "setupStreamingPriorities: file '$fileName' (idx=$fileIndex, size=$fileSize bytes)" +
                " piece range ${streamingFirstPiece}-${streamingLastPiece} ($totalFilePieces pieces)" +
                " pieceLen=${ti.pieceLength()} window=$streamingFirstPiece-${streamingWindowEnd - 1}")

        // Tell libtorrent to download the file's pieces in sequential order.
        // Piece priorities + deadlines below refine this further.
        rawHandle?.set_sequential_range(streamingFirstPiece, streamingLastPiece)
        Log.d(TAG, "setupStreamingPriorities: set_sequential_range($streamingFirstPiece, $streamingLastPiece)")

        val tailDeadlineCount = minOf(STREAMING_DEADLINE_SIZE, totalFilePieces)
        var prioSet = 0
        for (i in streamingFirstPiece..streamingLastPiece) {
            val isTail = i >= streamingLastPiece - tailDeadlineCount + 1
            val priority = if (i < streamingWindowEnd || isTail) Priority.TOP_PRIORITY else Priority.DEFAULT
            try {
                h.piecePriority(i, priority)
                prioSet++
            } catch (e: Exception) {
                Log.w(TAG, "setupStreamingPriorities: piecePriority($i) failed: ${e.message}")
            }
        }
        Log.d(TAG, "setupStreamingPriorities: set $prioSet piece priorities (head+tail TOP_PRIORITY, rest DEFAULT)")

        val headDeadlineCount = minOf(STREAMING_DEADLINE_SIZE, totalFilePieces)
        var deadlineSet = 0
        for (i in 0 until headDeadlineCount) {
            val piece = streamingFirstPiece + i
            try {
                h.setPieceDeadline(piece, i + 1)
                deadlineSet++
            } catch (e: Exception) {
                Log.w(TAG, "setupStreamingPriorities: setPieceDeadline($piece) failed: ${e.message}")
            }
        }
        Log.d(TAG, "setupStreamingPriorities: set $deadlineSet head deadlines (pieces $streamingFirstPiece-${streamingFirstPiece + headDeadlineCount - 1})")

        val tailDeadlineStart = maxOf(streamingFirstPiece, streamingLastPiece - tailDeadlineCount + 1)
        if (totalFilePieces > tailDeadlineCount + 2) {
            for (i in tailDeadlineStart..streamingLastPiece) {
                val deadline = (i - tailDeadlineStart).coerceAtMost(headDeadlineCount - 1) + 1
                try { h.setPieceDeadline(i, deadline) }
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
        val h = handle ?: return
        if (!h.isValid()) return

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
            Log.d(TAG, "advanceWindow: completed up to piece $lastConsecutive, window -> $streamingWindowStart-${newWindowEnd - 1}")
            if (newWindowEnd > streamingWindowEnd) {
                for (i in streamingWindowEnd until newWindowEnd) {
                    try { h.piecePriority(i, Priority.TOP_PRIORITY) }
                    catch (e: Exception) { Log.w(TAG, "advanceWindow: piecePriority($i) failed: ${e.message}") }
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
            val size = h.torrentFile()?.files()?.fileSize(fileIndex) ?: 0L
            Log.d(TAG, "getFileSize: fileIndex=$fileIndex size=$size")
            size
        } catch (_: Exception) { 0L }
    }

    fun getLastPieceForFile(): Int {
        val h = handle ?: return -1
        val ti = try { h.torrentFile() } catch (_: Exception) { null } ?: return -1
        val fs = ti.files() ?: return -1
        val fileIndex = findSelectedFileIndex(h, fs) ?: return -1
        return fs.lastPieceIndexAtFile(fileIndex)
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
        val contiguousBytes = contiguousPieces.toLong() * pieceLength
        Log.d(TAG, "getContiguousDownloadedBytes: $contiguousPieces/$totalFilePieces pieces = $contiguousBytes bytes (of $fileSize)")
        return contiguousBytes
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
            val path = ti.files()?.filePath(fileIndex, saveDir.absolutePath)
            Log.d(TAG, "getFileSavePath: fileIndex=$fileIndex path=$path")
            path
        } catch (_: Exception) { null }
    }

    fun getNumPieces(): Int = try { handle?.torrentFile()?.numPieces() ?: 1 } catch (_: Exception) { 1 }

    fun getPieceSize(): Long {
        val ti = try { handle?.torrentFile() } catch (_: Exception) { null } ?: return 4L * 1024 * 1024
        return (ti.totalSize() + ti.numPieces() - 1) / ti.numPieces()
    }

    fun havePiece(pieceIndex: Int): Boolean {
        return try {
            rawHandle?.have_piece(pieceIndex) ?: false
        } catch (_: Exception) { false }
    }

    fun removeCurrentTorrent() {
        Log.d(TAG, "removeCurrentTorrent: cleaning up previous torrent")
        rawHandle?.let {
            try { sessionManager.swig().remove_torrent(it) }
            catch (e: Exception) { Log.w(TAG, "removeCurrentTorrent: remove_torrent failed: ${e.message}") }
        }
        handle = null
        rawHandle = null
        resetStreamingState()
        Log.d(TAG, "removeCurrentTorrent: done")
    }

    fun clearCache() {
        Log.d(TAG, "clearCache: deleting ${saveDir.absolutePath}")
        try { saveDir.listFiles()?.forEach { it.deleteRecursively() } } catch (_: Exception) {}
    }

    fun stop() {
        Log.i(TAG, "stop: shutting down engine")
        isRunning.set(false)
        pollThread?.interrupt()
        rawHandle?.let {
            try { sessionManager.swig().remove_torrent(it) }
            catch (e: Exception) { Log.w(TAG, "stop: remove_torrent failed: ${e.message}") }
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
        Log.d(TAG, "applyFilePriorities: selectedIndex=$selectedIndex, others=IGNORE")
        try {
            val ti = h.torrentFile()
            if (ti != null) {
                val fs = ti.files()
                if (fs != null) {
                    val nf = fs.numFiles()
                    Log.d(TAG, "applyFilePriorities: $nf files total")
                    for (i in 0 until nf) {
                        val prio = if (i == selectedIndex) Priority.DEFAULT else Priority.IGNORE
                        h.filePriority(i, prio)
                        if (i == selectedIndex) {
                            Log.d(TAG, "applyFilePriorities: file $i '${fs.fileName(i)}' -> DEFAULT")
                        }
                    }
                    return
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "applyFilePriorities: error, falling back to single: ${e.message}")
        }
        h.filePriority(selectedIndex, Priority.DEFAULT)
    }

    fun getLargestVideoFileIndex(): Int {
        return try {
            val ti = handle?.torrentFile() ?: return 0
            val fs = ti.files() ?: return 0
            var bestIdx = 0; var bestSize = 0L
            val videoExts = setOf("mkv", "mp4", "webm", "avi", "mov", "m4v")
            Log.d(TAG, "getLargestVideoFileIndex: scanning ${fs.numFiles()} files for video")
            for (i in 0 until fs.numFiles()) {
                val name = fs.fileName(i)
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext in videoExts) {
                    val size = fs.fileSize(i)
                    Log.d(TAG, "getLargestVideoFileIndex: file $i '$name' ($size bytes, .$ext)")
                    if (size > bestSize) { bestSize = size; bestIdx = i }
                } else {
                    Log.d(TAG, "getLargestVideoFileIndex: file $i '$name' — skipped (not video)")
                }
            }
            Log.i(TAG, "getLargestVideoFileIndex: selected=$bestIdx '${fs.fileName(bestIdx)}' ($bestSize bytes)")
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