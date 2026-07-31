package com.blissless.tensei.viewmodel

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.blissless.tensei.MainViewModel
import com.blissless.tensei.data.manga.MangaDexManager
import com.blissless.tensei.data.manga.MangaDownloadManager
import com.blissless.tensei.data.manga.MangaRepository
import com.blissless.tensei.data.manga.MangaTrackManager
import com.blissless.tensei.data.models.MangaChapter
import com.blissless.tensei.data.models.MangaDetail
import com.blissless.tensei.data.models.MangaExploreMedia
import com.blissless.tensei.data.models.MangaMedia
import com.blissless.tensei.data.models.MangaTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class InstalledExtension(val label: String, val packageName: String) {
    val authority: String get() = "$packageName.provider"
}

data class ExtensionChapter(
    val number: String,
    val title: String,
    val id: String,
    val index: Int,
    val pageCount: Int
)

// ─── Managers (lazily initialized from MainViewModel.init) ──────────
var MainViewModel.mangaRepository: MangaRepository?
    get() = _mangaRepository
    set(value) { _mangaRepository = value }
private var _mangaRepository: MangaRepository? = null

var MainViewModel.mangaTrackManager: MangaTrackManager?
    get() = _mangaTrackManager
    set(value) { _mangaTrackManager = value }
private var _mangaTrackManager: MangaTrackManager? = null

var MainViewModel.mangaDexManager: MangaDexManager?
    get() = _mangaDexManager
    set(value) { _mangaDexManager = value }
private var _mangaDexManager: MangaDexManager? = null

var MainViewModel.mangaDownloadManager: MangaDownloadManager?
    get() = _mangaDownloadManager
    set(value) { _mangaDownloadManager = value }
private var _mangaDownloadManager: MangaDownloadManager? = null

// ─── Extension State ──────────────────────────────────────────────────

private val _installedExtensions = MutableStateFlow<List<InstalledExtension>>(emptyList())
val MainViewModel.installedExtensions: StateFlow<List<InstalledExtension>> get() = _installedExtensions.asStateFlow()

private val _selectedExtensionAuthority = MutableStateFlow<String?>(null)
val MainViewModel.selectedExtensionAuthority: StateFlow<String?> get() = _selectedExtensionAuthority.asStateFlow()

fun MainViewModel.discoverExtensions() {
    val beaconIntent = Intent("com.blissless.mangaclient.EXTENSION_BEACON")
    val resolveInfoList = context.packageManager.queryBroadcastReceivers(beaconIntent, 0)
    val extensions = resolveInfoList
        .filter { info ->
            info.loadLabel(context.packageManager).toString()
                .startsWith("Oni: ", ignoreCase = true)
        }
        .map { info ->
            InstalledExtension(
                label = info.loadLabel(context.packageManager).toString(),
                packageName = info.activityInfo.packageName
            )
        }
    _installedExtensions.value = extensions
}

fun MainViewModel.selectExtension(authority: String?) {
    _selectedExtensionAuthority.value = authority
}

private suspend fun MainViewModel.fetchExtensionChapterList(mangaTitle: String): List<ExtensionChapter>? {
    val authority = _selectedExtensionAuthority.value ?: return null
    return try {
        val uri = Uri.parse("content://$authority/chapters")
            .buildUpon()
            .appendQueryParameter("manga", mangaTitle)
            .appendQueryParameter("anime", mangaTitle)
            .build()
        val cursor = context.contentResolver.query(uri, null, null, null, null) ?: return null
        cursor.use { c ->
            if (!c.moveToFirst()) return@use null
            val col = c.getColumnIndex("data")
            if (col < 0) return@use null
            val jsonData = c.getString(col)
            val json = JSONObject(jsonData)
            val chaptersArr = json.optJSONArray("chapters") ?: return@use null
            (0 until chaptersArr.length()).map { i ->
                val ch = chaptersArr.getJSONObject(i)
                ExtensionChapter(
                    number = ch.optString("number", ""),
                    title = ch.optString("title", ""),
                    id = ch.optString("id", ""),
                    index = ch.optInt("index", i),
                    pageCount = ch.optInt("pageCount", 0)
                )
            }
        }
    } catch (_: Exception) { null }
}

