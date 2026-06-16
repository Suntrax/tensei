package com.blissless.tensei.data

import android.content.Context
import android.content.SharedPreferences
import com.blissless.tensei.data.models.LocalAnimeEntry
import com.blissless.tensei.data.models.StoredFavorite
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.core.content.edit

/**
 * Manages user preferences and settings
 */
class UserPreferences(private val context: Context) {

    companion object {
        private const val TAG = "UserPreferences"
        private const val PREFS_NAME = "anilist_prefs"
        private const val TOKEN_KEY = "auth_token"

        // Preference keys
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_DISABLE_MATERIAL_COLORS = "disable_material_colors"
        private const val KEY_PREFERRED_CATEGORY = "preferred_category"
        private const val KEY_SHOW_STATUS_COLORS = "show_status_colors"
        private const val KEY_SHOW_ANIME_CARD_BUTTONS = "show_anime_card_buttons"
        private const val KEY_PREFER_ENGLISH_TITLES = "prefer_english_titles"
        private const val KEY_PREVENT_SCHEDULE_SYNC = "prevent_schedule_sync"
        private const val KEY_TRACKING_PERCENTAGE = "tracking_percentage"
        private const val KEY_FORWARD_SKIP_SECONDS = "forward_skip_seconds"
        private const val KEY_BACKWARD_SKIP_SECONDS = "backward_skip_seconds"
        private const val KEY_HIDE_NAVBAR_TEXT = "hide_navbar_text"
        private const val KEY_SIMPLIFY_EPISODE_MENU = "simplify_episode_menu"
        private const val KEY_SIMPLIFY_ANIME_DETAILS = "simplify_anime_details"
        private const val KEY_AUTO_SKIP_OPENING = "auto_skip_opening"
        private const val KEY_AUTO_SKIP_ENDING = "auto_skip_ending"
        private const val KEY_AUTO_PLAY_NEXT_EPISODE = "auto_play_next_episode"
        private const val KEY_ENABLE_THUMBNAIL_PREVIEW = "enable_thumbnail_preview"
        private const val KEY_LOCAL_FAVORITES_V2 = "local_favorites_v2"
        private const val KEY_LOCAL_FAVORITES = "local_favorites"
        private const val KEY_ANILIST_FAVORITES = "anilist_favorites"
        private const val KEY_PREFERRED_SCRAPER = "preferred_scraper"
        private const val KEY_DEFAULT_EXTENSION = "default_extension_package"
        private const val KEY_HIDE_ADULT_CONTENT = "hide_adult_content"
        private const val KEY_STREAM_PROVIDER = "stream_provider"
        private const val KEY_STARTUP_SCREEN = "startup_screen"
        private const val KEY_BUFFER_AHEAD_SECONDS = "buffer_ahead_seconds"
        private const val KEY_BUFFER_SIZE_MB = "buffer_size_mb"
        private const val KEY_SHOW_BUFFER_INDICATOR = "show_buffer_indicator"
        private const val KEY_LAST_HOME_REFRESH = "last_home_refresh_time"
        private const val KEY_LAST_EXPLORE_REFRESH = "last_explore_refresh_time"
        private const val KEY_LOCAL_ANIME_STATUS = "local_anime_status"
        private const val KEY_DEFAULT_SUBTITLE_LANG = "default_subtitle_lang"
        private const val KEY_DOWNLOAD_PREFERRED_CATEGORY = "download_preferred_category"
        private const val KEY_DOWNLOAD_SUBTITLE_LANG = "download_subtitle_lang"
        private const val KEY_MAL_FAVORITES = "mal_favorites"
        private const val KEY_CHECK_UPDATES_ON_START = "check_updates_on_start"
        private const val KEY_SWIPE_VOLUME = "swipe_volume"
        private const val KEY_SWIPE_BRIGHTNESS = "swipe_brightness"
    }

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Auth token
    private val _authToken = MutableStateFlow<String?>(null)
    val authToken: StateFlow<String?> = _authToken.asStateFlow()

