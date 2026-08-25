package com.blissless.tensei.data.models

import kotlinx.serialization.Serializable

private val ADULT_GENRES = setOf("hentai", "nudity")

fun isAdultContent(isAdult: Boolean, genres: List<String>): Boolean =
    isAdult || genres.any { it.lowercase() in ADULT_GENRES }

// ============================================
// CORE ANIME MODELS
// ============================================

@Serializable
data class ExploreAnime(
    val id: Int,
    val title: String,
    val titleEnglish: String? = null,
    val cover: String,
    val banner: String?,
    val episodes: Int,
    val latestEpisode: Int?,
    val averageScore: Int?,
    val genres: List<String>,
    val year: Int? = null,
    val malId: Int? = null,
    val format: String? = null,
    val isAdult: Boolean = false,
    val status: String? = null
)

@Serializable
data class AnimeMedia(
    val id: Int,
    val title: String,
    val titleEnglish: String? = null,
    val cover: String,
    val banner: String? = null,
    val progress: Int = 0,
    val totalEpisodes: Int = 0,
    val latestEpisode: Int? = null,
    val status: String = "",
    val averageScore: Int? = null,
    val genres: List<String> = emptyList(),
    val listStatus: String = "",
    val listEntryId: Int? = null,
    val year: Int? = null,
    val malId: Int? = null,
    val format: String? = null,
    val userScore: Int? = null,
    val siteUrl: String? = null
)

@Serializable
data class AiringScheduleAnime(
    val id: Int,
    val title: String,
    val titleEnglish: String? = null,
    val cover: String,
    val episodes: Int = 0,
    val airingEpisode: Int = 0,
    val airingAt: Long = 0,
    val timeUntilAiring: Long? = null,
    val averageScore: Int? = null,
    val genres: List<String> = emptyList(),
    val year: Int? = null,
    val malId: Int? = null,
    val isAdult: Boolean = false
)

@Serializable
data class UserActivity(
    val id: Int,
    val type: String,
    val status: String,
    val progress: String?,
    val createdAt: Long,
    val mediaId: Int,
    val mediaTitle: String,
    val mediaTitleEnglish: String? = null,
    val mediaCover: String,
    val episodes: Int?,
    val averageScore: Int?,
    val year: Int? = null
)

@Serializable
data class TmdbEpisode(
    val episode: Int,
    val title: String,
    val description: String,
    val image: String?
)

// ============================================
// SKIP TIMESTAMP MODELS
// ============================================

/**
 * Represents a single skip timestamp marker (intro, outro, recap, etc.)
 *
 * @param startTime The start time in seconds
 * @param skipType The type of skip ("op", "ed", "recap", "mixed-ed", "mixed-op")
 * @param skipId Unique identifier for this skip marker
 */
@Serializable
data class Timestamp(
    val startTime: Double,
    val skipType: String,
    val skipId: String
)

/**
 * Represents all skip timestamps for an episode.
 * Contains intro/outro/recap timing information for skip functionality.
 *
 * @param episodeNumber The episode number these timestamps belong to
 * @param introStart Start time of the opening in seconds (null if no intro)
 * @param introEnd End time of the opening in seconds (null if no intro)
 * @param creditsStart Start time of the ending credits in seconds (null if no outro)
 * @param creditsEnd End time of the ending credits in seconds (null if no outro)
 * @param recapStart Start time of a recap in seconds (null if no recap)
 * @param recapEnd End time of a recap in seconds (null if no recap)
 * @param allTimestamps List of all timestamp markers for this episode
 */
@Serializable
data class EpisodeTimestamps(
    val episodeNumber: Int,
    val introStart: Long? = null,
    val introEnd: Long? = null,
    val creditsStart: Long? = null,
    val creditsEnd: Long? = null,
    val recapStart: Long? = null,
    val recapEnd: Long? = null,
    val allTimestamps: List<Timestamp> = emptyList()
) {
    /**
     * Check if this episode has any valid skip timestamps
     */
    fun hasTimestamps(): Boolean {
        return introStart != null || creditsStart != null || recapStart != null
    }

}

// ============================================
// DETAILED ANIME DATA
// ============================================

@Serializable
data class DetailedAnimeData(
    val id: Int,
    val title: String,
    val titleRomaji: String? = null,
    val titleEnglish: String? = null,
    val titleNative: String? = null,
    val cover: String,
    val banner: String? = null,
    val description: String? = null,
    val episodes: Int = 0,
    val duration: Int? = null,
    val status: String? = null,
    val averageScore: Int? = null,
    val meanScore: Int? = null,
    val popularity: Int? = null,
    val favourites: Int? = null,
    val genres: List<String> = emptyList(),
    val tags: List<TagData> = emptyList(),
    val season: String? = null,
    val year: Int? = null,
    val format: String? = null,
    val source: String? = null,
    val studios: List<StudioData> = emptyList(),
    val startDate: String? = null,
    val endDate: String? = null,
    val nextAiringEpisode: Int? = null,
    val nextAiringTime: Long? = null,
    val isAdult: Boolean = false,
    val trailerUrl: String? = null,
    val trailerThumbnail: String? = null,
    val staff: DetailedAnimeStaff? = null,
    val recommendations: List<ExploreAnime> = emptyList(),
    val latestEpisode: Int? = null,
    val malId: Int? = null,
    val relations: List<AnimeRelation> = emptyList(),
    val characters: DetailedAnimeCharacters? = null,
    val siteUrl: String? = null
)

