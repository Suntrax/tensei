package com.blissless.tensei.torrent

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.util.Log
import eu.kanade.tachiyomi.animesource.model.Track
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

data class DetectedMagnetExtension(
    val packageName: String,
    val name: String,
    val authority: String
)

data class StreamUrlResult(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val subtitles: List<Track> = emptyList(),
    val streams: List<StreamEntry> = emptyList()
)

data class StreamEntry(
    val lang: String,
    val isDefault: Boolean,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val subtitles: List<Track> = emptyList()
)

class MagnetExtensionClient(private val context: Context) {

    companion object {
        private const val TAG = "MagnetExtensionClient"
        private const val BEACON_ACTION = "com.blissless.animeclient.EXTENSION_BEACON"
        private const val PROVIDER_SUFFIX = ".provider"
        private const val SCRAPE_PATH = "scrape"
    }

    fun detectExtensions(): List<DetectedMagnetExtension> {
        val beaconIntent = Intent(BEACON_ACTION)
        val resolveInfoList = context.packageManager.queryBroadcastReceivers(beaconIntent, 0)
        Log.d(TAG, "detectExtensions: found ${resolveInfoList.size} receivers for action '$BEACON_ACTION'")
        val result = resolveInfoList.mapNotNull { info ->
            val packageName = info.activityInfo.packageName
            val label = info.loadLabel(context.packageManager).toString()
            Log.d(TAG, "detectExtensions: candidate pkg=$packageName label='$label'")
            if (label.startsWith("Tensei: ", ignoreCase = true) || label.startsWith("Anime: ", ignoreCase = true)) {
                DetectedMagnetExtension(packageName, label, "$packageName$PROVIDER_SUFFIX")
            } else null
        }
        Log.i(TAG, "detectExtensions: ${result.size} magnet extension(s) accepted: ${result.map { it.authority }}")
        return result
    }

