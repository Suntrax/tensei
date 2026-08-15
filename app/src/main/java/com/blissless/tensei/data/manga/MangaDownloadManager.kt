package com.blissless.tensei.data.manga

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import java.net.URL

data class MangaDownloadTask(
    val mangaId: Int,
    val mangaTitle: String,
    val chapterId: String,
    val chapterNumber: Float,
    val pageCount: Int = 0,
    val downloadedPages: Int = 0,
    val status: MangaDownloadStatus = MangaDownloadStatus.QUEUED
)

enum class MangaDownloadStatus {
    QUEUED, DOWNLOADING, COMPLETED, FAILED, CANCELLED
}

data class DownloadedManga(
    val mangaId: Int,
    val mangaTitle: String,
    val coverUrl: String,
    val chapters: List<DownloadedChapter>
)

data class DownloadedChapter(
    val chapterNumber: Float,
    val chapterTitle: String,
    val pageFiles: List<String>,
    val totalPages: Int
)

/** One chapter queued for a batch download, with its already-scraped image URLs. */
data class MangaChapterDownload(
    val chapterId: String,
    val chapterNumber: Float,
    val imageUrls: List<String>
)

/**
 * Live progress of a multi-chapter batch download. Mirrors oni's BatchDownloadState so the
 * download dialog can render per-chapter status (completed/failed/downloading) while running.
 */
data class MangaBatchDownloadState(
    val mangaId: Int,
    val mangaTitle: String,
    val totalChapters: Int,
    val completedChapters: Int = 0,
    val failedChapters: Int = 0,
    val currentIndex: Int = -1,
    val isComplete: Boolean = false,
    val currentChapterNumber: Float? = null,
    val completedNumbers: Set<Float> = emptySet(),
    val failedNumbers: Set<Float> = emptySet()
)

/**
 * Manages manga chapter downloads with concurrency limiting and safe file handling.
 *
 * Downloads use the oni on-disk layout so chapters downloaded by the oni app before the
 * merge are picked up automatically, and anything downloaded here stays readable by oni:
 *
 * ```
 * <location>/<manga-title-slug>/chapter-<number>/page-<NNN>.<ext>
 * <location>/blue-lock/chapter-1.0/page-001.jpg
 * ```
 *
 * The manga folder is keyed by a slug of the title (e.g. "blue-lock"), NOT the AniList id.
 * No `.meta` file is written; identity (mangaId/title/cover) is resolved from the local
 * tracking via [titleResolver] so already-downloaded chapters map back to the manga in UI.
 *
 * Downloads can go to app-internal storage (default) or any folder the user grants via SAF.
 * Changing the location ([setDownloadLocation]) rescans the new folder so previously
 * downloaded chapters show up immediately.
 */