@Serializable
data class TagData(
    val name: String,
    val rank: Int? = null,
    val isMediaSpoiler: Boolean = false,
    val description: String? = null,
    val isAdult: Boolean = false
)

@Serializable
data class StudioData(
    val id: Int,
    val name: String,
    val isAnimationStudio: Boolean = true
)

@Serializable
data class AnimeRelation(
    val id: Int,
    val title: String,
    val cover: String,
    val episodes: Int?,
    val latestEpisode: Int? = null,
    val averageScore: Int?,
    val format: String?,
    val relationType: String
)

// ============================================
// STREAM MODELS (API)
// ============================================

/**
 * Quality option for a stream (e.g., 1080p, 720p, 480p).
 */
data class QualityOption(
    val quality: String,   // e.g., "1080p", "720p", "480p"
    val url: String,
    val width: Int
)

/**
 * Result of a stream fetch operation.
 * Includes skip timestamps from Animekai embed.
 */
data class AniwatchStreamResult(
    val url: String,
    val isDirectStream: Boolean = true,
    val headers: Map<String, String>? = null,
    val subtitleUrl: String? = null,
    val serverName: String = "Unknown",
    val category: String = "sub",
    val qualities: List<QualityOption> = emptyList(),
    // Skip timestamps from Animekai (in seconds)
    val introStart: Int? = null,
    val introEnd: Int? = null,
    val outroStart: Int? = null,
    val outroEnd: Int? = null
)

/**
 * Server information for an episode.
 */
data class ServerInfo(
    val name: String,
    val url: String,
    val qualities: List<QualityOption> = emptyList()
)

/**
 * Episode streams containing sub and dub servers.
 */
data class EpisodeStreams(
    val subServers: List<ServerInfo>,
    val dubServers: List<ServerInfo>,
    val animeId: String = "",
    val episodeId: String = ""
)

// ============================================
// CACHE MODELS
// ============================================

@Serializable
data class StreamCacheData(val entries: Map<String, StreamCacheEntry>)

@Serializable
data class StreamCacheEntry(
    val stream: CachedStream?,
    val episodeInfo: CachedEpisodeInfo?,
    val timestamp: Long
)

@Serializable
data class CachedQuality(
    val quality: String,
    val url: String,
    val width: Int
)

@Serializable
data class CachedStream(
    val url: String,
    val isDirectStream: Boolean,
    val headers: Map<String, String>?,
    val subtitleUrl: String?,
    val serverName: String,
    val category: String,
    val qualities: List<CachedQuality> = emptyList(),
    // Skip timestamps (in seconds)
    val introStart: Int? = null,
    val introEnd: Int? = null,
    val outroStart: Int? = null,
    val outroEnd: Int? = null
)

@Serializable
data class CachedEpisodeInfo(
    val subServers: List<CachedServer>,
    val dubServers: List<CachedServer>,
    val animeId: String,
    val episodeId: String
)

@Serializable
data class CachedServer(
    val name: String,
    val url: String,
    val qualities: List<CachedQuality> = emptyList()
)

@Serializable
data class CachedTrack(
    val url: String,
    val lang: String,
)

@Serializable
data class CachedVideo(
    val videoUrl: String,
    val videoTitle: String,
    val resolution: Int?,
    val headers: Map<String, String>?,
    val subtitleTracks: List<CachedTrack>,
    val audioTracks: List<CachedTrack>,
)

@Serializable
data class CachedHoster(
    val hosterUrl: String,
    val hosterName: String,
)

@Serializable
data class CachedExtensionStream(
    val url: String,
    val referer: String,
    val subtitleUrl: String?,
    val subtitleTracks: List<CachedTrack>,
    val videoTitle: String,
    val videos: List<CachedVideo>,
    val hosters: List<CachedHoster>?,
    val videoHeaders: Map<String, String>,
    val cachedAt: Long,
)

@Serializable
data class AiringCacheData(
    val scheduleByDay: Map<Int, List<AiringScheduleAnime>>,
    val airingAnimeList: List<AiringScheduleAnime>
)

@Serializable
data class ExploreCacheData(
    val featuredAnime: List<ExploreAnime>,
    val seasonalAnime: List<ExploreAnime>,
    val topSeries: List<ExploreAnime>,
    val topMovies: List<ExploreAnime>,
    val actionAnime: List<ExploreAnime>,
    val romanceAnime: List<ExploreAnime>,
    val comedyAnime: List<ExploreAnime>,
    val fantasyAnime: List<ExploreAnime>,
    val scifiAnime: List<ExploreAnime>
)

@Serializable
data class HomeCacheData(
    val currentlyWatching: List<AnimeMedia>,
    val planningToWatch: List<AnimeMedia>,
    val completed: List<AnimeMedia>,
    val onHold: List<AnimeMedia>,
    val dropped: List<AnimeMedia>,
    val userId: Int?,
    val userName: String?,
    val userAvatar: String?
)

