package com.blissless.tensei.ui.screens.settings

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileDownload
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
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
import com.blissless.tensei.ui.theme.ThemeMode
import com.blissless.tensei.api.myanimelist.LoginProvider
import com.blissless.tensei.extensions.ExtensionsScreen
import com.blissless.tensei.extensions.ExtensionsViewModel
import com.blissless.tensei.update.UpdateViewModel
import kotlin.math.round

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    isLoggedIn: Boolean,
    showStatusColors: Boolean = true,
    autoSkipOpening: Boolean = false,
    autoSkipEnding: Boolean = false,
    autoPlayNextEpisode: Boolean = true,
    disableMaterialColors: Boolean = false,
    preferredCategory: String = "sub",
    preferredScraper: String = "Animekai",
    onNavigateBack: () -> Unit = {},
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

    val groups = remember {
        listOf(
            SettingsGroup("account", "Account", "Login and manage your anime list", Icons.Default.Person),
            SettingsGroup("appearance", "Appearance", "Theme, colors, and display options", Icons.Default.Palette),
            SettingsGroup("general", "General", "Startup screen and sync settings", Icons.Default.Settings),
            SettingsGroup("downloads", "Downloads", "Sub/dub, subtitles, and download preferences", Icons.Default.Download),
            SettingsGroup("stream", "Stream Settings", "Audio preferences and buffering", Icons.Default.PlayArrow),
            SettingsGroup("player", "Player Settings", "Playback controls and skipping", Icons.Default.Subscriptions),
            SettingsGroup("cache", "Cache Management", "Storage and data cleanup", Icons.Default.Storage),
            SettingsGroup("extensions", "Extensions", "Manage source extensions", Icons.Default.Extension),
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
                onGroupClick = { selectedGroup = it }
            )
        } else {
            BackHandler { selectedGroup = null }
            when (targetGroup) {
                "account" -> AccountSettingsPage(viewModel = viewModel, onBack = { selectedGroup = null })
                "appearance" -> AppearanceSettingsPage(viewModel = viewModel, disableMaterialColors = disableMaterialColors, onBack = { selectedGroup = null })
                "general" -> GeneralSettingsPage(viewModel = viewModel, onBack = { selectedGroup = null })
                "downloads" -> DownloadsSettingsPage(viewModel = viewModel, onBack = { selectedGroup = null })
                "stream" -> StreamSettingsPage(viewModel = viewModel, disableMaterialColors = disableMaterialColors, preferredCategory = preferredCategory, onNavigateToExtensions = { selectedGroup = "extensions" }, onBack = { selectedGroup = null })
                "player" -> PlayerSettingsPage(viewModel = viewModel, autoSkipOpening = autoSkipOpening, autoSkipEnding = autoSkipEnding, autoPlayNextEpisode = autoPlayNextEpisode, onBack = { selectedGroup = null })
                "cache" -> CacheSettingsPage(viewModel = viewModel, context = LocalContext.current, onBack = { selectedGroup = null })
                "extensions" -> ExtensionsSettingsPage(viewModel = viewModel, onBack = { selectedGroup = null })
                "about" -> AboutSettingsPage(viewModel = viewModel, onBack = { selectedGroup = null })
            }
        }
    }
}

private data class SettingsGroup(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector
)

// ─── Landing Page ───────────────────────────────────────────────────────

@Composable
private fun SettingsLandingPage(
    groups: List<SettingsGroup>,
    onGroupClick: (String) -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp)
            .padding(top = 36.dp, bottom = 100.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(bottom = 20.dp)
        ) {
            AsyncImage(
                model = R.mipmap.ic_launcher_round,
                contentDescription = "App",
                modifier = Modifier.size(48.dp).clip(CircleShape)
            )
            Column {
                Text(
                    "Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "Customize your experience",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                groups.forEachIndexed { index, group ->
                    SettingsListItem(group = group, onClick = { onGroupClick(group.id) })
                    if (index < groups.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 72.dp, end = 20.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                            thickness = 0.5.dp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsListItem(
    group: SettingsGroup,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = group.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                group.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                group.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
            modifier = Modifier.size(20.dp)
        )
    }
}

// ─── Scaffold ───────────────────────────────────────────────────────────

@Composable
private fun SettingsPageScaffold(
    title: String,
    onBack: () -> Unit,
    scrollable: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, top = 12.dp, bottom = 4.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            actions()
        }

        if (scrollable) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                content = content
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(horizontal = 20.dp),
                content = content
            )
        }
    }
}

