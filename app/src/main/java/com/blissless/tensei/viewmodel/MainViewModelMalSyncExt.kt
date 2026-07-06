package com.blissless.tensei.viewmodel

import androidx.lifecycle.viewModelScope
import com.blissless.tensei.MainViewModel
import com.blissless.tensei.api.myanimelist.LoginProvider
import com.blissless.tensei.data.models.AnimeMedia
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * MAL (MyAnimeList) sync logic for [MainViewModel].
 *
 * Extracted from MainViewModel.kt. Handles:
 * - The API retry loop (re-fetches explore data when API error + online)
 * - The debounced AniList/MAL sync queue (status, progress, score, delete, favorite)
 * - Status mapping between AniList format (CURRENT, PLANNING, …) and MAL format
 *   (watching, plan_to_watch, …)
 * - Fetching the MAL anime list and reconciling it with the cached detailed anime
 * - Toggling MAL favorites locally
 *
 * Public signatures preserved as extension functions; private helpers are
 * marked `internal` so they can be called across the file boundary while
 * still being hidden from outside the module.
 */

// ─── API retry loop ─────────────────────────────────────────────────────────

fun MainViewModel.startApiRetryLoop() {
    apiRetryJob?.cancel()
    apiRetryJob = viewModelScope.launch {
        while (true) {
            delay(MainViewModel.MIN_REFRESH_INTERVAL_MS.milliseconds)
            if (!_isOffline.value && _apiError.value != null) {
                fetchExploreData(force = true)
            }
        }
    }
}

// ─── Debounced sync queue ───────────────────────────────────────────────────

fun MainViewModel.queueSync(
    mediaId: Int,
    type: String,
    malId: Int? = null,
    status: String? = null,
    progress: Int? = null,
    score: Int? = null,
    entryId: Int? = null,
    favoriteAdded: Boolean? = null,
) {
    val existingSync = pendingSyncs[mediaId]
    val resolvedMalId = malId ?: existingSync?.malId ?: cacheManager.detailedAnimeCache.value[mediaId]?.malId

    pendingSyncs[mediaId] = MainViewModel.PendingSync(
        type = type,
        mediaId = mediaId,
        malId = resolvedMalId,
        status = status ?: existingSync?.status,
        progress = progress ?: existingSync?.progress,
        score = score ?: existingSync?.score,
        entryId = entryId ?: existingSync?.entryId,
        favoriteAdded = favoriteAdded ?: existingSync?.favoriteAdded,
    )

    if (type == "favorite") {
        // Favorites use a separate 1-second debounce that resets on each toggle
        favoriteSyncJob?.cancel()
        favoriteSyncJob = viewModelScope.launch {
            delay(MainViewModel.FAVORITE_DEBOUNCE_MS.milliseconds)
            executeFavoriteSyncs()
        }
    } else {
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            delay(MainViewModel.SYNC_DEBOUNCE_MS.milliseconds)
            executePendingSyncs()
        }
    }
}

internal suspend fun MainViewModel.executeFavoriteSyncs() {
    // Only execute pending favorite syncs (not other types)
    val favoriteSyncs = pendingSyncs.filter { it.value.type == "favorite" }.toMap()
    // Remove only the favorite entries from pending
    favoriteSyncs.keys.forEach { pendingSyncs.remove(it) }

    for ((_, sync) in favoriteSyncs) {
        val shouldBeFavorited = sync.favoriteAdded == true
        try {
            if (shouldBeFavorited) {
                repository.addAniListFavorite(sync.mediaId)
            } else {
                repository.removeAniListFavorite(sync.mediaId)
            }
        } catch (_: Exception) {
            // Re-queue the failed sync so it retries
            pendingSyncs[sync.mediaId] = sync
        }
    }
}

internal suspend fun MainViewModel.executePendingSyncs() {
    // Only process non-favorite syncs (favorites have their own debounce path)
    val syncsToExecute = pendingSyncs.filter { it.value.type != "favorite" }.toMap()
    syncsToExecute.keys.forEach { pendingSyncs.remove(it) }

    for ((_, sync) in syncsToExecute) {
        when (sync.type) {
            "status" -> {
                sync.status?.let {
                    if (_loginProvider.value == LoginProvider.MAL) {
                        val malId = sync.malId
                        if (malId != null) {
                            val malStatus = mapToMalStatus(it)
                            if (malStatus != null) {
                                malApiService.updateAnimeStatus(malId, malStatus, sync.score, sync.progress)
                            }
                        }
                    } else {
                        repository.updateStatus(sync.mediaId, it, sync.progress)
                    }
                }
            }
            "progress" -> {
                sync.progress?.let {
                    if (_loginProvider.value == LoginProvider.MAL) {
                        val malId = sync.malId
                        if (malId != null) {
                            malApiService.updateAnimeStatus(malId, null, null, it)
                        }
                    } else {
                        repository.updateProgress(sync.mediaId, it)
                    }
                }
            }
            "score" -> {
                sync.score?.let {
                    if (_loginProvider.value == LoginProvider.MAL) {
                        val malId = sync.malId
                        if (malId != null) {
                            malApiService.updateAnimeStatus(malId, null, it, null)
                        }
                    } else {
                        repository.updateScore(sync.mediaId, it)
                    }
                }
            }
            "delete" -> {
                sync.entryId?.let {
                    if (_loginProvider.value == LoginProvider.MAL) {
                        val malId = sync.malId
                        if (malId != null) {
                            malApiService.deleteAnimeFromList(malId)
                        }
                    } else {
                        repository.deleteListEntry(it)
                    }
                }
            }
        }
    }

    if (syncsToExecute.isNotEmpty()) {
        if (_loginProvider.value == LoginProvider.MAL) {
            fetchMalList()
        } else {
            fetchLists()
        }
    }
}

