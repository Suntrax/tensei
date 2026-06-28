package com.blissless.tensei.torrent

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.util.Log
import org.json.JSONArray
import org.json.JSONException
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

    fun fetchMagnets(authority: String, anilistId: Int, animeName: String): MagnetData? {
        val providerUri = Uri.parse("content://$authority/$SCRAPE_PATH")
        val queryUri = providerUri.buildUpon()
            .appendQueryParameter("anime", animeName)
            .appendQueryParameter("anilistId", anilistId.toString())
            .build()

        Log.d(TAG, "Querying: $queryUri")

        var cursor: Cursor? = null
        val jsonData: String? = try {
            cursor = context.contentResolver.query(queryUri, null, null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val colIdx = cursor.getColumnIndex("data")
                if (colIdx != -1) cursor.getString(colIdx) else null
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "ContentProvider query failed", e)
            null
        } finally {
            cursor?.close()
        }

        if (jsonData == null) {
            Log.w(TAG, "No data returned from extension (authority=$authority, anime='$animeName')")
            return null
        }
        return try {
            parseMagnets(jsonData, anilistId)
        } catch (e: Exception) {
            // Never let a parse failure propagate and kill the caller.
            Log.e(TAG, "parseMagnets threw for authority=$authority anime='$animeName'", e)
            null
        }
    }

    private fun parseMagnets(jsonData: String, anilistId: Int): MagnetData {
        return try {
            val jsonObject = JSONObject(jsonData)
            if (jsonObject.has("error")) {
                Log.w(TAG, "Extension error: ${jsonObject.getString("error")}")
                return MagnetData(emptyList(), false)
            }

            val episodes = mutableListOf<MagnetEpisode>()
            val keys = jsonObject.keys()
            var hasMultipleQualities = false

            while (keys.hasNext()) {
                val epNum = keys.next()
                val value = jsonObject.get(epNum)

                val epNumber = parseEpisodeNumber(epNum)

                if (value is JSONObject) {
                    hasMultipleQualities = true
                    val qualityMap = value as JSONObject
                    val qKeys = qualityMap.keys()
                    var bestQuality = ""
                    var bestMagnet = ""
                    while (qKeys.hasNext()) {
                        val q = qKeys.next()
                        val magnet = qualityMap.getString(q)
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
                    episodes.add(MagnetEpisode(epNumber, bestMagnet, bestQuality))
                } else if (value is String) {
                    episodes.add(MagnetEpisode(epNumber, value as String, ""))
                }
            }

            if (episodes.isEmpty()) return MagnetData(emptyList(), false)
            MagnetData(episodes.sortedBy { it.episode }, !hasMultipleQualities && episodes.size == 1)
        } catch (_: JSONException) {
            // SubsPlease-style object parse failed — try SeaDex-style JSON array.
            try {
                val jsonArray = JSONArray(jsonData)
                if (jsonArray.length() == 0) return MagnetData(emptyList(), false)

                val magnets = (0 until jsonArray.length()).mapNotNull { idx ->
                    try { jsonArray.getString(idx) } catch (_: Exception) { null }
                }
                if (magnets.isEmpty()) return MagnetData(emptyList(), false)
                MagnetData(listOf(MagnetEpisode(0, magnets.first(), "")), true)
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to parse JSON (object and array both failed)", e2)
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
        rawKey.trim().toIntOrNull()?.let { return it }
        // Pull the first run of digits out of the key (handles "Episode 1", "EP01", "- 01 -")
        val match = Regex("\\d+").find(rawKey)
        return match?.value?.toIntOrNull() ?: 0
    }
}