@Serializable
data class PlaybackPositionCache(
    val positions: Map<String, Long>,
    val durations: Map<String, Long> = emptyMap(),
    val startedAt: Map<String, Long> = emptyMap()
)

data class ContinueWatchingEntry(
    val animeId: Int,
    val episode: Int,
    val animeTitle: String,
    val animeTitleEnglish: String?,
    val animeCover: String,
    val animeBanner: String?,
    val position: Long,
    val duration: Long,
    val startedAt: Long
)

// ============================================
// STREAM FETCH RESULT
// ============================================

// ============================================
// API RESPONSE MODELS (AniList)
// ============================================

@Serializable
data class ViewerResponse(val data: ViewerData)

@Serializable
data class ViewerData(val Viewer: Viewer)

@Serializable
data class Viewer(
    val id: Int,
    val name: String,
    val about: String? = null,
    val avatar: Avatar? = null,
    val bannerImage: String? = null,
    val siteUrl: String? = null,
    val createdAt: Long? = null,
    val statistics: ViewerStatistics? = null
)

@Serializable
data class ViewerStatistics(
    val anime: UserAnimeStats? = null
)

@Serializable
data class Avatar(val medium: String, val large: String? = null)

@Serializable
data class MediaListResponse(val data: MediaListData)

@Serializable
data class MediaListData(val MediaListCollection: MediaListCollection)

@Serializable
data class MediaListCollection(val lists: List<MediaList>)

@Serializable
data class MediaList(
    val name: String,
    val status: String?,
    val entries: List<MediaListEntry>
)

@Serializable
data class MediaListEntry(
    val id: Int,
    val mediaId: Int,
    val progress: Int?,
    val status: String?,
    val score: Int? = null,
    val media: MediaEntryMedia
)

@Serializable
data class MediaEntryMedia(
    val id: Int,
    val idMal: Int? = null,
    val title: MediaTitle,
    val coverImage: MediaCoverImage?,
    val bannerImage: String?,
    val episodes: Int?,
    val nextAiringEpisode: NextAiringEpisode?,
    val status: String?,
    val averageScore: Int?,
    val genres: List<String>?,
    val seasonYear: Int? = null
)

@Serializable
data class ExploreResponse(val data: ExploreData)

@Serializable
data class ExploreData(val Page: ExplorePage)

@Serializable
data class ExplorePage(val media: List<ExploreMedia>)

@Serializable
data class ExploreMedia(
    val id: Int,
    val idMal: Int? = null,
    val title: MediaTitle,
    val coverImage: MediaCoverImage?,
    val bannerImage: String?,
    val episodes: Int?,
    val nextAiringEpisode: NextAiringEpisode? = null,
    val status: String?,
    val averageScore: Int?,
    val genres: List<String>?,
    val tags: List<TagData>? = null,
    val seasonYear: Int? = null,
    val startDate: FuzzyDate? = null,
    val isAdult: Boolean = false,
    val format: String? = null
)

@Serializable
data class BatchedExploreResponse(val data: BatchedExploreData)

@Serializable
data class BatchedExploreData(
    val featured: ExplorePage,
    val seasonal: ExplorePage,
    val topSeries: ExplorePage,
    val topMovies: ExplorePage,
    val action: ExplorePage,
    val romance: ExplorePage,
    val comedy: ExplorePage,
    val fantasy: ExplorePage,
    val scifi: ExplorePage
)

@Serializable
data class FuzzyDate(
    val year: Int? = null,
    val month: Int? = null,
    val day: Int? = null
)

@Serializable
data class MediaTitle(
    val romaji: String?,
    val english: String?
)

@Serializable
data class MediaCoverImage(
    val extraLarge: String? = null,
    val large: String? = null,
    val medium: String? = null
)

@Serializable
data class NextAiringEpisode(
    val episode: Int? = null,
    val airingAt: Long? = null,
    val timeUntilAiring: Long? = null
)

@Serializable
data class AiringScheduleResponse(val data: AiringScheduleData)

@Serializable
data class AiringScheduleData(val Page: AiringSchedulePage)

@Serializable
data class AiringSchedulePage(val airingSchedules: List<AiringScheduleEntry>)

@Serializable
data class AiringScheduleEntry(
    val id: Int,
    val airingAt: Long,
    val episode: Int,
    val timeUntilAiring: Long? = null,
    val mediaId: Int,
    val media: AiringScheduleMedia?
)

@Serializable
data class AiringScheduleMedia(
    val id: Int,
    val idMal: Int? = null,
    val title: MediaTitle,
    val coverImage: MediaCoverImage?,
    val episodes: Int?,
    val status: String?,
    val averageScore: Int?,
    val genres: List<String>?,
    val tags: List<TagData>? = null,
    val seasonYear: Int? = null,
    val isAdult: Boolean = false
)

@Serializable
data class UserFavoritesResponse(val data: UserFavoritesData)

@Serializable
data class UserFavoritesData(val User: UserFavoritesUser)

@Serializable
data class UserFavoritesUser(val favourites: UserFavorites)

