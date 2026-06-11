package com.blissless.tensei.data

/**
 * Optimized GraphQL queries for AniList API
 * 
 * Design principles:
 * 1. Request only fields we actually use
 * 2. Batch related queries when possible
 * 3. Use fragments for consistency
 * 4. Minimize payload size
 */
object GraphqlQueries {

    // ============================================
    // FRAGMENTS - Reusable field selections
    // ============================================
    
    /**
     * Minimal media fields for lists (reduces payload by ~60%)
     */
    const val MEDIA_LIST_FRAGMENT = """
        fragment MediaListFields on Media {
            id
            idMal
            title { romaji english }
            coverImage { extraLarge }
            bannerImage
            episodes
            status
            averageScore
            genres
            seasonYear
            nextAiringEpisode { episode airingAt }
        }
    """

    /**
     * Minimal fields for explore/grid displays
     */
    const val MEDIA_EXPLORE_FRAGMENT = """
        fragment MediaExploreFields on Media {
            id
            idMal
            title { romaji english }
            coverImage { extraLarge }
            bannerImage
            episodes
            status
            averageScore
            genres
            seasonYear
            isAdult
            startDate { year }
            nextAiringEpisode { episode airingAt }
        }
    """

    /**
     * Minimal fields for airing schedule
     */
    const val AIRING_FRAGMENT = """
        fragment AiringFields on AiringSchedule {
            id
            airingAt
            episode
            timeUntilAiring
            mediaId
            media {
                id
                idMal
                title { romaji english }
                coverImage { extraLarge }
                episodes
                status
                averageScore
                genres
                seasonYear
                isAdult
            }
        }
    """

    // ============================================
    // USER QUERIES
    // ============================================

    // ============================================
    // EXPLORE QUERIES - BATCHED
    // ============================================

    // ============================================
    // AIRING SCHEDULE
    // ============================================

    // ============================================
    // SEARCH
    // ============================================

    // ============================================
    // DETAILED ANIME
    // ============================================

    // ============================================
    // MUTATIONS
    // ============================================

    // ============================================
    // USER ACTIVITY
    // ============================================

    // ============================================
    // BATCH QUERIES - For efficiency
    // ============================================

    // ============================================
    // ANIME RELATIONS
    // ============================================

    /**
     * Get anime relations (sequels, prequels, adaptations, etc.)
     */
    val GET_ANIME_RELATIONS = """
        query (${'$'}id: Int!) {
            Media(id: ${'$'}id, type: ANIME) {
                id
                title { romaji english }
                relations {
                    edges {
                        relationType
                        node {
                            id
                            title { romaji english }
                            coverImage { extraLarge }
                            episodes
                            averageScore
                            format
                            nextAiringEpisode { episode }
                        }
                    }
                }
            }
        }
    """.trimIndent()
}