    // UI Preferences
    private val _themeMode = MutableStateFlow("system")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _isOled = MutableStateFlow(false)
    val isOled: StateFlow<Boolean> = _isOled.asStateFlow()

    private val _disableMaterialColors = MutableStateFlow(true)
    val disableMaterialColors: StateFlow<Boolean> = _disableMaterialColors.asStateFlow()

    private val _preferredCategory = MutableStateFlow("sub")
    val preferredCategory: StateFlow<String> = _preferredCategory.asStateFlow()

    private val _showStatusColors = MutableStateFlow(true)
    val showStatusColors: StateFlow<Boolean> = _showStatusColors.asStateFlow()

    private val _showAnimeCardButtons = MutableStateFlow(false)
    val showAnimeCardButtons: StateFlow<Boolean> = _showAnimeCardButtons.asStateFlow()

    private val _preferEnglishTitles = MutableStateFlow(true)
    val preferEnglishTitles: StateFlow<Boolean> = _preferEnglishTitles.asStateFlow()

    private val _preventScheduleSync = MutableStateFlow(false)
    val preventScheduleSync: StateFlow<Boolean> = _preventScheduleSync.asStateFlow()

    private val _trackingPercentage = MutableStateFlow(85)
    val trackingPercentage: StateFlow<Int> = _trackingPercentage.asStateFlow()

    private val _forwardSkipSeconds = MutableStateFlow(10)
    val forwardSkipSeconds: StateFlow<Int> = _forwardSkipSeconds.asStateFlow()

    private val _backwardSkipSeconds = MutableStateFlow(10)
    val backwardSkipSeconds: StateFlow<Int> = _backwardSkipSeconds.asStateFlow()

    private val _hideNavbarText = MutableStateFlow(false)
    val hideNavbarText: StateFlow<Boolean> = _hideNavbarText.asStateFlow()

    private val _simplifyEpisodeMenu = MutableStateFlow(true)
    val simplifyEpisodeMenu: StateFlow<Boolean> = _simplifyEpisodeMenu.asStateFlow()

    private val _simplifyAnimeDetails = MutableStateFlow(true)
    val simplifyAnimeDetails: StateFlow<Boolean> = _simplifyAnimeDetails.asStateFlow()

    private val _autoSkipOpening = MutableStateFlow(false)
    val autoSkipOpening: StateFlow<Boolean> = _autoSkipOpening.asStateFlow()

    private val _autoSkipEnding = MutableStateFlow(false)
    val autoSkipEnding: StateFlow<Boolean> = _autoSkipEnding.asStateFlow()

    private val _autoPlayNextEpisode = MutableStateFlow(false)
    val autoPlayNextEpisode: StateFlow<Boolean> = _autoPlayNextEpisode.asStateFlow()

    // Thumbnail extraction for seekbar preview
    private val _enableThumbnailPreview = MutableStateFlow(false)
    val enableThumbnailPreview: StateFlow<Boolean> = _enableThumbnailPreview.asStateFlow()

    // Preferred Scraper
    private val _preferredScraper = MutableStateFlow("Animekai")
    val preferredScraper: StateFlow<String> = _preferredScraper.asStateFlow()

    // Default Extension
    private val _defaultExtensionPackage = MutableStateFlow("")
    val defaultExtensionPackage: StateFlow<String> = _defaultExtensionPackage.asStateFlow()

    // Default Subtitle Language
    private val _defaultSubtitleLang = MutableStateFlow("English")
    val defaultSubtitleLang: StateFlow<String> = _defaultSubtitleLang.asStateFlow()

    // Download-specific preferences (default "same_as_stream" mirrors stream settings)
    private val _downloadPreferredCategory = MutableStateFlow("same_as_stream")
    val downloadPreferredCategory: StateFlow<String> = _downloadPreferredCategory.asStateFlow()

    private val _downloadSubtitleLang = MutableStateFlow("same_as_stream")
    val downloadSubtitleLang: StateFlow<String> = _downloadSubtitleLang.asStateFlow()

