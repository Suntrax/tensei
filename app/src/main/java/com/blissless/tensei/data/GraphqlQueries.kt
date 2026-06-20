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
    val GET_ANIME_RELATIONS = $$"""
        query ($id: Int!) {
            Media(id: $id, type: ANIME) {
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

    val GET_ALL_TAGS = """
        query {
            MediaTagCollection {
                id
                name
                description
                category
                rank
                isAdult
            }
        }
    """.trimIndent()
}