private fun MainViewModel.fetchExtensionChapterImages(mangaTitle: String, chapterParam: String, authority: String): List<String>? {
    return try {
        val uri = Uri.parse("content://$authority/scrape")
            .buildUpon()
            .appendQueryParameter("manga", mangaTitle)
            .appendQueryParameter("anime", mangaTitle)
            .appendQueryParameter("chapter", chapterParam)
            .build()
        val cursor = context.contentResolver.query(uri, null, null, null, null) ?: return null
        cursor.use { c ->
            if (!c.moveToFirst()) return@use null
            val col = c.getColumnIndex("data")
            if (col < 0) return@use null
            val jsonData = c.getString(col)
            val json = JSONObject(jsonData)
            val chapter = json.optJSONObject("chapter") ?: return@use null
            val imagesArr = chapter.optJSONArray("images") ?: return@use null
            (0 until imagesArr.length()).map { imagesArr.getString(it) }
        }
    } catch (_: Exception) { null }
}

// ─── State Flows ─────────────────────────────────────────────────────

private val _mangaContinueReading = MutableStateFlow<List<MangaMedia>>(emptyList())
val MainViewModel.mangaContinueReading: StateFlow<List<MangaMedia>> get() = _mangaContinueReading.asStateFlow()

private val _mangaPlanningToRead = MutableStateFlow<List<MangaMedia>>(emptyList())
val MainViewModel.mangaPlanningToRead: StateFlow<List<MangaMedia>> get() = _mangaPlanningToRead.asStateFlow()

private val _mangaCompleted = MutableStateFlow<List<MangaMedia>>(emptyList())
val MainViewModel.mangaCompleted: StateFlow<List<MangaMedia>> get() = _mangaCompleted.asStateFlow()

private val _mangaExploreSections = MutableStateFlow<Map<String, List<MangaExploreMedia>>>(emptyMap())
val MainViewModel.mangaExploreSections: StateFlow<Map<String, List<MangaExploreMedia>>> get() = _mangaExploreSections.asStateFlow()

private val _mangaDetail = MutableStateFlow<MangaDetail?>(null)
val MainViewModel.mangaDetail: StateFlow<MangaDetail?> get() = _mangaDetail.asStateFlow()

private val _mangaChapters = MutableStateFlow<List<MangaChapter>>(emptyList())
val MainViewModel.mangaChapters: StateFlow<List<MangaChapter>> get() = _mangaChapters.asStateFlow()

private val _mangaChapterImages = MutableStateFlow<List<String>?>(null)
val MainViewModel.mangaChapterImages: StateFlow<List<String>?> get() = _mangaChapterImages.asStateFlow()

private val _mangaDexId = MutableStateFlow<String?>(null)
val MainViewModel.mangaDexId: StateFlow<String?> get() = _mangaDexId.asStateFlow()

private val _isLoadingManga = MutableStateFlow(false)
val MainViewModel.isLoadingManga: StateFlow<Boolean> get() = _isLoadingManga.asStateFlow()

private val _isLoadingMangaChapters = MutableStateFlow(false)
val MainViewModel.isLoadingMangaChapters: StateFlow<Boolean> get() = _isLoadingMangaChapters.asStateFlow()

// ─── Initialization ──────────────────────────────────────────────────

fun MainViewModel.initManga() {
    _mangaRepository = MangaRepository()
    _mangaTrackManager = MangaTrackManager(context)
    _mangaDexManager = MangaDexManager()
    _mangaDownloadManager = MangaDownloadManager(context)
    loadLocalMangaTracking()
}

