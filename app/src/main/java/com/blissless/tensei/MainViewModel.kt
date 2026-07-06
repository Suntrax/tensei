package com.blissless.tensei

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.blissless.tensei.data.AnimeRepository
import com.blissless.tensei.data.CacheManager
import com.blissless.tensei.api.jikan.JikanService
import com.blissless.tensei.api.jikan.JikanUserFavorites
import com.blissless.tensei.api.jikan.JikanUserHistory
import com.blissless.tensei.api.myanimelist.LoginProvider
import com.blissless.tensei.api.myanimelist.MalApiService
import com.blissless.tensei.data.UserPreferences
import com.blissless.tensei.data.models.AiringScheduleAnime
import com.blissless.tensei.data.models.AnimeMedia
import com.blissless.tensei.data.models.AnimeRelation
import com.blissless.tensei.data.models.CharacterData
import com.blissless.tensei.data.models.DetailedAnimeData
import com.blissless.tensei.data.models.ExploreAnime
import com.blissless.tensei.data.models.StaffData
import com.blissless.tensei.data.models.ExploreCacheData
import com.blissless.tensei.data.models.ExploreMedia
import com.blissless.tensei.data.models.HomeCacheData
import com.blissless.tensei.data.models.LocalAnimeEntry
import com.blissless.tensei.data.models.MediaTag
import com.blissless.tensei.data.models.StoredFavorite
import com.blissless.tensei.data.models.StudioData
import com.blissless.tensei.data.models.TmdbEpisode
import com.blissless.tensei.data.models.UserActivity
import com.blissless.tensei.data.models.UserAnimeStats
import com.blissless.tensei.data.models.UserFavoriteAnime
import com.blissless.tensei.download.EpisodeDownloadManager
import com.blissless.tensei.update.GitHubRelease
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.model.Track
import com.blissless.tensei.torrent.MagnetData
import com.blissless.tensei.torrent.MagnetExtensionClient
import okhttp3.OkHttpClient
// Extension functions on MainViewModel (defined in com.blissless.tensei.viewmodel)
import com.blissless.tensei.viewmodel.startApiRetryLoop
import com.blissless.tensei.viewmodel.queueSync
import com.blissless.tensei.viewmodel.fetchMalList
import com.blissless.tensei.viewmodel.toggleMalFavoriteById
import com.blissless.tensei.viewmodel.loadMalFavoritesFromCache
import com.blissless.tensei.viewmodel.fetchAniListFavorites
import com.blissless.tensei.viewmodel.loadAniListFavoritesFromStorage
import com.blissless.tensei.viewmodel.toggleAniListFavorite
import com.blissless.tensei.viewmodel.refreshReleasingAnimeProgress
import com.blissless.tensei.viewmodel.playEpisodeWithExtension
import com.blissless.tensei.viewmodel.fetchExtensionHosterVideos
import com.blissless.tensei.viewmodel.loadAvailableMagnetExtensions
import com.blissless.tensei.viewmodel.fetchMagnetEpisodes
import com.blissless.tensei.viewmodel.fetchMagnetForEpisode
import com.blissless.tensei.viewmodel.fetchStreamUrlForEpisode
import com.blissless.tensei.viewmodel.ensureMagnetClient
import com.blissless.tensei.viewmodel.clearMagnetEpisodes
import com.blissless.tensei.viewmodel.getMagnetEpisodeNumbers
import com.blissless.tensei.viewmodel.getMagnetForEpisode
// Extension functions on AnimeRepository (defined in com.blissless.tensei.data)
import com.blissless.tensei.data.fetchTmdbEpisodes
import com.blissless.tensei.data.fetchAnimeRelationsList

@UnstableApi
class MainViewModel : ViewModel() {

    companion object {
        internal const val TAG = "MainViewModel"
        private const val CLIENT_ID = BuildConfig.CLIENT_ID_ANILIST
        internal const val MIN_REFRESH_INTERVAL_MS = 5 * 60 * 1000L // 5 minutes
        internal const val SYNC_DEBOUNCE_MS = 2000L // 2 seconds debounce for API sync
        internal const val FAVORITE_DEBOUNCE_MS = 1000L // 1 second debounce for favorite toggles
    }

    internal lateinit var userPreferences: UserPreferences
    internal lateinit var cacheManager: CacheManager
    lateinit var episodeDownloadManager: EpisodeDownloadManager
    internal lateinit var repository: AnimeRepository
    internal lateinit var context: Context
    private var connectivityCallback: ConnectivityManager.NetworkCallback? = null
    internal var sourceManager: com.blissless.tensei.stream.SourceManager? = null

    data class PreFetchedExtensionData(
        val source: AnimeCatalogueSource,
        val sAnime: SAnime,
        val episodes: List<SEpisode>
    )
    internal val _preFetchedExtensionData = mutableMapOf<Int, PreFetchedExtensionData>()

    fun getPreFetchedExtensionData(animeId: Int): PreFetchedExtensionData? =
        _preFetchedExtensionData[animeId]

    private val _preFetchedEpisodeNumbers = MutableStateFlow<Map<Int, Set<Int>>>(emptyMap())
    val preFetchedEpisodeNumbers: StateFlow<Map<Int, Set<Int>>> = _preFetchedEpisodeNumbers.asStateFlow()

    private val _availableExtensions = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val availableExtensions: StateFlow<List<Pair<String, String>>> = _availableExtensions.asStateFlow()

    fun loadAvailableExtensions() {
        viewModelScope.launch(Dispatchers.IO) {
            val sm = sourceManager ?: return@launch
            sm.loadSources()
            _availableExtensions.value = sm.getSources().map {
                it.extension.name.removePrefix("Aniyomi: ") to it.extension.packageName
            }.distinctBy { it.second }
        }
    }

    private fun preLoadExtensionSources() {
        viewModelScope.launch(Dispatchers.IO) {
            sourceManager?.loadSources()
        }
    }