// ─── Status mapping (AniList <-> MAL) ───────────────────────────────────────

internal fun MainViewModel.mapToMalStatus(status: String): String? {
    return when (status) {
        "CURRENT" -> "watching"
        "PLANNING" -> "plan_to_watch"
        "COMPLETED" -> "completed"
        "PAUSED" -> "on_hold"
        "DROPPED" -> "dropped"
        else -> null
    }
}

internal fun MainViewModel.mapFromMalStatus(malStatus: String?): String {
    return when (malStatus) {
        "watching" -> "CURRENT"
        "plan_to_watch" -> "PLANNING"
        "completed" -> "COMPLETED"
        "on_hold" -> "PAUSED"
        "dropped" -> "DROPPED"
        else -> "PLANNING"
    }
}

// ─── MAL list fetch & reconciliation ────────────────────────────────────────

internal suspend fun MainViewModel.fetchMalList() {
    if (_loginProvider.value != LoginProvider.MAL) return

    val entries = malApiService.getAnimeList()

    val currentlyWatching = mutableListOf<AnimeMedia>()
    val planningToWatch = mutableListOf<AnimeMedia>()
    val completed = mutableListOf<AnimeMedia>()
    val onHold = mutableListOf<AnimeMedia>()
    val dropped = mutableListOf<AnimeMedia>()

    entries.forEach { entry ->
        val malId = entry.node.id
        val status = entry.list_status?.status
        val progress = entry.list_status?.num_episodes_watched ?: 0

        // Find matching anime from cache by MAL ID
        val cachedAnime = cacheManager.detailedAnimeCache.value.values.find {
            it.malId == malId || it.id == malId
        }

        val anime = if (cachedAnime != null) {
            AnimeMedia(
                id = cachedAnime.id,
                title = cachedAnime.title,
                titleEnglish = cachedAnime.titleEnglish,
                cover = cachedAnime.cover,
                banner = cachedAnime.banner,
                progress = progress,
                totalEpisodes = cachedAnime.episodes,
                latestEpisode = cachedAnime.latestEpisode ?: cachedAnime.nextAiringEpisode?.let { it - 1 },
                status = cachedAnime.status ?: "",
                averageScore = cachedAnime.averageScore,
                genres = cachedAnime.genres,
                listStatus = mapFromMalStatus(status),
                malId = malId,
                format = cachedAnime.format
            )
        } else {
            AnimeMedia(
                id = malId,
                title = entry.node.title,
                titleEnglish = entry.node.alternative_titles?.en ?: entry.node.title,
                cover = entry.node.main_picture?.large ?: entry.node.main_picture?.medium ?: "",
                progress = progress,
                totalEpisodes = entry.node.num_episodes,
                listStatus = mapFromMalStatus(status),
                malId = malId
            )
        }

        when (mapFromMalStatus(status)) {
            "CURRENT" -> currentlyWatching.add(anime)
            "PLANNING" -> planningToWatch.add(anime)
            "COMPLETED" -> completed.add(anime)
            "PAUSED" -> onHold.add(anime)
            "DROPPED" -> dropped.add(anime)
            else -> planningToWatch.add(anime) // Default to planning for unknown status
        }
    }

    _currentlyWatching.value = currentlyWatching.sortedByDescending { it.averageScore ?: 0 }
    _planningToWatch.value = planningToWatch.sortedByDescending { it.averageScore ?: 0 }
    _completed.value = completed.sortedByDescending { it.averageScore ?: 0 }
    _onHold.value = onHold.sortedByDescending { it.averageScore ?: 0 }
    _dropped.value = dropped.sortedByDescending { it.averageScore ?: 0 }

    loadMalFavoritesFromCache()
}

internal fun MainViewModel.toggleMalFavoriteById(mediaId: Int) {
    val currentFavorites = _malFavorites.value.toMutableList()
    val existingIndex = currentFavorites.indexOfFirst { it.id == mediaId || it.malId == mediaId }
    if (existingIndex >= 0) {
        currentFavorites.removeAt(existingIndex)
    } else {
        // Try to find the anime in existing lists
        val allAnime = _currentlyWatching.value + _planningToWatch.value + _completed.value + _onHold.value + _dropped.value
        val anime = allAnime.find { it.id == mediaId || it.malId == mediaId }
        if (anime != null) {
            currentFavorites.add(anime)
        }
    }
    _malFavorites.value = currentFavorites
    userPreferences.saveMalFavorites(currentFavorites.map { it.id })
}

internal fun MainViewModel.loadMalFavoritesFromCache() {
    val favoriteIds = userPreferences.getMalFavorites()
    val allAnime = _currentlyWatching.value + _planningToWatch.value + _completed.value + _onHold.value + _dropped.value
    val favoriteAnimeList = mutableListOf<AnimeMedia>()
    for (id in favoriteIds) {
        val anime = allAnime.find { it.id == id || it.malId == id }
        if (anime != null) {
            favoriteAnimeList.add(anime)
        }
    }
    _malFavorites.value = favoriteAnimeList
}
