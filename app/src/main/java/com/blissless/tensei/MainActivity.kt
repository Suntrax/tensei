package com.blissless.tensei

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.animation.Crossfade
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import com.blissless.tensei.api.myanimelist.LoginProvider
import com.blissless.tensei.data.models.AnimeMedia
import com.blissless.tensei.data.models.DetailedAnimeData
import com.blissless.tensei.data.models.EpisodeStreams
import com.blissless.tensei.data.models.ExploreAnime
import com.blissless.tensei.data.models.LocalAnimeEntry
import eu.kanade.tachiyomi.animesource.model.Video
import com.blissless.tensei.data.models.QualityOption
import com.blissless.tensei.data.models.ServerInfo
import com.blissless.tensei.stream.PlayerData
import com.blissless.tensei.data.models.toDetailedAnimeData
import com.blissless.tensei.ui.screens.cast.AllCastScreen
import com.blissless.tensei.ui.screens.cast.AllStaffScreen
import com.blissless.tensei.ui.screens.character.CharacterScreen
import com.blissless.tensei.ui.screens.details.DetailedAnimeScreen
import com.blissless.tensei.ui.screens.explore.ExploreScreen
import com.blissless.tensei.ui.screens.home.HomeScreen
import com.blissless.tensei.ui.screens.player.PlayerScreen
import com.blissless.tensei.ui.screens.airing.ScheduleScreen
import com.blissless.tensei.ui.screens.settings.SettingsScreen
import com.blissless.tensei.ui.screens.downloads.DownloadsScreen
import com.blissless.tensei.ui.screens.search.SearchScreen
import com.blissless.tensei.ui.screens.downloads.EpisodeDownloadDialog
import com.blissless.tensei.extensions.ExtensionsViewModel
import com.blissless.tensei.ui.screens.status.StatusListScreen
import com.blissless.tensei.ui.screens.character.StaffScreen
import com.blissless.tensei.ui.screens.relations.AllRelationsScreen
import com.blissless.tensei.ui.theme.AppTheme
import com.blissless.tensei.ui.theme.ThemeMode
import com.blissless.tensei.update.UpdateViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()
    private val _widgetClicks = kotlinx.coroutines.flow.MutableSharedFlow<Int>(replay = 1, extraBufferCapacity = 1)
    val widgetClicks: kotlinx.coroutines.flow.SharedFlow<Int> = _widgetClicks

    companion object {
        const val PREFS_NAME = "anilist_prefs"
        const val TOKEN_KEY = "auth_token"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val hasToken = prefs.getString(TOKEN_KEY, null) != null
        val savedToken = prefs.getString(TOKEN_KEY, null)

        mainViewModel.init(applicationContext, hasToken)

        intent.getStringExtra("notification_anime")?.let { if (it.isNotBlank()) mainViewModel.onNotificationAnimeTap(it) }
        intent.getIntExtra("widget_anime_id", 0).let { if (it > 0) _widgetClicks.tryEmit(it) }
        if (intent.getBooleanExtra("open_extensions", false)) {
            mainViewModel.requestOpenExtensions()
        }
        handleAuthCallback(intent)

        setContent {
            var showSplash by remember { mutableStateOf(true) }
            var splashProgress by remember { mutableFloatStateOf(1f) }

            LaunchedEffect(mainViewModel.splashReady) {
                if (mainViewModel.splashReady.value) {
                    splashProgress = 2f
                    delay(400.milliseconds)
                    showSplash = false
                }
            }

            LaunchedEffect(Unit) {
                delay(1400.milliseconds)
                splashProgress = 2f
                delay(400.milliseconds)
                showSplash = false
            }

            if (showSplash) {
                val animatedProgress by animateFloatAsState(
                    targetValue = splashProgress,
                    animationSpec = tween(durationMillis = 400, easing = LinearEasing),
                    label = "splash_progress"
                )

                val scale = when {
                    animatedProgress < 1f -> 0.85f + (0.15f * animatedProgress)
                    else -> 1f + ((animatedProgress - 1f) * 0.15f)
                }
                val alpha = when {
                    animatedProgress < 1f -> animatedProgress
                    animatedProgress < 2f -> 1f - ((animatedProgress - 1f) * 1f)
                    else -> 0f
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.splash),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                alpha = alpha.coerceIn(0f, 1f)
                            ),
                        contentScale = ContentScale.Crop
                    )
                }
            } else {
            val themeModeStr by mainViewModel.themeMode.collectAsState()
            val isOled by mainViewModel.isOled.collectAsState()
            val disableMaterialColors by mainViewModel.disableMaterialColors.collectAsState()
            val showStatusColors by mainViewModel.showStatusColors.collectAsState()
            val showAnimeCardButtons by mainViewModel.showAnimeCardButtons.collectAsState()
            val preferEnglishTitles by mainViewModel.preferEnglishTitles.collectAsState()
            val preventScheduleSync by mainViewModel.preventScheduleSync.collectAsState()

            var isLoggedIn by remember { mutableStateOf(savedToken != null) }
            val token by mainViewModel.authToken.collectAsState()
            val loginProvider by mainViewModel.loginProvider.collectAsState()
            var showLocalSyncDialog by remember { mutableStateOf(false) }
            val localAnimeStatus by mainViewModel.localAnimeStatus.collectAsState()
            
            LaunchedEffect(token, loginProvider) {
                val isAnyLoggedIn = token != null || loginProvider != LoginProvider.NONE
                if (isAnyLoggedIn && !isLoggedIn && localAnimeStatus.isNotEmpty()) {
                    showLocalSyncDialog = true
                }
                isLoggedIn = isAnyLoggedIn
            }

            LaunchedEffect(Unit) {
                enableHighRefreshRate()
            }

            val notifPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { _ -> }

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_DENIED) {
                        notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }
            
            val toastContext = LocalContext.current
            LaunchedEffect(Unit) {
                mainViewModel.toastMessage.collect { message ->
                    Toast.makeText(toastContext, message, Toast.LENGTH_SHORT).show()
                }
            }
            
            LaunchedEffect(Unit) {
                mainViewModel.logoutEvent.collect {
                    (toastContext as? MainActivity)?.resetAuthFlags()
                }
            }

            if (showLocalSyncDialog) {
                AlertDialog(
                    onDismissRequest = { showLocalSyncDialog = false },
                    containerColor = Color(0xFF1A1A1A),
                    title = { 
                        Text(
                            "Sync Local Changes",
                            color = Color.White,
                            style = MaterialTheme.typography.headlineSmall
                        ) 
                    },
                    text = {
                        Column {
                            Text(
                                "You have ${localAnimeStatus.size} anime tracked offline.",
                                color = Color.White.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Text(
                                "Choose how to sync:",
                                color = Color.White,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Text(
                                "1. Discard Local Changes",
                                color = Color(0xFFF44336),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "Remove all offline changes. AniList data will remain unchanged.",
                                color = Color.White.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Text(
                                "2. Add New Anime Only",
                                color = Color(0xFF4CAF50),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "Add new anime from offline to AniList. Won't overwrite existing entries.",
                                color = Color.White.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Text(
                                "3. Overwrite AniList",
                                color = Color(0xFF2196F3),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "Replace all matching anime on AniList with your offline changes.",
                                color = Color.White.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showLocalSyncDialog = false
                                mainViewModel.discardLocalChanges()
                            }
                        ) {
                            Text("Discard", color = Color(0xFFF44336))
                        }
                    },
                    dismissButton = {
                        Row {
                            TextButton(
                                onClick = {
                                    showLocalSyncDialog = false
                                    mainViewModel.addLocalToAniListOnlyNew()
                                }
                            ) {
                                Text("Add New Only", color = Color(0xFF4CAF50))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(
                                onClick = {
                                    showLocalSyncDialog = false
                                    mainViewModel.overwriteAniListWithLocal()
                                }
                            ) {
                                Text("Overwrite", color = Color(0xFF2196F3))
                            }
                        }
                    }
                )
            }

            val themeMode = remember(themeModeStr) { ThemeMode.fromValue(themeModeStr) }
            AppTheme(themeMode = themeMode, useMonochrome = disableMaterialColors) {
                MainScreen(
                    viewModel = mainViewModel,
                    isOled = isOled,
                    showStatusColors = showStatusColors,
                    showAnimeCardButtons = showAnimeCardButtons,
                    preferEnglishTitles = preferEnglishTitles,
                    preventScheduleSync = preventScheduleSync,
                    isLoggedIn = isLoggedIn
                )
            }
            }
        }
    }

    /**
     * Enable the highest supported refresh rate and request high frame rate for animations.
     */
    private fun enableHighRefreshRate() {
        try {
            // Step 1: Set the preferred display mode to the highest refresh rate available
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                display?.let { disp ->
                    val modes = disp.supportedModes
                    var bestMode: android.view.Display.Mode? = null
                    var highestRefreshRate = 60f

                    modes?.forEach { mode ->
                        if (mode.refreshRate > highestRefreshRate) {
                            highestRefreshRate = mode.refreshRate
                            bestMode = mode
                        }
                    }

                    bestMode?.let { mode ->
                        val params = window.attributes
                        params.preferredDisplayModeId = mode.modeId
                        window.attributes = params
                    }
                }
            }

            // Step 2: Request high frame rate for the window
            // This tells the system we want animations to run at the display's native refresh rate
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.attributes = window.attributes.apply {
                    // Request the surface to render at high frame rate
                    preferredRefreshRate = 120f // Request up to 120Hz
                }
                
                // Try to set frame rate directly on the decor view's surface (API 30+)
                try {
                    val decorView = window.decorView
                    decorView.viewTreeObserver.addOnPreDrawListener {
                        // Keep requesting high frame rate
                        decorView.postInvalidateOnAnimation()
                        true
                    }
                } catch (_: Exception) {
                    // Ignore - this is optional enhancement
                }
            }

        } catch (e: Exception) {
            // Gracefully handle any errors - fallback to system default
            e.printStackTrace()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthCallback(intent)
        intent.getStringExtra("notification_anime")?.let { if (it.isNotBlank()) mainViewModel.onNotificationAnimeTap(it) }
        intent.getIntExtra("widget_anime_id", 0).let { if (it > 0) _widgetClicks.tryEmit(it) }
        if (intent.getBooleanExtra("open_extensions", false)) {
            mainViewModel.requestOpenExtensions()
        }
    }

    override fun onResume() {
        super.onResume()
        // Check for any pending auth callback when returning to the app
        handleAuthCallback(intent)
    }

    private var isMalAuthHandled = false
    private var isAniListAuthHandled = false
    
    private fun handleAuthCallback(intent: Intent?) {
        if (intent == null) {
            return
        }

        val uriString = intent.dataString ?: return

        // Check if it's MAL auth (contains code= parameter)
        if (!isMalAuthHandled && uriString.contains("code=") && uriString.startsWith("animescraper://success")) {
            isMalAuthHandled = true
            mainViewModel.handleMalAuthAuthCode(uriString)
        }
        // Check if it's AniList auth (contains access_token=)
        else if (!isAniListAuthHandled && uriString.contains("access_token=") && uriString.startsWith("animescraper://success")) {
            isAniListAuthHandled = true
            mainViewModel.handleAuthRedirect(intent)
        }
    }
    
    fun resetAuthFlags() {
        isMalAuthHandled = false
        isAniListAuthHandled = false
    }
}

