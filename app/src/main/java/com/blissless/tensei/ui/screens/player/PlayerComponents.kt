package com.blissless.tensei.ui.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Reusable UI components for the Player screen.
 *
 * Extracted from PlayerScreen.kt to reduce file size.
 */

/**
 * Stream error overlay shown when playback fails.
 *
 * Displays the error message with an optional "Try Next Server" button
 * that cycles through available servers.
 *
 * @param errorMessage     The error message to display
 * @param showTryNextServer Whether to show the "Try Next Server" button
 * @param onTryNextServer  Called when the user taps "Try Next Server"
 */
@Composable
internal fun StreamErrorOverlay(
    errorMessage: String,
    showTryNextServer: Boolean,
    onTryNextServer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Refresh,
                null,
                tint = Color(0xFFFFA726),
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("Stream Error", color = Color.White, style = MaterialTheme.typography.titleMedium)
            Text(errorMessage, color = Color.Gray, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))

            Spacer(modifier = Modifier.height(12.dp))

            if (showTryNextServer) {
                Button(
                    onClick = onTryNextServer,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Try Next Server")
                }
            }
        }
    }
}

/**
 * Loading indicator shown when the stream is loading or changing servers.
 *
 * @param modifier Modifier for positioning (typically align to center)
 */
@Composable
internal fun PlayerLoadingIndicator(
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.CircularProgressIndicator(
        modifier = modifier,
        color = Color.White
    )
}

/**
 * Volume overlay indicator shown when the user adjusts volume via swipe.
 *
 * Displays a vertical progress bar with a volume icon and percentage.
 *
 * @param visible            Whether the overlay is shown
 * @param volume             Volume level (0.0 to 1.0)
 * @param disableMaterialColors Whether to use white instead of Material primary
 */
@Composable
internal fun VolumeOverlay(
    visible: Boolean,
    volume: Float,
    disableMaterialColors: Boolean,
    modifier: Modifier = Modifier,
) {
    val accentColor = if (disableMaterialColors) Color.White else MaterialTheme.colorScheme.primary
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(200)) + slideInVertically { it / 4 },
        exit = fadeOut(animationSpec = tween(300)),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .padding(start = 16.dp)
                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = "Volume",
                tint = accentColor,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = "${(volume * 100).toInt()}%",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium
            )
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(80.dp)
                    .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(2.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(volume)
                        .align(Alignment.BottomCenter)
                        .background(accentColor, RoundedCornerShape(2.dp))
                )
            }
        }
    }
}

/**
 * Brightness overlay indicator shown when the user adjusts brightness via swipe.
 *
 * Displays a vertical progress bar with a brightness icon and percentage.
 *
 * @param visible            Whether the overlay is shown
 * @param brightness         Brightness level (0.01 to 1.0)
 * @param disableMaterialColors Whether to use white instead of amber
 */
@Composable
internal fun BrightnessOverlay(
    visible: Boolean,
    brightness: Float,
    disableMaterialColors: Boolean,
    modifier: Modifier = Modifier,
) {
    val accentColor = if (disableMaterialColors) Color.White else Color(0xFFFFD54F)
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(200)) + slideInVertically { it / 4 },
        exit = fadeOut(animationSpec = tween(300)),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .padding(end = 16.dp)
                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.BrightnessHigh,
                contentDescription = "Brightness",
                tint = accentColor,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = "${(brightness * 100).toInt()}%",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium
            )
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(80.dp)
                    .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(2.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(brightness)
                        .align(Alignment.BottomCenter)
                        .background(accentColor, RoundedCornerShape(2.dp))
                )
            }
        }
    }
}

/**
 * Skip intro/outro buttons shown during playback.
 *
 * Displays floating buttons on the right side of the player when
 * skip timestamps are available. The "Skip Ending" button becomes
 * "Next Episode" when there's a next episode and credits are at the end.
 *
 * @param showSkipOpening Whether to show the skip opening button
 * @param showSkipEnding  Whether to show the skip ending button
 * @param isLatestEpisode Whether this is the latest aired episode
 * @param creditsAtEnd    Whether credits are at the end of the video
 * @param isChangingServer Whether the server is currently changing
 * @param onSkipOpening   Called when skip opening is tapped
 * @param onSkipEnding    Called when skip ending is tapped
 * @param modifier        Modifier for positioning
 */
