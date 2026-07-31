package com.blissless.tensei.data.manga

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
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

class MangaDownloadManager(context: Context) {

    private val baseDir = File(context.filesDir, "manga_downloads").also { it.mkdirs() }

    private val _activeDownloads = MutableStateFlow<List<MangaDownloadTask>>(emptyList())
    val activeDownloads: StateFlow<List<MangaDownloadTask>> = _activeDownloads.asStateFlow()

    private val _downloadedManga = MutableStateFlow<List<DownloadedManga>>(emptyList())

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
                    ?.filter { it.isFile && it.extension in IMAGE_EXTENSIONS }
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
        return chDir.exists() && chDir.listFiles()?.any { it.extension in IMAGE_EXTENSIONS } == true
    }

    suspend fun startDownload(mangaId: Int, mangaTitle: String, coverUrl: String, chapterId: String, chapterNumber: Float, imageUrls: List<String>) {
        if (isChapterDownloaded(mangaId, chapterNumber)) return
        val task = MangaDownloadTask(
            mangaId = mangaId,
            mangaTitle = mangaTitle,
            chapterId = chapterId,
            chapterNumber = chapterNumber,
            pageCount = imageUrls.size,
            status = MangaDownloadStatus.QUEUED
        )
        _activeDownloads.value = _activeDownloads.value + task

        withContext(Dispatchers.IO) {
            val mangaDir = getMangaDir(mangaId)
            val chDir = File(mangaDir, chapterNumber.toString()).also { it.mkdirs() }
            val metaFile = File(mangaDir, ".meta")
            if (!metaFile.exists()) {
                metaFile.writeText(listOf(mangaId.toString(), mangaTitle, coverUrl).joinToString("\n"))
            }
            var successCount = 0
            for ((index, imageUrl) in imageUrls.withIndex()) {
                val currentTasks = _activeDownloads.value.toMutableList()
                val currentIndex = currentTasks.indexOfFirst { it.chapterId == chapterId && it.mangaId == mangaId }
                if (currentIndex < 0 || currentTasks[currentIndex].status == MangaDownloadStatus.CANCELLED) break
                try {
                    val ext = imageUrl.substringAfterLast('.', "jpg").take(4)
                    val pageFile = File(chDir, "page-${(index + 1).toString().padStart(4, '0')}.$ext")
                    if (!pageFile.exists()) {
                        URL(imageUrl).openStream().use { input ->
                            pageFile.outputStream().use { output -> input.copyTo(output) }
                        }
                    }
                    successCount++
                    currentTasks[currentIndex] = currentTasks[currentIndex].copy(
                        downloadedPages = successCount,
                        status = MangaDownloadStatus.DOWNLOADING
                    )
                    _activeDownloads.value = currentTasks
                } catch (e: Exception) {
                    currentTasks[currentIndex] = currentTasks[currentIndex].copy(status = MangaDownloadStatus.FAILED)
                    _activeDownloads.value = currentTasks
                    break
                }
            }
            if (successCount == imageUrls.size) {
                val finalTasks = _activeDownloads.value.toMutableList()
                val finIndex = finalTasks.indexOfFirst { it.chapterId == chapterId && it.mangaId == mangaId }
                if (finIndex >= 0) {
                    finalTasks[finIndex] = finalTasks[finIndex].copy(status = MangaDownloadStatus.COMPLETED)
                    _activeDownloads.value = finalTasks
                }
            }
            scanDownloadedManga()
        }
    }

    fun cancelDownload(mangaId: Int, chapterId: String) {
        val tasks = _activeDownloads.value.toMutableList()
        val index = tasks.indexOfFirst { it.chapterId == chapterId && it.mangaId == mangaId }
        if (index >= 0) {
            tasks[index] = tasks[index].copy(status = MangaDownloadStatus.CANCELLED)
            _activeDownloads.value = tasks
        }
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

    private fun getMangaDir(mangaId: Int): File = File(baseDir, mangaId.toString()).also { it.mkdirs() }

    companion object {
        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif")
    }
}
