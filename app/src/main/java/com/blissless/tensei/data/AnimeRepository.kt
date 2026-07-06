package com.blissless.tensei.data

import android.util.Log
import com.blissless.tensei.BuildConfig
import com.blissless.tensei.data.models.AiringScheduleEntry
import com.blissless.tensei.data.models.AiringScheduleResponse
import com.blissless.tensei.data.models.AllCharactersResponse
import com.blissless.tensei.data.models.AllStaffResponse
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
import com.blissless.tensei.data.models.UserActivity
import com.blissless.tensei.data.models.UserFavoritesResponse
import com.blissless.tensei.data.models.UserStatsResponse
import com.blissless.tensei.data.models.ViewerResponse
import com.blissless.tensei.network.GraphQLClient
import com.blissless.tensei.network.GraphQLConfig
import kotlinx.serialization.json.Json
import com.blissless.tensei.util.ErrorHandler

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

    internal val json = Json {
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
            } catch (e: Exception) { ErrorHandler.report("AnimeRepository", "operation failed, returning null", e); null }
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
                staff
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

    // TMDB episode fetching logic — implementations live in AnimeRepositoryTmdbExt.kt
    // (suspend fun fetchTmdbEpisodes, searchTmdb, fetchTvDetails, fetchSeason,
    //  buildEpisodesFromPool, calculateEpisodeOffset, calculateRecursiveOffset,
    //  getPrequelEpisodesSum, fetchEpisodeOffsetFromAniwatch,
    //  findTmdbEpisodeOffsetByTitle, wordOverlapScore, findBestMatch,
    //  normalizeTitle, detectFormatFromTitle, extractBaseTitle)
    internal val tmdbBearerToken = BuildConfig.TMDB_API_KEY
    internal val visitedOffsetIds = mutableSetOf<Int>()


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
            } catch (e: Exception) { ErrorHandler.report("AnimeRepository", "operation failed, returning null", e); null }
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


