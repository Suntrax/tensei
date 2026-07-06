package com.blissless.tensei.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Setting panels for the Subtitle Settings dialog.
 *
 * Extracted from SubtitleSettingsDialog.kt. Each panel is a self-contained
 * section of settings (templates, text size, outline, shadow, background,
 * font color, advanced). They use the reusable UI components from
 * SubtitleSettingsComponents.kt.
 */

// ===========================================================================
// PANELS
// ===========================================================================

@Composable
internal fun TemplatePanel(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
    ) {
        PanelHeader(title = "Background Templates", onDismiss = onDismiss)
        SectionDivider()
        Spacer(Modifier.height(12.dp))
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TEMPLATES.chunked(4).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    row.forEach { tmpl ->
                        val idx = TEMPLATES.indexOf(tmpl)
                        ThemeColorItem(
                            name = tmpl.name,
                            colors = tmpl.colors,
                            isSelected = idx == selectedIndex,
                            onClick = { onSelect(idx) }
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
internal fun TextSizePanel(
    currentSize: Float,
    onSizeChange: (Float) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
    ) {
        PanelHeader(title = "Text Size", onDismiss = onDismiss, onReset = onReset)
        SectionDivider()
        Spacer(Modifier.height(12.dp))
        SliderRow(
            label = "Font Size",
            value = currentSize,
            onValueChange = onSizeChange,
            valueRange = 10f..48f,
            displayValue = "${currentSize.toInt()} sp"
        )
    }
}

@Composable
internal fun OutlinePanel(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    width: Float,
    onWidthChange: (Float) -> Unit,
    color: Long,
    onColorChange: (Long) -> Unit,
    onResetAll: () -> Unit,
    onResetWidth: () -> Unit,
    onResetColor: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
    ) {
        PanelHeader(title = "Outline", onDismiss = onDismiss, onReset = onResetAll)
        SectionDivider()
        Spacer(Modifier.height(8.dp))
        ToggleRow(label = "Enable Outline", checked = enabled, onCheckedChange = onToggle)
        if (enabled) {
            Spacer(Modifier.height(8.dp))
            SliderRow(
                label = "Width",
                value = width,
                onValueChange = onWidthChange,
                valueRange = 1f..6f,
                displayValue = "${width.toInt()} px"
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Color", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                ResetTextButton(onClick = onResetColor)
            }
            ColorSwatchRow(
                colors = OUTLINE_PRESETS,
                selectedColor = color,
                onColorSelect = onColorChange,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
    }
}

@Composable
internal fun ShadowPanel(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    blur: Float,
    onBlurChange: (Float) -> Unit,
    offsetX: Float,
    onOffsetXChange: (Float) -> Unit,
    offsetY: Float,
    onOffsetYChange: (Float) -> Unit,
    color: Long,
    onColorChange: (Long) -> Unit,
    onResetAll: () -> Unit,
    onResetBlur: () -> Unit,
    onResetOffsetX: () -> Unit,
    onResetOffsetY: () -> Unit,
    onResetColor: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        PanelHeader(title = "Shadow", onDismiss = onDismiss, onReset = onResetAll)
        SectionDivider()
        Spacer(Modifier.height(8.dp))
        ToggleRow(label = "Enable Shadow", checked = enabled, onCheckedChange = onToggle)
        if (enabled) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Blur", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                ResetTextButton(onClick = onResetBlur)
            }
            SliderRow(
                label = "",
                value = blur,
                onValueChange = onBlurChange,
                valueRange = 1f..10f,
                displayValue = "${blur.toInt()} px"
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Offset X", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                ResetTextButton(onClick = onResetOffsetX)
            }
            SliderRow(
                label = "",
                value = offsetX,
                onValueChange = onOffsetXChange,
                valueRange = -10f..10f,
                displayValue = "${offsetX.toInt()} px"
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Offset Y", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                ResetTextButton(onClick = onResetOffsetY)
            }
            SliderRow(
                label = "",
                value = offsetY,
                onValueChange = onOffsetYChange,
                valueRange = -10f..10f,
                displayValue = "${offsetY.toInt()} px"
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Color", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                ResetTextButton(onClick = onResetColor)
            }
            ColorSwatchRow(
                colors = SHADOW_PRESETS,
                selectedColor = color,
                onColorSelect = onColorChange,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
    }
}

@Composable
internal fun BgPanel(
    bgColor: Long,
    onColorChange: (Long) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val bgAlpha = ((bgColor shr 24) and 0xFF).toFloat() / 255f
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
    ) {
        PanelHeader(title = "Subtitle Background", onDismiss = onDismiss, onReset = onReset)
        SectionDivider()
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Presets", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BG_SUB_PRESETS.forEach { colorLong ->
                val color = Color(colorLong)
                val isSelected = (bgColor and 0x00FFFFFFL) == (colorLong and 0x00FFFFFFL)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(color)
                        .then(
                            if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            else Modifier.border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                        )
                        .clickable { onColorChange(colorLong) },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = if (Color(colorLong) == Color.Black) Color.White else Color.Black,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        SliderRow(
            label = "Opacity",
            value = bgAlpha,
            onValueChange = { newAlpha ->
                val alphaBits = (newAlpha * 255).toInt().coerceIn(0, 255)
                val newColor = (bgColor and 0x00FFFFFFL) or (alphaBits.toLong() shl 24)
                onColorChange(newColor)
            },
            valueRange = 0f..1f,
            displayValue = "${(bgAlpha * 100).toInt()}%"
        )
    }
}

@Composable
internal fun FontColorPanel(
    currentColor: Long,
    onColorChange: (Long) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
    ) {
        PanelHeader(title = "Font Color", onDismiss = onDismiss, onReset = onReset)
        SectionDivider()
        Spacer(Modifier.height(8.dp))
        ImmediateColorPickerContent(
            initialColor = Color(currentColor),
            onColorChange = { newColor ->
                onColorChange(newColor.toArgb().toLong())
            }
        )
    }
}

