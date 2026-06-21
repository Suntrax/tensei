package com.blissless.tensei.ui.screens.airing

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.blissless.tensei.MainViewModel
import com.blissless.tensei.data.models.AiringScheduleAnime
import com.blissless.tensei.data.models.AnimeMedia
import com.blissless.tensei.data.models.ExploreAnime
import com.blissless.tensei.data.models.isAdultContent
import com.blissless.tensei.data.models.toDetailedAnimeData
import com.blissless.tensei.ui.components.rememberCinematicAnimation
import com.blissless.tensei.ui.screens.details.DetailedAnimeScreen
import com.blissless.tensei.ui.theme.StatusColors
import com.blissless.tensei.ui.theme.StatusLabels
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.absoluteValue
import kotlin.time.Duration.Companion.milliseconds

val DayNames = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
val DayAbbreviations = listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa")

private fun getDaysFromCurrentDay(currentDay: Int): List<Int> {
    return (0..6).map { offset -> (currentDay + offset) % 7 }
}

private sealed class TimelineItem {
    data class Anime(val data: AiringScheduleAnime, val isPast: Boolean) : TimelineItem()
    data class NowIndicator(val timeString: String) : TimelineItem()
    data class DayHeader(val dayIndex: Int, val dayName: String) : TimelineItem()
}

private fun easeOutCubic(t: Float): Float {
    val t1 = t - 1; return t1 * t1 * t1 + 1
}

