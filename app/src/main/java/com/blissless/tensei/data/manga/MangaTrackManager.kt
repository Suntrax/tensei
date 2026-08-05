package com.blissless.tensei.data.manga

import android.content.Context
import android.content.SharedPreferences
import com.blissless.tensei.data.models.MangaChapter
import com.blissless.tensei.data.models.MangaTrack
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class MangaTrackManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("manga_tracking", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun getAllTracking(): List<MangaTrack> = getTracks()

    fun getContinueReading(): List<MangaTrack> =
        getTracks()
            .filter { it.status == "CURRENT" && (it.scrollProgress > 0f || it.currentChapterPages > 0) }
            .sortedByDescending { it.progress }

    /** Every CURRENT-status track — the source for the home "Currently Reading" row. */
    fun getCurrentlyReading(): List<MangaTrack> =
        getTracks().filter { it.status == "CURRENT" }.sortedByDescending { it.progress }

    fun getPlanningToRead(): List<MangaTrack> =
        getTracks().filter { it.status == "PLANNING" }

    fun getCompleted(): List<MangaTrack> =
        getTracks().filter { it.status == "COMPLETED" }

    fun getDropped(): List<MangaTrack> =
        getTracks().filter { it.status == "DROPPED" }

    fun getPaused(): List<MangaTrack> =
        getTracks().filter { it.status == "PAUSED" }

    fun getTrack(mangaId: Int): MangaTrack? = getTracks().find { it.mangaId == mangaId }

    fun addTrack(track: MangaTrack) {
        val tracks = getTracks().toMutableList()
        tracks.removeAll { it.mangaId == track.mangaId }
        tracks.add(track)
        saveTracks(tracks)
    }

    fun removeTrack(mangaId: Int) {
        val tracks = getTracks().toMutableList()
        tracks.removeAll { it.mangaId == mangaId }
        saveTracks(tracks)
    }

    fun markAsReading(mangaId: Int) {
        updateTrackingStatus(mangaId, "CURRENT")
    }

    fun markAsPlanning(mangaId: Int) {
        updateTrackingStatus(mangaId, "PLANNING")
    }

    fun markChapterComplete(mangaId: Int, chapter: MangaChapter) {
        android.util.Log.d("MangaSyncDebug", "track.markChapterComplete mangaId=$mangaId chapterNumber=${chapter.chapterNumber}")
        val tracks = getTracks().toMutableList()
        val index = tracks.indexOfFirst { it.mangaId == mangaId }
        val newProgress = if (chapter.chapterNumber > 0f) chapter.chapterNumber else 1f
        if (index >= 0) {
            val track = tracks[index]
            val finalProgress = if (newProgress > track.progress) newProgress else track.progress
            tracks[index] = track.copy(
                progress = finalProgress,
                lastReadChapter = chapter,
                status = if (track.status == "COMPLETED") "COMPLETED" else "CURRENT"
            )
        } else {
            // First-time reader: auto-create a track so progress is recorded.
            // Caller can override title/cover via updateMangaInfo later (e.g. from fetchMangaDetail).
            tracks.add(
                MangaTrack(
                    mangaId = mangaId,
                    progress = newProgress,
                    lastReadChapter = chapter,
                    status = "CURRENT",
                    totalChapters = 0
                )
            )
        }
        saveTracks(tracks)
    }

    /**
     * Ensure a track exists for the given manga. If none exists, a new CURRENT track is created
     * with the provided metadata. Returns the (possibly newly-created) track.
     */
    fun ensureTrack(mangaId: Int, title: String = "", cover: String = "", totalChapters: Int = 0): MangaTrack {
        val tracks = getTracks().toMutableList()
        val index = tracks.indexOfFirst { it.mangaId == mangaId }
        if (index >= 0) {
            // Patch in any non-default metadata if missing
            val existing = tracks[index]
            val patched = existing.copy(
                title = existing.title.ifBlank { title },
                cover = existing.cover.ifBlank { cover },
                totalChapters = if (existing.totalChapters == 0) totalChapters else existing.totalChapters
            )
            if (patched != existing) {
                tracks[index] = patched
                saveTracks(tracks)
            }
            return patched
        }
        val newTrack = MangaTrack(
            mangaId = mangaId,
            title = title,
            cover = cover,
            totalChapters = totalChapters,
            status = "CURRENT"
        )
        tracks.add(newTrack)
        saveTracks(tracks)
        return newTrack
    }

    fun updateChapterProgress(mangaId: Int, progress: Float) {
        android.util.Log.d("MangaSyncDebug", "track.updateChapterProgress mangaId=$mangaId progress=$progress")
        val tracks = getTracks().toMutableList()
        val index = tracks.indexOfFirst { it.mangaId == mangaId }
        if (index >= 0) {
            tracks[index] = tracks[index].copy(progress = progress)
        }
        saveTracks(tracks)
    }

    fun updateScrollProgress(mangaId: Int, scrollProgress: Float) {
        val tracks = getTracks().toMutableList()
        val index = tracks.indexOfFirst { it.mangaId == mangaId }
        if (index >= 0) {
            tracks[index] = tracks[index].copy(scrollProgress = scrollProgress)
        }
        saveTracks(tracks)
    }

    fun updateTrackingStatus(mangaId: Int, status: String) {
        android.util.Log.d("MangaSyncDebug", "track.updateTrackingStatus mangaId=$mangaId status='$status'")
        val tracks = getTracks().toMutableList()
        val index = tracks.indexOfFirst { it.mangaId == mangaId }
        if (index >= 0) {
            tracks[index] = tracks[index].copy(status = status)
        } else {
            tracks.add(MangaTrack(mangaId = mangaId, status = status))
        }
        saveTracks(tracks)
    }

    fun updateTotalChapters(mangaId: Int, totalChapters: Int, totalVolumes: Int?) {
        val tracks = getTracks().toMutableList()
        val index = tracks.indexOfFirst { it.mangaId == mangaId }
        if (index >= 0) {
            tracks[index] = tracks[index].copy(
                totalChapters = totalChapters,
                totalVolumes = totalVolumes
            )
        }
        saveTracks(tracks)
    }

    fun updateMangaDexId(mangaId: Int, mangaDexId: String) {
        val tracks = getTracks().toMutableList()
        val index = tracks.indexOfFirst { it.mangaId == mangaId }
        if (index >= 0) {
            tracks[index] = tracks[index].copy(mangaDexId = mangaDexId)
        }
        saveTracks(tracks)
    }

    fun updateChapterPages(mangaId: Int, pages: Int) {
        if (pages <= 0) return
        val tracks = getTracks().toMutableList()
        val index = tracks.indexOfFirst { it.mangaId == mangaId }
        if (index >= 0) {
            tracks[index] = tracks[index].copy(currentChapterPages = pages)
        }
        saveTracks(tracks)
    }

    /**
     * Clear the in-chapter reading state (scroll position + current chapter page count).
     * Used when dismissing a "Continue Reading" card so the manga leaves the Continue
     * Reading row while remaining tracked in its status list.
     */
    fun clearChapterProgress(mangaId: Int) {
        val tracks = getTracks().toMutableList()
        val index = tracks.indexOfFirst { it.mangaId == mangaId }
        if (index >= 0) {
            tracks[index] = tracks[index].copy(scrollProgress = 0f, currentChapterPages = 0)
        }
        saveTracks(tracks)
    }

    fun updateMangaInfo(mangaId: Int, title: String, cover: String) {
        // Only patch an existing track — never auto-create one here. This is called from
        // fetchMangaDetail (i.e. merely viewing a detail page), so creating a track would
        // silently add manga to "Planning to Read" without the user doing anything.
        val tracks = getTracks().toMutableList()
        val index = tracks.indexOfFirst { it.mangaId == mangaId }
        if (index >= 0) {
            tracks[index] = tracks[index].copy(title = title, cover = cover)
            saveTracks(tracks)
        }
    }

    private fun getTracks(): List<MangaTrack> {
        val raw = prefs.getString("tracks", null) ?: return emptyList()
        return try {
            json.decodeFromString<List<MangaTrack>>(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveTracks(tracks: List<MangaTrack>) {
        prefs.edit().putString("tracks", json.encodeToString(tracks)).apply()
    }
}