class MangaDownloadManager(
    context: Context,
    /**
     * Resolves a manga title slug (folder name like "blue-lock") to (mangaId, title, coverUrl).
     * Returns null when unknown — the entry then keeps a placeholder id 0, the humanized title
     * and an empty cover.
     */
    private val titleResolver: ((titleSlug: String) -> Triple<Int, String, String>?)? = null
) {

    private val appContext = context.applicationContext
    private val defaultBaseDir = File(appContext.filesDir, "manga_downloads").also { it.mkdirs() }

    private var storage: MangaDownloadStorage = FileStorage(defaultBaseDir, titleResolver)

    private val _activeDownloads = MutableStateFlow<List<MangaDownloadTask>>(emptyList())
    val activeDownloads: StateFlow<List<MangaDownloadTask>> = _activeDownloads.asStateFlow()

    private val _downloadedManga = MutableStateFlow<List<DownloadedManga>>(emptyList())
    val downloadedManga: StateFlow<List<DownloadedManga>> = _downloadedManga.asStateFlow()

    /**
     * True when the last scan of the selected location failed because the app lost access
     * to it (a SAF tree whose persistable grant vanished after reinstall). The Downloads
     * screen shows a "re-select folder" prompt instead of an empty list in that case.
     */
    private val _locationPermissionDenied = MutableStateFlow(false)
    val locationPermissionDenied: StateFlow<Boolean> = _locationPermissionDenied.asStateFlow()

    private val _batchDownloadState = MutableStateFlow<MangaBatchDownloadState?>(null)
    val batchDownloadState: StateFlow<MangaBatchDownloadState?> = _batchDownloadState.asStateFlow()

    /** Dedicated scope for batch downloads so they can be cancelled independently. */
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var batchJob: Job? = null

    /** Dedicated scope for filesystem scans. The scan walks every manga/chapter folder (for SAF
     *  this is hundreds of ContentResolver IPC round-trips), so it must never run on the main
     *  thread. Concurrent scans are collapsed: starting a new one cancels the in-flight one. */
    private val scanScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var scanJob: Job? = null

    /** Limit concurrent downloads to avoid 429s. */
    private val downloadSemaphore = Semaphore(2)

    init {
        scanDownloadedManga()
    }

    /**
     * Switches where manga downloads are stored. `null` keeps the app-internal default
     * (`filesDir/manga_downloads`). Passing a SAF tree URI stores downloads there instead.
     * The new location is scanned immediately so existing downloads appear in the UI.
     */
    fun setDownloadLocation(uri: String?) {
        storage = if (uri == null) {
            FileStorage(defaultBaseDir, titleResolver)
        } else {
            try {
                SafStorage(appContext, Uri.parse(uri), titleResolver)
            } catch (e: Exception) {
                FileStorage(defaultBaseDir, titleResolver)
            }
        }
        scanDownloadedManga()
    }

    /**
     * Re-scans the current download location on a background thread (the walk touches every
     * manga/chapter folder; for SAF storage that's a ContentResolver IPC per folder, which must
     * never happen on the main thread). If a scan is already running it is superseded — callers
     * like the batch downloader trigger one after every chapter, and a slow scan in the middle
     * of that would otherwise pile up. The result lands in [downloadedManga] a moment later.
     */
    fun scanDownloadedManga(): List<DownloadedManga> {
        scanJob?.cancel()
        scanJob = scanScope.launch {
            val result = storage.scan()
            _downloadedManga.value = result.manga
            _locationPermissionDenied.value = result.permissionDenied
            android.util.Log.d("MangaDownload", "scanDownloadedManga: ${result.manga.size} manga, chapters=${result.manga.sumOf { it.chapters.size }}, permissionDenied=${result.permissionDenied}")
        }
        return _downloadedManga.value
    }

    fun getDownloadedMangaList(): List<DownloadedManga> = _downloadedManga.value

    fun getDownloadedChapters(mangaId: Int): List<DownloadedChapter> {
        return _downloadedManga.value.find { it.mangaId == mangaId }?.chapters ?: emptyList()
    }

    fun isChapterDownloaded(mangaId: Int, chapterNumber: Float): Boolean {
        val title = titleForMangaId(mangaId) ?: return false
        return storage.isChapterDownloaded(slugifyTitle(title), chapterNumber)
    }

    suspend fun startDownload(
        mangaId: Int,
        mangaTitle: String,
        coverUrl: String,
        chapterId: String,
        chapterNumber: Float,
        imageUrls: List<String>
    ) {
        if (storage.isChapterDownloaded(slugifyTitle(mangaTitle), chapterNumber)) return
        downloadSemaphore.withPermit {
            performChapterDownload(mangaId, mangaTitle, coverUrl, chapterId, chapterNumber, imageUrls)
        }
    }

    /**
     * Download a list of chapters sequentially, publishing progress via [batchDownloadState].
     * Runs on an internal scope so it survives (and can be cancelled independently of) the
     * caller's lifecycle. Skipping already-downloaded chapters counts them as completed.
     */
    fun startBatchDownload(
        mangaId: Int,
        mangaTitle: String,
        coverUrl: String,
        chapters: List<MangaChapterDownload>
    ) {
        if (chapters.isEmpty()) return
        batchJob?.cancel()
        val targets = chapters.distinctBy { it.chapterNumber }
        batchJob = scope.launch {
            try {
                var completed = 0
                var failed = 0
                val completedNums = mutableSetOf<Float>()
                val failedNums = mutableSetOf<Float>()

                for ((index, ch) in targets.withIndex()) {
                    _batchDownloadState.value = MangaBatchDownloadState(
                        mangaId = mangaId,
                        mangaTitle = mangaTitle,
                        totalChapters = targets.size,
                        completedChapters = completed,
                        failedChapters = failed,
                        currentIndex = index,
                        isComplete = false,
                        currentChapterNumber = ch.chapterNumber,
                        completedNumbers = completedNums.toSet(),
                        failedNumbers = failedNums.toSet()
                    )

                    val ok = performChapterDownload(
                        mangaId = mangaId,
                        mangaTitle = mangaTitle,
                        coverUrl = coverUrl,
                        chapterId = ch.chapterId,
                        chapterNumber = ch.chapterNumber,
                        imageUrls = ch.imageUrls
                    )
                    if (ok) {
                        completed++
                        completedNums.add(ch.chapterNumber)
                    } else {
                        failed++
                        failedNums.add(ch.chapterNumber)
                    }
                }

                _batchDownloadState.value = MangaBatchDownloadState(
                    mangaId = mangaId,
                    mangaTitle = mangaTitle,
                    totalChapters = targets.size,
                    completedChapters = completed,
                    failedChapters = failed,
                    currentIndex = targets.size - 1,
                    isComplete = true,
                    completedNumbers = completedNums.toSet(),
                    failedNumbers = failedNums.toSet()
                )

                // Give the dialog a moment to show the completion state, then clear it.
                delay(5000)
                _batchDownloadState.value = null
            } catch (e: CancellationException) {
                _batchDownloadState.value = null
                throw e
            }
        }
    }

    fun cancelBatchDownload() {
        batchJob?.cancel()
        batchJob = null
        _batchDownloadState.value = null
    }

    fun cancelDownload(mangaId: Int, chapterId: String) {
        updateTask(mangaId, chapterId) { it.copy(status = MangaDownloadStatus.CANCELLED) }
        // Clean up partial files for the chapter
        val task = findTask(mangaId, chapterId)
        val chapterNumber = task?.chapterNumber
        if (chapterNumber != null) storage.cleanupChapter(slugifyTitle(task.mangaTitle), chapterNumber)
    }

    fun deleteChapter(mangaId: Int, chapterNumber: Float) {
        val title = titleForMangaId(mangaId) ?: return
        storage.deleteChapter(slugifyTitle(title), chapterNumber)
        scanDownloadedManga()
    }

    fun deleteManga(mangaId: Int) {
        val title = titleForMangaId(mangaId) ?: return
        storage.deleteManga(slugifyTitle(title))
        scanDownloadedManga()
    }

    // ─── Internal helpers ──────────────────────────────────────────────

    /** Resolves the display title of a manga from the last scan by its AniList id. */
    private fun titleForMangaId(mangaId: Int): String? =
        _downloadedManga.value.find { it.mangaId == mangaId }?.mangaTitle

    @Synchronized
    private fun addTask(task: MangaDownloadTask) {
        _activeDownloads.value = _activeDownloads.value + task
    }

    @Synchronized
    private fun removeTask(mangaId: Int, chapterId: String) {
        _activeDownloads.value = _activeDownloads.value.filterNot { it.mangaId == mangaId && it.chapterId == chapterId }
    }

    @Synchronized
    private fun updateTask(mangaId: Int, chapterId: String, transform: (MangaDownloadTask) -> MangaDownloadTask) {
        _activeDownloads.value = _activeDownloads.value.map {
            if (it.mangaId == mangaId && it.chapterId == chapterId) transform(it) else it
        }
    }

    @Synchronized
    private fun findTask(mangaId: Int, chapterId: String): MangaDownloadTask? {
        return _activeDownloads.value.find { it.mangaId == mangaId && it.chapterId == chapterId }
    }

    /**
     * Downloads one chapter's pages to `<location>/<slug>/chapter-<number>/`.
     * Returns true when the chapter is fully downloaded (already-downloaded chapters count
     * as success). Marks the active task COMPLETED/FAILED as appropriate and removes the
     * completed task after a short delay so the UI can show the completion state.
     */
    private suspend fun performChapterDownload(
        mangaId: Int,
        mangaTitle: String,
        coverUrl: String,
        chapterId: String,
        chapterNumber: Float,
        imageUrls: List<String>
    ): Boolean {
        if (imageUrls.isEmpty()) return false
        val slug = slugifyTitle(mangaTitle)
        if (storage.isChapterDownloaded(slug, chapterNumber)) return true

        val task = MangaDownloadTask(
            mangaId = mangaId,
            mangaTitle = mangaTitle,
            chapterId = chapterId,
            chapterNumber = chapterNumber,
            pageCount = imageUrls.size,
            status = MangaDownloadStatus.QUEUED
        )
        addTask(task)

        val success = withContext(Dispatchers.IO) {
            storage.writeChapterPages(
                slug = slug,
                mangaTitle = mangaTitle,
                coverUrl = coverUrl,
                chapterNumber = chapterNumber,
                imageUrls = imageUrls,
                isCancelled = { findTask(mangaId, chapterId)?.status == MangaDownloadStatus.CANCELLED }
            )
        }

        if (success) {
            updateTask(mangaId, chapterId) {
                it.copy(
                    downloadedPages = it.pageCount,
                    status = MangaDownloadStatus.COMPLETED
                )
            }
            scanDownloadedManga()
            // Auto-remove completed task after a short delay so UI can show completion
            delay(2000)
            removeTask(mangaId, chapterId)
            return true
        } else {
            val wasCancelled = findTask(mangaId, chapterId)?.status == MangaDownloadStatus.CANCELLED
            if (wasCancelled) {
                removeTask(mangaId, chapterId)
            } else {
                updateTask(mangaId, chapterId) {
                    it.copy(status = MangaDownloadStatus.FAILED)
                }
            }
            return false
        }
    }
}

