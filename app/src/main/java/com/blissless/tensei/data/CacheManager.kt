package com.blissless.tensei.data

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.OptIn
import androidx.core.content.edit
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.OkHttpClient
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.blissless.tensei.data.models.AiringCacheData
import com.blissless.tensei.data.models.AiringScheduleAnime
import com.blissless.tensei.data.models.AniwatchStreamResult
import com.blissless.tensei.data.models.CachedEpisodeInfo
import com.blissless.tensei.data.models.CachedExtensionStream
import com.blissless.tensei.data.models.CachedQuality
import com.blissless.tensei.data.models.CachedServer
import com.blissless.tensei.data.models.CachedStream
import com.blissless.tensei.data.models.DetailedAnimeData
import com.blissless.tensei.data.models.EpisodeStreams
import com.blissless.tensei.data.models.ExploreCacheData
import com.blissless.tensei.data.models.HomeCacheData
import com.blissless.tensei.data.models.PlaybackPositionCache
import com.blissless.tensei.data.models.QualityOption
import com.blissless.tensei.data.models.ServerInfo
import com.blissless.tensei.data.models.StreamCacheData
import com.blissless.tensei.data.models.StreamCacheEntry
import com.blissless.tensei.data.models.TmdbEpisode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.io.File

@UnstableApi
class CacheManager(private val sharedPreferences: SharedPreferences) {

    companion object {
        private const val CACHE_DURATION_MS = 7 * 24 * 60 * 60 * 1000L
        private const val STREAM_CACHE_DURATION_MS = 24 * 60 * 60 * 1000L // 24 hours

        private const val CACHE_EXPLORE_TIME = "cache_explore_time"
        private const val CACHE_HOME_TIME = "cache_home_time"
        private const val CACHE_EXPLORE_DATA = "cache_explore_data"
        private const val CACHE_HOME_DATA = "cache_home_data"
        private const val CACHE_STREAM_DATA = "cache_stream_data"
        private const val CACHE_AIRING_TIME = "cache_airing_time"
        private const val CACHE_AIRING_DATA = "cache_airing_data"
        private const val CACHE_PLAYBACK_POSITIONS = "cache_playback_positions"
        private const val CACHE_EXTENSION_STREAMS = "cache_extension_streams"
        
        // Video cache settings
        private const val VIDEO_CACHE_SIZE_BYTES = 1024L * 1024 * 1024 // 1 GB default - more space for offline content
        private var videoCache: SimpleCache? = null
        private var videoDbProvider: StandaloneDatabaseProvider? = null
        private var isCacheInitialized = false
    }

    // Initialize video cache - call this once when app starts
    @OptIn(UnstableApi::class)
    fun initializeVideoCache(context: Context) {
        if (isCacheInitialized) return
        
        try {
            val cacheDir = File(context.cacheDir, "video_cache")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            val evictor = LeastRecentlyUsedCacheEvictor(VIDEO_CACHE_SIZE_BYTES)
            val dbProvider = StandaloneDatabaseProvider(context)
            videoDbProvider = dbProvider
            videoCache = SimpleCache(cacheDir, evictor, dbProvider)
            isCacheInitialized = true
        } catch (_: Exception) {
            // Cache initialization failed, continue without caching
        }
    }
    
    // Get a CacheDataSource.Factory that uses the video cache
    @OptIn(UnstableApi::class)
    fun getCacheDataSourceFactory(referer: String, extensionClient: OkHttpClient? = null, extensionHeaders: Map<String, String> = emptyMap()): CacheDataSource.Factory? {
        val cache = videoCache ?: return null
        
        val httpDataSourceFactory = if (extensionClient != null && extensionHeaders.isNotEmpty()) {
            OkHttpDataSource.Factory(extensionClient)
                .setDefaultRequestProperties(extensionHeaders)
        } else if (extensionClient != null) {
            OkHttpDataSource.Factory(extensionClient)
                .setDefaultRequestProperties(mapOf("Referer" to referer))
        } else {
            DefaultHttpDataSource.Factory()
                .setConnectTimeoutMs(20000)
                .setReadTimeoutMs(60000)
                .setDefaultRequestProperties(mapOf("Referer" to referer))
        }
        
        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(httpDataSourceFactory)
    }

