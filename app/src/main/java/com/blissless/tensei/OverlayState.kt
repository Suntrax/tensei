package com.blissless.tensei

import com.blissless.tensei.data.models.AnimeMedia
import com.blissless.tensei.data.models.ExploreAnime

sealed class OverlayState {
    open val previousStates: List<OverlayState> = emptyList()

    data object None : OverlayState()

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

    data class EpisodeDownloadDialog(
        val anime: AnimeMedia
    ) : OverlayState()
}


