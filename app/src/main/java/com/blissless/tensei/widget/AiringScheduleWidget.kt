package com.blissless.tensei.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.components.CircleIconButton
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.blissless.tensei.MainActivity
import com.blissless.tensei.R
import com.blissless.tensei.data.models.isAdultContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import java.util.concurrent.TimeUnit
import androidx.core.content.edit
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.blissless.tensei.util.ErrorHandler

private const val PREFS_NAME = "airing_schedule_widget"
private const val ANILIST_PREFS = "anilist_prefs"
private const val KEY_DATA = "schedule_data"
private const val KEY_UPD = "last_update"
private const val KEY_REFRESH_TIME = "last_refresh_time"
private const val KEY_WAS_AUTH = "was_authed"
private const val WORK_PERIODIC = "airing_schedule_widget_periodic"
private const val WORK_NOW = "airing_schedule_widget_now"
private const val COOLDOWN_MS = 30_000L

@Serializable
data class WidgetAiringEntry(
    val id: Int,
    val title: String,
    val airingEpisode: Int,
    val airingAt: Long,
    val timeUntilAiring: Long? = null,
    val cover: String = "",
    val averageScore: Int? = null,
    val dayOfWeek: Int = 0,
    val userStatus: String? = null,
    val isAdult: Boolean = false,
    val titleEnglish: String? = null,
    val titleRomaji: String? = null,
    val genres: List<String> = emptyList()
)

@Serializable
data class WidgetScheduleData(
    val entries: List<WidgetAiringEntry>,
    val lastUpdateTime: Long = 0
)

private val statusColors = mapOf(
    "CURRENT" to Color(0xFF2196F3),
    "PLANNING" to Color(0xFF9C27B0),
    "COMPLETED" to Color(0xFF4CAF50),
    "PAUSED" to Color(0xFFFFC107),
    "DROPPED" to Color(0xFFF44336),
    "REPEATING" to Color(0xFF00BCD4)
)
@Composable
@Suppress("RestrictedApi")
private fun cp(color: Color): ColorProvider = ColorProvider(color)

object AiringScheduleWidget : GlanceAppWidget() {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var data = loadData(prefs)
        val token = context.getSharedPreferences(ANILIST_PREFS, Context.MODE_PRIVATE)
            .getString("auth_token", null)
        val wasAuthed = prefs.getBoolean(KEY_WAS_AUTH, false)
        val isAuthed = token != null

        if (data.entries.isEmpty() || isAuthed != wasAuthed) {
            try {
                val fresh = withContext(Dispatchers.IO) {
                    WidgetScheduleFetcher.quickFetch(token)
                }
                if (fresh.entries.isNotEmpty()) {
                    saveWidgetData(context, fresh, isAuthed)
                    withContext(Dispatchers.IO) { cacheCovers(context, fresh.entries) }
                    data = fresh
                }
            } catch (_: Exception) {
            }
        }

        val coverCache = withContext(Dispatchers.IO) {
            val cache = mutableMapOf<Int, Bitmap?>()
            data.entries.forEach { entry ->
                val file = File(context.cacheDir, "widget_cover_${entry.id}.jpg")
                if (file.exists() && file.length() > 100) {
                    try {
                        val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                        val bm = BitmapFactory.decodeFile(file.absolutePath, opts)
                        if (bm != null) cache[entry.id] = bm
                    } catch (e: Exception) { ErrorHandler.ignore("AiringScheduleWidget", "best-effort operation failed", e) }
                }
            }
            cache
        }

        val userPrefs = context.getSharedPreferences(ANILIST_PREFS, Context.MODE_PRIVATE)
        val hideAdult = userPrefs.getBoolean("hide_adult_content", true)
        val preferEnglish = userPrefs.getBoolean("prefer_english_titles", true)
        val themeMode = userPrefs.getString("theme_mode", "system") ?: "system"
        val isDark = when (themeMode) {
            "light" -> false
            "oled", "dark" -> true
            else -> {
                val nightMode = context.resources.configuration.uiMode and
                        android.content.res.Configuration.UI_MODE_NIGHT_MASK
                nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
        }
        val isOled = themeMode == "oled"

        if (data.entries.isEmpty() || isStale(data.lastUpdateTime))
            triggerNow(context, bypassCooldown = true)
        schedulePeriodic(context)

        provideContent { WidgetContent(context, data, coverCache, hideAdult, preferEnglish, isDark, isOled) }
    }

    private fun loadData(prefs: android.content.SharedPreferences): WidgetScheduleData {
        val s = prefs.getString(KEY_DATA, null) ?: return WidgetScheduleData(emptyList())
        return try {
            json.decodeFromString<WidgetScheduleData>(s)
        } catch (_: Exception) {
            WidgetScheduleData(emptyList())
        }
    }