private fun MainViewModel.loadLocalMangaTracking() {
    val tracker = mangaTrackManager ?: return
    val reading = tracker.getContinueReading().map { toMangaMedia(it) }
    val planning = tracker.getPlanningToRead().map { toMangaMedia(it) }
    val completed = tracker.getCompleted().map { toMangaMedia(it) }
    _mangaContinueReading.value = reading
    _mangaPlanningToRead.value = planning
    _mangaCompleted.value = completed
}

private fun toMangaMedia(track: MangaTrack): MangaMedia {
    return MangaMedia(
        id = track.mangaId,
        title = track.title,
        cover = track.cover,
        progress = track.progress.toInt(),
        totalChapters = track.totalChapters,
        totalVolumes = track.totalVolumes,
        listStatus = track.status
    )
}

// ─── Search & Explore ────────────────────────────────────────────────

fun MainViewModel.searchManga(query: String, page: Int = 1, onResult: (List<MangaExploreMedia>) -> Unit = {}) {
    viewModelScope.launch {
        val results = mangaRepository?.searchManga(query, page) ?: emptyList()
        onResult(results)
    }
}

fun MainViewModel.fetchMangaExplore() {
    viewModelScope.launch {
        _isLoadingManga.value = true
        val sections = mangaRepository?.fetchExploreSections() ?: emptyMap()
        _mangaExploreSections.value = sections
        _isLoadingManga.value = false
    }
}

suspend fun MainViewModel.fetchMangaLists(): Boolean {
    val userId = _userId.value ?: return false
    val token = authToken.value ?: return false
    val lists = mangaRepository?.fetchUserMangaLists(userId, token) ?: return false
    _mangaContinueReading.value = lists["CURRENT"] ?: lists["Reading"] ?: emptyList()
    _mangaPlanningToRead.value = lists["PLANNING"] ?: lists["Plan to Read"] ?: emptyList()
    _mangaCompleted.value = lists["COMPLETED"] ?: emptyList()
    return true
}

// ─── Detail ──────────────────────────────────────────────────────────

fun MainViewModel.fetchMangaDetail(mangaId: Int) {
    viewModelScope.launch {
        _isLoadingManga.value = true
        val detail = mangaRepository?.fetchMangaDetail(mangaId)
        _mangaDetail.value = detail
        if (detail != null) {
            mangaTrackManager?.updateMangaInfo(mangaId, detail.title, detail.cover)
        }
        _isLoadingManga.value = false
    }
}

fun MainViewModel.clearMangaDetail() {
    _mangaDetail.value = null
    _mangaChapters.value = emptyList()
    _mangaChapterImages.value = null
    _mangaDexId.value = null
}

// ─── Chapters ────────────────────────────────────────────────────────

fun MainViewModel.loadMangaChapters(mangaId: Int, title: String) {
    viewModelScope.launch {
        _isLoadingMangaChapters.value = true

        val detail = _mangaDetail.value
        var chapters = emptyList<MangaChapter>()

        // Try extension first
        val extChapters = fetchExtensionChapterList(title)
        if (extChapters != null && extChapters.isNotEmpty()) {
            chapters = extChapters.sortedBy { it.index }.mapIndexed { idx, ch ->
                MangaChapter(
                    url = "ext:${ch.id}",
                    title = if (ch.title.isNotBlank()) "Chapter ${ch.number}: ${ch.title}" else "Chapter ${ch.number}",
                    chapterId = ch.id,
                    chapterNumber = ch.number.toFloatOrNull() ?: idx.toFloat()
                )
            }
        } else {
            // Fallback to MangaDex
            val mangaDexId = mangaDexManager?.findMangaByAniListId(title, mangaId)
            _mangaDexId.value = mangaDexId
            if (mangaDexId != null) {
                val aggregate = mangaDexManager?.fetchAggregate(mangaDexId)
                chapters = mangaDexManager?.buildChapterList(aggregate) ?: emptyList()
            }

            if (chapters.isEmpty() && detail != null) {
                chapters = (1..detail.chapters).map { i ->
                    MangaChapter(
                        url = "",
                        title = "Ch. $i",
                        chapterId = "",
                        chapterNumber = i.toFloat()
                    )
                }
            }

            if (mangaDexId != null) {
                mangaTrackManager?.updateMangaDexId(mangaId, mangaDexId)
            }
        }

        _mangaChapters.value = chapters

        mangaTrackManager?.updateTotalChapters(
            mangaId,
            chapters.size,
            detail?.volumes
        )

        _isLoadingMangaChapters.value = false
    }
}