@SuppressLint("FrequentlyChangingValue")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    viewModel: MainViewModel,
    isOled: Boolean = false,
    isVisible: Boolean = false,
    preventAutoSync: Boolean = true,
    hideAdultContent: Boolean = false,
    preferEnglishTitles: Boolean = true,
    isLoggedIn: Boolean = false,
    onPlayEpisode: (AnimeMedia, Int, String?) -> Unit = { _, _, _ -> },
    onAnimeDialogOpen: (Boolean) -> Unit = {},
    onCharacterClick: (Int) -> Unit = {},
    onStaffClick: (Int) -> Unit = {},
    onViewAllCast: (Int, String) -> Unit = { _, _ -> },
    onViewAllStaff: (Int, String) -> Unit = { _, _ -> },
    onViewAllRelations: (Int, String) -> Unit = { _, _ -> },
    onNoExtension: () -> Unit = {}
) {
    val airingList by viewModel.airingAnimeList.collectAsState()
    val scheduleByDay by viewModel.airingSchedule.collectAsState()
    val isLoading by viewModel.isLoadingSchedule.collectAsState()
    val localAnimeStatus by viewModel.localAnimeStatus.collectAsState()
    val apiError by viewModel.apiError.collectAsState()
    val isOffline by viewModel.isOffline.collectAsState()

    LaunchedEffect(airingList, scheduleByDay, isLoading) {
        val scheduleHasData = scheduleByDay.values.any { it.isNotEmpty() }
        if ((airingList.isEmpty() || !scheduleHasData) && !isLoading) viewModel.fetchAiringSchedule()
    }

    val scope = rememberCoroutineScope()
    val calendar = Calendar.getInstance()
    var currentDayOfWeek by remember { mutableIntStateOf(calendar.get(Calendar.DAY_OF_WEEK) - 1) }
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    var lastKnownDay by remember { mutableIntStateOf(currentDayOfWeek) }
    val orderedDays = remember(currentDayOfWeek) { getDaysFromCurrentDay(currentDayOfWeek) }
    var viewMode by remember { mutableIntStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }
    val listStateAllUpcoming = rememberLazyListState()
    val listStateByDay = rememberLazyListState()
    var visibleDayByScroll by remember { mutableIntStateOf(currentDayOfWeek) }
    var isProgrammaticScroll by remember { mutableStateOf(false) }
    var isInputLocked by remember { mutableStateOf(false) }
    var selectedDay by remember { mutableIntStateOf(currentDayOfWeek) }
    var selectedAnime by remember { mutableStateOf<ExploreAnime?>(null) }
    var showAnimeDialog by remember { mutableStateOf(false) }
    var firstOpenedAnime by remember { mutableStateOf<ExploreAnime?>(null) }
    val context = LocalContext.current
    val aniListFavorites by viewModel.aniListFavorites.collectAsState()
    val currentlyWatching by viewModel.currentlyWatching.collectAsState()
    val planningToWatch by viewModel.planningToWatch.collectAsState()
    val completed by viewModel.completed.collectAsState()
    val onHold by viewModel.onHold.collectAsState()
    val dropped by viewModel.dropped.collectAsState()

    val animeStatusMap = remember(currentlyWatching, planningToWatch, completed, onHold, dropped, localAnimeStatus) {
        val map = mutableMapOf<Int, String>()
        currentlyWatching.forEach { map[it.id] = "CURRENT" }
        planningToWatch.forEach { map[it.id] = "PLANNING" }
        completed.forEach { map[it.id] = "COMPLETED" }
        onHold.forEach { map[it.id] = "PAUSED" }
        dropped.forEach { map[it.id] = "DROPPED" }
        localAnimeStatus.forEach { (id, entry) -> if (!map.containsKey(id)) map[id] = entry.status }
        map
    }

    val animeProgressMap = remember(currentlyWatching, planningToWatch, completed, onHold, dropped, localAnimeStatus) {
        val map = mutableMapOf<Int, Int>()
        currentlyWatching.forEach { if (it.progress > 0) map[it.id] = it.progress }
        planningToWatch.forEach { if (it.progress > 0) map[it.id] = it.progress }
        completed.forEach { if (it.progress > 0) map[it.id] = it.progress }
        onHold.forEach { if (it.progress > 0) map[it.id] = it.progress }
        dropped.forEach { if (it.progress > 0) map[it.id] = it.progress }
        localAnimeStatus.forEach { (id, entry) -> if (entry.progress > 0 && !map.containsKey(id)) map[id] = entry.progress }
        map
    }

    val isFavoriteRateLimited by viewModel.isFavoriteRateLimited.collectAsState()
    var listVersion by remember { mutableIntStateOf(0) }
    val favoriteIds = remember(listVersion, aniListFavorites) { aniListFavorites.map { it.id }.toSet() }

    LaunchedEffect(currentlyWatching, planningToWatch, completed, onHold, dropped, aniListFavorites) { listVersion++ }
    LaunchedEffect(isFavoriteRateLimited) {
        if (isFavoriteRateLimited) Toast.makeText(context, "Please wait before toggling again", Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(isLoading) { if (!isLoading && isRefreshing) isRefreshing = false }
    LaunchedEffect(Unit) { if (!preventAutoSync) viewModel.fetchAiringSchedule() }
    LaunchedEffect(isLoading) { if (!isLoading && isRefreshing) isRefreshing = false }
    LaunchedEffect(Unit) {
        while (true) { delay(300000.milliseconds); currentTime = System.currentTimeMillis() / 1000; if (!preventAutoSync) viewModel.fetchAiringSchedule() }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000.milliseconds)
            val newTime = System.currentTimeMillis() / 1000
            currentTime = newTime
            val newCalendar = Calendar.getInstance()
            val newDay = newCalendar.get(Calendar.DAY_OF_WEEK) - 1
            if (newDay != lastKnownDay) { lastKnownDay = newDay; currentDayOfWeek = newDay; selectedDay = newDay; visibleDayByScroll = newDay }
        }
    }

    val startOfToday = remember(currentTime) {
        val cal = Calendar.getInstance(); cal.timeInMillis = currentTime * 1000L
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        cal.timeInMillis / 1000
    }
    val endOfToday = remember(startOfToday) { startOfToday + 86400L }
    val sevenDaysFromNow = remember(currentTime) { currentTime + 604800L }

    val filteredScheduleByDay = remember(scheduleByDay, startOfToday, endOfToday, currentTime, sevenDaysFromNow, currentDayOfWeek, hideAdultContent) {
        val result = mutableMapOf<Int, MutableList<AiringScheduleAnime>>()
        for (i in 0..6) result[i] = mutableListOf()
        scheduleByDay.values.flatten().filter { !hideAdultContent || (!isAdultContent(it.isAdult, it.genres)) }.forEach { anime ->
            val ac = Calendar.getInstance(); ac.timeInMillis = anime.airingAt * 1000L; val animeDow = ac.get(Calendar.DAY_OF_WEEK) - 1
            if (animeDow == currentDayOfWeek) { if (anime.airingAt in startOfToday..endOfToday) result[animeDow]?.add(anime) }
            else { if (anime.airingAt in currentTime..sevenDaysFromNow) result[animeDow]?.add(anime) }
        }
        result.forEach { (_, list) -> list.sortBy { it.airingAt } }; result
    }

    val allUpcomingTimelineItems = remember(filteredScheduleByDay, orderedDays, currentTime, currentDayOfWeek) {
        val items = mutableListOf<TimelineItem>()
        val tf = SimpleDateFormat("HH:mm", Locale.getDefault())
        val now = tf.format(Date(currentTime * 1000))
        orderedDays.forEach { dayIndex ->
            val dayAnime = filteredScheduleByDay[dayIndex] ?: emptyList()
            val isToday = dayIndex == currentDayOfWeek
            items.add(TimelineItem.DayHeader(dayIndex, DayNames[dayIndex]))
            if (dayAnime.isNotEmpty() && isToday) {
                dayAnime.filter { it.airingAt <= currentTime }.forEach { items.add(TimelineItem.Anime(it, true)) }
                items.add(TimelineItem.NowIndicator(now))
                dayAnime.filter { it.airingAt > currentTime }.forEach { items.add(TimelineItem.Anime(it, false)) }
            } else if (dayAnime.isNotEmpty()) {
                dayAnime.forEach { items.add(TimelineItem.Anime(it, false)) }
            }
        }
        items
    }

    val byDayTimelineItems = remember(filteredScheduleByDay, selectedDay, currentTime, currentDayOfWeek) {
        val items = mutableListOf<TimelineItem>()
        val tf = SimpleDateFormat("HH:mm", Locale.getDefault())
        val now = tf.format(Date(currentTime * 1000))
        val dayAnime = filteredScheduleByDay[selectedDay] ?: emptyList()
        val isToday = selectedDay == currentDayOfWeek
        if (dayAnime.isNotEmpty() && isToday) {
            dayAnime.filter { it.airingAt <= currentTime }.forEach { items.add(TimelineItem.Anime(it, true)) }
            items.add(TimelineItem.NowIndicator(now))
            dayAnime.filter { it.airingAt > currentTime }.forEach { items.add(TimelineItem.Anime(it, false)) }
        } else if (dayAnime.isNotEmpty()) {
            dayAnime.forEach { items.add(TimelineItem.Anime(it, false)) }
        }
        items
    }

    val dayToItemIndexMapAll = remember(allUpcomingTimelineItems) {
        val map = mutableMapOf<Int, Int>()
        allUpcomingTimelineItems.forEachIndexed { i, item -> if (item is TimelineItem.DayHeader) map[item.dayIndex] = i }
        map
    }

    val nowIndicatorIndexAll = remember(allUpcomingTimelineItems) { allUpcomingTimelineItems.indexOfFirst { it is TimelineItem.NowIndicator } }
    val nowIndicatorIndexByDay = remember(byDayTimelineItems) { byDayTimelineItems.indexOfFirst { it is TimelineItem.NowIndicator } }

    LaunchedEffect(listStateAllUpcoming.firstVisibleItemIndex, viewMode, currentDayOfWeek) {
        if (viewMode == 0 && !isProgrammaticScroll) {
            val fvi = listStateAllUpcoming.firstVisibleItemIndex
            if (fvi < allUpcomingTimelineItems.size) {
                var day = currentDayOfWeek
                for (i in fvi downTo 0) { val item = allUpcomingTimelineItems.getOrNull(i); if (item is TimelineItem.DayHeader) { day = item.dayIndex; break } }
                visibleDayByScroll = day
            }
        }
    }

    LaunchedEffect(isProgrammaticScroll) {
        if (isProgrammaticScroll) { delay(550.milliseconds); isProgrammaticScroll = false; isInputLocked = false }
    }

    LaunchedEffect(isVisible, preventAutoSync) {
        if (isVisible && !preventAutoSync) {
            val today = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1
            currentDayOfWeek = today; visibleDayByScroll = today; selectedDay = today; lastKnownDay = today
            val targetIndex = if (viewMode == 0) nowIndicatorIndexAll else nowIndicatorIndexByDay
            val ls = if (viewMode == 0) listStateAllUpcoming else listStateByDay
            if (targetIndex >= 0) ls.scrollToItem(targetIndex, scrollOffset = -100)
        }
    }

    val todayPastCount = filteredScheduleByDay[currentDayOfWeek]?.count { it.airingAt <= currentTime } ?: 0
    val totalUpcomingThisWeek = remember(filteredScheduleByDay, currentTime) { filteredScheduleByDay.values.sumOf { dl -> dl.count { it.airingAt > currentTime } } }
    val selectedDayPastCount = filteredScheduleByDay[selectedDay]?.count { it.airingAt <= currentTime } ?: 0
    val selectedDayFutureCount = filteredScheduleByDay[selectedDay]?.count { it.airingAt > currentTime } ?: 0

    val bg = MaterialTheme.colorScheme.background
    val onBg = MaterialTheme.colorScheme.onBackground

    Column(modifier = Modifier.fillMaxSize().background(bg).windowInsetsPadding(WindowInsets.statusBars)) {
        if (apiError != null || isOffline) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = if (isOffline) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.errorContainer,
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isOffline) Icons.Default.SignalWifiOff else Icons.Default.CloudOff,
                        contentDescription = null,
                        tint = if (isOffline) Color.White else MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isOffline) "No internet connection" else "AniList is currently unavailable",
                        color = if (isOffline) Color.White else MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Airing Schedule", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = onBg)
                Text(if (viewMode == 0) "$todayPastCount aired · $totalUpcomingThisWeek upcoming" else if (selectedDayPastCount > 0) "$selectedDayPastCount aired · $selectedDayFutureCount upcoming" else "$selectedDayFutureCount upcoming",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = viewMode == 0,
                onClick = { viewMode = 0; if (nowIndicatorIndexAll >= 0) scope.launch { listStateAllUpcoming.scrollToItem(nowIndicatorIndexAll, scrollOffset = -100) } },
                label = { Text("All Upcoming", color = if (viewMode == 0) MaterialTheme.colorScheme.onPrimary else Color.Unspecified) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(10.dp)
            )
            FilterChip(
                selected = viewMode == 1,
                onClick = { viewMode = 1; selectedDay = currentDayOfWeek; if (nowIndicatorIndexByDay >= 0) scope.launch { listStateByDay.scrollToItem(nowIndicatorIndexByDay, scrollOffset = -100) } },
                label = { Text("By Day", color = if (viewMode == 1) MaterialTheme.colorScheme.onPrimary else Color.Unspecified) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(10.dp)
            )
        }

        Spacer(Modifier.height(12.dp))

        val orderedDaysForSelector = if (viewMode == 0) orderedDays else orderedDays
        val currentDayForSelector = if (viewMode == 0) visibleDayByScroll else selectedDay

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            orderedDaysForSelector.forEach { dayIndex ->
                val isSelected = currentDayForSelector == dayIndex
                val isToday = currentDayOfWeek == dayIndex
                val dayAnimeCount = filteredScheduleByDay[dayIndex]?.size ?: 0
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        if (viewMode == 0) {
                            visibleDayByScroll = dayIndex
                            val targetIndex = if (isToday && nowIndicatorIndexAll >= 0) nowIndicatorIndexAll else dayToItemIndexMapAll[dayIndex] ?: 0
                            scope.launch { listStateAllUpcoming.scrollToItem(targetIndex, scrollOffset = if (isToday) -100 else 0) }
                        } else {
                            selectedDay = dayIndex
                            if (isToday && nowIndicatorIndexByDay >= 0) scope.launch { listStateByDay.scrollToItem(nowIndicatorIndexByDay, scrollOffset = -100) }
                            else scope.launch { listStateByDay.scrollToItem(0) }
                        }
                    },
                    label = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Text(DayAbbreviations[dayIndex], fontSize = 11.sp, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.Unspecified)
                            if (dayAnimeCount > 0) Text("$dayAnimeCount", fontSize = 9.sp,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.Unspecified)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    border = null
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { isRefreshing = true; currentTime = System.currentTimeMillis() / 1000; viewModel.fetchAiringSchedule(force = true) },
            modifier = Modifier.fillMaxSize()
        ) {
            if (isLoading && airingList.isEmpty()) {
                ScheduleLoadingSkeleton()
            } else {
                val timelineItems = if (viewMode == 0) allUpcomingTimelineItems else byDayTimelineItems
                val currentListState = if (viewMode == 0) listStateAllUpcoming else listStateByDay

                if (timelineItems.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No airing anime", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(if (viewMode == 1) "for this day" else "found", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                            Spacer(Modifier.height(16.dp))
                            Text("Swipe down to refresh", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                        }
                    }
                } else {
                    TimelineScheduleList(
                        timelineItems = timelineItems,
                        currentDayOfWeek = currentDayOfWeek,
                        preferEnglishTitles = preferEnglishTitles,
                        animeStatusMap = animeStatusMap,
                        listState = currentListState,
                        isVisible = isVisible,
                        onAnimeClick = { anime ->
                            val exploreAnime = ExploreAnime(
                                id = anime.id, title = anime.title, titleEnglish = anime.titleEnglish, cover = anime.cover,
                                banner = null, episodes = anime.episodes, latestEpisode = anime.airingEpisode,
                                averageScore = anime.averageScore, genres = anime.genres, year = anime.year, format = null, malId = anime.malId
                            )
                            firstOpenedAnime = exploreAnime; selectedAnime = exploreAnime; showAnimeDialog = true; onAnimeDialogOpen(true)
                        }
                    )
                }
            }
        }
    }

    if (showAnimeDialog && selectedAnime != null) {
        val currentStatus by remember(listVersion, selectedAnime!!.id) { derivedStateOf { animeStatusMap[selectedAnime!!.id] } }
        val currentProgress by remember(listVersion, selectedAnime!!.id) { derivedStateOf { animeProgressMap[selectedAnime!!.id] } }
        val isFavorite by remember(listVersion, favoriteIds, selectedAnime!!.id) { derivedStateOf { favoriteIds.contains(selectedAnime!!.id) } }
        DetailedAnimeScreen(
            anime = selectedAnime!!.toDetailedAnimeData(), viewModel = viewModel, isOled = isOled,
            currentStatus = currentStatus, currentProgress = currentProgress, isFavorite = isFavorite, isLoggedIn = isLoggedIn,
            onDismiss = { if (firstOpenedAnime != null && selectedAnime!!.id != firstOpenedAnime!!.id) selectedAnime = firstOpenedAnime else { showAnimeDialog = false; selectedAnime = null; firstOpenedAnime = null; onAnimeDialogOpen(false) } },
            onSwipeToClose = { showAnimeDialog = false; selectedAnime = null; firstOpenedAnime = null; onAnimeDialogOpen(false) },
            onPlayEpisode = { episode, _ ->
                val am = AnimeMedia(id = selectedAnime!!.id, title = selectedAnime!!.title, titleEnglish = selectedAnime!!.titleEnglish, cover = selectedAnime!!.cover, banner = selectedAnime!!.banner, progress = 0, totalEpisodes = selectedAnime!!.episodes, latestEpisode = selectedAnime!!.latestEpisode, status = "", averageScore = selectedAnime!!.averageScore, genres = selectedAnime!!.genres, listStatus = "", listEntryId = 0, year = selectedAnime!!.year, malId = selectedAnime!!.malId)
                onPlayEpisode(am, episode, null); showAnimeDialog = false; selectedAnime = null; firstOpenedAnime = null; onAnimeDialogOpen(false)
            },
            onUpdateStatus = { if (it != null) viewModel.addExploreAnimeToList(selectedAnime!!, it) },
            onRemove = { viewModel.removeAnimeFromList(selectedAnime!!.id) },
            onRelationClick = { relation ->
                try { scope.launch { try { delay(100.milliseconds); val d = viewModel.fetchDetailedAnimeData(relation.id); if (d != null) selectedAnime = ExploreAnime(id = relation.id, title = d.title, titleEnglish = d.titleEnglish, cover = d.cover, banner = d.banner, episodes = d.episodes, latestEpisode = d.latestEpisode, averageScore = d.averageScore, genres = d.genres, year = d.year, format = d.format) else Toast.makeText(context, "Anime not found - ID: ${relation.id}", Toast.LENGTH_SHORT).show() } catch (e: Exception) { Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show() } } } catch (e: Exception) { Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show() }
            },
            onCharacterClick = onCharacterClick, onStaffClick = onStaffClick,
            onViewAllCast = { onViewAllCast(selectedAnime!!.id, selectedAnime!!.title) },
            onViewAllStaff = { onViewAllStaff(selectedAnime!!.id, selectedAnime!!.title) },
            onViewAllRelations = { id, title -> onViewAllRelations(id, title) },
            onNoExtension = {
                showAnimeDialog = false
                onNoExtension()
            }
        )
    }
}