    private fun isStale(lastUpdate: Long) = lastUpdate == 0L || System.currentTimeMillis() - lastUpdate > 300_000

    fun triggerNow(context: Context, bypassCooldown: Boolean = false) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val last = prefs.getLong(KEY_REFRESH_TIME, 0)
        if (!bypassCooldown && last != 0L && now - last < COOLDOWN_MS) {
            return
        }
        prefs.edit { putLong(KEY_REFRESH_TIME, now) }
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NOW, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<AiringScheduleWorker>().build()
        )
    }

    private fun schedulePeriodic(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<AiringScheduleWorker>(30, TimeUnit.MINUTES).build()
        )
    }

    @Composable
    fun WidgetContent(context: Context, data: WidgetScheduleData, coverCache: Map<Int, Bitmap?>, hideAdult: Boolean = false, preferEnglish: Boolean = true, isDark: Boolean = true, isOled: Boolean = false) {
        val cal = Calendar.getInstance()
        val dow = cal.get(Calendar.DAY_OF_WEEK) - 1
        val dayNames = listOf("Sunday","Monday","Tuesday","Wednesday","Thursday","Friday","Saturday")

        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val todayStart = cal.timeInMillis / 1000
        val todayEnd = todayStart + 86400

        val nowTs = System.currentTimeMillis() / 1000
        val items = data.entries
            .filter { if (hideAdult) !isAdultContent(it.isAdult, it.genres) else true }
            .filter { it.dayOfWeek == dow && it.airingAt >= todayStart && it.airingAt < todayEnd }
            .sortedBy { it.airingAt }

        val ago = if (data.lastUpdateTime > 0) {
            val d = System.currentTimeMillis() - data.lastUpdateTime
            when { d < 60_000 -> "just now"; d < 3_600_000 -> "${d / 60_000}m ago"; else -> "${d / 3_600_000}h ago" }
        } else "never"

        val bgColor = when {
            isOled -> Color(0xFF000000)
            isDark -> Color(0xFF0D0D0D)
            else -> Color(0xFFF5F5F5)
        }
        val itemBgColor = when {
            isOled -> Color(0xFF0A0A0A)
            isDark -> Color(0xFF1A1A1A)
            else -> Color(0xFFFFFFFF)
        }
        val titleColor = if (isDark) Color.White else Color(0xFF1A1A1A)
        val mutedColor = if (isDark) Color(0xFF9E9E9E) else Color(0xFF757575)
        val dimColor = if (isDark) Color(0xFF616161) else Color(0xFF9E9E9E)
        val faintColor = if (isDark) Color(0xFF424242) else Color(0xFFBDBDBD)

        Column(
            modifier = GlanceModifier.fillMaxSize()
                .background(cp(bgColor))
                .padding(start = 14.dp, top = 14.dp, end = 14.dp, bottom = 12.dp)
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(dayNames[dow], style = TextStyle(
                        color = cp(titleColor), fontSize = 16.sp, fontWeight = FontWeight.Bold
                    ))
                    Text("${items.size} airing today", style = TextStyle(
                        color = cp(mutedColor), fontSize = 12.sp
                    ))
                }
                val refreshBg = cp(if (isDark) Color(0x33FFFFFF) else Color(0x33000000))
                val refreshIconTint = cp(if (isDark) Color.White else Color(0xFF616161))
                CircleIconButton(
                    imageProvider = ImageProvider(R.drawable.ic_refresh),
                    contentDescription = "Refresh",
                    onClick = action("refresh") { triggerNow(context) },
                    modifier = GlanceModifier.size(32.dp),
                    backgroundColor = refreshBg,
                    contentColor = refreshIconTint
                )
            }
            Spacer(GlanceModifier.height(10.dp))

            LazyColumn(
                modifier = GlanceModifier.defaultWeight().fillMaxWidth()
            ) {
                if (items.isEmpty()) {
                    item {
                        Box(
                            modifier = GlanceModifier.fillMaxWidth().padding(vertical = 20.dp),
                            contentAlignment = Alignment.Center,
                            content = @Composable { Text("No anime airing today", style = TextStyle(
                                color = cp(dimColor), fontSize = 14.sp
                            ))}
                        )
                    }
                } else {
                    items(items) { e ->
                        Column {
                            Box(
                                modifier = GlanceModifier.fillMaxWidth()
                                    .padding(horizontal = 2.dp)
                                    .background(cp(itemBgColor)),
                                content = @Composable {
                                    AiringItem(e, coverCache[e.id], nowTs, context, preferEnglish, isDark)
                                }
                            )
                            Spacer(GlanceModifier.height(4.dp))
                        }
                    }
                }
            }

            Spacer(GlanceModifier.height(6.dp))
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Updated $ago", style = TextStyle(
                    color = cp(dimColor), fontSize = 10.sp
                ))
                Spacer(GlanceModifier.defaultWeight())
                Text("Tensei", style = TextStyle(
                    color = cp(faintColor), fontSize = 10.sp, fontWeight = FontWeight.Bold
                ))
            }
            Spacer(GlanceModifier.height(4.dp))
        }
    }

    @Composable
    private fun AiringItem(e: WidgetAiringEntry, coverBitmap: Bitmap?, nowTs: Long, context: Context, preferEnglish: Boolean = true, isDark: Boolean = true) {
        fun String?.valid(): String? = this?.takeIf { it.isNotBlank() && it != "null" }
        val displayTitle = if (preferEnglish) e.titleEnglish.valid() ?: e.titleRomaji.valid() ?: e.title
                           else e.titleRomaji.valid() ?: e.titleEnglish.valid() ?: e.title
        val isPast = e.airingAt <= nowTs
        val displayTime = if (isPast) {
            val ago = nowTs - e.airingAt
            val h = (ago / 3600).toInt()
            val m = ((ago % 3600) / 60).toInt()
            when {
                h > 0 && m > 0 -> "${h}h ${m}m ago"
                h > 0 -> "${h}h ago"
                m > 0 -> "${m}m ago"
                else -> "just now"
            }
        } else {
            val diff = e.timeUntilAiring ?: (e.airingAt - nowTs)
            val h = (diff / 3600).toInt()
            val m = ((diff % 3600) / 60).toInt()
            when {
                h > 0 && m > 0 -> "in ${h}h ${m}m"
                h > 0 -> "in ${h}h"
                m > 0 -> "in ${m}m"
                else -> "< 1m"
            }
        }

        val score = e.averageScore?.let { " \u00b7 \u2605${it / 10}.${it % 10}" } ?: ""
        val pastTitleColor = if (isDark) Color(0xFF757575) else Color(0xFFBDBDBD)
        val currentTitleColor = if (isDark) Color.White else Color(0xFF1A1A1A)
        val titleColor = if (isPast) pastTitleColor else currentTitleColor
        val pastInfoColor = if (isDark) Color(0xFF555555) else Color(0xFF9E9E9E)
        val currentInfoColor = if (isDark) Color(0xFF9E9E9E) else Color(0xFF757575)
        val infoColor = if (isPast) pastInfoColor else currentInfoColor

        val statusColor = e.userStatus?.let { statusColors[it] }

        Row(
            modifier = GlanceModifier.fillMaxWidth()
                .padding(end = 14.dp)
                .clickable(action("open_${e.id}") {
                    val intent = Intent(context, MainActivity::class.java).apply {
                        putExtra("widget_anime_id", e.id)
                        putExtra("widget_anime_title", e.title)
                        putExtra("widget_anime_cover", e.cover)
                        putExtra("widget_anime_score", e.averageScore ?: 0)
                        putExtra("widget_anime_episode", e.airingEpisode)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    context.startActivity(intent)
                }),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (statusColor != null) {
                Box(modifier = GlanceModifier.width(3.dp).height(80.dp).background(cp(statusColor)), content = {})
                Spacer(GlanceModifier.width(4.dp))
            }
            val coverBg = if (isDark) Color(0xFF242424) else Color(0xFFE0E0E0)
            val coverPlaceholderText = if (isDark) Color(0xFF616161) else Color(0xFF9E9E9E)
            Box(
                modifier = GlanceModifier.size(56.dp, 80.dp)
                    .background(cp(coverBg)),
                contentAlignment = Alignment.Center,
                content = @Composable {
                    if (coverBitmap != null) {
                        Image(
                            provider = ImageProvider(coverBitmap),
                            contentDescription = "Cover",
                            modifier = GlanceModifier.fillMaxSize()
                        )
                    } else {
                        Text(displayTitle.take(1), style = TextStyle(
                            color = cp(coverPlaceholderText), fontSize = 18.sp
                        ))
                    }
                }
            )
            Spacer(GlanceModifier.width(10.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(displayTitle, style = TextStyle(
                    color = cp(titleColor), fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                ))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (statusColor != null) {
                    Text("\u25CF", style = TextStyle(
                        color = cp(statusColor), fontSize = 8.sp
                    ))
                    Spacer(GlanceModifier.width(4.dp))
                }
                Text("Ep ${e.airingEpisode}", style = TextStyle(
                    color = cp(infoColor), fontSize = 11.sp
                ))
                if (score.isNotEmpty()) {
                    Text(score, style = TextStyle(
                        color = cp(infoColor), fontSize = 11.sp
                    ))
                }
            }
            }
            Spacer(GlanceModifier.width(6.dp))
            Text(displayTime, style = TextStyle(
                color = cp(if (isPast) Color(0xFFF44336) else Color(0xFF4CAF50)),
                fontSize = 11.sp, fontWeight = FontWeight.Medium
            ))
            Spacer(GlanceModifier.width(4.dp))
        }
    }
}

class AiringScheduleWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = AiringScheduleWidget
}

private fun saveWidgetData(context: Context, data: WidgetScheduleData, isAuthed: Boolean = false) {
    val j = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    val saved = j.encodeToString(data)
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit(commit = true) {
        putString(KEY_DATA, saved)
            .putLong(KEY_UPD, System.currentTimeMillis())
            .putBoolean(KEY_WAS_AUTH, isAuthed)
    }
}

private fun roundCorners(bitmap: Bitmap): Bitmap {
    val out = createBitmap(bitmap.width, bitmap.height)
    val canvas = android.graphics.Canvas(out)
    canvas.drawColor(0xFF242424.toInt())
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    canvas.drawRoundRect(RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat()), 8f, 8f, paint)
    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    canvas.drawBitmap(bitmap, 0f, 0f, paint)
    if (bitmap != out) bitmap.recycle()
    return out
}

private suspend fun updateWidget(context: Context) {
    try {
        val glanceManager = GlanceAppWidgetManager(context)
        val glanceIds = glanceManager.getGlanceIds(AiringScheduleWidget::class.java)
        if (glanceIds.isNotEmpty()) {
            glanceIds.forEach { id -> AiringScheduleWidget.update(context, id) }
            return
        }
    } catch (_: Exception) {
    }
    try { AiringScheduleWidget.updateAll(context) } catch (_: Exception) {
    }
}