    // Hide Adult Content
    private val _hideAdultContent = MutableStateFlow(false)
    val hideAdultContent: StateFlow<Boolean> = _hideAdultContent.asStateFlow()

    // Stream Provider (1 = Miruro, 2 = Animekai)
    private val _streamProvider = MutableStateFlow(1)
    val streamProvider: StateFlow<Int> = _streamProvider.asStateFlow()

    // Startup Screen
    private val _startupScreen = MutableStateFlow(2)
    val startupScreen: StateFlow<Int> = _startupScreen.asStateFlow()
    
    // Buffer Settings
    private val _bufferAheadSeconds = MutableStateFlow(30)
    val bufferAheadSeconds: StateFlow<Int> = _bufferAheadSeconds.asStateFlow()
    
    private val _bufferSizeMb = MutableStateFlow(100)
    val bufferSizeMb: StateFlow<Int> = _bufferSizeMb.asStateFlow()
    
    private val _showBufferIndicator = MutableStateFlow(true)
    val showBufferIndicator: StateFlow<Boolean> = _showBufferIndicator.asStateFlow()

    // Local favorites
    private val _localFavorites = MutableStateFlow<Map<Int, StoredFavorite>>(emptyMap())
    val localFavorites: StateFlow<Map<Int, StoredFavorite>> = _localFavorites.asStateFlow()

    val localFavoriteIds: Set<Int> get() = _localFavorites.value.keys

    // AniList favorites (stored locally, synced to API)
    private val _aniListFavorites = MutableStateFlow<Set<Int>>(emptySet())
    val aniListFavorites: StateFlow<Set<Int>> = _aniListFavorites.asStateFlow()

    fun isAniListFavorite(mediaId: Int): Boolean = _aniListFavorites.value.contains(mediaId)

    fun toggleAniListFavorite(mediaId: Int): Boolean {
        val current = _aniListFavorites.value.toMutableSet()
        val wasAdded = !current.contains(mediaId)
        if (wasAdded) {
            current.add(mediaId)
        } else {
            current.remove(mediaId)
        }
        _aniListFavorites.value = current
        saveAniListFavorites(current)
        return wasAdded
    }

    private fun saveAniListFavorites(favorites: Set<Int>) {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val encoded = json.encodeToString(kotlinx.serialization.serializer(), favorites.toList())
        sharedPreferences.edit().putString(KEY_ANILIST_FAVORITES, encoded).apply()
    }

    private fun loadAniListFavorites() {
        val saved = sharedPreferences.getString(KEY_ANILIST_FAVORITES, null)
        if (saved != null) {
            try {
                val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                val list = json.decodeFromString<List<Int>>(kotlinx.serialization.serializer(), saved)
                _aniListFavorites.value = list.toSet()
            } catch (e: Exception) {
                _aniListFavorites.value = emptySet()
            }
        }
    }

    // Local anime status (for offline users)
    private val _localAnimeStatus = MutableStateFlow<Map<Int, LocalAnimeEntry>>(emptyMap())
    val localAnimeStatus: StateFlow<Map<Int, LocalAnimeEntry>> = _localAnimeStatus.asStateFlow()

