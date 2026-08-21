package com.blissless.tensei.viewmodel

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.viewModelScope
import com.blissless.tensei.MainViewModel
import com.blissless.tensei.data.manga.DownloadedManga
import com.blissless.tensei.data.manga.MangaBatchDownloadState
import com.blissless.tensei.data.manga.MangaChapterDownload
import com.blissless.tensei.data.manga.MangaDexManager
import com.blissless.tensei.data.manga.MangaDownloadManager
import com.blissless.tensei.data.manga.MangaDownloadTask
import com.blissless.tensei.data.manga.MangaRepository
import com.blissless.tensei.data.manga.MangaTrackManager
import com.blissless.tensei.data.models.MangaActivityNode
import com.blissless.tensei.data.models.MangaChapter
import com.blissless.tensei.data.models.MangaDetail
import com.blissless.tensei.data.models.MangaExploreMedia
import com.blissless.tensei.data.models.MangaFavorite
import com.blissless.tensei.data.models.MangaMedia
import com.blissless.tensei.data.models.MangaTrack
import com.blissless.tensei.data.models.MangaCharacterNode
import com.blissless.tensei.data.models.MangaStaffEdge
import com.blissless.tensei.data.models.MangaRelation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class InstalledExtension(val label: String, val packageName: String) {
    val authority: String get() {
        if (com.blissless.tensei.extensions.ExtensionDetector.isBlisslessMangaExtension(packageName)) {
            return packageName.removeSuffix(".manga") + ".provider"
        }
        if (com.blissless.tensei.extensions.ExtensionDetector.isBlisslessStreamExtension(packageName)) {
            return packageName.removeSuffix(".anime.stream") + ".provider"
        }
        if (com.blissless.tensei.extensions.ExtensionDetector.isBlisslessTorrentExtension(packageName)) {
            return packageName.removeSuffix(".anime.torrent") + ".provider"
        }
        return "$packageName.provider"
    }
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

/**
 * Discover installed manga extensions. Detects by package name pattern
 * (com.blissless.*.manga) and falls back to beacon labels for backward compat.
 * Safe to call repeatedly.
 */
fun MainViewModel.discoverExtensions() {
    val pm = context.packageManager
    val extensions = mutableListOf<InstalledExtension>()
    val seenPackages = mutableSetOf<String>()

    // 1) Package-name-based detection for *.manga
    val installedPkgs = try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(android.content.pm.PackageManager.PackageInfoFlags.of(
                (android.content.pm.PackageManager.GET_META_DATA or android.content.pm.PackageManager.GET_CONFIGURATIONS).toLong()
            ))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(android.content.pm.PackageManager.GET_META_DATA or android.content.pm.PackageManager.GET_CONFIGURATIONS)
        }
    } catch (_: Exception) { emptyList() }
    for (pkg in installedPkgs) {
        val pkgName = pkg.packageName
        if (com.blissless.tensei.extensions.ExtensionDetector.isBlisslessMangaExtension(pkgName)) {
            seenPackages.add(pkgName)
            extensions.add(
                InstalledExtension(
                    label = com.blissless.tensei.extensions.ExtensionDetector.extensionDisplayName(pkgName),
                    packageName = pkgName
                )
            )
        }
    }

    // 2) Beacon fallback (backward compat)
    val beaconIntent = Intent("com.blissless.mangaclient.EXTENSION_BEACON")
    val resolveInfoList = context.packageManager.queryBroadcastReceivers(beaconIntent, 0)
    for (info in resolveInfoList) {
        val pkgName = info.activityInfo.packageName
        if (pkgName in seenPackages) continue
        val label = info.loadLabel(pm).toString()
        if (label.startsWith("Oni: ", ignoreCase = true) ||
            label.startsWith("Tensei: ", ignoreCase = true)) {
            extensions.add(InstalledExtension(label = label, packageName = pkgName))
        }
    }

    _installedExtensions.value = extensions.sortedBy { it.label }
}

/**
 * Select an extension as the active source for chapter lists and image fetching.
 * Persists across app restarts via UserPreferences.
 */
fun MainViewModel.selectExtension(authority: String?) {
    _selectedExtensionAuthority.value = authority
    userPreferences.setSelectedMangaExtensionAuthority(authority)
}

private fun MainViewModel.restoreExtensionSelection() {
    val saved = userPreferences.getSelectedMangaExtensionAuthority()
    if (saved != null) {
        // Migrate stale authorities from old package names (e.g. "com.blissless.x.manga.provider" -> "com.blissless.x.provider")
        val migrated = migratedAuthority(saved)
        if (migrated != saved) {
            userPreferences.setSelectedMangaExtensionAuthority(migrated)
        }
        _selectedExtensionAuthority.value = migrated
    }
}

private fun migratedAuthority(saved: String): String {
    if (!saved.endsWith(".provider")) return saved
    val withoutProvider = saved.removeSuffix(".provider")
    if (withoutProvider.endsWith(".manga")) return withoutProvider.removeSuffix(".manga") + ".provider"
    if (withoutProvider.endsWith(".anime.stream")) return withoutProvider.removeSuffix(".anime.stream") + ".provider"
    if (withoutProvider.endsWith(".anime.torrent")) return withoutProvider.removeSuffix(".anime.torrent") + ".provider"
    return saved
}

