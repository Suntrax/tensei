package com.blissless.tensei.ui.screens.profile

import android.content.Intent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.blissless.tensei.MainViewModel
import com.blissless.tensei.api.jikan.JikanFavoriteAnime
import com.blissless.tensei.api.jikan.JikanHistoryEntry
import com.blissless.tensei.api.jikan.JikanImageUrls
import com.blissless.tensei.api.jikan.JikanImages
import com.blissless.tensei.api.myanimelist.LoginProvider
import com.blissless.tensei.data.models.UserAnimeStats
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

data class HistoryData(val entries: List<JikanHistoryEntry>, val statuses: List<String>, val progressList: List<String>)

enum class UserProfileSection {
    ABOUT_ME, FAVORITES, HISTORY
}

@Composable
fun UserProfileScreen(
    viewModel: MainViewModel,
    preferEnglishTitles: Boolean = true,
    onDismiss: () -> Unit,
    onShowDetailedAnimeFromMal: (Int) -> Unit,
    onShowDetailedAnimeFromAniList: (Int) -> Unit
) {
    var selectedSection by remember { mutableStateOf(UserProfileSection.ABOUT_ME) }
    val context = LocalContext.current

    val loginProvider by viewModel.loginProvider.collectAsState()
    val jikanFavorites by viewModel.jikanFavorites.collectAsState()
    val jikanHistory by viewModel.jikanHistory.collectAsState()
    val aniListFavorites by viewModel.aniListFavorites.collectAsState()
    val userActivity by viewModel.userActivity.collectAsState()
    val userStats by viewModel.userStats.collectAsState()
    val userAvatar by viewModel.userAvatar.collectAsState()
    val userName by viewModel.userName.collectAsState()
    val userBanner by viewModel.userBanner.collectAsState()
    val userBio by viewModel.userBio.collectAsState()
    val userSiteUrl by viewModel.userSiteUrl.collectAsState()
    val userCreatedAt by viewModel.userCreatedAt.collectAsState()

    LaunchedEffect(loginProvider) {
        if (loginProvider == LoginProvider.ANILIST) {
            viewModel.loadAniListFavoritesFromStorage()
            viewModel.fetchAniListFavorites()
            viewModel.fetchUserActivity()
            viewModel.fetchUserStats()
        }
    }

    val favorites: List<JikanFavoriteAnime> = when (loginProvider) {
        LoginProvider.ANILIST -> {
            aniListFavorites.map { aniListFavorite ->
                val coverUrl = aniListFavorite.coverImage?.extraLarge ?: ""
                JikanFavoriteAnime(
                    id = aniListFavorite.id,
                    malId = 0,
                    title = aniListFavorite.title.romaji ?: aniListFavorite.title.english ?: "",
                    titleEnglish = aniListFavorite.title.english,
                    images = JikanImages(jpg = JikanImageUrls(coverUrl)),
                    year = aniListFavorite.seasonYear,
                    episodes = aniListFavorite.episodes,
                    averageScore = aniListFavorite.averageScore,
                    format = aniListFavorite.format,
                    status = aniListFavorite.status
                )
            }
        }
        LoginProvider.MAL -> jikanFavorites?.anime ?: emptyList()
        LoginProvider.NONE -> emptyList()
    }

    val historyData = when (loginProvider) {
        LoginProvider.ANILIST -> {
            val statuses = mutableListOf<String>()
            val entries = userActivity.take(50).map { activity ->
                val progressStr = activity.progress
                val episodeDisplay = progressStr?.let { prog ->
                    val nums =
                        prog.filter { it.isDigit() }.chunked(2).mapNotNull { it.toIntOrNull() }
                    when {
                        nums.size >= 2 && nums[1] > nums[0] -> "${nums[0]}-${nums[1]}"
                        nums.isNotEmpty() -> "Episode ${nums[0]}"
                        else -> null
                    }
                }
                statuses.add(activity.status)
                JikanHistoryEntry(
                    malId = activity.mediaId,
                    title = activity.mediaTitle,
                    titleEnglish = activity.mediaTitleEnglish,
                    images = JikanImages(jpg = JikanImageUrls(activity.mediaCover)),
                    episodesWatched = episodeDisplay?.filter { it.isDigit() }?.toIntOrNull(),
                    chaptersRead = null, increment = null,
                    date = formatTimestamp(activity.createdAt)
                )
            }
            HistoryData(entries, statuses, userActivity.take(50).map { it.progress ?: "" })
        }
        LoginProvider.MAL -> {
            val malHistory = jikanHistory?.anime?.take(50) ?: emptyList()
            HistoryData(malHistory, malHistory.map { it.date ?: "" }, malHistory.map { "Episode ${it.episodesWatched ?: 0}" })
        }
        LoginProvider.NONE -> HistoryData(emptyList(), emptyList(), emptyList())
    }

    val history = historyData.entries
    val statuses = historyData.statuses
    val progressDisplay = historyData.progressList

    val statusBarsPadding = WindowInsets.statusBars.asPaddingValues()
    val navigationBarsPadding = WindowInsets.navigationBars.asPaddingValues()

    val slideOffset = remember { Animatable(1000f) }
    val dismissSlideOffset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        slideOffset.animateTo(targetValue = 0f, animationSpec = tween(200, easing = LinearEasing))
    }

    fun dismissWithAnimation() {
        scope.launch {
            dismissSlideOffset.snapTo(0f)
            dismissSlideOffset.animateTo(targetValue = 1000f, animationSpec = tween(150, easing = LinearEasing))
            onDismiss()
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (slideOffset.value > 0 || dismissSlideOffset.value > 0) 0f else 1f,
        animationSpec = tween(durationMillis = 200, easing = LinearEasing), label = "alpha"
    )

    Dialog(
        onDismissRequest = { dismissWithAnimation() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, (slideOffset.value + dismissSlideOffset.value).roundToInt()) }
                .graphicsLayer { this.alpha = alpha }
                .padding(0.dp),
            shape = RoundedCornerShape(0.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = statusBarsPadding.calculateTopPadding() + 8.dp, start = 8.dp, end = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { dismissWithAnimation() }) {
                        Icon(
                            Icons.Default.Close, "Close",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        when (selectedSection) {
                            UserProfileSection.ABOUT_ME -> "About Me"
                            UserProfileSection.FAVORITES -> "Favorites"
                            UserProfileSection.HISTORY -> "History"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.weight(1f))
                    if (selectedSection == UserProfileSection.ABOUT_ME && userSiteUrl != null) {
                        IconButton(onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, userSiteUrl)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Profile"))
                        }) {
                            Icon(Icons.Default.Share, "Share", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        Spacer(Modifier.width(48.dp))
                    }
                }

                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)
                ) {
                    when (selectedSection) {
                        UserProfileSection.ABOUT_ME -> AboutMeContent(
                            username = userName ?: "User",
                            userAvatar = userAvatar, userBanner = userBanner,
                            userBio = userBio,
                            userCreatedAt = userCreatedAt, userStats = userStats
                        )
                        UserProfileSection.FAVORITES -> FavoritesContent(
                            favorites = favorites,
                            preferEnglishTitles = preferEnglishTitles,
                            onAnimeClick = { anime ->
                                if (anime.malId != 0) {
                                    onShowDetailedAnimeFromMal(anime.malId)
                                } else if (anime.id != 0) {
                                    onShowDetailedAnimeFromAniList(anime.id)
                                }
                            },
                            onRemoveFavorite = {
                                viewModel.toggleAniListFavorite(it.malId)
                            }
                        )
                        UserProfileSection.HISTORY -> HistoryContent(
                            history = history,
                            preferEnglishTitles = preferEnglishTitles,
                            onAnimeClick = { entry ->
                                if (loginProvider == LoginProvider.MAL) {
                                    onShowDetailedAnimeFromMal(entry.malId)
                                } else {
                                    onShowDetailedAnimeFromAniList(entry.malId)
                                }
                            },
                            statuses = statuses, progressList = progressDisplay
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = navigationBarsPadding.calculateBottomPadding() + 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UserProfileNavButton(
                        icon = Icons.Default.Person, title = "About Me",
                        isSelected = selectedSection == UserProfileSection.ABOUT_ME,
                        onClick = { selectedSection = UserProfileSection.ABOUT_ME }
                    )
                    UserProfileNavButton(
                        icon = Icons.Default.Favorite, title = "Favorites",
                        isSelected = selectedSection == UserProfileSection.FAVORITES,
                        onClick = { selectedSection = UserProfileSection.FAVORITES },
                        badge = favorites.size
                    )
                    UserProfileNavButton(
                        icon = Icons.Default.History, title = "History",
                        isSelected = selectedSection == UserProfileSection.HISTORY,
                        onClick = { selectedSection = UserProfileSection.HISTORY },
                        badge = history.size
                    )
                }
            }
        }
    }
}