@Serializable
data class UserFavorites(val anime: UserFavoritesAnime)

@Serializable
data class UserFavoritesAnime(val nodes: List<UserFavoriteAnime>)

@Serializable
data class UserFavoriteAnime(
    val id: Int,
    val title: MediaTitle,
    val coverImage: MediaCoverImage?,
    val episodes: Int?,
    val averageScore: Int?,
    val genres: List<String>?,
    val seasonYear: Int? = null,
    val format: String? = null,
    val status: String? = null,
    val userScore: Int? = null
)

@Serializable
data class UserStatsResponse(val data: UserStatsData)

@Serializable
data class UserStatsData(val User: UserStats)

@Serializable
data class UserStats(val statistics: UserStatistics)

@Serializable
data class UserStatistics(val anime: UserAnimeStats)

@Serializable
data class UserAnimeStats(
    val count: Int = 0,
    val episodesWatched: Int = 0,
    val minutesWatched: Int = 0,
    val meanScore: Double? = null
)

@Serializable
data class SimpleActivityResponse(val data: SimpleActivityData)

@Serializable
data class SimpleActivityData(val Page: SimpleActivityPage)

@Serializable
data class SimpleActivityPage(val activities: List<SimpleActivityEntry>)

@Serializable
data class SimpleActivityEntry(
    val createdAt: Long,
    val status: String?,
    val progress: String?,
    val media: SimpleActivityMedia?
)

@Serializable
data class SimpleActivityMedia(
    val id: Int,
    val title: SimpleActivityTitle,
    val coverImage: MediaCoverImage?
)

@Serializable
data class SimpleActivityTitle(
    val romaji: String?,
    val english: String?
)

@Serializable
data class DetailedAnimeResponse(val data: DetailedAnimeDataWrapper)

@Serializable
data class DetailedAnimeDataWrapper(val Media: DetailedAnimeMedia)

@Serializable
data class DetailedAnimeMedia(
    val id: Int,
    val idMal: Int? = null,
    val title: DetailedAnimeTitle? = null,
    val coverImage: MediaCoverImage? = null,
    val bannerImage: String? = null,
    val description: String? = null,
    val episodes: Int? = null,
    val duration: Int? = null,
    val status: String? = null,
    val averageScore: Int? = null,
    val popularity: Int? = null,
    val favourites: Int? = null,
    val genres: List<String>? = null,
    val tags: List<TagData>? = null,
    val season: String? = null,
    val seasonYear: Int? = null,
    val format: String? = null,
    val source: String? = null,
    val studios: DetailedAnimeStudios? = null,
    val startDate: FuzzyDate? = null,
    val endDate: FuzzyDate? = null,
    val nextAiringEpisode: NextAiringEpisode? = null,
    val relations: AnimeRelations? = null,
    val isAdult: Boolean = false,
    val characters: DetailedAnimeCharacters? = null,
    val trailer: MediaTrailer? = null,
    val staff: DetailedAnimeStaff? = null,
    val recommendations: DetailedAnimeRecommendations? = null
)

@Serializable
data class DetailedAnimeRecommendations(
    val nodes: List<DetailedAnimeRecommendationNode> = emptyList()
)

@Serializable
data class DetailedAnimeRecommendationNode(
    val mediaRecommendation: DetailedAnimeMedia? = null
)

@Serializable
data class DetailedAnimeTitle(
    val romaji: String? = null,
    val english: String? = null,
    val native: String? = null
)

@Serializable
data class DetailedAnimeStudios(
    val nodes: List<DetailedAnimeStudioNode> = emptyList()
)

@Serializable
data class DetailedAnimeStudioNode(
    val id: Int? = null,
    val name: String? = null
)

// ============================================
// ANIME RELATIONS
// ============================================

@Serializable
data class AnimeRelationsResponse(val data: AnimeRelationsData)

@Serializable
data class AnimeRelationsData(val Media: AnimeRelationsMedia)

@Serializable
data class AnimeRelationsMedia(
    val id: Int,
    val title: MediaTitle? = null,
    val episodes: Int? = null,
    val format: String? = null,
    val nextAiringEpisode: NextAiringEpisode? = null,
    val relations: AnimeRelations? = null
)

@Serializable
data class AnimeRelations(
    val edges: List<AnimeRelationEdge> = emptyList()
)

@Serializable
data class AnimeRelationEdge(
    val relationType: String? = null,
    val node: AnimeRelationNode? = null
)

@Serializable
data class AnimeRelationNode(
    val id: Int,
    val title: MediaTitle? = null,
    val coverImage: MediaCoverImage? = null,
    val episodes: Int? = null,
    val averageScore: Int? = null,
    val type: String? = null,
    val format: String? = null,
    val nextAiringEpisode: NextAiringEpisode? = null
)

@Serializable
data class AnimeRecommendationsResponse(val data: AnimeRecommendationsData)

@Serializable
data class AnimeRecommendationsData(val Media: AnimeRecommendationsMedia)

@Serializable
data class AnimeRecommendationsMedia(
    val id: Int,
    val recommendations: AnimeRecommendations? = null
)

@Serializable
data class AnimeRecommendations(
    val nodes: List<AnimeRecommendationNode> = emptyList()
)

