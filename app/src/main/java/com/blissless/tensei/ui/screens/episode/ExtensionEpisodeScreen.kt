package com.blissless.tensei.ui.screens.episode

import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.Video
import okhttp3.OkHttpClient

data class ExtensionStreamParams(
    val videoUrl: String,
    val referer: String,
    val subtitleUrl: String?,
    val animeName: String,
    val episodeNumber: Int,
    val extensionClient: OkHttpClient?,
    val extensionHeaders: Map<String, String>,
    val allHosters: List<Hoster> = emptyList(),
    val allVideos: List<Video> = emptyList(),
    val sourcePackageName: String = "",
    val episodeUrl: String = "",
    val extensionName: String = "",
)


