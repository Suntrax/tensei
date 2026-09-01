package com.blissless.tensei.ui.screens.settings

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.blissless.tensei.MainViewModel
import com.blissless.tensei.R
import com.blissless.tensei.ui.components.appIconDrawable
import com.blissless.tensei.ui.theme.ThemeMode
import com.blissless.tensei.api.myanimelist.LoginProvider
import com.blissless.tensei.extensions.ExtensionsScreen
import com.blissless.tensei.extensions.ExtensionsViewModel
import com.blissless.tensei.update.UpdateViewModel
import kotlin.math.round
import androidx.core.net.toUri
// Extension functions on MainViewModel (defined in com.blissless.tensei.viewmodel)
import com.blissless.tensei.viewmodel.setThemeMode
import com.blissless.tensei.viewmodel.setAppIcon
import com.blissless.tensei.viewmodel.setDisableMaterialColors
import com.blissless.tensei.viewmodel.setShowStatusColors
import com.blissless.tensei.viewmodel.setShowAnimeCardButtons
import com.blissless.tensei.viewmodel.setShowMangaCardButtons
import com.blissless.tensei.viewmodel.setShowMangaStatusColors
import com.blissless.tensei.viewmodel.setMaxPerformance
import com.blissless.tensei.viewmodel.setPreferEnglishTitles
import com.blissless.tensei.viewmodel.setSimplifyEpisodeMenu
import com.blissless.tensei.viewmodel.setStartupScreen
import com.blissless.tensei.viewmodel.setPreventScheduleSync
import com.blissless.tensei.viewmodel.setHideAdultContent
import com.blissless.tensei.viewmodel.setMangaReaderMode
import com.blissless.tensei.viewmodel.setMangaDataSaver
import com.blissless.tensei.viewmodel.setMangaPageIndicator
import com.blissless.tensei.viewmodel.setMangaFullscreen
import com.blissless.tensei.viewmodel.setMangaAutoAdvance
import com.blissless.tensei.viewmodel.setMangaLockRotation
import com.blissless.tensei.viewmodel.setMangaSyncThreshold
import com.blissless.tensei.viewmodel.setStreamMethod
import com.blissless.tensei.viewmodel.setPreferredCategory
import com.blissless.tensei.viewmodel.setBufferAheadSeconds
import com.blissless.tensei.viewmodel.setBufferSizeMb
import com.blissless.tensei.viewmodel.setShowBufferIndicator
import com.blissless.tensei.viewmodel.setDefaultExtensionPackage
import com.blissless.tensei.viewmodel.setDefaultMagnetExtension
import com.blissless.tensei.viewmodel.setDefaultStreamExtension
import com.blissless.tensei.viewmodel.setDefaultSubtitleLang
import com.blissless.tensei.viewmodel.setTrackingPercentage
import com.blissless.tensei.viewmodel.setForwardSkipSeconds
import com.blissless.tensei.viewmodel.setBackwardSkipSeconds
import com.blissless.tensei.viewmodel.setAutoSkipOpening
import com.blissless.tensei.viewmodel.setAutoSkipEnding
import com.blissless.tensei.viewmodel.setAutoPlayNextEpisode
import com.blissless.tensei.viewmodel.setSwipeVolume
import com.blissless.tensei.viewmodel.setSwipeBrightness
import com.blissless.tensei.viewmodel.setSwipeSwap
import com.blissless.tensei.viewmodel.setSupportsPiP
import com.blissless.tensei.viewmodel.setDiscordRichPresence
import com.blissless.tensei.viewmodel.setCheckUpdatesOnStart
import com.blissless.tensei.viewmodel.setAutoSyncCrossProviderStartup
import com.blissless.tensei.viewmodel.setAutoSyncCrossProviderDirection
import com.blissless.tensei.viewmodel.setAutoUpdateExtensions
import com.blissless.tensei.viewmodel.loadAvailableMagnetExtensions
import com.blissless.tensei.viewmodel.loadAvailableStreamExtensions
import com.blissless.tensei.viewmodel.getVideoCacheSize
import com.blissless.tensei.viewmodel.clearNonEssentialCaches
import com.blissless.tensei.viewmodel.discoverExtensions
import com.blissless.tensei.viewmodel.installedExtensions
import com.blissless.tensei.viewmodel.InstalledExtension
import com.blissless.tensei.viewmodel.selectedExtensionAuthority
import com.blissless.tensei.viewmodel.selectExtension
import com.blissless.tensei.viewmodel.showCrossProviderCopyDialog
import com.blissless.tensei.util.ErrorHandler
import com.blissless.tensei.util.toast
import com.blissless.tensei.util.longToast

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    autoSkipOpening: Boolean = false,
    autoSkipEnding: Boolean = false,
    autoPlayNextEpisode: Boolean = true,
    disableMaterialColors: Boolean = false,
    preferredCategory: String = "sub",
    initialGroup: String? = null
) {
    var selectedGroup by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(initialGroup) {
        if (initialGroup != null) {
            selectedGroup = initialGroup
        }
    }

    LaunchedEffect(selectedGroup) {
        viewModel.setHideNavbar(selectedGroup != null)
    }

    val appIcon by viewModel.appIcon.collectAsState()

    val groups = remember {
        listOf(
            SettingsGroup("appearance", "Appearance", "Theme, colors, and display options", Icons.Default.Palette),
            SettingsGroup("account", "Account & Sync", "Login, tracking, and sync settings", Icons.Default.Person),
            SettingsGroup("general", "General", "Startup screen and content preferences", Icons.Default.Settings),
            SettingsGroup("stream", "Stream Settings", "Streaming method, audio, and buffering", Icons.Default.PlayArrow),
            SettingsGroup("player", "Player Settings", "Playback controls and skipping", Icons.Default.Subscriptions),
            SettingsGroup("reader", "Reader Settings", "Manga reading preferences", Icons.Default.Bookmark),
            SettingsGroup("extensions", "Extensions", "Manage source extensions", Icons.Default.Extension),
            SettingsGroup("cache", "Cache Management", "Storage and data cleanup", Icons.Default.Storage),
            SettingsGroup("about", "About", "Version and updates", Icons.Default.Info)
        )
    }

    AnimatedContent(
        targetState = selectedGroup,
        transitionSpec = {
            if (targetState == null) {
                (fadeIn(animationSpec = tween(220)) + slideInHorizontally(animationSpec = tween(220)) { -it / 8 })
                    .togetherWith(fadeOut(animationSpec = tween(220)) + slideOutHorizontally(animationSpec = tween(220)) { it / 8 })
            } else {
                (fadeIn(animationSpec = tween(220)) + slideInHorizontally(animationSpec = tween(220)))
                    .togetherWith(fadeOut(animationSpec = tween(220)) + slideOutHorizontally(animationSpec = tween(220)))
            }
        },
        label = "settingsNavigation"
    ) { targetGroup ->
        if (targetGroup == null) {
            SettingsLandingPage(
                groups = groups,
                appIcon = appIcon,
                onGroupClick = { selectedGroup = it }
            )
        } else {
            BackHandler { selectedGroup = null }
            when (targetGroup) {
                "appearance" -> AppearanceSettingsPage(viewModel = viewModel, disableMaterialColors = disableMaterialColors, onBack = { selectedGroup = null })
                "account" -> AccountSettingsPage(viewModel = viewModel, onBack = { selectedGroup = null })
                "general" -> GeneralSettingsPage(viewModel = viewModel, onBack = { selectedGroup = null })
                "stream" -> StreamSettingsPage(viewModel = viewModel,
                    preferredCategory = preferredCategory, onNavigateToExtensions = { selectedGroup = "extensions" }, onBack = { selectedGroup = null })
                "player" -> PlayerSettingsPage(viewModel = viewModel, autoSkipOpening = autoSkipOpening, autoSkipEnding = autoSkipEnding, autoPlayNextEpisode = autoPlayNextEpisode, onBack = { selectedGroup = null })
                "reader" -> ReaderSettingsPage(viewModel = viewModel, onBack = { selectedGroup = null })
                "extensions" -> ExtensionsSettingsPage(viewModel = viewModel, onBack = { selectedGroup = null })
                "cache" -> CacheSettingsPage(viewModel = viewModel, context = LocalContext.current, onBack = { selectedGroup = null })
                "about" -> AboutSettingsPage(viewModel = viewModel, onBack = { selectedGroup = null })
            }
        }
    }
}



