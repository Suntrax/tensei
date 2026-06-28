package com.blissless.tensei.torrent

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

data class DetectedMagnetExtension(
    val packageName: String,
    val name: String,
    val authority: String
)

class MagnetExtensionClient(private val context: Context) {

    companion object {
        private const val TAG = "MagnetExtensionClient"
        private const val BEACON_ACTION = "com.blissless.animeclient.EXTENSION_BEACON"
        private const val PROVIDER_SUFFIX = ".provider"
        private const val SCRAPE_PATH = "scrape"
    }

    fun detectExtensions(): List<DetectedMagnetExtension> {
        Log.d(TAG, "detectExtensions: scanning for magnet extensions")
        val beaconIntent = Intent(BEACON_ACTION)
        val resolveInfoList = context.packageManager.queryBroadcastReceivers(beaconIntent, 0)
        Log.d(TAG, "detectExtensions: found ${resolveInfoList.size} potential beacon receivers")
        val result = resolveInfoList.mapNotNull { info ->
            val packageName = info.activityInfo.packageName
            val label = info.loadLabel(context.packageManager).toString()
            val matches = label.startsWith("Tensei: ", ignoreCase = true) || label.startsWith("Anime: ", ignoreCase = true)
            Log.d(TAG, "detectExtensions: pkg=$packageName label=$label matches=$matches")
            if (matches) {
                DetectedMagnetExtension(packageName, label, "$packageName$PROVIDER_SUFFIX")
            } else null
        }
        Log.d(TAG, "detectExtensions: returning ${result.size} extensions: ${result.map { "${it.name} (${it.authority})" }}")
        return result
    }

    fun fetchMagnets(authority: String, anilistId: Int, animeName: String): MagnetData? {
        val providerUri = Uri.parse("content://$authority/$SCRAPE_PATH")
        val queryUri = providerUri.buildUpon()
            .appendQueryParameter("anime", animeName)
            .appendQueryParameter("anilistId", anilistId.toString())
            .build()

        Log.d(TAG, "fetchMagnets: querying authority=$authority anilistId=$anilistId animeName=$animeName")
        Log.d(TAG, "fetchMagnets: full URI=$queryUri")

        var cursor: Cursor? = null
        val jsonData: String? = try {
            val startTime = System.currentTimeMillis()
            cursor = context.contentResolver.query(queryUri, null, null, null, null)
            val elapsed = System.currentTimeMillis() - startTime
            Log.d(TAG, "fetchMagnets: query took ${elapsed}ms, cursor=${cursor != null}")
            if (cursor != null && cursor.moveToFirst()) {
                Log.d(TAG, "fetchMagnets: cursor has ${cursor.count} rows")
                val colIdx = cursor.getColumnIndex("data")
                Log.d(TAG, "fetchMagnets: data column index=$colIdx")
                if (colIdx != -1) {
                    val data = cursor.getString(colIdx)
                    Log.d(TAG, "fetchMagnets: returned data length=${data?.length ?: 0}")
                    data
                } else {
                    Log.w(TAG, "fetchMagnets: no 'data' column in cursor")
                    null
                }
            } else {
                Log.w(TAG, "fetchMagnets: cursor is null or empty")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchMagnets: ContentProvider query failed", e)
            null
        } finally {
            cursor?.close()
        }

        if (jsonData == null) {
            Log.w(TAG, "fetchMagnets: no JSON data returned for $animeName")
            return null
        }
        Log.d(TAG, "fetchMagnets: raw JSON data: ${jsonData.take(500)}")
        return parseMagnets(jsonData, anilistId)
    }

    private fun parseMagnets(jsonData: String, anilistId: Int): MagnetData {
        Log.d(TAG, "parseMagnets: parsing JSON for anilistId=$anilistId, data length=${jsonData.length}")
        return try {
            val jsonObject = JSONObject(jsonData)
            if (jsonObject.has("error")) {
                Log.w(TAG, "parseMagnets: extension returned error: ${jsonObject.getString("error")}")
                return MagnetData(emptyList(), false)
            }

            val episodes = mutableListOf<MagnetEpisode>()
            val keys = jsonObject.keys()
            var hasMultipleQualities = false
            var epCount = 0

            while (keys.hasNext()) {
                val epNum = keys.next()
                val value = jsonObject.get(epNum)
                epCount++

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
                        val qNum = q.filter { it.isDigit() }.toIntOrNull() ?: 0
                        val bestNum = bestQuality.filter { it.isDigit() }.toIntOrNull() ?: 0
                        qCount++
                        if (qNum > bestNum) {
                            bestQuality = q
                            bestMagnet = magnet
                        }
                        if (bestQuality.isEmpty()) {
                            bestQuality = q
                            bestMagnet = magnet
                        }
                    }
                    Log.d(TAG, "parseMagnets: ep=$epNum has $qCount qualities, selected $bestQuality")
                    episodes.add(MagnetEpisode(epNum.toIntOrNull() ?: 0, bestMagnet, bestQuality))
                } else if (value is String) {
                    Log.d(TAG, "parseMagnets: ep=$epNum single magnet (first 80: ${(value as String).take(80)})")
                    episodes.add(MagnetEpisode(epNum.toIntOrNull() ?: 0, value as String, ""))
                } else {
                    Log.w(TAG, "parseMagnets: unexpected value type for ep=$epNum: ${value?.javaClass?.name}")
                }
            }

            Log.d(TAG, "parseMagnets: parsed $epCount entries, ${episodes.size} valid episodes, hasMultipleQualities=$hasMultipleQualities")
            if (episodes.isEmpty()) {
                Log.w(TAG, "parseMagnets: no valid episodes parsed")
                return MagnetData(emptyList(), false)
            }
            val isSingleTorrent = !hasMultipleQualities && episodes.size == 1
            Log.d(TAG, "parseMagnets: sorted ${episodes.size} episodes, isSingleTorrent=$isSingleTorrent")
            MagnetData(episodes.sortedBy { it.episode }, isSingleTorrent)
        } catch (_: JSONException) {
            Log.d(TAG, "parseMagnets: not a JSON object, trying JSON array")
            try {
                val jsonArray = JSONArray(jsonData)
                Log.d(TAG, "parseMagnets: JSON array with ${jsonArray.length()} elements")
                if (jsonArray.length() == 0) {
                    Log.w(TAG, "parseMagnets: empty JSON array")
                    return MagnetData(emptyList(), false)
                }

                val magnets = (0 until jsonArray.length()).map { jsonArray.getString(it) }
                Log.d(TAG, "parseMagnets: parsed array, first magnet: ${magnets.first().take(80)}")
                MagnetData(listOf(MagnetEpisode(0, magnets.first(), "")), true)
            } catch (e2: Exception) {
                Log.e(TAG, "parseMagnets: failed to parse JSON as object or array", e2)
                MagnetData(emptyList(), false)
            }
        }
    }

    private class JSONException : Exception()
}
