package eu.kanade.tachiyomi.animesource.model

sealed class AnimeFilter<T>(val name: String) {
    abstract class Text(name: String) : AnimeFilter<String>(name)
    abstract class Sort(name: String)
        : AnimeFilter<Sort.Selection?>(name) {
        data class Selection(val index: Int, val ascending: Boolean)
    }
}