@Composable
internal fun AdvancedPanel(
    verticalPosition: Float,
    onVerticalChange: (Float) -> Unit,
    horizontalPosition: Float,
    onHorizontalChange: (Float) -> Unit,
    maxWidthRatio: Float,
    onMaxWidthChange: (Float) -> Unit,
    delayMs: Int,
    onDelayChange: (Int) -> Unit,
    rotation: Float,
    onRotationChange: (Float) -> Unit,
    onResetAll: () -> Unit,
    onResetVertical: () -> Unit,
    onResetHorizontal: () -> Unit,
    onResetMaxWidth: () -> Unit,
    onResetDelay: () -> Unit,
    onResetRotation: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        PanelHeader(title = "Advanced Settings", onDismiss = onDismiss, onReset = onResetAll)
        SectionDivider()
        Spacer(Modifier.height(8.dp))

        Text(
            text = "Position",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Vertical", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            ResetTextButton(onClick = onResetVertical)
        }
        SliderRow(
            label = "",
            value = verticalPosition,
            onValueChange = onVerticalChange,
            valueRange = 0.05f..0.95f,
            displayValue = "${(verticalPosition * 100).toInt()}%"
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Horizontal", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            ResetTextButton(onClick = onResetHorizontal)
        }
        SliderRow(
            label = "",
            value = horizontalPosition,
            onValueChange = onHorizontalChange,
            valueRange = 0.05f..0.95f,
            displayValue = "${(horizontalPosition * 100).toInt()}%"
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Max Width", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            ResetTextButton(onClick = onResetMaxWidth)
        }
        SliderRow(
            label = "",
            value = maxWidthRatio,
            onValueChange = onMaxWidthChange,
            valueRange = 0.3f..1f,
            displayValue = "${(maxWidthRatio * 100).toInt()}%"
        )

        Spacer(Modifier.height(8.dp))
        SectionDivider()
        Spacer(Modifier.height(8.dp))

        Text(
            text = "Timing",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Delay", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            ResetTextButton(onClick = onResetDelay)
        }
        SliderRow(
            label = "",
            value = (delayMs / 1000f).coerceIn(-10f, 10f),
            onValueChange = { onDelayChange((it * 1000).roundToInt()) },
            valueRange = -10f..10f,
            displayValue = "${delayMs} ms"
        )

        Spacer(Modifier.height(8.dp))
        SectionDivider()
        Spacer(Modifier.height(8.dp))

        Text(
            text = "Rotation",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Angle: ${rotation.toInt()}°", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
            ResetTextButton(onClick = onResetRotation)
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(0f, 45f, 90f, 135f, 180f, 270f).forEach { ang ->
                Surface(
                    onClick = { onRotationChange(ang) },
                    shape = chipShape,
                    color = if (rotation == ang) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                    else Color.White.copy(alpha = 0.06f)
                ) {
                    Text(
                        text = "${ang.toInt()}°",
                        color = if (rotation == ang) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f),
                        fontSize = 13.sp,
                        fontWeight = if (rotation == ang) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