    /**
     * Load all preferences from SharedPreferences.
     * @param hasToken Whether to attempt loading the auth token
     */
    fun loadPreferences(hasToken: Boolean) {
        // Load auth token if requested
        if (hasToken) {
            val token = sharedPreferences.getString(TOKEN_KEY, null)
            _authToken.value = token
        }

        // Load UI preferences
        _themeMode.value = sharedPreferences.getString(KEY_THEME_MODE, "system") ?: "system"
        _isOled.value = _themeMode.value == "oled"
        _disableMaterialColors.value = sharedPreferences.getBoolean(KEY_DISABLE_MATERIAL_COLORS, true)
        _preferredCategory.value = sharedPreferences.getString(KEY_PREFERRED_CATEGORY, "sub") ?: "sub"
        _showStatusColors.value = sharedPreferences.getBoolean(KEY_SHOW_STATUS_COLORS, false)
        _showAnimeCardButtons.value = sharedPreferences.getBoolean(KEY_SHOW_ANIME_CARD_BUTTONS, false)
        _preferEnglishTitles.value = sharedPreferences.getBoolean(KEY_PREFER_ENGLISH_TITLES, true)

        _preventScheduleSync.value = sharedPreferences.getBoolean(KEY_PREVENT_SCHEDULE_SYNC, false)
        _trackingPercentage.value = sharedPreferences.getInt(KEY_TRACKING_PERCENTAGE, 85)
        _forwardSkipSeconds.value = sharedPreferences.getInt(KEY_FORWARD_SKIP_SECONDS, 10)
        _backwardSkipSeconds.value = sharedPreferences.getInt(KEY_BACKWARD_SKIP_SECONDS, 10)
        _hideNavbarText.value = sharedPreferences.getBoolean(KEY_HIDE_NAVBAR_TEXT, false)
        _simplifyEpisodeMenu.value = sharedPreferences.getBoolean(KEY_SIMPLIFY_EPISODE_MENU, false)
        _simplifyAnimeDetails.value = sharedPreferences.getBoolean(KEY_SIMPLIFY_ANIME_DETAILS, false)
        _autoSkipOpening.value = sharedPreferences.getBoolean(KEY_AUTO_SKIP_OPENING, false)
        _autoSkipEnding.value = sharedPreferences.getBoolean(KEY_AUTO_SKIP_ENDING, false)
        _autoPlayNextEpisode.value = sharedPreferences.getBoolean(KEY_AUTO_PLAY_NEXT_EPISODE, true)
        _enableThumbnailPreview.value = sharedPreferences.getBoolean(KEY_ENABLE_THUMBNAIL_PREVIEW, false)
        _preferredScraper.value = sharedPreferences.getString(KEY_PREFERRED_SCRAPER, "Animekai") ?: "Animekai"
        _defaultExtensionPackage.value = sharedPreferences.getString(KEY_DEFAULT_EXTENSION, "") ?: ""
        _defaultSubtitleLang.value = sharedPreferences.getString(KEY_DEFAULT_SUBTITLE_LANG, "English") ?: "English"
        _downloadPreferredCategory.value = sharedPreferences.getString(KEY_DOWNLOAD_PREFERRED_CATEGORY, "same_as_stream") ?: "same_as_stream"
        _downloadSubtitleLang.value = sharedPreferences.getString(KEY_DOWNLOAD_SUBTITLE_LANG, "same_as_stream") ?: "same_as_stream"
        _hideAdultContent.value = sharedPreferences.getBoolean(KEY_HIDE_ADULT_CONTENT, true)
        _streamProvider.value = sharedPreferences.getInt(KEY_STREAM_PROVIDER, 1)
        _startupScreen.value = sharedPreferences.getInt(KEY_STARTUP_SCREEN, 2)
        _bufferAheadSeconds.value = sharedPreferences.getInt(KEY_BUFFER_AHEAD_SECONDS, 60)
        _bufferSizeMb.value = sharedPreferences.getInt(KEY_BUFFER_SIZE_MB, 200)
        _showBufferIndicator.value = sharedPreferences.getBoolean(KEY_SHOW_BUFFER_INDICATOR, true)
        _checkUpdatesOnStart.value = sharedPreferences.getBoolean(KEY_CHECK_UPDATES_ON_START, false)
        _swipeVolume.value = sharedPreferences.getBoolean(KEY_SWIPE_VOLUME, false)
        _swipeBrightness.value = sharedPreferences.getBoolean(KEY_SWIPE_BRIGHTNESS, false)

        // Load local favorites
        loadLocalFavorites()

        // Load AniList favorites (stored locally, synced to API)
        loadAniListFavorites()

        // Load local anime status
        loadLocalAnimeStatus()

    }