@Serializable
data class AnimeRecommendationNode(
    val mediaRecommendation: AnimeRelationNode? = null
)

// ============================================
// TMDB API RESPONSE MODELS
// ============================================

@Serializable
data class TmdbSearchResponse(
    val page: Int = 0,
    val results: List<TmdbSearchResult> = emptyList(),
    val total_pages: Int = 0,
    val total_results: Int = 0
)

@Serializable
data class TmdbSearchResult(
    val id: Int = 0,
    val name: String? = null,
    val title: String? = null,
    val original_name: String? = null,
    val original_title: String? = null,
    val overview: String? = null,
    val poster_path: String? = null,
    val backdrop_path: String? = null,
    val first_air_date: String? = null,
    val release_date: String? = null,
    val vote_average: Double? = null,
    val popularity: Double? = null,
    val original_language: String? = null,
    val origin_country: List<String>? = null
)

@Serializable
data class TmdbTvDetails(
    val id: Int = 0,
    val name: String? = null,
    val original_name: String? = null,
    val overview: String? = null,
    val poster_path: String? = null,
    val backdrop_path: String? = null,
    val first_air_date: String? = null,
    val last_air_date: String? = null,
    val number_of_seasons: Int = 0,
    val number_of_episodes: Int = 0,
    val vote_average: Double? = null,
    val popularity: Double? = null,
    val status: String? = null,
    val original_language: String? = null,
    val origin_country: List<String>? = null,
    val genres: List<TmdbGenre> = emptyList(),
    val seasons: List<TmdbSeason> = emptyList()
)

@Serializable
data class TmdbGenre(
    val id: Int = 0,
    val name: String? = null
)

@Serializable
data class TmdbSeason(
    val id: Int = 0,
    val season_number: Int = 0,
    val name: String? = null,
    val overview: String? = null,
    val poster_path: String? = null,
    val air_date: String? = null,
    val episode_count: Int = 0
)

@Serializable
data class TmdbSeasonDetails(
    val id: Int = 0,
    val season_number: Int = 0,
    val name: String? = null,
    val overview: String? = null,
    val poster_path: String? = null,
    val air_date: String? = null,
    val episodes: List<TmdbEpisodeDetails> = emptyList()
)

@Serializable
data class TmdbEpisodeDetails(
    val id: Int = 0,
    val episode_number: Int = 0,
    val season_number: Int = 0,
    val name: String? = null,
    val overview: String? = null,
    val still_path: String? = null,
    val air_date: String? = null,
    val runtime: Int? = null,
    val vote_average: Double? = null
)

// ============================================
// EXTENSION FUNCTIONS
// ============================================

fun ExploreAnime.toDetailedAnimeData(): DetailedAnimeData {
    return DetailedAnimeData(
        id = id,
        title = title,
        titleRomaji = title,
        titleEnglish = titleEnglish,
        cover = cover,
        banner = banner,
        episodes = episodes,
        averageScore = averageScore,
        genres = genres,
        year = year,
        latestEpisode = latestEpisode,
        malId = malId,
        siteUrl = com.blissless.tensei.network.Endpoints.AniList.animePageUrl(id)
    )
}

fun AnimeMedia.toDetailedAnimeData(): DetailedAnimeData {
    return DetailedAnimeData(
        id = id,
        title = title,
        titleRomaji = title,
        titleEnglish = titleEnglish,
        cover = cover,
        banner = banner,
        episodes = totalEpisodes,
        averageScore = averageScore,
        genres = genres,
        year = year,
        latestEpisode = latestEpisode,
        malId = malId,
        siteUrl = siteUrl ?: com.blissless.tensei.network.Endpoints.AniList.animePageUrl(id)
    )
}

// ============================================
// LOCAL FAVORITES
// ============================================

@Serializable
data class StoredFavorite(
    val id: Int,
    val title: String,
    val cover: String,
    val banner: String? = null,
    val year: Int? = null,
    val averageScore: Int? = null
)

@Serializable
data class LocalAnimeEntry(
    val id: Int,
    val status: String,
    val progress: Int = 0,
    val totalEpisodes: Int = 0,
    val title: String = "",
    val cover: String = "",
    val banner: String? = null,
    val year: Int? = null,
    val averageScore: Int? = null,
    val score: Int? = null
)

@Serializable
data class DetailedAnimeCharacters(
    val nodes: List<DetailedAnimeCharacterNode> = emptyList()
)

@Serializable
data class DetailedAnimeCharacterNode(
    val id: Int,
    val name: DetailedCharacterName? = null,
    val image: MediaCoverImage? = null
)

@Serializable
data class DetailedCharacterName(
    val full: String? = null,
    val native: String? = null
)

@Serializable
data class DetailedAnimeStaff(
    val edges: List<DetailedAnimeStaffEdge> = emptyList()
)

@Serializable
data class DetailedAnimeStaffEdge(
    val node: DetailedAnimeStaffNode? = null,
    val role: String? = null
)

@Serializable
data class DetailedAnimeStaffNode(
    val id: Int,
    val name: DetailedCharacterName? = null,
    val image: MediaCoverImage? = null
)