private fun cacheCovers(context: Context, entries: List<WidgetAiringEntry>) {
    entries.forEach { entry ->
        if (entry.cover.isEmpty()) return@forEach
        val file = File(context.cacheDir, "widget_cover_${entry.id}.jpg")
        if (file.exists() && file.length() > 500) return@forEach
        try {
            val conn = URL(entry.cover).openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            val bitmap = BitmapFactory.decodeStream(conn.inputStream)
            conn.disconnect()
            if (bitmap != null) {
                val w = bitmap.width
                val h = bitmap.height
                val scale = minOf(168f / w, 240f / h)
                val sw = (w * scale).toInt()
                val sh = (h * scale).toInt()
                val scaled = bitmap.scale(sw, sh)
                if (scaled != bitmap) bitmap.recycle()
                val rounded = roundCorners(scaled)
                FileOutputStream(file).use { rounded.compress(Bitmap.CompressFormat.JPEG, 80, it) }
                rounded.recycle()
            }
        } catch (_: Exception) {
        }
    }
}

class AiringScheduleWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val token = applicationContext.getSharedPreferences(ANILIST_PREFS, Context.MODE_PRIVATE)
                .getString("auth_token", null)
            val data = WidgetScheduleFetcher.fetch(applicationContext, token)
            cacheCovers(applicationContext, data.entries)
            saveWidgetData(applicationContext, data, token != null)
            updateWidget(applicationContext)
            Result.success()
        } catch (_: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}

object WidgetScheduleFetcher {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    fun quickFetch(authToken: String? = null): WidgetScheduleData {
        val ct = System.currentTimeMillis() / 1000
        val body = JSONObject().apply {
            put("query",
                $$"query($p:Int,$s:Int,$e:Int){Page(page:$p,perPage:50){airingSchedules(airingAt_greater:$s,airingAt_lesser:$e,sort:TIME){id airingAt episode timeUntilAiring mediaId media{id idMal title{romaji english}coverImage{extraLarge}episodes status averageScore genres seasonYear isAdult mediaListEntry{id status}}}}}"
            )
            put("variables", JSONObject(mapOf("p" to 1, "s" to (ct - 86400).toInt(), "e" to (ct + 86400).toInt())))
        }
        val resp = execute(body.toString(), authToken, shortTimeout = true)
        val parsed = parse(resp)
        return WidgetScheduleData(parsed, System.currentTimeMillis())
    }

