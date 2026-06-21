package eu.kanade.tachiyomi.animesource.model

interface SAnime {
    var url: String
    var title: String
    var artist: String?
    var author: String?
    var description: String?
    var genre: String?
    var status: Int
    var thumbnail_url: String?
    var background_url: String?
    var update_strategy: AnimeUpdateStrategy
    var fetch_type: FetchType
    var season_number: Double
    var initialized: Boolean

    companion object {
        const val UNKNOWN = 0
        const val ONGOING = 1
        const val COMPLETED = 2
        const val LICENSED = 3
        const val PUBLISHING_FINISHED = 4
        const val CANCELLED = 5
        const val ON_HIATUS = 6

        fun create(): SAnime = SAnimeImpl()

        private class SAnimeImpl : SAnime {
            override var url: String = ""
            override var title: String = ""
            override var artist: String? = null
            override var author: String? = null
            override var description: String? = null
            override var genre: String? = null
            override var status: Int = UNKNOWN
            override var thumbnail_url: String? = null
            override var background_url: String? = null
            override var update_strategy: AnimeUpdateStrategy = AnimeUpdateStrategy.ALWAYS_UPDATE
            override var fetch_type: FetchType = FetchType.Episodes
            override var season_number: Double = 1.0
            override var initialized: Boolean = false
        }
    }
}
