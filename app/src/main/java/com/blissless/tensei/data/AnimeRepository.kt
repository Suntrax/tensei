package com.blissless.tensei.data

import android.util.Log
import com.blissless.tensei.BuildConfig
import com.blissless.tensei.data.models.AiringScheduleEntry
import com.blissless.tensei.data.models.AiringScheduleResponse
import com.blissless.tensei.data.models.AllCharactersResponse
import com.blissless.tensei.data.models.AllStaffResponse
import com.blissless.tensei.data.models.AnimeRelation
import com.blissless.tensei.data.models.AnimeRelationsMedia
import com.blissless.tensei.data.models.AnimeRelationsResponse
import com.blissless.tensei.data.models.BatchedExploreResponse
import com.blissless.tensei.data.models.CharacterData
import com.blissless.tensei.data.models.CharacterResponse
import com.blissless.tensei.data.models.DetailedAnimeMedia
import com.blissless.tensei.data.models.DetailedAnimeResponse
import com.blissless.tensei.data.models.ExploreMedia
import com.blissless.tensei.data.models.ExploreResponse
import com.blissless.tensei.data.models.MediaListResponse
import com.blissless.tensei.data.models.MediaTag
import com.blissless.tensei.data.models.MediaTagCollectionResponse
import com.blissless.tensei.data.models.SimpleActivityResponse
import com.blissless.tensei.data.models.StaffData
import com.blissless.tensei.data.models.StaffResponse
import com.blissless.tensei.data.models.TmdbEpisode
import com.blissless.tensei.data.models.TmdbSearchResponse
import com.blissless.tensei.data.models.TmdbSearchResult
import com.blissless.tensei.data.models.TmdbSeasonDetails
import com.blissless.tensei.data.models.TmdbTvDetails
import com.blissless.tensei.data.models.UserActivity
import com.blissless.tensei.data.models.UserFavoritesResponse
import com.blissless.tensei.data.models.UserStatsResponse
import com.blissless.tensei.data.models.ViewerResponse
import com.blissless.tensei.network.GraphQLClient
import com.blissless.tensei.network.GraphQLConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection

/**
 * Handles all API calls and data fetching.
 * Optimized to use GraphQLClient for high-performance AniList requests.
 */
