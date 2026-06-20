package eu.kanade.tachiyomi.animesource.model

interface SAnime {
    var url: String
    var title: String
    var description: String?
    var status: Int
    var initialized: Boolean

    companion object {
        const val COMPLETED = 2

    }
}
