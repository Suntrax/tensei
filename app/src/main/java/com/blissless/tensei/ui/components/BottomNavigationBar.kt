package com.blissless.tensei.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Bottom pill-style navigation bar with four tabs (Schedule, Explore, Home, Downloads).
 *
 * Extracted from MainActivity.kt's MainScreen composable to reduce file size.
 * The selected tab animates to a wider pill with a label; the others collapse
 * to icon-only.
 *
 * @param selectedIndex     Currently selected tab index (0..3)
 * @param isOled            Whether OLED-black mode is enabled (affects colors)
 * @param disableMaterialColors Whether to override Material colors with white-on-black
 * @param hideNavbar        When true, the navbar is not rendered
 * @param isLoadingStream   When true, the navbar is hidden (player loading state)
 * @param showSearchScreen  When true, the navbar is hidden (search overlay open)
 * @param onSelect          Callback invoked with the new tab index when a tab is pressed
 * @param scope             CoroutineScope used for the pointer-input handler
 */
@Composable
fun BottomNavigationBar(
    selectedIndex: Int,
    isOled: Boolean,
    disableMaterialColors: Boolean,
    hideNavbar: Boolean,
    isLoadingStream: Boolean,
    showSearchScreen: Boolean,
    onSelect: (Int) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
) {
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
                                                    onSelect(index)
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
                                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(4.dp))
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
}