    // ============================================
    // Auth Methods
    // ============================================

    fun saveToken(token: String) {
        sharedPreferences.edit { putString(TOKEN_KEY, token) }
        _authToken.value = token
    }

    fun clearToken() {
        _authToken.value = null
        sharedPreferences.edit { remove(TOKEN_KEY) }
    }

    // ============================================
    // UI Preference Setters
    // ============================================

    fun setThemeMode(mode: String) {
        _themeMode.value = mode
        _isOled.value = mode == "oled"
        sharedPreferences.edit { putString(KEY_THEME_MODE, mode) }
    }

    fun setDisableMaterialColors(enabled: Boolean) {
        _disableMaterialColors.value = enabled
        sharedPreferences.edit { putBoolean(KEY_DISABLE_MATERIAL_COLORS, enabled) }
    }

    fun setPreferredCategory(category: String) {
        _preferredCategory.value = category
        sharedPreferences.edit { putString(KEY_PREFERRED_CATEGORY, category) }
    }

    fun setShowStatusColors(enabled: Boolean) {
        _showStatusColors.value = enabled
        sharedPreferences.edit { putBoolean(KEY_SHOW_STATUS_COLORS, enabled) }
    }

    fun setShowAnimeCardButtons(enabled: Boolean) {
        _showAnimeCardButtons.value = enabled
        sharedPreferences.edit { putBoolean(KEY_SHOW_ANIME_CARD_BUTTONS, enabled) }
    }

    fun setPreferEnglishTitles(enabled: Boolean) {
        _preferEnglishTitles.value = enabled
        sharedPreferences.edit { putBoolean(KEY_PREFER_ENGLISH_TITLES, enabled) }
    }

    fun setPreventScheduleSync(enabled: Boolean) {
        _preventScheduleSync.value = enabled
        sharedPreferences.edit { putBoolean(KEY_PREVENT_SCHEDULE_SYNC, enabled) }
    }

    fun setTrackingPercentage(percentage: Int) {
        val validPercentage = percentage.coerceIn(50, 100)
        _trackingPercentage.value = validPercentage
        sharedPreferences.edit { putInt(KEY_TRACKING_PERCENTAGE, validPercentage) }
    }

    fun setForwardSkipSeconds(seconds: Int) {
        val validSeconds = seconds.coerceIn(5, 30)
        _forwardSkipSeconds.value = validSeconds
        sharedPreferences.edit {putInt(KEY_FORWARD_SKIP_SECONDS, validSeconds) }
    }

    fun setBackwardSkipSeconds(seconds: Int) {
        val validSeconds = seconds.coerceIn(5, 30)
        _backwardSkipSeconds.value = validSeconds
        sharedPreferences.edit { putInt(KEY_BACKWARD_SKIP_SECONDS, validSeconds) }
    }

    fun setHideNavbarText(enabled: Boolean) {
        _hideNavbarText.value = enabled
        sharedPreferences.edit {putBoolean(KEY_HIDE_NAVBAR_TEXT, enabled) }
    }

    fun setSimplifyEpisodeMenu(enabled: Boolean) {
        _simplifyEpisodeMenu.value = enabled
        sharedPreferences.edit { putBoolean(KEY_SIMPLIFY_EPISODE_MENU, enabled) }
    }

    fun setSimplifyAnimeDetails(enabled: Boolean) {
        _simplifyAnimeDetails.value = enabled
        sharedPreferences.edit { putBoolean(KEY_SIMPLIFY_ANIME_DETAILS, enabled) }
    }

    fun setAutoSkipOpening(enabled: Boolean) {
        _autoSkipOpening.value = enabled
        sharedPreferences.edit {putBoolean(KEY_AUTO_SKIP_OPENING, enabled) }
    }

    fun setAutoSkipEnding(enabled: Boolean) {
        _autoSkipEnding.value = enabled
        sharedPreferences.edit {putBoolean(KEY_AUTO_SKIP_ENDING, enabled) }
    }

