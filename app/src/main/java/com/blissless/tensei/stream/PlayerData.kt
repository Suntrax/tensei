package com.blissless.tensei.stream

import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Track
import eu.kanade.tachiyomi.animesource.model.Video
import okhttp3.OkHttpClient

object PlayerData {
    @Volatile
    var videos: List<Video> = emptyList()
    @Volatile
    var animeTitle: String = ""
    @Volatile
    var currentQualityIndex: Int = 0
    @Volatile
    var selectedSubtitle: Track? = null
    @Volatile
    var selectedAudio: Track? = null
    @Volatile
    var extensionClient: OkHttpClient? = null
    @Volatile
    var extensionSource: AnimeCatalogueSource? = null
    @Volatile
    var extensionEpisode: SEpisode? = null
    @Volatile
    var allHosters: List<Hoster> = emptyList()
}


