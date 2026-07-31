package com.blissless.tensei.data.manga

import com.blissless.tensei.data.models.MangaDetail
import com.blissless.tensei.data.models.MangaDetailMedia
import com.blissless.tensei.data.models.MangaDetailResponse
import com.blissless.tensei.data.models.MangaExploreMedia
import com.blissless.tensei.data.models.MangaExploreResponse
import com.blissless.tensei.data.models.MangaMedia
import com.blissless.tensei.data.models.MangaMediaListEntry
import com.blissless.tensei.data.models.MangaRelation
import com.blissless.tensei.data.models.MangaUserListResponse
import com.blissless.tensei.data.models.MangaUserProfileResponse
import com.blissless.tensei.data.models.MangaDetailCharacters
import com.blissless.tensei.data.models.MangaStaff
import com.blissless.tensei.data.models.MangaStaffEdge
import com.blissless.tensei.data.models.MangaCharacterNode
import com.blissless.tensei.data.models.MangaCharacterName
import com.blissless.tensei.data.models.MangaCharacters
import com.blissless.tensei.BuildConfig
import com.blissless.tensei.network.GraphQLClient
import com.blissless.tensei.network.GraphQLConfig
import com.blissless.tensei.util.ErrorHandler
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class MangaRepository {

    private val graphQLClient = GraphQLClient(
        config = GraphQLConfig(
            maxConcurrentRequests = 5,
            minRequestIntervalMs = 100L,
            cacheDurationMs = 60 * 60 * 1000L,
            userDataCacheDurationMs = 60 * 60 * 1000L
        )
    )

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    companion object {
        private val CLIENT_IDS = listOf(BuildConfig.CLIENT_ID_ANILIST)
        private const val TAG = "MangaRepository"
    }

    private suspend fun executeQuery(query: String, variables: Map<String, Any?> = emptyMap(), token: String? = null): String? {
        val result = graphQLClient.execute(
            query = query,
            variables = variables,
            requiresAuth = token != null,
            authToken = token,
            clientIds = CLIENT_IDS,
            useCache = true,
            parser = { it }
        )
        return result.data
    }

    suspend fun searchManga(search: String, page: Int = 1, perPage: Int = 30): List<MangaExploreMedia> {
        val query = """
            query (${'$'}search: String, ${'$'}page: Int, ${'$'}perPage: Int) {
                Page(page: ${'$'}page, perPage: ${'$'}perPage) {
                    media(search: ${'$'}search, type: MANGA, isAdult: false) {
                        id idMal title { romaji english }
                        coverImage { extraLarge large }
                        bannerImage chapters volumes status averageScore genres
                        seasonYear startDate { year month day } isAdult format
                    }
                }
            }
        """.trimIndent()
        val vars = mapOf("search" to search, "page" to page, "perPage" to perPage)
        val raw = executeQuery(query, vars) ?: return emptyList()
        return try {
            json.decodeFromString<MangaExploreResponse>(raw).data.Page.media
        } catch (e: Exception) {
            ErrorHandler.ignore(TAG, "search parse failed", e); emptyList()
        }
    }

    suspend fun fetchExploreSections(): Map<String, List<MangaExploreMedia>> {
        val query = """
            query {
                popular: Page(page: 1, perPage: 12) { media(sort: POPULARITY_DESC, type: MANGA, isAdult: false) { id idMal title { romaji english } coverImage { extraLarge large } bannerImage chapters volumes status averageScore genres seasonYear startDate { year month day } isAdult format } }
                topRated: Page(page: 1, perPage: 12) { media(sort: SCORE_DESC, type: MANGA, isAdult: false) { id idMal title { romaji english } coverImage { extraLarge large } bannerImage chapters volumes status averageScore genres seasonYear startDate { year month day } isAdult format } }
                favourites: Page(page: 1, perPage: 12) { media(sort: FAVOURITES_DESC, type: MANGA, isAdult: false) { id idMal title { romaji english } coverImage { extraLarge large } bannerImage chapters volumes status averageScore genres seasonYear startDate { year month day } isAdult format } }
                action: Page(page: 1, perPage: 12) { media(genre: "Action", sort: POPULARITY_DESC, type: MANGA, isAdult: false) { id idMal title { romaji english } coverImage { extraLarge large } bannerImage chapters volumes status averageScore genres seasonYear startDate { year month day } isAdult format } }
                romance: Page(page: 1, perPage: 12) { media(genre: "Romance", sort: POPULARITY_DESC, type: MANGA, isAdult: false) { id idMal title { romaji english } coverImage { extraLarge large } bannerImage chapters volumes status averageScore genres seasonYear startDate { year month day } isAdult format } }
                fantasy: Page(page: 1, perPage: 12) { media(genre: "Fantasy", sort: POPULARITY_DESC, type: MANGA, isAdult: false) { id idMal title { romaji english } coverImage { extraLarge large } bannerImage chapters volumes status averageScore genres seasonYear startDate { year month day } isAdult format } }
            }
        """.trimIndent()
        val raw = executeQuery(query) ?: return emptyMap()
        return try {
            val root = json.parseToJsonElement(raw).jsonObject
            val data = root["data"]?.jsonObject ?: return emptyMap()
            val sections = mutableMapOf<String, List<MangaExploreMedia>>()
            for ((key, value) in data) {
                val page = value.jsonObject["Page"]?.jsonObject ?: continue
                val mediaArray = page["media"]?.jsonArray ?: continue
                sections[key] = json.decodeFromJsonElement<List<MangaExploreMedia>>(mediaArray)
            }
            sections
        } catch (e: Exception) {
            ErrorHandler.ignore(TAG, "explore parse failed", e)
            emptyMap()
        }
    }

    suspend fun fetchMangaDetail(mangaId: Int): MangaDetail? {
        val query = """
            query (${'$'}id: Int) {
                Media(id: ${'$'}id, type: MANGA) {
                    id idMal title { romaji english native }
                    coverImage { extraLarge large } bannerImage description
                    chapters volumes status averageScore meanScore popularity favourites
                    genres tags { name rank isMediaSpoiler description isAdult }
                    seasonYear format source isAdult
                    relations { edges { relationType node { id title { romaji english } coverImage { extraLarge } chapters averageScore format } } }
                    characters { nodes { id name { full native } image { large } } }
                    staff { edges { node { id name { full native } image { large } } role } }
                    recommendations { nodes { mediaRecommendation { id idMal title { romaji english } coverImage { extraLarge } chapters volumes averageScore format } } }
                    rankings { id rank type context primary }
                    synonyms externalLinks { url site }
                }
            }
        """.trimIndent()
        val raw = executeQuery(query, mapOf("id" to mangaId)) ?: return null
        return try {
            val wrapper = json.decodeFromString<MangaDetailResponse>(raw)
            mapMangaDetail(wrapper.data.Media)
        } catch (e: Exception) {
            ErrorHandler.ignore(TAG, "detail parse failed", e); null
        }
    }

    private fun mapMangaDetail(media: MangaDetailMedia): MangaDetail {
        return MangaDetail(
            id = media.id,
            malId = media.idMal,
            title = media.title?.romaji ?: media.title?.english ?: "Unknown",
            titleEnglish = media.title?.english,
            titleNative = media.title?.native,
            cover = media.coverImage?.extraLarge ?: media.coverImage?.large ?: "",
            banner = media.bannerImage,
            description = media.description,
            chapters = media.chapters ?: 0,
            volumes = media.volumes,
            status = media.status,
            averageScore = media.averageScore,
            meanScore = media.meanScore,
            popularity = media.popularity,
            favourites = media.favourites,
            genres = media.genres ?: emptyList(),
            tags = media.tags ?: emptyList(),
            year = media.seasonYear,
            format = media.format,
            source = media.source,
            isAdult = media.isAdult,
            staff = media.staff?.let { MangaStaff(it.edges.map { e ->
                MangaStaffEdge(
                    node = e.node?.let { n -> com.blissless.tensei.data.models.MangaStaffNode(n.id, n.name?.let { MangaCharacterName(it.full, it.native) }, n.image) },
                    role = e.role
                )
            })},
            recommendations = media.recommendations?.nodes?.mapNotNull { r ->
                r.mediaRecommendation?.let { m ->
                    MangaMedia(
                        id = m.id, title = m.title?.romaji ?: m.title?.english ?: "",
                        titleEnglish = m.title?.english, cover = m.coverImage?.extraLarge ?: "",
                        totalChapters = m.chapters ?: 0, averageScore = m.averageScore
                    )
                }
            } ?: emptyList(),
            characters = media.characters?.let { MangaCharacters(it.nodes.map { n ->
                MangaCharacterNode(n.id, n.name?.let { MangaCharacterName(it.full, it.native) }, n.image)
            })},
            relations = media.relations?.edges?.map { e ->
                MangaRelation(
                    id = e.node.id,
                    title = e.node.title?.english ?: e.node.title?.romaji ?: "Unknown",
                    cover = e.node.coverImage?.extraLarge ?: "",
                    chapters = e.node.chapters,
                    averageScore = e.node.averageScore,
                    format = e.node.format,
                    relationType = e.relationType ?: "UNKNOWN"
                )
            } ?: emptyList(),
            synonymTitles = media.synonyms ?: emptyList(),
            rankings = media.rankings ?: emptyList(),
            externalLinks = media.externalLinks ?: emptyList()
        )
    }

    suspend fun fetchUserMangaLists(userId: Int, token: String): Map<String, List<MangaMedia>>? {
        val query = """
            query (${'$'}userId: Int) {
                MediaListCollection(userId: ${'$'}userId, type: MANGA) {
                    lists { name status entries { id mediaId progress progressVolumes score status media { id idMal title { romaji english } coverImage { extraLarge large } bannerImage chapters volumes status averageScore genres seasonYear format } } }
                }
            }
        """.trimIndent()
        val raw = executeQuery(query, mapOf("userId" to userId), token) ?: return null
        return try {
            val response = json.decodeFromString<MangaUserListResponse>(raw)
            response.data.MediaListCollection.lists.associate { list ->
                (list.status ?: list.name) to list.entries.map { entry -> mapMangaMedia(entry) }
            }
        } catch (e: Exception) {
            ErrorHandler.ignore(TAG, "user lists parse failed", e); null
        }
    }

    private fun mapMangaMedia(entry: MangaMediaListEntry): MangaMedia {
        val media = entry.media
        return MangaMedia(
            id = media.id,
            title = media.title.romaji ?: media.title.english ?: "Unknown",
            titleEnglish = media.title.english,
            cover = media.coverImage?.extraLarge ?: media.coverImage?.large ?: "",
            banner = media.bannerImage,
            progress = entry.progress ?: 0,
            totalChapters = media.chapters ?: 0,
            totalVolumes = media.volumes,
            status = media.status ?: "",
            averageScore = media.averageScore,
            genres = media.genres ?: emptyList(),
            listStatus = entry.status ?: "",
            listEntryId = entry.id,
            year = media.seasonYear,
            malId = media.idMal,
            format = media.format
        )
    }

    suspend fun fetchMangaUserProfile(token: String): com.blissless.tensei.data.models.MangaUserProfile? {
        val query = """
            query {
                Viewer { id name avatar { medium large } bannerImage siteUrl createdAt statistics { manga { count chaptersRead volumesRead meanScore } } }
            }
        """.trimIndent()
        val raw = executeQuery(query, token = token) ?: return null
        return try {
            json.decodeFromString<MangaUserProfileResponse>(raw).data.User
        } catch (e: Exception) {
            ErrorHandler.ignore(TAG, "user profile parse failed", e); null
        }
    }

    suspend fun updateMangaStatus(mediaId: Int, status: String, token: String, progress: Int? = null, progressVolumes: Int? = null, score: Int? = null): Boolean {
        val query = """
            mutation (${'$'}mediaId: Int, ${'$'}status: MediaListStatus, ${'$'}progress: Int, ${'$'}progressVolumes: Int, ${'$'}score: Int) {
                SaveMediaListEntry(mediaId: ${'$'}mediaId, status: ${'$'}status, progress: ${'$'}progress, progressVolumes: ${'$'}progressVolumes, score: ${'$'}score) { id }
            }
        """.trimIndent()
        val vars = mutableMapOf<String, Any?>("mediaId" to mediaId, "status" to status)
        if (progress != null) vars["progress"] = progress
        if (progressVolumes != null) vars["progressVolumes"] = progressVolumes
        if (score != null) vars["score"] = score
        return executeQuery(query, vars, token) != null
    }

    suspend fun deleteMangaListEntry(entryId: Int, token: String): Boolean {
        val query = """
            mutation (${'$'}id: Int) { DeleteMediaListEntry(id: ${'$'}id) { deleted } }
        """.trimIndent()
        return executeQuery(query, mapOf("id" to entryId), token) != null
    }

    suspend fun toggleMangaFavorite(mediaId: Int, token: String): Boolean {
        val query = """
            mutation (${'$'}mediaId: Int) { ToggleFavourite(mediaId: ${'$'}mediaId) { media { favourites } } }
        """.trimIndent()
        return executeQuery(query, mapOf("mediaId" to mediaId), token) != null
    }
}