    fun preFetchExtensionEpisodes(anime: AnimeMedia, packageName: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            _preFetchedExtensionData.remove(anime.id)
            _preFetchedEpisodeNumbers.value -= anime.id

            val pkg = packageName ?: defaultExtensionPackage.value
            if (pkg.isEmpty()) {
                _preFetchedEpisodeNumbers.value += (anime.id to emptySet())
                return@launch
            }

            val sm = sourceManager
            if (sm == null) {
                _preFetchedEpisodeNumbers.value += (anime.id to emptySet())
                return@launch
            }

            try { sm.loadSources() } catch (_: Exception) {
                _preFetchedEpisodeNumbers.value += (anime.id to emptySet())
                return@launch
            }
            val allSources = sm.getSources()
            val sw = allSources.find { it.extension.packageName == pkg }
            if (sw == null) {
                _preFetchedEpisodeNumbers.value += (anime.id to emptySet())
                return@launch
            }
            val source = sw.source

            val searchTerms = listOfNotNull(anime.titleEnglish, anime.title).distinct()
            var matchedSAnime: SAnime? = null
            for (query in searchTerms) {
                try {
                    val page = source.getSearchAnime(1, query, AnimeFilterList())
                    matchedSAnime = page.animes.firstOrNull { a ->
                        a.title.contains(anime.title, ignoreCase = true) ||
                                (anime.titleEnglish != null && a.title.contains(anime.titleEnglish, ignoreCase = true))
                    } ?: page.animes.firstOrNull()
                    if (matchedSAnime != null) break
                } catch (_: Exception) { }
            }

            val sAnime = matchedSAnime
            if (sAnime == null) {
                _preFetchedEpisodeNumbers.value += (anime.id to emptySet())
                return@launch
            }

            val sEpisodes = try {
                source.getEpisodeList(sAnime)
            } catch (_: Exception) {
                try {
                    val details = source.getAnimeDetails(sAnime)
                    source.getEpisodeList(details)
                } catch (_: Exception) { emptyList() }
            }

            _preFetchedExtensionData[anime.id] = PreFetchedExtensionData(source, sAnime, sEpisodes)
            _preFetchedEpisodeNumbers.value += (anime.id to sEpisodes.map { it.episode_number.toInt() }.toSet())
        }
    }

    // ─── Magnet Extension Support ────────────────────────────────────────
    // State holders (internal so viewmodel/MainViewModelMagnetExt.kt can access them).
    // Method implementations live in that file.
    internal var magnetExtensionClient: MagnetExtensionClient? = null

    internal val _availableMagnetExtensions = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val availableMagnetExtensions: StateFlow<List<Pair<String, String>>> = _availableMagnetExtensions.asStateFlow()

    val defaultMagnetExtension: StateFlow<String?> get() = userPreferences.defaultMagnetExtension

    internal val _magnetEpisodes = MutableStateFlow<Map<Int, MagnetData>>(emptyMap())
    val magnetEpisodes: StateFlow<Map<Int, MagnetData>> = _magnetEpisodes.asStateFlow()

    // Magnet extension methods — implementations live in viewmodel/MainViewModelMagnetExt.kt
    // (fun ensureMagnetClient, loadAvailableMagnetExtensions, parseTitleForSearch,
    //  fetchMagnetEpisodes, fetchMagnetEpisodesSync, clearMagnetEpisodes,
    //  getMagnetEpisodeNumbers, getMagnetForEpisode, fetchMagnetForEpisode,
    //  fetchStreamUrlForEpisode)

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: android.net.Network) {
            _isOffline.value = false
        }

        override fun onLost(network: android.net.Network) {
            _isOffline.value = true
        }

        override fun onCapabilitiesChanged(network: android.net.Network, capabilities: NetworkCapabilities) {
            val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            _isOffline.value = !hasInternet
        }
    }

    internal var apiRetryJob: Job? = null

    // Sync queue for debounced AniList API calls
    internal data class PendingSync(val type: String, val mediaId: Int, val malId: Int? = null, val status: String? = null, val progress: Int? = null, val score: Int? = null, val entryId: Int? = null, val favoriteAdded: Boolean? = null)
    internal val pendingSyncs = mutableMapOf<Int, PendingSync>() // mediaId -> pending sync
    internal var syncJob: Job? = null
    internal var favoriteSyncJob: Job? = null

    // MAL sync methods — implementations live in viewmodel/MainViewModelMalSyncExt.kt
    // (fun startApiRetryLoop, queueSync, executeFavoriteSyncs, executePendingSyncs,
    //  mapToMalStatus, mapFromMalStatus, fetchMalList, toggleMalFavoriteById,
    //  loadMalFavoritesFromCache)

    // Track last refresh time to prevent rapid re-fetches (persisted to survive app restarts)
    private var lastHomeRefreshTime: Long
        get() = userPreferences.getLastHomeRefreshTime()
        set(value) = userPreferences.setLastHomeRefreshTime(value)

    private var lastExploreRefreshTime: Long
        get() = userPreferences.getLastExploreRefreshTime()
        set(value) = userPreferences.setLastExploreRefreshTime(value)

    // UI State
    internal val _userId = MutableStateFlow<Int?>(null)

    private val _userName = MutableStateFlow<String?>(null)
    val userName: StateFlow<String?> = _userName.asStateFlow()

    private val _userAvatar = MutableStateFlow<String?>(null)
    val userAvatar: StateFlow<String?> = _userAvatar.asStateFlow()

    private val _userBanner = MutableStateFlow<String?>(null)
    val userBanner: StateFlow<String?> = _userBanner.asStateFlow()

    private val _userBio = MutableStateFlow<String?>(null)
    val userBio: StateFlow<String?> = _userBio.asStateFlow()

    private val _userSiteUrl = MutableStateFlow<String?>(null)
    val userSiteUrl: StateFlow<String?> = _userSiteUrl.asStateFlow()

    private val _userCreatedAt = MutableStateFlow<Long?>(null)
    val userCreatedAt: StateFlow<Long?> = _userCreatedAt.asStateFlow()

    private val _isLoadingExplore = MutableStateFlow(false)
    val isLoadingExplore: StateFlow<Boolean> = _isLoadingExplore.asStateFlow()

    internal val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

    internal val _apiError = MutableStateFlow<String?>(null)
    val apiError: StateFlow<String?> = _apiError.asStateFlow()

    private val _isLoadingHome = MutableStateFlow(false)
    val isLoadingHome: StateFlow<Boolean> = _isLoadingHome.asStateFlow()

    private val _isLoadingSchedule = MutableStateFlow(false)
    val isLoadingSchedule: StateFlow<Boolean> = _isLoadingSchedule.asStateFlow()

    private val _splashReady = MutableStateFlow(false)
    val splashReady: StateFlow<Boolean> = _splashReady.asStateFlow()

    // Anime lists
    internal val _currentlyWatching = MutableStateFlow<List<AnimeMedia>>(emptyList())
    val currentlyWatching: StateFlow<List<AnimeMedia>> = _currentlyWatching.asStateFlow()

    internal val _planningToWatch = MutableStateFlow<List<AnimeMedia>>(emptyList())
    val planningToWatch: StateFlow<List<AnimeMedia>> = _planningToWatch.asStateFlow()

    internal val _completed = MutableStateFlow<List<AnimeMedia>>(emptyList())
    val completed: StateFlow<List<AnimeMedia>> = _completed.asStateFlow()

    internal val _onHold = MutableStateFlow<List<AnimeMedia>>(emptyList())
    val onHold: StateFlow<List<AnimeMedia>> = _onHold.asStateFlow()

    internal val _dropped = MutableStateFlow<List<AnimeMedia>>(emptyList())
    val dropped: StateFlow<List<AnimeMedia>> = _dropped.asStateFlow()

    // Offline anime lists (for logged-out users)
    private val _offlineCurrentlyWatching = MutableStateFlow<List<AnimeMedia>>(emptyList())
    val offlineCurrentlyWatching: StateFlow<List<AnimeMedia>> = _offlineCurrentlyWatching.asStateFlow()

    private val _offlinePlanningToWatch = MutableStateFlow<List<AnimeMedia>>(emptyList())
    val offlinePlanningToWatch: StateFlow<List<AnimeMedia>> = _offlinePlanningToWatch.asStateFlow()

    private val _offlineCompleted = MutableStateFlow<List<AnimeMedia>>(emptyList())
    val offlineCompleted: StateFlow<List<AnimeMedia>> = _offlineCompleted.asStateFlow()

    private val _offlineOnHold = MutableStateFlow<List<AnimeMedia>>(emptyList())
    val offlineOnHold: StateFlow<List<AnimeMedia>> = _offlineOnHold.asStateFlow()

    private val _offlineDropped = MutableStateFlow<List<AnimeMedia>>(emptyList())
    val offlineDropped: StateFlow<List<AnimeMedia>> = _offlineDropped.asStateFlow()

    // Explore data
    private val _featuredAnime = MutableStateFlow<List<ExploreAnime>>(emptyList())
    val featuredAnime: StateFlow<List<ExploreAnime>> = _featuredAnime.asStateFlow()

    private val _seasonalAnime = MutableStateFlow<List<ExploreAnime>>(emptyList())
    val seasonalAnime: StateFlow<List<ExploreAnime>> = _seasonalAnime.asStateFlow()

    private val _topSeries = MutableStateFlow<List<ExploreAnime>>(emptyList())
    val topSeries: StateFlow<List<ExploreAnime>> = _topSeries.asStateFlow()

    private val _topMovies = MutableStateFlow<List<ExploreAnime>>(emptyList())
    val topMovies: StateFlow<List<ExploreAnime>> = _topMovies.asStateFlow()

    private val _actionAnime = MutableStateFlow<List<ExploreAnime>>(emptyList())
    val actionAnime: StateFlow<List<ExploreAnime>> = _actionAnime.asStateFlow()

    private val _romanceAnime = MutableStateFlow<List<ExploreAnime>>(emptyList())
    val romanceAnime: StateFlow<List<ExploreAnime>> = _romanceAnime.asStateFlow()

    private val _comedyAnime = MutableStateFlow<List<ExploreAnime>>(emptyList())
    val comedyAnime: StateFlow<List<ExploreAnime>> = _comedyAnime.asStateFlow()

    private val _fantasyAnime = MutableStateFlow<List<ExploreAnime>>(emptyList())
    val fantasyAnime: StateFlow<List<ExploreAnime>> = _fantasyAnime.asStateFlow()

    private val _scifiAnime = MutableStateFlow<List<ExploreAnime>>(emptyList())
    val scifiAnime: StateFlow<List<ExploreAnime>> = _scifiAnime.asStateFlow()

    // Schedule
    private val _airingSchedule = MutableStateFlow<Map<Int, List<AiringScheduleAnime>>>(emptyMap())
    val airingSchedule: StateFlow<Map<Int, List<AiringScheduleAnime>>> = _airingSchedule.asStateFlow()

    private val _airingAnimeList = MutableStateFlow<List<AiringScheduleAnime>>(emptyList())
    val airingAnimeList: StateFlow<List<AiringScheduleAnime>> = _airingAnimeList.asStateFlow()

    // Other UI state
    internal val _userActivity = MutableStateFlow<List<UserActivity>>(emptyList())
    val userActivity: StateFlow<List<UserActivity>> = _userActivity.asStateFlow()

    internal val _userStats = MutableStateFlow<UserAnimeStats?>(null)
    val userStats: StateFlow<UserAnimeStats?> = _userStats.asStateFlow()

    // AniList Favorites
    internal val _aniListFavorites = MutableStateFlow<List<UserFavoriteAnime>>(emptyList())
    val aniListFavorites: StateFlow<List<UserFavoriteAnime>> = _aniListFavorites.asStateFlow()

    // Jikan (MAL) Favorites and History
    private val _jikanFavorites = MutableStateFlow<JikanUserFavorites?>(null)
    val jikanFavorites: StateFlow<JikanUserFavorites?> = _jikanFavorites.asStateFlow()

    private val _jikanHistory = MutableStateFlow<JikanUserHistory?>(null)
    val jikanHistory: StateFlow<JikanUserHistory?> = _jikanHistory.asStateFlow()

    private var jikanService: JikanService? = null
    private var malUsername: String? = null
    private val _malUsername = MutableStateFlow<String?>(null)

    private val _isFavoriteRateLimited = MutableStateFlow(false)
    val isFavoriteRateLimited: StateFlow<Boolean> = _isFavoriteRateLimited.asStateFlow()

    // Preferences Delegations
    val authToken: StateFlow<String?> get() = userPreferences.authToken
    val themeMode: StateFlow<String> get() = userPreferences.themeMode
    val isOled: StateFlow<Boolean> get() = userPreferences.isOled
    val disableMaterialColors: StateFlow<Boolean> get() = userPreferences.disableMaterialColors
    val preferredCategory: StateFlow<String> get() = userPreferences.preferredCategory
    val showStatusColors: StateFlow<Boolean> get() = userPreferences.showStatusColors
    val showAnimeCardButtons: StateFlow<Boolean> get() = userPreferences.showAnimeCardButtons
    val preferEnglishTitles: StateFlow<Boolean> get() = userPreferences.preferEnglishTitles
    val preventScheduleSync: StateFlow<Boolean> get() = userPreferences.preventScheduleSync
    val trackingPercentage: StateFlow<Int> get() = userPreferences.trackingPercentage
    val forwardSkipSeconds: StateFlow<Int> get() = userPreferences.forwardSkipSeconds
    val backwardSkipSeconds: StateFlow<Int> get() = userPreferences.backwardSkipSeconds
    val simplifyEpisodeMenu: StateFlow<Boolean> get() = userPreferences.simplifyEpisodeMenu
    val autoSkipOpening: StateFlow<Boolean> get() = userPreferences.autoSkipOpening
    val autoSkipEnding: StateFlow<Boolean> get() = userPreferences.autoSkipEnding
    val autoPlayNextEpisode: StateFlow<Boolean> get() = userPreferences.autoPlayNextEpisode
    val localFavorites: StateFlow<Map<Int, StoredFavorite>> get() = userPreferences.localFavorites
    val localAnimeStatus: StateFlow<Map<Int, LocalAnimeEntry>> get() = userPreferences.localAnimeStatus
    val defaultExtensionPackage: StateFlow<String> get() = userPreferences.defaultExtensionPackage
    val defaultSubtitleLang: StateFlow<String> get() = userPreferences.defaultSubtitleLang
    val downloadPreferredCategory: StateFlow<String> get() = userPreferences.downloadPreferredCategory
    val downloadSubtitleLang: StateFlow<String> get() = userPreferences.downloadSubtitleLang
    val hideAdultContent: StateFlow<Boolean> get() = userPreferences.hideAdultContent
    val startupScreen: StateFlow<Int> get() = userPreferences.startupScreen

    // Buffer Settings
    val bufferAheadSeconds: StateFlow<Int> get() = userPreferences.bufferAheadSeconds
    val bufferSizeMb: StateFlow<Int> get() = userPreferences.bufferSizeMb
    val showBufferIndicator: StateFlow<Boolean> get() = userPreferences.showBufferIndicator
    val checkUpdatesOnStart: StateFlow<Boolean> get() = userPreferences.checkUpdatesOnStart
    val autoUpdateExtensions: StateFlow<Boolean> get() = userPreferences.autoUpdateExtensions
    val streamMethod: StateFlow<String> get() = userPreferences.streamMethod
    val downloadDirectoryUri: StateFlow<String?> get() = userPreferences.downloadDirectoryUri
    val keepDownloadedFiles: StateFlow<Boolean> get() = userPreferences.keepDownloadedFiles

    // Notification tap events
    private val _notificationAnimeTaps = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 1)
    val notificationAnimeTaps: SharedFlow<String> = _notificationAnimeTaps.asSharedFlow()

    fun onNotificationAnimeTap(animeName: String) {
        _notificationAnimeTaps.tryEmit(animeName)
    }

    private val _openExtensionsEvents = Channel<Unit>(Channel.BUFFERED)
    val openExtensionsEvents: Flow<Unit> = _openExtensionsEvents.receiveAsFlow()

    fun requestOpenExtensions() {
        _openExtensionsEvents.trySend(Unit)
    }
    val swipeVolume: StateFlow<Boolean> get() = userPreferences.swipeVolume
    val swipeBrightness: StateFlow<Boolean> get() = userPreferences.swipeBrightness
    val swipeSwap: StateFlow<Boolean> get() = userPreferences.swipeSwap

    // Pending update from startup check
    private val _pendingUpdateRelease = MutableStateFlow<GitHubRelease?>(null)
    val pendingUpdateRelease: StateFlow<GitHubRelease?> = _pendingUpdateRelease.asStateFlow()

    val playbackPositions: StateFlow<Map<String, Long>> get() = cacheManager.playbackPositions

    // Cache methods — implementations live in viewmodel/MainViewModelCacheExt.kt
    // (fun getCacheDataSourceFactory, getDownloadCacheSize, clearDownloadCache,
    //  savePlaybackPosition, … clearNonEssentialCaches)

    // MAL API Service
    internal lateinit var malApiService: MalApiService

    // Login provider tracking
    internal val _loginProvider = MutableStateFlow(LoginProvider.NONE)
    val loginProvider: StateFlow<LoginProvider> = _loginProvider.asStateFlow()

    // MAL favorites (stored locally with full anime data)
    internal val _malFavorites = MutableStateFlow<List<AnimeMedia>>(emptyList())
    val malFavorites: StateFlow<List<AnimeMedia>> = _malFavorites.asStateFlow()

    // Toast messages for UI feedback
    internal val _toastMessage = MutableSharedFlow<String>()
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    private val _logoutEvent = MutableSharedFlow<Unit>()
    val logoutEvent: SharedFlow<Unit> = _logoutEvent.asSharedFlow()

    // Card bounds for shared element transition
    data class CardBounds(val animeId: Int, val coverUrl: String, val bounds: android.graphics.RectF)
    private val _exploreAnimeCardBounds = MutableStateFlow<CardBounds?>(null)
    val exploreAnimeCardBounds: StateFlow<CardBounds?> = _exploreAnimeCardBounds.asStateFlow()

    private val _homeAnimeCardBounds = MutableStateFlow<CardBounds?>(null)
    val homeAnimeCardBounds: StateFlow<CardBounds?> = _homeAnimeCardBounds.asStateFlow()

    private val _hideNavbar = MutableStateFlow(false)
    val hideNavbar: StateFlow<Boolean> = _hideNavbar.asStateFlow()

    fun setHideNavbar(hide: Boolean) {
        _hideNavbar.value = hide
    }

    fun setExploreAnimeCardBounds(animeId: Int, coverUrl: String, bounds: android.graphics.RectF?) {
        if (bounds != null && bounds.width() > 0 && bounds.height() > 0) {
            _exploreAnimeCardBounds.value = CardBounds(animeId, coverUrl, bounds)
        }
    }

    fun setHomeAnimeCardBounds(animeId: Int, coverUrl: String, bounds: android.graphics.RectF?) {
        if (bounds != null && bounds.width() > 0 && bounds.height() > 0) {
            _homeAnimeCardBounds.value = CardBounds(animeId, coverUrl, bounds)
        }
    }

    fun clearExploreAnimeCardBounds() {
        _exploreAnimeCardBounds.value = null
    }

    fun clearHomeAnimeCardBounds() {
        _homeAnimeCardBounds.value = null
    }

    // Is logged in (either AniList or MAL)
    val isLoggedIn: Boolean get() = _loginProvider.value != LoginProvider.NONE

    fun init(context: Context, hasToken: Boolean) {
        this.context = context.applicationContext
        userPreferences = UserPreferences(context)
        cacheManager = CacheManager(userPreferences.getSharedPreferences())
        repository = AnimeRepository(userPreferences, cacheManager)
        malApiService = MalApiService(context)
        jikanService = JikanService()
        sourceManager = com.blissless.tensei.stream.SourceManager(context)

        // Initialize video cache for offline playback
        cacheManager.initializeVideoCache(context)

        // Initialize download manager
        episodeDownloadManager = EpisodeDownloadManager(context)
        episodeDownloadManager.initialize()

        // Check connectivity and register callback for auto-detection
        checkConnectivity()
        registerConnectivityCallback()
        startApiRetryLoop()

        userPreferences.loadPreferences(hasToken)
        cacheManager.loadStreamCache()
        cacheManager.loadExtensionStreamCache()
        cacheManager.loadPlaybackPositions()
        cacheManager.loadTmdbEpisodeCache()
        loadAiringScheduleCache()
        updateOfflineLists()

        // Check login provider
        if (hasToken) {
            _loginProvider.value = LoginProvider.ANILIST
        } else if (malApiService.getAuthManager().isLoggedIn) {
            _loginProvider.value = LoginProvider.MAL
            loadMalUserData()
        }

        viewModelScope.launch {
            // Pre-load extension sources so they're available when episode screen opens
            preLoadExtensionSources()
            // Run home data, explore data, and airing schedule in PARALLEL for faster startup
            val homeDeferred = async {
                if (hasToken || _loginProvider.value != LoginProvider.NONE) {
                    loadHomeDataWithCache()
                    if (hasToken) {
                        loadAniListFavoritesFromStorage()
                    }
                } else {
                    // prefetchOfflineWatchingStreams() // Disabled for now
                }
            }

            val exploreDeferred = async { loadExploreDataWithCache() }
            val scheduleDeferred = async { fetchAiringSchedule() }

            // Wait for all to complete (they run in parallel)
            homeDeferred.await()
            exploreDeferred.await()
            scheduleDeferred.await()

            _splashReady.value = true

            // Fetch AniList favorites in background after initial load completes
            if (hasToken) {
                fetchAniListFavorites()
            }

            // Check for updates on start if enabled
            if (userPreferences.checkUpdatesOnStart.value) {
                checkForUpdatesSilently()
            }
        }
    }

    fun checkForUpdatesSilently() {
        viewModelScope.launch {
            try {
                val url = "https://api.github.com/repos/Suntrax/tensei/releases/latest"
                val request = okhttp3.Request.Builder().url(url)
                    .header("Accept", "application/vnd.github.v3+json")
                    .build()
                val response = withContext(Dispatchers.IO) {
                    OkHttpClient().newCall(request).execute()
                }
                if (!response.isSuccessful) return@launch
                val body = withContext(Dispatchers.IO) { response.body.string() }
                val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                val release = json.decodeFromString<GitHubRelease>(body)
                val currentVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
                val cleanTag = release.tagName.removePrefix("v").removePrefix("V")
                val parts1 = cleanTag.split(".").map { it.toIntOrNull() ?: 0 }
                val parts2 = currentVersion.split(".").map { it.toIntOrNull() ?: 0 }
                val maxLen = maxOf(parts1.size, parts2.size)
                var cmp = 0
                for (i in 0 until maxLen) {
                    val p1 = parts1.getOrElse(i) { 0 }
                    val p2 = parts2.getOrElse(i) { 0 }
                    if (p1 != p2) { cmp = p1 - p2; break }
                }
                if (cmp > 0) {
                    _pendingUpdateRelease.value = release
                }
            } catch (_: Exception) { }
        }
    }

    private fun checkConnectivity() {
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val network = connectivityManager?.activeNetwork
            val capabilities = connectivityManager?.getNetworkCapabilities(network)
            val isConnected = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            _isOffline.value = !isConnected
        } catch (_: Exception) {
            _isOffline.value = false
        }
    }

    private fun registerConnectivityCallback() {
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityCallback = networkCallback
            connectivityManager?.registerNetworkCallback(networkRequest, networkCallback)
        } catch (_: Exception) {
        }
    }

    private fun loadMalUserData() {
        val malAuth = malApiService.getAuthManager()
        val userInfo = malAuth.userInfo.value
        if (userInfo != null) {
            _userName.value = userInfo.name
            _userAvatar.value = userInfo.picture
            malUsername = userInfo.name
            _malUsername.value = userInfo.name
            fetchJikanUserData()
        }
    }

    fun fetchJikanUserData() {
        val username = malUsername ?: return
        viewModelScope.launch {
            _jikanFavorites.value = jikanService?.getUserFavorites(username)
            _jikanHistory.value = jikanService?.getUserHistory(username)
        }
    }

    fun loginWithMal() {
        if (BuildConfig.MAL_CLIENT_ID.isBlank()) {
            return
        }
        val uri = malApiService.getAuthUrl(BuildConfig.MAL_CLIENT_ID)
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        context.startActivity(intent)
    }

    fun handleMalAuthAuthCode(uriString: String) {
        if (uriString.isEmpty()) {
            return
        }

        if (!uriString.startsWith("animescraper://success?code=") && !uriString.startsWith("animescraper://success")) {
            return
        }

        val code = uriString.substringAfter("code=").substringBefore("&")

        if (code.isEmpty()) {
            viewModelScope.launch { _toastMessage.emit("MAL login failed: No auth code received") }
            return
        }

        viewModelScope.launch {
            _toastMessage.emit("Completing MAL login...")
            val success = malApiService.exchangeCodeForToken(code, BuildConfig.MAL_CLIENT_ID, null)
            if (success) {
                userPreferences.clearToken()
                _loginProvider.value = LoginProvider.MAL
                loadMalUserData()
                fetchMalList()
                // prefetchOfflineWatchingStreams() // Disabled for now
                _toastMessage.emit("Successfully logged into MyAnimeList!")
            } else {
                _toastMessage.emit("MAL login failed: Token exchange error")
            }
        }
    }

    fun loginWithAniList() {
        // Clear MAL data if switching
        if (_loginProvider.value == LoginProvider.MAL) {
            malApiService.getAuthManager().clearToken()
            _malFavorites.value = emptyList()
        }

        val url = "https://anilist.co/api/v2/oauth/authorize?client_id=$CLIENT_ID&response_type=token"
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
    }

    fun handleAuthRedirect(intent: Intent?) {
        intent?.dataString?.takeIf { it.startsWith("animescraper://success") }?.let { uri ->
            uri.replace("#", "?").toUri().getQueryParameter("access_token")?.let { token ->
                userPreferences.saveToken(token)
                _loginProvider.value = LoginProvider.ANILIST
                viewModelScope.launch {
                    _isLoadingHome.value = true
                    fetchUser()
                    fetchLists()
                    _isLoadingHome.value = false
                    // prefetchContinueWatchingStreams() // Disabled for now
                }
            }
        }
    }

    fun logout() {
        when (_loginProvider.value) {
            LoginProvider.ANILIST -> {
                userPreferences.clearAllUserData()
                userPreferences.clearToken()
            }
            LoginProvider.MAL -> {
                malApiService.getAuthManager().clearToken()
                userPreferences.clearMalFavorites()
                _malFavorites.value = emptyList()
                _jikanFavorites.value = null
                _jikanHistory.value = null
                malUsername = null
                _malUsername.value = null
            }
            LoginProvider.NONE -> {}
        }

        cacheManager.clearAllCaches()
        _loginProvider.value = LoginProvider.NONE
        _userId.value = null; _userName.value = null; _userAvatar.value = null
        _userBanner.value = null; _userBio.value = null; _userSiteUrl.value = null; _userCreatedAt.value = null
        _currentlyWatching.value = emptyList(); _planningToWatch.value = emptyList(); _completed.value = emptyList(); _onHold.value = emptyList(); _dropped.value = emptyList()
        _aniListFavorites.value = emptyList()
        _isLoadingHome.value = false

        viewModelScope.launch {
            _logoutEvent.emit(Unit)
        }
    }

    fun updateOfflineLists() {
        val statusMap = userPreferences.getAllLocalAnimeStatus()
        val cache = cacheManager.detailedAnimeCache.value
        val favorites = userPreferences.localFavorites.value

        val currentlyWatching = mutableListOf<AnimeMedia>()
        val planningToWatch = mutableListOf<AnimeMedia>()
        val completed = mutableListOf<AnimeMedia>()
        val onHold = mutableListOf<AnimeMedia>()
        val dropped = mutableListOf<AnimeMedia>()

        statusMap.forEach { (id, entry) ->
            val cachedAnime = cache[id]
            val favorite = favorites[id]

            val anime = if (cachedAnime != null) {
                AnimeMedia(
                    id = cachedAnime.id,
                    title = cachedAnime.title,
                    titleEnglish = cachedAnime.titleEnglish,
                    cover = cachedAnime.cover,
                    banner = cachedAnime.banner,
                    progress = entry.progress,
                    totalEpisodes = cachedAnime.episodes,
                    latestEpisode = cachedAnime.latestEpisode ?: cachedAnime.nextAiringEpisode?.let { it - 1 },
                    status = cachedAnime.status ?: "",
                    averageScore = cachedAnime.averageScore,
                    genres = cachedAnime.genres,
                    listStatus = entry.status,
                    year = cachedAnime.year,
                    malId = cachedAnime.malId,
                    format = cachedAnime.format
                )
            } else if (favorite != null) {
                AnimeMedia(
                    id = favorite.id,
                    title = favorite.title,
                    cover = favorite.cover,
                    banner = favorite.banner,
                    progress = entry.progress,
                    totalEpisodes = entry.totalEpisodes,
                    listStatus = entry.status,
                    year = favorite.year,
                    averageScore = favorite.averageScore
                )
            } else if (entry.title.isNotEmpty()) {
                // Use data stored in LocalAnimeEntry itself
                AnimeMedia(
                    id = entry.id,
                    title = entry.title,
                    cover = entry.cover,
                    banner = entry.banner,
                    progress = entry.progress,
                    totalEpisodes = entry.totalEpisodes,
                    listStatus = entry.status,
                    year = entry.year,
                    averageScore = entry.averageScore
                )
            } else {
                null
            }

            if (anime != null) {
                when (entry.status) {
                    "CURRENT" -> currentlyWatching.add(anime)
                    "PLANNING" -> planningToWatch.add(anime)
                    "COMPLETED" -> completed.add(anime)
                    "PAUSED" -> onHold.add(anime)
                    "DROPPED" -> dropped.add(anime)
                }
            }
        }

        _offlineCurrentlyWatching.value = currentlyWatching.sortedByDescending { it.averageScore ?: 0 }
        _offlinePlanningToWatch.value = planningToWatch.sortedByDescending { it.averageScore ?: 0 }
        _offlineCompleted.value = completed.sortedByDescending { it.averageScore ?: 0 }
        _offlineOnHold.value = onHold.sortedByDescending { it.averageScore ?: 0 }
        _offlineDropped.value = dropped.sortedByDescending { it.averageScore ?: 0 }

        // Prefetch streams for offline "Continue Watching" list
        // prefetchOfflineWatchingStreams() // Disabled for now
    }

    private suspend fun loadHomeDataWithCache() {
        cacheManager.loadHomeDataFromCache()?.let {
            updateHomeState(it)
        }

        val now = System.currentTimeMillis()
        if (now - lastHomeRefreshTime < MIN_REFRESH_INTERVAL_MS) {
            _isLoadingHome.value = false
            refreshReleasingAnimeProgress()
            // prefetchContinueWatchingStreams() // Disabled for now
            return
        }

        _isLoadingHome.value = true
        val userSuccess = fetchUser()
        val listsSuccess = fetchLists()
        _isLoadingHome.value = false

        if (userSuccess && listsSuccess) {
            lastHomeRefreshTime = System.currentTimeMillis()
        }

        refreshReleasingAnimeProgress()
        // prefetchContinueWatchingStreams() // Disabled for now
    }

    private fun updateHomeState(data: HomeCacheData) {
        _currentlyWatching.value = data.currentlyWatching
        _planningToWatch.value = data.planningToWatch
        _completed.value = data.completed
        _onHold.value = data.onHold
        _dropped.value = data.dropped
        _userId.value = data.userId
        _userName.value = data.userName
        _userAvatar.value = data.userAvatar
    }

    private fun loadExploreDataWithCache() {
        val cachedData = cacheManager.loadExploreDataFromCache()
        if (cachedData != null) {
            updateExploreState(cachedData)
        } else {
            fetchExploreData(force = true)
        }
    }

    private fun updateExploreState(data: ExploreCacheData) {
        _featuredAnime.value = data.featuredAnime
        _seasonalAnime.value = data.seasonalAnime
        _topSeries.value = data.topSeries
        _topMovies.value = data.topMovies
        _actionAnime.value = data.actionAnime
        _romanceAnime.value = data.romanceAnime
        _comedyAnime.value = data.comedyAnime
        _fantasyAnime.value = data.fantasyAnime
        _scifiAnime.value = data.scifiAnime
    }

    private fun loadAiringScheduleCache() {
        cacheManager.loadAiringScheduleCache()?.let {
            _airingSchedule.value = it.scheduleByDay
            _airingAnimeList.value = it.airingAnimeList
        }
    }

    // API calls
    suspend fun fetchUser(): Boolean {
        val result = repository.fetchUser()?.let {
            _userId.value = it.data.Viewer.id
            _userName.value = it.data.Viewer.name
            _userAvatar.value = it.data.Viewer.avatar?.large ?: it.data.Viewer.avatar?.medium
            _userBanner.value = it.data.Viewer.bannerImage
            _userBio.value = it.data.Viewer.about
            _userSiteUrl.value = it.data.Viewer.siteUrl
            _userCreatedAt.value = it.data.Viewer.createdAt
            it.data.Viewer.statistics?.anime?.let { stats ->
                _userStats.value = stats
            }
            true
        } ?: false
        return result
    }

    suspend fun fetchLists(): Boolean {
        val userId = _userId.value ?: return false
        val response = repository.fetchMediaLists(userId) ?: return false

        val grouped = response.data.MediaListCollection.lists.flatMap { list ->
            list.entries.map { entry ->
                val anime = AnimeMedia(
                    id = entry.mediaId,
                    title = entry.media.title.romaji ?: entry.media.title.english ?: "Unknown",
                    titleEnglish = entry.media.title.english,
                    cover = entry.media.coverImage?.extraLarge ?: "",
                    banner = entry.media.bannerImage,
                    progress = entry.progress ?: 0,
                    totalEpisodes = entry.media.episodes ?: 0,
                    latestEpisode = entry.media.nextAiringEpisode?.episode?.let { it - 1 },
                    status = entry.media.status ?: "",
                    averageScore = entry.media.averageScore,
                    genres = entry.media.genres ?: emptyList(),
                    listStatus = list.status ?: list.name,
                    listEntryId = entry.id,
                    year = entry.media.seasonYear,
                    malId = entry.media.idMal
                )
                (list.status ?: list.name) to anime
            }
        }.groupBy({ it.first }, { it.second })

        _currentlyWatching.value = grouped["CURRENT"] ?: grouped["Watching"] ?: emptyList()
        _planningToWatch.value = grouped["PLANNING"] ?: grouped["Plan to Watch"] ?: emptyList()
        _completed.value = grouped["COMPLETED"] ?: emptyList()
        _onHold.value = grouped["PAUSED"] ?: emptyList()
        _dropped.value = grouped["DROPPED"] ?: emptyList()
        saveHomeDataToCache()
        return true
    }

    fun fetchExploreData(force: Boolean = false) {
        val now = System.currentTimeMillis()

        if (!force && now - lastExploreRefreshTime < MIN_REFRESH_INTERVAL_MS) {
            return
        }

        viewModelScope.launch {
            _isLoadingExplore.value = true
            val (response, error) = repository.fetchBatchedExploreWithError(useCache = !force)
            if (response == null) {
                _isLoadingExplore.value = false
                _apiError.value = error ?: "Failed to load content"
                return@launch
            }

            val success = try {
                _featuredAnime.value = response.data.featured.media.map { mapExploreMedia(it) }
                _seasonalAnime.value = response.data.seasonal.media.map { mapExploreMedia(it) }
                _topSeries.value = response.data.topSeries.media.map { mapExploreMedia(it) }.filter { (it.averageScore ?: 0) >= 70 }
                _topMovies.value = response.data.topMovies.media.map { mapExploreMedia(it) }.filter { (it.averageScore ?: 0) >= 70 }
                _actionAnime.value = response.data.action.media.map { mapExploreMedia(it) }.filter { (it.averageScore ?: 0) >= 60 }
                _romanceAnime.value = response.data.romance.media.map { mapExploreMedia(it) }.filter { (it.averageScore ?: 0) >= 60 }
                _comedyAnime.value = response.data.comedy.media.map { mapExploreMedia(it) }.filter { (it.averageScore ?: 0) >= 60 }
                _fantasyAnime.value = response.data.fantasy.media.map { mapExploreMedia(it) }.filter { (it.averageScore ?: 0) >= 60 }
                _scifiAnime.value = response.data.scifi.media.map { mapExploreMedia(it) }.filter { (it.averageScore ?: 0) >= 60 }
                saveExploreDataToCache()
                _apiError.value = null
                true
            } catch (e: Exception) {
                _apiError.value = e.message ?: "Failed to load content"
                false
            }
            _isLoadingExplore.value = false
            if (success) {
                lastExploreRefreshTime = System.currentTimeMillis()
            }
        }
    }

    private fun mapExploreMedia(media: ExploreMedia): ExploreAnime {
        val title = media.title.romaji ?: media.title.english ?: "Unknown"
        val episodes = media.episodes ?: 0
        val latestEpisode = media.nextAiringEpisode?.episode?.let { it - 1 }

        return ExploreAnime(
            id = media.id,
            title = title,
            titleEnglish = media.title.english,
            cover = media.coverImage?.extraLarge ?: "",
            banner = media.bannerImage,
            episodes = episodes,
            latestEpisode = latestEpisode,
            averageScore = media.averageScore,
            genres = media.genres ?: emptyList(),
            year = media.startDate?.year ?: media.seasonYear,
            malId = media.idMal,
            isAdult = media.isAdult,
            format = media.format
        )
    }

    fun fetchAiringSchedule(force: Boolean = false) {
        val now = System.currentTimeMillis()

        val cached = cacheManager.loadAiringScheduleCache()
        if (cached != null && !force && now - lastExploreRefreshTime < MIN_REFRESH_INTERVAL_MS) {
            return
        }

        viewModelScope.launch {
            _isLoadingSchedule.value = true
            try {
                val schedules = repository.fetchAiringSchedule()

                val airingList = schedules.filter { it.media != null }.map { schedule ->
                    val media = schedule.media!!
                    val title = media.title.romaji ?: media.title.english ?: "Unknown"
                    val titleEnglish = media.title.english
                    val episodes = media.episodes ?: 0

                    AiringScheduleAnime(
                        id = media.id,
                        title = title,
                        titleEnglish = titleEnglish,
                        cover = schedule.media.coverImage?.extraLarge ?: "",
                        episodes = episodes,
                        airingEpisode = schedule.episode,
                        airingAt = schedule.airingAt,
                        timeUntilAiring = schedule.timeUntilAiring,
                        averageScore = media.averageScore,
                        genres = media.genres ?: emptyList(),
                        year = media.seasonYear,
                        malId = media.idMal,
                        isAdult = media.isAdult
                    )
                }.sortedBy { it.airingAt }

                val scheduleByDay = airingList.groupBy { anime ->
                    val calendar = Calendar.getInstance().apply { timeInMillis = anime.airingAt * 1000L }
                    calendar.get(Calendar.DAY_OF_WEEK) - 1
                }

                _airingSchedule.value = scheduleByDay
                _airingAnimeList.value = airingList
                cacheManager.saveAiringScheduleCache(scheduleByDay, airingList)
                lastExploreRefreshTime = System.currentTimeMillis()
            } catch (_: Exception) {
                // Keep existing cached data on failure
            }
            _isLoadingSchedule.value = false
        }
    }

    fun updateAnimeProgress(mediaId: Int, progress: Int) {
        val currentEntry = userPreferences.getLocalAnimeStatus(mediaId)
        if (currentEntry != null) {
            val cachedAnime = cacheManager.detailedAnimeCache.value[mediaId]
            userPreferences.updateLocalAnimeProgress(mediaId, progress, cachedAnime?.episodes ?: currentEntry.totalEpisodes)
            updateOfflineLists()
        }

        // Immediately update progress in logged-in lists
        updateProgressInLists(mediaId, progress)

        val cachedAnime = cacheManager.detailedAnimeCache.value[mediaId]
        val malId = cachedAnime?.malId

        if (_loginProvider.value == LoginProvider.MAL && malId == null) {
            viewModelScope.launch {
                cacheManager.clearDetailedAnimeCache(mediaId)
                val details = fetchDetailedAnimeData(mediaId)
                var resolvedMalId = details?.malId

                if (resolvedMalId == null && details == null) {
                    val allAnime = _currentlyWatching.value + _planningToWatch.value + _completed.value + _onHold.value + _dropped.value
                    val animeFromList = allAnime.find { it.id == mediaId }
                    if (animeFromList != null) {
                        resolvedMalId = jikanService?.searchAnimeByTitle(animeFromList.title)
                    }
                }

                if (resolvedMalId != null) {
                    queueSync(mediaId, "progress", malId = resolvedMalId, progress = progress)
                }
            }
            return
        }

        queueSync(mediaId, "progress", malId = malId, progress = progress)
    }

    private fun updateProgressInLists(mediaId: Int, progress: Int) {
        val updateInList: (MutableStateFlow<List<AnimeMedia>>, (AnimeMedia) -> AnimeMedia) -> Unit = { list, updater ->
            list.value = list.value.map { if (it.id == mediaId) updater(it) else it }
        }

        updateInList(_currentlyWatching) { it.copy(progress = progress) }
        updateInList(_planningToWatch) { it.copy(progress = progress) }
        updateInList(_completed) { it.copy(progress = progress) }
        updateInList(_onHold) { it.copy(progress = progress) }
        updateInList(_dropped) { it.copy(progress = progress) }
    }

    fun updateAnimeStatus(mediaId: Int, status: String, progress: Int? = null) {
        val currentEntry = userPreferences.getLocalAnimeStatus(mediaId)
        val cachedAnime = cacheManager.detailedAnimeCache.value[mediaId]
        val malId = cachedAnime?.malId

        if (_loginProvider.value == LoginProvider.MAL && malId == null) {
            viewModelScope.launch {
                cacheManager.clearDetailedAnimeCache(mediaId)
                val details = fetchDetailedAnimeData(mediaId)
                var resolvedMalId = details?.malId

                if (resolvedMalId == null && details == null) {
                    val allAnime = _currentlyWatching.value + _planningToWatch.value + _completed.value + _onHold.value + _dropped.value
                    val animeFromList = allAnime.find { it.id == mediaId }
                    if (animeFromList != null) {
                        resolvedMalId = jikanService?.searchAnimeByTitle(animeFromList.title)
                    }
                } else if (resolvedMalId == null) {
                    resolvedMalId = jikanService?.searchAnimeByTitle(details.title)
                }

                setLocalAnimeStatus(
                    mediaId,
                    LocalAnimeEntry(
                        id = mediaId,
                        status = status,
                        progress = progress ?: currentEntry?.progress ?: 0,
                        totalEpisodes = details?.episodes ?: currentEntry?.totalEpisodes ?: 0
                    )
                )
                moveAnimeBetweenLists(mediaId, status, progress)
                queueSync(mediaId, "status", malId = resolvedMalId, status = status, progress = progress)
            }
            return
        }

        setLocalAnimeStatus(
            mediaId,
            LocalAnimeEntry(
                id = mediaId,
                status = status,
                progress = progress ?: currentEntry?.progress ?: 0,
                totalEpisodes = cachedAnime?.episodes ?: currentEntry?.totalEpisodes ?: 0
            )
        )

        // Immediately update logged-in lists for instant visual feedback
        moveAnimeBetweenLists(mediaId, status, progress)

        queueSync(mediaId, "status", malId = malId, status = status, progress = progress)
    }

    private fun moveAnimeBetweenLists(mediaId: Int, newStatus: String, newProgress: Int?) {
        // Find the anime in the current list
        val allLists = listOf(
            _currentlyWatching.value to { l: List<AnimeMedia> -> _currentlyWatching.value = l },
            _planningToWatch.value to { l: List<AnimeMedia> -> _planningToWatch.value = l },
            _completed.value to { l: List<AnimeMedia> -> _completed.value = l },
            _onHold.value to { l: List<AnimeMedia> -> _onHold.value = l },
            _dropped.value to { l: List<AnimeMedia> -> _dropped.value = l }
        )

        var anime: AnimeMedia? = null
        var sourceListIndex = -1

        for ((index, pair) in allLists.withIndex()) {
            val (list, _) = pair
            val found = list.find { it.id == mediaId }
            if (found != null) {
                anime = found
                sourceListIndex = index
                break
            }
        }

        // If anime not found in any list, create a new entry from cached/local data
        if (anime == null) {
            val localEntry = userPreferences.getLocalAnimeStatus(mediaId)
            val cachedAnime = cacheManager.detailedAnimeCache.value[mediaId]

            anime = if (cachedAnime != null) {
                AnimeMedia(
                    id = cachedAnime.id,
                    title = cachedAnime.title,
                    titleEnglish = cachedAnime.titleEnglish,
                    cover = cachedAnime.cover,
                    banner = cachedAnime.banner,
                    progress = newProgress ?: 0,
                    totalEpisodes = cachedAnime.episodes,
                    latestEpisode = cachedAnime.nextAiringEpisode,
                    status = cachedAnime.status ?: "",
                    averageScore = cachedAnime.averageScore,
                    genres = cachedAnime.genres,
                    listStatus = newStatus,
                    year = cachedAnime.year,
                    malId = cachedAnime.malId,
                    format = cachedAnime.format
                )
            } else if (localEntry != null) {
                AnimeMedia(
                    id = localEntry.id,
                    title = localEntry.title.ifEmpty { "Unknown" },
                    cover = localEntry.cover,
                    banner = localEntry.banner,
                    progress = newProgress ?: localEntry.progress,
                    totalEpisodes = localEntry.totalEpisodes,
                    listStatus = newStatus,
                    year = localEntry.year,
                    averageScore = localEntry.averageScore
                )
            } else {
                // No data available, skip
                return
            }
        }

        // If anime was found in a source list, remove from it first
        if (sourceListIndex >= 0) {
            val (sourceList, sourceSetter) = allLists[sourceListIndex]
            sourceSetter(sourceList.filter { it.id != mediaId })
        }

        // Update the anime with new status and progress
        val updatedAnime = anime.copy(
            listStatus = newStatus,
            progress = newProgress ?: anime.progress
        )

        // Add to target list
        val targetList = when (newStatus) {
            "CURRENT" -> _currentlyWatching
            "PLANNING" -> _planningToWatch
            "COMPLETED" -> _completed
            "PAUSED" -> _onHold
            "DROPPED" -> _dropped
            else -> return
        }

        targetList.value += updatedAnime
    }

    fun removeAnimeFromList(mediaId: Int) {
        val entryId = (currentlyWatching.value + planningToWatch.value + completed.value + onHold.value + dropped.value)
            .find { it.id == mediaId }?.listEntryId

        // Immediately remove from all lists for instant feedback
        _currentlyWatching.value = _currentlyWatching.value.filter { it.id != mediaId }
        _planningToWatch.value = _planningToWatch.value.filter { it.id != mediaId }
        _completed.value = _completed.value.filter { it.id != mediaId }
        _onHold.value = _onHold.value.filter { it.id != mediaId }
        _dropped.value = _dropped.value.filter { it.id != mediaId }

        setLocalAnimeStatus(mediaId, null)
        if (entryId != null) {
            queueSync(mediaId, "delete", entryId = entryId)
        }
    }

    fun discardLocalChanges() {
        userPreferences.clearLocalAnimeStatus()
        updateOfflineLists()
    }

    fun addLocalToAniListOnlyNew() {
        viewModelScope.launch {
            val localStatus = localAnimeStatus.value
            val allAniListEntries = currentlyWatching.value + planningToWatch.value + completed.value + onHold.value + dropped.value

            for ((mediaId, entry) in localStatus) {
                if (allAniListEntries.none { it.id == mediaId }) {
                    repository.updateStatus(mediaId, entry.status, entry.progress)
                }
            }
            userPreferences.clearLocalAnimeStatus()
            updateOfflineLists()
            fetchLists()
        }
    }

    fun overwriteAniListWithLocal() {
        viewModelScope.launch {
            val localStatus = localAnimeStatus.value
            val allAniListEntries = currentlyWatching.value + planningToWatch.value + completed.value + onHold.value + dropped.value

            for (entry in allAniListEntries) {
                val localEntry = localStatus[entry.id]
                if (localEntry != null) {
                    repository.updateStatus(entry.id, localEntry.status, localEntry.progress)
                }
            }

            for ((mediaId, entry) in localStatus) {
                if (allAniListEntries.none { it.id == mediaId }) {
                    repository.updateStatus(mediaId, entry.status, entry.progress)
                }
            }

            userPreferences.clearLocalAnimeStatus()
            updateOfflineLists()
            fetchLists()
        }
    }

    // Settings — implementations live in viewmodel/MainViewModelSettingsExt.kt
    // (fun setThemeMode, setDefaultExtensionPackage, invalidateAllStreamCaches,
    //  setDisableMaterialColors, … setDefaultMagnetExtension)

    // Favorites
    fun toggleLocalFavorite(mediaId: Int) {
        userPreferences.toggleLocalFavorite(mediaId)
        updateOfflineLists()
    }

    fun toggleOfflineFavorite(animeId: Int, title: String, cover: String, banner: String?, year: Int?, averageScore: Int?) {
        userPreferences.toggleLocalFavorite(animeId, title, cover, banner, year, averageScore)
        updateOfflineLists()
    }

    fun setLocalAnimeStatus(mediaId: Int, entry: LocalAnimeEntry?) {
        userPreferences.setLocalAnimeStatus(mediaId, entry)
        updateOfflineLists()
    }

    // Playback
    val playbackDurations: StateFlow<Map<String, Long>> get() = cacheManager.playbackDurations
    val startedAt: StateFlow<Map<String, Long>> get() = cacheManager.startedAt
    // Playback position & stream cache — implementations live in viewmodel/MainViewModelCacheExt.kt
    // (fun savePlaybackPosition, getPlaybackPosition, clearPlaybackPosition,
    //  removeContinueWatchingEntry, invalidateStreamCache, getCachedExtensionStream,
    //  cacheExtensionStream, invalidateExtensionStreamCache, clearAnimeExtensionStreamCaches,
    //  clearAllExtensionStreamCaches, removeFromVideoCache, getVideoCacheSize, clearNonEssentialCaches)

    /**
     * Get stream for a specific server.
     * Uses the watchPath from the provider.
     */

    /**
     * Get stream using Miruro. Scrapes preferred first, then other in background.
     */

    suspend fun fetchDetailedAnimeData(animeId: Int, malId: Int? = null): DetailedAnimeData? {
        var media = repository.fetchDetailedAnime(animeId)
        
        // If not found and have MAL ID, try finding by MAL ID
        if (media == null && malId != null && malId > 0) {
            val foundMedia = repository.findAnimeByMalId(malId)
            if (foundMedia != null) {
                media = repository.fetchDetailedAnime(foundMedia.id)
            }
        }
        
        if (media == null) {
            return null
        }
        val relationsList = media.relations?.edges?.map { edge ->
            edge.node.let { node ->
                AnimeRelation(
                    id = node.id,
                    title = node.title?.english ?: node.title?.romaji ?: "Unknown",
                    cover = node.coverImage?.extraLarge ?: "",
                    episodes = node.episodes,
                    latestEpisode = node.nextAiringEpisode?.episode?.let { it - 1 },
                    averageScore = node.averageScore,
                    format = node.format,
                    relationType = edge.relationType ?: "UNKNOWN"
                )
            }
        } ?: emptyList()
        val detailedData = DetailedAnimeData(
            id = media.id,
            malId = media.idMal,
            title = media.title?.romaji ?: media.title?.english ?: "Unknown",
            titleRomaji = media.title?.romaji, titleEnglish = media.title?.english, titleNative = media.title?.native,
            cover = media.coverImage?.extraLarge ?: "", banner = media.bannerImage, description = media.description,
            episodes = media.episodes ?: 0, duration = media.duration, status = media.status,
            averageScore = media.averageScore, popularity = media.popularity, favourites = media.favourites,
            genres = media.genres ?: emptyList(), tags = media.tags ?: emptyList(), season = media.season, year = media.seasonYear ?: media.startDate?.year,
            format = media.format, source = media.source,
            studios = media.studios?.nodes?.map { StudioData(it.id ?: 0, it.name ?: "") } ?: emptyList(),
            startDate = media.startDate?.let { "${it.year}-${it.month}-${it.day}" },
            endDate = media.endDate?.let { "${it.year}-${it.month}-${it.day}" },
            latestEpisode = media.nextAiringEpisode?.episode?.let { it - 1 },
            nextAiringEpisode = media.nextAiringEpisode?.episode, nextAiringTime = media.nextAiringEpisode?.airingAt,
            relations = relationsList,
            isAdult = media.isAdult,
            characters = media.characters,
            trailerUrl = media.trailer?.let {
                when (it.site) {
                    "youtube" -> "https://www.youtube.com/watch?v=${it.id}"
                    "dailymotion" -> "https://www.dailymotion.com/video/${it.id}"
                    else -> null
                }
            },
            trailerThumbnail = media.trailer?.let {
                if (it.site == "youtube" && it.id != null) {
                    "https://img.youtube.com/vi/${it.id}/maxresdefault.jpg"
                } else null
            },
            staff = media.staff
        )
        cacheManager.cacheDetailedAnime(animeId, detailedData)
        return detailedData
    }

    suspend fun fetchAnimeRelations(animeId: Int): List<AnimeRelation>? {
        return repository.fetchAnimeRelationsList(animeId)
    }

    suspend fun fetchDetailedAnimeDataByMalId(malId: Int): DetailedAnimeData? {
        val media = repository.findAnimeByMalId(malId) ?: return null
        return fetchDetailedAnimeData(media.id)
    }

    suspend fun fetchCharacter(characterId: Int): CharacterData? {
        cacheManager.getCachedCharacter(characterId)?.let { return it }
        val data = repository.fetchCharacter(characterId)
        if (data != null) cacheManager.cacheCharacter(characterId, data)
        return data
    }

    suspend fun fetchStaff(staffId: Int): StaffData? {
        cacheManager.getCachedStaff(staffId)?.let { return it }
        val data = repository.fetchStaff(staffId)
        if (data != null) cacheManager.cacheStaff(staffId, data)
        return data
    }

    suspend fun fetchAllCharacters(animeId: Int): List<CharacterData>? {
        cacheManager.getCachedAllCharacters(animeId)?.let { return it }
        val data = repository.fetchAllCharacters(animeId)
        if (data != null) cacheManager.cacheAllCharacters(animeId, data)
        return data
    }

    suspend fun fetchAllStaff(animeId: Int): List<StaffData>? {
        cacheManager.getCachedAllStaff(animeId)?.let { return it }
        val data = repository.fetchAllStaff(animeId)
        if (data != null) cacheManager.cacheAllStaff(animeId, data)
        return data
    }

    suspend fun searchAnimeAdvanced(
        search: String? = null,
        genres: List<String>? = null,
        tags: List<String>? = null,
        format: String? = null,
        status: String? = null,
        season: String? = null,
        seasonYear: Int? = null,
        sort: String = "POPULARITY_DESC",
        isAdult: Boolean? = null,
        page: Int = 1,
        perPage: Int = 30
    ) = repository.searchAnimeAdvanced(
        search = search, genres = genres, tags = tags,
        format = format, status = status,
        season = season, seasonYear = seasonYear,
        sort = sort, isAdult = isAdult,
        page = page, perPage = perPage
    ).map { mapExploreMedia(it) }

    private var cachedTags: List<MediaTag>? = null
    suspend fun getAllTags(): List<MediaTag> {
        if (cachedTags == null) cachedTags = repository.fetchAllTags()
        return cachedTags!!
    }

    // AniList favorites & user activity — implementations live in viewmodel/MainViewModelAniListFavoritesExt.kt
    // (fun fetchUserActivity, fetchUserStats, fetchAniListFavorites,
    //  loadAniListFavoritesFromStorage, toggleAniListFavorite, refreshReleasingAnimeProgress)


    // Misc
    internal fun saveHomeDataToCache() = cacheManager.saveHomeDataToCache(HomeCacheData(_currentlyWatching.value, _planningToWatch.value, _completed.value, _onHold.value, _dropped.value, _userId.value, _userName.value, _userAvatar.value))
    private fun saveExploreDataToCache() = cacheManager.saveExploreDataToCache(ExploreCacheData(_featuredAnime.value, _seasonalAnime.value, _topSeries.value, _topMovies.value, _actionAnime.value, _romanceAnime.value, _comedyAnime.value, _fantasyAnime.value, _scifiAnime.value))

    fun refreshHome(force: Boolean = false) {
        val now = System.currentTimeMillis()

        // Skip if recently refreshed (unless forced)
        if (!force && now - lastHomeRefreshTime < MIN_REFRESH_INTERVAL_MS) {
            return
        }

        lastHomeRefreshTime = now
        cacheManager.invalidateUserCache()
        viewModelScope.launch {
            _isLoadingHome.value = true
            if (_loginProvider.value == LoginProvider.MAL) {
                fetchMalList()
                fetchJikanUserData()
            } else {
                fetchLists()
            }
            _isLoadingHome.value = false
            // prefetchContinueWatchingStreams() // Disabled for now
        }
    }

    fun forceRefreshExplore() = fetchExploreData(force = true)

    suspend fun fetchTmdbEpisodes(title: String, id: Int, year: Int? = null, format: String? = null, latest: Int = Int.MAX_VALUE) = repository.fetchTmdbEpisodes(title, id, year, format, latest)

    fun getCachedTmdbEpisodes(animeId: Int, status: String? = null): List<TmdbEpisode>? = cacheManager.getCachedTmdbEpisodes(animeId, status)
    fun cacheTmdbEpisodes(animeId: Int, episodes: List<TmdbEpisode>) = cacheManager.cacheTmdbEpisodes(animeId, episodes)
    fun clearTmdbEpisodeCache(animeId: Int) = cacheManager.clearTmdbEpisodeCache(animeId)
    fun pruneTmdbEpisodeCache(retainedIds: Set<Int>) = cacheManager.pruneTmdbEpisodeCache(retainedIds)
    val tmdbEpisodeCache: StateFlow<Map<Int, List<TmdbEpisode>>> get() = cacheManager.tmdbEpisodeCache

    fun retryDownload(animeId: Int, episode: Int) {
        viewModelScope.launch {
            val defaultPkg = defaultExtensionPackage.value
            if (defaultPkg.isEmpty()) {
                _toastMessage.emit("No extension configured for download")
                return@launch
            }

            // Build AnimeMedia from available data
            val cached = cacheManager.detailedAnimeCache.value[animeId]
            val allLists = currentlyWatching.value + planningToWatch.value + completed.value + onHold.value + dropped.value
            val offlineLists = offlineCurrentlyWatching.value + offlinePlanningToWatch.value + offlineCompleted.value + offlineOnHold.value + offlineDropped.value
            val localFavs = localFavorites.value.values.map { fav ->
                AnimeMedia(id = fav.id, title = fav.title, cover = fav.cover, banner = fav.banner, year = fav.year, averageScore = fav.averageScore)
            }
            val allFromLists = allLists + offlineLists + localFavs
            val listAnime = allFromLists.find { it.id == animeId }

            val anime = if (cached != null) {
                AnimeMedia(
                    id = cached.id, title = cached.title, titleEnglish = cached.titleEnglish,
                    cover = cached.cover, banner = cached.banner, year = cached.year,
                    format = cached.format, status = cached.status ?: "",
                    totalEpisodes = cached.episodes, averageScore = cached.averageScore,
                    genres = cached.genres, malId = cached.malId,
                )
            } else listAnime

            if (anime == null) {
                _toastMessage.emit("Cannot retry: anime data not found")
                return@launch
            }

            val result = playEpisodeWithExtension(anime, episode, defaultPkg)
            if (result == null) {
                _toastMessage.emit("Retry failed: could not fetch fresh stream data")
                return@launch
            }

            episodeDownloadManager.removeDownload("${animeId}_$episode")
            episodeDownloadManager.downloadDirectoryUri = downloadDirectoryUri.value
            episodeDownloadManager.keepDownloadedFiles = keepDownloadedFiles.value
            episodeDownloadManager.startDownload(
                animeId = animeId,
                animeName = anime.title,
                episode = episode,
                videoUrl = result.url,
                referer = result.referer,
                videoTitle = result.videoTitle,
                subtitleUrl = result.subtitleUrl,
                subtitleTracks = result.subtitleTrackList,
                videoHeaders = result.videoHeaders,
                mimeType = if (result.url.contains(".m3u8")) "application/x-mpegurl" else "video/mp4",
                malId = anime.malId,
                year = anime.year,
            )
        }
    }

    fun addExploreAnimeToList(anime: ExploreAnime, status: String) {
        queueSync(anime.id, "status", malId = anime.malId, status = status, progress = if (status == "CURRENT") 0 else null)
        updateAnimeStatus(anime.id, status, if (status == "CURRENT") 0 else null)
    }

    data class ExtensionStreamResult(
        val url: String,
        val referer: String,
        val subtitleUrl: String?,
        val subtitleTrackList: List<Track> = emptyList(),
        val videoTitle: String,
        val videos: List<Video> = emptyList(),
        val hosters: List<Hoster>? = null,
        val extensionClient: OkHttpClient? = null,
        val videoHeaders: Map<String, String> = emptyMap(),
        val source: AnimeCatalogueSource? = null,
        val episode: SEpisode? = null,
    )

    // Extension playback — implementations live in viewmodel/MainViewModelExtensionPlaybackExt.kt
    // (suspend fun playEpisodeWithExtension, suspend fun fetchExtensionHosterVideos)


    override fun onCleared() {
        super.onCleared()
        connectivityCallback?.let { callback ->
            try {
                val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                connectivityManager?.unregisterNetworkCallback(callback)
            } catch (_: Exception) {
            }
        }
    }
}