    fun setAutoPlayNextEpisode(enabled: Boolean) {
        _autoPlayNextEpisode.value = enabled
        sharedPreferences.edit {putBoolean(KEY_AUTO_PLAY_NEXT_EPISODE, enabled) }
    }

    /**
     * Enable or disable thumbnail preview for video scrubbing.
     * This is a resource-intensive feature that extracts video frames.
     */
    fun setEnableThumbnailPreview(enabled: Boolean) {
        _enableThumbnailPreview.value = enabled
        sharedPreferences.edit {putBoolean(KEY_ENABLE_THUMBNAIL_PREVIEW, enabled) }
    }

    fun setPreferredScraper(scraper: String) {
        _preferredScraper.value = scraper
        sharedPreferences.edit { putString(KEY_PREFERRED_SCRAPER, scraper) }
    }

    fun setDefaultExtensionPackage(packageName: String) {
        _defaultExtensionPackage.value = packageName
        sharedPreferences.edit { putString(KEY_DEFAULT_EXTENSION, packageName) }
    }

    fun setDefaultSubtitleLang(lang: String) {
        _defaultSubtitleLang.value = lang
        sharedPreferences.edit { putString(KEY_DEFAULT_SUBTITLE_LANG, lang) }
    }

    fun setDownloadPreferredCategory(category: String) {
        _downloadPreferredCategory.value = category
        sharedPreferences.edit { putString(KEY_DOWNLOAD_PREFERRED_CATEGORY, category) }
    }

    fun setDownloadSubtitleLang(lang: String) {
        _downloadSubtitleLang.value = lang
        sharedPreferences.edit { putString(KEY_DOWNLOAD_SUBTITLE_LANG, lang) }
    }

    fun setHideAdultContent(enabled: Boolean) {
        _hideAdultContent.value = enabled
        sharedPreferences.edit { putBoolean(KEY_HIDE_ADULT_CONTENT, enabled) }
    }

    fun setStreamProvider(provider: Int) {
        _streamProvider.value = provider
        sharedPreferences.edit { putInt(KEY_STREAM_PROVIDER, provider) }
    }

    fun setStartupScreen(screen: Int) {
        _startupScreen.value = screen
        sharedPreferences.edit { putInt(KEY_STARTUP_SCREEN, screen) }
    }

    // ============================================
    // Buffer Settings
    // ============================================
    
    fun setBufferAheadSeconds(seconds: Int) {
        _bufferAheadSeconds.value = seconds
        sharedPreferences.edit { putInt(KEY_BUFFER_AHEAD_SECONDS, seconds) }
    }
    
    fun setBufferSizeMb(sizeMb: Int) {
        _bufferSizeMb.value = sizeMb
        sharedPreferences.edit { putInt(KEY_BUFFER_SIZE_MB, sizeMb) }
    }
    
    fun setShowBufferIndicator(show: Boolean) {
        _showBufferIndicator.value = show
        sharedPreferences.edit { putBoolean(KEY_SHOW_BUFFER_INDICATOR, show) }
    }

    // ============================================
    // Local Favorites
    // ============================================

    fun isLocalFavorite(mediaId: Int): Boolean = _localFavorites.value.containsKey(mediaId)

    fun canAddFavorite(): Boolean = _localFavorites.value.size < 10

    fun getLocalFavoriteCount(): Int = _localFavorites.value.size

    fun toggleLocalFavorite(
        mediaId: Int,
        title: String = "",
        cover: String = "",
        banner: String? = null,
        year: Int? = null,
        averageScore: Int? = null
    ) {
        val currentFavorites = _localFavorites.value.toMutableMap()
        val existingFavorite = currentFavorites[mediaId]

        if (existingFavorite != null) {
            // Update metadata if title was empty before
            if (existingFavorite.title.isEmpty() && title.isNotEmpty()) {
                currentFavorites[mediaId] = StoredFavorite(mediaId, title, cover, banner, year, averageScore)
            } else {
                // Remove favorite
                currentFavorites.remove(mediaId)
            }
        } else {
            // Add new favorite (max 10)
            if (currentFavorites.size >= 10) {
                return
            }
            currentFavorites[mediaId] = StoredFavorite(mediaId, title, cover, banner, year, averageScore)
        }

        _localFavorites.value = currentFavorites
        saveLocalFavorites(currentFavorites)
    }