@Composable
private fun TimelineScheduleList(
    timelineItems: List<TimelineItem>,
    currentDayOfWeek: Int,
    preferEnglishTitles: Boolean,
    animeStatusMap: Map<Int, String>,
    listState: LazyListState,
    isVisible: Boolean = true,
    onAnimeClick: (AiringScheduleAnime) -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val density = LocalDensity.current
    val translationYOffset = with(density) { (-30).dp.toPx() }
    val isScrolling by remember { derivedStateOf { listState.isScrollInProgress } }
    val cinematicProgress = rememberCinematicAnimation("schedule", isVisible, true)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        state = listState
    ) {
        itemsIndexed(
            items = timelineItems,
            key = { _, item -> when (item) { is TimelineItem.Anime -> "anime_${item.data.id}_${item.data.airingEpisode}"; is TimelineItem.NowIndicator -> "now_${item.timeString}"; is TimelineItem.DayHeader -> "day_${item.dayIndex}" } }
        ) { index, item ->
            val staggerDelay = minOf(index, 20) * 30f
            val staggerMs = staggerDelay / 1000f
            val rawProgress = ((cinematicProgress - staggerMs) / (1f - staggerMs))
            val easedProgress = easeOutCubic(rawProgress.coerceAtLeast(0f).coerceAtMost(1f))
            val introScale = 0.3f + easedProgress * 0.7f
            val introAlpha = easedProgress.coerceAtLeast(0f)
            val introTranslationY = translationYOffset * (1f - easedProgress)
            val layoutInfo by remember { derivedStateOf { listState.layoutInfo } }
            val visibleItems = layoutInfo.visibleItemsInfo
            val itemInfo = visibleItems.find { it.index == index }
            val centerOffset = if (itemInfo != null) { val ic = itemInfo.offset + itemInfo.size / 2; val sc = (layoutInfo.viewportSize.height / 2).toFloat(); (ic - sc) / sc } else 0f
            val animatedOffset by animateFloatAsState(targetValue = if (isScrolling) centerOffset.coerceIn(-2f, 2f) else 0f, animationSpec = if (isScrolling) spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium) else spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow), label = "offset")
            val scrollScale = 1f - (animatedOffset.absoluteValue * 0.2f).coerceAtMost(0.2f)
            val scrollAlpha = 1f - (animatedOffset.absoluteValue * 0.4f).coerceAtMost(0.6f)
            val scrollParallax = animatedOffset * 25f
            val finalScale = scrollScale * introScale
            val finalAlpha = (scrollAlpha * introAlpha).coerceIn(0f, 1f)
            val finalTranslationY = scrollParallax + introTranslationY

            Box(modifier = Modifier.graphicsLayer { scaleX = finalScale; scaleY = finalScale; alpha = finalAlpha; translationY = finalTranslationY }) {
                when (item) {
                    is TimelineItem.DayHeader -> DayHeaderItem(item.dayName, item.dayIndex == currentDayOfWeek)
                    is TimelineItem.Anime -> TimelineAnimeItem(timeFormat.format(Date(item.data.airingAt * 1000L)), item.data, item.isPast,
                        preferEnglishTitles, animeStatusMap[item.data.id], onClick = { onAnimeClick(item.data) })
                    is TimelineItem.NowIndicator -> CurrentTimeIndicator(item.timeString)
                }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun DayHeaderItem(dayName: String, isToday: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 58.dp, end = 8.dp, top = 28.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            dayName.uppercase(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (isToday) {
            Spacer(Modifier.width(8.dp))
            Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)) {
                Text("TODAY", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }
        }
    }
}

