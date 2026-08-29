package com.blissless.tensei.api.myanimelist

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.blissless.tensei.BuildConfig
import com.blissless.tensei.network.Endpoints
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import androidx.core.net.toUri
import org.json.JSONArray
import org.json.JSONObject

class MalApiService(context: Context) {

    companion object {
        private const val MAL_API_BASE = Endpoints.Mal.API_BASE
        private const val TIMEOUT_MS = 15000
    }

    /**
     * Perform a PUT with urlencoded [formParams], manually following redirects so the
     * Authorization / client-id headers are preserved. HttpURLConnection's built-in redirect
     * following strips Authorization on cross-host redirects, which breaks authenticated PUTs.
     * Returns the final HTTP status code (or -1 if too many redirects).
     */
    private fun execPutWithAuth(url: URL, formParams: String, authHeader: String): Int {
        var currentUrl: URL = url
        repeat(6) {
            val conn = currentUrl.openConnection() as HttpURLConnection
            conn.requestMethod = "PUT"
            conn.doOutput = true
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.setRequestProperty("Authorization", authHeader)
            conn.setRequestProperty("X-MAL-CLIENT-ID", BuildConfig.MAL_CLIENT_ID)
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            try {
                conn.outputStream.use { it.write(formParams.toByteArray(Charsets.UTF_8)) }
            } catch (e: Exception) {
                return@repeat
            }
            val responseCode = conn.responseCode
            if (responseCode in 300..399) {
                val location = conn.getHeaderField("Location")
                if (location.isNullOrBlank()) return responseCode
                currentUrl = URL(currentUrl, location)
            } else {
                return responseCode
            }
        }
        return -1
    }

    private val authManager = MalAuthManager(context)

    fun getAuthUrl(clientId: String, state: String = "random_state_string"): Uri {
        val redirectUri = "animescraper://success"

        val codeVerifier = generateCodeVerifier()
        authManager.saveCodeVerifier(codeVerifier)

        val scope = "write:users+read:users+profile"

        val url = Endpoints.Mal.AUTH_URL +
                "?response_type=code" +
                "&client_id=$clientId" +
                "&redirect_uri=${URLEncoder.encode(redirectUri, "UTF-8")}" +
                "&state=$state" +
                "&code_challenge_method=plain" +
                "&code_challenge=$codeVerifier" +
                "&scope=${URLEncoder.encode(scope, "UTF-8")}"

        return url.toUri()
    }