    /**
     * Update metadata for an existing favorite.
     */
    fun updateFavoriteMetadata(
        mediaId: Int,
        title: String,
        cover: String,
        banner: String?,
        year: Int?,
        averageScore: Int?
    ) {
        val currentFavorites = _localFavorites.value.toMutableMap()
        if (currentFavorites.containsKey(mediaId)) {
            currentFavorites[mediaId] = StoredFavorite(mediaId, title, cover, banner, year, averageScore)
            _localFavorites.value = currentFavorites
            saveLocalFavorites(currentFavorites)
        }
    }

    private fun saveLocalFavorites(favorites: Map<Int, StoredFavorite>) {
        val json = kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
        val favoritesJson = favorites.values.map { fav ->
            json.encodeToString(StoredFavorite.serializer(), fav)
        }.toSet()

        sharedPreferences.edit {
            putStringSet(KEY_LOCAL_FAVORITES_V2, favoritesJson)
        }
    }

    private fun loadLocalFavorites() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

        // Try v2 format first (with metadata)
        val savedV2 = sharedPreferences.getStringSet(KEY_LOCAL_FAVORITES_V2, null)
        if (savedV2 != null) {
            val favorites = mutableMapOf<Int, StoredFavorite>()
            savedV2.forEach { favJson ->
                try {
                    val fav = json.decodeFromString(StoredFavorite.serializer(), favJson)
                    favorites[fav.id] = fav
                } catch (e: Exception) {
                }
            }
            _localFavorites.value = favorites
            return
        }

