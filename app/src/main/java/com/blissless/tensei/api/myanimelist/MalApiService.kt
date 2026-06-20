package com.blissless.tensei.api.myanimelist

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.blissless.tensei.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import androidx.core.net.toUri

class MalApiService(context: Context) {

    companion object {
        private const val MAL_API_BASE = "https://api.myanimelist.net/v2"
        private const val TIMEOUT_MS = 15000
    }

    private val authManager = MalAuthManager(context)

    fun getAuthUrl(clientId: String, state: String = "random_state_string"): Uri {
        val redirectUri = "animescraper://success"

        val codeVerifier = generateCodeVerifier()
        authManager.saveCodeVerifier(codeVerifier)

        val scope = "write:users+read:users+profile"

        val url = "https://myanimelist.net/v1/oauth2/authorize" +
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

                val url = URL("https://myanimelist.net/v1/oauth2/token")

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

                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "PUT"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                conn.setRequestProperty("Authorization", authHeader)
                conn.setRequestProperty("X-MAL-CLIENT-ID", BuildConfig.MAL_CLIENT_ID)
                conn.connectTimeout = TIMEOUT_MS
                conn.readTimeout = TIMEOUT_MS

                val params = mutableListOf<String>()
                status?.let { params.add("status=$it") }
                score?.let { params.add("score=$it") }
                episodesWatched?.let { params.add("num_watched_episodes=$it") }

                if (params.isEmpty()) {
                    return@withContext false
                }

                conn.outputStream.use { it.write(params.joinToString("&").toByteArray()) }

                val responseCode = conn.responseCode

                val success = responseCode == HttpURLConnection.HTTP_OK || responseCode == 201

                success
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

