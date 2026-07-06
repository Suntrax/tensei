package com.blissless.tensei.data

import android.util.Log
import com.blissless.tensei.BuildConfig
import com.blissless.tensei.data.models.AnimeRelation
import com.blissless.tensei.data.models.AnimeRelationsMedia
import com.blissless.tensei.data.models.AnimeRelationsResponse
import com.blissless.tensei.data.models.TmdbEpisode
import com.blissless.tensei.data.models.TmdbSearchResponse
import com.blissless.tensei.data.models.TmdbSearchResult
import com.blissless.tensei.data.models.TmdbSeasonDetails
import com.blissless.tensei.data.models.TmdbTvDetails
import com.blissless.tensei.network.Endpoints
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection

/**
 * TMDB episode metadata fetching logic for [AnimeRepository].
 *
 * Extracted from AnimeRepository.kt. This is the largest self-contained
 * concern in the repository: fetching and reconciling episode metadata
 * from TheMovieDB (TMDB) for anime titles.
 *
 * The flow:
 *   1. Search TMDB for the anime title (TV vs movie endpoint based on format)
 *   2. Pick the best match using title similarity + genre heuristics
 *   3. Fetch all seasons in parallel
 *   4. Calculate the episode offset (handles multi-season anime, prequel
 *      recursion via AniList, and Aniwatch fallback for ambiguous cases)
 *   5. Build a flat episode list with titles, descriptions, and airing status
 *
 * Public API unchanged. Internal helpers marked `internal` so they can be
 * shared across the file boundary while staying hidden from outside the
 * module.
 */

    // tmdbBearerToken and visitedOffsetIds remain in AnimeRepository.kt as internal vals.

suspend fun AnimeRepository.fetchTmdbEpisodes(
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
                    image = bestMatch.poster_path?.let { Endpoints.Tmdb.imageUrl(it) }
                ))
            }
            
            // Continue with TV show logic
            val tvDetails = fetchTvDetails(bestMatch.id) ?: return@withContext emptyList()
            Log.d("TmdbDebug", "TV details: id=${bestMatch.id} name=${tvDetails.name} seasons=${tvDetails.seasons.map { it.season_number }} totalEps=${tvDetails.number_of_episodes}")
            
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
            Log.d("TmdbDebug", "Fetching seasons: ${sortedSeasons.map { it.season_number }} for tmdbId=${tvDetails.id}")
            val allSeasonDetails = coroutineScope {
                sortedSeasons.map { season ->
                    async { fetchSeason(tvDetails.id, season.season_number) }
                }.awaitAll().filterNotNull()
            }
            Log.d("TmdbDebug", "Fetched ${allSeasonDetails.size} seasons, episodes per season: ${allSeasonDetails.map { "${it.season_number}:${it.episodes.size}" }}")

            val (episodeOffset, maxEpisodes) = calculateEpisodeOffset(tvDetails, allSeasonDetails, animeTitle, animeId, bestMatch.name, searchResults.size)
            Log.d("TmdbDebug", "Offset=$episodeOffset maxEpisodes=$maxEpisodes animeTitle=$animeTitle")

            val result = buildEpisodesFromPool(allSeasonDetails, episodeOffset, latestAiredEpisode, maxEpisodes)
            Log.d("TmdbDebug", "Final episode count=${result.size}, first=${result.firstOrNull()?.episode}, last=${result.lastOrNull()?.episode}")
            result
        } catch (e: Exception) {
            Log.e("TmdbDebug", "fetchTmdbEpisodes failed for animeId=$animeId title=$animeTitle", e)
            emptyList()
        }
    }

internal fun AnimeRepository.searchTmdb(title: String, format: String? = null): List<TmdbSearchResult> {
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
                val movieUrl = URL(Endpoints.Tmdb.searchMovie(encodedTitle))
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
                    val tvUrl = URL(Endpoints.Tmdb.searchTv(encodedTitle))
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
                val tvUrl = URL(Endpoints.Tmdb.searchTv(encodedTitle))
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
                    val movieUrl = URL(Endpoints.Tmdb.searchMovie(encodedTitle))
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
        } catch (e: Exception) {
            Log.e("TmdbDebug", "searchTmdb failed for title='$title' format=$format", e)
            emptyList() 
        }
    }

internal fun AnimeRepository.fetchTvDetails(tmdbId: Int): TmdbTvDetails? {
        return try {
            val url = URL(Endpoints.Tmdb.tvDetails(tmdbId))
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
        } catch (e: Exception) {
            Log.e("TmdbDebug", "fetchTvDetails failed for tmdbId=$tmdbId", e)
            null
        }
    }

internal suspend fun AnimeRepository.fetchSeason(tvId: Int, seasonNumber: Int): TmdbSeasonDetails? = withContext(Dispatchers.IO) {
        try {
            val urlStr = Endpoints.Tmdb.season(tvId, seasonNumber)
            val url = URL(urlStr)
            val connection = (url.openConnection() as HttpsURLConnection).apply {
                readTimeout = 15000
                connectTimeout = 15000
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $tmdbBearerToken")
            }

            val responseCode = connection.responseCode
            if (responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                val details = json.decodeFromString<TmdbSeasonDetails>(response)
                Log.d("TmdbDebug", "Season $seasonNumber: ${details.episodes.size} episodes")
                details
            } else {
                Log.w("TmdbDebug", "Season $seasonNumber: HTTP $responseCode for $urlStr")
                null
            }
        } catch (e: Exception) {
            Log.e("TmdbDebug", "Season $seasonNumber error: ${e.message}")
            null
        }
    }

