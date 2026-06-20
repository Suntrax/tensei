package com.blissless.tensei.api

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import androidx.core.content.edit
import com.blissless.tensei.data.models.EpisodeTimestamps
import com.blissless.tensei.data.models.Timestamp

/**
 * Local cache for storing computed OP/ED timestamps.
 *
 * This avoids re-computing fingerprints for episodes that have already been processed.
 * Timestamps are stored per anime/episode and persist across app restarts.
 */
class TimestampCache(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "timestamp_cache"
        private const val KEY_CACHE_DATA = "cache_data"
        private const val KEY_CACHE_VERSION = "cache_version"

        // Increment this when cache format changes
        private const val CURRENT_VERSION = 1
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = false
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Cached timestamp entry
     */
    @Serializable
    data class CachedTimestamp(
        val animeId: Int,
        val animeName: String,
        val episodeNumber: Int,
        val introStart: Float? = null,  // in seconds
        val introEnd: Float? = null,
        val creditsStart: Float? = null,
        val creditsEnd: Float? = null,
        val source: String,              // "aniskip", "animethemes", "fingerprint", "animekai"
        val confidence: Float = 1.0f,    // 0.0 to 1.0
        val timestamp: Long = System.currentTimeMillis()
    ) {
        fun hasTimestamps(): Boolean = introStart != null || creditsStart != null
    }

    /**
     * Cache data structure
     */
    @Serializable
    data class CacheData(
        val entries: Map<String, CachedTimestamp> = emptyMap()
    )

    /**
     * Get cached timestamp for an anime/episode
     */
    fun getTimestamp(animeId: Int, episodeNumber: Int): CachedTimestamp? {
        val key = generateKey(animeId, episodeNumber)
        return getTimestampByKey(key)
    }

    /**
     * Get cached timestamp by anime name (fallback when no ID)
     */
    fun getTimestampByName(animeName: String, episodeNumber: Int): CachedTimestamp? {
        val key = generateKeyNameKey(animeName, episodeNumber)
        return getTimestampByKey(key)
    }

    private fun getTimestampByKey(key: String): CachedTimestamp? {
        return try {
            val cacheData = loadCacheData()
            cacheData.entries[key]
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Save timestamp to cache
     */
    fun saveTimestamp(timestamp: CachedTimestamp) {
        try {
            val currentData = loadCacheData()
            val mutableEntries = currentData.entries.toMutableMap()

            // Save by anime ID
            if (timestamp.animeId > 0) {
                val key = generateKey(timestamp.animeId, timestamp.episodeNumber)
                mutableEntries[key] = timestamp
            }

            // Also save by name for fallback lookups
            val nameKey = generateKeyNameKey(timestamp.animeName, timestamp.episodeNumber)
            mutableEntries[nameKey] = timestamp

            // Create new immutable cache data
            val newData = CacheData(entries = mutableEntries.toMap())
            saveCacheData(newData)

        } catch (_: Exception) {
        }
    }

    /**
     * Save OP/ED timestamps from EpisodeTimestamps format.
     * This is the unified method for saving timestamps from any source.
     *
     * @param animeId The anime ID (can be MAL ID or AniList ID)
     * @param animeName The anime name for fallback lookups
     * @param episodeNumber The episode number
     * @param timestamps The timestamps to save
     * @param source The source identifier ("aniskip", "animethemes", "fingerprint", "animekai")
     */
    fun saveFromEpisodeTimestamps(
        animeId: Int,
        animeName: String,
        episodeNumber: Int,
        timestamps: EpisodeTimestamps,
        source: String
    ) {
        val cached = CachedTimestamp(
            animeId = animeId,
            animeName = animeName,
            episodeNumber = episodeNumber,
            introStart = timestamps.introStart?.toFloat(),
            introEnd = timestamps.introEnd?.toFloat(),
            creditsStart = timestamps.creditsStart?.toFloat(),
            creditsEnd = timestamps.creditsEnd?.toFloat(),
            source = source
        )
        saveTimestamp(cached)
    }

    /**
     * Convert CachedTimestamp to EpisodeTimestamps format.
     */
    fun toEpisodeTimestamps(cached: CachedTimestamp): EpisodeTimestamps {
        return EpisodeTimestamps(
            episodeNumber = cached.episodeNumber,
            introStart = cached.introStart?.toLong(),
            introEnd = cached.introEnd?.toLong(),
            creditsStart = cached.creditsStart?.toLong(),
            creditsEnd = cached.creditsEnd?.toLong(),
            recapStart = null,
            recapEnd = null,
            allTimestamps = buildList {
                if (cached.introStart != null && cached.introEnd != null) {
                    add(Timestamp(cached.introStart.toDouble(), "op", "op"))
                }
                if (cached.creditsStart != null) {
                    add(Timestamp(cached.creditsStart.toDouble(), "ed", "ed"))
                }
            }
        )
    }

    // Helper functions

    private fun generateKey(animeId: Int, episodeNumber: Int): String {
        return "$animeId|$episodeNumber"
    }

    private fun generateKeyNameKey(animeName: String, episodeNumber: Int): String {
        return "name|${animeName.lowercase()}|$episodeNumber"
    }

    private fun loadCacheData(): CacheData {
        return try {
            // Check version
            val version = prefs.getInt(KEY_CACHE_VERSION, 0)
            if (version != CURRENT_VERSION) {
                // Version mismatch, clear cache
                prefs.edit { clear().putInt(KEY_CACHE_VERSION, CURRENT_VERSION) }
                return CacheData()
            }

            val jsonString = prefs.getString(KEY_CACHE_DATA, null)
            if (jsonString != null) {
                json.decodeFromString<CacheData>(jsonString)
            } else {
                CacheData()
            }
        } catch (_: Exception) {
            CacheData()
        }
    }

    private fun saveCacheData(data: CacheData) {
        val jsonString = json.encodeToString(data)
        prefs.edit {
            putString(KEY_CACHE_DATA, jsonString)
                .putInt(KEY_CACHE_VERSION, CURRENT_VERSION)
        }
    }
}