// â”€â”€â”€ Account â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
private fun AccountSettingsPage(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    var showAniListLogoutConfirmation by remember { mutableStateOf(false) }
    var showMalLogoutConfirmation by remember { mutableStateOf(false) }
    val loginProvider by viewModel.loginProvider.collectAsState(initial = LoginProvider.NONE)
    val trackingPercentage by viewModel.trackingPercentage.collectAsState(initial = 85)
    val preventScheduleSync by viewModel.preventScheduleSync.collectAsState()
    val mangaSyncThreshold by viewModel.mangaSyncThreshold.collectAsState()
    val discordRichPresence by viewModel.discordRichPresence.collectAsState(initial = false)
    val autoSyncCrossProviderStartup by viewModel.autoSyncCrossProviderStartup.collectAsState(initial = false)
    val autoSyncCrossProviderDirection by viewModel.autoSyncCrossProviderDirection.collectAsState(initial = true)

    val userName by viewModel.userName.collectAsState()
    val userAvatar by viewModel.userAvatar.collectAsState()
    val malUsername by viewModel.malUsernameFlow.collectAsState()
    val malAvatar by viewModel.malAvatar.collectAsState()

    SettingsPageScaffold(title = "Account & Sync", onBack = onBack) {
        val anilistLogged = loginProvider == LoginProvider.ANILIST || loginProvider == LoginProvider.BOTH
        val malLogged = loginProvider == LoginProvider.MAL || loginProvider == LoginProvider.BOTH

        if (anilistLogged) {
            SectionHeader("ANILIST")
            SettingsCard {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (userAvatar != null) {
                        AsyncImage(
                            model = userAvatar,
                            contentDescription = "AniList Avatar",
                            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                        }
                    }
                    Column {
                        Text(userName ?: "Logged In", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text("via AniList", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { showAniListLogoutConfirmation = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.9f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Log Out of AniList", color = Color.White)
                }
            }

            // When only AniList is logged in, offer adding MAL so the user can reach BOTH.
            if (loginProvider == LoginProvider.ANILIST) {
                Spacer(modifier = Modifier.height(8.dp))
                MalLoginButton(viewModel)
            }
        }

        if (malLogged) {
            Spacer(modifier = Modifier.height(8.dp))
            SectionHeader("MYANIMELIST")
            SettingsCard {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (malAvatar != null) {
                        AsyncImage(
                            model = malAvatar,
                            contentDescription = "MyAnimeList Avatar",
                            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(26.dp))
                        }
                    }
                    Column {
                        Text(malUsername ?: "Logged In", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text("via MyAnimeList", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { showMalLogoutConfirmation = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.9f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Log Out of MyAnimeList", color = Color.White)
                }
            }

            // When only MAL is logged in, offer adding AniList so the user can reach BOTH.
            if (loginProvider == LoginProvider.MAL) {
                Spacer(modifier = Modifier.height(8.dp))
                AniListLoginButton(viewModel)
            }
        }

        if (loginProvider == LoginProvider.BOTH) {
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = { viewModel.showCrossProviderCopyDialog() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                )
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sync Now (AniList to MAL)")
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        "Auto-Sync on Start",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Automatically sync AniList and MyAnimeList when the app starts",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = autoSyncCrossProviderStartup,
                    onCheckedChange = { viewModel.setAutoSyncCrossProviderStartup(it) }
                )
            }
            if (autoSyncCrossProviderStartup) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    "Sync Direction",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { viewModel.setAutoSyncCrossProviderDirection(true) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (autoSyncCrossProviderDirection)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            "AniList to MAL",
                            color = if (autoSyncCrossProviderDirection)
                                MaterialTheme.colorScheme.onPrimary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { viewModel.setAutoSyncCrossProviderDirection(false) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!autoSyncCrossProviderDirection)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            "MAL to AniList",
                            color = if (!autoSyncCrossProviderDirection)
                                MaterialTheme.colorScheme.onPrimary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (loginProvider == LoginProvider.NONE) {
            SectionHeader("SIGN IN")
            SettingsCard {
                Spacer(modifier = Modifier.height(4.dp))
                AniListLoginButton(viewModel)
                Spacer(modifier = Modifier.height(8.dp))
                MalLoginButton(viewModel)
                Spacer(modifier = Modifier.height(4.dp))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        SectionHeader("TRACKING & SYNC")
        SettingsCard {
            SettingsToggle(
                title = "Auto Sync Schedule",
                description = "Automatically sync airing schedule when opening",
                checked = !preventScheduleSync,
                onCheckedChange = { viewModel.setPreventScheduleSync(!it) }
            )
        }
        SettingsCard {
            SettingsSliderRow(
                title = "Episode Tracking",
                description = "Auto-update anime progress when you've watched this percentage",
                value = trackingPercentage.toFloat(),
                valueRange = 50f..100f,
                valueLabel = "${trackingPercentage}%",
                onValueChange = { viewModel.setTrackingPercentage((round(it / 5f) * 5f).toInt()) },
                minLabel = "50%",
                maxLabel = "100%"
            )
        }
        SettingsCard {
            SettingsSliderRow(
                title = "Manga Tracking",
                description = "Auto-update manga progress when you've read this percentage",
                value = mangaSyncThreshold.toFloat(),
                valueRange = 75f..100f,
                valueLabel = "${mangaSyncThreshold}%",
                onValueChange = { viewModel.setMangaSyncThreshold(it.toInt()) },
                minLabel = "75%",
                maxLabel = "100%"
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        SectionHeader("DISCORD")
        SettingsCard {
            SettingsToggle(
                title = "Discord Rich Presence",
                description = "Show what you're watching/reading on Discord\nMay only work with the official Discord app",
                checked = discordRichPresence,
                onCheckedChange = { viewModel.setDiscordRichPresence(it) }
            )
        }
    }

    if (showAniListLogoutConfirmation) {
        AlertDialog(
            onDismissRequest = { showAniListLogoutConfirmation = false },
            title = { Text("Log Out of AniList") },
            text = { Text("Are you sure you want to log out of AniList? Your MyAnimeList session will be kept.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.logoutProvider(LoginProvider.ANILIST); showAniListLogoutConfirmation = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Log Out") }
            },
            dismissButton = {
                TextButton(onClick = { showAniListLogoutConfirmation = false }) { Text("Cancel") }
            }
        )
    }

    if (showMalLogoutConfirmation) {
        AlertDialog(
            onDismissRequest = { showMalLogoutConfirmation = false },
            title = { Text("Log Out of MyAnimeList") },
            text = { Text("Are you sure you want to log out of MyAnimeList? Your AniList session will be kept.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.logoutProvider(LoginProvider.MAL); showMalLogoutConfirmation = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Log Out") }
            },
            dismissButton = {
                TextButton(onClick = { showMalLogoutConfirmation = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun AniListLoginButton(viewModel: MainViewModel) {
    Button(
        onClick = { viewModel.loginWithAniList() },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary
        )
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(com.blissless.tensei.network.Endpoints.AniList.FAVICON)
                .crossfade(true).build(),
            contentDescription = "AniList",
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text("Login with AniList")
    }
}

@Composable
private fun MalLoginButton(viewModel: MainViewModel) {
    Button(
        onClick = { viewModel.loginWithMal() },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
        )
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(com.blissless.tensei.network.Endpoints.Mal.FAVICON)
                .crossfade(true).build(),
            contentDescription = "MyAnimeList",
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text("Login with MyAnimeList")
    }
}

// â”€â”€â”€ Appearance â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
private fun AppearanceSettingsPage(
    viewModel: MainViewModel,
    disableMaterialColors: Boolean,
    onBack: () -> Unit
) {
    val showStatusColorsState by viewModel.showStatusColors.collectAsState(initial = true)
    val simplifyEpisodeMenuState by viewModel.simplifyEpisodeMenu.collectAsState(initial = false)
    val showAnimeCardButtons by viewModel.showAnimeCardButtons.collectAsState(initial = true)
    val showMangaCardButtons by viewModel.showMangaCardButtons.collectAsState(initial = true)
    val showMangaStatusColors by viewModel.showMangaStatusColors.collectAsState(initial = true)

    SettingsPageScaffold(title = "Appearance", onBack = onBack) {
        val currentThemeMode by viewModel.themeMode.collectAsState()

        SectionHeader("THEME MODE")
        SettingsCard {
            SettingsRadioItem(
                selected = currentThemeMode == ThemeMode.SYSTEM.value,
                onClick = { viewModel.setThemeMode(ThemeMode.SYSTEM.value) },
                icon = Icons.Default.Settings,
                title = "System Theme",
                description = "Follow your device theme setting"
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = 54.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f),
                thickness = 0.5.dp
            )
            SettingsRadioItem(
                selected = currentThemeMode == ThemeMode.LIGHT.value,
                onClick = { viewModel.setThemeMode(ThemeMode.LIGHT.value) },
                icon = Icons.Default.LightMode,
                title = "Light",
                description = "Bright and clean appearance"
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = 54.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f),
                thickness = 0.5.dp
            )
            SettingsRadioItem(
                selected = currentThemeMode == ThemeMode.DARK.value,
                onClick = { viewModel.setThemeMode(ThemeMode.DARK.value) },
                icon = Icons.Default.DarkMode,
                title = "Dark",
                description = "Easy on the eyes at night"
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = 54.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f),
                thickness = 0.5.dp
            )
            SettingsRadioItem(
                selected = currentThemeMode == ThemeMode.OLED.value,
                onClick = { viewModel.setThemeMode(ThemeMode.OLED.value) },
                icon = Icons.Default.Storage,
                title = "OLED",
                description = "Pure black for AMOLED screens"
            )
        }

        SectionHeader("COLORS")
        SettingsCard {
            SettingsToggle(
                title = "Monochrome Theme",
                description = "Disable Material You colors for neutral appearance",
                checked = disableMaterialColors,
                onCheckedChange = { viewModel.setDisableMaterialColors(it) }
            )
        }

        val appIcon by viewModel.appIcon.collectAsState()

        SectionHeader("APP ICON")
        SettingsCard {
            AppIconRadioItem(
                selected = appIcon == "default",
                onClick = { viewModel.setAppIcon("default") },
                previewRes = R.drawable.ic_icon_default,
                title = "Default",
                description = "Standard app icon"
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = 54.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f),
                thickness = 0.5.dp
            )
            AppIconRadioItem(
                selected = appIcon == "wob",
                onClick = { viewModel.setAppIcon("wob") },
                previewRes = R.drawable.ic_icon_wob,
                title = "White on Black",
                description = "Light logo on a dark background"
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = 54.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f),
                thickness = 0.5.dp
            )
            AppIconRadioItem(
                selected = appIcon == "bow",
                onClick = { viewModel.setAppIcon("bow") },
                previewRes = R.drawable.ic_icon_bow,
                title = "Black on White",
                description = "Dark logo on a light background"
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = 54.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f),
                thickness = 0.5.dp
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    "The app will restart to apply the new icon",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }

        SectionHeader("DISPLAY")
        SettingsCard {
            SettingsToggle(
                title = "Anime Status Color Indicators",
                description = "Show colored status bars on anime cards",
                checked = showStatusColorsState,
                onCheckedChange = { viewModel.setShowStatusColors(it) }
            )
            SettingsToggle(
                title = "Anime Card Buttons",
                description = "Bookmark and play buttons on anime cards in Explore",
                checked = showAnimeCardButtons,
                onCheckedChange = { viewModel.setShowAnimeCardButtons(it) }
            )
            SettingsToggle(
                title = "Manga Status Color Indicators",
                description = "Show colored status bars on manga cards",
                checked = showMangaStatusColors,
                onCheckedChange = { viewModel.setShowMangaStatusColors(it) }
            )
            SettingsToggle(
                title = "Manga Card Buttons",
                description = "Bookmark and read buttons on manga cards in Explore",
                checked = showMangaCardButtons,
                onCheckedChange = { viewModel.setShowMangaCardButtons(it) }
            )
        }

        SectionHeader("EPISODES")
        SettingsCard {
            SettingsToggle(
                title = "Simple Episode Menu",
                description = "Use compact episode grid instead of detailed cards (also affects player compact view)",
                checked = simplifyEpisodeMenuState,
                onCheckedChange = { viewModel.setSimplifyEpisodeMenu(it) }
            )
        }
    }
}

// â”€â”€â”€ General â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
private fun GeneralSettingsPage(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val startupScreenState by viewModel.startupScreen.collectAsState()
    val hideAdultContentState by viewModel.hideAdultContent.collectAsState(initial = false)
    val preferEnglishTitles by viewModel.preferEnglishTitles.collectAsState(initial = true)

    SettingsPageScaffold(title = "General", onBack = onBack) {
        SectionHeader("LAUNCH")
        Text(
            text = "Choose which screen appears when you open the app",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 8.dp)
        )
        SettingsCard {
            SettingsRadioItem(
                selected = startupScreenState == 0,
                onClick = { viewModel.setStartupScreen(0) },
                icon = Icons.Default.CalendarMonth,
                title = "Schedule",
                description = "Airing schedule view"
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = 54.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f),
                thickness = 0.5.dp
            )
            SettingsRadioItem(
                selected = startupScreenState == 1,
                onClick = { viewModel.setStartupScreen(1) },
                icon = Icons.Default.Explore,
                title = "Anime",
                description = "Browse and discover anime"
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = 54.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f),
                thickness = 0.5.dp
            )
            SettingsRadioItem(
                selected = startupScreenState == 2,
                onClick = { viewModel.setStartupScreen(2) },
                icon = Icons.Default.Home,
                title = "Home",
                description = "Your anime and manga lists"
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = 54.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f),
                thickness = 0.5.dp
            )
            SettingsRadioItem(
                selected = startupScreenState == 3,
                onClick = { viewModel.setStartupScreen(3) },
                icon = Icons.Default.MenuBook,
                title = "Manga",
                description = "Browse and discover manga"
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = 54.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f),
                thickness = 0.5.dp
            )
            SettingsRadioItem(
                selected = startupScreenState == 4,
                onClick = { viewModel.setStartupScreen(4) },
                icon = Icons.Default.Search,
                title = "Search",
                description = "Search anime and manga"
            )
        }

        SectionHeader("CONTENT")
        SettingsCard {
            SettingsToggle(
                title = "Hide Adult Content",
                description = "Exclude 18+ anime from showing up",
                checked = hideAdultContentState,
                onCheckedChange = { viewModel.setHideAdultContent(it) }
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = 54.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f),
                thickness = 0.5.dp
            )
            SettingsToggle(
                title = "English Titles",
                description = "Show English titles instead of Romaji",
                checked = preferEnglishTitles,
                onCheckedChange = { viewModel.setPreferEnglishTitles(it) }
            )
        }

        SectionHeader("PERFORMANCE")
        SettingsCard {
            val maxPerformanceState by viewModel.maxPerformance.collectAsState(initial = false)
            SettingsToggle(
                title = "Max Performance",
                description = "Force the app to run at the highest refresh rate available. Only works on Android 11+",
                checked = maxPerformanceState,
                onCheckedChange = { viewModel.setMaxPerformance(it) }
            )
        }
    }
}