@Serializable
data class MediaTrailer(
    val id: String? = null,
    val site: String? = null
)

@Serializable
data class CharacterData(
    val id: Int,
    val name: DetailedCharacterName? = null,
    val image: MediaCoverImage? = null,
    val description: String? = null,
    val anime: CharacterAnimeConnection? = null
)

@Serializable
data class CharacterAnimeConnection(
    val nodes: List<CharacterAnimeNode> = emptyList()
)

@Serializable
data class CharacterAnimeNode(
    val id: Int,
    val title: MediaTitle? = null,
    val coverImage: MediaCoverImage? = null,
    val format: String? = null
)

@Serializable
data class StaffData(
    val id: Int,
    val name: DetailedCharacterName? = null,
    val image: MediaCoverImage? = null,
    val description: String? = null,
    val anime: StaffAnimeConnection? = null,
    val primaryOccupations: List<String>? = null
)

@Serializable
data class StaffAnimeConnection(
    val edges: List<StaffAnimeEdge> = emptyList()
)

@Serializable
data class StaffAnimeEdge(
    val node: CharacterAnimeNode? = null,
    val staffRole: String? = null
)

@Serializable
data class CharacterResponse(val data: CharacterResponseData)
@Serializable
data class CharacterResponseData(val Character: CharacterData?)

@Serializable
data class StaffResponse(val data: StaffResponseData)
@Serializable
data class StaffResponseData(val Staff: StaffData?)

@Serializable
data class AllCharactersResponse(val data: AllCharactersData)
@Serializable
data class AllCharactersData(val Media: AllCharactersMedia?)
@Serializable
data class AllCharactersMedia(val characters: AllCharactersConnection?)
@Serializable
data class AllCharactersConnection(val nodes: List<CharacterData> = emptyList())

@Serializable
data class AllStaffResponse(val data: AllStaffData)
@Serializable
data class AllStaffData(val Media: AllStaffMedia?)
@Serializable
data class AllStaffMedia(val staff: AllStaffConnection?)
@Serializable
data class AllStaffConnection(val nodes: List<StaffData> = emptyList())

@Serializable
data class MediaTagCollectionResponse(val data: MediaTagCollectionData)
@Serializable
data class MediaTagCollectionData(val MediaTagCollection: List<MediaTag>)
@Serializable
data class MediaTag(
    val id: Int,
    val name: String,
    val description: String? = null,
    val category: String? = null,
    val rank: Int? = null,
    val isAdult: Boolean = false
)

// ============================================
// MANGA MODELS
// ============================================

@Serializable
data class MangaMedia(
    val id: Int,
    val title: String,
    val titleEnglish: String? = null,
    val cover: String,
    val banner: String? = null,
    val progress: Int = 0,
    val totalChapters: Int = 0,
    val totalVolumes: Int? = null,
    val latestChapter: Float? = null,
    val status: String = "",
    val averageScore: Int? = null,
    val genres: List<String> = emptyList(),
    val listStatus: String = "",
    val listEntryId: Int? = null,
    val year: Int? = null,
    val malId: Int? = null,
    val format: String? = null,
    val userScore: Int? = null,
    val siteUrl: String? = null,
    val mangaDexId: String? = null,
    val scrollProgress: Float = 0f,
    val currentChapterPages: Int = 0
)

@Serializable
data class MangaChapter(
    val url: String,
    val title: String,
    val chapterId: String,
    val volume: Float? = null,
    val chapterNumber: Float = 0f,
    val groups: List<String> = emptyList()
)

@Serializable
data class ChapterImages(
    val chapterUrl: String,
    val images: List<String>
)

@Serializable
data class MangaDetail(
    val id: Int,
    val title: String,
    val titleEnglish: String? = null,
    val titleNative: String? = null,
    val cover: String,
    val banner: String? = null,
    val description: String? = null,
    val chapters: Int = 0,
    val volumes: Int? = null,
    val status: String? = null,
    val averageScore: Int? = null,
    val meanScore: Int? = null,
    val popularity: Int? = null,
    val favourites: Int? = null,
    val genres: List<String> = emptyList(),
    val tags: List<TagData> = emptyList(),
    val year: Int? = null,
    val format: String? = null,
    val source: String? = null,
    val isAdult: Boolean = false,
    val staff: MangaStaff? = null,
    val recommendations: List<MangaMedia> = emptyList(),
    val malId: Int? = null,
    val relations: List<MangaRelation> = emptyList(),
    val characters: MangaCharacters? = null,
    val siteUrl: String? = null,
    val mangaDexId: String? = null,
    val synonymTitles: List<String> = emptyList(),
    val rankings: List<MangaRankingEntry> = emptyList(),
    val externalLinks: List<MangaExternalLink> = emptyList()
)

@Serializable
data class MangaRelation(
    val id: Int,
    val title: String,
    val cover: String,
    val chapters: Int?,
    val averageScore: Int?,
    val format: String?,
    val relationType: String
)

@Serializable
data class MangaStaff(
    val edges: List<MangaStaffEdge> = emptyList()
)

@Serializable
data class MangaStaffEdge(
    val node: MangaStaffNode? = null,
    val role: String? = null
)