    private fun generateCodeVerifier(): String {
        // 43-128 characters - use alphanumeric only for maximum compatibility
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..64).map { chars.random() }.joinToString("")
    }

    suspend fun exchangeCodeForToken(code: String, clientId: String, clientSecret: String? = null): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val redirectUri = "animescraper://success"
                val codeVerifier = authManager.getCodeVerifier() ?: return@withContext false

                authManager.clearCodeVerifier()

                val url = URL(Endpoints.Mal.TOKEN_URL)

                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

                val useClientSecret = !clientSecret.isNullOrBlank()
                if (useClientSecret) {
                    val authString = Base64.encodeToString(
                        "$clientId:$clientSecret".toByteArray(),
                        Base64.NO_WRAP
                    )
                    conn.setRequestProperty("Authorization", "Basic $authString")
                } else {
                    conn.setRequestProperty("X-MAL-CLIENT-ID", clientId)
                }
                conn.connectTimeout = TIMEOUT_MS
                conn.readTimeout = TIMEOUT_MS

                val postData = buildString {
                    append("client_id=$clientId")
                    if (useClientSecret && clientSecret.isNotBlank()) {
                        append("&client_secret=$clientSecret")
                    }
                    append("&grant_type=authorization_code")
                    append("&code=$code")
                    append("&redirect_uri=${URLEncoder.encode(redirectUri, "UTF-8")}")
                    append("&code_verifier=$codeVerifier")
                }

                conn.outputStream.use { it.write(postData.toByteArray()) }

                val responseCode = conn.responseCode

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val response = reader.readText()
                    reader.close()

                    parseAndSaveToken(response)
                    fetchUserInfo()
                    true
                } else {
                    val errorReader = BufferedReader(InputStreamReader(conn.errorStream))
                    errorReader.close()
                    false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

    private fun parseAndSaveToken(response: String) {
        try {
            val tokenMatch = Regex("\"access_token\"\\s*:\\s*\"([^\"]+)\"").find(response)
            val tokenTypeMatch = Regex("\"token_type\"\\s*:\\s*\"([^\"]+)\"").find(response)
            val expiresMatch = Regex("\"expires_in\"\\s*:\\s*(\\d+)").find(response)
            val refreshMatch = Regex("\"refresh_token\"\\s*:\\s*\"([^\"]+)\"").find(response)

            val token = tokenMatch?.groupValues?.get(1)
            val tokenType = tokenTypeMatch?.groupValues?.get(1) ?: "Bearer"
            val expiresIn = expiresMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val refreshToken = refreshMatch?.groupValues?.get(1)

            if (token != null) {
                authManager.saveToken(token, tokenType, expiresIn, refreshToken)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun fetchUserInfo() {
        val response = makeGetRequest("$MAL_API_BASE/users/@me?fields=name,picture")
        if (response != null) {
            try {
                val nameMatch = Regex("\"name\"\\s*:\\s*\"([^\"]+)\"").find(response)
                val pictureMatch = Regex("\"picture\"\\s*:\\s*\"([^\"]+)\"").find(response)

                val name = nameMatch?.groupValues?.get(1)
                val picture = pictureMatch?.groupValues?.get(1)

                if (name != null) {
                    authManager.saveUserInfo(name, picture)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun getAnimeList(status: String? = null, limit: Int = 1000): List<MalAnimeListEntry> =
        withContext(Dispatchers.IO) {
            val entries = mutableListOf<MalAnimeListEntry>()
            var offset = 0
            val maxTotal = 1000

            while (offset < maxTotal) {
                val fields =
                    "list_status{status,score,num_episodes_watched,updated_at},title,main_picture,num_episodes,alternative_titles"
                var url =
                    "$MAL_API_BASE/users/@me/animelist?fields=$fields&limit=$limit&offset=$offset"
                if (status != null) {
                    url += "&status=$status"
                }

                val response = makeGetRequest(url) ?: break

                try {
                    val items = parseAnimeListResponse(response)
                    if (items.isEmpty()) {
                        break
                    }
                    entries.addAll(items)
                    offset += limit

                } catch (e: Exception) {
                    e.printStackTrace()
                    break
                }
            }

            entries
        }

    private fun parseAnimeListResponse(jsonStr: String): List<MalAnimeListEntry> {
        val entries = mutableListOf<MalAnimeListEntry>()

        val dataMatch = Regex("\"data\"\\s*:\\s*\\[(.*?)]\\s*,\\s*\"paging\"", RegexOption.DOT_MATCHES_ALL).find(jsonStr)
        val dataStr = if (dataMatch != null) {
            dataMatch.groupValues[1]
        } else {
            val altMatch = Regex("\"data\"\\s*:\\s*\\[(.*)]", RegexOption.DOT_MATCHES_ALL).find(jsonStr)
            if (altMatch != null) altMatch.groupValues[1] else jsonStr
        }

        var searchIndex = 0
        while (searchIndex < dataStr.length) {
            val nodeStart = dataStr.indexOf("{\"node\":{", searchIndex)
            if (nodeStart == -1) break

            val nodeEnd = findMatchingBrace(dataStr, nodeStart + 8)
            if (nodeEnd == -1) break

            val listStatusStart = dataStr.indexOf("\"list_status\":{", nodeEnd)
            if (listStatusStart == -1 || listStatusStart > nodeEnd + 100) {
                searchIndex = nodeEnd + 1
                continue
            }

            val listStatusEnd = findMatchingBrace(dataStr, listStatusStart + 14)
            if (listStatusEnd == -1) {
                searchIndex = nodeEnd + 1
                continue
            }

            val block = dataStr.substring(nodeStart, listStatusEnd + 1)

            try {
                val idMatch = Regex("\"id\"\\s*:\\s*(\\d+)").find(block)
                val id = idMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0

                val titleMatch = Regex("\"title\"\\s*:\\s*\"([^\"]+)\"").find(block)
                val title = titleMatch?.groupValues?.get(1) ?: "Unknown"

                val mediumPic = Regex("\"medium\"\\s*:\\s*\"([^\"]+)\"").find(block)?.groupValues?.get(1)
                val largePic = Regex("\"large\"\\s*:\\s*\"([^\"]+)\"").find(block)?.groupValues?.get(1)

                val totalEpisodesMatch = Regex("\"num_episodes\"\\s*:\\s*(\\d+)").find(block)
                val totalEpisodes = totalEpisodesMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0

                val altTitlesMatch = Regex("\"alternative_titles\"\\s*:\\s*\\{([^}]+)\\}").find(block)
                var altTitleEn: String? = null
                if (altTitlesMatch != null) {
                    val enMatch = Regex("\"en\"\\s*:\\s*\"([^\"]+)\"").find(altTitlesMatch.value)
                    altTitleEn = enMatch?.groupValues?.get(1)
                }

                val statusMatch = Regex("\"status\"\\s*:\\s*\"([^\"]+)\"").find(block)
                val status = statusMatch?.groupValues?.get(1)

                val scoreMatch = Regex("\"score\"\\s*:\\s*(\\d+)").find(block)
                val score = scoreMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0

                val episodesMatch = Regex("\"num_episodes_watched\"\\s*:\\s*(\\d+)").find(block)
                val episodes = episodesMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0

                entries.add(
                    MalAnimeListEntry(
                        node = MalAnimeNode(
                            id = id,
                            title = title,
                            main_picture = MalPicture(medium = mediumPic, large = largePic),
                            num_episodes = totalEpisodes,
                            alternative_titles = if (altTitleEn != null) MalAlternativeTitles(en = altTitleEn) else null
                        ),
                        list_status = if (status != null) MalListStatus(
                            status = status,
                            score = score,
                            num_episodes_watched = episodes
                        ) else null
                    )
                )
            } catch (_: Exception) {
                // skip malformed entries
            }

            searchIndex = listStatusEnd + 1
        }

        return entries
    }

    suspend fun getMangaList(status: String? = null, limit: Int = 1000): List<MalMangaListEntry> =
        withContext(Dispatchers.IO) {
            val entries = mutableListOf<MalMangaListEntry>()
            var offset = 0
            val maxTotal = 1000

            while (offset < maxTotal) {
                val fields =
                    "list_status{status,score,num_chapters_read,num_volumes_read,updated_at},title,main_picture,num_chapters,num_volumes,alternative_titles"
                var url =
                    "$MAL_API_BASE/users/@me/mangalist?fields=$fields&limit=$limit&offset=$offset"
                if (status != null) {
                    url += "&status=$status"
                }

                val response = makeGetRequest(url) ?: break

                try {
                    val items = parseMangaListResponse(response)
                    if (items.isEmpty()) {
                        break
                    }
                    entries.addAll(items)
                    offset += limit

                } catch (e: Exception) {
                    e.printStackTrace()
                    break
                }
            }

            entries
        }

    // ─── MAL website list-loader (load.json) ──────────────────────────────────
    //
    // The myanimelist.net "list" screens expose a JSON array (one object per entry) whose shape
    // differs from the /v2 API: statuses are numeric and media ids are prefixed (anime_id/manga_id).
    // We reuse the typed models (MalAnimeListEntry/MalMangaListEntry) so the cross-provider sync can
    // consume the same data, mapping numeric status to the MAL string status used everywhere else.

    private fun getMalUsername(): String? =
        authManager.userInfo.value?.name?.takeIf { it.isNotBlank() }

    /**
     * GET against a myanimelist.net site URL. Unlike [makeGetRequest] (which targets the /v2 API),
     * the site's load.json does not take the X-MAL-CLIENT-ID header; we still send the bearer token
     * when available. Returns the raw body on HTTP 200, otherwise null.
     */
    private fun makeWebGetRequest(path: String): String? {
        return try {
            val conn = URL(path).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.instanceFollowRedirects = true
            authManager.getAuthHeader()?.let { conn.setRequestProperty("Authorization", it) }
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            val responseCode = conn.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Fetch the full MAL anime list from the website load.json endpoint using the logged-in
     * username. Returns empty if we cannot determine the username or the request fails.
     */
    suspend fun getAnimeListFromWeb(): List<MalAnimeListEntry> = withContext(Dispatchers.IO) {
        val username = getMalUsername() ?: return@withContext emptyList()
        val entries = mutableListOf<MalAnimeListEntry>()
        var offset = 0
        val pageSize = 300
        var guard = 0
        while (guard < 100) {
            val url = "https://myanimelist.net/animelist/$username/load.json?status=7&offset=$offset&limit=$pageSize"
            val response = makeWebGetRequest(url) ?: break
            val page = parseMalWebAnimeList(response)
            if (page.isEmpty()) break
            entries.addAll(page)
            offset += pageSize
            guard++
        }
        entries
    }

    /**
     * Fetch the full MAL manga list from the website load.json endpoint (mangalist variant).
     */
    suspend fun getMangaListFromWeb(): List<MalMangaListEntry> = withContext(Dispatchers.IO) {
        val username = getMalUsername() ?: return@withContext emptyList()
        val entries = mutableListOf<MalMangaListEntry>()
        var offset = 0
        val pageSize = 300
        var guard = 0
        while (guard < 100) {
            val url = "https://myanimelist.net/mangalist/$username/load.json?status=7&offset=$offset&limit=$pageSize"
            val response = makeWebGetRequest(url) ?: break
            val page = parseMalWebMangaList(response)
            if (page.isEmpty()) break
            entries.addAll(page)
            offset += pageSize
            guard++
        }
        entries
    }

    /**
     * Fetch from the website load.json, falling back to the /v2 API whenever the site request
     * yields nothing (e.g. list not public or username unavailable).
     */
    suspend fun getAnimeListWithWeb(): List<MalAnimeListEntry> =
        getAnimeListFromWeb().ifEmpty { getAnimeList() }

    suspend fun getMangaListWithWeb(): List<MalMangaListEntry> =
        getMangaListFromWeb().ifEmpty { getMangaList() }

    private fun parseMalWebAnimeList(jsonStr: String): List<MalAnimeListEntry> {
        val entries = mutableListOf<MalAnimeListEntry>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val id = obj.optInt("anime_id", 0)
                if (id <= 0) continue
                val statusNum = obj.optInt("status", -1)
                val status = malAnimeStatusFromNum(statusNum)
                entries.add(
                    MalAnimeListEntry(
                        node = MalAnimeNode(
                            id = id,
                            title = obj.optString("anime_title", "Unknown"),
                            num_episodes = obj.optInt("anime_num_episodes", 0)
                        ),
                        list_status = MalListStatus(
                            status = status,
                            score = obj.optInt("score", 0),
                            num_episodes_watched = obj.optInt("num_watched_episodes", 0)
                        )
                    )
                )
            }
        } catch (_: Exception) {
        }
        return entries
    }

    private fun parseMalWebMangaList(jsonStr: String): List<MalMangaListEntry> {
        val entries = mutableListOf<MalMangaListEntry>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val id = obj.optInt("manga_id", 0)
                if (id <= 0) continue
                val statusNum = obj.optInt("status", -1)
                val status = malMangaStatusFromNum(statusNum)
                entries.add(
                    MalMangaListEntry(
                        node = MalMangaNode(
                            id = id,
                            title = obj.optString("manga_title", "Unknown"),
                            num_chapters = obj.optInt("manga_num_chapters", 0),
                            num_volumes = obj.optInt("manga_num_volumes", 0)
                        ),
                        list_status = MalMangaListStatus(
                            status = status,
                            score = obj.optInt("score", 0),
                            // MAL's website "load.json" manga list uses "num_read_chapters"
                            // (NOT the /v2 API name "num_chapters_read"). Reading the wrong
                            // field defaulted to 0, which made every reading entry look like
                            // 0 chapters and spuriously re-pushed progress on every sync.
                            num_chapters_read = obj.optInt("num_read_chapters", 0)
                        )
                    )
                )
            }
        } catch (_: Exception) {
        }
        return entries
    }

    /** MAPS numeric MAL anime list status → MAL string status used by the /v2 API. */
    private fun malAnimeStatusFromNum(status: Int): String? = when (status) {
        1 -> "watching"
        2 -> "completed"
        3 -> "on_hold"
        4 -> "dropped"
        6 -> "plan_to_watch"
        else -> null
    }

    /** MAPS numeric MAL manga list status → MAL string status used by the /v2 API. */
    private fun malMangaStatusFromNum(status: Int): String? = when (status) {
        1 -> "reading"
        2 -> "completed"
        3 -> "on_hold"
        4 -> "dropped"
        6 -> "plan_to_read"
        else -> null
    }

    private fun parseMangaListResponse(jsonStr: String): List<MalMangaListEntry> {
        val entries = mutableListOf<MalMangaListEntry>()

        val dataMatch = Regex("\"data\"\\s*:\\s*\\[(.*?)]\\s*,\\s*\"paging\"", RegexOption.DOT_MATCHES_ALL).find(jsonStr)
        val dataStr = if (dataMatch != null) {
            dataMatch.groupValues[1]
        } else {
            val altMatch = Regex("\"data\"\\s*:\\s*\\[(.*)]", RegexOption.DOT_MATCHES_ALL).find(jsonStr)
            if (altMatch != null) altMatch.groupValues[1] else jsonStr
        }

        var searchIndex = 0
        while (searchIndex < dataStr.length) {
            val nodeStart = dataStr.indexOf("{\"node\":{", searchIndex)
            if (nodeStart == -1) break

            val nodeEnd = findMatchingBrace(dataStr, nodeStart + 8)
            if (nodeEnd == -1) break

            val listStatusStart = dataStr.indexOf("\"list_status\":{", nodeEnd)
            if (listStatusStart == -1 || listStatusStart > nodeEnd + 100) {
                searchIndex = nodeEnd + 1
                continue
            }

            val listStatusEnd = findMatchingBrace(dataStr, listStatusStart + 14)
            if (listStatusEnd == -1) {
                searchIndex = nodeEnd + 1
                continue
            }

            val block = dataStr.substring(nodeStart, listStatusEnd + 1)

            try {
                val idMatch = Regex("\"id\"\\s*:\\s*(\\d+)").find(block)
                val id = idMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0

                val titleMatch = Regex("\"title\"\\s*:\\s*\"([^\"]+)\"").find(block)
                val title = titleMatch?.groupValues?.get(1) ?: "Unknown"

                val mediumPic = Regex("\"medium\"\\s*:\\s*\"([^\"]+)\"").find(block)?.groupValues?.get(1)
                val largePic = Regex("\"large\"\\s*:\\s*\"([^\"]+)\"").find(block)?.groupValues?.get(1)

                val totalChaptersMatch = Regex("\"num_chapters\"\\s*:\\s*(\\d+)").find(block)
                val totalChapters = totalChaptersMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0

                val totalVolumesMatch = Regex("\"num_volumes\"\\s*:\\s*(\\d+)").find(block)
                val totalVolumes = totalVolumesMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0

                val altTitlesMatch = Regex("\"alternative_titles\"\\s*:\\s*\\{([^}]+)\\}").find(block)
                var altTitleEn: String? = null
                if (altTitlesMatch != null) {
                    val enMatch = Regex("\"en\"\\s*:\\s*\"([^\"]+)\"").find(altTitlesMatch.value)
                    altTitleEn = enMatch?.groupValues?.get(1)
                }

                val statusMatch = Regex("\"status\"\\s*:\\s*\"([^\"]+)\"").find(block)
                val status = statusMatch?.groupValues?.get(1)

                val scoreMatch = Regex("\"score\"\\s*:\\s*(\\d+)").find(block)
                val score = scoreMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0

                val chaptersMatch = Regex("\"num_chapters_read\"\\s*:\\s*(\\d+)").find(block)
                val chapters = chaptersMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0

                entries.add(
                    MalMangaListEntry(
                        node = MalMangaNode(
                            id = id,
                            title = title,
                            main_picture = MalPicture(medium = mediumPic, large = largePic),
                            num_chapters = totalChapters,
                            num_volumes = totalVolumes,
                            alternative_titles = if (altTitleEn != null) MalAlternativeTitles(en = altTitleEn) else null
                        ),
                        list_status = if (status != null) MalMangaListStatus(
                            status = status,
                            score = score,
                            num_chapters_read = chapters
                        ) else null
                    )
                )
            } catch (_: Exception) {
                // skip malformed entries
            }

            searchIndex = listStatusEnd + 1
        }

        return entries
    }

    private fun findMatchingBrace(str: String, startIndex: Int): Int {
        if (startIndex >= str.length || str[startIndex] != '{') return -1
        var braceCount = 1
        var i = startIndex + 1
        var inString = false
        var escapeNext = false

        while (i < str.length && braceCount > 0) {
            val c = str[i]
            when {
                escapeNext -> escapeNext = false
                c == '\\' -> escapeNext = true
                c == '"' && !escapeNext -> inString = !inString
                !inString -> when (c) {
                    '{' -> braceCount++
                    '}' -> braceCount--
                }
            }
            i++
        }

        return if (braceCount == 0) i - 1 else -1
    }

    suspend fun updateAnimeStatus(animeId: Int, status: String?, score: Int? = null, episodesWatched: Int? = null): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val authHeader = authManager.getAuthHeader() ?: return@withContext false

                val url = URL("$MAL_API_BASE/anime/$animeId/my_list_status")

                val params = mutableListOf<String>()
                status?.let { params.add("status=$it") }
                score?.let { params.add("score=$it") }
                episodesWatched?.let { params.add("num_watched_episodes=$it") }

                if (params.isEmpty()) {
                    return@withContext false
                }

                val responseCode = execPutWithAuth(url, params.joinToString("&"), authHeader)

                if (responseCode != HttpURLConnection.HTTP_OK && responseCode != 201) {
                    android.util.Log.w("MalApi", "updateAnimeStatus FAILED animeId=$animeId code=$responseCode")
                } else {
                    android.util.Log.d("MalApi", "updateAnimeStatus OK animeId=$animeId code=$responseCode")
                }

                responseCode == HttpURLConnection.HTTP_OK || responseCode == 201
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

    suspend fun deleteAnimeFromList(animeId: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("$MAL_API_BASE/anime/$animeId/my_list_status")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "DELETE"
            conn.setRequestProperty(
                "Authorization",
                authManager.getAuthHeader() ?: return@withContext false
            )
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS

            val responseCode = conn.responseCode
            responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_NOT_FOUND
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // ─── Manga (via MAL /manga/{id}/my_list_status) ────────────────────────────

    /**
     * Update a manga's status/score/chapter progress on MAL. Status is in MAL form
     * (reading, plan_to_read, completed, on_hold, dropped). Params use the manga
     * endpoint with num_chapters_read.
     */
    suspend fun updateMangaStatus(
        malMangaId: Int,
        status: String? = null,
        score: Int? = null,
        chaptersRead: Int? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val authHeader = authManager.getAuthHeader() ?: return@withContext false

            val url = URL("$MAL_API_BASE/manga/$malMangaId/my_list_status")

            val params = mutableListOf<String>()
            status?.let { params.add("status=$it") }
            score?.let { params.add("score=$it") }
            chaptersRead?.let { params.add("num_chapters_read=$it") }

            if (params.isEmpty()) {
                return@withContext false
            }

            val responseCode = execPutWithAuth(url, params.joinToString("&"), authHeader)

            if (responseCode != HttpURLConnection.HTTP_OK && responseCode != 201) {
                android.util.Log.w("MalApi", "updateMangaStatus FAILED mangaId=$malMangaId code=$responseCode")
            } else {
                android.util.Log.d("MalApi", "updateMangaStatus OK mangaId=$malMangaId code=$responseCode")
            }

            responseCode == HttpURLConnection.HTTP_OK || responseCode == 201
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun deleteMangaFromList(malMangaId: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("$MAL_API_BASE/manga/$malMangaId/my_list_status")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "DELETE"
            conn.setRequestProperty(
                "Authorization",
                authManager.getAuthHeader() ?: return@withContext false
            )
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS

            val responseCode = conn.responseCode
            responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_NOT_FOUND
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun makeGetRequest(path: String): String? {
        return try {
            val authHeader = authManager.getAuthHeader() ?: return null

            val url = URL(path)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", authHeader)
            conn.setRequestProperty("X-MAL-CLIENT-ID", BuildConfig.MAL_CLIENT_ID)
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS

            val responseCode = conn.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val response = reader.readText()
                reader.close()
                response
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getAuthManager() = authManager
}