@androidx.compose.runtime.Composable
internal fun SkipButtonsOverlay(
    showSkipOpening: Boolean,
    showSkipEnding: Boolean,
    isLatestEpisode: Boolean,
    creditsAtEnd: Boolean,
    isChangingServer: Boolean,
    onSkipOpening: () -> Unit,
    onSkipEnding: () -> Unit,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = showSkipOpening || showSkipEnding,
        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(initialScale = 0.8f),
        exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(targetScale = 0.8f),
        modifier = modifier
    ) {
        androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)) {
            if (showSkipOpening) {
                SkipIconButton(
                    icon = Icons.Default.FastForward,
                    label = "Skip\nOpening",
                    backgroundColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f),
                    iconTint = androidx.compose.ui.graphics.Color.White,
                    onClick = onSkipOpening
                )
            }
            if (showSkipEnding) {
                SkipIconButton(
                    icon = androidx.compose.material.icons.Icons.Default.SkipNext,
                    label = if (isLatestEpisode || !creditsAtEnd) "Skip\nEnding" else "Next\nEpisode",
                    backgroundColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f),
                    iconTint = androidx.compose.ui.graphics.Color.White,
                    onClick = onSkipEnding
                )
            }
        }
    }
}

/**
 * Autoplay toggle and fullscreen button row.
 *
 * Displays a compact row with an autoplay switch and a fullscreen
 * toggle icon. Both are clickable and share a dark rounded background.
 *
 * @param autoPlayNextEpisode Current autoplay state
 * @param isFullscreen        Current fullscreen state
 * @param onAutoPlayChange    Called when autoplay toggle is clicked
 * @param onFullscreenToggle  Called when fullscreen icon is clicked
 */
@Composable
internal fun AutoplayFullscreenRow(
    autoPlayNextEpisode: Boolean,
    isFullscreen: Boolean,
    onAutoPlayChange: (Boolean) -> Unit,
    onFullscreenToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.5f), shape = RoundedCornerShape(14.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            onClick = { onAutoPlayChange(!autoPlayNextEpisode) },
            color = Color.Transparent
        ) {
            Row(
                modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Autoplay",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall
                )
                Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                    Switch(
                        checked = autoPlayNextEpisode,
                        onCheckedChange = onAutoPlayChange,
                        modifier = Modifier.scale(0.5f),
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = Color.White,
                            checkedThumbColor = Color.Black,
                            uncheckedTrackColor = Color.White.copy(alpha = 0.3f),
                            uncheckedThumbColor = Color.White
                        )
                    )
                }
            }
        }
        Surface(
            onClick = onFullscreenToggle,
            color = Color.Transparent
        ) {
            Row(
                modifier = Modifier.padding(start = 6.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    contentDescription = if (isFullscreen) "Exit fullscreen" else "Enter fullscreen",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Playback speed selector button with dropdown menu.
 *
 * Displays the current speed (e.g. "1x") with a speed icon. Tapping
 * opens a dropdown with speed options (0.5x to 2x).
 *
 * @param currentSpeed  Current playback speed
 * @param showMenu      Whether the dropdown menu is expanded
 * @param onShowMenuChange Called to show/hide the menu
 * @param onSpeedChange Called when a speed is selected
 */
@Composable
internal fun PlaybackSpeedSelector(
    currentSpeed: Float,
    showMenu: Boolean,
    onShowMenuChange: (Boolean) -> Unit,
    onSpeedChange: (Float) -> Unit,
) {
    val speedOptions = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
    Box {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color.Black.copy(alpha = 0.5f),
            onClick = { onShowMenuChange(true) }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = "Playback speed",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "${currentSpeed}x",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White
                )
            }
        }

        androidx.compose.material3.DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { onShowMenuChange(false) }
        ) {
            speedOptions.forEach { speed ->
                androidx.compose.material3.DropdownMenuItem(
                    text = {
                        Text(
                            text = "${speed}x",
                            color = if (currentSpeed == speed) MaterialTheme.colorScheme.primary else Color.White
                        )
                    },
                    onClick = {
                        onSpeedChange(speed)
                        onShowMenuChange(false)
                    },
                    leadingIcon = if (currentSpeed == speed) {
                        { Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) }
                    } else null
                )
            }
        }
    }
}