@Serializable
data class MangaStaffNode(
    val id: Int,
    val name: MangaCharacterName? = null,
    val image: MediaCoverImage? = null
)

@Serializable
data class MangaCharacters(
    val nodes: List<MangaCharacterNode> = emptyList()
)

@Serializable
data class MangaCharacterNode(
    val id: Int,
    val name: MangaCharacterName? = null,
    val image: MediaCoverImage? = null
)

@Serializable
data class MangaCharacterName(
    val full: String? = null,
    val native: String? = null
)

@Serializable
data class MangaExternalLink(
    val url: String? = null,
    val site: String? = null
)

@Serializable
data class MangaRankingEntry(
    val id: Int? = null,
    val rank: Int? = null,
    val type: String? = null,
    val context: String? = null,
    val allTime: Boolean? = null,
    val season: String? = null,
    val year: Int? = null
)

@Serializable
data class MangaTrack(
    val mangaId: Int,
    val title: String = "",
    val cover: String = "",
    val progress: Float = 0f,
    val scrollProgress: Float = 0f,
    val totalChapters: Int = 0,
    val totalVolumes: Int? = null,
    val status: String = "PLANNING",
    val mangaDexId: String? = null,
    val lastReadChapter: MangaChapter? = null,
    val currentChapterPages: Int = 0,
    val score: Int? = null,
    val averageScore: Int? = null
)

@Serializable
data class MangaExploreMedia(
    val id: Int,
    val idMal: Int? = null,
    val title: MangaTitle,
    val coverImage: MediaCoverImage? = null,
    val bannerImage: String? = null,
    val chapters: Int? = null,
    val status: String? = null,
    val averageScore: Int? = null,
    val genres: List<String>? = null,
    val seasonYear: Int? = null,
    val startDate: MangaFuzzyDate? = null,
    val isAdult: Boolean = false,
    val format: String? = null,
    val volumes: Int? = null
)

@Serializable
data class MangaTitle(
    val romaji: String? = null,
    val english: String? = null
)

@Serializable
data class MangaFuzzyDate(
    val year: Int? = null,
    val month: Int? = null,
    val day: Int? = null
)

@Serializable
data class MangaExploreResponse(val data: MangaExploreData)

@Serializable
data class MangaExploreData(val Page: MangaExplorePage)

@Serializable
data class MangaExplorePage(val media: List<MangaExploreMedia>)

@Serializable
data class MangaDetailResponse(val data: MangaDetailDataWrapper)

@Serializable
data class MangaDetailDataWrapper(val Media: MangaDetailMedia)

@Serializable
data class MangaDetailMedia(
    val id: Int,
    val idMal: Int? = null,
    val title: MangaDetailTitle? = null,
    val coverImage: MediaCoverImage? = null,
    val bannerImage: String? = null,
    val description: String? = null,
    val chapters: Int? = null,
    val volumes: Int? = null,
    val status: String? = null,
    val averageScore: Int? = null,
    val meanScore: Int? = null,
    val popularity: Int? = null,
    val favourites: Int? = null,
    val genres: List<String>? = null,
    val tags: List<TagData>? = null,
    val seasonYear: Int? = null,
    val startDate: StartDate? = null,
    val format: String? = null,
    val source: String? = null,
    val isAdult: Boolean = false,
    val relations: MangaDetailRelations? = null,
    val characters: MangaDetailCharacters? = null,
    val staff: MangaDetailStaffWrapper? = null,
    val recommendations: MangaDetailRecommendations? = null,
    val synonyms: List<String>? = null,
    val rankings: List<MangaRankingEntry>? = null,
    val externalLinks: List<MangaExternalLink>? = null
)

@Serializable
data class MangaDetailTitle(
    val romaji: String? = null,
    val english: String? = null,
    val native: String? = null
)

@Serializable
data class StartDate(
    val year: Int? = null,
    val month: Int? = null,
    val day: Int? = null
)

@Serializable
data class MangaDetailRelations(
    val edges: List<MangaDetailRelationEdge>
)

@Serializable
data class MangaDetailRelationEdge(
    val relationType: String? = null,
    val node: MangaDetailRelationNode? = null
)

@Serializable
data class MangaDetailRelationNode(
    val id: Int,
    val title: MangaTitle? = null,
    val coverImage: MediaCoverImage? = null,
    val chapters: Int? = null,
    val averageScore: Int? = null,
    val format: String? = null
)

@Serializable
data class MangaDetailCharacters(
    val nodes: List<MangaDetailCharacterNode> = emptyList()
)

@Serializable
data class MangaDetailCharacterNode(
    val id: Int,
    val name: MangaCharacterName? = null,
    val image: MediaCoverImage? = null
)

@Serializable
data class MangaDetailStaffWrapper(
    val edges: List<MangaStaffEdge> = emptyList()
)

@Serializable
data class MangaDetailRecommendations(
    val nodes: List<MangaDetailRecommendationNode> = emptyList()
)

@Serializable
data class MangaDetailRecommendationNode(
    val mediaRecommendation: MangaExploreMedia? = null
)

@Serializable
data class MangaUserListResponse(val data: MangaUserListData)