@Composable
private fun UserProfileNavButton(
    icon: ImageVector, title: String, isSelected: Boolean,
    onClick: () -> Unit, badge: Int? = null
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            Icon(
                icon, contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            if (badge != null && badge > 0) {
                androidx.compose.material3.Badge(
                    modifier = Modifier.offset(x = 10.dp, y = (-4).dp),
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Text(badge.toString(), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            title, color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun AboutMeContent(
    username: String,
    userAvatar: String? = null, userBanner: String? = null,
    userBio: String? = null,
    userCreatedAt: Long? = null, userStats: UserAnimeStats? = null
) {
    var showFullscreenAvatar by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        userBanner?.let { bannerUrl ->
            AsyncImage(
                model = bannerUrl, contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(180.dp),
                contentScale = ContentScale.Crop
            )
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(140.dp))

            Box {
                userAvatar?.let { avatarUrl ->
                    AsyncImage(
                        model = avatarUrl, contentDescription = "Avatar",
                        modifier = Modifier
                            .size(96.dp)
                            .clip(RoundedCornerShape(48.dp))
                            .clickable { showFullscreenAvatar = true },
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                username,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            userBio?.let { bio ->
                if (bio.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        bio,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 5,
                        textAlign = TextAlign.Center,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            userCreatedAt?.let { timestamp ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Joined ${formatDate(timestamp)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (userStats != null) {
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(Modifier.weight(1f)) {
                            StatCard(value = userStats.count.toString(), label = "Anime", color = MaterialTheme.colorScheme.primary)
                        }
                        Box(Modifier.weight(1f)) {
                            StatCard(value = formatEpisodes(userStats.episodesWatched), label = "Episodes", color = MaterialTheme.colorScheme.tertiary)
                        }
                        Box(Modifier.weight(1f)) {
                            StatCard(
                                value = userStats.meanScore?.let { "%.1f".format(it / 10.0) } ?: "-",
                                label = "Mean", color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }

                Spacer(modifier = Modifier.height(12.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "Total: ${formatMinutesWatched(userStats.minutesWatched)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))
        }

        if (showFullscreenAvatar) {
            Dialog(onDismissRequest = { showFullscreenAvatar = false }) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.fillMaxSize().clickable { showFullscreenAvatar = false })
                    userAvatar?.let { avatarUrl ->
                        AsyncImage(
                            model = avatarUrl, contentDescription = "Avatar",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(value: String, label: String, color: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.15f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatEpisodes(episodes: Int): String = when {
    episodes >= 1000 -> "%.1fK".format(episodes / 1000.0)
    else -> episodes.toString()
}

private fun formatMinutesWatched(minutes: Int): String {
    val hours = minutes / 60
    val days = hours / 24
    return when {
        days > 0 -> "$days days"
        hours > 0 -> "$hours hours"
        else -> "$minutes min"
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("d MMMM, yyyy", Locale.forLanguageTag("de-DE"))
    return sdf.format(Date(timestamp * 1000))
}

@Composable
private fun FavoritesContent(
    favorites: List<JikanFavoriteAnime>,
    preferEnglishTitles: Boolean,
    onAnimeClick: (JikanFavoriteAnime) -> Unit,
    onRemoveFavorite: ((JikanFavoriteAnime) -> Unit)? = null
) {
    if (favorites.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Favorite, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text("No favorites yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            itemsIndexed(favorites) { _, anime ->
                FavoriteItem(
                    anime = anime,
                    preferEnglishTitles = preferEnglishTitles,
                    onClick = { onAnimeClick(anime) },
                    onRemove = { onRemoveFavorite?.invoke(anime) }
                )
            }
        }
    }
}

@Composable
private fun FavoriteItem(
    anime: JikanFavoriteAnime,
    preferEnglishTitles: Boolean,
    onClick: () -> Unit,
    onRemove: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = anime.images.jpg?.imageUrl, contentDescription = anime.title,
                modifier = Modifier.width(60.dp).height(84.dp).clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                val displayTitle = if (preferEnglishTitles && !anime.titleEnglish.isNullOrEmpty()) anime.titleEnglish else anime.title
                Text(
                    displayTitle, color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodyMedium, maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                anime.year?.let { year ->
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (anime.format != null) {
                            Text(
                                anime.format, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.labelSmall
                            )
                            Text(" · ", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                        }
                        Text(
                            year.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                if (anime.year == null && anime.format != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        anime.format, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                anime.episodes?.let { eps ->
                    if (eps > 0) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "$eps episodes", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                anime.averageScore?.let { score ->
                    if (score > 0) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "${score / 10}.${score % 10}", color = Color(0xFFFFD700),
                            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            if (onRemove != null) {
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Filled.Favorite, "Remove from favorites",
                        tint = Color(0xFFFF1744), modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
@Composable
private fun HistoryContent(
    history: List<JikanHistoryEntry>,
    preferEnglishTitles: Boolean,
    onAnimeClick: (JikanHistoryEntry) -> Unit,
    statuses: List<String> = emptyList(),
    progressList: List<String> = emptyList()
) {
    if (history.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.History, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text("No watch history", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            itemsIndexed(history) { index, entry ->
                HistoryItem(
                    entry = entry,
                    preferEnglishTitles = preferEnglishTitles,
                    onClick = { onAnimeClick(entry) },
                    status = statuses.getOrNull(index),
                    progress = progressList.getOrNull(index)
                )
            }
        }
    }
}

@Composable
private fun HistoryItem(
    entry: JikanHistoryEntry,
    preferEnglishTitles: Boolean, onClick: () -> Unit,
    status: String? = null, progress: String? = null
) {
    val (statusIcon, statusColor, statusLabel) = when {
        status?.contains("completed", ignoreCase = true) == true -> Triple(Icons.Default.Check, Color(0xFF4CAF50), "Completed")
        status?.contains("watching", ignoreCase = true) == true -> Triple(Icons.Default.PlayArrow, Color(0xFF2196F3), "Watched")
        status?.contains("plan", ignoreCase = true) == true -> Triple(Icons.Default.Bookmark, Color(0xFF9C27B0), "Planning to Watch")
        status?.contains("hold", ignoreCase = true) == true -> Triple(Icons.Default.Pause, Color(0xFFFFC107), "On Hold")
        status?.contains("dropped", ignoreCase = true) == true -> Triple(Icons.Default.Delete, Color(0xFFF44336), "Dropped")
        else -> Triple(Icons.Default.PlayArrow, Color(0xFF2196F3), status ?: "")
    }

    Card(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = entry.images.jpg?.imageUrl, contentDescription = entry.title,
                modifier = Modifier.width(60.dp).height(84.dp).clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                val displayTitle = if (preferEnglishTitles && !entry.titleEnglish.isNullOrEmpty()) entry.titleEnglish else entry.title
                Text(
                    displayTitle, color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(statusIcon, contentDescription = null, tint = statusColor, modifier = Modifier.size(14.dp))
                    Text("$statusLabel $progress", color = statusColor, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.height(2.dp))
                entry.date?.let { date ->
                    Text(date, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("d MMMM, yyyy - HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp * 1000))
}