/**
 * Aspect ratio / resize mode toggle button.
 *
 * Cycles through resize modes (Fit, Stretch, 16:9) on each tap.
 *
 * @param onClick Called when the button is tapped
 */
@Composable
internal fun ResizeButton(
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.Black.copy(alpha = 0.5f),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.AspectRatio,
                "Change aspect ratio",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Player settings button with swipe gesture toggle dropdown.
 *
 * Shows a gear icon that opens a dropdown with toggles for:
 * - Swipe for Volume (left or right side based on swap)
 * - Swipe for Brightness (opposite side)
 * - Swap Sides
 *
 * @param showMenu         Whether the dropdown is expanded
 * @param onShowMenuChange Called to show/hide the menu
 * @param swipeVolume      Whether volume swipe is enabled
 * @param swipeBrightness  Whether brightness swipe is enabled
 * @param swipeSwap        Whether sides are swapped
 * @param onSwipeVolumeChange Called when volume toggle changes
 * @param onSwipeBrightnessChange Called when brightness toggle changes
 * @param onSwipeSwapChange Called when swap toggle changes
 */
@Composable
internal fun PlayerSettingsButton(
    showMenu: Boolean,
    onShowMenuChange: (Boolean) -> Unit,
    swipeVolume: Boolean,
    swipeBrightness: Boolean,
    swipeSwap: Boolean,
    onSwipeVolumeChange: (Boolean) -> Unit,
    onSwipeBrightnessChange: (Boolean) -> Unit,
    onSwipeSwapChange: (Boolean) -> Unit,
) {
    Box {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color.Black.copy(alpha = 0.5f),
            onClick = { onShowMenuChange(true) }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Settings,
                    "Player Settings",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        androidx.compose.material3.DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { onShowMenuChange(false) },
            modifier = Modifier.background(Color(0xFF1A1A1A)).width(220.dp)
        ) {
            androidx.compose.material3.DropdownMenuItem(
                text = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Swipe for Volume (${if (swipeSwap) "Right" else "Left"})", color = Color.White)
                        Switch(
                            checked = swipeVolume,
                            onCheckedChange = onSwipeVolumeChange,
                            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
                        )
                    }
                },
                onClick = { onSwipeVolumeChange(!swipeVolume) }
            )
            androidx.compose.material3.DropdownMenuItem(
                text = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Swipe for Brightness (${if (swipeSwap) "Left" else "Right"})", color = Color.White)
                        Switch(
                            checked = swipeBrightness,
                            onCheckedChange = onSwipeBrightnessChange,
                            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
                        )
                    }
                },
                onClick = { onSwipeBrightnessChange(!swipeBrightness) }
            )
            androidx.compose.material3.DropdownMenuItem(
                text = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Swap Sides", color = Color.White)
                        Switch(
                            checked = swipeSwap,
                            onCheckedChange = onSwipeSwapChange,
                            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
                        )
                    }
                },
                onClick = { onSwipeSwapChange(!swipeSwap) }
            )
        }
    }
}

/**
 * Skip indicator overlay shown when the user double-taps to seek.
 *
 * Shows a rewind or forward icon with the accumulated skip time text.
 *
 * @param visible    Whether the indicator is shown
 * @param isForward  Whether this is a forward skip (vs rewind)
 * @param text       The accumulated skip time text (e.g. "+10s")
 * @param modifier   Modifier for positioning
 */
@Composable
internal fun SkipIndicatorOverlay(
    visible: Boolean,
    isForward: Boolean,
    text: String,
    modifier: Modifier = Modifier,
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(initialScale = 0.8f),
        exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(targetScale = 0.8f),
        modifier = modifier
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier.size(56.dp).background(Color.Black.copy(alpha = 0.6f), androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isForward) Icons.Default.FastForward else Icons.Default.FastRewind,
                    if (isForward) "Forward" else "Rewind",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(text, color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}
