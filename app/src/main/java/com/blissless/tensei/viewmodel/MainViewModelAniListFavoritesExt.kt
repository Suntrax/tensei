package com.blissless.tensei.viewmodel

import androidx.lifecycle.viewModelScope
import com.blissless.tensei.MainViewModel
import com.blissless.tensei.api.myanimelist.LoginProvider
import com.blissless.tensei.data.models.AnimeMedia
import com.blissless.tensei.data.models.MediaCoverImage
import com.blissless.tensei.data.models.MediaTitle
import com.blissless.tensei.data.models.UserFavoriteAnime
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

/**
 * AniList favorites and user-activity logic for [MainViewModel].
 *
 * Extracted from MainViewModel.kt. Handles:
 * - Fetching user activity / stats / favorites from the AniList API
 * - Loading favorites from local storage (offline-first)
 * - Toggling a favorite (delegates to MAL path when logged in via MAL,
 *   otherwise does local-first update + queues a debounced API sync)
 * - Refreshing the latest-episode field for currently-airing anime in the
 *   user's "currently watching" list
 *
 * Public signatures preserved as extension functions.
 */

fun MainViewModel.fetchUserActivity() {
    val userId = _userId.value ?: return
    viewModelScope.launch { repository.fetchUserActivity(userId)?.let { _userActivity.value = it } }
}

fun MainViewModel.fetchUserStats() {
    val userId = _userId.value ?: return
    viewModelScope.launch {
        repository.fetchUserStats(userId)?.let {
            _userStats.value = it.data.User.statistics.anime
        }
    }
}

fun MainViewModel.fetchAniListFavorites() {
    val userId = _userId.value ?: return
    viewModelScope.launch {
        repository.fetchUserFavorites(userId)?.let { response ->
            val apiFavorites = response.data.User.favourites.anime.nodes
            // Merge API favorites with locally stored favorites to preserve offline additions
            val localFavoriteIds = userPreferences.aniListFavorites.value
            val mergedFavorites = apiFavorites.map { apiFav ->
                val isLocalFavorite = localFavoriteIds.contains(apiFav.id)
                if (isLocalFavorite) {
                    // Keep local version which might have more up-to-date info
                    val localFav = _aniListFavorites.value.find { it.id == apiFav.id }
                    localFav ?: apiFav
                } else {
                    apiFav
                }
            }.toMutableList()

            // Add any favorites that were added locally but not on API yet
            localFavoriteIds.forEach { localId ->
                if (mergedFavorites.none { it.id == localId }) {
                    val localOnly = _aniListFavorites.value.find { it.id == localId }
                    if (localOnly != null) {
                        mergedFavorites.add(localOnly)
                    }
                }
            }

            _aniListFavorites.value = mergedFavorites
        }
    }
}

fun MainViewModel.loadAniListFavoritesFromStorage() {
    // Load favorites from UserPreferences (IDs only)
    val favoriteIds = userPreferences.aniListFavorites.value

    if (favoriteIds.isEmpty()) {
        _aniListFavorites.value = emptyList()
        return
    }

    // Convert IDs to UserFavoriteAnime placeholders (will be enriched by detailedAnimeCache if available)
    val favorites = favoriteIds.map { id ->
        val cached = cacheManager.detailedAnimeCache.value[id]
        if (cached != null) {
            UserFavoriteAnime(
                id = cached.id,
                title = MediaTitle(romaji = cached.title, english = cached.titleEnglish),
                coverImage = MediaCoverImage(extraLarge = cached.cover),
                episodes = cached.episodes,
                averageScore = cached.averageScore,
                genres = cached.genres,
                seasonYear = cached.year
            )
        } else {
            // Try to find in currently watching lists
            val allAnime = _currentlyWatching.value + _planningToWatch.value + _completed.value + _onHold.value + _dropped.value
            val anime = allAnime.find { it.id == id }
            if (anime != null) {
                UserFavoriteAnime(
                    id = anime.id,
                    title = MediaTitle(romaji = anime.title, english = anime.titleEnglish),
                    coverImage = MediaCoverImage(extraLarge = anime.cover),
                    episodes = anime.totalEpisodes,
                    averageScore = anime.averageScore,
                    genres = anime.genres,
                    seasonYear = anime.year
                )
            } else {
                UserFavoriteAnime(
                    id = id,
                    title = MediaTitle(romaji = "Loading...", english = null),
                    coverImage = MediaCoverImage(large = "", medium = ""),
                    episodes = null,
                    averageScore = null,
                    genres = emptyList(),
                    seasonYear = null
                )
            }
        }
    }
    _aniListFavorites.value = favorites
}