@Suppress("ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE")
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    isOled: Boolean,
    showStatusColors: Boolean,
    showAnimeCardButtons: Boolean,
    preferEnglishTitles: Boolean,
    preventScheduleSync: Boolean,
    isLoggedIn: Boolean
) {
    val hideNavbar by viewModel.hideNavbar.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val extViewModel: ExtensionsViewModel = viewModel()
    val extUiState by extViewModel.uiState.collectAsState()

    val startupScreen by viewModel.startupScreen.collectAsState()
    val currentPageState = remember { mutableIntStateOf(startupScreen) }
    var currentPage by currentPageState

    var preloadedPages by remember { mutableStateOf(setOf(1, 3)) }

    var overlayOpen by remember { mutableStateOf(false) }

    val currentlyWatching by viewModel.currentlyWatching.collectAsState()
    val planningToWatch by viewModel.planningToWatch.collectAsState()
    val completed by viewModel.completed.collectAsState()
    val onHold by viewModel.onHold.collectAsState()
    val dropped by viewModel.dropped.collectAsState()

    val forwardSkipSeconds by viewModel.forwardSkipSeconds.collectAsState(initial = 10)
    val backwardSkipSeconds by viewModel.backwardSkipSeconds.collectAsState(initial = 10)

    val simplifyEpisodeMenu by viewModel.simplifyEpisodeMenu.collectAsState(initial = true)
    val hideAdultContent by viewModel.hideAdultContent.collectAsState(initial = false)

    val aniListFavorites by viewModel.aniListFavorites.collectAsState()
    val aniListFavoriteIds = remember(aniListFavorites) { aniListFavorites.map { it.id }.toSet() }
    val malFavorites by viewModel.malFavorites.collectAsState()
    val localAnimeStatus by viewModel.localAnimeStatus.collectAsState()
    val isFavoriteRateLimited by viewModel.isFavoriteRateLimited.collectAsState()
    val playbackPositions by viewModel.playbackPositions.collectAsState()
    val playbackDurations by viewModel.playbackDurations.collectAsState()

    LaunchedEffect(isFavoriteRateLimited) {
        if (isFavoriteRateLimited) {
            Toast.makeText(context, "Please wait before toggling again", Toast.LENGTH_SHORT).show()
        }
    }

    val autoSkipOpening by viewModel.autoSkipOpening.collectAsState(initial = false)
    val autoSkipEnding by viewModel.autoSkipEnding.collectAsState(initial = false)
    val autoPlayNextEpisode by viewModel.autoPlayNextEpisode.collectAsState(initial = false)

    val disableMaterialColors by viewModel.disableMaterialColors.collectAsState(initial = false)
    val preferredCategory by viewModel.preferredCategory.collectAsState(initial = "sub")
    val showBufferIndicator by viewModel.showBufferIndicator.collectAsState(initial = true)
    val bufferAheadSeconds by viewModel.bufferAheadSeconds.collectAsState(initial = 30)
    val swipeVolume by viewModel.swipeVolume.collectAsState(initial = false)
    val swipeBrightness by viewModel.swipeBrightness.collectAsState(initial = false)
    val swipeSwap by viewModel.swipeSwap.collectAsState(initial = false)

    val isLoadingHome by viewModel.isLoadingHome.collectAsState()
    LaunchedEffect(isLoadingHome) {
        if (!isLoadingHome && 0 !in preloadedPages) {
            preloadedPages = preloadedPages + 0
        }
    }

    var isLoggedInKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(isLoggedIn) {
        isLoggedInKey++
    }

    LaunchedEffect(currentPage) {
        if (currentPage !in preloadedPages) {
            preloadedPages = preloadedPages + currentPage
            when (currentPage) {
                0 -> { viewModel.fetchAiringSchedule() }
                1 -> { }
                2 -> { viewModel.refreshHome() }
                3 -> { }
            }
        }
    }

    var showPlayer by remember { mutableStateOf(false) }
    var isAutoRefreshing by remember { mutableStateOf(false) }
    var pendingSeekPosition by remember { mutableStateOf<Long?>(null) }
    var currentVideoUrl by remember { mutableStateOf<String?>(null) }
    var currentReferer by remember { mutableStateOf("https://megacloud.tv/") }
    var currentSubtitleUrl by remember { mutableStateOf<String?>(null) }
    var currentAnime by remember { mutableStateOf<AnimeMedia?>(null) }
    var currentEpisode by remember { mutableIntStateOf(0) }
    var totalEpisodes by remember { mutableIntStateOf(0) }
    var isLoadingStream by remember { mutableStateOf(false) }
    var loadingJob by remember { mutableStateOf<Job?>(null) }
    var streamError by remember { mutableStateOf<String?>(null) }
    var currentServerAttempt by remember { mutableStateOf<String?>(null) }
    var currentServerAttemptIsFallback by remember { mutableStateOf(false) }

    var currentEpisodeInfo by remember { mutableStateOf<EpisodeStreams?>(null) }
    var currentEpisodeTitle by remember { mutableStateOf<String?>(null) }
    var hasPrefetchedNextOnTracking by remember { mutableStateOf(false) }

    LaunchedEffect(currentEpisode) {
        hasPrefetchedNextOnTracking = false
    }

    var currentCategory by remember { mutableStateOf("sub") }
    var currentServerName by remember { mutableStateOf("") }
    var currentServerIndex by remember { mutableIntStateOf(0) }

    var isFallbackStream by remember { mutableStateOf(false) }
    var requestedCategory by remember { mutableStateOf("sub") }
    var actualCategory by remember { mutableStateOf("sub") }
    var isManualServerChange by remember { mutableStateOf(false) }
    var isChangingEpisode by remember { mutableStateOf(false) }
    var episodeTrigger by remember { mutableIntStateOf(0) }

    // Quality state
    var currentQualityOptions by remember { mutableStateOf<List<QualityOption>>(emptyList()) }
    var currentQuality by remember { mutableStateOf("Auto") }

    var savedPlaybackPosition by remember { mutableLongStateOf(0L) }

    var overlayState by remember { mutableStateOf<OverlayState>(OverlayState.None) }
    
    var scheduleDialogOpen by remember { mutableStateOf(false) }
    
    var showSearchScreen by remember { mutableStateOf(false) }
    
    var detailedAnimeFromMal by remember { mutableStateOf<DetailedAnimeData?>(null) }

    // Extension flow state
    var showNoExtDialog by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var pendingSettingsGroup by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(Unit) {
        viewModel.openExtensionsEvents.collect {
            showSettings = true
            pendingSettingsGroup = "extensions"
        }
    }
    
    var extensionVideos by remember { mutableStateOf<List<Video>?>(null) }
    var extensionHosters by remember { mutableStateOf<List<eu.kanade.tachiyomi.animesource.model.Hoster>?>(null) }
    var showExtHosterDialog by remember { mutableStateOf(false) }
    var showExtVideoDialog by remember { mutableStateOf(false) }
    var pendingExtResult by remember { mutableStateOf<MainViewModel.ExtensionStreamResult?>(null) }
    var isExtensionFlow by remember { mutableStateOf(false) }
    var extensionOkHttpClient by remember { mutableStateOf<okhttp3.OkHttpClient?>(null) }
    var extensionVideoHeaders by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var extensionSourcePackage by remember { mutableStateOf("") }
    var extensionEpisodeUrl by remember { mutableStateOf("") }
    var extensionEpisodeNumber by remember { mutableIntStateOf(0) }
    var extensionServers by remember { mutableStateOf(emptyList<ServerInfo>()) }
    var extensionName by remember { mutableStateOf("") }
    var currentSubtitleTracks by remember { mutableStateOf<List<eu.kanade.tachiyomi.animesource.model.Track>>(emptyList()) }
    var cachedExtensionNext by remember { mutableStateOf<MainViewModel.ExtensionStreamResult?>(null) }
    val episodeCache = remember { mutableMapOf<Int, MainViewModel.ExtensionStreamResult>() }
    
    // Callback to show detailed anime from MAL history using AniList API
    val onShowDetailedAnimeFromMal: (Int) -> Unit = { malId ->
        kotlinx.coroutines.MainScope().launch {
            val detailedData = viewModel.fetchDetailedAnimeDataByMalId(malId)
            detailedAnimeFromMal = detailedData
        }
    }
    
    // Animekai timestamps (PRIMARY source)
    var animekaiIntroStart by remember { mutableStateOf<Int?>(null) }
    var animekaiIntroEnd by remember { mutableStateOf<Int?>(null) }
    var animekaiOutroStart by remember { mutableStateOf<Int?>(null) }
    var animekaiOutroEnd by remember { mutableStateOf<Int?>(null) }

        var showStatusListScreen by remember { mutableStateOf(false) }
        var statusListTitle by remember { mutableStateOf("") }
    var statusListType by remember { mutableStateOf("") }
    var statusListIcon by remember { mutableStateOf<ImageVector?>(null) }
    var statusListAnime by remember { mutableStateOf<List<AnimeMedia>>(emptyList()) }

    var selectedAnimeState by remember { mutableStateOf<AnimeMedia?>(null) }
    var showDetailedAnimeScreen by remember { mutableStateOf(false) }
    var currentCardBounds by remember { mutableStateOf<MainViewModel.CardBounds?>(null) }

    val mainAct = context as? MainActivity
    LaunchedEffect(Unit) {
        mainAct?.widgetClicks?.collect { animeId ->
            if (animeId <= 0) return@collect
            val detailedData = viewModel.fetchDetailedAnimeData(animeId)
            if (detailedData != null) {
                selectedAnimeState = AnimeMedia(
                    id = detailedData.id,
                    title = detailedData.title,
                    titleEnglish = detailedData.titleEnglish,
                    cover = detailedData.cover,
                    banner = detailedData.banner,
                    progress = 0,
                    totalEpisodes = detailedData.episodes,
                    latestEpisode = detailedData.latestEpisode,
                    status = detailedData.status ?: "",
                    averageScore = detailedData.averageScore,
                    genres = detailedData.genres,
                    listStatus = "",
                    listEntryId = 0,
                    year = detailedData.year,
                    malId = detailedData.malId
                )
                showDetailedAnimeScreen = true
            }
        }
    }

    val animeStatusMap = remember(currentlyWatching, planningToWatch, completed, onHold, dropped) {
        val map = mutableMapOf<Int, String>()
        currentlyWatching.forEach { map[it.id] = "CURRENT" }
        planningToWatch.forEach { map[it.id] = "PLANNING" }
        completed.forEach { map[it.id] = "COMPLETED" }
        onHold.forEach { map[it.id] = "PAUSED" }
        dropped.forEach { map[it.id] = "DROPPED" }
        map
    }

    val animeProgressMap = remember(currentlyWatching, planningToWatch, completed, onHold, dropped) {
        val map = mutableMapOf<Int, Int>()
        currentlyWatching.forEach { if (it.progress > 0) map[it.id] = it.progress }
        planningToWatch.forEach { if (it.progress > 0) map[it.id] = it.progress }
        completed.forEach { if (it.progress > 0) map[it.id] = it.progress }
        onHold.forEach { if (it.progress > 0) map[it.id] = it.progress }
        dropped.forEach { if (it.progress > 0) map[it.id] = it.progress }
        map
    }

    val onShowAnimeDialog: (ExploreAnime, ExploreAnime?) -> Unit = { anime, previousAnime ->
        val currentDialog = overlayState as? OverlayState.ExploreAnimeDialog
        val firstAnime = currentDialog?.firstAnime ?: previousAnime ?: anime
        val isFirstOpen = currentDialog == null
        val prevStates = if (currentDialog != null) currentDialog.previousStates + currentDialog else emptyList()
        // Clear the card bounds when opening the detailed screen to hide the source card
        viewModel.clearExploreAnimeCardBounds()
        viewModel.clearHomeAnimeCardBounds()
        overlayState = OverlayState.ExploreAnimeDialog(anime = anime, firstAnime = firstAnime, isFirstOpen = isFirstOpen, previousStates = prevStates)
    }
    
    // Wrapper for callbacks that expect single parameter

    // Callback to pop the navigation stack (when closing from ExploreScreen inline dialog or system back)
    val onClearAnimeStack: () -> Unit = {
        val prev = overlayState.previousStates
        overlayState = if (prev.isNotEmpty()) prev.last() else OverlayState.None
    }

    fun sanitizeEpisodeTitle(title: String?): String? {
        if (title == null) return null
        return title.replaceFirst(Regex("^Ep\\.?(?:isode)?\\s*\\d+[\\s:\\-–—]+", RegexOption.IGNORE_CASE), "").trim()
    }

    fun playExtensionVideo(result: MainViewModel.ExtensionStreamResult, index: Int) {
        result.videos.forEachIndexed { _, _ ->
        }
        val video = result.videos.find { it.videoUrl == result.url }
            ?: result.videos.getOrNull(index)
            ?: return
        streamError = null
        currentEpisodeTitle = sanitizeEpisodeTitle(result.episode?.name) ?: "Episode $currentEpisode"
        // Use result.url (which may have been resolved from proxy to real URL), fallback to video.videoUrl
        currentVideoUrl = result.url.ifEmpty { video.videoUrl }
        currentReferer = result.referer
        val preferredLang = viewModel.defaultSubtitleLang.value
        val sortedTracks = video.subtitleTracks.sortedByDescending { t ->
            when {
                t.lang.equals(preferredLang, ignoreCase = true) -> 2
                t.lang.equals("English", ignoreCase = true) -> 1
                else -> 0
            }
        }
        currentSubtitleTracks = sortedTracks
        currentSubtitleUrl = sortedTracks.firstOrNull()?.url
        sortedTracks.forEachIndexed { _, _ ->
        }
        extensionName = result.source?.name ?: ""
        currentServerName = result.hosters?.firstOrNull()?.hosterName ?: extensionName.ifEmpty { "Extension" }
        val hasDubHoster = result.hosters?.any { it.hosterName.contains("dub", ignoreCase = true) } == true
        currentCategory = if (hasDubHoster || result.videoTitle.contains("dub", ignoreCase = true)) "dub" else "sub"
        actualCategory = currentCategory
        requestedCategory = preferredCategory
        currentQualityOptions = emptyList()
        currentQuality = "Auto"
        currentServerIndex = 0
        isExtensionFlow = false
        extensionOkHttpClient = result.extensionClient
        extensionVideoHeaders = result.videoHeaders
        extensionServers = (result.hosters ?: emptyList()).map { hoster ->
            ServerInfo(name = hoster.hosterName, url = hoster.hosterUrl)
        }
        showPlayer = true
        // After starting DUB playback, fetch SUB subtitles in background
        if (currentCategory == "dub" && result.source != null && result.episode != null) {
            val src = result.source
            val ep = result.episode
            scope.launch {
                val episodeVideos = withContext(Dispatchers.IO) {
                    try { src.getVideoList(ep) } catch (_: Throwable) { emptyList() }
                }
                val subVideo = episodeVideos.find {
                    it.videoTitle.contains("sub", ignoreCase = true) && !it.videoTitle.contains("dub", ignoreCase = true) && it.subtitleTracks.isNotEmpty()
                } ?: episodeVideos.find {
                    !it.videoTitle.contains("dub", ignoreCase = true) && it.subtitleTracks.isNotEmpty()
                }
                if (subVideo != null) {
                    subVideo.subtitleTracks.forEachIndexed { _, _ ->
                    }
                    currentSubtitleTracks = currentSubtitleTracks + subVideo.subtitleTracks
                    if (currentSubtitleUrl == null) {
                        currentSubtitleUrl = currentSubtitleTracks.firstOrNull()?.url
                    }
                }
            }
        }
    }

    fun loadAndPlayEpisode(anime: AnimeMedia, episode: Int, isAutoRefresh: Boolean = false) {
        if (!isAutoRefresh) {
            isAutoRefreshing = false
            pendingSeekPosition = null
        }
        currentAnime = anime
        currentEpisode = episode
        totalEpisodes = anime.totalEpisodes
        streamError = null
        savedPlaybackPosition = viewModel.getPlaybackPosition(anime.id, episode)
        // For auto-refresh, update the player in-place (no unmount/remount).
        // For initial load, unmount so PlayerScreen reinitializes fresh.
        if (!isAutoRefresh) {
            showPlayer = false
        }

        val extPackage = viewModel.defaultExtensionPackage.value
        if (extPackage.isNotEmpty()) {
            isExtensionFlow = true
            isLoadingStream = true
            extensionVideos = null
            extensionHosters = null
            pendingExtResult = null
            showExtHosterDialog = false
            showExtVideoDialog = false
            scope.launch {
                val result = viewModel.playEpisodeWithExtension(anime, episode, extPackage)
                pendingExtResult = result
                if (result != null && result.videos.isNotEmpty()) {
                    extensionVideos = result.videos
                    extensionHosters = result.hosters
                    extensionSourcePackage = extPackage
                    extensionEpisodeNumber = episode
                    extensionEpisodeUrl = result.episode?.url ?: ""
                    PlayerData.extensionSource = result.source
                    PlayerData.extensionEpisode = result.episode
                    PlayerData.allHosters = result.hosters ?: emptyList()
                    playExtensionVideo(result, 0)
                } else {
                    streamError = "Extension stream not found: Ep $episode"
                    Toast.makeText(context, "Extension failed for Ep $episode", Toast.LENGTH_SHORT).show()
                }
                if (isAutoRefresh) isAutoRefreshing = false
                isLoadingStream = false
            }
            return
        }

        showNoExtDialog = true
        return
    }

    suspend fun getTmdbEpisodeTitle(anime: AnimeMedia, episode: Int): String {
        val cachedEpisodes = viewModel.getCachedTmdbEpisodes(anime.id)
        if (cachedEpisodes != null) {
            val title = cachedEpisodes.find { it.episode == episode }?.title
            if (!title.isNullOrEmpty()) return sanitizeEpisodeTitle(title) ?: "Episode $episode"
        }
        return try {
            val tmdbEpisodes = viewModel.fetchTmdbEpisodes(anime.title, anime.id, anime.year, anime.format)
            val title = tmdbEpisodes.find { it.episode == episode }?.title
            sanitizeEpisodeTitle(title) ?: "Episode $episode"
        } catch (_: Exception) {
            "Episode $episode"
        }
    }

    val onPlayEpisode: (AnimeMedia, Int, String?) -> Unit = { anime, episode, title ->
        if (title == null) {
            scope.launch {
                currentEpisodeTitle = getTmdbEpisodeTitle(anime, episode)
                loadAndPlayEpisode(anime, episode)
            }
        } else {
            currentEpisodeTitle = sanitizeEpisodeTitle(title) ?: "Episode $episode"
            loadAndPlayEpisode(anime, episode)
        }
    }

    fun fetchAndCacheEpisode(ep: Int) {
        if (currentAnime == null) return
        val pkg = extensionSourcePackage
        if (pkg.isEmpty()) return
        scope.launch {
            if (episodeCache.containsKey(ep)) return@launch
            val result = viewModel.playEpisodeWithExtension(currentAnime!!, ep, pkg)
            if (result != null) {
                episodeCache[ep] = result
            }
        }
    }

    fun prefetchExtensionNextEpisode() {
        if (currentAnime == null) return
        scope.launch {
            val nextEp = currentEpisode + 1
            val pkg = extensionSourcePackage
            if (pkg.isEmpty()) return@launch
            val result = viewModel.playEpisodeWithExtension(currentAnime!!, nextEp, pkg)
            if (result != null) {
                cachedExtensionNext = result
                episodeCache[nextEp] = result
            }
        }
    }

    val onPreviousEpisode: () -> Unit = {
        if (!isChangingEpisode && currentAnime != null && currentEpisode > 1) {
            isChangingEpisode = true

            val prevEp = currentEpisode - 1
            val cached = episodeCache[prevEp]

            if (cached != null) {
                currentEpisode = prevEp
                savedPlaybackPosition = viewModel.getPlaybackPosition(currentAnime!!.id, prevEp)
                currentEpisodeTitle = sanitizeEpisodeTitle(cached.episode?.name) ?: "Episode $prevEp"
                currentVideoUrl = cached.url
                currentReferer = cached.referer
                currentSubtitleUrl = cached.subtitleUrl
                currentSubtitleTracks = cached.videos.firstOrNull()?.subtitleTracks ?: emptyList()
                currentServerName = if (!cached.hosters.isNullOrEmpty()) cached.hosters.first().hosterName else "Extension"
                currentCategory = "sub"
                currentQualityOptions = cached.videos.map { QualityOption(quality = it.videoTitle, url = it.videoUrl, width = it.resolution ?: 0) }
                currentQuality = cached.videoTitle
                extensionOkHttpClient = cached.extensionClient
                extensionVideoHeaders = cached.videoHeaders
                extensionHosters = cached.hosters
                extensionServers = (cached.hosters ?: emptyList()).map { h -> ServerInfo(name = h.hosterName, url = h.hosterUrl) }
                episodeTrigger++
                isChangingEpisode = false
                prefetchExtensionNextEpisode()
                fetchAndCacheEpisode(prevEp - 1)
            } else {
                isLoadingStream = true
                scope.launch {
                    val pkg = extensionSourcePackage
                    if (pkg.isEmpty()) {
                        Toast.makeText(context, "No extension configured", Toast.LENGTH_SHORT).show()
                        isChangingEpisode = false
                        isLoadingStream = false
                        return@launch
                    }
                    val result = viewModel.playEpisodeWithExtension(currentAnime!!, prevEp, pkg)
                    if (result != null && result.videos.isNotEmpty()) {
                        episodeCache[prevEp] = result
                        currentEpisode = prevEp
                        savedPlaybackPosition = viewModel.getPlaybackPosition(currentAnime!!.id, prevEp)
                        currentEpisodeTitle = sanitizeEpisodeTitle(result.episode?.name) ?: "Episode $prevEp"
                        currentVideoUrl = result.url
                        currentReferer = result.referer
                        currentSubtitleUrl = result.subtitleUrl
                        currentSubtitleTracks = result.videos.firstOrNull()?.subtitleTracks ?: emptyList()
                        currentServerName = if (!result.hosters.isNullOrEmpty()) result.hosters.first().hosterName else "Extension"
                        currentCategory = "sub"
                        currentQualityOptions = result.videos.map { QualityOption(quality = it.videoTitle, url = it.videoUrl, width = it.resolution ?: 0) }
                        currentQuality = result.videoTitle
                        extensionOkHttpClient = result.extensionClient
                        extensionVideoHeaders = result.videoHeaders
                        extensionHosters = result.hosters
                        extensionServers = (result.hosters ?: emptyList()).map { h -> ServerInfo(name = h.hosterName, url = h.hosterUrl) }
                        episodeTrigger++
                        isChangingEpisode = false
                        isLoadingStream = false
                        prefetchExtensionNextEpisode()
                        fetchAndCacheEpisode(prevEp - 1)
                    } else {
                        Toast.makeText(context, "Failed to load episode $prevEp", Toast.LENGTH_SHORT).show()
                        isChangingEpisode = false
                        isLoadingStream = false
                    }
                }
            }
        }
    }

    val onNextEpisode: () -> Unit = {
        if (!isChangingEpisode && currentAnime != null) {
            isChangingEpisode = true

            val nextEp = currentEpisode + 1
            val cached = cachedExtensionNext ?: episodeCache[nextEp]

            if (cached != null) {
                cachedExtensionNext = null
                currentEpisode = nextEp
                savedPlaybackPosition = viewModel.getPlaybackPosition(currentAnime!!.id, nextEp)
                currentEpisodeTitle = sanitizeEpisodeTitle(cached.episode?.name) ?: "Episode $nextEp"
                currentVideoUrl = cached.url
                currentReferer = cached.referer
                currentSubtitleUrl = cached.subtitleUrl
                currentSubtitleTracks = cached.videos.firstOrNull()?.subtitleTracks ?: emptyList()
                currentServerName = if (!cached.hosters.isNullOrEmpty()) cached.hosters.first().hosterName else "Extension"
                currentCategory = "sub"
                currentQualityOptions = cached.videos.map { QualityOption(quality = it.videoTitle, url = it.videoUrl, width = it.resolution ?: 0) }
                currentQuality = cached.videoTitle
                extensionOkHttpClient = cached.extensionClient
                extensionVideoHeaders = cached.videoHeaders
                extensionHosters = cached.hosters
                extensionServers = (cached.hosters ?: emptyList()).map { h -> ServerInfo(name = h.hosterName, url = h.hosterUrl) }
                episodeCache[nextEp] = cached
                episodeTrigger++
                isChangingEpisode = false
                prefetchExtensionNextEpisode()
                fetchAndCacheEpisode(nextEp - 1)
            } else {
                isLoadingStream = true
                scope.launch {
                    val pkg = extensionSourcePackage
                    if (pkg.isEmpty()) {
                        Toast.makeText(context, "No extension configured", Toast.LENGTH_SHORT).show()
                        isChangingEpisode = false
                        isLoadingStream = false
                        return@launch
                    }
                    val result = viewModel.playEpisodeWithExtension(currentAnime!!, nextEp, pkg)
                    if (result != null && result.videos.isNotEmpty()) {
                        episodeCache[nextEp] = result
                        currentEpisode = nextEp
                        savedPlaybackPosition = viewModel.getPlaybackPosition(currentAnime!!.id, nextEp)
                        currentEpisodeTitle = sanitizeEpisodeTitle(result.episode?.name) ?: "Episode $nextEp"
                        currentVideoUrl = result.url
                        currentReferer = result.referer
                        currentSubtitleUrl = result.subtitleUrl
                        currentSubtitleTracks = result.videos.firstOrNull()?.subtitleTracks ?: emptyList()
                        currentServerName = if (!result.hosters.isNullOrEmpty()) result.hosters.first().hosterName else "Extension"
                        currentCategory = "sub"
                        currentQualityOptions = result.videos.map { QualityOption(quality = it.videoTitle, url = it.videoUrl, width = it.resolution ?: 0) }
                        currentQuality = result.videoTitle
                        extensionOkHttpClient = result.extensionClient
                        extensionVideoHeaders = result.videoHeaders
                        extensionHosters = result.hosters
                        extensionServers = (result.hosters ?: emptyList()).map { h -> ServerInfo(name = h.hosterName, url = h.hosterUrl) }
                        episodeTrigger++
                        isChangingEpisode = false
                        isLoadingStream = false
                        prefetchExtensionNextEpisode()
                        fetchAndCacheEpisode(nextEp - 1)
                    } else {
                        Toast.makeText(context, "Failed to load episode $nextEp", Toast.LENGTH_SHORT).show()
                        isChangingEpisode = false
                        isLoadingStream = false
                    }
                }
            }
        }
    }

    fun handleExtensionServerChange(hosterName: String) {
        val hoster = extensionHosters?.find { it.hosterName == hosterName } ?: return
        val source = PlayerData.extensionSource
        if (source == null) {
            Toast.makeText(context, "Source not available", Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            isLoadingStream = true
            val result = viewModel.fetchExtensionHosterVideos(source, hoster)
            if (result != null) {
                currentVideoUrl = result.url
                currentReferer = result.referer
                currentSubtitleUrl = result.subtitleUrl
                currentServerName = hosterName
                currentCategory = if (hosterName.contains("dub", ignoreCase = true) || result.videoTitle.contains("dub", ignoreCase = true)) "dub" else "sub"
                currentQualityOptions = result.videos.map { QualityOption(quality = it.videoTitle, url = it.videoUrl, width = it.resolution ?: 0) }
                currentQuality = result.videoTitle
                extensionOkHttpClient = result.extensionClient
                extensionVideoHeaders = result.videoHeaders
                episodeTrigger++
            } else {
                Toast.makeText(context, "Failed to load $hosterName", Toast.LENGTH_SHORT).show()
            }
            isLoadingStream = false
        }
    }

    fun invalidateCurrentStreamCache() {
        currentAnime?.let { anime ->
            viewModel.invalidateStreamCache(anime.id, currentEpisode, currentCategory)
            viewModel.clearAnimeExtensionStreamCaches(anime.id)
            currentVideoUrl?.let { viewModel.removeFromVideoCache(it) }
        }
    }

    fun onPlaybackError() {
        // Invalidate the cache for this stream
        invalidateCurrentStreamCache()

        currentAnime?.let { _ ->

        }
    }

    val exploreDialog = overlayState as? OverlayState.ExploreAnimeDialog
    if (exploreDialog != null) {
        val isAnimeFavorite = aniListFavoriteIds.contains(exploreDialog.anime.id)
        DetailedAnimeScreen(
            anime = exploreDialog.anime.toDetailedAnimeData(),
            viewModel = viewModel,
            isOled = isOled,
            currentStatus = animeStatusMap[exploreDialog.anime.id],
            currentProgress = animeProgressMap[exploreDialog.anime.id],
            isFavorite = isAnimeFavorite,
            initialCardBounds = viewModel.exploreAnimeCardBounds.collectAsState().value,
            onDismiss = {
                overlayState = OverlayState.None
            },
            onNavigateBack = onClearAnimeStack,
            onSwipeToClose = { overlayState = OverlayState.None },
            onPlayEpisode = { episode, _ ->
                val animeMedia = AnimeMedia(
                    id = exploreDialog.anime.id,
                    title = exploreDialog.anime.title,
                    titleEnglish = exploreDialog.anime.titleEnglish,
                    cover = exploreDialog.anime.cover,
                    banner = exploreDialog.anime.banner,
                    progress = 0,
                    totalEpisodes = exploreDialog.anime.episodes,
                    latestEpisode = exploreDialog.anime.latestEpisode,
                    status = "",
                    averageScore = exploreDialog.anime.averageScore,
                    genres = exploreDialog.anime.genres,
                    listStatus = "",
                    listEntryId = 0,
                    year = exploreDialog.anime.year,
                    malId = exploreDialog.anime.malId
                )
                viewModel.addExploreAnimeToList(exploreDialog.anime, "CURRENT")
                onPlayEpisode(animeMedia, episode, null)
                overlayState = OverlayState.None
            },
            onUpdateStatus = { status ->
                if (status != null) {
                    viewModel.addExploreAnimeToList(exploreDialog.anime, status)
                }
            },
            onRemove = {
                viewModel.removeAnimeFromList(exploreDialog.anime.id)
            },
            onUpdateLocalStatus = { status ->
                val currentEntry = localAnimeStatus[exploreDialog.anime.id]
                if (status != null) {
                    viewModel.setLocalAnimeStatus(
                        exploreDialog.anime.id,
                        LocalAnimeEntry(
                            id = exploreDialog.anime.id,
                            status = status,
                            progress = currentEntry?.progress ?: 0,
                            totalEpisodes = exploreDialog.anime.episodes
                        )
                    )
                } else {
                    viewModel.setLocalAnimeStatus(exploreDialog.anime.id, null)
                }
            },
            onRemoveLocalStatus = {
                viewModel.setLocalAnimeStatus(exploreDialog.anime.id, null)
            },
            isLoggedIn = isLoggedIn,
            onNavigateToSettings = {
                overlayState = OverlayState.None
                showSettings = true
                pendingSettingsGroup = if (extUiState.extensions.isEmpty()) "extensions" else "stream"
            },
            onNoExtension = {
                overlayState = OverlayState.None
                showSettings = true
                pendingSettingsGroup = if (extUiState.extensions.isEmpty()) "extensions" else "stream"
            },
            onRelationClick = { relation ->
                try {
                    scope.launch {
                        try {
                            delay(100.milliseconds)
                            val detailedData = viewModel.fetchDetailedAnimeData(relation.id)
                            if (detailedData != null) {
                                viewModel.clearExploreAnimeCardBounds()
                                val newAnime = ExploreAnime(
                                    id = relation.id,
                                    title = detailedData.title,
                                    titleEnglish = detailedData.titleEnglish,
                                    cover = detailedData.cover,
                                    banner = detailedData.banner,
                                    episodes = detailedData.episodes,
                                    latestEpisode = detailedData.latestEpisode,
                                    averageScore = detailedData.averageScore,
                                    genres = detailedData.genres,
                                    year = detailedData.year,
                                    format = detailedData.format
                                )
                                overlayState = OverlayState.ExploreAnimeDialog(
                                    anime = newAnime,
                                    firstAnime = exploreDialog.firstAnime ?: exploreDialog.anime,
                                    isFirstOpen = false,
                                    previousStates = exploreDialog.previousStates + exploreDialog
                                )
                            } else {
                                Toast.makeText(context, "Anime not found - ID: ${relation.id}", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            },
            onCharacterClick = { characterId ->
                overlayState = OverlayState.CharacterDialog(
                    characterId = characterId,
                    animeId = exploreDialog.anime.id,
                    previousStates = exploreDialog.previousStates + exploreDialog
                )
            },
            onStaffClick = { staffId ->
                overlayState = OverlayState.StaffDialog(
                    staffId = staffId,
                    animeId = exploreDialog.anime.id,
                    previousStates = exploreDialog.previousStates + exploreDialog
                )
            },
            onViewAllCast = {
                overlayState = OverlayState.AllCastDialog(
                    animeId = exploreDialog.anime.id,
                    animeTitle = exploreDialog.anime.title,
                    previousStates = exploreDialog.previousStates + exploreDialog
                )
            },
            onViewAllStaff = {
                overlayState = OverlayState.AllStaffDialog(
                    animeId = exploreDialog.anime.id,
                    animeTitle = exploreDialog.anime.title,
                    previousStates = exploreDialog.previousStates + exploreDialog
                )
            },
            onViewAllRelations = { animeId, title ->
                overlayState = OverlayState.AllRelationsDialog(
                    animeId = animeId,
                    animeTitle = title,
                    previousStates = exploreDialog.previousStates + exploreDialog
                )
            },
            preferEnglishTitles = preferEnglishTitles,
        )
    }

    // DetailedAnimeScreen for StatusListScreen
    if (selectedAnimeState != null && showDetailedAnimeScreen) {
        val currentStatusForAnime = animeStatusMap[selectedAnimeState!!.id]
        val currentProgressForAnime = animeProgressMap[selectedAnimeState!!.id]
        val isAnimeFavorite = aniListFavoriteIds.contains(selectedAnimeState!!.id)
        DetailedAnimeScreen(
            anime = selectedAnimeState!!.toDetailedAnimeData(),
            viewModel = viewModel,
            isOled = isOled,
            currentStatus = currentStatusForAnime,
            currentProgress = currentProgressForAnime,
            isFavorite = isAnimeFavorite,
            initialCardBounds = currentCardBounds,
            onDismiss = { showDetailedAnimeScreen = false },
            onNavigateBack = { showDetailedAnimeScreen = false },
            onSwipeToClose = { showDetailedAnimeScreen = false },
            onPlayEpisode = { episode, _ ->
                onPlayEpisode(selectedAnimeState!!, episode, null)
                showDetailedAnimeScreen = false
            },
            onUpdateStatus = { status ->
                if (status != null) {
                    viewModel.addExploreAnimeToList(
                        ExploreAnime(
                            id = selectedAnimeState!!.id,
                            title = selectedAnimeState!!.title,
                            titleEnglish = selectedAnimeState!!.titleEnglish,
                            cover = selectedAnimeState!!.cover,
                            banner = selectedAnimeState!!.banner,
                            episodes = selectedAnimeState!!.totalEpisodes,
                            latestEpisode = selectedAnimeState!!.latestEpisode,
                            averageScore = selectedAnimeState!!.averageScore,
                            genres = selectedAnimeState!!.genres,
                            year = selectedAnimeState!!.year,
                            format = selectedAnimeState!!.format
                        ),
                        status
                    )
                }
            },
            onRemove = {
                viewModel.removeAnimeFromList(selectedAnimeState!!.id)
                showDetailedAnimeScreen = false
            },
            onRelationClick = { relation ->
                try {
                    scope.launch {
                        try {
                            delay(100.milliseconds)
                            val detailedData = viewModel.fetchDetailedAnimeData(relation.id)
                            if (detailedData != null) {
                                currentCardBounds = null
                                selectedAnimeState = AnimeMedia(
                                    id = detailedData.id,
                                    title = detailedData.title,
                                    titleEnglish = detailedData.titleEnglish,
                                    cover = detailedData.cover,
                                    banner = detailedData.banner,
                                    progress = 0,
                                    totalEpisodes = detailedData.episodes,
                                    latestEpisode = detailedData.latestEpisode,
                                    status = detailedData.status ?: "",
                                    averageScore = detailedData.averageScore,
                                    genres = detailedData.genres,
                                    listStatus = "",
                                    listEntryId = 0,
                                    year = detailedData.year,
                                    malId = detailedData.malId
                                )
                            } else {
                                Toast.makeText(context, "Anime not found", Toast.LENGTH_SHORT).show()
                            }
                        } catch (_: Exception) {
                            Toast.makeText(context, "Anime not found", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (_: Exception) {
                    Toast.makeText(context, "Anime not found", Toast.LENGTH_SHORT).show()
                }
            },
            onCharacterClick = { characterId ->
                overlayState = OverlayState.CharacterDialog(
                    characterId = characterId,
                    animeId = selectedAnimeState!!.id
                )
            },
            onStaffClick = { staffId ->
                overlayState = OverlayState.StaffDialog(
                    staffId = staffId,
                    animeId = selectedAnimeState!!.id
                )
            },
            onViewAllCast = { overlayState = OverlayState.AllCastDialog(animeId = selectedAnimeState!!.id, animeTitle = selectedAnimeState!!.title) },
            onViewAllStaff = { overlayState = OverlayState.AllStaffDialog(animeId = selectedAnimeState!!.id, animeTitle = selectedAnimeState!!.title) },
            onViewAllRelations = { animeId, title -> overlayState = OverlayState.AllRelationsDialog(animeId = animeId, animeTitle = title) },
            isLoggedIn = isLoggedIn,
            onUpdateLocalStatus = { status ->
                val currentEntry = localAnimeStatus[selectedAnimeState!!.id]
                if (status != null) {
                    viewModel.setLocalAnimeStatus(
                        selectedAnimeState!!.id,
                        LocalAnimeEntry(
                            id = selectedAnimeState!!.id,
                            status = status,
                            progress = currentEntry?.progress ?: 0,
                            totalEpisodes = selectedAnimeState!!.totalEpisodes
                        )
                    )
                } else {
                    viewModel.setLocalAnimeStatus(selectedAnimeState!!.id, null)
                }
            },
            onRemoveLocalStatus = { viewModel.setLocalAnimeStatus(selectedAnimeState!!.id, null) },
            preferEnglishTitles = preferEnglishTitles,
            onNavigateToSettings = {
                overlayState = OverlayState.None
                showSettings = true
                pendingSettingsGroup = if (extUiState.extensions.isEmpty()) "extensions" else "stream"
            },
            onNoExtension = {
                overlayState = OverlayState.None
                showSettings = true
                pendingSettingsGroup = if (extUiState.extensions.isEmpty()) "extensions" else "stream"
            }
        )
    }

    // Character Screen
    val characterDialog = overlayState as? OverlayState.CharacterDialog
    if (characterDialog != null) {
        CharacterScreen(
            characterId = characterDialog.characterId,
            viewModel = viewModel,
            onDismiss = {
                overlayState = OverlayState.None
            },
            onNavigateBack = onClearAnimeStack,
            onAnimeClick = { animeId ->
                scope.launch {
                    val detailedData = viewModel.fetchDetailedAnimeData(animeId)
                    if (detailedData != null) {
                        val newAnime = ExploreAnime(
                            id = detailedData.id,
                            title = detailedData.title,
                            titleEnglish = detailedData.titleEnglish,
                            cover = detailedData.cover,
                            banner = detailedData.banner,
                            episodes = detailedData.episodes,
                            latestEpisode = detailedData.latestEpisode,
                            averageScore = detailedData.averageScore,
                            genres = detailedData.genres,
                            year = detailedData.year,
                            format = detailedData.format
                        )
                        overlayState = OverlayState.ExploreAnimeDialog(
                            anime = newAnime, firstAnime = newAnime, isFirstOpen = false,
                            previousStates = characterDialog.previousStates + characterDialog
                        )
                    } else {
                        Toast.makeText(context, "Anime not found", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onCharacterClick = { id ->
                overlayState = OverlayState.CharacterDialog(
                    characterId = id,
                    animeId = 0,
                    previousStates = characterDialog.previousStates + characterDialog
                )
            },
            onStaffClick = { id ->
                overlayState = OverlayState.StaffDialog(
                    staffId = id,
                    animeId = 0,
                    previousStates = characterDialog.previousStates + characterDialog
                )
            }
        )
    }

    // Staff Screen
    val staffDialog = overlayState as? OverlayState.StaffDialog
    if (staffDialog != null) {
        StaffScreen(
            staffId = staffDialog.staffId,
            viewModel = viewModel,
            onDismiss = {
                overlayState = OverlayState.None
            },
            onNavigateBack = onClearAnimeStack,
            onAnimeClick = { animeId ->
                scope.launch {
                    val detailedData = viewModel.fetchDetailedAnimeData(animeId)
                    if (detailedData != null) {
                        val newAnime = ExploreAnime(
                            id = detailedData.id,
                            title = detailedData.title,
                            titleEnglish = detailedData.titleEnglish,
                            cover = detailedData.cover,
                            banner = detailedData.banner,
                            episodes = detailedData.episodes,
                            latestEpisode = detailedData.latestEpisode,
                            averageScore = detailedData.averageScore,
                            genres = detailedData.genres,
                            year = detailedData.year,
                            format = detailedData.format
                        )
                        overlayState = OverlayState.ExploreAnimeDialog(
                            anime = newAnime, firstAnime = newAnime, isFirstOpen = false,
                            previousStates = staffDialog.previousStates + staffDialog
                        )
                    } else {
                        Toast.makeText(context, "Anime not found", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onCharacterClick = { id ->
                overlayState = OverlayState.CharacterDialog(
                    characterId = id,
                    animeId = 0,
                    previousStates = staffDialog.previousStates + staffDialog
                )
            },
            onStaffClick = { id ->
                overlayState = OverlayState.StaffDialog(
                    staffId = id,
                    animeId = 0,
                    previousStates = staffDialog.previousStates + staffDialog
                )
            }
        )
    }

    // All Cast Screen
    val allCastDialog = overlayState as? OverlayState.AllCastDialog
    if (allCastDialog != null) {
        AllCastScreen(
            animeId = allCastDialog.animeId,
            animeTitle = allCastDialog.animeTitle,
            viewModel = viewModel,
            onDismiss = {
                overlayState = OverlayState.None
            },
            onNavigateBack = onClearAnimeStack,
            onCharacterClick = { characterId ->
                overlayState = OverlayState.CharacterDialog(
                    characterId = characterId,
                    animeId = allCastDialog.animeId,
                    previousStates = allCastDialog.previousStates + allCastDialog
                )
            }
        )
    }

    // All Staff Screen
    val allStaffDialog = overlayState as? OverlayState.AllStaffDialog
    if (allStaffDialog != null) {
        AllStaffScreen(
            animeId = allStaffDialog.animeId,
            animeTitle = allStaffDialog.animeTitle,
            viewModel = viewModel,
            onDismiss = {
                overlayState = OverlayState.None
            },
            onNavigateBack = onClearAnimeStack,
            onStaffClick = { staffId ->
                overlayState = OverlayState.StaffDialog(
                    staffId = staffId,
                    animeId = allStaffDialog.animeId,
                    previousStates = allStaffDialog.previousStates + allStaffDialog
                )
            }
        )
    }

    // Episode Download Dialog
    val downloadDialog = overlayState as? OverlayState.EpisodeDownloadDialog
    if (downloadDialog != null) {
        EpisodeDownloadDialog(
            anime = downloadDialog.anime,
            viewModel = viewModel,
            downloadManager = viewModel.episodeDownloadManager,
            isOled = isOled,
            preferEnglishTitles = preferEnglishTitles,
            onDismiss = { overlayState = OverlayState.None },
            onNavigateToSettings = {
                overlayState = OverlayState.None
                showSettings = true
                pendingSettingsGroup = if (extUiState.extensions.isEmpty()) "extensions" else "stream"
            }
        )
    }

    // All Relations Screen
    val allRelationsDialog = overlayState as? OverlayState.AllRelationsDialog
    if (allRelationsDialog != null) {
        AllRelationsScreen(
            animeId = allRelationsDialog.animeId,
            animeTitle = allRelationsDialog.animeTitle,
            viewModel = viewModel,
            onDismiss = {
                overlayState = OverlayState.None
            },
            onNavigateBack = onClearAnimeStack,
            onAnimeClick = { animeId ->
                scope.launch {
                    val detailedData = viewModel.fetchDetailedAnimeData(animeId)
                    if (detailedData != null) {
                        val newAnime = ExploreAnime(
                            id = detailedData.id,
                            title = detailedData.title,
                            titleEnglish = detailedData.titleEnglish,
                            cover = detailedData.cover,
                            banner = detailedData.banner,
                            episodes = detailedData.episodes,
                            latestEpisode = detailedData.latestEpisode,
                            averageScore = detailedData.averageScore,
                            genres = detailedData.genres,
                            year = detailedData.year,
                            format = detailedData.format
                        )
                        overlayState = OverlayState.ExploreAnimeDialog(anime = newAnime, firstAnime = newAnime, isFirstOpen = false)
                    } else {
                        Toast.makeText(context, "Anime not found", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    if (showNoExtDialog) {
        AlertDialog(
            onDismissRequest = { showNoExtDialog = false },
            title = { Text("No Default Extension") },
            text = {
                Text("Set a default extension in Settings to enable streaming.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showNoExtDialog = false
                    overlayState = OverlayState.None
                    showSettings = true
                    pendingSettingsGroup = if (extUiState.extensions.isEmpty()) "extensions" else "stream"
                }) {
                    Text(if (extUiState.extensions.isEmpty()) "Go to Extensions" else "Go to Stream Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNoExtDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showPlayer && currentVideoUrl != null) {
        currentAnime?.let { anime ->
            val released = anime.latestEpisode ?: anime.totalEpisodes
            PlayerScreen(
                videoUrl = currentVideoUrl!!,
                referer = currentReferer,
                subtitleUrl = currentSubtitleUrl,
                subtitleTracks = currentSubtitleTracks,
                currentEpisode = currentEpisode,
                totalEpisodes = totalEpisodes,
                animeName = anime.title,
                episodeTitle = currentEpisodeTitle,
                animeId = anime.id,
                malId = anime.malId ?: 0,
                animeYear = anime.year,
                isLoadingStream = isLoadingStream,
                episodeInfo = currentEpisodeInfo,
                currentServerName = currentServerName,
                currentCategory = currentCategory,
                isFallbackStream = isFallbackStream && !isManualServerChange,
                requestedCategory = requestedCategory,
                forwardSkipSeconds = forwardSkipSeconds,
                backwardSkipSeconds = backwardSkipSeconds,
                savedPosition = savedPlaybackPosition,
                pendingSeekPosition = pendingSeekPosition,
                onSetPendingSeekPosition = {
                    pendingSeekPosition = it
                    if (it != null) isAutoRefreshing = false  // Allow new refresh on consecutive seeks
                },
                currentQuality = currentQuality,
                // Animekai timestamps (PRIMARY source)
                animekaiIntroStart = animekaiIntroStart,
                animekaiIntroEnd = animekaiIntroEnd,
                animekaiOutroStart = animekaiOutroStart,
                animekaiOutroEnd = animekaiOutroEnd,
                onSavePosition = { position, duration ->
                    viewModel.savePlaybackPosition(anime.id, currentEpisode, position, duration)
                },
                onClearPlaybackPosition = { _, episode ->
                    viewModel.clearPlaybackPosition(anime.id, episode)
                },
                onPositionSaved = { position ->
                    savedPlaybackPosition = position
                },
                onProgressUpdate = { percentage ->
                    val trackingPercent = viewModel.trackingPercentage.value
                    if (percentage >= trackingPercent && anime.id > 0) {
                        viewModel.updateAnimeProgress(anime.id, currentEpisode)
                        if (!hasPrefetchedNextOnTracking && currentEpisode < released) {
                            hasPrefetchedNextOnTracking = true
                            prefetchExtensionNextEpisode()
                        }
                    }
                },
                onPreviousEpisode = if (currentEpisode > 1) onPreviousEpisode else null,
                onNextEpisode = if (currentEpisode < released) onNextEpisode else null,
                isLatestEpisode = released in 1..currentEpisode,
                onPlaybackError = { onPlaybackError() },
                onInvalidateStreamCache = { invalidateCurrentStreamCache() },
                onRefreshStream = {
                    if (!isAutoRefreshing) {
                        isAutoRefreshing = true
                        currentAnime?.let { a ->
                            loadAndPlayEpisode(a, currentEpisode, isAutoRefresh = true)
                        }
                    }
                },
                autoSkipOpening = autoSkipOpening,
                autoSkipEnding = autoSkipEnding,
                autoPlayNextEpisode = autoPlayNextEpisode,
                onAutoPlayNextEpisodeChanged = { viewModel.setAutoPlayNextEpisode(it) },
                swipeVolume = swipeVolume,
                swipeBrightness = swipeBrightness,
                swipeSwap = swipeSwap,
                onSwipeVolumeChange = { viewModel.setSwipeVolume(it) },
                onSwipeBrightnessChange = { viewModel.setSwipeBrightness(it) },
                onSwipeSwapChange = { viewModel.setSwipeSwap(it) },
                disableMaterialColors = disableMaterialColors,
                showBufferIndicator = showBufferIndicator,
                bufferAheadSeconds = bufferAheadSeconds,
                onGetCacheDataSourceFactory = { referer -> viewModel.getCacheDataSourceFactory(referer, extensionOkHttpClient, extensionVideoHeaders) },
                onBackClick = { 
                    showPlayer = false
                    currentVideoUrl = null
                    pendingSeekPosition = null
                    extensionOkHttpClient = null
                    extensionVideoHeaders = emptyMap()
                    extensionHosters = null
                    extensionServers = emptyList()
                    cachedExtensionNext = null
                    PlayerData.extensionSource = null
                    PlayerData.extensionEpisode = null
                    PlayerData.allHosters = emptyList()
                },
                extensionOkHttpClient = extensionOkHttpClient,
                extensionVideoHeaders = extensionVideoHeaders,
                extensionServers = extensionServers,
                extensionName = extensionName,
                onExtensionServerChange = { hosterName -> handleExtensionServerChange(hosterName) },
                onPrefetchNextExtensionEpisode = { prefetchExtensionNextEpisode() },
            )
        }

        androidx.activity.compose.BackHandler {
            when {
                scheduleDialogOpen -> {
                    scheduleDialogOpen = false
                }
                overlayState !is OverlayState.None -> {
                    onClearAnimeStack()
                }
                else -> {
                    showPlayer = false
                    currentVideoUrl = null
                    extensionOkHttpClient = null
                    extensionVideoHeaders = emptyMap()
                    extensionHosters = null
                    extensionServers = emptyList()
                    cachedExtensionNext = null
                    PlayerData.extensionSource = null
                    PlayerData.extensionEpisode = null
                    PlayerData.allHosters = emptyList()
                }
            }
        }
    } else {
        if (showSettings) {
            val settingsInitialGroup = pendingSettingsGroup
            Dialog(
                onDismissRequest = { showSettings = false; pendingSettingsGroup = null },
                properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SettingsScreen(
                        viewModel = viewModel,
                        autoSkipOpening = autoSkipOpening,
                        autoSkipEnding = autoSkipEnding,
                        autoPlayNextEpisode = autoPlayNextEpisode,
                        disableMaterialColors = disableMaterialColors,
                        preferredCategory = preferredCategory,
                        initialGroup = settingsInitialGroup
                    )
                }
            }
        }

        Scaffold(
            containerColor = if (isOled) Color.Black else MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                Crossfade(
                    targetState = currentPage,
                    animationSpec = tween(280),
                    label = "screenCrossfade"
                ) { page ->
                    when (page) {
                        0 -> ScheduleScreen(
                            viewModel = viewModel,
                            isOled = isOled,
                            isVisible = true,
                            preventAutoSync = preventScheduleSync,
                            hideAdultContent = hideAdultContent,
                            preferEnglishTitles = preferEnglishTitles,
                            isLoggedIn = isLoggedIn,
                            onPlayEpisode = onPlayEpisode,
                            onAnimeDialogOpen = { isOpen -> scheduleDialogOpen = isOpen },
                            onCharacterClick = { characterId ->
                                overlayState = OverlayState.CharacterDialog(characterId = characterId, animeId = 0)
                            },
                            onStaffClick = { staffId ->
                                overlayState = OverlayState.StaffDialog(staffId = staffId, animeId = 0)
                            },
                            onViewAllCast = { animeId, animeTitle ->
                                overlayState = OverlayState.AllCastDialog(animeId = animeId, animeTitle = animeTitle)
                            },
                            onViewAllStaff = { animeId, animeTitle ->
                                overlayState = OverlayState.AllStaffDialog(animeId = animeId, animeTitle = animeTitle)
                            },
                            onViewAllRelations = { animeId, animeTitle ->
                                overlayState = OverlayState.AllRelationsDialog(animeId = animeId, animeTitle = animeTitle)
                            },
                            onNoExtension = {
                                showSettings = true
                                pendingSettingsGroup = if (extUiState.extensions.isEmpty()) "extensions" else "stream"
                            }
                        )
                        1 -> ExploreScreen(
                            viewModel = viewModel,
                            isLoggedIn = isLoggedIn,
                            isOled = isOled,
                            showStatusColors = showStatusColors,
                            showAnimeCardButtons = showAnimeCardButtons,
                            preferEnglishTitles = preferEnglishTitles,
                            hideAdultContent = hideAdultContent,
                            favoriteIds = if (viewModel.loginProvider.collectAsState().value == LoginProvider.MAL) malFavorites.map { it.id }.toSet() else aniListFavoriteIds,
                            onPlayEpisode = onPlayEpisode,
                            currentlyWatching = currentlyWatching,
                            planningToWatch = planningToWatch,
                            completed = completed,
                            onHold = onHold,
                            dropped = dropped,
                            isVisible = true,
                            onClearAnimeStack = onClearAnimeStack,
                            onCharacterClick = { characterId ->
                                overlayState = OverlayState.CharacterDialog(characterId = characterId, animeId = 0)
                            },
                            onStaffClick = { staffId ->
                                overlayState = OverlayState.StaffDialog(staffId = staffId, animeId = 0)
                            },
                            onViewAllCast = { animeId, animeTitle ->
                                overlayState = OverlayState.AllCastDialog(animeId = animeId, animeTitle = animeTitle)
                            },
                            onViewAllStaff = { animeId, animeTitle ->
                                overlayState = OverlayState.AllStaffDialog(animeId = animeId, animeTitle = animeTitle)
                            },
                            onViewAllRelations = { animeId, animeTitle ->
                                overlayState = OverlayState.AllRelationsDialog(animeId = animeId, animeTitle = animeTitle)
                            },
                            onSearchClick = { showSearchScreen = true },
                            onNoExtension = {
                                showSettings = true
                                pendingSettingsGroup = if (extUiState.extensions.isEmpty()) "extensions" else "stream"
                            }
                        )
                        2 -> HomeScreen(
                            viewModel = viewModel,
                            isLoggedIn = isLoggedIn,
                            isOled = isOled,
                            showStatusColors = showStatusColors,
                            simplifyEpisodeMenu = simplifyEpisodeMenu,
                            preferEnglishTitles = preferEnglishTitles,
                            onOverlayOpenChange = { overlayOpen = it },
                            onNavigateToSettings = {
                                showSettings = true
                            },
                            onNoExtension = {
                                showSettings = true
                                pendingSettingsGroup = if (extUiState.extensions.isEmpty()) "extensions" else "stream"
                            },
                            favoriteIds = if (viewModel.loginProvider.collectAsState().value == LoginProvider.MAL) malFavorites.map { it.id }.toSet() else aniListFavoriteIds,
                            onPlayEpisode = onPlayEpisode,
                            onLoginClick = { viewModel.loginWithAniList() },
                            onShowAnimeDialog = onShowAnimeDialog,
                            onShowDetailedAnimeFromMal = onShowDetailedAnimeFromMal,
                            onShowDetailedAnimeFromAniList = { aniListId ->
                                scope.launch {
                                    val detailedData = viewModel.fetchDetailedAnimeData(aniListId)
                                    if (detailedData != null) {
                                        val newAnime = ExploreAnime(
                                            id = detailedData.id,
                                            title = detailedData.title,
                                            titleEnglish = detailedData.titleEnglish,
                                            cover = detailedData.cover,
                                            banner = detailedData.banner,
                                            episodes = detailedData.episodes,
                                            latestEpisode = detailedData.latestEpisode,
                                            averageScore = detailedData.averageScore,
                                            genres = detailedData.genres,
                                            year = detailedData.year,
                                            format = detailedData.format
                                        )
                                        overlayState = OverlayState.ExploreAnimeDialog(anime = newAnime, firstAnime = newAnime, isFirstOpen = false)
                                    } else {
                                        Toast.makeText(context, "Anime not found", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onCharacterClick = { characterId ->
                                overlayState = OverlayState.CharacterDialog(characterId = characterId, animeId = 0)
                            },
                            onStaffClick = { staffId ->
                                overlayState = OverlayState.StaffDialog(staffId = staffId, animeId = 0)
                            },
                            onViewAllCast = { animeId, animeTitle ->
                                overlayState = OverlayState.AllCastDialog(animeId = animeId, animeTitle = animeTitle)
                            },
                            onViewAllStaff = { animeId, animeTitle ->
                                overlayState = OverlayState.AllStaffDialog(animeId = animeId, animeTitle = animeTitle)
                            },
                            onViewAllRelations = { animeId, animeTitle ->
                                overlayState = OverlayState.AllRelationsDialog(animeId = animeId, animeTitle = animeTitle)
                            },
                            onNavigateToSearch = { showSearchScreen = true },
                            playbackPositions = playbackPositions,
                            playbackDurations = playbackDurations
                        )
                        3 -> DownloadsScreen(
                            viewModel = viewModel,
                            downloadManager = viewModel.episodeDownloadManager,
                            isOled = isOled,
                            onNavbarHidden = viewModel::setHideNavbar,
                        )
                    }
                }

                AnimatedVisibility(
                    visible = showSearchScreen,
                    enter = fadeIn(animationSpec = tween(300)),
                    exit = fadeOut(animationSpec = tween(250))
                ) {
                    SearchScreen(
                        viewModel = viewModel,
                        isOled = isOled,
                        isLoggedIn = isLoggedIn,
                        preferEnglishTitles = preferEnglishTitles,
                        hideAdultContent = hideAdultContent,
                        currentlyWatching = currentlyWatching,
                        planningToWatch = planningToWatch,
                        completed = completed,
                        onHold = onHold,
                        dropped = dropped,
                        localAnimeStatus = viewModel.localAnimeStatus.collectAsState().value,
                        favoriteIds = if (viewModel.loginProvider.collectAsState().value == LoginProvider.MAL) malFavorites.map { it.id }.toSet() else aniListFavoriteIds,
                        onClose = { showSearchScreen = false },
                        onPlayEpisode = onPlayEpisode,
                        onCharacterClick = { characterId ->
                            overlayState = OverlayState.CharacterDialog(characterId = characterId, animeId = 0)
                        },
                        onStaffClick = { staffId ->
                                overlayState = OverlayState.StaffDialog(staffId = staffId, animeId = 0)
                            },
                            onViewAllCast = { animeId, animeTitle ->
                                overlayState = OverlayState.AllCastDialog(animeId = animeId, animeTitle = animeTitle)
                            },
                            onViewAllStaff = { animeId, animeTitle ->
                                overlayState = OverlayState.AllStaffDialog(animeId = animeId, animeTitle = animeTitle)
                            },
                            onViewAllRelations = { animeId, animeTitle ->
                                overlayState = OverlayState.AllRelationsDialog(animeId = animeId, animeTitle = animeTitle)
                            },
                            onNoExtension = {
                                showSettings = true
                                pendingSettingsGroup = if (extUiState.extensions.isEmpty()) "extensions" else "stream"
                            }
                        )
                    }

                if (isLoadingStream) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.7f))
                            .pointerInput(Unit) { },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Loading stream...", color = Color.White)
                            if (currentServerAttempt != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (currentServerAttemptIsFallback) "Trying $currentServerAttempt (fallback)..." else "Trying $currentServerAttempt...",
                                    color = Color.Yellow,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            OutlinedButton(
                                onClick = {
                                    loadingJob?.cancel()
                                    isLoadingStream = false
                                    loadingJob = null
                                }
                            ) {
                                Text("Cancel")
                            }
                        }
                    }
                }

                if (showStatusListScreen) {
                    StatusListScreen(
                        title = statusListTitle,
                        icon = statusListIcon ?: Icons.Default.PlayArrow,
                        animeList = statusListAnime,
                        listType = statusListType,
                        showStatusColors = showStatusColors,
                        preferEnglishTitles = preferEnglishTitles,
                        onAnimeClick = { anime, bounds ->
                            viewModel.setHomeAnimeCardBounds(anime.id, anime.cover, bounds?.bounds)
                            selectedAnimeState = anime
                            showDetailedAnimeScreen = true
                        },
                        onPlayClick = { anime ->
                            val nextEp = anime.progress + 1
                            val released = anime.latestEpisode ?: anime.totalEpisodes
                            if (anime.latestEpisode != null && nextEp > released) {
                                Toast.makeText(context, "Episode not aired yet", Toast.LENGTH_SHORT).show()
                            } else {
                                onPlayEpisode(anime, nextEp, null)
                            }
                        },
                        onInfoClick = { anime, bounds ->
                            viewModel.setHomeAnimeCardBounds(anime.id, anime.cover, bounds?.bounds)
                            selectedAnimeState = anime
                            showDetailedAnimeScreen = true
                        },
                        onStatusClick = { anime ->
                            viewModel.setExploreAnimeCardBounds(anime.id, anime.cover, null)
                            selectedAnimeState = anime
                            showDetailedAnimeScreen = true
                        },
                        onBackClick = { showStatusListScreen = false },
                        onDismiss = {
                            viewModel.setHideNavbar(false)
                            showStatusListScreen = false
                        }
                    )
                }

                val surfaceColor = if (isOled) Color.Black else MaterialTheme.colorScheme.surface
                val onSurfaceColor = if (isOled) Color.White else MaterialTheme.colorScheme.onSurface
                val primaryContainerColor = MaterialTheme.colorScheme.primaryContainer

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        .offset(y = (-16).dp)
                ) {
                    if (!hideNavbar && !isLoadingStream && !showSearchScreen) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(bottom = 4.dp, start = 48.dp, end = 48.dp),
                            shape = MaterialTheme.shapes.extraLarge,
                            color = surfaceColor.copy(alpha = 0.95f),
                            tonalElevation = 4.dp,
                            shadowElevation = 8.dp,
                            border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(width = 1.dp)
                        ) {
                            val selectedIndex = currentPage

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val items = listOf("Schedule", "Explore", "Home", "Downloads")
                                val icons = listOf(Icons.Default.CalendarMonth, Icons.Default.Explore, Icons.Default.Home, Icons.Default.FileDownload)

                            items.forEachIndexed { index, item ->
                                val isSelected = index == selectedIndex

                                Box(
                                    modifier = Modifier
                                        .weight(if (isSelected) 0.67f else 0.25f)
                                        .animateContentSize(
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                stiffness = Spring.StiffnessLow
                                            )
                                        )
                                        .height(56.dp)
                                        .pointerInput(Unit) {
                                            awaitPointerEventScope {
                                                while (true) {
                                                    val event = awaitPointerEvent()
                                                    if (event.changes.any { it.pressed }) {
                                                        scope.launch {
                                                            currentPage = index
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {

                                    if (isSelected) {
                                        val pillColor = if (disableMaterialColors) {
                                            Color.White.copy(alpha = 0.2f)
                                        } else {
                                            primaryContainerColor
                                        }
                                        val pillTextColor = if (disableMaterialColors) {
                                            Color.White
                                        } else {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        }

                                        Surface(
                                            shape = MaterialTheme.shapes.extraLarge,
                                            color = pillColor,
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .padding(vertical = 5.dp)
                                                .fillMaxWidth(0.95f)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                Icon(
                                                    icons[index],
                                                    contentDescription = item,
                                                    tint = pillTextColor,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    item,
                                                    color = pillTextColor,
                                                    style = MaterialTheme.typography.labelMedium
                                                )
                                            }
                                        }
                                    } else {
                                        Icon(
                                            icons[index],
                                            contentDescription = item,
                                            tint = if (isOled) Color.White.copy(alpha = 0.6f) else onSurfaceColor.copy(alpha = 0.6f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    }
                }

streamError?.let { error ->
                    LaunchedEffect(error) {
                        delay(3500.milliseconds)
                        streamError = null
                    }

                    AlertDialog(
                        onDismissRequest = { streamError = null },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(28.dp)
                            )
                        },
                        title = { 
                            Text(
                                text = "Stream Error",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        text = { 
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 3,
                                modifier = Modifier.width(250.dp)
                            )
                        },
                        confirmButton = { 
                            TextButton(onClick = { streamError = null }) {
                                Text("OK", fontWeight = FontWeight.Bold)
                            }
},
                        dismissButton = null
                    )
                }

                // Update check on startup
                val updateViewModel: UpdateViewModel = viewModel()
                val pendingUpdateState by viewModel.pendingUpdateRelease.collectAsState()
                var showUpdateDialog by remember { mutableStateOf(false) }

                LaunchedEffect(pendingUpdateState) {
                    if (pendingUpdateState != null) {
                        showUpdateDialog = true
                    }
                }

                val pendingUpdate = pendingUpdateState
                if (showUpdateDialog && pendingUpdate != null) {
                    AlertDialog(
                        onDismissRequest = { showUpdateDialog = false },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        },
                        title = {
                            Text(
                                text = "Update Available",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        text = {
                            Column {
                                Text(
                                    text = "Version ${pendingUpdate.tagName.removePrefix("v")} is now available.",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Would you like to download and install it?",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showUpdateDialog = false
                                    updateViewModel.setReleaseAndDownload(pendingUpdate)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Update")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showUpdateDialog = false }) {
                                Text("Later")
                            }
                        }
                    )
                }
            }
        }
    }
}