class AnimeRepository(
    private val userPreferences: UserPreferences,
    private val cacheManager: CacheManager
) {

    companion object {
        private val CLIENT_IDS = listOf(BuildConfig.CLIENT_ID_ANILIST)

    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    // High-performance GraphQL client
    private val graphQLClient = GraphQLClient(
        config = GraphQLConfig(
            maxConcurrentRequests = 5,
            minRequestIntervalMs = 100L,
            cacheDurationMs = 60 * 60 * 1000L, // 1 hour for public data
            userDataCacheDurationMs = 60 * 60 * 1000L // 1 hour for user data
        )
    )

    // Use longer cache for authenticated requests
    private val authCacheDuration get() = graphQLClient.getConfig().userDataCacheDurationMs

    // ============================================
    // GraphQL Requests (Optimized via GraphQLClient)
    // ============================================

    suspend fun graphqlRequest(query: String, variables: Map<String, Any?>): String? {
        val token = userPreferences.authToken.value ?: return null

        val result = graphQLClient.execute(
            query = query,
            variables = variables,
            requiresAuth = true,
            authToken = token,
            clientIds = CLIENT_IDS,
            useCache = true,
            cacheDurationMs = authCacheDuration, // Use longer cache for user data
            parser = { it } // Return raw string for existing parsing logic
        )

        return result.data
    }

    suspend fun graphqlMutation(query: String, variables: Map<String, Any?>): String? {
        val token = userPreferences.authToken.value ?: return null

        val result = graphQLClient.execute(
            query = query,
            variables = variables,
            requiresAuth = true,
            authToken = token,
            clientIds = CLIENT_IDS,
            useCache = false, // Mutations should never be cached
            parser = { it }
        )

        return result.data
    }

    suspend fun publicGraphqlRequest(query: String, variables: Map<String, Any?>): String? {
        val result = graphQLClient.execute(
            query = query,
            variables = variables,
            requiresAuth = false,
            clientIds = CLIENT_IDS,
            useCache = true,
            cacheDurationMs = 60 * 60 * 1000L, // 1 hour for public data
            parser = { it }
        )

        if (result.data == null) {
            Log.e("GraphQLDebug", "Error: ${result.error?.message}")
        } else {
            Log.d("GraphQLDebug", "Success: ${result.data.take(200)}")
        }
        return result.data
    }

    // ============================================
    // User Operations
    // ============================================

    suspend fun fetchUser(): ViewerResponse? {
        val query = """
            query {
                Viewer {
                    id
                    name
                    about
                    avatar { medium large }
                    bannerImage
                    siteUrl
                    createdAt
                    statistics {
                        anime {
                            count
                            episodesWatched
                            minutesWatched
                            meanScore
                        }
                    }
                }
            }
        """.trimIndent()

        return graphqlRequest(query, emptyMap())?.let {
            try {
                json.decodeFromString<ViewerResponse>(it)
            } catch (_: Exception) {
                null
            }
        }
    }

    suspend fun fetchUserStats(userId: Int): UserStatsResponse? {
        val query = $$"""
            query ($userId: Int) {
                User(id: $userId) {
                    statistics {
                        anime {
                            count
                            episodesWatched
                            minutesWatched
                            meanScore
                        }
                    }
                }
            }
        """.trimIndent()

        return graphqlRequest(query, mapOf("userId" to userId))?.let {
            try {
                json.decodeFromString<UserStatsResponse>(it)
            } catch (_: Exception) {
                null
            }
        }
    }

    // ============================================
    // Anime Lists
    // ============================================

    suspend fun fetchMediaLists(userId: Int): MediaListResponse? {
        val query = $$"""
            query ($userId: Int) {
                MediaListCollection(userId: $userId, type: ANIME) {
                    lists {
                        name
                        status
                        entries {
                            id
                            mediaId
                            progress
                            status
                            media {
                                id
                                idMal
                                title { romaji english }
                                coverImage { extraLarge }
                                bannerImage
                                episodes
                                nextAiringEpisode { episode airingAt }
                                status
                                averageScore
                                genres
                                seasonYear
                            }
                        }
                    }
                }
            }
        """.trimIndent()

        return graphqlRequest(query, mapOf("userId" to userId))?.let {
            try {
                json.decodeFromString<MediaListResponse>(it)
            } catch (_: Exception) {
                null
            }
        }
    }

    // ============================================
    // Explore Data
    // ============================================

    data class ExploreResult(val response: BatchedExploreResponse?, val error: String?)

    suspend fun fetchBatchedExploreWithError(useCache: Boolean = true): ExploreResult {
        val query = """
            query {
                featured: Page(page: 1, perPage: 10) {
                    media(type: ANIME, status: RELEASING, sort: POPULARITY_DESC) {
                        id
                        idMal
                        title { romaji english }
                        coverImage { extraLarge }
                        bannerImage
                        episodes
                        nextAiringEpisode { episode airingAt }
                        status
                        averageScore
                        genres
                        seasonYear
                        startDate { year }
                    }
                }
                seasonal: Page(page: 1, perPage: 20) {
                    media(type: ANIME, sort: POPULARITY_DESC, status: RELEASING) {
                        id
                        idMal
                        title { romaji english }
                        coverImage { extraLarge }
                        bannerImage
                        episodes
                        nextAiringEpisode { episode airingAt }
                        status
                        averageScore
                        genres
                        seasonYear
                        startDate { year }
                    }
                }
                topSeries: Page(page: 1, perPage: 20) {
                    media(type: ANIME, format: TV, sort: SCORE_DESC) {
                        id
                        idMal
                        title { romaji english }
                        coverImage { extraLarge }
                        bannerImage
                        episodes
                        nextAiringEpisode { episode airingAt }
                        status
                        averageScore
                        genres
                        seasonYear
                        startDate { year }
                    }
                }
                topMovies: Page(page: 1, perPage: 20) {
                    media(type: ANIME, format: MOVIE, sort: SCORE_DESC) {
                        id
                        idMal
                        title { romaji english }
                        coverImage { extraLarge }
                        bannerImage
                        episodes
                        nextAiringEpisode { episode airingAt }
                        status
                        averageScore
                        genres
                        seasonYear
                        startDate { year }
                    }
                }
                action: Page(page: 1, perPage: 20) {
                    media(type: ANIME, genre: "Action", sort: POPULARITY_DESC) {
                        id
                        idMal
                        title { romaji english }
                        coverImage { extraLarge }
                        bannerImage
                        episodes
                        nextAiringEpisode { episode airingAt }
                        status
                        averageScore
                        genres
                        seasonYear
                        startDate { year }
                    }
                }
                romance: Page(page: 1, perPage: 20) {
                    media(type: ANIME, genre: "Romance", sort: POPULARITY_DESC) {
                        id
                        idMal
                        title { romaji english }
                        coverImage { extraLarge }
                        bannerImage
                        episodes
                        nextAiringEpisode { episode airingAt }
                        status
                        averageScore
                        genres
                        seasonYear
                        startDate { year }
                    }
                }
                comedy: Page(page: 1, perPage: 20) {
                    media(type: ANIME, genre: "Comedy", sort: POPULARITY_DESC) {
                        id
                        idMal
                        title { romaji english }
                        coverImage { extraLarge }
                        bannerImage
                        episodes
                        nextAiringEpisode { episode airingAt }
                        status
                        averageScore
                        genres
                        seasonYear
                        startDate { year }
                    }
                }
                fantasy: Page(page: 1, perPage: 20) {
                    media(type: ANIME, genre: "Fantasy", sort: POPULARITY_DESC) {
                        id
                        idMal
                        title { romaji english }
                        coverImage { extraLarge }
                        bannerImage
                        episodes
                        nextAiringEpisode { episode airingAt }
                        status
                        averageScore
                        genres
                        seasonYear
                        startDate { year }
                    }
                }
                scifi: Page(page: 1, perPage: 20) {
                    media(type: ANIME, genre: "Sci-Fi", sort: POPULARITY_DESC) {
                        id
                        idMal
                        title { romaji english }
                        coverImage { extraLarge }
                        bannerImage
                        episodes
                        nextAiringEpisode { episode airingAt }
                        status
                        averageScore
                        genres
                        seasonYear
                        startDate { year }
                    }
                }
            }
        """.trimIndent()

        val rawResult = publicGraphqlRequestWithError(query, emptyMap(), useCache)
        return if (rawResult.data != null) {
            try {
                val response = json.decodeFromString<BatchedExploreResponse>(rawResult.data)
                ExploreResult(response, null)
            } catch (e: Exception) {
                ExploreResult(null, "JSON parse error: ${e.message}")
            }
        } else {
            ExploreResult(null, rawResult.error ?: "Unknown error")
        }
    }

    suspend fun publicGraphqlRequestWithError(query: String, variables: Map<String, Any?> = emptyMap(), useCache: Boolean = true): PublicGraphqlResult {
        val result = graphQLClient.execute(
            query = query,
            variables = variables,
            requiresAuth = false,
            clientIds = CLIENT_IDS,
            useCache = useCache,
            parser = { it }
        )

        return if (result.data != null) {
            PublicGraphqlResult(result.data, null)
        } else {
            PublicGraphqlResult(null, result.error?.message ?: "Unknown GraphQL error")
        }
    }

    data class PublicGraphqlResult(val data: String?, val error: String?)

    // ============================================
    // Airing Schedule
    // ============================================

    suspend fun fetchAiringSchedule(): List<AiringScheduleEntry> {
        val currentTime = System.currentTimeMillis() / 1000
        val startTime = currentTime - (24 * 60 * 60)
        val endTime = currentTime + (8 * 24 * 60 * 60)

        val query = $$"""
            query ($page: Int, $startTime: Int, $endTime: Int) {
                Page(page: $page, perPage: 50) {
                    airingSchedules(airingAt_greater: $startTime, airingAt_lesser: $endTime, sort: TIME) {
                        id
                        airingAt
                        episode
                        timeUntilAiring
                        mediaId
                        media {
                            id
                            idMal
                            title { romaji english }
                            coverImage { extraLarge }
                            episodes
                            status
                            averageScore
                            genres
                            seasonYear
                            isAdult
                        }
                    }
                }
            }
        """.trimIndent()

        val allSchedules = mutableListOf<AiringScheduleEntry>()
        var page = 1
        var hasMore = true

        while (hasMore && page <= 5) {
            val result = publicGraphqlRequestWithError(
                query,
                mapOf("page" to page, "startTime" to startTime, "endTime" to endTime)
            )

            if (result.data == null) {
                break
            }

            try {
                val data = json.decodeFromString<AiringScheduleResponse>(result.data)
                val pageSchedules = data.data.Page.airingSchedules

                if (pageSchedules.isEmpty()) {
                    hasMore = false
                } else {
                    allSchedules.addAll(pageSchedules)
                    hasMore = pageSchedules.size == 50
                    page++
                }
            } catch (_: Exception) {
                break
            }
        }

        return allSchedules
    }

    // ============================================
    // Search
    // ============================================

    suspend fun searchAnime(searchQuery: String): List<ExploreMedia> {
        if (searchQuery.isBlank()) return emptyList()

        val query = $$"""
            query ($search: String) {
                Page(page: 1, perPage: 20) {
                    media(search: $search, type: ANIME, sort: POPULARITY_DESC) {
                        id
                        idMal
                        title { romaji english }
                        coverImage { extraLarge }
                        bannerImage
                        episodes
                        nextAiringEpisode { episode airingAt }
                        status
                        averageScore
                        genres
                        seasonYear
                        isAdult
                        startDate { year }
                        format
                    }
                }
            }
        """.trimIndent()

        return publicGraphqlRequest(query, mapOf("search" to searchQuery))?.let {
            try {
                val data = json.decodeFromString<ExploreResponse>(it)
                data.data.Page.media
            } catch (_: Exception) {
                emptyList()
            }
        } ?: emptyList()
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
    ): List<ExploreMedia> {
        val varDeclarations = mutableListOf($$"$sort: [MediaSort]", $$"$page: Int",
            $$"$perPage: Int"
        )
        val varValues = mutableMapOf<String, Any?>(
            "sort" to listOf(sort),
            "page" to page,
            "perPage" to perPage
        )
        val mediaArgs = mutableListOf("type: ANIME", $$"sort: $sort")
        if (search != null) {
            varDeclarations.add(0, $$"$search: String")
            mediaArgs.add(0, $$"search: $search")
            varValues["search"] = search
        }

        fun addFilter(varName: String, varType: String, argName: String, value: Any?) {
            if (value != null) {
                varDeclarations.add($$"$$$varName: $$varType")
                mediaArgs.add($$"$$argName: $$$varName")
                varValues[varName] = value
            }
        }

        addFilter("genre_in", "[String]", "genre_in", genres)
        addFilter("tag_in", "[String]", "tag_in", tags)
        addFilter("season", "MediaSeason", "season", season)
        addFilter("seasonYear", "Int", "seasonYear", seasonYear)
        addFilter("format", "MediaFormat", "format", format)
        addFilter("status", "MediaStatus", "status", status)
        addFilter("isAdult", "Boolean", "isAdult", isAdult)

        val query = $$"""
            query ($${varDeclarations.joinToString(", ")}) {
                Page(page: $page, perPage: $perPage) {
                    media($${mediaArgs.joinToString("\n                        ")}) {
                        id
                        idMal
                        title { romaji english }
                        coverImage { extraLarge }
                        bannerImage
                        episodes
                        nextAiringEpisode { episode airingAt }
                        status
                        averageScore
                        genres
                        seasonYear
                        isAdult
                        startDate { year }
                        format
                    }
                }
            }
        """.trimIndent()

        Log.d("SearchDebug", "Query: $query")
        Log.d("SearchDebug", "Variables: $varValues")
        val response = publicGraphqlRequest(query, varValues)
        Log.d("SearchDebug", "Response: ${response?.take(500)}")
        return response?.let {
            try {
                val data = json.decodeFromString<ExploreResponse>(it)
                Log.d("SearchDebug", "Parsed ${data.data.Page.media.size} results")
                data.data.Page.media
            } catch (e: Exception) {
                Log.e("SearchDebug", "Parse error: ${e.message}")
                Log.e("SearchDebug", "Response: ${it.take(1000)}")
                emptyList()
            }
        } ?: emptyList<ExploreMedia>().also { Log.e("SearchDebug", "Response was null") }
    }

    suspend fun fetchAllTags(): List<MediaTag> {
        val response = publicGraphqlRequest(GraphqlQueries.GET_ALL_TAGS, emptyMap())
        return response?.let {
            try {
                json.decodeFromString<MediaTagCollectionResponse>(it).data.MediaTagCollection
            } catch (e: Exception) {
                Log.e("TagDebug", "Parse error: ${e.message}")
                emptyList()
            }
        } ?: emptyList()
    }
    
    suspend fun findAnimeByMalId(malId: Int): ExploreMedia? {
        val query = $$"""
            query ($malId: Int) {
                Page(page: 1, perPage: 1) {
                    media(type: ANIME, idMal: $malId) {
                        id
                        idMal
                        title { romaji english native }
                        coverImage { extraLarge }
                        bannerImage
                        episodes
                        nextAiringEpisode { episode airingAt }
                        status
                        averageScore
                        genres
                        seasonYear
                        isAdult
                        startDate { year }
                        format
                    }
                }
            }
        """.trimIndent()

        return publicGraphqlRequest(query, mapOf("malId" to malId))?.let {
            try {
                val data = json.decodeFromString<ExploreResponse>(it)
                data.data.Page.media.firstOrNull()
            } catch (_: Exception) {
                null
            }
        }
    }

    // ============================================
    // Detailed Anime
    // ============================================

    suspend fun fetchDetailedAnime(animeId: Int): DetailedAnimeMedia? {
        val query = $$"""
            query ($id: Int) {
                Media(id: $id, type: ANIME) {
                    id
                    idMal
                    title { romaji english native }
                    coverImage { extraLarge }
                    bannerImage
                    description(asHtml: false)
                    episodes
                    duration
                    status
                    averageScore
                    popularity
                    favourites
                    genres
                    tags {
                        name
                        rank
                        isMediaSpoiler
                        description
                        isAdult
                    }
                    season
                    seasonYear
                    format
                    source
                    studios(isMain: true) { nodes { id name } }
                    startDate { year month day }
                    endDate { year month day }
                    nextAiringEpisode { episode airingAt }
                    isAdult
                    characters(perPage: 10) {
                        nodes {
                            id
                            name { full }
                            image { large }
                        }
                    }
                    trailer {
                        id
                        site
                    }
                    staff(perPage: 10) {
                        edges {
                            node {
                                id
                                name { full }
                                image { large }
                            }
                            role
                        }
                    }
                    relations {
                        edges {
                            relationType
                            node {
                                id
                                title { romaji english }
                                coverImage { extraLarge }
                                episodes
                                averageScore
                                format
                                nextAiringEpisode { episode }
                            }
                        }
                    }
                }
            }
        """.trimIndent()

        return publicGraphqlRequest(query, mapOf("id" to animeId))?.let { response ->
            try {
                val data = json.decodeFromString<DetailedAnimeResponse>(response)
                data.data.Media
            } catch (_: Exception) {
                null
            }
        }
    }

    suspend fun fetchAnimeRelationsForOffset(animeId: Int): AnimeRelationsMedia? {
        val query = $$"""
            query ($id: Int!) {
                Media(id: $id, type: ANIME) {
                    id
                    title { romaji english }
                    episodes
                    format
                    nextAiringEpisode { episode }
                    relations {
                        edges {
                            relationType
                            node {
                                id
                                title { romaji english }
                                episodes
                                type
                                format
                                nextAiringEpisode { episode }
                            }
                        }
                    }
                }
            }
        """.trimIndent()

        return publicGraphqlRequest(query, mapOf("id" to animeId))?.let {
            try {
                json.decodeFromString<AnimeRelationsResponse>(it).data.Media
            } catch (_: Exception) { null }
        }
    }

    // ============================================
    // Mutations
    // ============================================

    suspend fun fetchCharacter(characterId: Int): CharacterData? {
        val query = $$"""
            query ($id: Int!) {
                Character(id: $id) {
                    id
                    name { full native }
                    image { large medium }
                    description(asHtml: false)
                    anime: media(perPage: 10, sort: POPULARITY_DESC) {
                        nodes {
                            id
                            title { romaji english }
                            coverImage { extraLarge }
                        }
                    }
                }
            }
        """.trimIndent()

        return publicGraphqlRequest(query, mapOf("id" to characterId))?.let { response ->
            try {
                val data = json.decodeFromString<CharacterResponse>(response)
                data.data.Character
            } catch (_: Exception) {
                null
            }
        }
    }

    suspend fun fetchStaff(staffId: Int): StaffData? {
        val query = $$"""
            query ($id: Int!) {
                Staff(id: $id) {
                    id
                    name { full native }
                    image { large medium }
                    description(asHtml: false)
                    anime: staffMedia(perPage: 15, sort: POPULARITY_DESC) {
                        edges {
                            node {
                                id
                                title { romaji english }
                                coverImage { extraLarge }
                            }
                            staffRole
                        }
                    }
                }
            }
        """.trimIndent()

        val response = publicGraphqlRequest(query, mapOf("id" to staffId)) ?: return null
        return try {
            val data = json.decodeFromString<StaffResponse>(response)
            data.data.Staff
        } catch (_: Exception) {
            null
        }
    }

    suspend fun fetchAllCharacters(animeId: Int): List<CharacterData>? {
        val query = $$"""
            query ($id: Int!) {
                Media(id: $id, type: ANIME) {
                    characters(perPage: 50) {
                        nodes {
                            id
                            name { full native }
                            image { large medium }
                        }
                    }
                }
            }
        """.trimIndent()

        return publicGraphqlRequest(query, mapOf("id" to animeId))?.let { response ->
            try {
                val data = json.decodeFromString<AllCharactersResponse>(response)
                val characters = data.data.Media?.characters?.nodes
                characters?.distinctBy { it.id }
            } catch (_: Exception) {
                null
            }
        }
    }

    suspend fun fetchAllStaff(animeId: Int): List<StaffData>? {
        val query = $$"""
            query ($id: Int!) {
                Media(id: $id, type: ANIME) {
                    staff(perPage: 50) {
                        nodes {
                            id
                            name { full native }
                            image { large }
                            primaryOccupations
                        }
                    }
                }
            }
        """.trimIndent()

        val response = publicGraphqlRequest(query, mapOf("id" to animeId))
        return response?.let { resp ->
            try {
                val data = json.decodeFromString<AllStaffResponse>(resp)
                val staff = data.data.Media?.staff?.nodes
                staff?.distinctBy { it.id }
            } catch (_: Exception) {
                null
            }
        }
    }

    suspend fun updateProgress(mediaId: Int, progress: Int): Boolean {
        cacheManager.invalidateUserCache()
        graphQLClient.clearCache() // Invalidate high-performance client cache too

        val query = $$"""
            mutation ($mediaId: Int, $progress: Int) {
                SaveMediaListEntry(mediaId: $mediaId, progress: $progress) {
                    id
                    progress
                }
            }
        """.trimIndent()

        return graphqlRequest(query, mapOf("mediaId" to mediaId, "progress" to progress)) != null
    }

    suspend fun updateStatus(mediaId: Int, status: String, progress: Int? = null): Boolean {
        cacheManager.invalidateUserCache()
        graphQLClient.clearCache()

        val query = $$"""
            mutation ($mediaId: Int, $status: MediaListStatus$${if (progress != null) $$", $progress: Int" else ""}) {
                SaveMediaListEntry(mediaId: $mediaId, status: $status$${if (progress != null) $$", progress: $progress" else ""}) {
                    id
                    status
                }
            }
        """.trimIndent()

        val variables = mutableMapOf<String, Any?>("mediaId" to mediaId, "status" to status)
        if (progress != null) variables["progress"] = progress

        return graphqlRequest(query, variables) != null
    }

    suspend fun deleteListEntry(entryId: Int): Boolean {
        cacheManager.invalidateUserCache()
        graphQLClient.clearCache()

        val query = $$"""
            mutation ($id: Int) {
                DeleteMediaListEntry(id: $id) {
                    deleted
                }
            }
        """.trimIndent()

        return graphqlRequest(query, mapOf("id" to entryId)) != null
    }

    suspend fun updateScore(mediaId: Int, score: Int): Boolean {
        cacheManager.invalidateUserCache()
        graphQLClient.clearCache()

        val query = $$"""
            mutation ($mediaId: Int, $score: Int) {
                SaveMediaListEntry(mediaId: $mediaId, score: $score) {
                    id
                    score
                }
            }
        """.trimIndent()

        return graphqlRequest(query, mapOf("mediaId" to mediaId, "score" to score)) != null
    }

    // ============================================
    // Stream Operations
    // ============================================

    // ============================================
    // TMDB Operations
    // ============================================

    private val tmdbBearerToken = BuildConfig.TMDB_API_KEY

    suspend fun fetchTmdbEpisodes(
        animeTitle: String,
        animeId: Int,
        animeYear: Int? = null,
        animeFormat: String? = null,
        latestAiredEpisode: Int = Int.MAX_VALUE
    ): List<TmdbEpisode> = withContext(Dispatchers.IO) {
        try {
            // Detect format from title if not provided
            val detectedFormat = animeFormat ?: detectFormatFromTitle(animeTitle)
            val baseTitle = extractBaseTitle(animeTitle)
            var searchResults = searchTmdb(baseTitle, detectedFormat)
            if (searchResults.isEmpty()) searchResults = searchTmdb(animeTitle, detectedFormat)
            // Also try searching with year if available
            if (searchResults.isEmpty() && animeYear != null) {
                searchResults = searchTmdb("$animeTitle $animeYear", detectedFormat)
            }
            if (searchResults.isEmpty()) return@withContext emptyList()

            val bestMatch = findBestMatch(searchResults, animeTitle) ?: return@withContext emptyList()
            
            // Check if this is a movie (has title field) vs TV show (has name field)
            val isMovieSearch = bestMatch.title != null
            
            if (isMovieSearch) {
                // For movies, return a single "episode" - just fetch basic info
                return@withContext listOf(TmdbEpisode(
                    episode = 1,
                    title = bestMatch.title,
                    description = bestMatch.overview ?: "",
                    image = bestMatch.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" }
                ))
            }
            
            // Continue with TV show logic
            val tvDetails = fetchTvDetails(bestMatch.id) ?: return@withContext emptyList()
            
            
            // Check if this looks like anime vs live action for Chinese titles
            val isChineseTitle = animeTitle.toCharArray().any { it.code in 0x4E00..0x9FFF || it.code in 0x3400..0x4DBF }
            val totalEpisodes = tvDetails.number_of_episodes
            
            // If it's a Chinese title with very few episodes (like 12), try to find one with more episodes
            if (isChineseTitle && totalEpisodes in 1..24 && searchResults.size > 1) {
                // Find result with highest ID (likely animation, higher ID = newer)
                val betterMatch = searchResults
                    .filter { it.id != bestMatch.id }
                    .maxByOrNull { it.id }
                if (betterMatch != null) {
                    val altTvDetails = fetchTvDetails(betterMatch.id)
                    if (altTvDetails != null && altTvDetails.number_of_episodes > totalEpisodes) {
                        // Use offset 0 since we already picked the correct entry (Season 1)
                        val betterSortedSeasons = altTvDetails.seasons.filter { it.season_number > 0 }.sortedBy { it.season_number }
                        val betterMaxEps = altTvDetails.number_of_episodes
                        val betterAllSeasonDetails = coroutineScope {
                            betterSortedSeasons.map { season ->
                                async { fetchSeason(altTvDetails.id, season.season_number) }
                            }.awaitAll().filterNotNull()
                        }
                        
                        val result = buildEpisodesFromPool(betterAllSeasonDetails, 0, latestAiredEpisode, betterMaxEps)
                        return@withContext result
                    }
                }
            }

            // Fetch all seasons in parallel to speed up and prevent timeouts
            val sortedSeasons = tvDetails.seasons.filter { it.season_number > 0 }.sortedBy { it.season_number }
            val allSeasonDetails = coroutineScope {
                sortedSeasons.map { season ->
                    async { fetchSeason(tvDetails.id, season.season_number) }
                }.awaitAll().filterNotNull()
            }

            val (episodeOffset, maxEpisodes) = calculateEpisodeOffset(tvDetails, allSeasonDetails, animeTitle, animeId, bestMatch.name, searchResults.size)

            val result = buildEpisodesFromPool(allSeasonDetails, episodeOffset, latestAiredEpisode, maxEpisodes)
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun searchTmdb(title: String, format: String? = null): List<TmdbSearchResult> {
        return try {
            val encodedTitle = URLEncoder.encode(title, "UTF-8")
            // Detect movie based on format or title patterns
            val isMovie = format == "MOVIE" || format == "OVA" || format == "ONA" || format == "SPECIAL" ||
                          title.contains("Movie", ignoreCase = true) ||
                          title.contains("OVA", ignoreCase = true) ||
                          title.contains("ONA", ignoreCase = true) ||
                          title.contains("Special", ignoreCase = true) ||
                          title.contains("Film", ignoreCase = true)
            
            val results = mutableListOf<TmdbSearchResult>()
            
            if (isMovie) {
                // Use search/movie endpoint for movies
                val movieUrl = URL("https://api.themoviedb.org/3/search/movie?query=$encodedTitle")
                val movieConnection = (movieUrl.openConnection() as HttpsURLConnection).apply {
                    readTimeout = 15000
                    connectTimeout = 15000
                    requestMethod = "GET"
                    setRequestProperty("Authorization", "Bearer $tmdbBearerToken")
                    setRequestProperty("accept", "application/json")
                }
                if (movieConnection.responseCode == 200) {
                    val response = movieConnection.inputStream.bufferedReader().readText()
                    val searchResponse = json.decodeFromString<TmdbSearchResponse>(response)
                    results.addAll(searchResponse.results)
                }
                movieConnection.disconnect()
                
                // If no movie results, try TV endpoint as fallback
                if (results.isEmpty()) {
                    val tvUrl = URL("https://api.themoviedb.org/3/search/tv?query=$encodedTitle")
                    val tvConnection = (tvUrl.openConnection() as HttpsURLConnection).apply {
                        readTimeout = 15000
                        connectTimeout = 15000
                        requestMethod = "GET"
                        setRequestProperty("Authorization", "Bearer $tmdbBearerToken")
                        setRequestProperty("accept", "application/json")
                    }
                    if (tvConnection.responseCode == 200) {
                        val response = tvConnection.inputStream.bufferedReader().readText()
                        results.addAll(json.decodeFromString<TmdbSearchResponse>(response).results)
                    }
                    tvConnection.disconnect()
                }
            } else {
                // Use search/tv endpoint for TV series - this searches by title properly
                val tvUrl = URL("https://api.themoviedb.org/3/search/tv?query=$encodedTitle")
                val tvConnection = (tvUrl.openConnection() as HttpsURLConnection).apply {
                    readTimeout = 15000
                    connectTimeout = 15000
                    requestMethod = "GET"
                    setRequestProperty("Authorization", "Bearer $tmdbBearerToken")
                    setRequestProperty("accept", "application/json")
                }
                if (tvConnection.responseCode == 200) {
                    val response = tvConnection.inputStream.bufferedReader().readText()
                    results.addAll(json.decodeFromString<TmdbSearchResponse>(response).results)
                }
                tvConnection.disconnect()
                
                // If no TV results, try movie endpoint as fallback
                if (results.isEmpty()) {
                    val movieUrl = URL("https://api.themoviedb.org/3/search/movie?query=$encodedTitle")
                    val movieConnection = (movieUrl.openConnection() as HttpsURLConnection).apply {
                        readTimeout = 15000
                        connectTimeout = 15000
                        requestMethod = "GET"
                        setRequestProperty("Authorization", "Bearer $tmdbBearerToken")
                        setRequestProperty("accept", "application/json")
                    }
                    if (movieConnection.responseCode == 200) {
                        val response = movieConnection.inputStream.bufferedReader().readText()
                        results.addAll(json.decodeFromString<TmdbSearchResponse>(response).results)
                    }
                    movieConnection.disconnect()
                }
            }
            
            results
        } catch (_: Exception) {
            emptyList() 
        }
    }

    private fun fetchTvDetails(tmdbId: Int): TmdbTvDetails? {
        return try {
            val url = URL("https://api.themoviedb.org/3/tv/$tmdbId?language=en-US")
            val connection = (url.openConnection() as HttpsURLConnection).apply {
                readTimeout = 15000
                connectTimeout = 15000
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $tmdbBearerToken")
            }

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                json.decodeFromString<TmdbTvDetails>(response)
            } else null
        } catch (_: Exception) { null }
    }

    private suspend fun fetchSeason(tvId: Int, seasonNumber: Int): TmdbSeasonDetails? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.themoviedb.org/3/tv/$tvId/season/$seasonNumber?language=en-US")
            val connection = (url.openConnection() as HttpsURLConnection).apply {
                readTimeout = 15000
                connectTimeout = 15000
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $tmdbBearerToken")
            }

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                json.decodeFromString<TmdbSeasonDetails>(response)
            } else null
        } catch (_: Exception) { null }
    }

    private fun buildEpisodesFromPool(
        allSeasonDetails: List<TmdbSeasonDetails>,
        episodeOffset: Int,
        latestAiredEpisode: Int,
        maxEpisodes: Int
    ): List<TmdbEpisode> {
        val allEpisodes = mutableListOf<TmdbEpisode>()
        var absoluteIndex = 1

        // First, collect all episodes from TMDB
        data class EpisodeData(val relativeNum: Int, val title: String?, val description: String?, val image: String?)
        val tmdbEpisodes = mutableListOf<EpisodeData>()
        
        for (season in allSeasonDetails) {
            for (episode in season.episodes) {
                val isTarget = if (maxEpisodes > 0) {
                    absoluteIndex > episodeOffset && absoluteIndex <= (episodeOffset + maxEpisodes)
                } else {
                    absoluteIndex > episodeOffset
                }

                if (isTarget) {
                    val relativeNum = absoluteIndex - episodeOffset
                    val title = if (episode.name != null && !episode.name.startsWith("Episode", ignoreCase = true)) {
                        episode.name
                    } else null
                    val image = episode.still_path?.let { "https://image.tmdb.org/t/p/w500$it" }
                    
                    tmdbEpisodes.add(EpisodeData(relativeNum, title, episode.overview, image))
                }
                absoluteIndex++
            }
        }
        
        // Calculate how many episodes TMDB returned
        val tmdbEpisodeCount = tmdbEpisodes.size
        val expectedEpisodeCount = if (maxEpisodes > 0) maxEpisodes else tmdbEpisodeCount
        
        // Add TMDB episodes with proper airing status
        for ((relativeNum, title, description, image) in tmdbEpisodes) {
            val hasAired = latestAiredEpisode == Int.MAX_VALUE || relativeNum <= latestAiredEpisode
            
            allEpisodes.add(TmdbEpisode(
                episode = relativeNum,
                title = title ?: "Episode $relativeNum",
                description = if (hasAired) (description ?: "") else "",
                image = image
            ))
        }
        
        // If TMDB doesn't have enough episodes, generate placeholders for long-running series
        if (tmdbEpisodeCount < expectedEpisodeCount) {
            val startEpisode = tmdbEpisodeCount + 1

            for (epNum in startEpisode..expectedEpisodeCount) {
                val hasAired = latestAiredEpisode == Int.MAX_VALUE || epNum <= latestAiredEpisode
                
                allEpisodes.add(TmdbEpisode(
                    episode = epNum,
                    title = "Episode $epNum",
                    description = if (hasAired) "" else "Not yet aired",
                    image = null
                ))
            }
        }
        
        return allEpisodes
    }

    private suspend fun calculateEpisodeOffset(
        tvDetails: TmdbTvDetails,
        allSeasonDetails: List<TmdbSeasonDetails>,
        animeTitle: String,
        animeId: Int,
        tmdbName: String?,
        tmdbResultsCount: Int = 1
    ): Pair<Int, Int> {
        
        // Always fetch AniList episode count first - it's the most reliable for anime
        val recursiveOffset = calculateRecursiveOffset(animeId)
        val aniListMedia = fetchAnimeRelationsForOffset(animeId)
        val totalEps = aniListMedia?.episodes ?: 0
        
        // If AniList has episode count, use it (more reliable for long-running anime like Detective Conan)
        if (totalEps > 0) {
            return Pair(recursiveOffset, totalEps)
        }
        
        // Fallback to TMDB only if AniList doesn't have episode count
        // If TMDB name exactly matches the original title, skip Aniwatch fallback and use offset 0
        val normalizedOriginal = normalizeTitle(animeTitle)
        val normalizedTmdbName = normalizeTitle(tmdbName ?: "")
        if (normalizedTmdbName == normalizedOriginal) {
            return Pair(0, tvDetails.number_of_episodes)
        }
        
        // If there were multiple TMDB results, assume the best match (highest ID) is correct
        if (tmdbResultsCount > 1) {
            return Pair(0, tvDetails.number_of_episodes)
        }

        // Title matching via Aniwatch first episode title
        val (aniwatchOffset, hianimeCount) = fetchEpisodeOffsetFromAniwatch(animeTitle, allSeasonDetails)
        if (aniwatchOffset >= 0) {
            return Pair(aniwatchOffset, if (hianimeCount > 0) hianimeCount else tvDetails.number_of_episodes)
        }

        return Pair(0, tvDetails.number_of_episodes)
    }

    private val visitedOffsetIds = mutableSetOf<Int>()

    private suspend fun calculateRecursiveOffset(animeId: Int): Int {
        visitedOffsetIds.clear()
        val offset = getPrequelEpisodesSum(animeId)
        return offset
    }

    private suspend fun getPrequelEpisodesSum(animeId: Int): Int {
        if (visitedOffsetIds.contains(animeId)) return 0
        visitedOffsetIds.add(animeId)

        val media = fetchAnimeRelationsForOffset(animeId) ?: return 0

        // Find ALL PREQUEL relations.
        val prequels = media.relations?.edges?.filter {
            it.relationType == "PREQUEL" && it.node.type == "ANIME"
        } ?: emptyList()

        var totalOffset = 0
        for (edge in prequels) {
            val node = edge.node
            // Only add episodes for Series formats (TV, ONA, TV_SHORT)
            // But ALWAYS recurse, even into Movies/Specials, to find older seasons
            val isSeriesFormat = node.format == "TV" || node.format == "ONA" || node.format == "TV_SHORT"
            val episodes = if (isSeriesFormat) (node.episodes ?: 0) else 0

            totalOffset += episodes + getPrequelEpisodesSum(node.id)
        }

        return totalOffset
    }

    suspend fun fetchAnimeRelationsList(animeId: Int): List<AnimeRelation>? {
        val query = GraphqlQueries.GET_ANIME_RELATIONS

        return publicGraphqlRequest(query, mapOf("id" to animeId))?.let {
            try {
                val data = json.decodeFromString<AnimeRelationsResponse>(it)
                data.data.Media.relations?.edges?.map { edge ->
                    edge.node.let { node ->
                        AnimeRelation(
                            id = node.id,
                            title = node.title?.english ?: node.title?.romaji ?: "Unknown",
                            cover = node.coverImage?.extraLarge ?: "",
                            episodes = node.episodes,
                            averageScore = node.averageScore,
                            format = node.format,
                            relationType = edge.relationType ?: "UNKNOWN"
                        )
                    }
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    private suspend fun fetchEpisodeOffsetFromAniwatch(
        animeTitle: String,
        allSeasonDetails: List<TmdbSeasonDetails>
    ): Pair<Int, Int> {
        return withContext(Dispatchers.IO) {
            try {
                val encodedTitle = URLEncoder.encode(animeTitle, "UTF-8")
                val url = URL("https://aniwatch-cxjn.vercel.app/api/v2/hianime/search?q=$encodedTitle&page=1")
                val connection = (url.openConnection() as HttpsURLConnection).apply {
                    readTimeout = 15000
                    connectTimeout = 15000
                }
                if (connection.responseCode != 200) return@withContext Pair(-1, 0)

                val response = connection.inputStream.bufferedReader().readText()
                val searchJson = json.parseToJsonElement(response)
                val animes = searchJson.jsonObject["data"]?.jsonObject?.get("animes")?.jsonArray ?: return@withContext Pair(-1, 0)

                val bestMatch = animes.firstOrNull()?.jsonObject ?: return@withContext Pair(-1, 0)
                val aniwatchId = bestMatch["id"]?.jsonPrimitive?.content ?: return@withContext Pair(-1, 0)

                val episodesUrl = URL("https://aniwatch-cxjn.vercel.app/api/v2/hianime/anime/$aniwatchId/episodes")
                val epConnection = (episodesUrl.openConnection() as HttpsURLConnection).apply {
                    readTimeout = 15000
                    connectTimeout = 15000
                }
                if (epConnection.responseCode != 200) return@withContext Pair(-1, 0)

                val epResponse = epConnection.inputStream.bufferedReader().readText()
                val epJson = json.parseToJsonElement(epResponse).jsonObject["data"]?.jsonObject
                val totalEps = epJson?.get("totalEpisodes")?.jsonPrimitive?.int ?: 0
                val firstEpTitle = epJson?.get("episodes")?.jsonArray?.firstOrNull()?.jsonObject?.get("title")?.jsonPrimitive?.content ?: return@withContext Pair(-1, 0)

                Pair(findTmdbEpisodeOffsetByTitle(allSeasonDetails, firstEpTitle), totalEps)
            } catch (_: Exception) { Pair(-1, 0) }
        }
    }

    private fun findTmdbEpisodeOffsetByTitle(allSeasonDetails: List<TmdbSeasonDetails>, targetTitle: String): Int {
        val normalizedTarget = normalizeTitle(targetTitle)
        if (normalizedTarget.startsWith("episode") && normalizedTarget.length < 12) return -1

        var absoluteIndex = 0
        for (season in allSeasonDetails) {
            for (episode in season.episodes) {
                val normalizedEpisode = normalizeTitle(episode.name ?: "")
                if (normalizedTarget == normalizedEpisode || (normalizedEpisode.isNotEmpty() && normalizedTarget.contains(normalizedEpisode))) {
                    return absoluteIndex
                }
                absoluteIndex++
            }
        }
        return -1
    }

    private fun findBestMatch(results: List<TmdbSearchResult>, originalTitle: String): TmdbSearchResult? {
        val normalizedOriginal = normalizeTitle(originalTitle)
        
        // Check if original title might be Chinese (contains CJK characters)
        val isChineseTitle = originalTitle.toCharArray().any { it.code in 0x4E00..0x9FFF || it.code in 0x3400..0x4DBF }
        
        // First, try a quick match without fetching genres
        val quickMatch = results.maxByOrNull { result ->
            val name = result.name ?: result.title ?: result.original_name ?: ""
            val normalizedName = normalizeTitle(name)
            var score = 0
            
            // Skip invalid results
            if (name.isEmpty() || name.length < 3) {
                return@maxByOrNull -1000
            }
            
            // Skip Western cartoons for anime searches
            val lowerName = name.lowercase()
            if (lowerName == "family guy" || lowerName == "the simpsons" || lowerName == "american dad") {
                return@maxByOrNull -500
            }
            
            // Exact match - highest priority
            if (normalizedName == normalizedOriginal) score += 500
            
            // When names are equal, prefer higher ID
            if (normalizedName == normalizedOriginal) {
                score += result.id / 1000
            }
            
            // Partial match
            if (normalizedName.length > 2 && normalizedOriginal.length > 2) {
                if (normalizedOriginal in normalizedName || normalizedName in normalizedOriginal) {
                    score += 100
                }
            }
            
            score
        }
        
        // If we have only one result, use it
        if (results.size == 1) {
            return quickMatch
        }
        
        // Check if there are multiple exact matches (need to differentiate by genre)
        val exactMatches = results.filter { result ->
            val name = result.name ?: result.title ?: ""
            normalizeTitle(name) == normalizedOriginal
        }
        
        // If there's exactly one exact match, use it
        if (exactMatches.size == 1) {
            return exactMatches.first()
        }
        
        // If there are multiple exact matches (like Bartender anime vs live action),
        // or no exact match at all, fetch genres to differentiate
        if (results.size > 1) {
            // Fetch details for each result to check genres - do this in parallel
            val resultsWithGenres = results.mapNotNull { result ->
                val details = fetchTvDetails(result.id)
                if (details != null) {
                    result to details
                } else null
            }
            
            // Check if any result has Animation genre
            val animationResults = resultsWithGenres.filter { (_, details) ->
                details.genres.any { it.name == "Animation" }
            }
            
            if (animationResults.isNotEmpty()) {
                // Prefer animation result that matches the original title best
                return animationResults.maxByOrNull { (result, _) ->
                    val name = result.name ?: result.title ?: ""
                    val normalizedName = normalizeTitle(name)
                    var score = 0
                    if (normalizedName == normalizedOriginal) score += 500
                    score += result.id / 1000
                    score
                }?.first
            }
            
            // For Chinese titles, also try higher ID as fallback
            if (isChineseTitle) {
                return results.maxByOrNull { it.id }
            }
        }
        
        return quickMatch
    }

    private fun normalizeTitle(title: String): String = title.lowercase().replace(Regex("[^a-z0-9\\s]"), "").replace(Regex("\\s+"), " ").trim()

    private fun detectFormatFromTitle(title: String): String? {
        val lowerTitle = title.lowercase()
        return when {
            lowerTitle.contains("movie") || lowerTitle.contains("film") -> "MOVIE"
            lowerTitle.contains("ova") -> "OVA"
            lowerTitle.contains("ona") -> "ONA"
            lowerTitle.contains("special") -> "SPECIAL"
            lowerTitle.contains("season") -> "TV"
            else -> null
        }
    }

    private fun extractBaseTitle(title: String): String {
        var baseTitle = title
        val suffixesToRemove = listOf(
            Regex("""\s+\d+(?:st|nd|rd|th)\s*[Ss]eason.*$"""),
            Regex("""\s+[Ss]eason\s*\d+.*$"""),
            Regex("""\s+[Pp]art\s*\d+.*$"""),
            Regex("""\s+II+$"""),
            Regex("""\s+\d+$""")
        )
        for (pattern in suffixesToRemove) baseTitle = baseTitle.replace(pattern, "")
        return baseTitle.replace(Regex("""[\s:－-]+$"""), "").trim()
    }

    suspend fun fetchUserActivity(userId: Int, perPage: Int = 50): List<UserActivity>? {
        val query = $$"""
            query ($userId: Int) {
                Page(page: 1, perPage: $$perPage) {
                    activities(userId: $userId, type: ANIME_LIST, sort: ID_DESC) {
                        ... on ListActivity {
                            createdAt
                            status
                            progress
                            media {
                                id
                                title { romaji english }
                                coverImage { extraLarge }
                            }
                        }
                    }
                }
            }
        """.trimIndent()

        return graphqlRequest(query, mapOf("userId" to userId))?.let {
            try {
                val data = json.decodeFromString<SimpleActivityResponse>(it)
                data.data.Page.activities.mapIndexedNotNull { index, activity ->
                    if (activity.media != null) {
                        UserActivity(
                            id = index,
                            type = "ANIME_LIST",
                            status = activity.status ?: "",
                            progress = activity.progress,
                            createdAt = activity.createdAt,
                            mediaId = activity.media.id,
                            mediaTitle = activity.media.title.romaji ?: activity.media.title.english
                            ?: "Unknown",
                            mediaTitleEnglish = activity.media.title.english,
                            mediaCover = activity.media.coverImage?.extraLarge ?: "",
                            episodes = null,
                            averageScore = null,
                            year = null
                        )
                    } else null
                }
            } catch (_: Exception) { null }
        }
    }

    suspend fun fetchUserFavorites(userId: Int): UserFavoritesResponse? {
        val query = $$"""
            query ($userId: Int) {
                User(id: $userId) {
                    favourites {
                        anime(page: 1, perPage: 30) {
                            nodes {
                                id
                                title { romaji english }
                                coverImage { extraLarge }
                                episodes
                                averageScore
                                genres
                                seasonYear
                                format
                                status
                            }
                        }
                    }
                }
            }
        """.trimIndent()

        return graphqlRequest(query, mapOf("userId" to userId))?.let {
            try {
                val response = json.decodeFromString<UserFavoritesResponse>(it)
                response
            } catch (_: Exception) {
                null
            }
        }
    }

    suspend fun toggleAniListFavorite(mediaId: Int): Boolean {
        graphQLClient.clearCache() // Clear cache to ensure fresh data
        
        val mutation = $$"""
            mutation ($mediaId: Int) {
                ToggleFavourite(animeId: $mediaId) {
                    anime { nodes { id } }
                }
            }
        """.trimIndent()

        val response = graphqlMutation(mutation, mapOf("mediaId" to mediaId))
        return !response.isNullOrEmpty()
    }
    
    suspend fun addAniListFavorite(mediaId: Int): Boolean {
        val mutation = $$"""
            mutation ($mediaId: Int) {
                ToggleFavourite(animeId: $mediaId) {
                    anime { nodes { id } }
                }
            }
        """.trimIndent()
        
        // Check if already favorited first
        val checkQuery = $$"""
            query ($mediaId: Int) {
                Media(id: $mediaId) {
                    id
                    isFavourite
                }
            }
        """.trimIndent()
        
        val result = graphqlRequest(checkQuery, mapOf("mediaId" to mediaId))
        if (result?.contains("\"isFavourite\":true") == true || result?.contains("\"isFavourite\": true") == true) {
            return true // Already favorited
        }
        
        val success = graphqlMutation(mutation, mapOf("mediaId" to mediaId)) != null
        return success
    }
    
    suspend fun removeAniListFavorite(mediaId: Int): Boolean {
        val mutation = $$"""
            mutation ($mediaId: Int) {
                ToggleFavourite(animeId: $mediaId) {
                    anime { nodes { id } }
                }
            }
        """.trimIndent()
        
        // Check if not favorited first
        val checkQuery = $$"""
            query ($mediaId: Int) {
                Media(id: $mediaId) {
                    id
                    isFavourite
                }
            }
        """.trimIndent()
        
        val result = graphqlRequest(checkQuery, mapOf("mediaId" to mediaId))
        if (result?.contains("\"isFavourite\":false") == true || result?.contains("\"isFavourite\": false") == true) {
            return true // Already not favorited
        }
        
        val success = graphqlMutation(mutation, mapOf("mediaId" to mediaId)) != null
        return success
    }
    
    suspend fun toggleAniListFavorite(mediaId: Int, addFavorite: Boolean): Boolean {
        if (addFavorite) {
            // Check if already favorited
            val checkQuery = $$"""
                query ($mediaId: Int) {
                    Media(id: $mediaId) {
                        id
                        isFavourite
                    }
                }
            """.trimIndent()
            
            val result = graphqlRequest(checkQuery, mapOf("mediaId" to mediaId))
            
            if (result?.contains("\"isFavourite\":true") == true || result?.contains("\"isFavourite\": true") == true) {
                return true // Already favorited
            }
        }
        
        val mutation = $$"""
            mutation ($mediaId: Int) {
                ToggleFavourite(animeId: $mediaId) {
                    anime { nodes { id } }
                }
            }
        """.trimIndent()
        
        val success = graphqlMutation(mutation, mapOf("mediaId" to mediaId)) != null
        return success
    }
}


