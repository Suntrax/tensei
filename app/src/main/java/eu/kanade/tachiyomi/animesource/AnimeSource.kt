package eu.kanade.tachiyomi.animesource

import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video

interface AnimeSource {
    val id: Long
    val name: String
    val lang: String
        get() = ""

    suspend fun getAnimeDetails(anime: SAnime): SAnime
    suspend fun getEpisodeList(anime: SAnime): List<SEpisode>
    suspend fun getSeasonList(anime: SAnime): List<SAnime>
    suspend fun getHosterList(episode: SEpisode): List<Hoster> = throw IllegalStateException("Not used")
    suspend fun getVideoList(hoster: Hoster): List<Video> = throw IllegalStateException("Not used")
    suspend fun getVideoList(episode: SEpisode): List<Video>
}
