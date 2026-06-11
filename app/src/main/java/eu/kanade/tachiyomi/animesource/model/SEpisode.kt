package eu.kanade.tachiyomi.animesource.model

interface SEpisode {
    var url: String
    var name: String
    var date_upload: Long
    var episode_number: Float
    var fillermark: Boolean
    var scanlator: String?
    var summary: String?
    var preview_url: String?

    companion object {
        fun create(): SEpisode = SEpisodeImpl()

        private class SEpisodeImpl : SEpisode {
            override var url: String = ""
            override var name: String = ""
            override var date_upload: Long = 0
            override var episode_number: Float = 0f
            override var fillermark: Boolean = false
            override var scanlator: String? = null
            override var summary: String? = null
            override var preview_url: String? = null
        }
    }
}