    private fun getContentLength(videoUrl: String, referer: String): Long {
        return try {
            val connection = java.net.URL(videoUrl).openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.setRequestProperty("Referer", referer)
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            val length = connection.contentLengthLong
            connection.disconnect()
            length
        } catch (_: Exception) {
            -1
        }
    }
    
    // Check if a video is fully cached
    @OptIn(UnstableApi::class)
    fun isVideoFullyCached(videoUrl: String): Boolean {
        val cache = videoCache ?: return false
        return try {
            val cachedSpans = cache.getCachedSpans(videoUrl)
            val cachedBytes = cachedSpans.sumOf { it.length }
            val contentLength = getContentLength(videoUrl, "")
            contentLength in 1..cachedBytes
        } catch (_: Exception) {
            false
        }
    }
    
    // Get cache progress for a video
    @OptIn(UnstableApi::class)
    fun getCacheProgress(videoUrl: String): Pair<Long, Long>? {
        val cache = videoCache ?: return null
        return try {
            val cachedSpans = cache.getCachedSpans(videoUrl)
            val cachedBytes = cachedSpans.sumOf { it.length }
            val contentLength = getContentLength(videoUrl, "")
            if (contentLength > 0) {
                Pair(cachedBytes, contentLength)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val _prefetchedStreams = MutableStateFlow<Map<String, AniwatchStreamResult?>>(emptyMap())
    val prefetchedStreams: StateFlow<Map<String, AniwatchStreamResult?>> = _prefetchedStreams.asStateFlow()

    private val _prefetchedEpisodeInfo = MutableStateFlow<Map<String, EpisodeStreams?>>(emptyMap())
    val prefetchedEpisodeInfo: StateFlow<Map<String, EpisodeStreams?>> = _prefetchedEpisodeInfo.asStateFlow()

    private val _detailedAnimeCache = MutableStateFlow<Map<Int, DetailedAnimeData>>(emptyMap())
    val detailedAnimeCache: StateFlow<Map<Int, DetailedAnimeData>> = _detailedAnimeCache.asStateFlow()

    private val _playbackPositions = MutableStateFlow<Map<String, Long>>(emptyMap())
    val playbackPositions: StateFlow<Map<String, Long>> = _playbackPositions.asStateFlow()

    private val _playbackDurations = MutableStateFlow<Map<String, Long>>(emptyMap())
    val playbackDurations: StateFlow<Map<String, Long>> = _playbackDurations.asStateFlow()

    // TMDB episode cache - 24h TTL for non-downloaded anime, persistent for downloaded anime
    private val _tmdbEpisodeCache = MutableStateFlow<Map<Int, List<TmdbEpisode>>>(emptyMap())
    val tmdbEpisodeCache: StateFlow<Map<Int, List<TmdbEpisode>>> = _tmdbEpisodeCache.asStateFlow()
    private val _tmdbCacheTimestamps = mutableMapOf<Int, Long>()
    private val TMDB_CACHE_MAX_AGE_MS = 24 * 60 * 60 * 1000L
    private val TMDB_CACHE_PREFS = "tmdb_episode_cache"
    private val TMDB_PERSISTENT_IDS_PREFS = "tmdb_persistent_ids"
    private val _persistentTmdbIds = mutableSetOf<Int>()

    fun getCachedTmdbEpisodes(animeId: Int, status: String? = null): List<TmdbEpisode>? {
        if (status == "RELEASING") return null
        val data = _tmdbEpisodeCache.value[animeId]
        if (data != null && animeId !in _persistentTmdbIds) {
            val timestamp = _tmdbCacheTimestamps[animeId]
            if (timestamp != null) {
                val age = System.currentTimeMillis() - timestamp
                if (age > TMDB_CACHE_MAX_AGE_MS) {
                    _tmdbEpisodeCache.value -= animeId
                    _tmdbCacheTimestamps.remove(animeId)
                    return null
                }
            }
        }
        return data
    }

    fun cacheTmdbEpisodes(animeId: Int, episodes: List<TmdbEpisode>) {
        _tmdbEpisodeCache.value += (animeId to episodes)
        _tmdbCacheTimestamps[animeId] = System.currentTimeMillis()
        saveTmdbEpisodeCache()
    }

    fun clearTmdbEpisodeCache(animeId: Int) {
        _tmdbEpisodeCache.value -= animeId
        _tmdbCacheTimestamps.remove(animeId)
        _persistentTmdbIds.remove(animeId)
        saveTmdbEpisodeCache()
        savePersistentTmdbIds()
    }

    fun pruneTmdbEpisodeCache(retainedIds: Set<Int>) {
        val current = _tmdbEpisodeCache.value
        val filtered = current.filterKeys { it in retainedIds }
        val removedIds = current.keys - filtered.keys
        removedIds.forEach { _persistentTmdbIds.remove(it) }
        // Mark retained IDs as persistent and refresh their timestamps
        val now = System.currentTimeMillis()
        retainedIds.forEach { id ->
            _persistentTmdbIds.add(id)
            _tmdbCacheTimestamps[id] = now
        }
        if (filtered.size != current.size || removedIds.isNotEmpty()) {
            _tmdbEpisodeCache.value = filtered
            savePersistentTmdbIds()
            saveTmdbEpisodeCache()
        }
    }

    fun loadTmdbEpisodeCache() {
        try {
            // Load persistent IDs
            val idsData = sharedPreferences.getString(TMDB_PERSISTENT_IDS_PREFS, null)
            if (idsData != null) {
                _persistentTmdbIds.addAll(json.decodeFromString<List<Int>>(idsData))
            }

            val data = sharedPreferences.getString(TMDB_CACHE_PREFS, null) ?: return
            val parsed = json.decodeFromString<Map<String, String>>(data)
            val now = System.currentTimeMillis()
            val restored = mutableMapOf<Int, List<TmdbEpisode>>()
            for ((key, value) in parsed) {
                val parts = key.split("|")
                if (parts.size == 2) {
                    val id = parts[0].toIntOrNull() ?: continue
                    val timestamp = parts[1].toLongOrNull() ?: continue
                    if (id in _persistentTmdbIds || (now - timestamp) < TMDB_CACHE_MAX_AGE_MS) {
                        val episodes = json.decodeFromString<List<TmdbEpisode>>(value)
                        restored[id] = episodes
                        _tmdbCacheTimestamps[id] = timestamp
                    }
                }
            }
            _tmdbEpisodeCache.value = restored
        } catch (_: Exception) {}
    }

    fun saveTmdbEpisodeCache() {
        try {
            val entries = _tmdbEpisodeCache.value.mapValues { (_, episodes) ->
                json.encodeToString(episodes)
            }
            val timestamped = entries.mapKeys { (id, _) ->
                val ts = _tmdbCacheTimestamps[id] ?: System.currentTimeMillis()
                "$id|$ts"
            }
            sharedPreferences.edit { putString(TMDB_CACHE_PREFS, json.encodeToString(timestamped)) }
        } catch (_: Exception) {}
    }

    private fun savePersistentTmdbIds() {
        try {
            sharedPreferences.edit { putString(TMDB_PERSISTENT_IDS_PREFS, json.encodeToString(_persistentTmdbIds.toList())) }
        } catch (_: Exception) {}
    }

    fun invalidateUserCache() {
        sharedPreferences.edit {
            remove(CACHE_HOME_DATA)
                .remove(CACHE_HOME_TIME)
        }
    }

    private fun isCacheValid(cacheKey: String, customDuration: Long = CACHE_DURATION_MS): Boolean {
        val cacheTime = sharedPreferences.getLong(cacheKey, 0)
        val now = System.currentTimeMillis()
        return (now - cacheTime) < customDuration
    }

    private fun setCacheTime(cacheKey: String) {
        sharedPreferences.edit { putLong(cacheKey, System.currentTimeMillis()) }
    }

    fun saveHomeDataToCache(data: HomeCacheData) {
        try {
            val jsonString = json.encodeToString(HomeCacheData.serializer(), data)
            sharedPreferences.edit { putString(CACHE_HOME_DATA, jsonString) }
            setCacheTime(CACHE_HOME_TIME)
        } catch (_: Exception) {
        }
    }

    fun loadHomeDataFromCache(): HomeCacheData? {
        val cachedData = sharedPreferences.getString(CACHE_HOME_DATA, null)
        if (cachedData != null && isCacheValid(CACHE_HOME_TIME)) {
            return try {
                json.decodeFromString<HomeCacheData>(cachedData)
            } catch (_: Exception) { null }
        }
        return null
    }

    fun saveExploreDataToCache(data: ExploreCacheData) {
        try {
            val jsonString = json.encodeToString(ExploreCacheData.serializer(), data)
            sharedPreferences.edit { putString(CACHE_EXPLORE_DATA, jsonString) }
            setCacheTime(CACHE_EXPLORE_TIME)
        } catch (_: Exception) {
        }
    }

    fun loadExploreDataFromCache(): ExploreCacheData? {
        val cachedData = sharedPreferences.getString(CACHE_EXPLORE_DATA, null)
        if (cachedData != null && isCacheValid(CACHE_EXPLORE_TIME)) {
            return try {
                json.decodeFromString<ExploreCacheData>(cachedData)
            } catch (_: Exception) { null }
        }
        return null
    }

    fun saveAiringScheduleCache(scheduleByDay: Map<Int, List<AiringScheduleAnime>>, airingAnimeList: List<AiringScheduleAnime>) {
        try {
            val cacheData = AiringCacheData(scheduleByDay, airingAnimeList)
            val jsonString = json.encodeToString(AiringCacheData.serializer(), cacheData)
            sharedPreferences.edit { putString(CACHE_AIRING_DATA, jsonString) }
            setCacheTime(CACHE_AIRING_TIME)
        } catch (_: Exception) {
        }
    }

    fun loadAiringScheduleCache(): AiringCacheData? {
        val cachedData = sharedPreferences.getString(CACHE_AIRING_DATA, null) ?: return null
        return try {
            json.decodeFromString<AiringCacheData>(cachedData)
        } catch (_: Exception) { null }
    }

    fun loadStreamCache() {
        try {
            val cachedData = sharedPreferences.getString(CACHE_STREAM_DATA, null) ?: return
            val cacheData = json.decodeFromString<StreamCacheData>(cachedData)
            val now = System.currentTimeMillis()

            val streamMap = mutableMapOf<String, AniwatchStreamResult?>()
            val episodeMap = mutableMapOf<String, EpisodeStreams?>()

            cacheData.entries.filter { (now - it.value.timestamp) < STREAM_CACHE_DURATION_MS }
                .forEach { (key, entry) ->
                    streamMap[key] = entry.stream?.let {
                        val qualities = it.qualities.map { q -> QualityOption(q.quality, q.url, q.width) }
                        AniwatchStreamResult(
                            it.url,
                            it.isDirectStream,
                            it.headers,
                            it.subtitleUrl,
                            it.serverName,
                            it.category,
                            qualities,
                            // Skip timestamps
                            it.introStart,
                            it.introEnd,
                            it.outroStart,
                            it.outroEnd
                        )
                    }
                    entry.episodeInfo?.let { ep ->
                        episodeMap[key] = EpisodeStreams(
                            ep.subServers.map { s ->
                                val quals = s.qualities.map { q -> QualityOption(q.quality, q.url, q.width) }
                                ServerInfo(s.name, s.url, quals)
                            },
                            ep.dubServers.map { s ->
                                val quals = s.qualities.map { q -> QualityOption(q.quality, q.url, q.width) }
                                ServerInfo(s.name, s.url, quals)
                            },
                            ep.animeId, ep.episodeId
                        )
                    }
                }

            _prefetchedStreams.value = streamMap
            _prefetchedEpisodeInfo.value = episodeMap
        } catch (_: Exception) {
        }
    }

    fun saveStreamCache() {
        try {
            val now = System.currentTimeMillis()
            val entries = _prefetchedStreams.value.mapValues { (key, stream) ->
                val ep = _prefetchedEpisodeInfo.value[key]
                StreamCacheEntry(
                    stream = stream?.let {
                        val qualities = it.qualities.map { q -> CachedQuality(q.quality, q.url, q.width) }
                        CachedStream(
                            it.url,
                            it.isDirectStream,
                            it.headers,
                            it.subtitleUrl,
                            it.serverName,
                            it.category,
                            qualities,
                            // Skip timestamps
                            it.introStart,
                            it.introEnd,
                            it.outroStart,
                            it.outroEnd
                        )
                    },
                    episodeInfo = ep?.let { info ->
                        CachedEpisodeInfo(
                            info.subServers.map { s ->
                                val quals = s.qualities.map { q -> CachedQuality(q.quality, q.url, q.width) }
                                CachedServer(s.name, s.url, quals)
                            },
                            info.dubServers.map { s ->
                                val quals = s.qualities.map { q -> CachedQuality(q.quality, q.url, q.width) }
                                CachedServer(s.name, s.url, quals)
                            },
                            info.animeId, info.episodeId
                        )
                    },
                    timestamp = now
                )
            }

            val jsonString = json.encodeToString(StreamCacheData.serializer(), StreamCacheData(entries))
            sharedPreferences.edit { putString(CACHE_STREAM_DATA, jsonString) }
        } catch (_: Exception) {
        }
    }

    fun loadPlaybackPositions() {
        try {
            val cachedData = sharedPreferences.getString(CACHE_PLAYBACK_POSITIONS, null) ?: return
            val cacheData = json.decodeFromString<PlaybackPositionCache>(cachedData)
            _playbackPositions.value = cacheData.positions
            _playbackDurations.value = cacheData.durations
        } catch (_: Exception) {
        }
    }

    fun savePlaybackPosition(animeId: Int, episode: Int, position: Long, duration: Long = 0L, isOffline: Boolean = false) {
        val key = "${animeId}_$episode${if (isOffline) "_offline" else ""}"
        _playbackPositions.value += (key to position)
        if (duration > 0L) {
            _playbackDurations.value += (key to duration)
        }
        try {
            val jsonString = json.encodeToString(
                PlaybackPositionCache.serializer(),
                PlaybackPositionCache(_playbackPositions.value, _playbackDurations.value)
            )
            sharedPreferences.edit { putString(CACHE_PLAYBACK_POSITIONS, jsonString) }
        } catch (_: Exception) { }
    }

    fun getPlaybackPosition(animeId: Int, episode: Int, isOffline: Boolean = false): Long = _playbackPositions.value["${animeId}_$episode${if (isOffline) "_offline" else ""}"] ?: 0L

    fun getPlaybackDuration(animeId: Int, episode: Int, isOffline: Boolean = false): Long = _playbackDurations.value["${animeId}_$episode${if (isOffline) "_offline" else ""}"] ?: 0L

    fun clearPlaybackPosition(animeId: Int, episode: Int, isOffline: Boolean = false) {
        val key = "${animeId}_$episode${if (isOffline) "_offline" else ""}"
        if (_playbackPositions.value.containsKey(key)) {
            _playbackPositions.value -= key
            _playbackDurations.value -= key
            sharedPreferences.edit {
                val jsonString = json.encodeToString(
                    PlaybackPositionCache.serializer(),
                    PlaybackPositionCache(_playbackPositions.value, _playbackDurations.value)
                )
                putString(CACHE_PLAYBACK_POSITIONS, jsonString)
            }
        }
    }

    fun clearAllPlaybackPositionsForAnime(animeId: Int) {
        val prefix = "${animeId}_"
        val newMap = _playbackPositions.value.filterKeys { !it.startsWith(prefix) }
        _playbackPositions.value = newMap
        _playbackDurations.value = _playbackDurations.value.filterKeys { !it.startsWith(prefix) }
        sharedPreferences.edit {
            val jsonString = json.encodeToString(
                PlaybackPositionCache.serializer(),
                PlaybackPositionCache(_playbackPositions.value, _playbackDurations.value)
            )
            putString(CACHE_PLAYBACK_POSITIONS, jsonString)
        }
    }

    fun hasStream(key: String): Boolean {
        val exists = _prefetchedStreams.value.containsKey(key)
        return exists
    }

    /**
     * Clear stream cache for a specific episode.
     * Call this when a stream fails to force refetch on next attempt.
     */
    fun invalidateStreamCache(animeId: Int, episode: Int, category: String? = null) {
        val keysToRemove = mutableListOf<String>()

        if (category != null) {
            // Remove specific category cache
            keysToRemove.add("${animeId}_${episode}_$category")
        } else {
            // Remove both sub and dub caches
            keysToRemove.add("${animeId}_${episode}_sub")
            keysToRemove.add("${animeId}_${episode}_dub")
        }

        // Also remove the old-style key
        keysToRemove.add("${animeId}_$episode")

        val newMap = _prefetchedStreams.value.toMutableMap()
        var removed = false
        keysToRemove.forEach { key ->
            if (newMap.containsKey(key)) {
                newMap.remove(key)
                removed = true
            }
        }

        if (removed) {
            _prefetchedStreams.value = newMap
            saveStreamCache()
        }
    }

    private val _extensionStreamCache = mutableMapOf<String, CachedExtensionStream>()

    fun getCachedExtensionStream(key: String): CachedExtensionStream? {
        val cached = _extensionStreamCache[key] ?: return null
        val now = System.currentTimeMillis()
        if (now - cached.cachedAt > STREAM_CACHE_DURATION_MS) {
            _extensionStreamCache.remove(key)
            saveExtensionStreamCache()
            return null
        }
        return cached
    }

    fun cacheExtensionStream(key: String, data: CachedExtensionStream) {
        _extensionStreamCache[key] = data
        saveExtensionStreamCache()
    }

    fun invalidateExtensionStreamCache(key: String) {
        _extensionStreamCache.remove(key)
        saveExtensionStreamCache()
    }

    fun clearAnimeExtensionStreamCaches(animeId: Int) {
        val prefix = "${animeId}_"
        val keysToRemove = _extensionStreamCache.keys.filter { it.startsWith(prefix) }
        keysToRemove.forEach { _extensionStreamCache.remove(it) }
        if (keysToRemove.isNotEmpty()) {
            saveExtensionStreamCache()
        }
    }

    fun clearAllExtensionStreamCaches() {
        _extensionStreamCache.clear()
        saveExtensionStreamCache()
    }

    fun loadExtensionStreamCache() {
        try {
            val raw = sharedPreferences.getString(CACHE_EXTENSION_STREAMS, null) ?: return
            val parsed = json.decodeFromString<Map<String, CachedExtensionStream>>(raw)
            val now = System.currentTimeMillis()
            _extensionStreamCache.clear()
            _extensionStreamCache.putAll(parsed.filter { (now - it.value.cachedAt) < STREAM_CACHE_DURATION_MS })
        } catch (_: Exception) {}
    }

    private fun saveExtensionStreamCache() {
        try {
            val raw = json.encodeToString(_extensionStreamCache)
            sharedPreferences.edit { putString(CACHE_EXTENSION_STREAMS, raw) }
        } catch (_: Exception) {}
    }

    // Time-based expiration for detailed anime cache
    private val _detailedAnimeCacheTimestamps = mutableMapOf<Int, Long>()
    private val DETAILED_ANIME_CACHE_MAX_AGE_MS = 24 * 60 * 60 * 1000L // 24 hours
    private val MAX_DETAILED_ANIME_CACHE_SIZE = 50

    fun cacheDetailedAnime(animeId: Int, data: DetailedAnimeData) {
        _detailedAnimeCache.value += (animeId to data)
        _detailedAnimeCacheTimestamps[animeId] = System.currentTimeMillis()
        trimDetailedAnimeCacheToLimit()
    }

    fun clearDetailedAnimeCache(animeId: Int) {
        val updated = _detailedAnimeCache.value.toMutableMap()
        updated.remove(animeId)
        _detailedAnimeCache.value = updated
        _detailedAnimeCacheTimestamps.remove(animeId)
    }

    fun clearExpiredDetailedAnimeCache() {
        val now = System.currentTimeMillis()
        val expiredKeys = _detailedAnimeCacheTimestamps.filter { (_, timestamp) ->
            (now - timestamp) > DETAILED_ANIME_CACHE_MAX_AGE_MS
        }.keys

        if (expiredKeys.isNotEmpty()) {
            val updated = _detailedAnimeCache.value.toMutableMap()
            expiredKeys.forEach { key ->
                updated.remove(key)
                _detailedAnimeCacheTimestamps.remove(key)
            }
            _detailedAnimeCache.value = updated
        }
    }

    private fun trimDetailedAnimeCacheToLimit() {
        if (_detailedAnimeCache.value.size > MAX_DETAILED_ANIME_CACHE_SIZE) {
            val sortedByTime = _detailedAnimeCacheTimestamps.entries.sortedBy { it.value }
            val keysToRemove = sortedByTime.take(_detailedAnimeCache.value.size - MAX_DETAILED_ANIME_CACHE_SIZE).map { it.key }

            val updated = _detailedAnimeCache.value.toMutableMap()
            keysToRemove.forEach { key ->
                updated.remove(key)
                _detailedAnimeCacheTimestamps.remove(key)
            }
            _detailedAnimeCache.value = updated
        }
    }

    fun invalidateAllStreamCaches() {
        _prefetchedStreams.value = emptyMap()
        _prefetchedEpisodeInfo.value = emptyMap()
        saveStreamCache()
    }

    fun clearAllCaches() {
        _prefetchedStreams.value = emptyMap()
        _prefetchedEpisodeInfo.value = emptyMap()
        _detailedAnimeCache.value = emptyMap()
        _detailedAnimeCacheTimestamps.clear()
        _tmdbCacheTimestamps.clear()
        _persistentTmdbIds.clear()
        sharedPreferences.edit { clear() }
    }

    // Remove a specific video URL from the ExoPlayer cache
    // CacheDataSource uses CacheKeyFactory.DEFAULT which keys by uri.toString()
    @OptIn(UnstableApi::class)
    fun removeFromVideoCache(videoUrl: String) {
        val cache = videoCache ?: return
        try {
            cache.removeResource(videoUrl)
        } catch (_: Exception) {}
    }

    // ExoPlayer video cache management
    @OptIn(UnstableApi::class)
    fun clearVideoCache(context: Context): Long {
        var bytesCleared = 0L

        // First release the cache if it exists
        val cache = videoCache
        if (cache != null) {
            try {
                cache.release()
                videoCache = null
                isCacheInitialized = false
            } catch (_: Exception) {
                // Cache release failed
            }
        }

        // Clear from disk
        try {
            val cacheDir = File(context.cacheDir, "video_cache")
            if (cacheDir.exists()) {
                cacheDir.listFiles()?.forEach { file ->
                    bytesCleared += file.length()
                    file.delete()
                }
            }
        } catch (_: Exception) {
            // Disk cleanup failed
        }

        return bytesCleared
    }

    @OptIn(UnstableApi::class)
    fun getVideoCacheSize(context: Context): Long {
        val cache = videoCache
        if (cache != null) {
            try {
                val cacheDir = File(context.cacheDir, "video_cache")
                if (cacheDir.exists()) {
                    return cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                }
            } catch (_: Exception) {
                // Error getting cache size
            }
        }

        // Fallback to disk calculation
        try {
            val cacheDir = File(context.cacheDir, "video_cache")
            if (cacheDir.exists()) {
                return cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            }
        } catch (_: Exception) {
            // Error getting disk cache size
        }
        return 0L
    }

    // Clear all non-essential caches (for settings "Clear Cache" button)
    fun clearNonEssentialCaches(context: Context) {
        clearVideoCache(context)
        clearExpiredDetailedAnimeCache()
        trimDetailedAnimeCacheToLimit()

        // Clear stream cache but keep playback positions
        _prefetchedStreams.value = emptyMap()
        _prefetchedEpisodeInfo.value = emptyMap()
        sharedPreferences.edit { remove(CACHE_STREAM_DATA) }
    }
}


