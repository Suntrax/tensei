package eu.kanade.tachiyomi.animesource.model

import android.net.Uri
import okhttp3.Headers

data class Track(val url: String, val lang: String)

enum class ChapterType {
    Opening, Ending, Recap, MixedOp, Other,
}

data class TimeStamp(
    val start: Double,
    val end: Double,
    val name: String,
    val type: ChapterType = ChapterType.Other,
)

data class Video(
    var videoUrl: String = "",
    val videoTitle: String = "",
    val resolution: Int? = null,
    val bitrate: Int? = null,
    val headers: Headers? = null,
    val preferred: Boolean = false,
    val subtitleTracks: List<Track> = emptyList(),
    val audioTracks: List<Track> = emptyList(),
    val timestamps: List<TimeStamp> = emptyList(),
    val mpvArgs: List<Pair<String, String>> = emptyList(),
    val ffmpegStreamArgs: List<Pair<String, String>> = emptyList(),
    val ffmpegVideoArgs: List<Pair<String, String>> = emptyList(),
    val internalData: String = "",
    val initialized: Boolean = false,
) {
    @Deprecated("Use videoTitle instead", ReplaceWith("videoTitle"))
    val quality: String
        get() = videoTitle

    val url: String
        get() = videoPageUrl

    private var videoPageUrl: String = ""

    @Deprecated("Use new Video constructor", level = DeprecationLevel.ERROR)
    constructor(
        url: String,
        quality: String,
        videoUrl: String?,
        headers: Headers? = null,
        subtitleTracks: List<Track> = emptyList(),
        audioTracks: List<Track> = emptyList(),
    ) : this(
        videoTitle = quality,
        videoUrl = videoUrl ?: "",
        headers = headers,
        subtitleTracks = subtitleTracks,
        audioTracks = audioTracks,
    ) {
        this.videoPageUrl = url
    }

    @Deprecated("Use new Video constructor", level = DeprecationLevel.ERROR)
    constructor(
        url: String,
        quality: String,
        videoUrl: String?,
        uri: Uri? = null,
        headers: Headers? = null,
    ) : this(
        videoTitle = quality,
        videoUrl = videoUrl ?: "",
        headers = headers,
    )
}