/**
 * Result of a storage scan. [manga] lists what was found; [permissionDenied] is true when
 * the location could not be read at all (e.g. a SAF tree whose persistable grant was lost
 * after an uninstall/reinstall). The UI uses it to prompt for re-selection instead of just
 * showing an empty list.
 */
private data class MangaScanResult(
    val manga: List<DownloadedManga> = emptyList(),
    val permissionDenied: Boolean = false
)

/**
 * Storage abstraction for downloaded manga chapters. Implementations back either
 * app-internal files or a user-granted SAF document tree. Both use the oni layout:
 * `<manga-title-slug>/chapter-<number>/page-<NNN>.<ext>`.
 */
private interface MangaDownloadStorage {
    val titleResolver: ((String) -> Triple<Int, String, String>?)?
    fun scan(): MangaScanResult
    fun isChapterDownloaded(slug: String, chapterNumber: Float): Boolean
    suspend fun writeChapterPages(
        slug: String,
        mangaTitle: String,
        coverUrl: String,
        chapterNumber: Float,
        imageUrls: List<String>,
        isCancelled: () -> Boolean
    ): Boolean
    fun deleteChapter(slug: String, chapterNumber: Float)
    fun deleteManga(slug: String)
    fun cleanupChapter(slug: String, chapterNumber: Float)
}