fun MainViewModel.toggleAniListFavorite(mediaId: Int, anime: AnimeMedia? = null) {
    if (_loginProvider.value == LoginProvider.MAL) {
        // Toggle MAL favorite using the ID-based method
        toggleMalFavoriteById(mediaId)
    } else {
        // Toggle AniList favorite - local-first with persistence
        // Check both in-memory list AND persisted storage to determine current state
        val isFavoriteInMemory = _aniListFavorites.value.any { it.id == mediaId }
        val isFavoriteInStorage = userPreferences.isAniListFavorite(mediaId)
        val isFavorite = isFavoriteInMemory || isFavoriteInStorage
        val willBeAdded = !isFavorite

        // Update persisted storage
        userPreferences.toggleAniListFavorite(mediaId)

        // Update UI list
        if (isFavorite) {
            _aniListFavorites.value = _aniListFavorites.value.filter { it.id != mediaId }
        } else {
            if (anime != null) {
                val userFavorite = UserFavoriteAnime(
                    id = anime.id,
                    title = MediaTitle(romaji = anime.title, english = anime.titleEnglish),
                    coverImage = MediaCoverImage(extraLarge = anime.cover),
                    episodes = anime.totalEpisodes,
                    averageScore = anime.averageScore,
                    genres = anime.genres,
                    seasonYear = anime.year
                )
                _aniListFavorites.value += userFavorite
            } else {
                val cachedAnime = cacheManager.detailedAnimeCache.value[mediaId]
                val placeholder = UserFavoriteAnime(
                    id = mediaId,
                    title = MediaTitle(romaji = cachedAnime?.title ?: "Loading...", english = cachedAnime?.titleEnglish),
                    coverImage = MediaCoverImage(extraLarge = cachedAnime?.cover ?: ""),
                    episodes = cachedAnime?.episodes,
                    averageScore = cachedAnime?.averageScore,
                    genres = cachedAnime?.genres ?: emptyList(),
                    seasonYear = cachedAnime?.year
                )
                _aniListFavorites.value += placeholder
            }
        }

        // Queue the API call for debounced sync with the desired state
        queueSync(mediaId, "favorite", favoriteAdded = willBeAdded)
    }
}

// ============================================
// PREFETCHING - Streams for adjacent episodes
// ============================================

internal suspend fun MainViewModel.refreshReleasingAnimeProgress() {
    if (_loginProvider.value == LoginProvider.MAL) {
        return
    }

    val releasing = _currentlyWatching.value.filter { it.status == "RELEASING" }
    if (releasing.isEmpty()) return
    releasing.chunked(3).forEach { chunk ->
        chunk.map { anime ->
            viewModelScope.async {
                repository.fetchDetailedAnime(anime.id)?.let { media ->
                    val newLatestEpisode = media.nextAiringEpisode?.episode?.let { it - 1 }
                    if (newLatestEpisode != anime.latestEpisode) return@async anime.copy(latestEpisode = newLatestEpisode)
                }
                null
            }
        }.awaitAll().filterNotNull().forEach { updated ->
            _currentlyWatching.value = _currentlyWatching.value.map { if (it.id == updated.id) updated else it }
        }
    }
    saveHomeDataToCache()
}