internal fun AnimeRepository.buildEpisodesFromPool(
        allSeasonDetails: List<TmdbSeasonDetails>,
        episodeOffset: Int,
        latestAiredEpisode: Int,
        maxEpisodes: Int
    ): List<TmdbEpisode> {
        val allEpisodes = mutableListOf<TmdbEpisode>()
        var absoluteIndex = 1

        // First, collect all episodes from TMDB
        Log.d("TmdbDebug", "buildEpisodesFromPool: offset=$episodeOffset maxEp=$maxEpisodes latest=$latestAiredEpisode seasons=${allSeasonDetails.size}")
        data class EpisodeData(val relativeNum: Int, val title: String?, val description: String?, val image: String?)
        val tmdbEpisodes = mutableListOf<EpisodeData>()
        
        for (season in allSeasonDetails) {
            Log.d("TmdbDebug", "  Season ${season.season_number}: ${season.episodes.size} episodes, starting absoluteIndex=$absoluteIndex")
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
                    val image = episode.still_path?.let { Endpoints.Tmdb.imageUrl(it) }
                    
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

internal suspend fun AnimeRepository.calculateEpisodeOffset(
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

internal suspend fun AnimeRepository.calculateRecursiveOffset(animeId: Int): Int {
        visitedOffsetIds.clear()
        val offset = getPrequelEpisodesSum(animeId)
        return offset
    }

internal suspend fun AnimeRepository.getPrequelEpisodesSum(animeId: Int): Int {
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

suspend fun AnimeRepository.fetchAnimeRelationsList(animeId: Int): List<AnimeRelation>? {
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

internal suspend fun AnimeRepository.fetchEpisodeOffsetFromAniwatch(
        animeTitle: String,
        allSeasonDetails: List<TmdbSeasonDetails>
    ): Pair<Int, Int> {
        return withContext(Dispatchers.IO) {
            try {
                val encodedTitle = URLEncoder.encode(animeTitle, "UTF-8")
                val url = URL(Endpoints.Aniwatch.search(encodedTitle, 1))
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

                val episodesUrl = URL(Endpoints.Aniwatch.episodes(aniwatchId))
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

internal fun AnimeRepository.findTmdbEpisodeOffsetByTitle(allSeasonDetails: List<TmdbSeasonDetails>, targetTitle: String): Int {
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

internal fun AnimeRepository.wordOverlapScore(original: String, candidate: String): Int {
        val origWords = original.split(" ").filter { it.length > 2 }.toSet()
        val candWords = candidate.split(" ").filter { it.length > 2 }.toSet()
        if (origWords.isEmpty() || candWords.isEmpty()) return 0
        val common = origWords.intersect(candWords).size
        val extra = (candWords - origWords).size
        return common * 30 - extra * 50
    }

internal fun AnimeRepository.findBestMatch(results: List<TmdbSearchResult>, originalTitle: String): TmdbSearchResult? {
        val normalizedOriginal = normalizeTitle(originalTitle)
        
        // Check if original title might be Chinese (contains CJK characters)
        val isChineseTitle = originalTitle.toCharArray().any { it.code in 0x4E00..0x9FFF || it.code in 0x3400..0x4DBF }
        
        // First, try a quick match without fetching genres
        val quickMatch = results.maxByOrNull { result ->
            val name = result.name ?: result.title ?: result.original_name ?: ""
            val normalizedName = normalizeTitle(name)
            val normalizedOrigName = normalizeTitle(result.original_name ?: "")
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
            if (normalizedName == normalizedOriginal) {
                score += 500
                score += result.id / 1000  // tiebreaker
            } else if (normalizedOrigName == normalizedOriginal) {
                score += 500
                score += result.id / 1000
            }
            
            // Partial match (substring)
            if (normalizedName.length > 2 && normalizedOriginal.length > 2) {
                if (normalizedOriginal in normalizedName || normalizedName in normalizedOriginal) {
                    score += 100
                }
            }
            
            // Word overlap scoring
            score += wordOverlapScore(normalizedOriginal, normalizedName)
            if (result.original_name != null) {
                score += wordOverlapScore(normalizedOriginal, normalizeTitle(result.original_name))
            }
            
            // Prefer Japanese original language for anime
            if (result.original_language == "ja") score += 30
            
            // Prefer higher popularity
            score += (result.popularity?.toInt() ?: 0) / 100
            
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
                    score += wordOverlapScore(normalizedOriginal, normalizedName)
                    if (result.original_name != null) {
                        score += wordOverlapScore(normalizedOriginal, normalizeTitle(result.original_name))
                    }
                    if (result.original_language == "ja") score += 30
                    score += (result.popularity?.toInt() ?: 0) / 100
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

internal fun AnimeRepository.normalizeTitle(title: String): String = title.lowercase().replace(Regex("[^a-z0-9\\s]"), "").replace(Regex("\\s+"), " ").trim()

internal fun AnimeRepository.detectFormatFromTitle(title: String): String? {
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

internal fun AnimeRepository.extractBaseTitle(title: String): String {
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