// â”€â”€â”€ Stream Settings â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
private fun StreamSettingsPage(
    viewModel: MainViewModel,
    preferredCategory: String,
    onNavigateToExtensions: () -> Unit,
    onBack: () -> Unit
) {
    val bufferAheadSeconds by viewModel.bufferAheadSeconds.collectAsState(initial = 30)
    val bufferSizeMb by viewModel.bufferSizeMb.collectAsState(initial = 100)
    val showBufferIndicator by viewModel.showBufferIndicator.collectAsState(initial = true)
    val defaultExtPackage by viewModel.defaultExtensionPackage.collectAsState()
    val defaultSubtitleLang by viewModel.defaultSubtitleLang.collectAsState()
    val streamMethod by viewModel.streamMethod.collectAsState()
    val extViewModel: ExtensionsViewModel = viewModel()
    val extUiState by extViewModel.uiState.collectAsState()
    val magnetExtensions by viewModel.availableMagnetExtensions.collectAsState()
    val streamExtensions by viewModel.availableStreamExtensions.collectAsState()
    val defaultMagnetExtension by viewModel.defaultMagnetExtension.collectAsState()
    val defaultStreamExtension by viewModel.defaultStreamExtension.collectAsState()
    var showExtPicker by remember { mutableStateOf(false) }
    var showSubtitleLangPicker by remember { mutableStateOf(false) }
    val subtitleLanguages = listOf("English", "Arabic", "French", "German", "Italian", "Portuguese", "Russian", "Spanish", "Japanese", "Chinese", "Korean")
    LaunchedEffect(Unit) {
        viewModel.loadAvailableMagnetExtensions()
        viewModel.loadAvailableStreamExtensions()
    }

    SettingsPageScaffold(title = "Stream Settings", onBack = onBack) {
        SectionHeader("STREAM METHOD")
        SettingsCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SettingsChoiceChip(label = "Standard", isSelected = streamMethod == "direct", onClick = { viewModel.setStreamMethod("direct") })
                SettingsChoiceChip(label = "Tensei", isSelected = streamMethod == "magnet", onClick = { viewModel.setStreamMethod("magnet") })
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Stream via HTTP extensions or Tensei extensions",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }

        SectionHeader("AUDIO")
        SettingsCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SettingsChoiceChip(label = "SUB", isSelected = preferredCategory == "sub", onClick = { viewModel.setPreferredCategory("sub") })
                SettingsChoiceChip(label = "DUB", isSelected = preferredCategory == "dub", onClick = { viewModel.setPreferredCategory("dub") })
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Preferred Audio Category",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "Try servers from this category first when playing",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }

        SectionHeader("EXTENSIONS")
        SettingsCard {
            ClickableSettingsRow(
                onClick = {
                    if (streamMethod == "direct") {
                        if (defaultExtPackage.isEmpty() && extUiState.extensions.isEmpty()) {
                            onNavigateToExtensions()
                        } else {
                            showExtPicker = true
                        }
                    } else {
                        showExtPicker = true
                    }
                },
                icon = Icons.Default.Extension,
                title = "Default Extension",
                subtitle = if (streamMethod == "direct") {
                    if (defaultExtPackage.isNotEmpty())
                        extUiState.extensions.find { it.packageName == defaultExtPackage }?.name ?: defaultExtPackage
                    else "None"
                } else {
                    val streamName = defaultStreamExtension?.let { auth -> streamExtensions.find { it.second == auth }?.first }
                    if (streamName != null) streamName
                    else {
                        val name = magnetExtensions.find { it.second == defaultMagnetExtension }?.first
                        name ?: defaultMagnetExtension ?: "None"
                    }
                }
            )
        }
        SettingsCard {
            ClickableSettingsRow(
                onClick = { showSubtitleLangPicker = true },
                icon = Icons.Default.Subtitles,
                title = "Default Subtitle Language",
                subtitle = defaultSubtitleLang
            )
        }

        SectionHeader("BUFFER")
        SettingsCard {
            SettingsSliderRow(
                title = "Buffer Ahead",
                description = "Amount of video to buffer ahead of playback",
                value = bufferAheadSeconds.toFloat(),
                valueRange = 0f..300f,
                valueLabel = "${bufferAheadSeconds}s",
                onValueChange = { viewModel.setBufferAheadSeconds((round(it / 10f) * 10f).toInt()) },
                minLabel = "0s",
                maxLabel = "300s",
                leadingIcon = Icons.Default.PlayArrow
            )
        }
        SettingsCard {
            SettingsSliderRow(
                title = "Max Buffer Size",
                description = "Maximum amount of data to buffer",
                value = bufferSizeMb.toFloat(),
                valueRange = 50f..500f,
                valueLabel = "${bufferSizeMb}MB",
                onValueChange = { viewModel.setBufferSizeMb((round(it / 25f) * 25f).toInt()) },
                minLabel = "50MB",
                maxLabel = "500MB",
                leadingIcon = Icons.Default.Memory
            )
        }
        SettingsCard {
            SettingsToggle(
                title = "Show Buffer Indicator",
                description = "Display buffered amount on the progress bar",
                checked = showBufferIndicator,
                onCheckedChange = { viewModel.setShowBufferIndicator(it) }
            )
        }
    }

    if (showExtPicker) {
        AlertDialog(
            onDismissRequest = { showExtPicker = false },
            title = { Text("Default Extension") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (streamMethod == "direct") {
                        val standardExtensions = extUiState.extensions.filter {
                            !com.blissless.tensei.extensions.ExtensionDetector.isBlisslessStreamExtension(it.packageName) &&
                            !com.blissless.tensei.extensions.ExtensionDetector.isBlisslessTorrentExtension(it.packageName)
                        }
                        if (standardExtensions.isNotEmpty()) {
                            Text(
                                "Standard",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        standardExtensions.forEach { ext ->
                            val isSelected = ext.packageName == defaultExtPackage
                            TextButton(
                                onClick = { viewModel.setDefaultExtensionPackage(ext.packageName); viewModel.setDefaultStreamExtension(null); showExtPicker = false },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = null,
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            ext.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                        Text(
                                            ext.packageName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        if (streamExtensions.isNotEmpty()) {
                            Text(
                                "Stream",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        streamExtensions.forEach { (name, authority) ->
                            val isSelected = authority == defaultStreamExtension
                            TextButton(
                                onClick = { viewModel.setDefaultStreamExtension(authority); viewModel.setDefaultExtensionPackage(""); viewModel.setDefaultMagnetExtension(""); showExtPicker = false },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = null,
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                        Text(
                                            authority,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        }
                        if (magnetExtensions.isNotEmpty()) {
                            if (streamExtensions.isNotEmpty()) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            }
                            Text(
                                "Torrent",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        magnetExtensions.forEach { (name, authority) ->
                            val isSelected = authority == defaultMagnetExtension
                            TextButton(
                                onClick = { viewModel.setDefaultMagnetExtension(authority); viewModel.setDefaultExtensionPackage(""); viewModel.setDefaultStreamExtension(null); showExtPicker = false },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = null,
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                        Text(
                                            authority,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showExtPicker = false }) { Text("Cancel") } }
        )
    }

    if (showSubtitleLangPicker) {
        AlertDialog(
            onDismissRequest = { showSubtitleLangPicker = false },
            title = { Text("Default Subtitle Language") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    subtitleLanguages.forEach { lang ->
                        val isSelected = lang == defaultSubtitleLang
                        TextButton(
                            onClick = { viewModel.setDefaultSubtitleLang(lang); showSubtitleLangPicker = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = null,
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                                Text(
                                    lang,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSubtitleLangPicker = false }) { Text("Cancel") } }
        )
    }
}

// â”€â”€â”€ Player Settings â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
private fun PlayerSettingsPage(
    viewModel: MainViewModel,
    autoSkipOpening: Boolean,
    autoSkipEnding: Boolean,
    autoPlayNextEpisode: Boolean,
    onBack: () -> Unit
) {
    val forwardSkipSeconds by viewModel.forwardSkipSeconds.collectAsState(initial = 10)
    val backwardSkipSeconds by viewModel.backwardSkipSeconds.collectAsState(initial = 10)
    val swipeVolume by viewModel.swipeVolume.collectAsState(initial = false)
    val swipeBrightness by viewModel.swipeBrightness.collectAsState(initial = false)
    val swipeSwap by viewModel.swipeSwap.collectAsState(initial = false)
    val supportsPiP by viewModel.supportsPiP.collectAsState(initial = false)
    val context = LocalContext.current

    SettingsPageScaffold(title = "Player Settings", onBack = onBack) {
        SectionHeader("SKIP CONTROLS")
        SettingsCard {
            SettingsSliderRow(
                title = "Skip Forward",
                description = "Double-tap right side to skip forward",
                value = forwardSkipSeconds.toFloat(),
                valueRange = 5f..30f,
                valueLabel = "${forwardSkipSeconds}s",
                onValueChange = { viewModel.setForwardSkipSeconds((round(it / 5f) * 5f).toInt()) },
                minLabel = "5s",
                maxLabel = "30s",
                leadingIcon = Icons.Default.FastForward
            )
        }
        SettingsCard {
            SettingsSliderRow(
                title = "Skip Backward",
                description = "Double-tap left side to skip backward",
                value = backwardSkipSeconds.toFloat(),
                valueRange = 5f..30f,
                valueLabel = "${backwardSkipSeconds}s",
                onValueChange = { viewModel.setBackwardSkipSeconds((round(it / 5f) * 5f).toInt()) },
                minLabel = "5s",
                maxLabel = "30s",
                leadingIcon = Icons.Default.FastRewind
            )
        }

        SectionHeader("AUTOMATION")
        SettingsCard {
            SettingsToggle(
                title = "Auto Skip Opening",
                description = "Automatically skip anime openings",
                checked = autoSkipOpening,
                onCheckedChange = { viewModel.setAutoSkipOpening(it) }
            )
            SettingsToggle(
                title = "Auto Skip Ending",
                description = "Automatically skip anime endings",
                checked = autoSkipEnding,
                onCheckedChange = { viewModel.setAutoSkipEnding(it) }
            )
            SettingsToggle(
                title = "Auto Play Next Episode",
                description = "Automatically play the next episode when current ends",
                checked = autoPlayNextEpisode,
                onCheckedChange = { viewModel.setAutoPlayNextEpisode(it) }
            )
            SettingsToggle(
                title = "Picture-in-Picture",
                description = "Enter mini player when leaving the app",
                checked = supportsPiP,
                onCheckedChange = { viewModel.setSupportsPiP(it) }
            )
        }

        SectionHeader("SWIPE GESTURES")
        SettingsCard {
            SettingsToggle(
                title = "Swipe for Volume",
                description = "Swipe up/down on the left side to adjust volume",
                checked = swipeVolume,
                onCheckedChange = { viewModel.setSwipeVolume(it) }
            )
            SettingsToggle(
                title = "Swipe for Brightness",
                description = "Swipe up/down on the right side to adjust brightness",
                checked = swipeBrightness,
                onCheckedChange = { viewModel.setSwipeBrightness(it) }
            )
            SettingsToggle(
                title = "Swap Sides",
                description = "Swap the volume and brightness gesture sides",
                checked = swipeSwap,
                onCheckedChange = { viewModel.setSwipeSwap(it) }
            )
        }
    }
}

// â”€â”€â”€ Reader Settings â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
private fun ReaderSettingsPage(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val readerMode by viewModel.mangaReaderMode.collectAsState()
    val showPageIndicator by viewModel.mangaPageIndicator.collectAsState()
    val lockRotation by viewModel.mangaLockRotation.collectAsState()
    val fullscreen by viewModel.mangaFullscreen.collectAsState()
    val autoAdvance by viewModel.mangaAutoAdvance.collectAsState()
    val installedMangaExtensions by viewModel.installedExtensions.collectAsState()
    val selectedMangaExtension by viewModel.selectedExtensionAuthority.collectAsState()
    var showMangaExtPicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.discoverExtensions()
    }

    SettingsPageScaffold(title = "Reader Settings", onBack = onBack) {
        SectionHeader("DEFAULT SOURCE")
        SettingsCard {
            ClickableSettingsRow(
                onClick = { showMangaExtPicker = true },
                icon = Icons.Default.Extension,
                title = "Default Extension",
                subtitle = installedMangaExtensions.find { it.authority == selectedMangaExtension }?.label
                    ?: selectedMangaExtension?.removeSuffix(".provider")
                    ?: "None"
            )
        }

        SectionHeader("READING MODE")
        SettingsCard {
            val modes = listOf(
                Triple("vertical_scroll", "Vertical Scroll", "Webtoon-style continuous scroll") to Icons.Default.ViewAgenda,
                Triple("left_to_right", "Left to Right", "One page per screen, swipe left") to Icons.AutoMirrored.Filled.ArrowForward,
                Triple("right_to_left", "Right to Left", "One page per screen, swipe right (manga)") to Icons.AutoMirrored.Filled.ArrowBack
            )
            modes.forEachIndexed { index, (triple, icon) ->
                val (key, title, description) = triple
                SettingsRadioItem(
                    selected = readerMode == key,
                    onClick = { viewModel.setMangaReaderMode(key) },
                    icon = icon,
                    title = title,
                    description = description
                )
                if (index < modes.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 54.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f),
                        thickness = 0.5.dp
                    )
                }
            }
        }

        SectionHeader("DISPLAY")
        SettingsCard {
            SettingsToggle(
                title = "Page Indicator",
                description = "Show current page number in the bottom-right corner while reading.",
                checked = showPageIndicator,
                onCheckedChange = { viewModel.setMangaPageIndicator(it) }
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = 54.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f),
                thickness = 0.5.dp
            )
            SettingsToggle(
                title = "Lock Rotation",
                description = "Keep the screen in its current orientation while reading.",
                checked = lockRotation,
                onCheckedChange = { viewModel.setMangaLockRotation(it) }
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = 54.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f),
                thickness = 0.5.dp
            )
            SettingsToggle(
                title = "Fullscreen",
                description = "Hide the status bar and navigation buttons while reading.",
                checked = fullscreen,
                onCheckedChange = { viewModel.setMangaFullscreen(it) }
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = 54.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f),
                thickness = 0.5.dp
            )
            SettingsToggle(
                title = "Next Chapter Button",
                description = "Shows a button to jump to the next chapter when you reach the end of the current one.",
                checked = autoAdvance,
                onCheckedChange = { viewModel.setMangaAutoAdvance(it) }
            )
        }
    }

    if (showMangaExtPicker) {
        AlertDialog(
            onDismissRequest = { showMangaExtPicker = false },
            title = { Text("Default Extension") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (installedMangaExtensions.isEmpty()) {
                        Text(
                            text = "No manga extensions installed. Install one from Extensions, then pick it here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    installedMangaExtensions.forEach { ext ->
                        val isSelected = ext.authority == selectedMangaExtension
                        TextButton(
                            onClick = { viewModel.selectExtension(ext.authority); showMangaExtPicker = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = null,
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        ext.label,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                    Text(
                                        ext.packageName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showMangaExtPicker = false }) { Text("Cancel") } }
        )
    }
}

// â”€â”€â”€ Cache Management â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
private fun CacheSettingsPage(
    viewModel: MainViewModel,
    context: Context,
    onBack: () -> Unit
) {
    var videoCacheSize by remember { mutableLongStateOf(0L) }
    var showClearCacheConfirmation by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        videoCacheSize = viewModel.getVideoCacheSize(context)
    }

    SettingsPageScaffold(title = "Cache Management", onBack = onBack) {
        SectionHeader("STORAGE")
        SettingsCard {
            CacheRow(
                icon = Icons.Default.PlayArrow,
                title = "Video Cache",
                size = formatFileSize(videoCacheSize),
                onClear = { showClearCacheConfirmation = "video" }
            )
        }
    }

    if (showClearCacheConfirmation != null) {
        val isVideo = showClearCacheConfirmation == "video"
        if (isVideo) {
            AlertDialog(
                onDismissRequest = { showClearCacheConfirmation = null },
                title = { Text("Clear Video Cache") },
                text = {
                    Text("This will clear all video cache and temporary data. Your playback positions will be preserved.")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.clearNonEssentialCaches(context)
                            videoCacheSize = 0L
                            showClearCacheConfirmation = null
                            context.toast("Cache cleared")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("Clear") }
                },
                dismissButton = { TextButton(onClick = { showClearCacheConfirmation = null }) { Text("Cancel") } }
            )
        }
    }
}

@Composable
private fun CacheRow(
    icon: ImageVector,
    title: String,
    size: String,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(size, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
        }
        Button(
            onClick = onClear,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.9f)
            ),
            shape = RoundedCornerShape(10.dp)
        ) { Text("Clear") }
    }
}

// â”€â”€â”€ Extensions â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
private fun ExtensionsSettingsPage(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val extViewModel: ExtensionsViewModel = viewModel()
    var selectedRepoUrl by remember { mutableStateOf<String?>(null) }

    SettingsPageScaffold(
        title = "Extensions",
        onBack = onBack,
        scrollable = false,
        navigationIcon = if (selectedRepoUrl != null) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
        actions = {
            IconButton(onClick = { extViewModel.loadExtensions(true) }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        }
    ) {
        ExtensionsScreen(
            viewModel = extViewModel,
            selectedRepoUrl = selectedRepoUrl,
            onSelectRepo = { selectedRepoUrl = it }
        )
    }
}

// â”€â”€â”€ About â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
private fun AboutSettingsPage(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val updateViewModel: UpdateViewModel = viewModel()
    val updateState by updateViewModel.uiState.collectAsState()
    val checkOnStart by viewModel.checkUpdatesOnStart.collectAsState()
    val appIcon by viewModel.appIcon.collectAsState()
    val packageInfo = remember {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0)
    }
    val currentVersion = packageInfo.versionName ?: ""

    SettingsPageScaffold(title = "About", onBack = onBack) {
        SectionHeader("APP")
        SettingsCard {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = appIconDrawable(appIcon),
                        contentDescription = "App",
                        modifier = Modifier.size(40.dp).clip(CircleShape)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Tensei", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    @Suppress("DEPRECATION")
                    Text(
                        "v$currentVersion (${packageInfo.versionCode})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }

        SectionHeader("UPDATES")
        SettingsCard {
            val release = updateState.release
            val isChecking = updateState.isChecking
            val isDownloading = updateState.isDownloading
            val error = updateState.error
            val statusText = when {
                isChecking -> "Checking..."
                isDownloading -> "Downloading ${(updateState.downloadProgress * 100).toInt()}%"
                error != null -> error
                release != null -> {
                    val tag = release.tagName.removePrefix("v")
                    if (compareVersions(tag, currentVersion) > 0) "Update available: v$tag"
                    else "Up to date (v$currentVersion)"
                }
                else -> "Tap to check for updates"
            }
            val hasUpdate = release != null && compareVersions(
                release.tagName.removePrefix("v"), currentVersion
            ) > 0

            Spacer(modifier = Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Check for Updates", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Text(statusText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                }
                if (isChecking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (isDownloading) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LinearProgressIndicator(
                            progress = { updateState.downloadProgress },
                            modifier = Modifier.width(80.dp).height(4.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "${(updateState.downloadProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    Button(
                        onClick = {
                            if (hasUpdate) updateViewModel.downloadUpdate()
                            else updateViewModel.checkForUpdates()
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            if (hasUpdate) "Update" else "Check",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
        }

        SectionHeader("AUTOMATION")
        SettingsCard {
            SettingsToggle(
                title = "Check for Updates on Start",
                description = "Automatically check for new versions when opening the app",
                checked = checkOnStart,
                onCheckedChange = { viewModel.setCheckUpdatesOnStart(it) }
            )
        }

        val autoUpdateExts by viewModel.autoUpdateExtensions.collectAsState()
        SettingsCard {
            SettingsToggle(
                title = "Auto-Update Extensions",
                description = "On app start, ask permission then automatically install extension updates",
                checked = autoUpdateExts,
                onCheckedChange = { viewModel.setAutoUpdateExtensions(it) }
            )
        }

        SectionHeader("LINKS")
        SettingsCard {
            val githubUrl = com.blissless.tensei.network.Endpoints.GitHub.TENSEI_REPO
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, githubUrl.toUri())
                            context.startActivity(intent)
                        }
                    )
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("GitHub Repository", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Text(githubUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                }
                Text(
                    "Open",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