    fun fetchMagnets(authority: String, anilistId: Int, animeName: String, animeRomaji: String = "", category: String = ""): MagnetData? {
        val providerUri = Uri.parse("content://$authority/$SCRAPE_PATH")
        val queryUri = providerUri.buildUpon()
            .appendQueryParameter("anime", animeName)
            .appendQueryParameter("anilistId", anilistId.toString())
            .apply { if (animeRomaji.isNotBlank()) appendQueryParameter("animeRomaji", animeRomaji) }
            .apply { if (category.isNotBlank()) appendQueryParameter("category", category) }
            .build()

        Log.d(TAG, "fetchMagnets: authority=$authority anime='$animeName' anilistId=$anilistId animeRomaji='$animeRomaji' category='$category' uri=$queryUri")

        var cursor: Cursor? = null
        val jsonData: String? = try {
            val startTime = System.currentTimeMillis()
            cursor = context.contentResolver.query(queryUri, null, null, null, null)
            val elapsed = System.currentTimeMillis() - startTime
            Log.d(TAG, "fetchMagnets: query returned in ${elapsed}ms, cursor=${cursor != null}, count=${cursor?.count ?: 0}")
            if (cursor != null && cursor.moveToFirst()) {
                val colIdx = cursor.getColumnIndex("data")
                Log.d(TAG, "fetchMagnets: cursor columns=${cursor.columnCount}, data column index=$colIdx")
                if (colIdx != -1) {
                    val data = cursor.getString(colIdx)
                    Log.d(TAG, "fetchMagnets: data length=${data?.length ?: 0}, preview=${data?.take(200)}")
                    data
                } else {
                    Log.w(TAG, "fetchMagnets: no 'data' column in cursor")
                    null
                }
            } else {
                Log.w(TAG, "fetchMagnets: cursor null or empty for anime='$animeName'")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchMagnets: ContentProvider query failed for authority=$authority anime='$animeName'", e)
            null
        } finally {
            cursor?.close()
        }

        if (jsonData == null) {
            Log.w(TAG, "fetchMagnets: no data returned (authority=$authority, anime='$animeName')")
            return null
        }
        return try {
            parseMagnets(jsonData, anilistId)
        } catch (e: Exception) {
            Log.e(TAG, "fetchMagnets: parseMagnets threw for authority=$authority anime='$animeName'", e)
            null
        }
    }

    fun fetchStreamUrl(authority: String, anilistId: Int, episode: Int, lang: String): StreamUrlResult? {
        val providerUri = Uri.parse("content://$authority/$SCRAPE_PATH")
        val queryUri = providerUri.buildUpon()
            .appendQueryParameter("anilistId", anilistId.toString())
            .appendQueryParameter("episode", episode.toString())
            .appendQueryParameter("lang", lang)
            .build()

        Log.d(TAG, "fetchStreamUrl: authority=$authority anilistId=$anilistId episode=$episode lang=$lang uri=$queryUri")

        var cursor: Cursor? = null
        val jsonData: String? = try {
            cursor = context.contentResolver.query(queryUri, null, null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val colIdx = cursor.getColumnIndex("data")
                if (colIdx != -1) cursor.getString(colIdx) else null
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "fetchStreamUrl: query failed", e)
            null
        } finally {
            cursor?.close()
        }

        if (jsonData == null) {
            Log.w(TAG, "fetchStreamUrl: no data returned")
            return null
        }

        return try {
            val json = JSONObject(jsonData)
            if (json.has("error")) {
                Log.w(TAG, "fetchStreamUrl: extension error: ${json.getString("error")}")
                null
            } else {
                val url = json.optString("url", null)?.ifBlank { null } ?: return null
                val headers = mutableMapOf<String, String>()
                json.optJSONObject("headers")?.let { h ->
                    val iter = h.keys()
                    while (iter.hasNext()) {
                        val key = iter.next()
                        val value = h.optString(key, "")
                        if (value.isNotBlank()) headers[key] = value
                    }
                }
                Log.d(TAG, "fetchStreamUrl: url=${url.take(80)}... headers=$headers")
                val subtitles = mutableListOf<Track>()
                json.optJSONArray("subtitles")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val sub = arr.optJSONObject(i) ?: continue
                        val subUrl = sub.optString("url", null)?.ifBlank { null } ?: continue
                        val lang = sub.optString("label", sub.optString("language", ""))
                        if (lang.isNotBlank()) {
                            subtitles.add(Track(subUrl, lang))
                        }
                    }
                }
                Log.d(TAG, "fetchStreamUrl: ${subtitles.size} subtitle tracks")

                // Parse the streams array (both sub+dub, user preference first)
                val streams = mutableListOf<StreamEntry>()
                json.optJSONArray("streams")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val stream = arr.optJSONObject(i) ?: continue
                        val streamUrl = stream.optString("url", null)?.ifBlank { null } ?: continue
                        val streamLang = stream.optString("lang", "")
                        val isDefault = stream.optBoolean("default", false)
                        val streamHeaders = mutableMapOf<String, String>()
                        stream.optJSONObject("headers")?.let { h ->
                            val iter = h.keys()
                            while (iter.hasNext()) {
                                val key = iter.next()
                                val value = h.optString(key, "")
                                if (value.isNotBlank()) streamHeaders[key] = value
                            }
                        }
                        val streamSubs = mutableListOf<Track>()
                        stream.optJSONArray("subtitles")?.let { subArr ->
                            for (j in 0 until subArr.length()) {
                                val sub = subArr.optJSONObject(j) ?: continue
                                val subUrl = sub.optString("url", null)?.ifBlank { null } ?: continue
                                val subLang = sub.optString("label", sub.optString("language", ""))
                                if (subLang.isNotBlank()) {
                                    streamSubs.add(Track(subUrl, subLang))
                                }
                            }
                        }
                        streams.add(StreamEntry(streamLang, isDefault, streamUrl, streamHeaders, streamSubs))
                    }
                }
                Log.d(TAG, "fetchStreamUrl: ${streams.size} streams (langs: ${streams.map { it.lang }})")

