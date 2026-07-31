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
                    .header("User-Agent", "Tensei/1.0")
                    .build()
                chain.proceed(request)
            }
            .build()

        private val json = Json { ignoreUnknownKeys = true }
    }

    suspend fun findMangaByAniListId(title: String, aniListId: Int): String? = withContext(Dispatchers.IO) {
        val searchQuery = title.take(80).replace("&", "and")
        val url = "$BASE_URL/manga?limit=10&title=$searchQuery&includes[]=cover_art"
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

        val exactMatch = data.firstOrNull { manga ->
            manga.attributes?.links?.al == aniListId.toString()
        }
        if (exactMatch != null) return@withContext exactMatch.id

        val titleLower = title.lowercase()
        data.firstOrNull { manga ->
            val attrs = manga.attributes ?: return@firstOrNull false
            val mangaTitle = attrs.title?.en?.lowercase() ?: ""
            val altTitles = attrs.altTitles?.mapNotNull { it.en?.lowercase() } ?: emptyList()
            mangaTitle == titleLower || altTitles.any { it == titleLower } ||
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

    suspend fun buildChapterList(aggregate: MangaDexAggregate?): List<MangaChapter> {
        if (aggregate?.volumes == null) return emptyList()
        val chapters = mutableListOf<MangaChapter>()
        val sortedVolumes = aggregate.volumes.entries.sortedBy { it.key.toFloatOrNull() ?: 0f }
        for ((_, volume) in sortedVolumes) {
            val volumeChapters = volume.chapters?.entries?.sortedBy { it.key.toFloatOrNull() ?: 0f } ?: continue
            for ((_, ch) in volumeChapters) {
                val chapterNum = ch.chapter?.toFloatOrNull() ?: continue
                val chapterId = ch.id ?: continue
                chapters.add(
                    MangaChapter(
                        url = "https://mangadex.org/chapter/$chapterId",
                        title = "Ch. $chapterNum",
                        chapterId = chapterId,
                        volume = volume.volume?.toFloatOrNull(),
                        chapterNumber = chapterNum
                    )
                )
                ch.others?.forEach { otherId ->
                    chapters.add(
                        MangaChapter(
                            url = "https://mangadex.org/chapter/$otherId",
                            title = "Ch. $chapterNum",
                            chapterId = otherId,
                            volume = volume.volume?.toFloatOrNull(),
                            chapterNumber = chapterNum
                        )
                    )
                }
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
