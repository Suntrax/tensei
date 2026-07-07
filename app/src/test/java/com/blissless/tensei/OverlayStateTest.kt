package com.blissless.tensei

import com.blissless.tensei.data.models.AnimeMedia
import com.blissless.tensei.data.models.ExploreAnime
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for [OverlayState] sealed class hierarchy.
 *
 * Tests the state hierarchy used for navigation between anime detail
 * dialogs (explore, character, staff, cast, relations, downloads).
 */
class OverlayStateTest {

    // ─── None state ───────────────────────────────────────────────────────

    @Test
    fun `None has empty previousStates`() {
        assertThat(OverlayState.None.previousStates).isEmpty()
    }

    @Test
    fun `None is an OverlayState`() {
        assertThat(OverlayState.None).isInstanceOf(OverlayState::class.java)
    }

    // ─── ExploreAnimeDialog ───────────────────────────────────────────────

    @Test
    fun `ExploreAnimeDialog stores anime and defaults`() {
        val anime = ExploreAnime(id = 1, title = "Test", titleEnglish = null, cover = "", banner = null, episodes = 12, latestEpisode = 0, averageScore = 80, genres = emptyList(), year = 2024, format = "TV")
        val state = OverlayState.ExploreAnimeDialog(anime = anime)
        assertThat(state.anime).isEqualTo(anime)
        assertThat(state.firstAnime).isNull()
        assertThat(state.isFirstOpen).isTrue()
        assertThat(state.previousStates).isEmpty()
    }

    @Test
    fun `ExploreAnimeDialog stores firstAnime and isFirstOpen`() {
        val anime = ExploreAnime(id = 1, title = "Test", titleEnglish = null, cover = "", banner = null, episodes = 12, latestEpisode = 0, averageScore = 80, genres = emptyList(), year = 2024, format = "TV")
        val firstAnime = ExploreAnime(id = 2, title = "First", titleEnglish = null, cover = "", banner = null, episodes = 24, latestEpisode = 0, averageScore = 90, genres = emptyList(), year = 2023, format = "TV")
        val state = OverlayState.ExploreAnimeDialog(
            anime = anime,
            firstAnime = firstAnime,
            isFirstOpen = false
        )
        assertThat(state.firstAnime).isEqualTo(firstAnime)
        assertThat(state.isFirstOpen).isFalse()
    }

    @Test
    fun `ExploreAnimeDialog stores previousStates`() {
        val anime = ExploreAnime(id = 1, title = "Test", titleEnglish = null, cover = "", banner = null, episodes = 12, latestEpisode = 0, averageScore = 80, genres = emptyList(), year = 2024, format = "TV")
        val previous = OverlayState.CharacterDialog(characterId = 10, animeId = 1)
        val state = OverlayState.ExploreAnimeDialog(
            anime = anime,
            previousStates = listOf(previous)
        )
        assertThat(state.previousStates).hasSize(1)
        assertThat(state.previousStates[0]).isEqualTo(previous)
    }

    // ─── CharacterDialog ──────────────────────────────────────────────────

    @Test
    fun `CharacterDialog stores characterId and animeId`() {
        val state = OverlayState.CharacterDialog(characterId = 42, animeId = 100)
        assertThat(state.characterId).isEqualTo(42)
        assertThat(state.animeId).isEqualTo(100)
        assertThat(state.previousStates).isEmpty()
    }

    // ─── StaffDialog ──────────────────────────────────────────────────────

    @Test
    fun `StaffDialog stores staffId and animeId`() {
        val state = OverlayState.StaffDialog(staffId = 99, animeId = 100)
        assertThat(state.staffId).isEqualTo(99)
        assertThat(state.animeId).isEqualTo(100)
        assertThat(state.previousStates).isEmpty()
    }

    // ─── AllCastDialog ────────────────────────────────────────────────────

    @Test
    fun `AllCastDialog stores animeId and title`() {
        val state = OverlayState.AllCastDialog(animeId = 50, animeTitle = "Test Anime")
        assertThat(state.animeId).isEqualTo(50)
        assertThat(state.animeTitle).isEqualTo("Test Anime")
        assertThat(state.previousStates).isEmpty()
    }

    // ─── AllStaffDialog ───────────────────────────────────────────────────

    @Test
    fun `AllStaffDialog stores animeId and title`() {
        val state = OverlayState.AllStaffDialog(animeId = 50, animeTitle = "Test Anime")
        assertThat(state.animeId).isEqualTo(50)
        assertThat(state.animeTitle).isEqualTo("Test Anime")
        assertThat(state.previousStates).isEmpty()
    }

    // ─── AllRelationsDialog ───────────────────────────────────────────────

    @Test
    fun `AllRelationsDialog stores animeId and title`() {
        val state = OverlayState.AllRelationsDialog(animeId = 50, animeTitle = "Test Anime")
        assertThat(state.animeId).isEqualTo(50)
        assertThat(state.animeTitle).isEqualTo("Test Anime")
        assertThat(state.previousStates).isEmpty()
    }

    // ─── EpisodeDownloadDialog ────────────────────────────────────────────

    @Test
    fun `EpisodeDownloadDialog stores anime`() {
        val anime = AnimeMedia(
            id = 1, title = "Test", titleEnglish = null, cover = "",
            banner = null, progress = 0, totalEpisodes = 12,
            latestEpisode = 0, status = "CURRENT", averageScore = 80,
            genres = emptyList(), listStatus = "", listEntryId = 0,
            year = 2024, malId = null, format = "TV"
        )
        val state = OverlayState.EpisodeDownloadDialog(anime = anime)
        assertThat(state.anime).isEqualTo(anime)
    }

    @Test
    fun `EpisodeDownloadDialog has empty previousStates by default`() {
        val anime = AnimeMedia(
            id = 1, title = "Test", titleEnglish = null, cover = "",
            banner = null, progress = 0, totalEpisodes = 12,
            latestEpisode = 0, status = "CURRENT", averageScore = 80,
            genres = emptyList(), listStatus = "", listEntryId = 0,
            year = 2024, malId = null, format = "TV"
        )
        val state = OverlayState.EpisodeDownloadDialog(anime = anime)
        assertThat(state.previousStates).isEmpty()
    }

    // ─── Navigation stack behavior ────────────────────────────────────────

    @Test
    fun `navigation stack can chain multiple states`() {
        val anime = ExploreAnime(id = 1, title = "Test", titleEnglish = null, cover = "", banner = null, episodes = 12, latestEpisode = 0, averageScore = 80, genres = emptyList(), year = 2024, format = "TV")
        val explore = OverlayState.ExploreAnimeDialog(anime = anime)
        val character = OverlayState.CharacterDialog(
            characterId = 10,
            animeId = 1,
            previousStates = listOf(explore)
        )
        val staff = OverlayState.StaffDialog(
            staffId = 20,
            animeId = 1,
            previousStates = listOf(explore, character)
        )
        assertThat(staff.previousStates).hasSize(2)
        assertThat(staff.previousStates[0]).isEqualTo(explore)
        assertThat(staff.previousStates[1]).isEqualTo(character)
    }
}