private class FileStorage(
    private val root: File,
    override val titleResolver: ((String) -> Triple<Int, String, String>?)?
) : MangaDownloadStorage {

    private fun getMangaDir(slug: String): File = File(root, slug).also { it.mkdirs() }

    private fun getChapterDir(slug: String, chapterNumber: Float): File =
        File(getMangaDir(slug), "chapter-${chapterNumber.toString()}")

    override fun scan(): MangaScanResult {
        val result = mutableListOf<DownloadedManga>()
        val mangaDirs = root.listFiles()?.filter { it.isDirectory } ?: emptyList()
        for (mangaDir in mangaDirs) {
            val slug = mangaDir.name
            val resolved = titleResolver?.invoke(slug)
            val chapters = mutableListOf<DownloadedChapter>()
            val chapterDirs = mangaDir.listFiles()
                ?.filter { it.isDirectory && !it.name.startsWith(".") }
                ?.mapNotNull { dir -> parseChapterNumber(dir.name)?.let { it to dir } }
                ?.sortedBy { it.first }
                ?: emptyList()
            for ((chapterNumber, chDir) in chapterDirs) {
                val pageFiles = chDir.listFiles()
                    ?.filter { it.isFile && it.extension.lowercase() in IMAGE_EXTENSIONS }
                    ?.sortedBy { it.name }
                    ?.map { it.absolutePath } ?: continue
                if (pageFiles.isEmpty()) continue
                chapters.add(
                    DownloadedChapter(
                        chapterNumber = chapterNumber,
                        chapterTitle = "Ch. ${formatChapterNumber(chapterNumber)}",
                        pageFiles = pageFiles,
                        totalPages = pageFiles.size
                    )
                )
            }
            if (chapters.isNotEmpty()) {
                result.add(
                    DownloadedManga(
                        mangaId = resolved?.first ?: 0,
                        mangaTitle = resolved?.second ?: humanizeTitleSlug(slug),
                        coverUrl = resolved?.third ?: "",
                        chapters = chapters
                    )
                )
            }
        }
        return MangaScanResult(manga = result)
    }

    override fun isChapterDownloaded(slug: String, chapterNumber: Float): Boolean {
        val chDir = getChapterDir(slug, chapterNumber)
        return chDir.exists() && chDir.listFiles()?.any { it.extension.lowercase() in IMAGE_EXTENSIONS } == true
    }

    override suspend fun writeChapterPages(
        slug: String,
        mangaTitle: String,
        coverUrl: String,
        chapterNumber: Float,
        imageUrls: List<String>,
        isCancelled: () -> Boolean
    ): Boolean {
        val chDir = getChapterDir(slug, chapterNumber).also { it.mkdirs() }
        var successCount = 0
        for ((index, imageUrl) in imageUrls.withIndex()) {
            // Check for cancellation
            if (isCancelled()) {
                cleanupChapter(slug, chapterNumber)
                return false
            }

            try {
                val ext = extractExtension(imageUrl)
                val pageFile = File(chDir, "page-${(index + 1).toString().padStart(3, '0')}.$ext")
                if (!pageFile.exists()) {
                    URL(imageUrl).openStream().use { input ->
                        pageFile.outputStream().use { output -> input.copyTo(output) }
                    }
                }
                successCount++
            } catch (e: Exception) {
                return false
            }
        }
        return successCount == imageUrls.size
    }

    override fun deleteChapter(slug: String, chapterNumber: Float) {
        val chDir = getChapterDir(slug, chapterNumber)
        if (chDir.exists()) chDir.deleteRecursively()
    }

    override fun deleteManga(slug: String) {
        val mangaDir = getMangaDir(slug)
        if (mangaDir.exists()) mangaDir.deleteRecursively()
    }

    override fun cleanupChapter(slug: String, chapterNumber: Float) {
        val chDir = getChapterDir(slug, chapterNumber)
        if (chDir.exists()) {
            chDir.listFiles()?.forEach { it.delete() }
        }
    }
}

