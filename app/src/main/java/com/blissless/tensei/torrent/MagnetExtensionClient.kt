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
        val beaconIntent = Intent(BEACON_ACTION)
        val resolveInfoList = context.packageManager.queryBroadcastReceivers(beaconIntent, 0)
        return resolveInfoList.mapNotNull { info ->
            val packageName = info.activityInfo.packageName
            val label = info.loadLabel(context.packageManager).toString()
            if (label.startsWith("Tensei: ", ignoreCase = true) || label.startsWith("Anime: ", ignoreCase = true)) {
                DetectedMagnetExtension(packageName, label, "$packageName$PROVIDER_SUFFIX")
            } else null
        }
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

        if (jsonData == null) return null
        return parseMagnets(jsonData, anilistId)
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
                    episodes.add(MagnetEpisode(epNum.toIntOrNull() ?: 0, bestMagnet, bestQuality))
                } else if (value is String) {
                    episodes.add(MagnetEpisode(epNum.toIntOrNull() ?: 0, value as String, ""))
                }
            }

            if (episodes.isEmpty()) return MagnetData(emptyList(), false)
            MagnetData(episodes.sortedBy { it.episode }, !hasMultipleQualities && episodes.size == 1)
        } catch (_: JSONException) {
            try {
                val jsonArray = JSONArray(jsonData)
                if (jsonArray.length() == 0) return MagnetData(emptyList(), false)

                val magnets = (0 until jsonArray.length()).map { jsonArray.getString(it) }
                MagnetData(listOf(MagnetEpisode(0, magnets.first(), "")), true)
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to parse JSON", e2)
                MagnetData(emptyList(), false)
            }
        }
    }

    private class JSONException : Exception()
}