        // Fall back to v1 format (IDs only)
        val saved = sharedPreferences.getStringSet(KEY_LOCAL_FAVORITES, emptySet()) ?: emptySet()
        if (saved.isNotEmpty()) {
            val favorites = mutableMapOf<Int, StoredFavorite>()
            saved.mapNotNull { it.toIntOrNull() }.forEach { id ->
                favorites[id] = StoredFavorite(id, "", "")
            }
            _localFavorites.value = favorites

            // Save in v2 format
            saveLocalFavorites(favorites)

            // Clear old format
            sharedPreferences.edit {remove(KEY_LOCAL_FAVORITES) }
        }
    }

    // ============================================
    // Data Management
    // ============================================

    fun clearAllUserData() {
        sharedPreferences.edit {
            remove(TOKEN_KEY)
                .remove("cache_home_data")
                .remove("cache_home_time")
                .remove("cache_explore_data")
                .remove("cache_explore_time")
                .remove("cache_airing_data")
                .remove("cache_airing_time")
        }
    }

    /**
     * Clear all preferences (useful for debugging or complete reset)
     */
    fun clearAllPreferences() {
        sharedPreferences.edit {clear()}
    }

    fun getSharedPreferences(): SharedPreferences = sharedPreferences

    // Refresh timestamp management
    fun getLastHomeRefreshTime(): Long = sharedPreferences.getLong(KEY_LAST_HOME_REFRESH, 0L)
    fun setLastHomeRefreshTime(time: Long) {
        sharedPreferences.edit { putLong(KEY_LAST_HOME_REFRESH, time) }
    }

    fun getLastExploreRefreshTime(): Long = sharedPreferences.getLong(KEY_LAST_EXPLORE_REFRESH, 0L)
    fun setLastExploreRefreshTime(time: Long) {
        sharedPreferences.edit { putLong(KEY_LAST_EXPLORE_REFRESH, time) }
    }

    // Local Anime Status (for offline users)
    fun getLocalAnimeStatus(mediaId: Int): LocalAnimeEntry? = _localAnimeStatus.value[mediaId]
    fun getAllLocalAnimeStatus(): Map<Int, LocalAnimeEntry> = _localAnimeStatus.value

    fun setLocalAnimeStatus(mediaId: Int, entry: LocalAnimeEntry?) {
        val currentStatus = _localAnimeStatus.value.toMutableMap()
        if (entry != null) {
            currentStatus[mediaId] = entry
        } else {
            currentStatus.remove(mediaId)
        }
        _localAnimeStatus.value = currentStatus
        saveLocalAnimeStatus(currentStatus)
    }

    fun updateLocalAnimeProgress(mediaId: Int, progress: Int, totalEpisodes: Int) {
        val current = _localAnimeStatus.value[mediaId]
        if (current != null) {
            setLocalAnimeStatus(mediaId, current.copy(progress = progress, totalEpisodes = totalEpisodes))
        }
    }

    fun clearLocalAnimeStatus() {
        _localAnimeStatus.value = emptyMap()
        saveLocalAnimeStatus(emptyMap())
    }

    private fun saveLocalAnimeStatus(statusMap: Map<Int, LocalAnimeEntry>) {
        val json = kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
        val statusList = statusMap.values.map { json.encodeToString(LocalAnimeEntry.serializer(), it) }.toSet()
        sharedPreferences.edit { putStringSet(KEY_LOCAL_ANIME_STATUS, statusList) }
    }

    private fun loadLocalAnimeStatus() {
        val saved = sharedPreferences.getStringSet(KEY_LOCAL_ANIME_STATUS, null)
        if (saved != null && saved.isNotEmpty()) {
            try {
                val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                val statusMap = mutableMapOf<Int, LocalAnimeEntry>()
                saved.forEach { entryJson ->
                    try {
                        val entry = json.decodeFromString(LocalAnimeEntry.serializer(), entryJson)
                        statusMap[entry.id] = entry
                    } catch (e: Exception) {
                        // Skip invalid entries
                    }
                }
                _localAnimeStatus.value = statusMap
            } catch (e: Exception) {
                _localAnimeStatus.value = emptyMap()
            }
        }
    }
    
    // Check for Updates on Start
    private val _checkUpdatesOnStart = MutableStateFlow(false)
    val checkUpdatesOnStart: StateFlow<Boolean> = _checkUpdatesOnStart.asStateFlow()

    // Swipe Gesture Controls
    private val _swipeVolume = MutableStateFlow(false)
    val swipeVolume: StateFlow<Boolean> = _swipeVolume.asStateFlow()

    private val _swipeBrightness = MutableStateFlow(false)
    val swipeBrightness: StateFlow<Boolean> = _swipeBrightness.asStateFlow()

    fun setCheckUpdatesOnStart(enabled: Boolean) {
        _checkUpdatesOnStart.value = enabled
        sharedPreferences.edit { putBoolean(KEY_CHECK_UPDATES_ON_START, enabled) }
    }

    fun setSwipeVolume(enabled: Boolean) {
        _swipeVolume.value = enabled
        sharedPreferences.edit { putBoolean(KEY_SWIPE_VOLUME, enabled) }
    }

    fun setSwipeBrightness(enabled: Boolean) {
        _swipeBrightness.value = enabled
        sharedPreferences.edit { putBoolean(KEY_SWIPE_BRIGHTNESS, enabled) }
    }

    // MAL Favorites
    fun getMalFavorites(): Set<Int> {
        val saved = sharedPreferences.getStringSet(KEY_MAL_FAVORITES, emptySet()) ?: emptySet()
        return saved.mapNotNull { it.toIntOrNull() }.toSet()
    }
    
    fun saveMalFavorites(favorites: List<Int>) {
        sharedPreferences.edit {
            putStringSet(KEY_MAL_FAVORITES, favorites.map { it.toString() }.toSet())
        }
    }
    
    fun clearMalFavorites() {
        sharedPreferences.edit {
            remove(KEY_MAL_FAVORITES)
        }
    }
}


