package com.blissless.tensei.viewmodel

import com.blissless.tensei.MainViewModel
import com.blissless.tensei.data.models.MangaMedia
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Direction of the one-time cross-provider copy offered on first simultaneous login. */
data class CrossProviderCopyPrompt(
    val visible: Boolean
)

/**
 * Frozen prompt for the one-time cross-provider copy. When [visible] is true, the UI shows an
 * overlay dialog asking the user to pick a copy direction. Null until both providers are logged in.
 */
private val _crossProviderCopyPrompt = MutableStateFlow<CrossProviderCopyPrompt?>(null)
val MainViewModel.crossProviderCopyPrompt: StateFlow<CrossProviderCopyPrompt?>
    get() = _crossProviderCopyPrompt.asStateFlow()

/** Lightweight (status, score, progress) snapshot of a user's list entry on one provider. */
private data class ListSnapshot(
    val status: String?,
    val score: Int,
    val progress: Int
)

/** Live progress of a running cross-provider copy. [isRunning] false means idle. */
data class CrossProviderCopyProgress(
    val isRunning: Boolean = false,
    val text: String = "",
    val processed: Int = 0,
    val total: Int = 0
)

private val _crossProviderCopyProgress = MutableStateFlow<CrossProviderCopyProgress>(CrossProviderCopyProgress())
val MainViewModel.crossProviderCopyProgress: StateFlow<CrossProviderCopyProgress>
    get() = _crossProviderCopyProgress.asStateFlow()

private const val SYNC_NOTIFICATION_CHANNEL = "cross_provider_sync"
private const val SYNC_NOTIFICATION_ID = 2002

private fun ensureSyncNotificationChannel(context: Context) {
    try {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(SYNC_NOTIFICATION_CHANNEL, "Cross-provider sync", NotificationManager.IMPORTANCE_DEFAULT)
        )
    } catch (_: Exception) {
    }
}

