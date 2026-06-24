package com.blissless.tensei.ui.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blissless.tensei.data.models.SubtitleSettings

private val COLOR_PRESETS = listOf(
    "White" to 0xFFFFFFFFL,
    "Yellow" to 0xFFFFFF00L,
    "Cyan" to 0xFF00FFFFL,
    "Lime" to 0xFF00FF00L,
    "Magenta" to 0xFFFF00FFL,
    "Red" to 0xFFFF0000L,
    "Blue" to 0xFF0000FFL,
    "Black" to 0xFF000000L,
)

private val BG_COLOR_PRESETS = listOf(
    "None" to 0x00000000L,
    "Black 25%" to 0x40000000L,
    "Black 50%" to 0x80000000L,
    "Black 75%" to 0xC0000000L,
    "White 25%" to 0x40FFFFFFL,
    "White 50%" to 0x80FFFFFFL,
)

@Composable
fun SubtitleSettingsDialog(
    currentSettings: SubtitleSettings,
    profiles: List<SubtitleSettings>,
    activeProfileIndex: Int,
    onSettingsChange: (SubtitleSettings) -> Unit,
    onProfileSelect: (Int) -> Unit,
    onResetProfile: (Int) -> Unit,
    onRenameProfile: (Int, String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header
            Text(
                "Subtitle Settings",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Profile tabs
            ProfileSelector(
                profiles = profiles,
                activeIndex = activeProfileIndex,
                onSelect = onProfileSelect,
                onRename = onRenameProfile,
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Scrollable settings
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(480.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    SettingsSection("Text Size") {
                        SizeSlider(
                            value = currentSettings.fontSize,
                            range = 10f..48f,
                            step = 1,
                            label = "${currentSettings.fontSize.toInt()}sp",
                            onValueChange = { onSettingsChange(currentSettings.copy(fontSize = it)) }
                        )
                    }
                }

                item {
                    SettingsSection("Text Color") {
                        ColorPicker(
                            selectedColor = currentSettings.fontColor,
                            presets = COLOR_PRESETS,
                            onSelect = { onSettingsChange(currentSettings.copy(fontColor = it)) }
                        )
                    }
                }

                item {
                    SettingsSection("Background Color") {
                        ColorPicker(
                            selectedColor = currentSettings.backgroundColor,
                            presets = BG_COLOR_PRESETS,
                            onSelect = { onSettingsChange(currentSettings.copy(backgroundColor = it)) }
                        )
                    }
                }

                item {
                    SettingsSection("Shadow") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Enable Shadow", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = currentSettings.enableShadow,
                                onCheckedChange = { onSettingsChange(currentSettings.copy(enableShadow = it)) },
                                colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }

                item {
                    SettingsSection("Outline") {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Enable Outline", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                                Switch(
                                    checked = currentSettings.enableOutline,
                                    onCheckedChange = { onSettingsChange(currentSettings.copy(enableOutline = it)) },
                                    colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
                                )
                            }
                            AnimatedVisibility(visible = currentSettings.enableOutline) {
                                Column {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "Outline Width: ${currentSettings.outlineWidth.toInt()}",
                                        color = Color.White.copy(alpha = 0.7f),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Slider(
                                        value = currentSettings.outlineWidth,
                                        onValueChange = { onSettingsChange(currentSettings.copy(outlineWidth = it)) },
                                        valueRange = 1f..6f,
                                        steps = 4,
                                        colors = SliderDefaults.colors(
                                            thumbColor = Color.White,
                                            activeTrackColor = Color.White
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "Outline Color",
                                        color = Color.White.copy(alpha = 0.7f),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    ColorPicker(
                                        selectedColor = currentSettings.outlineColor,
                                        presets = COLOR_PRESETS,
                                        onSelect = {
                                            onSettingsChange(currentSettings.copy(outlineColor = it))
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    SettingsSection("Subtitle Delay") {
                        Column {
                            val delaySec = currentSettings.delayMs / 1000f
                            Text(
                                text = if (delaySec >= 0) "+${String.format("%.1f", delaySec)}s (late)" else "${String.format("%.1f", delaySec)}s (early)",
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Slider(
                                value = delaySec,
                                onValueChange = { onSettingsChange(currentSettings.copy(delayMs = (it * 1000).toInt())) },
                                valueRange = -10f..10f,
                                steps = 39,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = Color.White
                                )
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("-10s", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                                Text("0", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                                Text("+10s", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                item {
                    SettingsSection("Vertical Position") {
                        Column {
                            val vPos = currentSettings.verticalPosition
                            val vLabel = when {
                                vPos < 0.33f -> "Top"
                                vPos < 0.66f -> "Middle"
                                else -> "Bottom"
                            }
                            Text(
                                "$vLabel (${(vPos * 100).toInt()}%)",
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Slider(
                                value = vPos,
                                onValueChange = { onSettingsChange(currentSettings.copy(verticalPosition = it)) },
                                valueRange = 0.05f..0.95f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = Color.White
                                )
                            )
                        }
                    }
                }

                item {
                    SettingsSection("Horizontal Position") {
                        Column {
                            val hPos = currentSettings.horizontalPosition
                            val hLabel = when {
                                hPos < 0.33f -> "Left"
                                hPos < 0.66f -> "Center"
                                else -> "Right"
                            }
                            Text(
                                "$hLabel (${(hPos * 100).toInt()}%)",
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Slider(
                                value = hPos,
                                onValueChange = { onSettingsChange(currentSettings.copy(horizontalPosition = it)) },
                                valueRange = 0.05f..0.95f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = Color.White
                                )
                            )
                        }
                    }
                }

                item {
                    SettingsSection("Max Width") {
                        Column {
                            Text(
                                "${(currentSettings.maxWidthRatio * 100).toInt()}%",
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Slider(
                                value = currentSettings.maxWidthRatio,
                                onValueChange = { onSettingsChange(currentSettings.copy(maxWidthRatio = it)) },
                                valueRange = 0.3f..1f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = Color.White
                                )
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { onResetProfile(activeProfileIndex) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF333333),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reset Profile to Default")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun ProfileSelector(
    profiles: List<SubtitleSettings>,
    activeIndex: Int,
    onSelect: (Int) -> Unit,
    onRename: (Int, String) -> Unit,
) {
    Column {
        Text(
            "Profiles",
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.labelMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            profiles.forEachIndexed { index, profile ->
                Box {
                    FilterChip(
                        selected = index == activeIndex,
                        onClick = { onSelect(index) },
                        label = {
                            Text(
                                profile.profileName,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFF333333),
                            labelColor = Color.White,
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column {
        Text(
            title,
            color = Color.White.copy(alpha = 0.9f),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        content()
        HorizontalDivider(
            modifier = Modifier.padding(top = 8.dp),
            color = Color.White.copy(alpha = 0.1f)
        )
    }
}

@Composable
private fun SizeSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Int,
    label: String,
    onValueChange: (Float) -> Unit,
) {
    Column {
        Text(label, color = Color.White, style = MaterialTheme.typography.bodySmall)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = step,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White
            )
        )
    }
}

@Composable
private fun ColorPicker(
    selectedColor: Long,
    presets: List<Pair<String, Long>>,
    onSelect: (Long) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        presets.forEach { (name, colorLong) ->
            val color = Color(colorLong)
            val isSelected = selectedColor == colorLong
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable { onSelect(colorLong) }
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(color)
                        .then(
                            if (isSelected) Modifier.border(2.dp, Color.White, CircleShape)
                            else Modifier.border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                        )
                )
                Text(
                    name,
                    color = if (isSelected) Color.White else Color.Gray,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1
                )
            }
        }
    }
}
