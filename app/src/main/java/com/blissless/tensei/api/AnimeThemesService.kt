package com.blissless.tensei.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Service for fetching anime opening/ending videos from AnimeThemes API.
 * Optimized with OkHttpClient for better connection management.
 */
class AnimeThemesService {

    companion object {
        private const val API_BASE = "https://api.animethemes.moe"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    suspend fun searchAnimeThemes(animeName: String, year: Int? = null): AnimeThemesResult? {
        return withContext(Dispatchers.IO) {
            try {
                val slug = convertToSlug(animeName)
                getAnimeThemesBySlug(slug) ?: run {
                    generateSlugVariations(animeName, year).firstNotNullOfOrNull { variation ->
                        getAnimeThemesBySlug(variation)
                    }
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    suspend fun getAnimeThemesBySlug(slug: String): AnimeThemesResult? = withContext(Dispatchers.IO) {
        try {
            val url = "$API_BASE/anime/$slug?include=animethemes.animethemeentries.videos"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", "AnimeApp/1.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body.string()
                    run {
                        val data = json.decodeFromString<AnimeThemesApiResponse>(body)
                        extractThemesFromAnime(data.anime)
                    }
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun convertToSlug(name: String): String = name.lowercase()
        .replace(Regex("[^a-z0-9\\s-]"), "")
        .replace(Regex("\\s+"), "-")
        .replace(Regex("-+"), "-")
        .trim('-')

    private fun generateSlugVariations(name: String, year: Int?): List<String> {
        val variations = mutableListOf<String>()
        val baseSlug = convertToSlug(name)
        if (year != null) variations.add("$baseSlug-$year")

        val cleanedName = name
            .replace(Regex(": season \\d+", RegexOption.IGNORE_CASE), "")
            .replace(Regex("season \\d+", RegexOption.IGNORE_CASE), "")
            .replace(Regex(" - .+"), "")
            .replace(Regex(": .+"), "")
            .trim()

        if (cleanedName != name) {
            val cleanedSlug = convertToSlug(cleanedName)
            variations.add(cleanedSlug)
            if (year != null) variations.add("$cleanedSlug-$year")
        }
        return variations.distinct()
    }

    private fun extractThemesFromAnime(anime: AnimeThemesAnime): AnimeThemesResult {
        val openings = mutableListOf<ThemeEntry>()
        val endings = mutableListOf<ThemeEntry>()

        anime.animethemes?.forEach { theme ->
            val isOp = theme.type?.startsWith("OP") == true || theme.slug?.startsWith("OP") == true
            val isEd = theme.type?.startsWith("ED") == true || theme.slug?.startsWith("ED") == true

            theme.animethemeentries?.forEach { entry ->
                entry.videos?.forEach { video ->
                    val themeEntry = ThemeEntry(
                        type = theme.type ?: "Unknown",
                        sequence = theme.sequence,
                        songTitle = null,
                        artist = null,
                        videoUrl = video.link,
                        audioUrl = null,
                        duration = 90,
                        size = video.size,
                        resolution = video.resolution
                    )
                    if (isOp) openings.add(themeEntry) else if (isEd) endings.add(themeEntry)
                }
            }
        }

        return AnimeThemesResult(anime.name, anime.slug, anime.year, openings, endings)
    }
}

@Serializable
data class AnimeThemesApiResponse(val anime: AnimeThemesAnime)

@Serializable
data class AnimeThemesAnime(
    val id: Int? = null,
    val name: String = "",
    val slug: String = "",
    val year: Int? = null,
    val animethemes: List<AnimeTheme>? = null
)

@Serializable
data class AnimeTheme(
    val id: Int? = null,
    val type: String? = null,
    val slug: String? = null,
    val sequence: Int? = null,
    val animethemeentries: List<AnimeThemeEntry>? = null
)

@Serializable
data class AnimeThemeEntry(
    val id: Int? = null,
    val videos: List<AnimeThemeVideo>? = null
)

@Serializable
data class AnimeThemeVideo(
    val id: Int? = null,
    val filename: String? = null,
    val link: String? = null,
    val resolution: Int? = null,
    val size: Int? = null
)

data class AnimeThemesResult(
    val animeName: String,
    val animeSlug: String,
    val year: Int?,
    val openings: List<ThemeEntry>,
    val endings: List<ThemeEntry>
)

data class ThemeEntry(
    val type: String,
    val sequence: Int?,
    val songTitle: String?,
    val artist: String?,
    val videoUrl: String?,
    val audioUrl: String?,
    val duration: Int?,
    val size: Int?,
    val resolution: Int?
)


