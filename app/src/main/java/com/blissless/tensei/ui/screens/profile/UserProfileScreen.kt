package com.blissless.tensei.ui.screens.profile

import android.content.Intent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
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
import com.blissless.tensei.ui.theme.StatusCompleted
import com.blissless.tensei.ui.theme.StatusColors
import com.blissless.tensei.ui.theme.StatusCurrent
import com.blissless.tensei.ui.theme.StatusDropped
import com.blissless.tensei.ui.theme.StatusLabels
import com.blissless.tensei.ui.theme.MangaStatusLabels
import com.blissless.tensei.ui.theme.StatusPaused
import com.blissless.tensei.ui.theme.StatusPlanning
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
// Extension functions on MainViewModel (defined in com.blissless.tensei.viewmodel)
import com.blissless.tensei.viewmodel.fetchAniListFavorites
import com.blissless.tensei.viewmodel.fetchUserActivity
import com.blissless.tensei.viewmodel.fetchUserStats
import com.blissless.tensei.viewmodel.loadAniListFavoritesFromStorage
import com.blissless.tensei.viewmodel.toggleAniListFavorite
import com.blissless.tensei.viewmodel.mangaContinueReading
import com.blissless.tensei.viewmodel.mangaPlanningToRead
import com.blissless.tensei.viewmodel.mangaCompleted
import com.blissless.tensei.viewmodel.mangaCurrentlyReading
import com.blissless.tensei.viewmodel.mangaPaused
import com.blissless.tensei.viewmodel.mangaDropped
import com.blissless.tensei.data.models.MangaMedia
import com.blissless.tensei.data.models.MangaFavorite
import com.blissless.tensei.data.models.MangaActivityNode
import com.blissless.tensei.data.models.MangaActivityMedia
import com.blissless.tensei.data.models.MangaTitle
import com.blissless.tensei.data.models.MediaCoverImage
import com.blissless.tensei.data.models.MangaFuzzyDate
import com.blissless.tensei.data.models.MangaUserProfile
import com.blissless.tensei.viewmodel.mangaFavorites
import com.blissless.tensei.viewmodel.mangaActivity
import com.blissless.tensei.viewmodel.mangaUserProfile
import com.blissless.tensei.viewmodel.fetchMangaUserProfile
import com.blissless.tensei.viewmodel.toggleMangaFavorite

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
    onShowDetailedAnimeFromAniList: (Int) -> Unit,
    onMangaClick: (MangaMedia) -> Unit = {}
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

    val mangaFavorites by viewModel.mangaFavorites.collectAsState()
    val mangaActivity by viewModel.mangaActivity.collectAsState()
    val mangaUserProfile by viewModel.mangaUserProfile.collectAsState()

    val mangaReading by viewModel.mangaCurrentlyReading.collectAsState()
    val mangaPlanning by viewModel.mangaPlanningToRead.collectAsState()
    val mangaFinished by viewModel.mangaCompleted.collectAsState()
    val mangaHeld by viewModel.mangaPaused.collectAsState()
    val mangaAbandoned by viewModel.mangaDropped.collectAsState()

    LaunchedEffect(loginProvider) {
        if (loginProvider == LoginProvider.ANILIST) {
            viewModel.loadAniListFavoritesFromStorage()
            // Refresh the Viewer profile (name/avatar/banner/bio/joined/stats) and
            // ensure _userId is populated BEFORE the child fetches that depend on it —
            // otherwise fetchUserStats / fetchUserActivity / fetchAniListFavorites
            // return early with null userId and About Me stays empty.
            viewModel.fetchUser()
            viewModel.fetchAniListFavorites()
            viewModel.fetchUserActivity()
            viewModel.fetchUserStats()
            viewModel.fetchMangaUserProfile()
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
            val progress = mutableListOf<String>()
            val entries = userActivity.take(50).map { activity ->
                val progressStr = activity.progress
                val episodeDisplay = progressStr?.let { prog ->
                    val nums =
                        Regex("\\d+").findAll(prog).map { it.value.toIntOrNull() }.filterNotNull().toList()
                    when {
                        nums.size >= 2 && nums[1] > nums[0] -> "episode ${nums[0]}-${nums[1]}"
                        nums.isNotEmpty() -> "episode ${nums[0]}"
                        else -> prog.lowercase()
                    }
                }
                statuses.add(activity.status)
                progress.add(episodeDisplay ?: "")
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
            HistoryData(entries, statuses, progress)
        }
        LoginProvider.MAL -> {
            val malHistory = jikanHistory?.anime?.take(50) ?: emptyList()
            HistoryData(malHistory, malHistory.map { it.date ?: "" }, malHistory.map { "episode ${it.episodesWatched ?: 0}" })
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
                            userCreatedAt = userCreatedAt, userStats = userStats,
                            mangaUserProfile = mangaUserProfile,
                            animeLibrary = listOf(
                                LibraryStatus("CURRENT", viewModel.currentlyWatching.value.size),
                                LibraryStatus("PLANNING", viewModel.planningToWatch.value.size),
                                LibraryStatus("COMPLETED", viewModel.completed.value.size),
                                LibraryStatus("PAUSED", viewModel.onHold.value.size),
                                LibraryStatus("DROPPED", viewModel.dropped.value.size)
                            ),
                            mangaLibrary = listOf(
                                LibraryStatus("CURRENT", mangaReading.size),
                                LibraryStatus("PLANNING", mangaPlanning.size),
                                LibraryStatus("COMPLETED", mangaFinished.size),
                                LibraryStatus("PAUSED", mangaHeld.size),
                                LibraryStatus("DROPPED", mangaAbandoned.size)
                            )
                        )
                        UserProfileSection.FAVORITES -> FavoritesContent(
                            favorites = favorites,
                            mangaFavorites = mangaFavorites,
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
                            },
                            onMangaClick = { manga ->
                                onMangaClick(
                                    MangaMedia(
                                        id = manga.id,
                                        title = if (preferEnglishTitles) {
                                            manga.title?.english ?: manga.title?.romaji ?: "Unknown"
                                        } else {
                                            manga.title?.romaji ?: manga.title?.english ?: "Unknown"
                                        },
                                        titleEnglish = manga.title?.english,
                                        cover = manga.coverImage?.extraLarge ?: manga.coverImage?.large ?: "",
                                        totalChapters = manga.chapters ?: 0,
                                        averageScore = manga.averageScore,
                                        siteUrl = manga.siteUrl
                                    )
                                )
                            },
                            onRemoveMangaFavorite = { manga ->
                                viewModel.toggleMangaFavorite(manga.id)
                            }
                        )
                        UserProfileSection.HISTORY -> HistoryContent(
                            history = history,
                            mangaHistory = mangaActivity,
                            preferEnglishTitles = preferEnglishTitles,
                            onAnimeClick = { entry ->
                                if (loginProvider == LoginProvider.MAL) {
                                    onShowDetailedAnimeFromMal(entry.malId)
                                } else {
                                    onShowDetailedAnimeFromAniList(entry.malId)
                                }
                            },
                            onMangaClick = { node ->
                                node.media?.let { media ->
                                    onMangaClick(
                                        MangaMedia(
                                            id = media.id,
                                            title = if (preferEnglishTitles) {
                                                media.title?.english ?: media.title?.romaji ?: "Unknown"
                                            } else {
                                                media.title?.romaji ?: media.title?.english ?: "Unknown"
                                            },
                                            titleEnglish = media.title?.english,
                                            cover = media.coverImage?.extraLarge ?: media.coverImage?.large ?: "",
                                            totalChapters = media.chapters ?: 0,
                                            siteUrl = media.siteUrl
                                        )
                                    )
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
                        badge = favorites.size + mangaFavorites.size
                    )
                    UserProfileNavButton(
                        icon = Icons.Default.History, title = "History",
                        isSelected = selectedSection == UserProfileSection.HISTORY,
                        onClick = { selectedSection = UserProfileSection.HISTORY },
                        badge = history.size + mangaActivity.size
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
    userCreatedAt: Long? = null, userStats: UserAnimeStats? = null,
    mangaUserProfile: MangaUserProfile? = null,
    animeLibrary: List<LibraryStatus> = emptyList(),
    mangaLibrary: List<LibraryStatus> = emptyList()
) {
    var showFullscreenAvatar by remember { mutableStateOf(false) }
    val mangaStats = mangaUserProfile?.statistics?.manga

    Box(modifier = Modifier.fillMaxSize()) {
        userBanner?.let { bannerUrl ->
            AsyncImage(
                model = bannerUrl, contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(180.dp),
                contentScale = ContentScale.Crop
            )
        }

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(if (userBanner != null) 140.dp else 8.dp))

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

            if (userStats != null || animeLibrary.any { it.count > 0 }) {
                Text(
                    "Anime",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

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

                Spacer(modifier = Modifier.height(20.dp))
                LibrarySection(title = "Anime Library", statuses = animeLibrary, labels = StatusLabels, colors = StatusColors)
            }

            val mangaTotal = mangaLibrary.sumOf { it.count }
            if (mangaTotal > 0 || mangaStats != null) {
                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    "Manga",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                StatCard(
                    value = mangaStats?.meanScore?.let { "%.1f".format(it) } ?: "-",
                    label = "Mean", color = MaterialTheme.colorScheme.secondary
                )

                Spacer(modifier = Modifier.height(20.dp))
                LibrarySection(title = "Manga Library", statuses = mangaLibrary, labels = MangaStatusLabels, colors = StatusColors)
            }

            Spacer(modifier = Modifier.height(24.dp))
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

private data class LibraryStatus(val key: String, val count: Int)

private val StatusIcons = mapOf(
    "CURRENT" to Icons.Default.PlayArrow,
    "PLANNING" to Icons.Default.Bookmark,
    "COMPLETED" to Icons.Default.Check,
    "PAUSED" to Icons.Default.Pause,
    "DROPPED" to Icons.Default.Delete
)

@Composable
private fun LibrarySection(
    title: String,
    statuses: List<LibraryStatus>,
    labels: Map<String, String>,
    colors: Map<String, Color>
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            val total = statuses.sumOf { it.count }.coerceAtLeast(1)
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                statuses.forEach { status ->
                    LibraryStatRow(
                        status = status,
                        total = total,
                        icon = StatusIcons[status.key] ?: Icons.Default.PlayArrow,
                        color = colors[status.key] ?: Color.Gray,
                        label = labels[status.key] ?: status.key
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryStatRow(status: LibraryStatus, total: Int, icon: ImageVector, color: Color, label: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(status.count.toFloat() / total)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            status.count.toString(),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.width(28.dp),
            textAlign = TextAlign.End
        )
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
    mangaFavorites: List<MangaFavorite> = emptyList(),
    preferEnglishTitles: Boolean,
    onAnimeClick: (JikanFavoriteAnime) -> Unit,
    onRemoveFavorite: ((JikanFavoriteAnime) -> Unit)? = null,
    onMangaClick: (MangaFavorite) -> Unit = {},
    onRemoveMangaFavorite: ((MangaFavorite) -> Unit)? = null
) {
    if (favorites.isEmpty() && mangaFavorites.isEmpty()) {
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
            if (favorites.isNotEmpty()) {
                item {
                    Text("Anime", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 4.dp))
                }
                itemsIndexed(favorites) { _, anime ->
                    FavoriteItem(
                        anime = anime,
                        preferEnglishTitles = preferEnglishTitles,
                        onClick = { onAnimeClick(anime) },
                        onRemove = { onRemoveFavorite?.invoke(anime) }
                    )
                }
            }
            if (mangaFavorites.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text("Manga", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 4.dp))
                }
                itemsIndexed(mangaFavorites) { _, manga ->
                    MangaFavoriteItem(
                        manga = manga,
                        preferEnglishTitles = preferEnglishTitles,
                        onClick = { onMangaClick(manga) },
                        onRemove = { onRemoveMangaFavorite?.invoke(manga) }
                    )
                }
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
    mangaHistory: List<MangaActivityNode> = emptyList(),
    preferEnglishTitles: Boolean,
    onAnimeClick: (JikanHistoryEntry) -> Unit,
    onMangaClick: (MangaActivityNode) -> Unit = {},
    statuses: List<String> = emptyList(),
    progressList: List<String> = emptyList()
) {
    if (history.isEmpty() && mangaHistory.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.History, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text("No history", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            if (history.isNotEmpty()) {
                item {
                    Text("Anime", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 4.dp))
                }
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
            if (mangaHistory.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text("Manga", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 4.dp))
                }
                itemsIndexed(mangaHistory) { _, node ->
                    MangaActivityItem(node = node, preferEnglishTitles = preferEnglishTitles, onClick = { onMangaClick(node) })
                }
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
        status?.contains("completed", ignoreCase = true) == true || status?.contains("finished", ignoreCase = true) == true ->
            Triple(Icons.Default.Check, StatusCompleted, "Completed")
        status?.contains("paused", ignoreCase = true) == true || status?.contains("hold", ignoreCase = true) == true ->
            Triple(Icons.Default.Pause, StatusPaused, "On Hold")
        status?.contains("dropped", ignoreCase = true) == true ->
            Triple(Icons.Default.Delete, StatusDropped, "Dropped")
        status?.contains("plan", ignoreCase = true) == true ->
            Triple(Icons.Default.Bookmark, StatusPlanning, "Planning to Watch")
        status?.contains("watching", ignoreCase = true) == true || status?.contains("watched", ignoreCase = true) == true ||
            status?.contains("repeating", ignoreCase = true) == true || status?.contains("rewatched", ignoreCase = true) == true ->
            Triple(Icons.Default.PlayArrow, StatusCurrent, "Watched")
        else -> Triple(Icons.Default.PlayArrow, StatusCurrent, status ?: "")
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

@Composable
private fun MangaFavoriteItem(
    manga: MangaFavorite,
    preferEnglishTitles: Boolean,
    onClick: () -> Unit = {},
    onRemove: (() -> Unit)? = null
) {
    val displayTitle = if (preferEnglishTitles) {
        manga.title?.english ?: manga.title?.romaji ?: "Unknown"
    } else {
        manga.title?.romaji ?: manga.title?.english ?: "Unknown"
    }
    val startYear = manga.startDate?.year
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
                model = manga.coverImage?.extraLarge ?: manga.coverImage?.large,
                contentDescription = displayTitle,
                modifier = Modifier.width(60.dp).height(84.dp).clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    displayTitle, color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodyMedium, maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (manga.format != null || startYear != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (manga.format != null) {
                            Text(
                                manga.format, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.labelSmall
                            )
                            if (startYear != null) {
                                Text(" · ", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                            }
                        }
                        startYear?.let { year ->
                            Text(
                                year.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                manga.chapters?.let { chapters ->
                    if (chapters > 0) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "$chapters chapters", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                manga.averageScore?.let { score ->
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
private fun MangaActivityItem(
    node: MangaActivityNode,
    preferEnglishTitles: Boolean,
    onClick: () -> Unit = {}
) {
    val media = node.media
    val displayTitle = if (preferEnglishTitles) {
        media?.title?.english ?: media?.title?.romaji ?: "Unknown"
    } else {
        media?.title?.romaji ?: media?.title?.english ?: "Unknown"
    }

    val status = node.status ?: ""
    val (statusIcon, statusColor, statusLabel) = when {
        status.contains("completed", ignoreCase = true) || status.contains("finished", ignoreCase = true) ->
            Triple(Icons.Default.Check, StatusCompleted, "Completed")
        status.contains("paused", ignoreCase = true) || status.contains("hold", ignoreCase = true) ->
            Triple(Icons.Default.Pause, StatusPaused, "On Hold")
        status.contains("dropped", ignoreCase = true) ->
            Triple(Icons.Default.Delete, StatusDropped, "Dropped")
        status.contains("plan", ignoreCase = true) ->
            Triple(Icons.Default.Bookmark, StatusPlanning, "Planning to Read")
        status.contains("reading", ignoreCase = true) || status.contains("read", ignoreCase = true) ||
            status.contains("current", ignoreCase = true) || status.contains("repeating", ignoreCase = true) ||
            status.contains("reread", ignoreCase = true) ->
            Triple(Icons.Default.PlayArrow, StatusCurrent, "Read")
        else -> Triple(Icons.Default.PlayArrow, StatusCurrent, status)
    }

    val progressSuffix = formatMangaProgress(node.progress)
    val statusText = if (progressSuffix != null) "$statusLabel $progressSuffix" else statusLabel

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
                model = media?.coverImage?.extraLarge ?: media?.coverImage?.large,
                contentDescription = displayTitle,
                modifier = Modifier.width(60.dp).height(84.dp).clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    displayTitle, color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(statusIcon, contentDescription = null, tint = statusColor, modifier = Modifier.size(14.dp))
                    Text(statusText, color = statusColor, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    formatTimestamp(node.createdAt),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private fun formatMangaProgress(progress: String?): String? {
    if (progress.isNullOrBlank()) return null
    val nums = Regex("\\d+").findAll(progress).map { it.value.toIntOrNull() }.filterNotNull().toList()
    return when {
        nums.size >= 2 && nums[1] > nums[0] -> "chapter ${nums[0]}-${nums[1]}"
        nums.isNotEmpty() -> "chapter ${nums[0]}"
        else -> "chapter $progress"
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("d MMMM, yyyy - HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp * 1000))
}