@SuppressLint("MissingPermission")
private fun showSyncProgressNotification(context: Context, text: String, processed: Int, total: Int) {
    try {
        if (!hasNotificationPermission(context)) return
        ensureSyncNotificationChannel(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val indeterminate = total <= 0
        val notification = NotificationCompat.Builder(context, SYNC_NOTIFICATION_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Syncing across providers")
            .setContentText(if (indeterminate) text else "$text  ($processed / $total)")
            .setProgress(if (indeterminate) 100 else total, if (indeterminate) 0 else processed, indeterminate)
            .setOngoing(true)
            .setSilent(true)
            .build()
        nm.notify(SYNC_NOTIFICATION_ID, notification)
    } catch (_: Exception) {
    }
}

@SuppressLint("MissingPermission")
private fun showSyncCompleteNotification(context: Context, text: String) {
    try {
        if (!hasNotificationPermission(context)) return
        ensureSyncNotificationChannel(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(SYNC_NOTIFICATION_ID)
        val notification = NotificationCompat.Builder(context, SYNC_NOTIFICATION_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Sync complete")
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        nm.notify(SYNC_NOTIFICATION_ID, notification)
    } catch (_: Exception) {
    }
}

private fun cancelSyncNotification(context: Context) {
    try {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(SYNC_NOTIFICATION_ID)
    } catch (_: Exception) {
    }
}

private fun hasNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ActivityCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

/**
 * Offer the one-time copy when BOTH providers are logged in. Called after login callbacks.
 * Shows the overlay prompt once (guarded by a persisted flag) so the user can pick a direction,
 * or skip. After the copy, ongoing diff-sync keeps the two providers reconciled.
 */
fun MainViewModel.offerCrossProviderSync() {
    if (!isBothActive) return
    if (userPreferences.isCrossProviderCopyDone()) {
        // One-time copy already handled — ensure ongoing diff-sync is running.
        viewModelScope.launch { runCrossProviderDiffSync() }
        return
    }
    _crossProviderCopyPrompt.value = CrossProviderCopyPrompt(visible = true)
}

/**
 * Force-show the copy dialog, bypassing the one-time flag. Used by the manual "Sync Now"
 * button in Settings when both providers are active.
 */
fun MainViewModel.showCrossProviderCopyDialog() {
    if (!isBothActive) return
    _crossProviderCopyPrompt.value = CrossProviderCopyPrompt(visible = true)
}

/** Dismiss the copy prompt without performing a copy. */
fun MainViewModel.dismissCrossProviderCopyPrompt() {
    _crossProviderCopyPrompt.value = CrossProviderCopyPrompt(visible = false)
    // Mark done so we don't keep nagging; ongoing diff-sync still reconciles new changes.
    userPreferences.setCrossProviderCopyDone(true)
}

/**
 * Apply the chosen copy direction and run the one-time copy for anime + manga.
 * @param toMal true = copy AniList â†’ MAL; false = copy MAL â†’ AniList.
 */
fun MainViewModel.applyCrossProviderCopy(toMal: Boolean) {
    _crossProviderCopyPrompt.value = CrossProviderCopyPrompt(visible = false)
    userPreferences.setCrossProviderCopyDone(true)
    viewModelScope.launch(Dispatchers.IO) {
        android.util.Log.d("CrossSync", "applyCrossProviderCopy: direction=${if (toMal) "AniList->MAL" else "MAL->AniList"} " +
            "aniListActive=$isAniListActive malActive=$isMalActive")
        val startMessage = if (toMal) "Copying AniList → MAL…" else "Copying MAL → AniList…"
        _crossProviderCopyProgress.value = CrossProviderCopyProgress(
            isRunning = true,
            text = startMessage,
            processed = 0,
            total = 0
        )
        showSyncProgressNotification(context, startMessage, 0, 0)
        withContext(Dispatchers.Main.immediate) {
            viewModelScope.launch { _toastMessage.emit(startMessage) }
        }
        var pushed = 0
        var skipped = 0
        try {
            if (toMal) {
                val r = copyAniListToMal()
                pushed = r.first
                skipped = r.second
            } else {
                val r = copyMalToAniList()
                pushed = r.first
                skipped = r.second
            }
        } catch (e: Exception) {
            // Individual item failures are handled internally; swallow structural errors.
            android.util.Log.w("CrossSync", "cross-provider copy failed: ${e.message}", e)
        }
        _crossProviderCopyProgress.value = CrossProviderCopyProgress(
            isRunning = false,
            text = "",
            processed = pushed,
            total = pushed + skipped
        )
        cancelSyncNotification(context)
        if (pushed > 0) {
            val doneMessage = if (toMal) "Copied $pushed items from AniList to MAL" else "Copied $pushed items from MAL to AniList"
            showSyncCompleteNotification(context, doneMessage)
            withContext(Dispatchers.Main.immediate) {
                android.util.Log.d("CrossSync", "cross-provider copy complete (toMal=$toMal)")
                viewModelScope.launch { _toastMessage.emit(doneMessage) }
            }
        } else {
            android.util.Log.d("CrossSync", "cross-provider copy finished: nothing changed (pushed=$pushed skipped=$skipped), no notification shown")
        }
    }
}

/**
 * Directional auto-sync on app startup. Unlike [applyCrossProviderCopy], this does not mark the
 * one-time copy as done or dismiss the copy prompt; it simply pushes the source provider's list
 * onto the target provider in the chosen direction.
 */
internal fun MainViewModel.runCrossProviderStartupSync(toMal: Boolean) {
    viewModelScope.launch(Dispatchers.IO) {
        android.util.Log.d("CrossSync", "runCrossProviderStartupSync: direction=${if (toMal) "AniList->MAL" else "MAL->AniList"} " +
            "aniListActive=$isAniListActive malActive=$isMalActive")
        val startMessage = if (toMal) "Auto-syncing AniList → MAL…" else "Auto-syncing MAL → AniList…"
        _crossProviderCopyProgress.value = CrossProviderCopyProgress(
            isRunning = true,
            text = startMessage,
            processed = 0,
            total = 0
        )
        showSyncProgressNotification(context, startMessage, 0, 0)
        withContext(Dispatchers.Main.immediate) {
            viewModelScope.launch { _toastMessage.emit(startMessage) }
        }
        var pushed = 0
        var skipped = 0
        try {
            val result = if (toMal) copyAniListToMal() else copyMalToAniList()
            pushed = result.first
            skipped = result.second
        } catch (e: Exception) {
            android.util.Log.w("CrossSync", "startup sync failed: ${e.message}", e)
        }
        _crossProviderCopyProgress.value = CrossProviderCopyProgress(
            isRunning = false,
            text = "",
            processed = pushed,
            total = pushed + skipped
        )
        cancelSyncNotification(context)
        if (pushed > 0) {
            val doneMessage = if (toMal) "AniList → MAL sync complete ($pushed items)" else "MAL → AniList sync complete ($pushed items)"
            showSyncCompleteNotification(context, doneMessage)
            withContext(Dispatchers.Main.immediate) {
                viewModelScope.launch { _toastMessage.emit(doneMessage) }
            }
        } else {
            android.util.Log.d("CrossSync", "startup sync finished: nothing changed (pushed=$pushed skipped=$skipped), no notification shown")
        }
    }
}

// â”€â”€â”€ One-time copy: AniList â†’ MAL â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

private suspend fun MainViewModel.copyAniListToMal(): Pair<Int, Int> {
    val tag = "CrossSync"
    // Anime
    val allAnime = _currentlyWatching.value + _planningToWatch.value +
        _completed.value + _onHold.value + _dropped.value
    val allManga = fetchAllAniListManga()
    val total = allAnime.size + allManga.size
    android.util.Log.d(tag, "copyAniListToMal: anime entries=${allAnime.size}, " +
        "animeListsLoaded=${_currentlyWatching.value.size + _planningToWatch.value.size + _completed.value.size + _onHold.value.size + _dropped.value.size} manga=${allManga.size}")
    var processed = 0
    var pushed = 0
    var skipped = 0
    val updateProgress: suspend () -> Unit = {
        _crossProviderCopyProgress.value = CrossProviderCopyProgress(
            isRunning = true,
            text = "Copying AniList → MAL…",
            processed = processed,
            total = total
        )
        showSyncProgressNotification(context, "Copying AniList → MAL…", processed, total)
    }

    // Pull MAL's current anime/manga lists (website load.json, falling back to /v2) so we only
    // overwrite entries whose status/score/progress differ, or that are new on MAL.
    val malAnimeMap = try {
        malApiService.getAnimeListWithWeb().associate { it.node.id to it }
    } catch (e: Exception) {
        android.util.Log.w(tag, "copyAniListToMal: getAnimeListWithWeb failed: ${e.message}", e)
        emptyMap()
    }
    val malMangaMap = try {
        malApiService.getMangaListWithWeb().associate { it.node.id to it }
    } catch (e: Exception) {
        android.util.Log.w(tag, "copyAniListToMal: getMangaListWithWeb failed: ${e.message}", e)
        emptyMap()
    }

    for (anime in allAnime) {
        processed++
        updateProgress()
        val malId = anime.malId
        if (malId == null) { skipped++; continue }
        val malStatus = mapToMalStatus(anime.listStatus)
        val score = anime.userScore
        val mal = malAnimeMap[malId]
        val isNew = mal == null
        val differs = isNew ||
            mal?.list_status?.status != malStatus ||
            (mal?.list_status?.score ?: 0) != (score ?: 0) ||
            (mal?.list_status?.num_episodes_watched ?: 0) != anime.progress
        if (!differs) {
            android.util.Log.d(tag, "copyAniListToMal anime UNCHANGED (skip): malId=$malId title=${anime.title}")
            skipped++
            continue
        }
        android.util.Log.d(tag, "copyAniListToMal anime${if (isNew) " (new)" else ""}: title=${anime.title} malId=$malId " +
            "AL[status=${anime.listStatus}->mal=$malStatus score=$score progress=${anime.progress}] " +
            "MAL[status=${mal?.list_status?.status} score=${mal?.list_status?.score} progress=${mal?.list_status?.num_episodes_watched}] differs=$differs")
        try {
            val updated = malApiService.updateAnimeStatus(malId, malStatus, score, anime.progress)
            if (updated) {
                pushed++
            } else {
                android.util.Log.w(tag, "copyAniListToMal anime REJECTED by MAL malId=$malId title=${anime.title}")
                skipped++
            }
        } catch (e: Exception) {
            android.util.Log.w(tag, "copyAniListToMal FAILED anime malId=$malId title=${anime.title}: ${e.message}", e)
            skipped++
        }
        delay(1000)
    }
    android.util.Log.d(tag, "copyAniListToMal: anime done pushed=$pushed skipped=$skipped")

    // Manga - use the AniList manga list directly (it carries the manga's idMal), because the
    // local-tracking flows lose `malId` when they are rebuilt from the tracking store.
    android.util.Log.d(tag, "copyAniListToMal: manga entries=${allManga.size}")
    for (manga in allManga) {
        processed++
        updateProgress()
        val malMangaId = manga.malId
        if (malMangaId == null) { skipped++; continue }
        val malStatus = mapMangaStatusToMal(manga.listStatus)
        // Scores are kept on AniList's 0-10 scale and passed through as-is, matching the
        // anime path. (AniList's GraphQL `score` returns the per-account format; for a
        // 10-point account a 10/10 is `10`, not `100`. The previous `/10` here halved
        // every rating, pushing e.g. 10 -> 1 onto MAL.)
        val malScore = manga.userScore?.coerceIn(0, 10)
        val mal = malMangaMap[malMangaId]
        val isNew = mal == null
        val differs = isNew ||
            mal?.list_status?.status != malStatus ||
            (mal?.list_status?.score ?: 0) != (malScore ?: 0) ||
            (mal?.list_status?.num_chapters_read ?: 0) != manga.progress
        if (!differs) {
            android.util.Log.d(tag, "copyAniListToMal manga UNCHANGED (skip): malId=$malMangaId title=${manga.title}")
            skipped++
            continue
        }
        android.util.Log.d(tag, "copyAniListToMal manga${if (isNew) " (new)" else ""}: title=${manga.title} malId=$malMangaId " +
            "AL[status=${manga.listStatus}->mal=$malStatus score=$malScore progress=${manga.progress}] " +
            "MAL[status=${mal?.list_status?.status} score=${mal?.list_status?.score} chapters=${mal?.list_status?.num_chapters_read}] differs=$differs")
        try {
            val updated = malApiService.updateMangaStatus(
                malMangaId,
                malStatus,
                malScore,
                manga.progress
            )
            if (updated) {
                pushed++
            } else {
                android.util.Log.w(tag, "copyAniListToMal manga REJECTED by MAL malId=$malMangaId title=${manga.title}")
                skipped++
            }
        } catch (e: Exception) {
            android.util.Log.w(tag, "copyAniListToMal FAILED manga malId=$malMangaId title=${manga.title}: ${e.message}", e)
            skipped++
        }
        delay(1000)
    }
    android.util.Log.d(tag, "copyAniListToMal: manga done pushed=$pushed skipped=$skipped")
    return pushed to skipped
}

/**
 * Fetch every AniList manga list entry (across all statuses) WITH its `malId` populated.
 * Unlike the local-tracking flows (which drop `malId` when rebuilt from the tracker), the
 * AniList list response carries idMal, so this is the correct source of truth for the
 * cross-provider manga copy/diff-sync. Merges the status-keyed map into a single list.
 */
private suspend fun MainViewModel.fetchAllAniListManga(): List<MangaMedia> {
    val userId = _userId.value ?: return emptyList()
    val token = authToken.value ?: return emptyList()
    val lists = mangaRepository?.fetchUserMangaLists(userId, token) ?: return emptyList()
    val current = lists["CURRENT"] ?: lists["Reading"] ?: emptyList()
    val planning = lists["PLANNING"] ?: lists["Plan to Read"] ?: emptyList()
    val completed = lists["COMPLETED"] ?: emptyList()
    val paused = lists["PAUSED"] ?: emptyList()
    val dropped = lists["DROPPED"] ?: emptyList()
    android.util.Log.d("CrossSync", "fetchAllAniListManga: current=${current.size} planning=${planning.size} " +
        "completed=${completed.size} paused=${paused.size} dropped=${dropped.size}")
    return current + planning + completed + paused + dropped
}

// â”€â”€â”€ One-time copy: MAL â†’ AniList â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

private suspend fun MainViewModel.copyMalToAniList(): Pair<Int, Int> {
    val tag = "CrossSync"
    val token = authToken.value
    if (token == null) {
        android.util.Log.w(tag, "copyMalToAniList: no AniList token, aborting")
        return 0 to 0
    }

    // Anime: pull MAL list and push each matching entry to AniList.
    val malAnime = try {
        malApiService.getAnimeListWithWeb()
    } catch (e: Exception) {
        android.util.Log.w(tag, "copyMalToAniList: getAnimeList failed: ${e.message}", e)
        return 0 to 0
    }
    val malManga = try {
        malApiService.getMangaListWithWeb()
    } catch (e: Exception) {
        android.util.Log.w(tag, "copyMalToAniList: getMangaList failed: ${e.message}", e)
        return 0 to 0
    }
    val total = malAnime.size + malManga.size
    android.util.Log.d(tag, "copyMalToAniList: anime entries=${malAnime.size} manga entries=${malManga.size}")
    var processed = 0
    var pushed = 0
    var skipped = 0
    val updateProgress: suspend () -> Unit = {
        _crossProviderCopyProgress.value = CrossProviderCopyProgress(
            isRunning = true,
            text = "Copying MAL → AniList…",
            processed = processed,
            total = total
        )
        showSyncProgressNotification(context, "Copying MAL → AniList…", processed, total)
    }
    for (entry in malAnime) {
        processed++
        updateProgress()
        val malId = entry.node.id
        val status = mapFromMalStatus(entry.list_status?.status)
        val progress = entry.list_status?.num_episodes_watched ?: 0
        val score = entry.list_status?.score
        android.util.Log.d(tag, "copyMalToAniList anime: malId=$malId title=${entry.node.title} " +
            "status=$status progress=$progress score=$score")

        val animeId = resolveAnimeIdForMal(malId)
        if (animeId == null) {
            android.util.Log.d(tag, "copyMalToAniList: no AniList id resolved for malId=$malId, skipping")
            skipped++
            continue
        }
        if (status != "PLANNING" || progress > 0 || (score != null && score > 0)) {
            try {
                queueSync(animeId, "status", malId = malId, status = status, progress = progress, score = score)
                pushed++
            } catch (e: Exception) {
                android.util.Log.w(tag, "copyMalToAniList FAILED queued anime malId=$malId: ${e.message}", e)
                skipped++
            }
        } else {
            skipped++
        }
        delay(1000)
    }
    android.util.Log.d(tag, "copyMalToAniList: anime done pushed=$pushed skipped=$skipped")

    // Manga: push each resolvable MAL manga entry to AniList.
    try {
        fetchMalMangaList()
    } catch (e: Exception) {
        android.util.Log.w(tag, "copyMalToAniList: fetchMalMangaList failed: ${e.message}", e)
        return pushed to skipped
    }
    val mangaRepository = this.mangaRepository
    if (mangaRepository == null) {
        android.util.Log.w(tag, "copyMalToAniList: mangaRepository null, skipping manga")
        return pushed to skipped
    }
    for (entry in malManga) {
        processed++
        updateProgress()
        val malId = entry.node.id
        val anilistId = resolveMangaIdForMal(malId)
        val anilistStatus = mapMangaStatusFromMal(entry.list_status?.status)
        val progress = entry.list_status?.num_chapters_read ?: 0
        val score = entry.list_status?.score?.takeIf { it > 0 }
        if (anilistId == null) {
            android.util.Log.d(tag, "copyMalToAniList manga: no AniList id resolved for malId=$malId, skipping")
            skipped++
            continue
        }
        android.util.Log.d(tag, "copyMalToAniList manga push: anilistId=$anilistId malId=$malId " +
            "status=$anilistStatus progress=$progress score=$score")
        try {
            mangaRepository.updateMangaStatus(
                anilistId, anilistStatus, token,
                progress = progress.takeIf { it > 0 },
                score = score
            )
            pushed++
        } catch (e: Exception) {
            android.util.Log.w(tag, "copyMalToAniList FAILED manga anilistId=$anilistId malId=$malId: ${e.message}", e)
            skipped++
        }
        delay(1000)
    }
    android.util.Log.d(tag, "copyMalToAniList: manga done pushed=$pushed skipped=$skipped")
    return pushed to skipped
}

/** Map a MAL anime id â†’ matching AniList anime id using cached detail, falling back to the id itself. */
private suspend fun MainViewModel.resolveAnimeIdForMal(malId: Int): Int? {
    cacheManager.detailedAnimeCache.value.values.firstOrNull { it.malId == malId }?.id?.let { return it }
    val inLists = _currentlyWatching.value + _planningToWatch.value +
        _completed.value + _onHold.value + _dropped.value
    val found = inLists.firstOrNull { it.id == malId || it.malId == malId }
    android.util.Log.d("CrossSync", "resolveAnimeIdForMal: malId=$malId cached=${cacheManager.detailedAnimeCache.value.size} " +
        "inListCount=${inLists.size} -> ${found?.id} (id=${found?.id} malId=${found?.malId})")
    return found?.id
}

// â”€â”€â”€ Ongoing diff-sync â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

/**
 * Reconcile diverged entries between AniList and MAL. Called on app start (when both active)
 * and on manual "Sync now" from settings. Uses AniList as the source of truth (home prefers it)
 * and pushes the AniList value to MAL whenever the two diverge. Individual failures are skipped
 * and retried on the next run.
 */
internal suspend fun MainViewModel.runCrossProviderDiffSync() {
    if (!isBothActive) return

    // Anime
    val malByAnimeId = mutableMapOf<Int, ListSnapshot>()
    malApiService.getAnimeListWithWeb().forEach { e ->
        malByAnimeId[e.node.id] = ListSnapshot(
            status = e.list_status?.status,
            score = e.list_status?.score ?: 0,
            progress = e.list_status?.num_episodes_watched ?: 0
        )
    }
    val allAnime = _currentlyWatching.value + _planningToWatch.value +
        _completed.value + _onHold.value + _dropped.value
    for (anime in allAnime) {
        val malId = anime.malId ?: continue
        val mal = malByAnimeId[malId]
        // Push if missing on MAL (new entry) or any field differs.
        val differs = mal == null ||
            mal.status != mapToMalStatus(anime.listStatus) ||
            mal.score != (anime.userScore ?: 0) ||
            mal.progress != anime.progress
        if (differs) {
            android.util.Log.d("CrossSync",
                "diff-sync anime${if (mal == null) " (new)" else ""}: malId=$malId title=${anime.title} " +
                    "status=${mapToMalStatus(anime.listStatus)} score=${anime.userScore} progress=${anime.progress}")
            malApiService.updateAnimeStatus(malId, mapToMalStatus(anime.listStatus), anime.userScore, anime.progress)
            delay(1000)
        }
    }

    // Manga
    val malMangaByMalId = mutableMapOf<Int, ListSnapshot>()
    malApiService.getMangaListWithWeb().forEach { e ->
        malMangaByMalId[e.node.id] = ListSnapshot(
            status = e.list_status?.status,
            score = e.list_status?.score ?: 0,
            progress = e.list_status?.num_chapters_read ?: 0
        )
    }
    val allManga = fetchAllAniListManga()
    for (manga in allManga) {
        val malId = manga.malId ?: continue
        val mal = malMangaByMalId[malId]
        // 0-10 scale, passed through as-is (matches anime path; see copyAniListToMal).
        val anilistScore = (manga.userScore ?: 0).coerceIn(0, 10)
        val differs = mal == null ||
            mal.status != mapMangaStatusToMal(manga.listStatus) ||
            mal.score != anilistScore ||
            mal.progress != manga.progress
        if (differs) {
            android.util.Log.d("CrossSync",
                "diff-sync manga${if (mal == null) " (new)" else ""}: malId=$malId title=${manga.title}")
            malApiService.updateMangaStatus(
                malId,
                mapMangaStatusToMal(manga.listStatus),
                anilistScore.coerceIn(0, 10),
                manga.progress
            )
            delay(1000)
        }
    }
    android.util.Log.d("CrossSync", "cross-provider diff-sync complete")
}

/** Bridge so the (otherwise private) manga queue is reachable from MainActivity's scope when needed. */
internal fun MainViewModel.triggerCrossProviderDiffSync() {
    viewModelScope.launch { runCrossProviderDiffSync() }
}