@Composable
private fun TimelineAnimeItem(
    timeString: String,
    anime: AiringScheduleAnime,
    isPast: Boolean,
    preferEnglishTitles: Boolean,
    animeStatus: String?,
    onClick: () -> Unit
) {
    val lineColor = if (isPast) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else MaterialTheme.colorScheme.primary
    val contentAlpha = if (isPast) 0.55f else 1f

    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(50.dp)) {
            Text(timeString, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = lineColor.copy(alpha = contentAlpha))
            Box(modifier = Modifier.padding(top = 4.dp).size(10.dp).background(lineColor, CircleShape))
            Box(modifier = Modifier.width(1.5.dp).height(90.dp).background(lineColor.copy(alpha = 0.2f)))
        }

        Spacer(Modifier.width(8.dp))

        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
            onClick = onClick
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = anime.cover, contentDescription = anime.title, contentScale = ContentScale.Crop,
                    modifier = Modifier.width(68.dp).height(92.dp).clip(RoundedCornerShape(8.dp)).alpha(contentAlpha)
                )

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    val displayTitle = if (preferEnglishTitles && !anime.titleEnglish.isNullOrEmpty()) anime.titleEnglish else anime.title
                    Text(displayTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha))

                    Spacer(Modifier.height(6.dp))

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)) {
                            Text("Ep ${anime.airingEpisode}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                        if (animeStatus != null) {
                            val statusColor = StatusColors[animeStatus] ?: Color.Gray
                            val statusLabel = StatusLabels[animeStatus] ?: animeStatus
                            Surface(shape = RoundedCornerShape(4.dp), color = statusColor.copy(alpha = 0.15f)) {
                                Text(statusLabel, style = MaterialTheme.typography.labelSmall, color = statusColor, fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }

                    if (!isPast && anime.timeUntilAiring != null) {
                        Spacer(Modifier.height(4.dp))
                        val timeUntilText = remember(anime.timeUntilAiring) {
                            val sec = anime.timeUntilAiring; val h = sec / 3600; val m = (sec % 3600) / 60
                            when { h > 24 -> "${h / 24}d ${h % 24}h"; h > 0 -> "${h}h ${m}m"; else -> "${m}m" }
                        }
                        Text("in $timeUntilText", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }

                    if (isPast) {
                        Spacer(Modifier.height(4.dp))
                        Text("Already aired", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                }

                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "View details", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun CurrentTimeIndicator(timeString: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(initialValue = 0.8f, targetValue = 1.3f, animationSpec = infiniteRepeatable(animation = tween(800), repeatMode = RepeatMode.Reverse), label = "scale")
    val alpha by infiniteTransition.animateFloat(initialValue = 0.5f, targetValue = 1f, animationSpec = infiniteRepeatable(animation = tween(800), repeatMode = RepeatMode.Reverse), label = "alpha")

    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(50.dp)) {
            Text(timeString, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
            Box(modifier = Modifier.padding(vertical = 4.dp).size(10.dp).graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }.background(MaterialTheme.colorScheme.secondary, CircleShape))
        }

        Spacer(Modifier.width(8.dp))

        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                Box(modifier = Modifier.size(6.dp).background(MaterialTheme.colorScheme.secondary, CircleShape))
                Spacer(Modifier.width(6.dp))
                Text("NOW", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
private fun ScheduleLoadingSkeleton() {
    val skeletonColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    val secondaryColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
    ) {
        items(6) {
            Box(modifier = Modifier.fillMaxWidth().padding(start = 58.dp, end = 8.dp, top = 24.dp, bottom = 12.dp).height(32.dp).background(skeletonColor, RoundedCornerShape(6.dp)))

            Row(modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 12.dp), verticalAlignment = Alignment.Top) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(50.dp)) {
                    Box(Modifier.width(36.dp).height(12.dp).background(secondaryColor, RoundedCornerShape(4.dp)))
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.size(10.dp).background(secondaryColor, CircleShape))
                    Box(Modifier.padding(top = 4.dp).width(1.5.dp).height(90.dp).background(secondaryColor))
                }
                Spacer(Modifier.width(8.dp))
                Card(modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = skeletonColor)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(68.dp).height(92.dp).background(secondaryColor, RoundedCornerShape(8.dp)))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Box(Modifier.fillMaxWidth(0.8f).height(12.dp).background(secondaryColor, RoundedCornerShape(4.dp)))
                            Spacer(Modifier.height(8.dp))
                            Box(Modifier.fillMaxWidth(0.45f).height(12.dp).background(secondaryColor, RoundedCornerShape(4.dp)))
                            Spacer(Modifier.height(8.dp))
                            Box(Modifier.width(46.dp).height(16.dp).background(secondaryColor, RoundedCornerShape(4.dp)))
                        }
                        Box(Modifier.size(22.dp).background(secondaryColor, RoundedCornerShape(11.dp)))
                    }
                }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}