// ─── Reusable Components ────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
        modifier = Modifier.padding(start = 4.dp)
    )
}

@Composable
private fun SettingsCard(
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content
        )
    }
}

@Composable
private fun SettingsToggle(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            )
        )
    }
}

@Composable
private fun SettingsSliderRow(
    title: String,
    description: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    onValueChange: (Float) -> Unit,
    minLabel: String,
    maxLabel: String,
    leadingIcon: ImageVector? = null
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                valueLabel,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            )
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(minLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            Text(maxLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun SettingsChoiceChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = {
            Text(
                label,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        shape = RoundedCornerShape(10.dp),
        enabled = enabled
    )
}

@Composable
private fun SettingsRadioItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

// ─── Account ────────────────────────────────────────────────────────────

@Composable
private fun AccountSettingsPage(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    var showLogoutConfirmation by remember { mutableStateOf(false) }
    val loginProvider by viewModel.loginProvider.collectAsState(initial = LoginProvider.NONE)

    SettingsPageScaffold(title = "Account", onBack = onBack) {
        if (loginProvider != LoginProvider.NONE) {
            val userName by viewModel.userName.collectAsState()
            val userAvatar by viewModel.userAvatar.collectAsState()
            val providerName = when (loginProvider) {
                LoginProvider.ANILIST -> "AniList"
                LoginProvider.MAL -> "MyAnimeList"
                LoginProvider.NONE -> ""
            }

            SectionHeader("SIGNED IN")
            SettingsCard {
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (userAvatar != null) {
                        AsyncImage(
                            model = userAvatar,
                            contentDescription = "User Avatar",
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
                        Text("via $providerName", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { showLogoutConfirmation = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.9f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Log Out", color = Color.White)
            }
        } else {
            SectionHeader("SIGN IN")
            SettingsCard {
                Spacer(modifier = Modifier.height(4.dp))
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
                            .data("https://anilist.co/img/icons/favicon-32x32.png")
                            .crossfade(true).build(),
                        contentDescription = "AniList",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Login with AniList")
                }
                Spacer(modifier = Modifier.height(8.dp))
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
                            .data("https://cdn.myanimelist.net/images/favicon.ico")
                            .crossfade(true).build(),
                        contentDescription = "MyAnimeList",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Login with MyAnimeList")
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }

    if (showLogoutConfirmation) {
        val providerName = when (loginProvider) {
            LoginProvider.ANILIST -> "AniList"
            LoginProvider.MAL -> "MyAnimeList"
            LoginProvider.NONE -> ""
        }
        AlertDialog(
            onDismissRequest = { showLogoutConfirmation = false },
            title = { Text("Logout") },
            text = { Text("Are you sure you want to log out from $providerName?") },
            confirmButton = {
                Button(
                    onClick = { viewModel.logout(); showLogoutConfirmation = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Log Out") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirmation = false }) { Text("Cancel") }
            }
        )
    }
}

// ─── Appearance ─────────────────────────────────────────────────────────

@Composable
private fun AppearanceSettingsPage(
    viewModel: MainViewModel,
    disableMaterialColors: Boolean,
    onBack: () -> Unit
) {
    val showStatusColorsState by viewModel.showStatusColors.collectAsState(initial = true)
    val simplifyEpisodeMenuState by viewModel.simplifyEpisodeMenu.collectAsState(initial = false)
    val showAnimeCardButtons by viewModel.showAnimeCardButtons.collectAsState(initial = true)
    val preferEnglishTitles by viewModel.preferEnglishTitles.collectAsState(initial = true)

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

        SectionHeader("DISPLAY")
        SettingsCard {
            SettingsToggle(
                title = "Status Color Indicators",
                description = "Show colored status bars on anime cards",
                checked = showStatusColorsState,
                onCheckedChange = { viewModel.setShowStatusColors(it) }
            )
            SettingsToggle(
                title = "Show Card Buttons",
                description = "Bookmark and play buttons on anime cards in Explore",
                checked = showAnimeCardButtons,
                onCheckedChange = { viewModel.setShowAnimeCardButtons(it) }
            )
            SettingsToggle(
                title = "English Titles",
                description = "Show English titles instead of Romaji",
                checked = preferEnglishTitles,
                onCheckedChange = { viewModel.setPreferEnglishTitles(it) }
            )
        }

        SectionHeader("EPISODES")
        SettingsCard {
            SettingsToggle(
                title = "Simple Episode Menu",
                description = "Use compact episode grid instead of detailed cards",
                checked = simplifyEpisodeMenuState,
                onCheckedChange = { viewModel.setSimplifyEpisodeMenu(it) }
            )
        }
    }
}

// ─── General ────────────────────────────────────────────────────────────

@Composable
private fun GeneralSettingsPage(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val startupScreenState by viewModel.startupScreen.collectAsState()
    val preventScheduleSync by viewModel.preventScheduleSync.collectAsState()
    val hideAdultContentState by viewModel.hideAdultContent.collectAsState(initial = false)

    SettingsPageScaffold(title = "General", onBack = onBack) {
        SectionHeader("LAUNCH")
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
                title = "Explore",
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
                description = "Your anime lists"
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = 54.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f),
                thickness = 0.5.dp
            )
            SettingsRadioItem(
                selected = startupScreenState == 3,
                onClick = { viewModel.setStartupScreen(3) },
                icon = Icons.Default.FileDownload,
                title = "Downloads",
                description = "Downloaded episodes"
            )
        }

        SectionHeader("SYNC")
        SettingsCard {
            SettingsToggle(
                title = "Auto Sync Schedule",
                description = "Automatically sync airing schedule when opening",
                checked = !preventScheduleSync,
                onCheckedChange = { viewModel.setPreventScheduleSync(!it) }
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
        }
    }
}

// ─── Stream Settings ────────────────────────────────────────────────────

@Composable
private fun StreamSettingsPage(
    viewModel: MainViewModel,
    disableMaterialColors: Boolean,
    preferredCategory: String,
    onNavigateToExtensions: () -> Unit,
    onBack: () -> Unit
) {
    val bufferAheadSeconds by viewModel.bufferAheadSeconds.collectAsState(initial = 30)
    val bufferSizeMb by viewModel.bufferSizeMb.collectAsState(initial = 100)
    val showBufferIndicator by viewModel.showBufferIndicator.collectAsState(initial = true)
    val defaultExtPackage by viewModel.defaultExtensionPackage.collectAsState()
    val defaultSubtitleLang by viewModel.defaultSubtitleLang.collectAsState()
    val extViewModel: ExtensionsViewModel = viewModel()
    val extUiState by extViewModel.uiState.collectAsState()
    var showExtPicker by remember { mutableStateOf(false) }
    var showSubtitleLangPicker by remember { mutableStateOf(false) }
    val subtitleLanguages = listOf("English", "Arabic", "French", "German", "Italian", "Portuguese", "Russian", "Spanish", "Japanese", "Chinese", "Korean")

    SettingsPageScaffold(title = "Stream Settings", onBack = onBack) {
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
                    if (defaultExtPackage.isEmpty() && extUiState.extensions.isEmpty()) {
                        onNavigateToExtensions()
                    } else {
                        showExtPicker = true
                    }
                },
                icon = Icons.Default.Extension,
                title = "Default Extension",
                subtitle = if (defaultExtPackage.isNotEmpty())
                    extUiState.extensions.find { it.packageName == defaultExtPackage }?.name ?: defaultExtPackage
                else "None"
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
                    extUiState.extensions.forEach { ext ->
                        val isSelected = ext.packageName == defaultExtPackage
                        TextButton(
                            onClick = { viewModel.setDefaultExtensionPackage(ext.packageName); showExtPicker = false },
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

@Composable
private fun ClickableSettingsRow(
    onClick: () -> Unit,
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
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
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
            modifier = Modifier.size(20.dp)
        )
    }
}

// ─── Downloads ──────────────────────────────────────────────────────────

@Composable
private fun DownloadsSettingsPage(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val downloadPreferredCategory by viewModel.downloadPreferredCategory.collectAsState(initial = "same_as_stream")
    val downloadSubtitleLang by viewModel.downloadSubtitleLang.collectAsState(initial = "same_as_stream")
    val streamPreferredCategory by viewModel.preferredCategory.collectAsState(initial = "sub")
    val streamSubtitleLang by viewModel.defaultSubtitleLang.collectAsState()
    var isIgnoringBattery by remember { mutableStateOf(checkBatteryOpt(context)) }
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isIgnoringBattery = checkBatteryOpt(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val subtitleLanguages = listOf("English", "Arabic", "French", "German", "Italian", "Portuguese", "Russian", "Spanish", "Japanese", "Chinese", "Korean")
    var showSubtitleLangPicker by remember { mutableStateOf(false) }

    SettingsPageScaffold(title = "Downloads", onBack = onBack) {
        SectionHeader("AUDIO")
        SettingsCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SettingsChoiceChip(label = "Same as stream", isSelected = downloadPreferredCategory == "same_as_stream", onClick = { viewModel.setDownloadPreferredCategory("same_as_stream") })
                SettingsChoiceChip(label = "SUB", isSelected = downloadPreferredCategory == "sub", onClick = { viewModel.setDownloadPreferredCategory("sub") })
                SettingsChoiceChip(label = "DUB", isSelected = downloadPreferredCategory == "dub", onClick = { viewModel.setDownloadPreferredCategory("dub") })
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Preferred Audio Category",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "Download subbed or dubbed audio when available",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }

        SectionHeader("SUBTITLES")
        SettingsCard {
            ClickableSettingsRow(
                onClick = { showSubtitleLangPicker = true },
                icon = Icons.Default.Subtitles,
                title = "Preferred Subtitle Language",
                subtitle = if (downloadSubtitleLang == "same_as_stream") "Same as stream ($streamSubtitleLang)" else downloadSubtitleLang
            )
        }

        SectionHeader("BACKGROUND DOWNLOADS")
        SettingsCard {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isIgnoringBattery) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            else MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Storage,
                        contentDescription = null,
                        tint = if (isIgnoringBattery) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Battery Optimization", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Text(
                        if (isIgnoringBattery) "Disabled - downloads will work reliably" else "Download reliability may be reduced",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                TextButton(onClick = {
                    if (isIgnoringBattery) {
                        Toast.makeText(context, "Battery optimization is already disabled", Toast.LENGTH_SHORT).show()
                    } else {
                        try {
                            val intent = android.content.Intent(
                                android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                android.net.Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    }
                }) {
                    Text(
                        if (isIgnoringBattery) "Disabled" else "Fix",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    if (showSubtitleLangPicker) {
        AlertDialog(
            onDismissRequest = { showSubtitleLangPicker = false },
            title = { Text("Preferred Subtitle Language") },
            text = {
                Column {
                    TextButton(onClick = { viewModel.setDownloadSubtitleLang("same_as_stream"); showSubtitleLangPicker = false }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Same as stream ($streamSubtitleLang)",
                            color = if (downloadSubtitleLang == "same_as_stream") MaterialTheme.colorScheme.primary else Color.Unspecified,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    subtitleLanguages.forEach { lang ->
                        TextButton(onClick = { viewModel.setDownloadSubtitleLang(lang); showSubtitleLangPicker = false }, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                lang,
                                color = if (downloadSubtitleLang == lang) MaterialTheme.colorScheme.primary else Color.Unspecified,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSubtitleLangPicker = false }) { Text("Cancel") } }
        )
    }
}

// ─── Player Settings ────────────────────────────────────────────────────

@Composable
private fun PlayerSettingsPage(
    viewModel: MainViewModel,
    autoSkipOpening: Boolean,
    autoSkipEnding: Boolean,
    autoPlayNextEpisode: Boolean,
    onBack: () -> Unit
) {
    val trackingPercentage by viewModel.trackingPercentage.collectAsState(initial = 85)
    val forwardSkipSeconds by viewModel.forwardSkipSeconds.collectAsState(initial = 10)
    val backwardSkipSeconds by viewModel.backwardSkipSeconds.collectAsState(initial = 10)
    val swipeVolume by viewModel.swipeVolume.collectAsState(initial = false)
    val swipeBrightness by viewModel.swipeBrightness.collectAsState(initial = false)
    val swipeSwap by viewModel.swipeSwap.collectAsState(initial = false)

    SettingsPageScaffold(title = "Player Settings", onBack = onBack) {
        SectionHeader("TRACKING")
        SettingsCard {
            SettingsSliderRow(
                title = "Episode Tracking",
                description = "Auto-update progress when you've watched this percentage",
                value = trackingPercentage.toFloat(),
                valueRange = 50f..100f,
                valueLabel = "${trackingPercentage}%",
                onValueChange = { viewModel.setTrackingPercentage((round(it / 5f) * 5f).toInt()) },
                minLabel = "50%",
                maxLabel = "100%"
            )
        }

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

// ─── Cache Management ───────────────────────────────────────────────────

@Composable
private fun CacheSettingsPage(
    viewModel: MainViewModel,
    context: Context,
    onBack: () -> Unit
) {
    var videoCacheSize by remember { mutableLongStateOf(0L) }
    var downloadCacheSize by remember { mutableLongStateOf(0L) }
    var showClearCacheConfirmation by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        videoCacheSize = viewModel.getVideoCacheSize(context)
        downloadCacheSize = viewModel.getDownloadCacheSize()
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
        SettingsCard {
            CacheRow(
                icon = Icons.Default.Download,
                title = "Download Cache",
                size = formatFileSize(downloadCacheSize),
                onClear = { showClearCacheConfirmation = "download" }
            )
        }
    }

    if (showClearCacheConfirmation != null) {
        val isVideo = showClearCacheConfirmation == "video"
        AlertDialog(
            onDismissRequest = { showClearCacheConfirmation = null },
            title = { Text(if (isVideo) "Clear Video Cache" else "Clear Download Cache") },
            text = {
                Text(
                    if (isVideo)
                        "This will clear all video cache and temporary data. Your playback positions will be preserved."
                    else
                        "This will delete all downloaded episodes. They will need to be re-downloaded."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isVideo) {
                            viewModel.clearNonEssentialCaches(context)
                            videoCacheSize = 0L
                        } else {
                            viewModel.clearDownloadCache()
                            downloadCacheSize = 0L
                        }
                        showClearCacheConfirmation = null
                        Toast.makeText(context, "Cache cleared", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { showClearCacheConfirmation = null }) { Text("Cancel") } }
        )
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

// ─── Extensions ─────────────────────────────────────────────────────────

@Composable
private fun ExtensionsSettingsPage(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val extViewModel: ExtensionsViewModel = viewModel()

    SettingsPageScaffold(title = "Extensions", onBack = onBack, scrollable = false, actions = {
        IconButton(onClick = { extViewModel.loadExtensions(true) }) {
            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
        }
    }) {
        ExtensionsScreen(viewModel = extViewModel)
    }
}

// ─── About ──────────────────────────────────────────────────────────────

@Composable
private fun AboutSettingsPage(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val updateViewModel: UpdateViewModel = viewModel()
    val updateState by updateViewModel.uiState.collectAsState()
    val checkOnStart by viewModel.checkUpdatesOnStart.collectAsState()
    val packageInfo = remember {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0)
    }
    val currentVersion = packageInfo.versionName ?: ""

    SettingsPageScaffold(title = "About", onBack = onBack) {
        SectionHeader("APP")
        SettingsCard {
            Row(
                modifier = Modifier.padding(vertical = 4.dp),
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
                        model = R.mipmap.ic_launcher_round,
                        contentDescription = "App",
                        modifier = Modifier.size(36.dp).clip(CircleShape)
                    )
                }
                Column {
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

        SectionHeader("LINKS")
        SettingsCard {
            val githubUrl = "https://github.com/Suntrax/tensei"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(githubUrl))
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

// ─── Utility Functions ──────────────────────────────────────────────────

private fun compareVersions(v1: String, v2: String): Int {
    val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
    val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
    val maxLen = maxOf(parts1.size, parts2.size)
    for (i in 0 until maxLen) {
        val p1 = parts1.getOrElse(i) { 0 }
        val p2 = parts2.getOrElse(i) { 0 }
        if (p1 != p2) return p1 - p2
    }
    return 0
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}

private fun checkBatteryOpt(context: android.content.Context): Boolean {
    val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
    return pm?.isIgnoringBatteryOptimizations(context.packageName) == true
}
