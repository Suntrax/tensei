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
        val tracks = getTracks().toMutableList()
        val index = tracks.indexOfFirst { it.mangaId == mangaId }
        if (index >= 0) {
            val track = tracks[index]
            val newProgress = if (chapter.chapterNumber > track.progress) chapter.chapterNumber else track.progress
            tracks[index] = track.copy(
                progress = newProgress,
                lastReadChapter = chapter,
                status = "CURRENT"
            )
        }
        saveTracks(tracks)
    }

    fun updateChapterProgress(mangaId: Int, progress: Float) {
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

    fun updateMangaInfo(mangaId: Int, title: String, cover: String) {
        val tracks = getTracks().toMutableList()
        val index = tracks.indexOfFirst { it.mangaId == mangaId }
        if (index >= 0) {
            tracks[index] = tracks[index].copy(title = title, cover = cover)
        } else {
            tracks.add(MangaTrack(mangaId = mangaId, title = title, cover = cover))
        }
        saveTracks(tracks)
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