// ─── Chapter Images ──────────────────────────────────────────────────

fun MainViewModel.loadChapterImages(chapterId: String, useDataSaver: Boolean = false, mangaTitle: String? = null) {
    viewModelScope.launch {
        _mangaChapterImages.value = null
        if (chapterId.isEmpty()) {
            _mangaChapterImages.value = emptyList()
            return@launch
        }

        val images = if (chapterId.startsWith("ext:") && mangaTitle != null) {
            val authority = _selectedExtensionAuthority.value
            if (authority != null) {
                val id = chapterId.removePrefix("ext:")
                withContext(Dispatchers.IO) {
                    fetchExtensionChapterImages(mangaTitle, id, authority)
                }
            } else null
        } else {
            withContext(Dispatchers.IO) {
                mangaDexManager?.fetchChapterImages(chapterId, useDataSaver)
            }
        }

        _mangaChapterImages.value = images
    }
}

fun MainViewModel.clearChapterImages() {
    _mangaChapterImages.value = null
}

// ─── Tracking ────────────────────────────────────────────────────────

fun MainViewModel.markMangaChapterRead(mangaId: Int, chapter: MangaChapter) {
    mangaTrackManager?.markChapterComplete(mangaId, chapter)
    loadLocalMangaTracking()
}

fun MainViewModel.updateMangaProgress(mangaId: Int, progress: Float) {
    mangaTrackManager?.updateChapterProgress(mangaId, progress)
    loadLocalMangaTracking()
}

fun MainViewModel.updateMangaScrollProgress(mangaId: Int, scrollProgress: Float) {
    mangaTrackManager?.updateScrollProgress(mangaId, scrollProgress)
}

fun MainViewModel.updateMangaStatus(mangaId: Int, status: String) {
    mangaTrackManager?.updateTrackingStatus(mangaId, status)
    loadLocalMangaTracking()

    viewModelScope.launch {
        val token = authToken.value
        if (token != null) {
            mangaRepository?.updateMangaStatus(mangaId, status, token)
        }
    }
}

fun MainViewModel.removeMangaTracking(mangaId: Int) {
    mangaTrackManager?.removeTrack(mangaId)
    loadLocalMangaTracking()
}

// ─── Downloads ───────────────────────────────────────────────────────

fun MainViewModel.downloadMangaChapter(mangaId: Int, mangaTitle: String, coverUrl: String, chapter: MangaChapter, imageUrls: List<String>) {
    viewModelScope.launch {
        mangaDownloadManager?.startDownload(
            mangaId = mangaId,
            mangaTitle = mangaTitle,
            coverUrl = coverUrl,
            chapterId = chapter.chapterId,
            chapterNumber = chapter.chapterNumber,
            imageUrls = imageUrls
        )
    }
}

fun MainViewModel.cancelMangaDownload(mangaId: Int, chapterId: String) {
    mangaDownloadManager?.cancelDownload(mangaId, chapterId)
}

fun MainViewModel.deleteMangaChapter(mangaId: Int, chapterNumber: Float) {
    mangaDownloadManager?.deleteChapter(mangaId, chapterNumber)
}

fun MainViewModel.deleteMangaDownload(mangaId: Int) {
    mangaDownloadManager?.deleteManga(mangaId)
}

fun MainViewModel.isChapterDownloaded(mangaId: Int, chapterNumber: Float): Boolean {
    return mangaDownloadManager?.isChapterDownloaded(mangaId, chapterNumber) ?: false
}