    fun fetch(context: Context, authToken: String? = null): WidgetScheduleData {
        val ct = System.currentTimeMillis() / 1000
        val query =
            $$"query($p:Int,$s:Int,$e:Int){Page(page:$p,perPage:50){airingSchedules(airingAt_greater:$s,airingAt_lesser:$e,sort:TIME){id airingAt episode timeUntilAiring mediaId media{id idMal title{romaji english}coverImage{extraLarge}episodes status averageScore genres seasonYear isAdult mediaListEntry{id status}}}}}"
        val entries = mutableListOf<WidgetAiringEntry>()
        var page = 1
        while (page <= 5) {
            try {
                val body = JSONObject().apply {
                    put("query", query)
                    put("variables", JSONObject(mapOf("p" to page, "s" to (ct - 86400).toInt(), "e" to (ct + 7*86400).toInt())))
                }
                val resp = execute(body.toString(), authToken)
                val parsed = parse(resp)
                if (parsed.isEmpty()) { break }
                entries.addAll(parsed)
                if (parsed.size < 50) break
                page++
            } catch (_: Exception) {
                break
            }
        }

        val cached = loadCached(context)
        if (cached != null) {
            val newIds = entries.map { it.id }.toSet()
            val cachedPast = cached.entries.filter { cachedE ->
                cachedE.airingAt > (ct - 172800) &&
                cachedE.airingAt <= (ct + 86400) &&
                cachedE.dayOfWeek == Calendar.getInstance().apply { timeInMillis = ct * 1000L }.get(Calendar.DAY_OF_WEEK) - 1 &&
                cachedE.id !in newIds
            }
            if (cachedPast.isNotEmpty()) {
                entries.addAll(cachedPast)
            }
            entries.sortBy { it.airingAt }
        }

        if (entries.isEmpty()) {
            loadCached(context)?.let { return it }
        }
        return WidgetScheduleData(entries, System.currentTimeMillis())
    }

    private fun loadCached(context: Context): WidgetScheduleData? {
        val s = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_DATA, null) ?: return null
        return try { json.decodeFromString<WidgetScheduleData>(s) }
        catch (e: Exception) { ErrorHandler.report("AiringScheduleWidget", "operation failed, returning null", e); null }
    }

    private fun execute(body: String, authToken: String?, shortTimeout: Boolean = false): String {
        val conn = URL(com.blissless.tensei.network.Endpoints.AniList.GRAPHQL).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Accept", "application/json")
        if (authToken != null) {
            conn.setRequestProperty("Authorization", "Bearer $authToken")
        }
        conn.doOutput = true
        if (shortTimeout) {
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
        } else {
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
        }
        conn.outputStream.use { it.write(body.toByteArray()) }
        if (conn.responseCode != 200) throw Exception("HTTP ${conn.responseCode}")
        return conn.inputStream.bufferedReader().readText()
    }

    private fun parse(json: String): List<WidgetAiringEntry> {
        val root = JSONObject(json)
        val schedules = root.optJSONObject("data")?.optJSONObject("Page")?.optJSONArray("airingSchedules") ?: return emptyList()
        val r = mutableListOf<WidgetAiringEntry>()
        for (i in 0 until schedules.length()) {
            try {
                val s = schedules.getJSONObject(i)
                val m = s.optJSONObject("media") ?: continue
                val t = m.optJSONObject("title") ?: continue
                val titleEn = t.optString("english", "").takeIf { it.isNotBlank() && it != "null" }
                val titleRo = t.optString("romaji", "").takeIf { it.isNotBlank() && it != "null" }
                val title = titleEn ?: titleRo ?: "Unknown"
                val cover = m.optJSONObject("coverImage")?.optString("extraLarge", "") ?: ""
                val score = if (m.has("averageScore") && !m.isNull("averageScore")) m.getInt("averageScore") else null
                val ta = if (s.has("timeUntilAiring") && !s.isNull("timeUntilAiring")) s.getLong("timeUntilAiring") else null
                val status = m.optJSONObject("mediaListEntry")?.optString("status")?.takeIf { it.isNotEmpty() }
                val genres = mutableListOf<String>()
                m.optJSONArray("genres")?.let { arr ->
                    for (j in 0 until arr.length()) { genres.add(arr.optString(j, "")) }
                }
                val adult = m.optBoolean("isAdult", false)
                val cal = Calendar.getInstance().apply { timeInMillis = s.getLong("airingAt") * 1000L }
                r.add(WidgetAiringEntry(m.getInt("id"), title, s.getInt("episode"), s.getLong("airingAt"), ta, cover, score, cal.get(Calendar.DAY_OF_WEEK) - 1, status, adult, titleEn, titleRo, genres))
            } catch (e: Exception) { ErrorHandler.ignore("AiringScheduleWidget", "best-effort operation failed", e) }
        }
        return r
    }
}


