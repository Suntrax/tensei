package com.blissless.tensei.network

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for [Endpoints].
 *
 * Verifies that URL construction helpers produce the expected strings.
 * These tests protect against accidental URL changes that would break
 * API integration.
 */
class EndpointsTest {

    // ─── AniList ──────────────────────────────────────────────────────────

    @Test
    fun `AniList GraphQL endpoint is correct`() {
        assertThat(Endpoints.AniList.GRAPHQL).isEqualTo("https://graphql.anilist.co")
    }

    @Test
    fun `AniList authUrl includes client id`() {
        val url = Endpoints.AniList.authUrl("my_client_123")
        assertThat(url).contains("client_id=my_client_123")
        assertThat(url).contains("response_type=token")
        assertThat(url).startsWith("https://anilist.co/api/v2/oauth/authorize")
    }

    @Test
    fun `AniList animePageUrl constructs correctly`() {
        assertThat(Endpoints.AniList.animePageUrl(12345))
            .isEqualTo("https://anilist.co/anime/12345")
    }

    @Test
    fun `AniList characterPageUrl constructs correctly`() {
        assertThat(Endpoints.AniList.characterPageUrl(678))
            .isEqualTo("https://anilist.co/character/678")
    }

    @Test
    fun `AniList staffPageUrl constructs correctly`() {
        assertThat(Endpoints.AniList.staffPageUrl(99))
            .isEqualTo("https://anilist.co/staff/99")
    }

    // ─── Mal ──────────────────────────────────────────────────────────────

    @Test
    fun `Mal API_BASE is correct`() {
        assertThat(Endpoints.Mal.API_BASE).isEqualTo("https://api.myanimelist.net/v2")
    }

    @Test
    fun `Mal AUTH_URL is correct`() {
        assertThat(Endpoints.Mal.AUTH_URL).isEqualTo("https://myanimelist.net/v1/oauth2/authorize")
    }

    @Test
    fun `Mal TOKEN_URL is correct`() {
        assertThat(Endpoints.Mal.TOKEN_URL).isEqualTo("https://myanimelist.net/v1/oauth2/token")
    }

    // ─── Jikan ────────────────────────────────────────────────────────────

    @Test
    fun `Jikan API_BASE is correct`() {
        assertThat(Endpoints.Jikan.API_BASE).isEqualTo("https://api.jikan.moe/v4")
    }

    @Test
    fun `Jikan searchAnime includes query and limit`() {
        val url = Endpoints.Jikan.searchAnime("naruto", 10)
        assertThat(url).contains("q=naruto")
        assertThat(url).contains("limit=10")
        assertThat(url).startsWith("https://api.jikan.moe/v4/anime")
    }

    // ─── TMDB ─────────────────────────────────────────────────────────────

    @Test
    fun `Tmdb API_BASE is correct`() {
        assertThat(Endpoints.Tmdb.API_BASE).isEqualTo("https://api.themoviedb.org/3")
    }

    @Test
    fun `Tmdb searchTv includes query`() {
        val url = Endpoints.Tmdb.searchTv("attack on titan")
        assertThat(url).contains("query=attack on titan")
        assertThat(url).startsWith("https://api.themoviedb.org/3/search/tv")
    }

    @Test
    fun `Tmdb searchMovie includes query`() {
        val url = Endpoints.Tmdb.searchMovie("your name")
        assertThat(url).contains("query=your name")
        assertThat(url).startsWith("https://api.themoviedb.org/3/search/movie")
    }

    @Test
    fun `Tmdb tvDetails constructs correctly`() {
        assertThat(Endpoints.Tmdb.tvDetails(12345))
            .isEqualTo("https://api.themoviedb.org/3/tv/12345?language=en-US")
    }

    @Test
    fun `Tmdb season constructs correctly`() {
        assertThat(Endpoints.Tmdb.season(12345, 2))
            .isEqualTo("https://api.themoviedb.org/3/tv/12345/season/2?language=en-US")
    }

    @Test
    fun `Tmdb imageUrl prepends base`() {
        assertThat(Endpoints.Tmdb.imageUrl("/abc.jpg"))
            .isEqualTo("https://image.tmdb.org/t/p/w500/abc.jpg")
    }

    // ─── Aniwatch ─────────────────────────────────────────────────────────

    @Test
    fun `Aniwatch search includes query and page`() {
        val url = Endpoints.Aniwatch.search("one piece", 1)
        assertThat(url).contains("q=one piece")
        assertThat(url).contains("page=1")
        assertThat(url).startsWith("https://aniwatch-cxjn.vercel.app/api/v2/hianime/search")
    }

    @Test
    fun `Aniwatch episodes constructs correctly`() {
        val url = Endpoints.Aniwatch.episodes("one-piece-21")
        assertThat(url).endsWith("/anime/one-piece-21/episodes")
    }

    // ─── GitHub ───────────────────────────────────────────────────────────

    @Test
    fun `GitHub latestRelease constructs correctly`() {
        val url = Endpoints.GitHub.latestRelease("Suntrax", "tensei")
        assertThat(url).isEqualTo("https://api.github.com/repos/Suntrax/tensei/releases/latest")
    }

    @Test
    fun `GitHub TENSEI_REPO is correct`() {
        assertThat(Endpoints.GitHub.TENSEI_REPO).isEqualTo("https://github.com/Suntrax/tensei")
    }

    @Test
    fun `GitHub TENSEI_LATEST_RELEASE is correct`() {
        assertThat(Endpoints.GitHub.TENSEI_LATEST_RELEASE)
            .isEqualTo("https://api.github.com/repos/Suntrax/tensei/releases/latest")
    }

    // ─── YouTube ──────────────────────────────────────────────────────────

    @Test
    fun `YouTube watchUrl constructs correctly`() {
        assertThat(Endpoints.YouTube.watchUrl("dQw4w9WgXcQ"))
            .isEqualTo("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
    }

    @Test
    fun `YouTube thumbnailUrl constructs correctly`() {
        assertThat(Endpoints.YouTube.thumbnailUrl("dQw4w9WgXcQ"))
            .isEqualTo("https://img.youtube.com/vi/dQw4w9WgXcQ/maxresdefault.jpg")
    }

    // ─── Dailymotion ──────────────────────────────────────────────────────

    @Test
    fun `Dailymotion watchUrl constructs correctly`() {
        assertThat(Endpoints.Dailymotion.watchUrl("abc123"))
            .isEqualTo("https://www.dailymotion.com/video/abc123")
    }
}
