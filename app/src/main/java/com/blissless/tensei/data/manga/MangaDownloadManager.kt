package com.blissless.tensei.data.manga

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

/**
 * Manages manga chapter downloads with concurrency limiting and safe file handling.
 *
 * Key fixes vs the original:
 * - Page file extension is extracted via URI parsing (handles query strings like `image.png?v=1`)
 * - Exposes [downloadedManga] as a reactive StateFlow so UI can observe changes
 * - Limits concurrent downloads to 2 to avoid MangaDex 429s
 * - cancelDownload cleans up partial files
 * - _activeDownloads mutations are thread-safe via Mutex
 */
class MangaDownloadManager(context: Context) {

    private val baseDir = File(context.filesDir, "manga_downloads").also { it.mkdirs() }

    private val _activeDownloads = MutableStateFlow<List<MangaDownloadTask>>(emptyList())
    val activeDownloads: StateFlow<List<MangaDownloadTask>> = _activeDownloads.asStateFlow()

    private val _downloadedManga = MutableStateFlow<List<DownloadedManga>>(emptyList())
    val downloadedManga: StateFlow<List<DownloadedManga>> = _downloadedManga.asStateFlow()

    /** Limit concurrent downloads to avoid 429s. */
    private val downloadSemaphore = Semaphore(2)

    init {
        scanDownloadedManga()
    }

    fun scanDownloadedManga(): List<DownloadedManga> {
        val result = mutableListOf<DownloadedManga>()
        val mangaDirs = baseDir.listFiles()?.filter { it.isDirectory } ?: emptyList()
        for (mangaDir in mangaDirs) {
            val metaFile = File(mangaDir, ".meta")
            if (!metaFile.exists()) continue
            val meta = metaFile.readLines().takeIf { it.size >= 3 } ?: continue
            val mangaId = meta[0].toIntOrNull() ?: continue
            val chapters = mutableListOf<DownloadedChapter>()
            val chapterDirs = mangaDir.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") } ?: emptyList()
            for (chDir in chapterDirs.sortedBy { it.name.toFloatOrNull() }) {
                val pageFiles = chDir.listFiles()
                    ?.filter { it.isFile && it.extension.lowercase() in IMAGE_EXTENSIONS }
                    ?.sortedBy { it.name }
                    ?.map { it.absolutePath } ?: continue
                if (pageFiles.isEmpty()) continue
                chapters.add(
                    DownloadedChapter(
                        chapterNumber = chDir.name.toFloatOrNull() ?: 0f,
                        chapterTitle = "Ch. ${chDir.name}",
                        pageFiles = pageFiles,
                        totalPages = pageFiles.size
                    )
                )
            }
            if (chapters.isNotEmpty()) {
                result.add(DownloadedManga(mangaId, meta[1], meta[2], chapters))
            }
        }
        _downloadedManga.value = result
        return result
    }

    fun getDownloadedMangaList(): List<DownloadedManga> = _downloadedManga.value

    fun getDownloadedChapters(mangaId: Int): List<DownloadedChapter> {
        return _downloadedManga.value.find { it.mangaId == mangaId }?.chapters ?: emptyList()
    }

    fun isChapterDownloaded(mangaId: Int, chapterNumber: Float): Boolean {
        val mangaDir = getMangaDir(mangaId)
        val chDir = File(mangaDir, chapterNumber.toString())
        return chDir.exists() && chDir.listFiles()?.any { it.extension.lowercase() in IMAGE_EXTENSIONS } == true
    }

    suspend fun startDownload(
        mangaId: Int,
        mangaTitle: String,
        coverUrl: String,
        chapterId: String,
        chapterNumber: Float,
        imageUrls: List<String>
    ) {
        if (isChapterDownloaded(mangaId, chapterNumber)) return

        val task = MangaDownloadTask(
            mangaId = mangaId,
            mangaTitle = mangaTitle,
            chapterId = chapterId,
            chapterNumber = chapterNumber,
            pageCount = imageUrls.size,
            status = MangaDownloadStatus.QUEUED
        )
        addTask(task)

        downloadSemaphore.withPermit {
            withContext(Dispatchers.IO) {
                val mangaDir = getMangaDir(mangaId)
                val chDir = File(mangaDir, chapterNumber.toString()).also { it.mkdirs() }
                val metaFile = File(mangaDir, ".meta")
                if (!metaFile.exists()) {
                    metaFile.writeText(listOf(mangaId.toString(), mangaTitle, coverUrl).joinToString("\n"))
                }
                var successCount = 0
                for ((index, imageUrl) in imageUrls.withIndex()) {
                    // Check for cancellation
                    val current = findTask(mangaId, chapterId)
                    if (current?.status == MangaDownloadStatus.CANCELLED) {
                        cleanupPartialFiles(chDir)
                        removeTask(mangaId, chapterId)
                        return@withContext
                    }

                    try {
                        val ext = extractExtension(imageUrl)
                        val pageFile = File(chDir, "page-${(index + 1).toString().padStart(4, '0')}.$ext")
                        if (!pageFile.exists()) {
                            URL(imageUrl).openStream().use { input ->
                                pageFile.outputStream().use { output -> input.copyTo(output) }
                            }
                        }
                        successCount++
                        updateTask(mangaId, chapterId) {
                            it.copy(
                                downloadedPages = successCount,
                                status = MangaDownloadStatus.DOWNLOADING
                            )
                        }
                    } catch (e: Exception) {
                        updateTask(mangaId, chapterId) {
                            it.copy(status = MangaDownloadStatus.FAILED)
                        }
                        break
                    }
                }
                if (successCount == imageUrls.size) {
                    updateTask(mangaId, chapterId) {
                        it.copy(status = MangaDownloadStatus.COMPLETED)
                    }
                    // Auto-remove completed task after a short delay so UI can show completion
                    kotlinx.coroutines.delay(2000)
                    removeTask(mangaId, chapterId)
                }
                scanDownloadedManga()
            }
        }
    }

    fun cancelDownload(mangaId: Int, chapterId: String) {
        updateTask(mangaId, chapterId) { it.copy(status = MangaDownloadStatus.CANCELLED) }
        // Clean up partial files for the chapter
        val chDir = File(getMangaDir(mangaId), findTask(mangaId, chapterId)?.chapterNumber?.toString() ?: "")
        if (chDir.exists()) cleanupPartialFiles(chDir)
    }

    fun deleteChapter(mangaId: Int, chapterNumber: Float) {
        val chDir = File(getMangaDir(mangaId), chapterNumber.toString())
        if (chDir.exists()) chDir.deleteRecursively()
        scanDownloadedManga()
    }

    fun deleteManga(mangaId: Int) {
        val mangaDir = getMangaDir(mangaId)
        if (mangaDir.exists()) mangaDir.deleteRecursively()
        scanDownloadedManga()
    }

    // ─── Internal helpers ──────────────────────────────────────────────

    private fun getMangaDir(mangaId: Int): File = File(baseDir, mangaId.toString()).also { it.mkdirs() }

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

    private fun cleanupPartialFiles(chDir: File) {
        if (chDir.exists()) {
            chDir.listFiles()?.forEach { it.delete() }
        }
    }

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

    companion object {
        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif")
    }
}