private suspend fun MainViewModel.fetchExtensionChapterList(mangaTitle: String): Pair<List<ExtensionChapter>?, Int>? {
    val authority = _selectedExtensionAuthority.value ?: return null
    if (mangaTitle.isBlank()) return null
    return withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse("content://$authority/chapters")
                .buildUpon()
                .appendQueryParameter("manga", mangaTitle)
                .appendQueryParameter("anime", mangaTitle)
                .build()
            android.util.Log.d("MangaDebug", "=== CHAPTER FETCH START ===")
            android.util.Log.d("MangaDebug", "Authority: '$authority'")
            android.util.Log.d("MangaDebug", "URI: $uri")
            android.util.Log.d("MangaDebug", "Title: '$mangaTitle'")
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            if (cursor == null) {
                android.util.Log.e("MangaDebug", "RESULT: cursor is NULL — content provider not found at authority='$authority'")
                android.util.Log.e("MangaDebug", "Check manifest: android:authorities should be '$authority'")
                return@withContext null
            }
            cursor.use { c ->
                android.util.Log.d("MangaDebug", "Cursor column count: ${c.columnCount}, column names: ${c.columnNames?.joinToString()}")
                if (!c.moveToFirst()) {
                    android.util.Log.e("MangaDebug", "RESULT: cursor has 0 rows")
                    return@withContext null
                }
                val col = c.getColumnIndex("data")
                if (col < 0) {
                    android.util.Log.e("MangaDebug", "RESULT: 'data' column not found. Available columns: ${c.columnNames?.joinToString()}")
                    return@withContext null
                }
                val jsonData = c.getString(col)
                android.util.Log.d("MangaDebug", "Raw JSON length: ${jsonData.length} chars")
                android.util.Log.d("MangaDebug", "Raw JSON (first 500): ${jsonData.take(500)}")
                val json = JSONObject(jsonData)
                if (json.has("error")) {
                    android.util.Log.e("MangaDebug", "Extension error: ${json.getString("error")}")
                    return@withContext null
                }
                android.util.Log.d("MangaDebug", "JSON keys: ${json.keys().asSequence().toList()}")
                val totalChapters = json.optInt("totalChapters", 0)
                val chaptersArr = json.optJSONArray("chapters")
                android.util.Log.d("MangaDebug", "totalChapters from JSON: $totalChapters")
                android.util.Log.d("MangaDebug", "chapters array is null: ${chaptersArr == null}")
                android.util.Log.d("MangaDebug", "chapters array length: ${chaptersArr?.length() ?: 0}")
                val chapters = mutableListOf<ExtensionChapter>()
                if (chaptersArr != null) {
                    for (i in 0 until chaptersArr.length()) {
                        val ch = chaptersArr.optJSONObject(i) ?: continue
                        chapters.add(
                            ExtensionChapter(
                                number = ch.optString("number", ""),
                                title = ch.optString("title", ""),
                                id = ch.optString("id", ""),
                                index = ch.optInt("index", i),
                                pageCount = ch.optInt("pageCount", 0)
                            )
                        )
                    }
                }
                android.util.Log.d("MangaDebug", "Parsed chapters count: ${chapters.size}")
                if (chapters.isNotEmpty()) {
                    android.util.Log.d("MangaDebug", "First chapter: number='${chapters.first().number}' title='${chapters.first().title}'")
                    android.util.Log.d("MangaDebug", "Last chapter: number='${chapters.last().number}' title='${chapters.last().title}'")
                }
                android.util.Log.d("MangaDebug", "=== CHAPTER FETCH END ===")
                Pair(chapters, totalChapters)
            }
        } catch (e: Exception) {
            android.util.Log.e("MangaDebug", "EXCEPTION: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }
}

private fun MainViewModel.fetchExtensionChapterImages(mangaTitle: String, chapterParam: String, authority: String): List<String>? {
    android.util.Log.d("MangaExt", "fetchExtensionChapterImages: title='$mangaTitle' chapter='$chapterParam' authority='$authority'")
    return try {
        val uri = Uri.parse("content://$authority/scrape")
            .buildUpon()
            .appendQueryParameter("manga", mangaTitle)
            .appendQueryParameter("anime", mangaTitle)
            .appendQueryParameter("chapter", chapterParam)
            .build()
        android.util.Log.d("MangaExt", "fetchExtensionChapterImages: querying URI=$uri")
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        if (cursor == null) {
            android.util.Log.w("MangaExt", "fetchExtensionChapterImages: cursor is null")
            return null
        }
        cursor.use { c ->
            if (!c.moveToFirst()) {
                android.util.Log.w("MangaExt", "fetchExtensionChapterImages: cursor has no rows")
                return@use null
            }
            val col = c.getColumnIndex("data")
            if (col < 0) {
                android.util.Log.w("MangaExt", "fetchExtensionChapterImages: no 'data' column in cursor")
                return@use null
            }
            val jsonData = c.getString(col)
            android.util.Log.d("MangaExt", "fetchExtensionChapterImages: raw JSON (first 200 chars): ${jsonData.take(200)}")
            val json = JSONObject(jsonData)
            if (json.has("error")) {
                android.util.Log.w("MangaExt", "fetchExtensionChapterImages: extension error: ${json.optString("error")}")
                return@use null
            }
            val chapter = json.optJSONObject("chapter") ?: run {
                android.util.Log.w("MangaExt", "fetchExtensionChapterImages: no 'chapter' object in JSON")
                return@use null
            }
            val imagesArr = chapter.optJSONArray("images") ?: run {
                android.util.Log.w("MangaExt", "fetchExtensionChapterImages: no 'images' array in chapter")
                return@use null
            }
            val images = (0 until imagesArr.length()).map { imagesArr.getString(it) }
            android.util.Log.d("MangaExt", "fetchExtensionChapterImages: got ${images.size} images, first=${images.firstOrNull()?.take(80)}")
            images
        }
    } catch (e: Exception) {
        android.util.Log.w("MangaExt", "fetchExtensionChapterImages failed: ${e.message}", e)
        null
    }
}

// ─── State Flows ─────────────────────────────────────────────────────

private val _mangaContinueReading = MutableStateFlow<List<MangaMedia>>(emptyList())
val MainViewModel.mangaContinueReading: StateFlow<List<MangaMedia>> get() = _mangaContinueReading.asStateFlow()

private val _mangaCurrentlyReading = MutableStateFlow<List<MangaMedia>>(emptyList())
val MainViewModel.mangaCurrentlyReading: StateFlow<List<MangaMedia>> get() = _mangaCurrentlyReading.asStateFlow()

private val _mangaPlanningToRead = MutableStateFlow<List<MangaMedia>>(emptyList())
val MainViewModel.mangaPlanningToRead: StateFlow<List<MangaMedia>> get() = _mangaPlanningToRead.asStateFlow()

private val _mangaCompleted = MutableStateFlow<List<MangaMedia>>(emptyList())
val MainViewModel.mangaCompleted: StateFlow<List<MangaMedia>> get() = _mangaCompleted.asStateFlow()

private val _mangaPaused = MutableStateFlow<List<MangaMedia>>(emptyList())
val MainViewModel.mangaPaused: StateFlow<List<MangaMedia>> get() = _mangaPaused.asStateFlow()

private val _mangaDropped = MutableStateFlow<List<MangaMedia>>(emptyList())
val MainViewModel.mangaDropped: StateFlow<List<MangaMedia>> get() = _mangaDropped.asStateFlow()

private val _mangaExploreSections = MutableStateFlow<Map<String, List<MangaExploreMedia>>>(emptyMap())
val MainViewModel.mangaExploreSections: StateFlow<Map<String, List<MangaExploreMedia>>> get() = _mangaExploreSections.asStateFlow()

private val _mangaDetail = MutableStateFlow<MangaDetail?>(null)
val MainViewModel.mangaDetail: StateFlow<MangaDetail?> get() = _mangaDetail.asStateFlow()

private val _mangaChapters = MutableStateFlow<List<MangaChapter>>(emptyList())
val MainViewModel.mangaChapters: StateFlow<List<MangaChapter>> get() = _mangaChapters.asStateFlow()

/**
 * The extension-derived total chapter count for the currently loaded manga (the display
 * denominator, e.g. 354 for a releasing title whose AniList entry is stale at 352).
 * 0 until a chapter list has been loaded. Used by the detail screen so its "Chapters" stats
 * match the extension count instead of the stale AniList value.
 */
private val _mangaTotalChapters = MutableStateFlow<Int>(0)
val MainViewModel.mangaTotalChapters: StateFlow<Int> get() = _mangaTotalChapters.asStateFlow()

/**
 * Chapter image loading state — null = loading, empty list = error/no source,
 * non-empty = success. Surfaces errors distinctly so the reader can render an error UI.
 */
private val _mangaChapterImages = MutableStateFlow<List<String>?>(null)
val MainViewModel.mangaChapterImages: StateFlow<List<String>?> get() = _mangaChapterImages.asStateFlow()

/** Human-readable error message when chapter images fail to load. Null = no error. */
private val _mangaChapterImagesError = MutableStateFlow<String?>(null)
val MainViewModel.mangaChapterImagesError: StateFlow<String?> get() = _mangaChapterImagesError.asStateFlow()

/**
 * Cached image URL lists keyed by chapterId. Populated by [loadChapterImages] on success and
 * by [prefetchMangaChapterImages] (which scrapes the NEXT chapter while the reader is near the
 * end of the current one). Serving from this cache makes chapter transitions — including
 * auto-advance — instant instead of waiting on a scrape round-trip.
 */
private val _mangaChapterImagesCache = MutableStateFlow<Map<String, List<String>>>(emptyMap())

/** Chapter IDs with an in-flight prefetch, to avoid duplicate scrapes. */
private val _prefetchingChapterIds = mutableSetOf<String>()

/** In-flight chapter-image load. Cancelled whenever a new chapter is requested so only the
 *  most recent load can write to [MainViewModel.mangaChapterImages] — otherwise a slower,
 *  stale fetch from a previous chapter (rapid next/prev taps) would overwrite the new one. */
private var chapterImagesJob: Job? = null

fun MainViewModel.clearMangaChapterImagesCache() {
    chapterImagesJob?.cancel()
    _mangaChapterImagesCache.value = emptyMap()
    _prefetchingChapterIds.clear()
}

private val _mangaDexId = MutableStateFlow<String?>(null)
val MainViewModel.mangaDexId: StateFlow<String?> get() = _mangaDexId.asStateFlow()

private val _mangaExtensionTitle = MutableStateFlow<String?>(null)
val MainViewModel.mangaExtensionTitle: StateFlow<String?> get() = _mangaExtensionTitle.asStateFlow()

private val _isLoadingManga = MutableStateFlow(false)
val MainViewModel.isLoadingManga: StateFlow<Boolean> get() = _isLoadingManga.asStateFlow()

private val _isLoadingMangaChapters = MutableStateFlow(false)
val MainViewModel.isLoadingMangaChapters: StateFlow<Boolean> get() = _isLoadingMangaChapters.asStateFlow()

// Tracks whether a chapter load has COMPLETED (success or failure) for the current manga.
// While false, the reader shows the loading state immediately instead of flashing the
// "No chapters found" empty state on the first frame before loadMangaChapters kicks in.
private val _hasLoadedMangaChapters = MutableStateFlow(false)
val MainViewModel.hasLoadedMangaChapters: StateFlow<Boolean> get() = _hasLoadedMangaChapters.asStateFlow()

// Profile state — real AniList favorites and activity, not faked from tracking lists
private val _mangaFavorites = MutableStateFlow<List<MangaFavorite>>(emptyList())
val MainViewModel.mangaFavorites: StateFlow<List<MangaFavorite>> get() = _mangaFavorites.asStateFlow()

private val _mangaActivity = MutableStateFlow<List<MangaActivityNode>>(emptyList())
val MainViewModel.mangaActivity: StateFlow<List<MangaActivityNode>> get() = _mangaActivity.asStateFlow()

private val _mangaUserProfile = MutableStateFlow<com.blissless.tensei.data.models.MangaUserProfile?>(null)
val MainViewModel.mangaUserProfile: StateFlow<com.blissless.tensei.data.models.MangaUserProfile?> get() = _mangaUserProfile.asStateFlow()

/** Track-set of manga the current user has favorited on AniList (for heart-icon state). */
private val _favoritedMangaIds = MutableStateFlow<Set<Int>>(emptySet())
val MainViewModel.favoritedMangaIds: StateFlow<Set<Int>> get() = _favoritedMangaIds.asStateFlow()

// ─── Initialization ──────────────────────────────────────────────────

fun MainViewModel.initManga() {
    _mangaRepository = MangaRepository()
    _mangaTrackManager = MangaTrackManager(context)
    _mangaDexManager = MangaDexManager()
    _mangaDownloadManager = MangaDownloadManager(context) { slug ->
        mangaTrackManager?.getAllTracking()?.firstOrNull { slugify(it.title) == slug }
            ?.let { Triple(it.mangaId, it.title, it.cover) }
    }
    loadLocalMangaTracking()
    // Discover installed extensions on every init — cheap and surfaces new installs.
    discoverExtensions()
    // Restore the user's previously-selected extension authority (if any).
    restoreExtensionSelection()
}

private fun MainViewModel.loadLocalMangaTracking() {
    val tracker = mangaTrackManager ?: return
    val reading = tracker.getContinueReading().map { toMangaMedia(it) }
    val currentlyReading = tracker.getCurrentlyReading().map { toMangaMedia(it) }
    val planning = tracker.getPlanningToRead().map { toMangaMedia(it) }
    val completed = tracker.getCompleted().map { toMangaMedia(it) }
    val paused = tracker.getPaused().map { toMangaMedia(it) }
    val dropped = tracker.getDropped().map { toMangaMedia(it) }
    _mangaContinueReading.value = reading
    _mangaCurrentlyReading.value = currentlyReading
    _mangaPlanningToRead.value = planning
    _mangaCompleted.value = completed
    _mangaPaused.value = paused
    _mangaDropped.value = dropped
}

private fun toMangaMedia(track: MangaTrack): MangaMedia {
    return MangaMedia(
        id = track.mangaId,
        title = track.title,
        cover = track.cover,
        progress = track.progress.toInt(),
        totalChapters = track.totalChapters,
        totalVolumes = track.totalVolumes,
        listStatus = track.status,
        scrollProgress = track.scrollProgress,
        currentChapterPages = track.currentChapterPages,
        userScore = track.score
    )
}

// ─── Search & Explore ────────────────────────────────────────────────

/**
 * Basic manga search — kept for backward compat. Returns results via callback.
 * For new UI, prefer [searchMangaAdvanced] which returns via a StateFlow.
 */
fun MainViewModel.searchManga(query: String, page: Int = 1, onResult: (List<MangaExploreMedia>) -> Unit = {}) {
    viewModelScope.launch {
        val results = mangaRepository?.searchManga(query, page) ?: emptyList()
        onResult(results)
    }
}

/**
 * Advanced manga search with genre/format/status/sort filters. Returns results synchronously
 * (caller should call from a coroutine scope).
 */
suspend fun MainViewModel.searchMangaAdvanced(
    search: String?,
    genres: List<String> = emptyList(),
    format: String? = null,
    status: String? = null,
    sort: String = "SEARCH_MATCH",
    page: Int = 1,
    perPage: Int = 30
): List<MangaExploreMedia> {
    return mangaRepository?.searchMangaAdvanced(search, genres, format, status, sort, page, perPage) ?: emptyList()
}

suspend fun MainViewModel.fetchMangaExplore() {
    android.util.Log.d("MangaExplore", "fetchMangaExplore: start, mangaRepository=${mangaRepository != null}")
    _isLoadingManga.value = true
    // Pass the auth token when available — AniList may treat authenticated requests differently
    // during rate-limiting/outages (HTTP 403 "API temporarily disabled").
    val token = authToken.value
    val sections = mangaRepository?.fetchExploreSections(token) ?: run {
        android.util.Log.w("MangaExplore", "fetchMangaExplore: mangaRepository is null or fetchExploreSections returned null")
        emptyMap()
    }
    android.util.Log.d("MangaExplore", "fetchMangaExplore: got ${sections.size} sections, keys=${sections.keys}")
    // Only overwrite existing data with a successful fetch — never wipe cached sections
    // with an empty response (network hiccup or API outage).
    if (sections.isNotEmpty()) {
        _mangaExploreSections.value = sections
        cacheManager.saveMangaExploreToCache(sections)
    }
    _isLoadingManga.value = false
}

/**
 * Restore the persisted manga explore sections so the Explore screen shows content
 * immediately on startup instead of waiting for a fresh fetch.
 */
fun MainViewModel.restoreMangaExploreFromCache() {
    val cached = cacheManager.loadMangaExploreFromCache()
    if (!cached.isNullOrEmpty()) {
        _mangaExploreSections.value = cached
    }
}

suspend fun MainViewModel.fetchMangaLists(): Boolean {
    val userId = _userId.value ?: return false
    val token = authToken.value ?: return false
    val lists = mangaRepository?.fetchUserMangaLists(userId, token) ?: return false

    // Merge AniList data with local tracks instead of blindly overwriting.
    // This prevents locally-tracked manga from disappearing when AniList returns
    // an empty response (network hiccup, silent parse failure, or user has
    // local-only tracks not yet synced to AniList).
    val anilistCurrent = lists["CURRENT"] ?: lists["Reading"]
    val anilistPlanning = lists["PLANNING"] ?: lists["Plan to Read"]
    val anilistCompleted = lists["COMPLETED"]
    val anilistPaused = lists["PAUSED"]
    val anilistDropped = lists["DROPPED"]

    // Build a merged list: start with local tracks, then add/update from AniList
    val localTracker = mangaTrackManager
    if (localTracker != null) {
        // Sync AniList entries into local tracking
        anilistCurrent?.forEach { m ->
            localTracker.ensureTrack(m.id, m.title, m.cover, m.totalChapters)
            localTracker.updateTrackingStatus(m.id, "CURRENT")
            // Never downgrade local progress: a stale AniList response (push still in
            // flight, or a network hiccup) must not roll back chapters the user just read.
            if (m.progress > 0) localTracker.updateChapterProgressKeepMax(m.id, m.progress.toFloat())
            if (m.userScore != null) localTracker.updateScore(m.id, m.userScore)
        }
        anilistPlanning?.forEach { m ->
            localTracker.ensureTrack(m.id, m.title, m.cover, m.totalChapters)
            localTracker.updateTrackingStatus(m.id, "PLANNING")
            if (m.userScore != null) localTracker.updateScore(m.id, m.userScore)
        }
        anilistCompleted?.forEach { m ->
            localTracker.ensureTrack(m.id, m.title, m.cover, m.totalChapters)
            localTracker.updateTrackingStatus(m.id, "COMPLETED")
            if (m.userScore != null) localTracker.updateScore(m.id, m.userScore)
        }
        anilistPaused?.forEach { m ->
            localTracker.ensureTrack(m.id, m.title, m.cover, m.totalChapters)
            localTracker.updateTrackingStatus(m.id, "PAUSED")
            if (m.userScore != null) localTracker.updateScore(m.id, m.userScore)
        }
        anilistDropped?.forEach { m ->
            localTracker.ensureTrack(m.id, m.title, m.cover, m.totalChapters)
            localTracker.updateTrackingStatus(m.id, "DROPPED")
            if (m.userScore != null) localTracker.updateScore(m.id, m.userScore)
        }
    }

    // Reload from local (which now includes both local-only and AniList-synced tracks)
    loadLocalMangaTracking()
    return true
}

// ─── Profile: real AniList favorites & activity ──────────────────────

/**
 * Fetch the user's manga favorites + activity + profile stats from AniList.
 * Replaces the previous "fake favorites = union of tracking lists" approach.
 */
fun MainViewModel.fetchMangaUserProfile() {
    val token = authToken.value ?: return
    viewModelScope.launch {
        val userId = _userId.value
        val favoritesDeferred = async { mangaRepository?.fetchUserMangaFavorites(token) ?: emptyList() }
        val profileDeferred = async { mangaRepository?.fetchMangaUserProfile(token) }
        val activityDeferred = async {
            if (userId != null) mangaRepository?.fetchUserMangaActivity(userId, token) ?: emptyList()
            else emptyList()
        }
        val favorites = favoritesDeferred.await()
        val profile = profileDeferred.await()
        val activity = activityDeferred.await()
        _mangaFavorites.value = favorites
        _favoritedMangaIds.value = favorites.map { it.id }.toSet()
        _mangaUserProfile.value = profile
        _mangaActivity.value = activity
    }
}

/**
 * Toggle the manga favorite state for the given AniList media ID.
 * Updates the local favorited-ids set immediately for responsive UI, then calls AniList.
 */
fun MainViewModel.toggleMangaFavorite(mangaId: Int) {
    val token = authToken.value ?: return
    // Optimistic update
    val current = _favoritedMangaIds.value
    _favoritedMangaIds.value = if (mangaId in current) current - mangaId else current + mangaId
    viewModelScope.launch {
        val ok = mangaRepository?.toggleMangaFavorite(mangaId, token) ?: false
        if (!ok) {
            // Revert on failure
            _favoritedMangaIds.value = current
        } else {
            // Refresh the favorites list to stay in sync
            val refreshed = mangaRepository?.fetchUserMangaFavorites(token) ?: emptyList()
            _mangaFavorites.value = refreshed
            _favoritedMangaIds.value = refreshed.map { it.id }.toSet()
        }
    }
}

/** Returns true if the given manga is currently favorited on AniList. */
fun MainViewModel.isMangaFavorited(mangaId: Int): Boolean = mangaId in _favoritedMangaIds.value

// ─── Detail ──────────────────────────────────────────────────────────

suspend fun MainViewModel.fetchMangaDetail(mangaId: Int) {
    android.util.Log.d("MangaDetail", "fetchMangaDetail: START mangaId=$mangaId")
    _isLoadingManga.value = true
    val token = authToken.value
    val detail = mangaRepository?.fetchMangaDetail(mangaId, token)
    _mangaDetail.value = detail
    if (detail != null) {
        mangaTrackManager?.updateMangaInfo(mangaId, detail.title, detail.cover)
        android.util.Log.d("MangaDetail", "fetchMangaDetail: SUCCESS mangaId=$mangaId title='${detail.title}' " +
            "desc=${detail.description != null} genres=${detail.genres.size} tags=${detail.tags.size} " +
            "chars=${detail.characters?.nodes?.size ?: 0} staff=${detail.staff?.edges?.size ?: 0} " +
            "relations=${detail.relations.size} recs=${detail.recommendations.size} " +
            "popularity=${detail.popularity} favourites=${detail.favourites} year=${detail.year} " +
            "format=${detail.format} source=${detail.source} volumes=${detail.volumes} " +
            "rankings=${detail.rankings.size} externalLinks=${detail.externalLinks.size}")
    } else {
        android.util.Log.w("MangaDetail", "fetchMangaDetail: FAILED (null) mangaId=$mangaId — detail screen will fall back to shallow MangaMedia.asDetail()")
    }
    _isLoadingManga.value = false
    android.util.Log.d("MangaDetail", "fetchMangaDetail: END mangaId=$mangaId isLoadingManga=${_isLoadingManga.value}")
}

fun MainViewModel.clearMangaDetail() {
    _mangaDetail.value = null
    _mangaChapters.value = emptyList()
    _mangaTotalChapters.value = 0
    _mangaChapterImages.value = null
    _mangaChapterImagesError.value = null
    _mangaDexId.value = null
    _mangaExtensionTitle.value = null
    _hasLoadedMangaChapters.value = false
}

suspend fun MainViewModel.fetchMangaAllCharacters(mangaId: Int): List<MangaCharacterNode> =
    mangaRepository?.fetchMangaAllCharacters(mangaId) ?: emptyList()

suspend fun MainViewModel.fetchMangaAllStaff(mangaId: Int): List<MangaStaffEdge> =
    mangaRepository?.fetchMangaAllStaff(mangaId) ?: emptyList()

suspend fun MainViewModel.fetchMangaAllRelations(mangaId: Int): List<MangaRelation> =
    mangaRepository?.fetchMangaAllRelations(mangaId) ?: emptyList()

suspend fun MainViewModel.fetchMangaAllRecommendations(mangaId: Int): List<MangaMedia> =
    mangaRepository?.fetchMangaAllRecommendations(mangaId) ?: emptyList()

// ─── Chapters ────────────────────────────────────────────────────────

/**
 * Load the chapter list for a manga. Matches oni's exact flow:
 *
 * 1. Fetch MangaDex aggregate FIRST (for count/volume metadata only — NOT for the chapter list)
 * 2. Fetch extension chapter list (atsu.moe) — this is the actual source of truth
 * 3. If extension returns chapters → use them with URL scheme `anilist_${mediaId}_ch_${number}`
 * 4. If extension returns nothing → synthesize 1..N fallback using AniList/MangaDex count
 *
 * The chapter URL is a routing token, NOT a real URL. The chapter number is later extracted
 * from the chapter TITLE (not the URL) when scraping images, matching oni's contract.
 */
suspend fun MainViewModel.loadMangaChapters(mangaId: Int, title: String) {
    android.util.Log.d("MangaChapters", "loadMangaChapters: mangaId=$mangaId title='$title'")
    _isLoadingMangaChapters.value = true
    _hasLoadedMangaChapters.value = false

    // Without a manga extension there is no real chapter source. If the manga has downloaded
    // chapters, fall back to them so the reader still works fully offline. Otherwise the
    // synthetic fallback (AniList chapter count) is wrong for releasing manga, so skip it
    // entirely — the reader shows the "select an extension" screen instead of a bogus list.
    if (_selectedExtensionAuthority.value == null) {
        val downloaded = mangaDownloadManager?.getDownloadedChapters(mangaId) ?: emptyList()
        if (downloaded.isNotEmpty()) {
            android.util.Log.d("MangaChapters", "loadMangaChapters: no extension, serving ${downloaded.size} downloaded chapters")
            _mangaExtensionTitle.value = null
            val downloadedChapters = downloaded.sortedBy { it.chapterNumber }.map { ch ->
                MangaChapter(
                    url = "anilist_${mangaId}_ch_${formatChapterNumberLocal(ch.chapterNumber)}",
                    title = if (ch.chapterTitle.isNotBlank()) ch.chapterTitle else "Chapter ${formatChapterNumberLocal(ch.chapterNumber)}",
                    chapterId = "anilist_${mangaId}_ch_${formatChapterNumberLocal(ch.chapterNumber)}",
                    chapterNumber = ch.chapterNumber
                )
            }
            _mangaChapters.value = downloadedChapters
            mangaTrackManager?.updateTotalChapters(mangaId, downloadedChapters.size, null)
            loadLocalMangaTracking()
            _hasLoadedMangaChapters.value = true
            _isLoadingMangaChapters.value = false
            return
        }
        android.util.Log.w("MangaChapters", "loadMangaChapters: no extension selected — skipping chapter list generation")
        _mangaChapters.value = emptyList()
        _hasLoadedMangaChapters.value = true
        _isLoadingMangaChapters.value = false
        return
    }

    val detail = _mangaDetail.value
    android.util.Log.d("MangaChapters", "loadMangaChapters: detail=${detail != null} detail.chapters=${detail?.chapters} detail.title=${detail?.title}")
    var chapters = emptyList<MangaChapter>()

    // Resolve the title — prefer English, then Romaji, then the passed-in title
    val resolvedTitle = detail?.titleEnglish?.takeIf { it.isNotBlank() }
        ?: detail?.title?.takeIf { it.isNotBlank() }
        ?: title
    android.util.Log.d("MangaChapters", "loadMangaChapters: resolvedTitle='$resolvedTitle'")

    // --- 1. Fetch MangaDex aggregate for count metadata (NOT for the chapter list) ---
    var mdLatestChapter: Int? = null
    var mdVolumeCount: Int? = null
    val mangaDexId = mangaDexManager?.findMangaByAniListId(resolvedTitle, mangaId)
    android.util.Log.d("MangaChapters", "loadMangaChapters: MangaDex lookup -> mangaDexId=$mangaDexId")
    _mangaDexId.value = mangaDexId
    if (mangaDexId != null) {
        val aggregate = mangaDexManager?.fetchAggregate(mangaDexId)
        if (aggregate != null) {
            // Get the highest chapter number from the aggregate (not the count of entries,
            // which can be wrong due to volumes/others grouping)
            val maxChapter = aggregate.volumes?.values?.flatMap { vol ->
                vol.chapters?.values?.mapNotNull { it.chapter?.toFloatOrNull()?.toInt() } ?: emptyList()
            }?.maxOrNull()
            mdLatestChapter = maxChapter
            mdVolumeCount = aggregate.volumes?.size
            android.util.Log.d("MangaChapters", "loadMangaChapters: MangaDex aggregate maxChapter=$maxChapter volumes=${mdVolumeCount}")
            mangaTrackManager?.updateMangaDexId(mangaId, mangaDexId)
        }
    }

    // --- 2. Fetch extension chapter list (atsu.moe) — the actual source of truth ---
    val titlesToTry = listOfNotNull(
        resolvedTitle,
        detail?.titleEnglish?.takeIf { it.isNotBlank() },
        detail?.title?.takeIf { it.isNotBlank() },
        title
    ).distinct()
    android.util.Log.d("MangaChapters", "loadMangaChapters: titlesToTry=$titlesToTry")

    var extChapters: List<ExtensionChapter>? = null
    var extTotalChapters = 0
    var matchedTitle: String? = null
    for (t in titlesToTry) {
        android.util.Log.d("MangaChapters", "loadMangaChapters: trying extension with title='$t'")
        val extResult = fetchExtensionChapterList(t)
        val resultChapters = extResult?.first
        val resultTotal = extResult?.second ?: 0
        // Keep the highest totalChapters seen across attempts — a later failed/null result
        // must not wipe the count reported by an earlier successful query.
        if (resultTotal > extTotalChapters) extTotalChapters = resultTotal
        android.util.Log.d("MangaChapters", "loadMangaChapters: extension returned ${resultChapters?.size ?: 0} chapters (total=$resultTotal) for '$t'")
        if (resultChapters != null && resultChapters.isNotEmpty()) {
            extChapters = resultChapters
            matchedTitle = t
            break
        }
    }

    if (extChapters != null && extChapters.isNotEmpty()) {
        android.util.Log.d("MangaDebug", "PATH: extension chapters (count=${extChapters.size}, totalChapters=$extTotalChapters)")
        _mangaExtensionTitle.value = matchedTitle
        // Build ChapterInfo list with oni's URL scheme: anilist_${mediaId}_ch_${number}
        // Sort by extension's index (oldest-first, chapter 1 at index 0)
        chapters = extChapters.sortedBy { it.index }.mapIndexed { idx, ch ->
            MangaChapter(
                url = "anilist_${mangaId}_ch_${ch.number}",
                title = if (ch.title.isNotBlank()) "Chapter ${ch.number}: ${ch.title}" else "Chapter ${ch.number}",
                chapterId = "anilist_${mangaId}_ch_${ch.number}",
                chapterNumber = ch.number.toFloatOrNull() ?: idx.toFloat()
            )
        }
    } else if (extTotalChapters > 0) {
        // The extension reports the CURRENT release count (totalChapters) but returned no
        // chapter entries for this title (e.g. the source page only exposes the count).
        // Use the extension total — the authoritative current number — as the chapter list.
        // This must NOT be replaced by the downloaded-chapter list: downloaded chapters still
        // open instantly because loadChapterImages serves local files by chapter number first,
        // and the count here is what home / detail / the chapter selection must show.
        android.util.Log.d("MangaChapters", "loadMangaChapters: extension returned only totalChapters=$extTotalChapters, building synthetic list")
        _mangaExtensionTitle.value = null
        chapters = (1..extTotalChapters).map { i ->
            MangaChapter(
                url = "anilist_${mangaId}_ch_$i",
                title = "Chapter $i",
                chapterId = "anilist_${mangaId}_ch_$i",
                chapterNumber = i.toFloat()
            )
        }
    } else {
        // Extension gave no data at all (e.g. offline). If this manga has downloaded chapters,
        // serve those so the reader stays fully offline even when an extension IS selected
        // (the reader's loadChapterImages serves local files first).
        val downloaded = mangaDownloadManager?.getDownloadedChapters(mangaId) ?: emptyList()
        if (downloaded.isNotEmpty()) {
            android.util.Log.d("MangaChapters", "loadMangaChapters: extension unavailable, serving ${downloaded.size} downloaded chapters")
            _mangaExtensionTitle.value = null
            chapters = downloaded.sortedBy { it.chapterNumber }.map { ch ->
                MangaChapter(
                    url = "anilist_${mangaId}_ch_${formatChapterNumberLocal(ch.chapterNumber)}",
                    title = if (ch.chapterTitle.isNotBlank()) ch.chapterTitle else "Chapter ${formatChapterNumberLocal(ch.chapterNumber)}",
                    chapterId = "anilist_${mangaId}_ch_${formatChapterNumberLocal(ch.chapterNumber)}",
                    chapterNumber = ch.chapterNumber
                )
            }
        } else {
            android.util.Log.d("MangaChapters", "loadMangaChapters: no extension chapters, using synthetic fallback")
            _mangaExtensionTitle.value = null
            // --- 3. Fallback: synthetic chapter list from the best available count ---
            // Only reached when the extension returned neither chapters nor a total.
            // Priority: MangaDex latest > AniList chapters > 0
            // AniList often returns chapters=null for ongoing manga, so it's the last resort.
            val fallbackTotal = when {
                mdLatestChapter != null && mdLatestChapter > 0 -> mdLatestChapter
                detail?.chapters != null && detail.chapters > 0 -> detail.chapters
                else -> 0
            }
            android.util.Log.d("MangaChapters", "loadMangaChapters: fallbackTotal=$fallbackTotal (mdLatest=$mdLatestChapter, detail.chapters=${detail?.chapters})")
            if (fallbackTotal > 0) {
                chapters = (1..fallbackTotal).map { i ->
                    MangaChapter(
                        url = "anilist_${mangaId}_ch_$i",
                        title = "Chapter $i",
                        chapterId = "anilist_${mangaId}_ch_$i",
                        chapterNumber = i.toFloat()
                    )
                }
            }
        }
    }

    android.util.Log.d("MangaDebug", "loadMangaChapters: final chapters.size=${chapters.size}")
    _mangaChapters.value = chapters

    // Display denominator for progress (e.g. 149/354). Prefer the extension's CURRENT release
    // count: AniList's chapters field is stale for releasing manga (e.g. Blue Lock stuck at 352
    // while the extension already has 354), so it must not override the extension total.
    // extTotalChapters covers the case where the extension returns only a partial list (e.g.
    // just the latest few chapters), which would otherwise make progress look like 149/3.
    val displayTotalChapters = when {
        extTotalChapters > 0 -> extTotalChapters
        extChapters != null && extChapters.isNotEmpty() -> chapters.size
        detail?.chapters != null && detail.chapters > 0 -> detail.chapters
        else -> chapters.size
    }
    _mangaTotalChapters.value = displayTotalChapters
    mangaTrackManager?.updateTotalChapters(
        mangaId,
        displayTotalChapters,
        mdVolumeCount ?: detail?.volumes
    )
    loadLocalMangaTracking()
    _hasLoadedMangaChapters.value = true
    _isLoadingMangaChapters.value = false
}

// ─── Chapter Images ──────────────────────────────────────────────────

/**
 * Load the image URL list for a single chapter. Matches oni's exact flow:
 *
 * 1. Extract the chapter number from the chapter TITLE (not the URL)
 *    Title format: "Chapter 346" or "Chapter 346.2: Some Title" → "346" or "346.2"
 * 2. Resolve the manga title (extension-matched title > detail title > passed title)
 * 3. Call the extension's /scrape endpoint with manga=<title>&chapter=<number>
 * 4. Parse the {chapter:{images:[...]}} JSON response
 *
 * If no extension is selected, shows an error telling the user to pick one in Settings.
 */
fun MainViewModel.loadChapterImages(chapterId: String, useDataSaver: Boolean = false, mangaTitle: String? = null, chapterTitle: String? = null, mangaId: Int = 0) {
    chapterImagesJob?.cancel()
    chapterImagesJob = viewModelScope.launch {
        // Offline-first: if this chapter is downloaded, serve the local page files straight
        // from disk. No network, no extension scrape — this is what makes reading work when
        // the connection is down (and makes downloaded chapters open instantly).
        // Titles can be "Chapter 1", "Chapter 1.5: Title", or the local "Ch. 1" format.
        val chapterNumber = chapterTitle
            ?.replaceFirst("Chapter ", "")
            ?.replaceFirst("Ch. ", "")
            ?.substringBefore(":")
            ?.trim()
            ?.toFloatOrNull()
        if (mangaId > 0 && chapterNumber != null) {
            val downloaded = mangaDownloadManager?.getDownloadedChapters(mangaId)
                ?.firstOrNull { it.chapterNumber == chapterNumber }
            if (downloaded != null && downloaded.pageFiles.isNotEmpty()) {
                android.util.Log.d("MangaReader", "loadChapterImages: serving ${downloaded.pageFiles.size} LOCAL pages for downloaded chapter $chapterNumber")
                _mangaChapterImagesCache.value = _mangaChapterImagesCache.value + (chapterId to downloaded.pageFiles)
                _mangaChapterImages.value = downloaded.pageFiles
                _mangaChapterImagesError.value = null
                return@launch
            }
        }

        // Serve from the prefetch cache first so chapter transitions (incl. auto-advance)
        // load instantly instead of waiting on a scrape round-trip.
        _mangaChapterImagesCache.value[chapterId]?.let { cached ->
            android.util.Log.d("MangaReader", "loadChapterImages: served ${cached.size} images from cache for chapterId='$chapterId'")
            _mangaChapterImages.value = cached
            _mangaChapterImagesError.value = null
            return@launch
        }

        _mangaChapterImages.value = null
        _mangaChapterImagesError.value = null

        android.util.Log.d("MangaReader", "loadChapterImages: chapterId='$chapterId' chapterTitle='$chapterTitle' mangaTitle='$mangaTitle' mangaId=$mangaId")

        // Extract the chapter number from the chapter TITLE (matching oni's contract)
        // Title format: "Chapter 346" or "Chapter 346.2: Some Title" → "346" or "346.2"
        val chapterParam = chapterTitle?.let { title ->
            title.removePrefix("Chapter ").substringBefore(":").trim()
        } ?: chapterId.substringAfterLast("_ch_").trim()

        android.util.Log.d("MangaReader", "loadChapterImages: extracted chapterParam='$chapterParam'")

        // Resolve the manga title for the extension
        val extTitle = _mangaExtensionTitle.value
            ?: _mangaDetail.value?.titleEnglish?.takeIf { it.isNotBlank() }
            ?: _mangaDetail.value?.title?.takeIf { it.isNotBlank() }
            ?: mangaTitle
            ?: ""

        if (extTitle.isBlank()) {
            android.util.Log.w("MangaReader", "loadChapterImages: no manga title available")
            _mangaChapterImages.value = emptyList()
            _mangaChapterImagesError.value = "Could not determine the manga title for the extension."
            return@launch
        }

        val authority = _selectedExtensionAuthority.value
        if (authority == null) {
            android.util.Log.w("MangaReader", "loadChapterImages: no extension selected")
            _mangaChapterImages.value = emptyList()
            _mangaChapterImagesError.value = "No manga extension selected. Pick one in Settings → Extensions to read this chapter."
            return@launch
        }

        android.util.Log.d("MangaReader", "loadChapterImages: calling extension scrape with title='$extTitle' chapter='$chapterParam' authority='$authority'")

        val images = withContext(Dispatchers.IO) {
            fetchExtensionChapterImages(extTitle, chapterParam, authority)
        }

        android.util.Log.d("MangaReader", "loadChapterImages: extension returned ${images?.size ?: 0} images")

        if (images == null) {
            _mangaChapterImages.value = emptyList()
            _mangaChapterImagesError.value = "Failed to load chapter images. Check your connection and try again."
        } else if (images.isEmpty()) {
            _mangaChapterImages.value = emptyList()
            _mangaChapterImagesError.value = "This chapter has no pages."
        } else {
            _mangaChapterImagesCache.value = _mangaChapterImagesCache.value + (chapterId to images)
            _mangaChapterImages.value = images
        }
    }
}

/**
 * Scrape the NEXT chapter's image list in the background and cache it, so that advancing to it
 * (manually or via auto-advance) is instant. Never touches [mangaChapterImages], so the reader
 * keeps showing the current chapter. Skips chapters that are already cached or in flight.
 */
fun MainViewModel.prefetchMangaChapterImages(chapter: MangaChapter?, mangaTitle: String? = null, mangaId: Int = 0, downloadedChapterNumbers: Set<Float> = emptySet()) {
    val next = chapter ?: return
    if (_mangaChapterImagesCache.value.containsKey(next.chapterId)) return
    if (!_prefetchingChapterIds.add(next.chapterId)) return
    // Already downloaded — the chapter opens straight from disk, nothing to prefetch.
    // This MUST use the in-memory scan result, never storage.isChapterDownloaded(): that does
    // real filesystem/SAF work, and this runs on the main thread from the reader's near-end
    // prefetch effect. The old code removed the id right after the check, so that main-thread
    // I/O re-ran on EVERY scroll frame past ~90% — the reader janked at the sync-threshold
    // point in both online and offline modes. Leaving the id in the in-flight set makes this a
    // one-time check per session (a downloaded chapter opens from disk anyway).
    if (mangaId > 0 && next.chapterNumber in downloadedChapterNumbers) {
        return
    }

    viewModelScope.launch {
        try {
            val authority = _selectedExtensionAuthority.value
            if (authority == null) return@launch
            val extTitle = _mangaExtensionTitle.value
                ?: _mangaDetail.value?.titleEnglish?.takeIf { it.isNotBlank() }
                ?: _mangaDetail.value?.title?.takeIf { it.isNotBlank() }
                ?: mangaTitle
                ?: return@launch
            if (extTitle.isBlank()) return@launch

            val chapterParam = next.title.removePrefix("Chapter ").substringBefore(":").trim()

            android.util.Log.d("MangaReader", "prefetchMangaChapterImages: chapterId='${next.chapterId}' chapter='$chapterParam' title='$extTitle'")

            val images = withContext(Dispatchers.IO) {
                fetchExtensionChapterImages(extTitle, chapterParam, authority)
            }
            if (images != null && images.isNotEmpty()) {
                _mangaChapterImagesCache.value = _mangaChapterImagesCache.value + (next.chapterId to images)
                android.util.Log.d("MangaReader", "prefetchMangaChapterImages: cached ${images.size} images for chapterId='${next.chapterId}'")
            }
        } finally {
            _prefetchingChapterIds.remove(next.chapterId)
        }
    }
}

fun MainViewModel.clearChapterImages() {
    _mangaChapterImages.value = null
    _mangaChapterImagesError.value = null
}

// ─── Tracking ────────────────────────────────────────────────────────

/**
 * Mark a chapter as read locally. If the user has no track for this manga yet, one is auto-created
 * with status CURRENT (matching oni's behavior).
 *
 * The AniList progress push is NOT done here — [onMangaScrollProgress] schedules a single
 * debounced (3s) AniList update when the sync threshold is reached. This function is called on
 * every scroll frame above the threshold, so pushing here would fire one mutation per frame and
 * spam/rate-limit the API (which silently breaks later updates, including manual status changes).
 */
fun MainViewModel.markMangaChapterRead(mangaId: Int, chapter: MangaChapter, mangaTitle: String = "", mangaCover: String = "") {
    android.util.Log.d("MangaSyncDebug", "markMangaChapterRead mangaId=$mangaId chapterNumber=${chapter.chapterNumber} title='$mangaTitle'")
    // The track writes and the tracking-list refresh are pure SharedPreferences work. Run them
    // off the main thread so the threshold-crossing frame (the scroll/page callback that
    // triggered this) doesn't hitch the reader. The reader's own "read" checkmark updates
    // immediately via markChapterReadInListUi (in-memory), so local-first UX is unaffected.
    viewModelScope.launch(Dispatchers.Default) {
        // Ensure a track exists so progress is recorded for first-time readers
        mangaTrackManager?.ensureTrack(mangaId, mangaTitle, mangaCover)
        mangaTrackManager?.markChapterComplete(mangaId, chapter)
        loadLocalMangaTracking()
    }
}

/**
 * Called when a chapter is opened in the reader. NOTE: this intentionally does nothing — a local
 * track is created lazily only once the user actually reads (see updateMangaScrollProgress), so
 * merely opening a chapter does not add the manga to tracking. Retained (empty) as a stable API
 * so the reader's "opened" call site still documents the intended entry point.
 */
fun MainViewModel.startMangaChapter(mangaId: Int, mangaTitle: String = "", mangaCover: String = "") {
}

/** Reload the local manga tracking lists (Continue Reading / Planning / Completed). */
fun MainViewModel.refreshMangaTracking() {
    loadLocalMangaTracking()
}

fun MainViewModel.updateMangaProgress(mangaId: Int, progress: Float) {
    android.util.Log.d("MangaSyncDebug", "updateMangaProgress mangaId=$mangaId progress=$progress")
    mangaTrackManager?.updateChapterProgress(mangaId, progress)
    loadLocalMangaTracking()
}

// ─── Manga → AniList sync queue (local-first, debounced) ──────────────
// Mirrors the anime queueSync/executePendingSyncs pattern: the UI is updated
// immediately from local tracking and the AniList mutation is pushed in the
// background after a short debounce. Rapid changes for the same manga coalesce
// into a single mutation (status/progress fields merge, latest non-null wins).
// Failed pushes are re-queued so they retry on the next debounce cycle.

private data class PendingMangaSync(
    val type: String,
    val mediaId: Int,
    val status: String? = null,
    val progress: Int? = null,
    val entryId: Int? = null,
    val score: Int? = null
)

private val pendingMangaSyncs = mutableMapOf<Int, PendingMangaSync>()
private var mangaSyncJob: Job? = null

// Scroll-progress persistence is throttled so the reader doesn't write SharedPreferences
// (full JSON encode of every track) on every scroll frame — that per-frame write, plus the
// repeated threshold-crossing work, was the source of jank once the autosync threshold hit.
private const val MANGA_SCROLL_PERSIST_INTERVAL_MS = 500L
private var lastMangaScrollPersistTime = 0L

// Mangas whose local track was already ensured this session. Lets the first real scroll
// progress create the Continue Reading track without decoding all tracks from prefs on
// every frame just to check existence.
private val mangaTrackEnsured = mutableSetOf<Int>()

// Chapters already marked read + pushed to AniList this session, keyed "$mangaId:$chapterId".
// The threshold-crossing path (track writes, tracking-list refresh, debounced sync re-queue)
// must run once per chapter — otherwise every scroll frame past the threshold re-runs it.
private val mangaReadSyncedChapters = mutableSetOf<String>()

private fun MainViewModel.queueMangaSync(
    mediaId: Int,
    type: String,
    status: String? = null,
    progress: Int? = null,
    entryId: Int? = null,
    score: Int? = null
) {
    val existing = pendingMangaSyncs[mediaId]
    pendingMangaSyncs[mediaId] = PendingMangaSync(
        type = type,
        mediaId = mediaId,
        status = status ?: existing?.status,
        progress = progress ?: existing?.progress,
        entryId = entryId ?: existing?.entryId,
        score = score ?: existing?.score
    )
    mangaSyncJob?.cancel()
    // Run the push on a background dispatcher. The mutation is a network call, and the
    // post-push refresh (fetchMangaLists) merges every AniList row into local tracking with
    // per-item SharedPreferences reads/writes — none of that may run on the main thread, or
    // the reader janks ~2s after progress crosses the sync threshold.
    mangaSyncJob = viewModelScope.launch(Dispatchers.IO) {
        delay(MainViewModel.SYNC_DEBOUNCE_MS)
        // Run the push to completion even if a newer sync is queued mid-flight.
        // Cancelling the executing job would abort the network call in progress and
        // skip the re-queue for failed pushes — rapid chapter flips (next-chapter
        // button) hit this constantly, silently dropping AniList updates.
        withContext(NonCancellable) { executeMangaPendingSyncs() }
    }
}

/**
 * Flush any pending manga → AniList syncs immediately, skipping the debounce.
 * Called when the reader closes so progress read in the last few seconds isn't
 * lost if the app is killed within the debounce window.
 */
fun MainViewModel.flushMangaSync() {
    if (pendingMangaSyncs.isEmpty()) return
    mangaSyncJob?.cancel()
    // Background dispatcher — see queueMangaSync for why the sync must not run on main.
    mangaSyncJob = viewModelScope.launch(Dispatchers.IO) {
        withContext(NonCancellable) { executeMangaPendingSyncs() }
    }
}

private suspend fun MainViewModel.executeMangaPendingSyncs() {
    val syncs = pendingMangaSyncs.toMap()
    pendingMangaSyncs.clear()
    if (syncs.isEmpty()) return

    val token = authToken.value
    var didPush = false
    if (token != null) {
        for ((_, sync) in syncs) {
            val ok = when (sync.type) {
                "status", "progress" -> {
                    val status = sync.status
                        ?: mangaTrackManager?.getTrack(sync.mediaId)?.status
                        ?: "CURRENT"
                    mangaRepository?.updateMangaStatus(sync.mediaId, status, token, sync.progress, score = sync.score)
                }
                "score" -> {
                    val score = sync.score
                        ?: mangaTrackManager?.getTrack(sync.mediaId)?.score
                        ?: 0
                    val status = sync.status
                        ?: mangaTrackManager?.getTrack(sync.mediaId)?.status
                        ?: "CURRENT"
                    mangaRepository?.updateMangaStatus(sync.mediaId, status, token, sync.progress, score = score)
                }
                "delete" -> {
                    val entryId = sync.entryId
                    if (entryId != null) mangaRepository?.deleteMangaListEntry(entryId, token) else null
                }
                else -> null
            }
            android.util.Log.d("MangaSyncDebug", "executeMangaPendingSyncs: type=${sync.type} mediaId=${sync.mediaId} ok=$ok")
            if (ok == true) {
                didPush = true
            } else {
                // Re-queue the failed sync so it retries on the next debounce cycle
                pendingMangaSyncs[sync.mediaId] = sync
            }
        }
    } else {
        android.util.Log.d("MangaSyncDebug", "executeMangaPendingSyncs: skipped — no auth token")
    }

    if (didPush) {
        // Refresh from AniList so local + remote stay in sync
        if (_userId.value != null) {
            fetchMangaLists()
        }
        loadLocalMangaTracking()
    }
}

/**
 * Handle scroll progress from the reader. This mirrors oni's onChapterScrollProgress flow:
 * - Always save scroll progress locally
 * - When scroll reaches the threshold (default 90%), mark the chapter as read
 * - If the chapter number is an integer (not partial like 12.5), push the new progress
 *   to AniList through the debounced background sync queue (rapid chapter flips coalesce
 *   into a single mutation)
 *
 * @param mangaId The AniList manga ID
 * @param chapter The chapter being read
 * @param scrollPercent 0.0 - 1.0 scroll progress
 * @param mangaTitle Title for track creation
 * @param mangaCover Cover URL for track creation
 */
fun MainViewModel.onMangaScrollProgress(
    mangaId: Int,
    chapter: MangaChapter?,
    scrollPercent: Float,
    mangaTitle: String = "",
    mangaCover: String = ""
): Boolean {
    if (chapter == null) return false
    if (!scrollPercent.isFinite()) return false

    // Always save scroll progress locally (this also lazily creates the track on first progress)
    updateMangaScrollProgress(mangaId, scrollPercent, mangaTitle, mangaCover)

    val threshold = userPreferences.mangaSyncThreshold.value / 100f
    if (scrollPercent >= threshold) {
        // Mark the chapter read (and push to AniList) only ONCE per chapter — on the first frame
        // that crosses the threshold. Without this guard every subsequent scroll frame re-runs the
        // track writes, the tracking-list StateFlow refresh, and the debounced sync re-queue,
        // which janks the reader once the threshold is reached.
        val readKey = "$mangaId:${chapter.chapterId}"
        if (mangaReadSyncedChapters.add(readKey)) {
            android.util.Log.d("MangaSyncDebug", "THRESHOLD CROSSED mangaId=$mangaId scrollPercent=$scrollPercent chapterNumber=${chapter.chapterNumber}")
            // Mark chapter as read (creates track if needed, updates local progress)
            markMangaChapterRead(mangaId, chapter, mangaTitle, mangaCover)

            // Schedule the AniList progress push through the debounced sync queue.
            // Only for integer chapter numbers (skip partial chapters like 12.5)
            val chapterNum = chapter.chapterNumber
            val isIntegerChapter = chapterNum > 0f && chapterNum == chapterNum.toInt().toFloat()
            if (isIntegerChapter) {
                queueMangaSync(mangaId, "progress", progress = chapterNum.toInt())
            }
            return true
        }
    }
    return false
}

/** Set the AniList sync threshold (75-100%). Persists across restarts. */
fun MainViewModel.setMangaSyncThreshold(percent: Int) {
    userPreferences.setMangaSyncThreshold(percent)
}

fun MainViewModel.updateMangaScrollProgress(mangaId: Int, scrollProgress: Float, mangaTitle: String = "", mangaCover: String = "") {
    // Guard against NaN/Infinity — same defensive pattern as oni's TrackingManager
    val safe = if (scrollProgress.isNaN() || scrollProgress.isInfinite()) 0f else scrollProgress
    // NOTE: Track creation is intentionally NOT done here — it was previously called on first
    // scroll progress (safe > 0f) which caused every opened chapter to appear in "Continue
    // Reading" immediately, even without the user reading enough. Track creation is now handled
    // exclusively by markMangaChapterRead, which fires only when the sync threshold is crossed.
    // Persist scroll progress — throttled, because writing SharedPreferences (full JSON encode of
    // all tracks) on every scroll frame is the jank source once the reader is at/over the sync
    // threshold. Resets to 0 (opening a non-resume chapter) are rare and must land immediately so
    // stale Continue Reading cards clear correctly; the final persisted value of a scroll gesture
    // stays within one interval of the actual position, which is plenty for resume.
    val now = SystemClock.elapsedRealtime()
    if (safe <= 0f || now - lastMangaScrollPersistTime >= MANGA_SCROLL_PERSIST_INTERVAL_MS) {
        mangaTrackManager?.updateScrollProgress(mangaId, safe)
        lastMangaScrollPersistTime = now
    }
}

/** Persist the page count of the chapter currently being read so home can show "pages left". */
fun MainViewModel.updateMangaChapterPages(mangaId: Int, pages: Int) {
    if (pages <= 0) return
    mangaTrackManager?.updateChapterPages(mangaId, pages)
}

fun MainViewModel.updateMangaStatus(mangaId: Int, status: String, progress: Int? = null, score: Int? = null) {
    val effectiveStatus = status.ifBlank { "CURRENT" }
    android.util.Log.d("MangaSyncDebug", "updateMangaStatus mangaId=$mangaId status='$status' effectiveStatus='$effectiveStatus' progress=$progress score=$score")
    // Local-first: apply the change immediately so the UI reacts instantly, then
    // queue the AniList push for the background debounced sync.
    mangaTrackManager?.updateTrackingStatus(mangaId, effectiveStatus)
    if (progress != null) {
        mangaTrackManager?.updateChapterProgress(mangaId, progress.toFloat())
    }
    if (score != null) {
        mangaTrackManager?.updateScore(mangaId, score)
    }
    loadLocalMangaTracking()
    queueMangaSync(mangaId, "status", status = effectiveStatus, progress = progress, score = score)
}

/** Set the AniList score (0-100) for a manga, local-first with a debounced remote push. */
fun MainViewModel.updateMangaScore(mangaId: Int, score: Int) {
    android.util.Log.d("MangaSyncDebug", "updateMangaScore mangaId=$mangaId score=$score")
    mangaTrackManager?.updateScore(mangaId, score)
    loadLocalMangaTracking()
    queueMangaSync(mangaId, "score", score = score)
}

fun MainViewModel.removeMangaTracking(mangaId: Int) {
    android.util.Log.d("MangaSyncDebug", "removeMangaTracking mangaId=$mangaId")
    mangaTrackManager?.removeTrack(mangaId)
    // Forget the ensure-track flag so re-reading this manga later recreates its local track.
    mangaTrackEnsured.remove(mangaId)
    mangaReadSyncedChapters.removeAll { it.startsWith("$mangaId:") }
    loadLocalMangaTracking()
}

/**
 * Dismiss a manga from the Continue Reading row only. Clears the in-chapter reading
 * state so the resume card disappears, but keeps the manga in its status list
 * (mirrors anime's removeContinueWatchingEntry, which doesn't untrack the anime).
 */
fun MainViewModel.dismissMangaContinueReading(mangaId: Int) {
    android.util.Log.d("MangaSyncDebug", "dismissMangaContinueReading mangaId=$mangaId")
    mangaTrackManager?.clearChapterProgress(mangaId)
    loadLocalMangaTracking()
}

// ─── Downloads ───────────────────────────────────────────────────────

/** Stable fallback flows used until [initManga] creates the download manager. */
private val _emptyBatchState = MutableStateFlow<MangaBatchDownloadState?>(null)
private val _emptyDownloadsFlow = MutableStateFlow<List<DownloadedManga>>(emptyList())
private val _emptyActiveDownloadsFlow = MutableStateFlow<List<MangaDownloadTask>>(emptyList())

/** Live batch-download progress for the download dialog (null when idle). */
val MainViewModel.mangaBatchDownloadState: StateFlow<MangaBatchDownloadState?>
    get() = mangaDownloadManager?.batchDownloadState ?: _emptyBatchState

/** Reactive list of downloaded manga (refreshed on every scan). */
val MainViewModel.mangaDownloads: StateFlow<List<DownloadedManga>>
    get() = mangaDownloadManager?.downloadedManga ?: _emptyDownloadsFlow

/** Live list of queued/downloading manga chapter tasks. */
val MainViewModel.mangaActiveDownloads: StateFlow<List<MangaDownloadTask>>
    get() = mangaDownloadManager?.activeDownloads ?: _emptyActiveDownloadsFlow

/** True when the last scan failed because the app lost access to the selected folder. */
val MainViewModel.mangaLocationPermissionDenied: StateFlow<Boolean>
    get() = mangaDownloadManager?.locationPermissionDenied ?: MutableStateFlow(false)

/**
 * Re-scans the current download location. Called when the Manga Downloads screen opens so
 * files moved into the folder while the app was running (or after a reinstall) show up.
 */
fun MainViewModel.refreshMangaDownloads() {
    mangaDownloadManager?.scanDownloadedManga()
}

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

/**
 * Scrape the image URLs for one chapter so it can be queued for download. Mirrors
 * [MainViewModel.loadChapterImages]'s title/number extraction and extension routing.
 */
fun MainViewModel.fetchChapterImagesForDownload(
    mangaTitle: String,
    chapterNumber: String,
    onResult: (Result<List<String>>) -> Unit
) {
    viewModelScope.launch {
        val authority = _selectedExtensionAuthority.value
        if (authority == null) {
            onResult(Result.failure(Exception("No manga extension selected")))
            return@launch
        }
        val extTitle = _mangaExtensionTitle.value
            ?: _mangaDetail.value?.titleEnglish?.takeIf { it.isNotBlank() }
            ?: _mangaDetail.value?.title?.takeIf { it.isNotBlank() }
            ?: mangaTitle
        if (extTitle.isBlank()) {
            onResult(Result.failure(Exception("Could not determine the manga title")))
            return@launch
        }
        val images = withContext(Dispatchers.IO) {
            fetchExtensionChapterImages(extTitle, chapterNumber, authority)
        }
        onResult(Result.success(images ?: emptyList()))
    }
}

/**
 * Refetch the up-to-date chapter list from the user's selected default extension. Used by the
 * download dialog so a chapter released since the screen was opened shows up — and so the dialog
 * never shows only the downloaded-chapters fallback when the initial extension fetch failed.
 * Tries the same set of titles as [loadMangaChapters] (English > Romaji > passed title) to find
 * the one the extension recognizes. Returns the same MangaChapter mapping the reader uses (oni
 * URL scheme). Empty list when no extension is selected, the fetch fails, or the source has no
 * chapters.
 */
suspend fun MainViewModel.fetchLatestChaptersForDownload(mangaId: Int, mangaTitle: String): List<MangaChapter> {
    val authority = _selectedExtensionAuthority.value ?: return emptyList()
    if (mangaTitle.isBlank()) return emptyList()
    android.util.Log.d("MangaDownload", "refetching latest chapters for '$mangaTitle' via authority='$authority'")
    val titlesToTry = listOfNotNull(
        _mangaDetail.value?.titleEnglish?.takeIf { it.isNotBlank() },
        _mangaDetail.value?.title?.takeIf { it.isNotBlank() },
        mangaTitle
    ).distinct()
    var extChapters: List<ExtensionChapter>? = null
    for (t in titlesToTry) {
        android.util.Log.d("MangaDownload", "refetching chapters with title='$t'")
        val extResult = fetchExtensionChapterList(t)
        extChapters = extResult?.first
        android.util.Log.d("MangaDownload", "refetch returned ${extChapters?.size ?: 0} chapters for '$t'")
        if (extChapters != null && extChapters.isNotEmpty()) break
    }
    if (extChapters.isNullOrEmpty()) return emptyList()
    return extChapters.sortedBy { it.index }.mapIndexed { idx, ch ->
        MangaChapter(
            url = "anilist_${mangaId}_ch_${ch.number}",
            title = if (ch.title.isNotBlank()) "Chapter ${ch.number}: ${ch.title}" else "Chapter ${ch.number}",
            chapterId = "anilist_${mangaId}_ch_${ch.number}",
            chapterNumber = ch.number.toFloatOrNull() ?: idx.toFloat()
        )
    }
}

/** Kick off a multi-chapter batch download (runs on the manager's own scope). */
fun MainViewModel.startMangaBatchDownload(mangaId: Int, mangaTitle: String, coverUrl: String, chapters: List<MangaChapterDownload>) {    if (chapters.isEmpty()) return
    mangaDownloadManager?.startBatchDownload(mangaId, mangaTitle, coverUrl, chapters)
}

fun MainViewModel.cancelMangaBatchDownload() {
    mangaDownloadManager?.cancelBatchDownload()
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

/** Formats a chapter number for display/URLs: 1.0 → "1", 1.5 → "1.5". */
private fun formatChapterNumberLocal(chapterNumber: Float): String =
    if (chapterNumber % 1f == 0f) chapterNumber.toInt().toString() else chapterNumber.toString()

/** Mirrors the oni folder-slug rule: "Blue Lock" → "blue-lock". */
private fun slugify(title: String): String =
    title.lowercase().trim().replace(Regex("[^a-z0-9]+"), "-").trim('-')