@Serializable
data class MangaUserListData(val MediaListCollection: MangaMediaListCollection)

@Serializable
data class MangaMediaListCollection(val lists: List<MangaMediaList>)

@Serializable
data class MangaMediaList(
    val name: String,
    val status: String?,
    val entries: List<MangaMediaListEntry>
)

@Serializable
data class MangaMediaListEntry(
    val id: Int,
    val mediaId: Int,
    val progress: Int?,
    val progressVolumes: Int?,
    val score: Int?,
    val status: String?,
    val media: MangaEntryMedia
)

@Serializable
data class MangaEntryMedia(
    val id: Int,
    val idMal: Int? = null,
    val title: MangaTitle,
    val coverImage: MediaCoverImage?,
    val bannerImage: String?,
    val chapters: Int?,
    val volumes: Int?,
    val status: String?,
    val averageScore: Int?,
    val genres: List<String>?,
    val seasonYear: Int? = null,
    val startDate: StartDate? = null,
    val format: String? = null
)

@Serializable
data class MangaUserProfileResponse(val data: MangaUserProfileData)

@Serializable
data class MangaUserProfileData(val User: MangaUserProfile? = null)

@Serializable
data class MangaUserProfile(
    val id: Int,
    val name: String,
    val avatar: Avatar? = null,
    val bannerImage: String? = null,
    val siteUrl: String? = null,
    val createdAt: Long? = null,
    val statistics: MangaUserStatistics? = null
)

@Serializable
data class MangaUserStatistics(
    val manga: MangaUserMangaStats? = null
)

@Serializable
data class MangaUserMangaStats(
    val count: Int = 0,
    val chaptersRead: Int = 0,
    val volumesRead: Int = 0,
    val meanScore: Double? = null
)

// MangaDex models
@Serializable
data class MangaDexAggregate(
    val result: String? = null,
    val volumes: Map<String, MangaDexVolume>? = null
)

@Serializable
data class MangaDexVolume(
    val volume: String? = null,
    val count: Int = 0,
    val chapters: Map<String, MangaDexVolumeChapter>? = null
)

@Serializable
data class MangaDexVolumeChapter(
    val chapter: String? = null,
    val id: String? = null,
    val others: List<String>? = null,
    val count: Int = 0
)

@Serializable
data class MangaDexAtHome(
    val result: String? = null,
    val baseUrl: String? = null,
    val chapter: MangaDexAtHomeChapter? = null
)

@Serializable
data class MangaDexAtHomeChapter(
    val hash: String? = null,
    val data: List<String>? = null,
    val dataSaver: List<String>? = null
)

@Serializable
data class MangaDexMangaResponse(
    val result: String? = null,
    val response: String? = null,
    val data: List<MangaDexMangaData>? = null
)

@Serializable
data class MangaDexMangaData(
    val id: String? = null,
    val type: String? = null,
    val attributes: MangaDexMangaAttributes? = null
)

@Serializable
data class MangaDexMangaAttributes(
    val title: MangaDexLocalizedString? = null,
    val altTitles: List<MangaDexLocalizedString>? = null,
    val links: MangaDexLinks? = null
)

@Serializable
data class MangaDexLocalizedString(
    val en: String? = null,
    val ja: String? = null
)

@Serializable
data class MangaDexLinks(
    val al: String? = null
)


// ─── Manga Favorites & Activity (Profile) ────────────────────────────

@Serializable
data class MangaFavorite(
    val id: Int,
    val title: MangaTitle? = null,
    val coverImage: MediaCoverImage? = null,
    val format: String? = null,
    val status: String? = null,
    val startDate: MangaFuzzyDate? = null,
    val chapters: Int? = null,
    val averageScore: Int? = null,
    val siteUrl: String? = null,
    val userScore: Int? = null
)

@Serializable
data class MangaFavoritesResponse(val data: MangaFavoritesData)

@Serializable
data class MangaFavoritesData(val Viewer: MangaFavoritesViewer)

@Serializable
data class MangaFavoritesViewer(val favourites: MangaFavoritesFavourites? = null)

@Serializable
data class MangaFavoritesFavourites(val manga: MangaFavoritesPage? = null)

@Serializable
data class MangaFavoritesPage(val nodes: List<MangaFavorite> = emptyList())

@Serializable
data class MangaActivity(
    val id: Int,
    val createdAt: Long,
    val status: String? = null,
    val progress: String? = null,
    val media: MangaActivityMedia? = null
)

@Serializable
data class MangaActivityMedia(
    val id: Int,
    val title: MangaTitle? = null,
    val coverImage: MediaCoverImage? = null,
    val chapters: Int? = null,
    val siteUrl: String? = null
)

@Serializable
data class MangaActivityResponse(val data: MangaActivityData)

@Serializable
data class MangaActivityData(val Page: MangaActivityPage)

@Serializable
data class MangaActivityPage(
    val activities: List<MangaActivityNode> = emptyList()
)

@Serializable
data class MangaActivityNode(
    val id: Int = 0,
    val createdAt: Long = 0,
    val status: String? = null,
    val progress: String? = null,
    val media: MangaActivityMedia? = null
)