private class SafStorage(
    private val context: Context,
    private val treeUri: Uri,
    override val titleResolver: ((String) -> Triple<Int, String, String>?)?
) : MangaDownloadStorage {

    private val cr: android.content.ContentResolver get() = context.contentResolver

    private data class Entry(val uri: Uri, val name: String, val mime: String) {
        val isDirectory: Boolean get() = DocumentsContract.Document.MIME_TYPE_DIR == mime
    }

    private fun rootUri(): Uri =
        DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))

    private fun listChildren(dirUri: Uri): List<Entry> {
        val docId = DocumentsContract.getDocumentId(dirUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        val result = mutableListOf<Entry>()
        try {
            cr.query(childrenUri, projection, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID))
                    val name = cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME))
                    val mime = cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE))
                    result.add(Entry(DocumentsContract.buildDocumentUriUsingTree(treeUri, id), name, mime))
                }
            }
        } catch (e: SecurityException) {
            // Lost SAF permission (e.g. after reinstall) — report to caller.
            android.util.Log.e("MangaDownload", "SafStorage.listChildren: no permission for $treeUri", e)
            throw e
        } catch (e: Exception) {
            android.util.Log.e("MangaDownload", "SafStorage.listChildren failed for $treeUri", e)
        }
        return result
    }

    /** Same as [listChildren] but never throws — for callers where a failed list just means "empty". */
    private fun listChildrenSafe(dirUri: Uri): List<Entry> =
        try {
            listChildren(dirUri)
        } catch (e: Exception) {
            emptyList()
        }

    private fun findDoc(dirUri: Uri, name: String): Uri? =
        listChildrenSafe(dirUri).firstOrNull { it.name == name }?.uri

    private fun findOrCreateDir(dirUri: Uri, name: String): Uri? {
        findDoc(dirUri, name)?.let { return it }
        val docId = DocumentsContract.getDocumentId(dirUri)
        return DocumentsContract.createDocument(
            cr,
            DocumentsContract.buildDocumentUriUsingTree(treeUri, docId),
            DocumentsContract.Document.MIME_TYPE_DIR,
            name
        )
    }

    private fun findOrCreateFile(dirUri: Uri, name: String, mime: String): Uri? {
        findDoc(dirUri, name)?.let { return it }
        val docId = DocumentsContract.getDocumentId(dirUri)
        return DocumentsContract.createDocument(
            cr,
            DocumentsContract.buildDocumentUriUsingTree(treeUri, docId),
            mime,
            name
        )
    }

    private fun deleteDoc(uri: Uri) {
        try {
            DocumentsContract.deleteDocument(cr, uri)
        } catch (_: Exception) {
        }
    }

    override fun scan(): MangaScanResult {
        val result = mutableListOf<DownloadedManga>()
        val root = rootUri()
        val rootChildren = try {
            listChildren(root)
        } catch (e: SecurityException) {
            android.util.Log.e("MangaDownload", "SafStorage.scan: no permission for $treeUri", e)
            return MangaScanResult(manga = emptyList(), permissionDenied = true)
        }
        for (mangaEntry in rootChildren.filter { it.isDirectory }) {
            val slug = mangaEntry.name
            val resolved = titleResolver?.invoke(slug)
            val chapters = mutableListOf<DownloadedChapter>()
            val chapterDirs = try {
                listChildren(mangaEntry.uri)
            } catch (e: SecurityException) {
                android.util.Log.e("MangaDownload", "SafStorage.scan: no permission for ${mangaEntry.uri}", e)
                return MangaScanResult(manga = result, permissionDenied = true)
            }
                .filter { it.isDirectory && !it.name.startsWith(".") }
                .mapNotNull { entry -> parseChapterNumber(entry.name)?.let { it to entry } }
                .sortedBy { it.first }
            for ((chapterNumber, chEntry) in chapterDirs) {
                val pages = try {
                    listChildren(chEntry.uri)
                } catch (e: SecurityException) {
                    android.util.Log.e("MangaDownload", "SafStorage.scan: no permission for ${chEntry.uri}", e)
                    return MangaScanResult(manga = result, permissionDenied = true)
                }
                    .filter { !it.isDirectory && it.name.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS }
                    .sortedBy { it.name }
                if (pages.isEmpty()) continue
                chapters.add(
                    DownloadedChapter(
                        chapterNumber = chapterNumber,
                        chapterTitle = "Ch. ${formatChapterNumber(chapterNumber)}",
                        pageFiles = pages.map { it.uri.toString() },
                        totalPages = pages.size
                    )
                )
            }
            if (chapters.isNotEmpty()) {
                result.add(
                    DownloadedManga(
                        mangaId = resolved?.first ?: 0,
                        mangaTitle = resolved?.second ?: humanizeTitleSlug(slug),
                        coverUrl = resolved?.third ?: "",
                        chapters = chapters
                    )
                )
            }
        }
        return MangaScanResult(manga = result)
    }

    override fun isChapterDownloaded(slug: String, chapterNumber: Float): Boolean {
        val root = rootUri()
        val mangaDir = findDoc(root, slug) ?: return false
        val chDir = findDoc(mangaDir, "chapter-${chapterNumber.toString()}") ?: return false
        return listChildrenSafe(chDir).any {
            !it.isDirectory && it.name.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS
        }
    }

    override suspend fun writeChapterPages(
        slug: String,
        mangaTitle: String,
        coverUrl: String,
        chapterNumber: Float,
        imageUrls: List<String>,
        isCancelled: () -> Boolean
    ): Boolean {
        val root = rootUri()
        val mangaDir = findOrCreateDir(root, slug) ?: return false
        val chDir = findOrCreateDir(mangaDir, "chapter-${chapterNumber.toString()}") ?: return false
        var successCount = 0
        for ((index, imageUrl) in imageUrls.withIndex()) {
            // Check for cancellation
            if (isCancelled()) {
                cleanupChapter(slug, chapterNumber)
                return false
            }

            try {
                val ext = extractExtension(imageUrl)
                val name = "page-${(index + 1).toString().padStart(3, '0')}.$ext"
                val existing = findDoc(chDir, name)
                val pageUri = existing ?: findOrCreateFile(chDir, name, "image/$ext")
                if (pageUri == null) return false
                if (existing == null) {
                    URL(imageUrl).openStream().use { input ->
                        cr.openOutputStream(pageUri)?.use { output -> input.copyTo(output) }
                    }
                }
                successCount++
            } catch (e: Exception) {
                return false
            }
        }
        return successCount == imageUrls.size
    }

    override fun deleteChapter(slug: String, chapterNumber: Float) {
        val root = rootUri()
        val mangaDir = findDoc(root, slug) ?: return
        val chDir = findDoc(mangaDir, "chapter-${chapterNumber.toString()}") ?: return
        deleteDoc(chDir)
    }

    override fun deleteManga(slug: String) {
        val root = rootUri()
        val mangaDir = findDoc(root, slug) ?: return
        deleteDoc(mangaDir)
    }

    override fun cleanupChapter(slug: String, chapterNumber: Float) {
        val root = rootUri()
        val mangaDir = findDoc(root, slug) ?: return
        val chDir = findDoc(mangaDir, "chapter-${chapterNumber.toString()}") ?: return
        listChildrenSafe(chDir).forEach { deleteDoc(it.uri) }
    }
}

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif")

