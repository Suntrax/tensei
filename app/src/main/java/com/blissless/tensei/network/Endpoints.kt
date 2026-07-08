package com.blissless.tensei.network

/**
 * Centralized registry of all external API endpoints and service URLs used
 * across the app.
 *
 * Extracted from hardcoded strings scattered throughout the codebase.
 * Benefits:
 *   - One place to update when an endpoint changes
 *   - Easier to spot drift between environments (prod/staging)
 *   - Removes magic strings from business logic
 *
 * Usage:
 *   URL(Endpoints.Tmdb.searchTv(encodedTitle))
 *   URL(Endpoints.AniList.GRAPHQL)
 *
 * Note: Some URLs are constructed dynamically (e.g. with path parameters).
 * For these, a helper function is provided that returns the full URL string.
 */
object Endpoints {

    /**
     * AniList GraphQL API and web app.
     * Used for: anime metadata, user lists, favorites, activity feed.
     */
    object AniList {
        /** GraphQL endpoint for all AniList queries and mutations. */
        const val GRAPHQL = "https://graphql.anilist.co"

        /** OAuth authorization URL (client-side token flow). */
        fun authUrl(clientId: String): String =
            "https://anilist.co/api/v2/oauth/authorize?client_id=$clientId&response_type=token"

        /** Public web URL for an anime page (used in share/copy dialogs). */
        fun animePageUrl(id: Int): String = "https://anilist.co/anime/$id"

        /** Public web URL for a character page. */
        fun characterPageUrl(id: Int): String = "https://anilist.co/character/$id"

        /** Public web URL for a staff member page. */
        fun staffPageUrl(id: Int): String = "https://anilist.co/staff/$id"

        /** Favicon used on the login button (loaded by Coil). */
        const val FAVICON = "https://anilist.co/img/icons/favicon-32x32.png"
    }

    /**
     * MyAnimeList (MAL) REST API and OAuth endpoints.
     * Used for: alternate anime tracker, user lists, favorites.
     */
    object Mal {
        /** REST API base for all MAL v2 endpoints. */
        const val API_BASE = "https://api.myanimelist.net/v2"

        /** OAuth 2.0 authorization URL (PKCE flow). */
        const val AUTH_URL = "https://myanimelist.net/v1/oauth2/authorize"

        /** OAuth 2.0 token exchange URL. */
        const val TOKEN_URL = "https://myanimelist.net/v1/oauth2/token"

        /** Favicon used on the login button (loaded by Coil). */
        const val FAVICON = "https://cdn.myanimelist.net/images/favicon.ico"
    }

    /**
     * Jikan API — unofficial MyAnimeList REST mirror.
     * Used for: user favorites/history when logged in via MAL.
     */
    object Jikan {
        const val API_BASE = "https://api.jikan.moe/v4"

        /** Search anime by name. */
        fun searchAnime(query: String, limit: Int = 10): String =
            "$API_BASE/anime?q=$query&limit=$limit"
    }

    /**
     * TheMovieDB (TMDB) REST API.
     * Used for: episode titles, descriptions, and images for anime.
     * Requires a Bearer token (see [com.blissless.tensei.BuildConfig.TMDB_API_KEY]).
     */
    object Tmdb {
        const val API_BASE = "https://api.themoviedb.org/3"

        /** Image CDN base for posters, stills, and backdrops. */
        const val IMAGE_BASE = "https://image.tmdb.org/t/p/w500"

        /** Search for a TV show by title. */
        fun searchTv(query: String): String = "$API_BASE/search/tv?query=$query"

        /** Search for a movie by title. */
        fun searchMovie(query: String): String = "$API_BASE/search/movie?query=$query"

        /** TV show details (seasons, episode count, genres). */
        fun tvDetails(tvId: Int): String = "$API_BASE/tv/$tvId?language=en-US"

        /** Season details (episode list with titles and descriptions). */
        fun season(tvId: Int, seasonNumber: Int): String =
            "$API_BASE/tv/$tvId/season/$seasonNumber?language=en-US"

        /** Full URL for a poster/still path returned by TMDB. */
        fun imageUrl(path: String): String = "$IMAGE_BASE$path"
    }

    /**
     * Aniwatch (HiAnime) API — unofficial anime streaming metadata.
     * Used for: episode offset detection when TMDB title matching is ambiguous.
     */
    object Aniwatch {
        private const val API_BASE = "https://aniwatch-cxjn.vercel.app/api/v2/hianime"

        /** Search for an anime by title. */
        fun search(query: String, page: Int = 1): String =
            "$API_BASE/search?q=$query&page=$page"

        /** Fetch episode list for a specific anime. */
        fun episodes(aniwatchId: String): String =
            "$API_BASE/anime/$aniwatchId/episodes"
    }

    /**
     * AnimeThemes API — anime opening/ending themes database.
     * Used for: theme song metadata.
     */
    object AnimeThemes {
        const val API_BASE = "https://api.animethemes.moe"
    }

    /**
     * AnimeSkip API — skip times for OP/ED in anime episodes.
     * Used for: auto-skip opening/ending during playback.
     */
    object AnimeSkip {
        const val API_URL = "https://api.aniskip.com/v2/skip-times"
    }

    /**
     * GitHub API and web URLs.
     * Used for: app update checks and the About screen.
     */
    object GitHub {
        /** GitHub API: latest release for a repo. */
        fun latestRelease(owner: String, repo: String): String =
            "https://api.github.com/repos/$owner/$repo/releases/latest"

        /** GitHub web URL for the Tensei repo. */
        const val TENSEI_REPO = "https://github.com/Suntrax/tensei"

        /** Latest release URL for the Tensei repo specifically. */
        const val TENSEI_LATEST_RELEASE =
            "https://api.github.com/repos/Suntrax/tensei/releases/latest"
    }

    /**
     * YouTube URLs — used when external streaming links point to YouTube.
     */
    object YouTube {
        fun watchUrl(videoId: String): String = "https://www.youtube.com/watch?v=$videoId"
        fun thumbnailUrl(videoId: String): String =
            "https://img.youtube.com/vi/$videoId/maxresdefault.jpg"
    }

    /**
     * Dailymotion URLs — used when external streaming links point to Dailymotion.
     */
    object Dailymotion {
        fun watchUrl(videoId: String): String = "https://www.dailymotion.com/video/$videoId"
    }

    /**
     * Default referer for streaming. Used as the initial value for the
     * player's referer state when no extension-specific referer is set.
     */
    const val DEFAULT_REFERER = "https://megacloud.tv/"
}
