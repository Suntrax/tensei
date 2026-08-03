package com.blissless.tensei.data.manga

import com.blissless.tensei.data.models.MangaChapter
import com.blissless.tensei.data.models.MangaDexAggregate
import com.blissless.tensei.data.models.MangaDexAtHome
import com.blissless.tensei.data.models.MangaDexMangaResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class MangaDexManager(private val client: OkHttpClient = DEFAULT_CLIENT) {

    companion object {
        private const val BASE_URL = "https://api.mangadex.org"
        private const val TAG = "MangaDexManager"
        private val DEFAULT_CLIENT = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Tensei/1.0 (https://github.com/Suntrax/tensei)")
                    .build()
                chain.proceed(request)
            }
            .build()

        private val json = Json { ignoreUnknownKeys = true }
    }

    suspend fun findMangaByAniListId(title: String, aniListId: Int): String? = withContext(Dispatchers.IO) {
        val sanitized = title.take(80).replace("&", "and")
        val encoded = URLEncoder.encode(sanitized, "UTF-8")
        val url = "$BASE_URL/manga?limit=10&title=$encoded&order[relevance]=desc&includes[]=cover_art"
        val request = Request.Builder().url(url).get().build()
        val response = try {
            client.newCall(request).execute()
        } catch (e: Exception) {
            com.blissless.tensei.util.ErrorHandler.ignore(TAG, "search failed", e)
            return@withContext null
        }
        if (!response.isSuccessful) return@withContext null
        val body = response.body.string()
        val parsed = try {
            json.decodeFromString<MangaDexMangaResponse>(body)
        } catch (e: Exception) {
            com.blissless.tensei.util.ErrorHandler.ignore(TAG, "parse failed", e)
            return@withContext null
        }
        val data = parsed.data ?: return@withContext null

        // 1. Exact match by AniList ID in links
        val exactMatch = data.firstOrNull { manga ->
            manga.attributes?.links?.al == aniListId.toString()
        }
        if (exactMatch != null) return@withContext exactMatch.id

        // 2. Exact title match (case-insensitive)
        val titleLower = title.lowercase().trim()
        val exactTitle = data.firstOrNull { manga ->
            val attrs = manga.attributes ?: return@firstOrNull false
            val mangaTitle = attrs.title?.en?.lowercase()?.trim() ?: ""
            val altTitles = attrs.altTitles?.mapNotNull { it.en?.lowercase()?.trim() } ?: emptyList()
            mangaTitle == titleLower || altTitles.any { it == titleLower }
        }
        if (exactTitle != null) return@withContext exactTitle.id

        // 3. Partial contains match
        data.firstOrNull { manga ->
            val attrs = manga.attributes ?: return@firstOrNull false
            val mangaTitle = attrs.title?.en?.lowercase() ?: ""
            val altTitles = attrs.altTitles?.mapNotNull { it.en?.lowercase() } ?: emptyList()
            mangaTitle.contains(titleLower) || altTitles.any { it.contains(titleLower) }
        }?.id
    }

    suspend fun fetchAggregate(mangaUuid: String): MangaDexAggregate? = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/manga/$mangaUuid/aggregate?translatedLanguage[]=en"
        val request = Request.Builder().url(url).get().build()
        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null
            json.decodeFromString<MangaDexAggregate>(response.body.string())
        } catch (e: Exception) {
            com.blissless.tensei.util.ErrorHandler.ignore(TAG, "fetchAggregate failed", e)
            null
        }
    }

    /**
     * Build a flat, deduplicated chapter list from a MangaDex aggregate.
     *
     * "Others" (duplicate chapter IDs for the same chapter number) are collapsed into a single
     * entry — we use the first ID. This prevents the reader's grouped chapter list from showing
     * N duplicate rows for the same chapter number.
     */
    suspend fun buildChapterList(aggregate: MangaDexAggregate?): List<MangaChapter> {
        if (aggregate?.volumes == null) return emptyList()
        val chapters = mutableListOf<MangaChapter>()
        val seenNumbers = mutableSetOf<Float>()
        val sortedVolumes = aggregate.volumes.entries.sortedBy { it.key.toFloatOrNull() ?: 0f }
        for ((_, volume) in sortedVolumes) {
            val volumeChapters = volume.chapters?.entries?.sortedBy { it.key.toFloatOrNull() ?: 0f } ?: continue
            for ((_, ch) in volumeChapters) {
                val chapterNum = ch.chapter?.toFloatOrNull() ?: continue
                val firstId = ch.id ?: continue
                if (seenNumbers.add(chapterNum)) {
                    chapters.add(
                        MangaChapter(
                            url = "https://mangadex.org/chapter/$firstId",
                            title = "Ch. $chapterNum",
                            chapterId = firstId,
                            volume = volume.volume?.toFloatOrNull(),
                            chapterNumber = chapterNum
                        )
                    )
                }
                // "others" are alternate scanlation groups for the same chapter number — skipped
            }
        }
        return chapters
    }

    suspend fun fetchChapterImages(chapterId: String, useDataSaver: Boolean = false): List<String>? = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/at-home/server/$chapterId"
        val request = Request.Builder().url(url).get().build()
        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null
            val atHome = json.decodeFromString<MangaDexAtHome>(response.body.string())
            val baseUrl = atHome.baseUrl ?: return@withContext null
            val chapter = atHome.chapter ?: return@withContext null
            val hash = chapter.hash ?: return@withContext null
            val files = if (useDataSaver) chapter.dataSaver else chapter.data
            files?.map { "${baseUrl}/data${if (useDataSaver) "-saver" else ""}/${hash}/$it" }
        } catch (e: Exception) {
            com.blissless.tensei.util.ErrorHandler.ignore(TAG, "fetchChapterImages failed", e)
            null
        }
    }
}