/**
 * Turns a manga title into the folder slug used on disk (matching oni): lowercase,
 * non-alphanumeric runs become a single hyphen. "Blue Lock" → "blue-lock".
 */
private fun slugifyTitle(title: String): String =
    title.lowercase().trim().replace(Regex("[^a-z0-9]+"), "-").trim('-')

/** Reverses [slugifyTitle] for display: "blue-lock" → "Blue Lock". */
private fun humanizeTitleSlug(slug: String): String =
    slug.split('-').filter { it.isNotBlank() }.joinToString(" ") { word ->
        word.replaceFirstChar { it.uppercase() }
    }

/**
 * Parses a chapter folder name like "chapter-1.0", "chapter_12" or "1.0" into a number.
 * Returns null when the name is not a chapter folder.
 */
private fun parseChapterNumber(name: String): Float? {
    val num = name.removePrefix("chapter-").removePrefix("chapter_").trim()
    return num.toFloatOrNull()
}

private fun formatChapterNumber(chapterNumber: Float): String =
    if (chapterNumber % 1f == 0f) chapterNumber.toInt().toString() else chapterNumber.toString()

/**
 * Extracts the file extension from a URL, handling query strings and fragments.
 * Example: `https://example.com/page.png?v=1` → "png"
 * Example: `https://example.com/page.jpg#fragment` → "jpg"
 * Falls back to "jpg" if no extension is found.
 */
private fun extractExtension(url: String): String {
    return try {
        // Strip query string and fragment
        val cleanUrl = url.substringBefore('?').substringBefore('#')
        val path = URI(cleanUrl).path
        val ext = path.substringAfterLast('.', "").lowercase()
        if (ext.isNotEmpty() && ext.length <= 5 && ext.matches(Regex("[a-z0-9]+"))) ext else "jpg"
    } catch (_: Exception) {
        // Fallback: simple last-dot extraction
        val clean = url.substringBefore('?').substringBefore('#')
        val ext = clean.substringAfterLast('.', "").lowercase()
        if (ext.isNotEmpty() && ext.length <= 5 && ext.matches(Regex("[a-z0-9]+"))) ext else "jpg"
    }
}