                StreamUrlResult(url, headers, subtitles, streams)
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchStreamUrl: parse failed", e)
            null
        }
    }

    private fun parseMagnets(jsonData: String, anilistId: Int): MagnetData {
        Log.d(TAG, "parseMagnets: parsing ${jsonData.length} chars for anilistId=$anilistId")
        return try {
            val jsonObject = JSONObject(jsonData)
            if (jsonObject.has("error")) {
                Log.w(TAG, "parseMagnets: extension error: ${jsonObject.getString("error")}")
                return MagnetData(emptyList(), false)
            }

            if (jsonObject.has("episodes")) {
                val episodesArray = jsonObject.optJSONArray("episodes")
                if (episodesArray != null && episodesArray.length() > 0) {
                    val episodes = mutableListOf<MagnetEpisode>()
                    for (i in 0 until episodesArray.length()) {
                        val epObj = episodesArray.optJSONObject(i)
                        if (epObj != null) {
                            val number = parseEpisodeNumber(epObj.optString("number", ""))
                            if (number > 0) {
                                episodes.add(MagnetEpisode(number, "", ""))
                            }
                        }
                    }
                    if (episodes.isNotEmpty()) {
                        Log.d(TAG, "parseMagnets: parsed ${episodes.size} episodes from episodes list format")
                        return MagnetData(episodes.sortedBy { it.episode }, false)
                    }
                }
                Log.w(TAG, "parseMagnets: 'episodes' key present but no valid entries")
                return MagnetData(emptyList(), false)
            }

            val episodes = mutableListOf<MagnetEpisode>()
            val keys = jsonObject.keys()
            var hasMultipleQualities = false
            var keyCount = 0

            while (keys.hasNext()) {
                val epNum = keys.next()
                val value = jsonObject.get(epNum)
                keyCount++

                val epNumber = parseEpisodeNumber(epNum)
                Log.v(TAG, "parseMagnets: key='$epNum' -> episode=$epNumber, valueType=${value?.javaClass?.simpleName}")

                if (value is JSONObject) {
                    hasMultipleQualities = true
                    val qualityMap = value as JSONObject
                    val qKeys = qualityMap.keys()
                    var bestQuality = ""
                    var bestMagnet = ""
                    var qCount = 0
                    while (qKeys.hasNext()) {
                        val q = qKeys.next()
                        val magnet = qualityMap.getString(q)
                        qCount++
                        val qNum = q.filter { it.isDigit() }.toIntOrNull() ?: 0
                        val bestNum = bestQuality.filter { it.isDigit() }.toIntOrNull() ?: 0
                        if (qNum > bestNum) {
                            bestQuality = q
                            bestMagnet = magnet
                        }
                        if (bestQuality.isEmpty()) {
                            bestQuality = q
                            bestMagnet = magnet
                        }
                    }
                    Log.d(TAG, "parseMagnets: ep=$epNumber qualities=$qCount selected='$bestQuality' magnet=${bestMagnet.take(80)}...")
                    episodes.add(MagnetEpisode(epNumber, bestMagnet, bestQuality))
                } else if (value is String) {
                    Log.d(TAG, "parseMagnets: ep=$epNumber single magnet=${(value as String).take(80)}...")
                    episodes.add(MagnetEpisode(epNumber, value as String, ""))
                } else {
                    Log.w(TAG, "parseMagnets: unexpected value type for key='$epNum': ${value?.javaClass?.name}")
                }
            }

            Log.d(TAG, "parseMagnets: processed $keyCount keys, ${episodes.size} valid episodes, hasMultipleQualities=$hasMultipleQualities")
            if (episodes.isEmpty()) {
                Log.w(TAG, "parseMagnets: no valid episodes parsed, returning empty")
                return MagnetData(emptyList(), false)
            }
            val isSingleTorrent = !hasMultipleQualities && episodes.size == 1
            Log.d(TAG, "parseMagnets: sorted ${episodes.size} episodes, isSingleTorrent=$isSingleTorrent")
            MagnetData(episodes.sortedBy { it.episode }, isSingleTorrent)
        } catch (_: JSONException) {
            Log.d(TAG, "parseMagnets: JSON object parse failed, trying JSON array (SeaDex-style)")
            try {
                val jsonArray = JSONArray(jsonData)
                Log.d(TAG, "parseMagnets: JSON array with ${jsonArray.length()} elements")
                if (jsonArray.length() == 0) {
                    Log.w(TAG, "parseMagnets: empty JSON array")
                    return MagnetData(emptyList(), false)
                }

                val magnets = (0 until jsonArray.length()).mapNotNull { idx ->
                    try {
                        val m = jsonArray.getString(idx)
                        Log.v(TAG, "parseMagnets: array[$idx]=${m.take(80)}...")
                        m
                    } catch (_: Exception) {
                        Log.w(TAG, "parseMagnets: array[$idx] is not a string, skipping")
                        null
                    }
                }
                if (magnets.isEmpty()) {
                    Log.w(TAG, "parseMagnets: no valid magnets in array")
                    return MagnetData(emptyList(), false)
                }
                Log.d(TAG, "parseMagnets: first magnet: ${magnets.first().take(80)}...")
                MagnetData(listOf(MagnetEpisode(0, magnets.first(), "")), true)
            } catch (e2: Exception) {
                Log.e(TAG, "parseMagnets: both object and array parse failed", e2)
                MagnetData(emptyList(), false)
            }
        }
    }

    /**
     * Extract an episode number from a JSON object key.
     *
     * Extensions use a variety of key formats: "1", "01", "Episode 1",
     * "EP 01", " - 01 -", etc. Falling back to 0 on [toIntOrNull] causes
     * real episode numbers to be lost (e.g. "Episode 1" -> 0), which made
     * [getMagnetForEpisode] miss the requested episode.
     */
    private fun parseEpisodeNumber(rawKey: String): Int {
        rawKey.trim().toIntOrNull()?.let {
            Log.v(TAG, "parseEpisodeNumber: '$rawKey' -> $it (direct parse)")
            return it
        }
        val match = Regex("\\d+").find(rawKey)
        val result = match?.value?.toIntOrNull() ?: 0
        Log.v(TAG, "parseEpisodeNumber: '$rawKey' -> $result (regex match=${match?.value})")
        return result
    }
}