package com.blissless.tensei

import com.blissless.tensei.data.models.AnimeMedia
import com.blissless.tensei.data.models.ExploreAnime
import com.blissless.tensei.data.models.MangaMedia

sealed class OverlayState {
    open val previousStates: List<OverlayState> = emptyList()

    data object None : OverlayState()

    // ─── Anime ────────────────────────────────────────────────────────────

    data class ExploreAnimeDialog(
        val anime: ExploreAnime,
        val firstAnime: ExploreAnime? = null,
        val isFirstOpen: Boolean = true,
        override val previousStates: List<OverlayState> = emptyList()
    ) : OverlayState()

    data class CharacterDialog(
        val characterId: Int,
        val animeId: Int,
        override val previousStates: List<OverlayState> = emptyList()
    ) : OverlayState()

    data class StaffDialog(
        val staffId: Int,
        val animeId: Int,
        override val previousStates: List<OverlayState> = emptyList()
    ) : OverlayState()

    data class AllCastDialog(
        val animeId: Int,
        val animeTitle: String,
        override val previousStates: List<OverlayState> = emptyList()
    ) : OverlayState()

    data class AllStaffDialog(
        val animeId: Int,
        val animeTitle: String,
        override val previousStates: List<OverlayState> = emptyList()
    ) : OverlayState()

    data class AllRelationsDialog(
        val animeId: Int,
        val animeTitle: String,
        override val previousStates: List<OverlayState> = emptyList()
    ) : OverlayState()

    data class AllRecommendationsDialog(
        val animeId: Int,
        val animeTitle: String,
        override val previousStates: List<OverlayState> = emptyList()
    ) : OverlayState()

    data class EpisodeDownloadDialog(
        val anime: AnimeMedia
    ) : OverlayState()

    // ─── Manga ────────────────────────────────────────────────────────────

    data class MangaDetailDialog(
        val manga: MangaMedia,
        val isFirstOpen: Boolean = true,
        override val previousStates: List<OverlayState> = emptyList()
    ) : OverlayState()

    data class MangaReaderDialog(
        val manga: MangaMedia,
        val chapterIndex: Int = 0
    ) : OverlayState()
}

sealed class MangaOverlay {
    abstract val mangaId: Int
    data object None : MangaOverlay() {
        override val mangaId: Int get() = 0
    }
    data class AllCharacters(override val mangaId: Int, val mangaTitle: String) : MangaOverlay()
    data class AllStaff(override val mangaId: Int, val mangaTitle: String) : MangaOverlay()
    data class AllRelations(override val mangaId: Int, val mangaTitle: String) : MangaOverlay()
    data class AllRecommendations(override val mangaId: Int, val mangaTitle: String) : MangaOverlay()
}
