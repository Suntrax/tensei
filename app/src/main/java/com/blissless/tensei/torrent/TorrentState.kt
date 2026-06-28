package com.blissless.tensei.torrent

data class TorrentFileEntry(
    val index: Int,
    val name: String,
    val size: Long,
    val path: String
)

data class TorrentMeta(
    val name: String,
    val files: List<TorrentFileEntry>
)

data class MagnetEpisode(
    val episode: Int,
    val magnet: String,
    val quality: String = ""
)

data class MagnetData(
    val episodes: List<MagnetEpisode>,
    val isSingleTorrent: Boolean
)

enum class TorrentStatus {
    ADDING, DOWNLOADING_METADATA, METADATA_RECEIVED, DOWNLOADING, FINISHED, ERROR
}
