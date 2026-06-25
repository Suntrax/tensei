package com.blissless.tensei.ui.screens.player

import android.app.Activity
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.blissless.tensei.data.models.SubtitleSettings
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val LOREM_IPSUM = "The quick brown fox jumps over the lazy dog.\nThis is a second line for testing."

// ----- Data classes -----
data class TemplateBg(val name: String, val colors: List<Color>)

private val TEMPLATES = listOf(
    TemplateBg("Dark Scene", listOf(Color(0xFF1a1a2e), Color(0xFF16213e))),
    TemplateBg("Daylight", listOf(Color(0xFF87CEEB), Color(0xFF98D8C8))),
    TemplateBg("Sunset", listOf(Color(0xFFFF6B35), Color(0xFFFFD93D))),
    TemplateBg("Forest", listOf(Color(0xFF2D5016), Color(0xFF4A7C2E))),
    TemplateBg("Night Sky", listOf(Color(0xFF0a0a2e), Color(0xFF1a1a4e))),
    TemplateBg("Beach", listOf(Color(0xFF0077B6), Color(0xFFF4A261))),
    TemplateBg("Solid Black", listOf(Color.Black)),
    TemplateBg("Solid White", listOf(Color.White)),
)

private val OUTLINE_PRESETS = listOf(
    Color.Black, Color.White, Color.Red, Color(0xFFFF8C00),
    Color.Yellow, Color.Green, Color.Blue, Color.Magenta
)
private val SHADOW_PRESETS = listOf(
    Color.Black, Color.White, Color(0xFF333333), Color(0xFF555555), Color(0xFF777777)
)
private val BG_SUB_PRESETS = listOf(
    0x00000000L, 0x40000000L, 0x80000000L, 0xC0000000L,
    0x40FFFFFFL, 0x80FFFFFFL, 0xFF000000L, 0xFFFFFFFFL
)

enum class ResizeMode { Fit16x9, Stretch }

// Unified full settings including shadow and position
data class SubtitleFullSettings(
    val fontSize: Float = 16f,
    val fontColor: Long = 0xFFFFFFFFL,
    val enableOutline: Boolean = false,
    val outlineWidth: Float = 2f,
    val outlineColor: Long = 0xFF000000L,
    val enableShadow: Boolean = false,
    val shadowBlur: Float = 2f,
    val shadowOffsetX: Float = 2f,
    val shadowOffsetY: Float = 2f,
    val shadowColor: Long = 0xFF000000L,
    val backgroundColor: Long = 0x00000000L,
    val verticalPosition: Float = 0.85f,
    val horizontalPosition: Float = 0.5f,
    val maxWidthRatio: Float = 0.8f,
    val delayMs: Int = 0,
    val profileName: String = "Default"
) {
    fun toLegacy(): SubtitleSettings = SubtitleSettings(
        fontSize = fontSize,
        fontColor = fontColor,
        enableShadow = enableShadow,
        enableOutline = enableOutline,
        outlineWidth = outlineWidth,
        outlineColor = outlineColor,
        backgroundColor = backgroundColor,
        verticalPosition = verticalPosition,
        horizontalPosition = horizontalPosition,
        maxWidthRatio = maxWidthRatio,
        delayMs = delayMs,
        profileName = profileName
    )
}

private object Defaults {
    val FULL_SETTINGS = SubtitleFullSettings()
}

// Main entry point
@RequiresApi(Build.VERSION_CODES.R)
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
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onDismiss)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        SubtitleSettingsContent(
            currentSettings = currentSettings,
            profiles = profiles,
            activeProfileIndex = activeProfileIndex,
            onSettingsChange = onSettingsChange,
            onProfileSelect = onProfileSelect,
            onResetProfile = onResetProfile,
            onRenameProfile = onRenameProfile,
            onDismiss = onDismiss,
            onSave = onSave
        )
    }
}

@RequiresApi(Build.VERSION_CODES.R)
@Composable
private fun SubtitleSettingsContent(
    currentSettings: SubtitleSettings,
    profiles: List<SubtitleSettings>,
    activeProfileIndex: Int,
    onSettingsChange: (SubtitleSettings) -> Unit,
    onProfileSelect: (Int) -> Unit,
    onResetProfile: (Int) -> Unit,
    onRenameProfile: (Int, String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    // Merge current settings into full settings with defaults
    var fullSettings by remember { mutableStateOf(
        SubtitleFullSettings(
            fontSize = currentSettings.fontSize,
            fontColor = currentSettings.fontColor,
            enableOutline = currentSettings.enableOutline,
            outlineWidth = currentSettings.outlineWidth,
            outlineColor = currentSettings.outlineColor,
            enableShadow = currentSettings.enableShadow,
            shadowBlur = Defaults.FULL_SETTINGS.shadowBlur,
            shadowOffsetX = Defaults.FULL_SETTINGS.shadowOffsetX,
            shadowOffsetY = Defaults.FULL_SETTINGS.shadowOffsetY,
            shadowColor = Defaults.FULL_SETTINGS.shadowColor,
            backgroundColor = currentSettings.backgroundColor,
            verticalPosition = currentSettings.verticalPosition,
            horizontalPosition = currentSettings.horizontalPosition,
            maxWidthRatio = currentSettings.maxWidthRatio,
            delayMs = currentSettings.delayMs,
            profileName = currentSettings.profileName
        )
    ) }

    var selectedTemplateIndex by remember { mutableIntStateOf(0) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var rotation by remember { mutableFloatStateOf(0f) }
    var resizeMode by remember { mutableStateOf(ResizeMode.Fit16x9) }
    var showRotationWheel by remember { mutableStateOf(false) }
    var showSidePanel by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var showCloseConfirm by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }

    // Track whether any changes have been made (for close confirmation)
    var hasChanges by remember { mutableStateOf(false) }

    // Mark changes whenever settings/drag/rotation change
    LaunchedEffect(fullSettings, dragOffsetX, dragOffsetY, rotation) {
        hasChanges = true
    }

    var actualWidth by remember { mutableIntStateOf(1080) }
    var actualHeight by remember { mutableIntStateOf(1920) }

    // Update ExoPlayer preview in real time, but don't auto-save to disk
    LaunchedEffect(fullSettings) {
        onSettingsChange(fullSettings.toLegacy())
    }

    // Commit drag offset into fullSettings when drag ends
    fun commitDrag() {
        if (dragOffsetX != 0f || dragOffsetY != 0f) {
            val newV = ((fullSettings.verticalPosition * actualHeight + dragOffsetY) / actualHeight).coerceIn(0.05f, 0.95f)
            val newH = ((fullSettings.horizontalPosition * actualWidth + dragOffsetX) / actualWidth).coerceIn(0.05f, 0.95f)
            fullSettings = fullSettings.copy(verticalPosition = newV, horizontalPosition = newH)
            dragOffsetX = 0f
            dragOffsetY = 0f
        }
    }

    // Quick action dialogs (non‑modal)
    var showTemplateDialog by remember { mutableStateOf(false) }
    var showTextSizeDialog by remember { mutableStateOf(false) }
    var showOutlineDialog by remember { mutableStateOf(false) }
    var showShadowDialog by remember { mutableStateOf(false) }
    var showBgDialog by remember { mutableStateOf(false) }
    var showFontColorPicker by remember { mutableStateOf(false) }

    // Immersive mode
    val view = LocalView.current
    val window = (view.context as Activity).window
    LaunchedEffect(Unit) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    val template = TEMPLATES[selectedTemplateIndex]
    val gradient = if (template.colors.size == 1) {
        Brush.verticalGradient(listOf(template.colors[0], template.colors[0]))
    } else {
        Brush.verticalGradient(template.colors)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                actualWidth = size.width
                actualHeight = size.height
            }
    ) {
        // Background
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cw = size.width
            val ch = size.height
            val aspect = 16f / 9f
            val dest = when (resizeMode) {
                ResizeMode.Fit16x9 -> {
                    val curr = cw / ch
                    if (curr > aspect) {
                        val w = ch * aspect
                        RectF((cw - w) / 2, 0f, (cw + w) / 2, ch)
                    } else {
                        val h = cw / aspect
                        RectF(0f, (ch - h) / 2, cw, (ch + h) / 2)
                    }
                }
                ResizeMode.Stretch -> RectF(0f, 0f, cw, ch)
            }
            // Corrected drawRect call: brush, topLeft, size
            drawRect(
                brush = gradient,
                topLeft = Offset(dest.left, dest.top),
                size = Size(dest.width(), dest.height())
            )
        }

        // Subtitle preview with rotation wheel
        val baseX = fullSettings.horizontalPosition * actualWidth
        val baseY = fullSettings.verticalPosition * actualHeight
        SubtitlePreview(
            settings = fullSettings,
            rotation = rotation,
            offsetX = baseX + dragOffsetX,
            offsetY = baseY + dragOffsetY,
            onDrag = { dx, dy ->
                dragOffsetX += dx
                dragOffsetY += dy
            },
            onDragEnd = ::commitDrag,
            onTap = { showRotationWheel = !showRotationWheel },
            showRotateWheel = showRotationWheel,
            onRotate = { rotation = it },
            modifier = Modifier.fillMaxSize()
        )

        // Top-left: profile selector, rename, save, close (stacked)
        var showProfileDropdown by remember { mutableStateOf(false) }
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .systemBarsPadding()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Profile selector
            Box {
                Button(
                    onClick = { showProfileDropdown = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = profiles.getOrNull(activeProfileIndex)?.profileName ?: "Profile",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
                DropdownMenu(
                    expanded = showProfileDropdown,
                    onDismissRequest = { showProfileDropdown = false }
                ) {
                    profiles.forEachIndexed { idx, p ->
                        DropdownMenuItem(
                            text = { Text(p.profileName) },
                            onClick = {
                                commitDrag()
                                onProfileSelect(idx)
                                showProfileDropdown = false
                            }
                        )
                    }
                }
            }
            // Rename
            Button(
                onClick = {
                    renameText = profiles.getOrNull(activeProfileIndex)?.profileName ?: ""
                    showRenameDialog = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF444444)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Rename", color = Color.White, fontWeight = FontWeight.Bold)
            }
            // Save
            Button(
                onClick = {
                    commitDrag()
                    onSave()
                    hasChanges = false
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Save", color = Color.White, fontWeight = FontWeight.Bold)
            }
            // Close
            Button(
                onClick = {
                    if (hasChanges) {
                        showCloseConfirm = true
                    } else {
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF555555)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Close", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        // Top-right quick actions (scrollable to prevent overlap)
        val btnBg = Color.White.copy(alpha = 0.18f)
        val btnTint = Color.White
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .systemBarsPadding()
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Template
            IconButton(
                onClick = { showTemplateDialog = true },
                modifier = Modifier
                    .size(36.dp)
                    .background(btnBg, CircleShape)
            ) { Icon(Icons.Default.Palette, "Templates", tint = btnTint, modifier = Modifier.size(18.dp)) }
            // Resize
            IconButton(
                onClick = {
                    resizeMode = if (resizeMode == ResizeMode.Fit16x9) ResizeMode.Stretch else ResizeMode.Fit16x9
                },
                modifier = Modifier
                    .size(36.dp)
                    .background(btnBg, CircleShape)
            ) {
                Icon(
                    if (resizeMode == ResizeMode.Fit16x9) Icons.Default.FitScreen else Icons.Default.AspectRatio,
                    "Resize",
                    tint = btnTint,
                    modifier = Modifier.size(18.dp)
                )
            }
            // Text size
            IconButton(
                onClick = { showTextSizeDialog = true },
                modifier = Modifier
                    .size(36.dp)
                    .background(btnBg, CircleShape)
            ) { Icon(Icons.Default.FormatSize, "Size", tint = btnTint, modifier = Modifier.size(18.dp)) }
            // Outline
            IconButton(
                onClick = { showOutlineDialog = true },
                modifier = Modifier
                    .size(36.dp)
                    .background(btnBg, CircleShape)
            ) { Icon(Icons.Default.BorderColor, "Outline", tint = btnTint, modifier = Modifier.size(18.dp)) }
            // Shadow
            IconButton(
                onClick = { showShadowDialog = true },
                modifier = Modifier
                    .size(36.dp)
                    .background(btnBg, CircleShape)
            ) { Icon(Icons.Default.BlurOn, "Shadow", tint = btnTint, modifier = Modifier.size(18.dp)) }
            // Subtitle background
            IconButton(
                onClick = { showBgDialog = true },
                modifier = Modifier
                    .size(36.dp)
                    .background(btnBg, CircleShape)
            ) { Icon(Icons.Default.FormatColorFill, "BG", tint = btnTint, modifier = Modifier.size(18.dp)) }
            // Text colour
            IconButton(
                onClick = { showFontColorPicker = true },
                modifier = Modifier
                    .size(36.dp)
                    .background(btnBg, CircleShape)
            ) { Icon(Icons.Default.FormatColorText, "Color", tint = btnTint, modifier = Modifier.size(18.dp)) }
        }
    }

    // ----- Quick Dialogs (non‑modal) -----
    if (showTemplateDialog) {
        AlertDialog(
            onDismissRequest = { showTemplateDialog = false },
            title = { Text("Background Templates") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    TEMPLATES.chunked(4).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { tmpl ->
                                val idx = TEMPLATES.indexOf(tmpl)
                                val bg = if (tmpl.colors.size == 1) Modifier.background(tmpl.colors[0]) else Modifier.background(
                                    Brush.verticalGradient(tmpl.colors)
                                )
                                Box(
                                    Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .then(bg)
                                        .border(
                                            if (idx == selectedTemplateIndex) 2.dp else 0.dp,
                                            MaterialTheme.colorScheme.primary,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            selectedTemplateIndex = idx
                                            showTemplateDialog = false
                                        }
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (showTextSizeDialog) {
        AlertDialog(
            onDismissRequest = { showTextSizeDialog = false },
            title = { Text("Text Size") },
            text = {
                Column {
                    Text(
                        "${fullSettings.fontSize.toInt()} sp",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Slider(
                        value = fullSettings.fontSize,
                        onValueChange = { fullSettings = fullSettings.copy(fontSize = it) },
                        valueRange = 10f..48f
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showTextSizeDialog = false }) { Text("Done") }
            }
        )
    }

    if (showOutlineDialog) {
        AlertDialog(
            onDismissRequest = { showOutlineDialog = false },
            title = { Text("Outline") },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Enable")
                        Spacer(Modifier.weight(1f))
                        Switch(
                            checked = fullSettings.enableOutline,
                            onCheckedChange = { fullSettings = fullSettings.copy(enableOutline = it) }
                        )
                    }
                    if (fullSettings.enableOutline) {
                        Spacer(Modifier.height(8.dp))
                        Text("Width: ${fullSettings.outlineWidth.toInt()} px")
                        Slider(
                            value = fullSettings.outlineWidth,
                            onValueChange = { fullSettings = fullSettings.copy(outlineWidth = it) },
                            valueRange = 1f..6f
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("Color")
                        Row(Modifier.horizontalScroll(rememberScrollState())) {
                            OUTLINE_PRESETS.forEach { c ->
                                Box(
                                    Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(c)
                                        .border(
                                            if (fullSettings.outlineColor == c.toArgb()
                                                    .toLong()
                                            ) 2.dp else 1.dp,
                                            MaterialTheme.colorScheme.primary,
                                            CircleShape
                                        )
                                        .clickable {
                                            fullSettings = fullSettings.copy(outlineColor = c.toArgb().toLong())
                                        }
                                )
                                Spacer(Modifier.width(4.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showOutlineDialog = false }) { Text("Done") }
            }
        )
    }

    if (showShadowDialog) {
        AlertDialog(
            onDismissRequest = { showShadowDialog = false },
            title = { Text("Shadow") },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Enable")
                        Spacer(Modifier.weight(1f))
                        Switch(
                            checked = fullSettings.enableShadow,
                            onCheckedChange = { fullSettings = fullSettings.copy(enableShadow = it) }
                        )
                    }
                    if (fullSettings.enableShadow) {
                        Spacer(Modifier.height(8.dp))
                        Text("Blur: ${fullSettings.shadowBlur.toInt()} px")
                        Slider(
                            value = fullSettings.shadowBlur,
                            onValueChange = { fullSettings = fullSettings.copy(shadowBlur = it) },
                            valueRange = 1f..10f
                        )
                        Text("Offset X: ${fullSettings.shadowOffsetX.toInt()} px")
                        Slider(
                            value = fullSettings.shadowOffsetX,
                            onValueChange = { fullSettings = fullSettings.copy(shadowOffsetX = it) },
                            valueRange = -10f..10f
                        )
                        Text("Offset Y: ${fullSettings.shadowOffsetY.toInt()} px")
                        Slider(
                            value = fullSettings.shadowOffsetY,
                            onValueChange = { fullSettings = fullSettings.copy(shadowOffsetY = it) },
                            valueRange = -10f..10f
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("Color")
                        Row(Modifier.horizontalScroll(rememberScrollState())) {
                            SHADOW_PRESETS.forEach { c ->
                                Box(
                                    Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(c)
                                        .border(
                                            if (fullSettings.shadowColor == c.toArgb()
                                                    .toLong()
                                            ) 2.dp else 1.dp,
                                            MaterialTheme.colorScheme.primary,
                                            CircleShape
                                        )
                                        .clickable {
                                            fullSettings = fullSettings.copy(shadowColor = c.toArgb().toLong())
                                        }
                                )
                                Spacer(Modifier.width(4.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showShadowDialog = false }) { Text("Done") }
            }
        )
    }

    if (showBgDialog) {
        AlertDialog(
            onDismissRequest = { showBgDialog = false },
            title = { Text("Subtitle Background") },
            text = {
                Column {
                    Text("Color & Opacity")
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        BG_SUB_PRESETS.forEach { colorLong ->
                            val color = Color(colorLong)
                            Box(
                                Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        if (fullSettings.backgroundColor == colorLong) 2.dp else 1.dp,
                                        MaterialTheme.colorScheme.primary,
                                        CircleShape
                                    )
                                    .clickable {
                                        fullSettings = fullSettings.copy(backgroundColor = colorLong)
                                    }
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBgDialog = false }) { Text("Done") }
            }
        )
    }

    if (showFontColorPicker) {
        var editingColor by remember { mutableStateOf(Color(fullSettings.fontColor)) }
        AlertDialog(
            onDismissRequest = { showFontColorPicker = false },
            title = { Text("Font Color") },
            text = {
                Column {
                    ImmediateColorPickerContent(
                        initialColor = editingColor,
                        onColorChange = { newColor ->
                            editingColor = newColor
                            fullSettings = fullSettings.copy(fontColor = newColor.toArgb().toLong())
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showFontColorPicker = false }) { Text("Done") }
            }
        )
    }

    // Side panel overlay (sliding from right)
    if (showSidePanel) {
        // Transparent background to close on tap
        Box(Modifier.fillMaxSize().clickable { showSidePanel = false }) {
            AnimatedVisibility(
                visible = showSidePanel,
                enter = slideInHorizontally(initialOffsetX = { it }),
                exit = slideOutHorizontally(targetOffsetX = { it }),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(280.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        Text("Settings", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(12.dp))

                        // Profiles
                        Text("Profiles", style = MaterialTheme.typography.labelMedium)
                        Row(Modifier.horizontalScroll(rememberScrollState())) {
                            profiles.forEachIndexed { idx, p ->
                                FilterChip(
                                    selected = idx == activeProfileIndex,
                                    onClick = { onProfileSelect(idx) },
                                    label = { Text(p.profileName) }
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))

                        // Position sliders
                        Text("Vertical: ${(fullSettings.verticalPosition * 100).toInt()}%")
                        Slider(
                            value = fullSettings.verticalPosition,
                            onValueChange = { fullSettings = fullSettings.copy(verticalPosition = it) },
                            valueRange = 0.05f..0.95f
                        )
                        Text("Horizontal: ${(fullSettings.horizontalPosition * 100).toInt()}%")
                        Slider(
                            value = fullSettings.horizontalPosition,
                            onValueChange = { fullSettings = fullSettings.copy(horizontalPosition = it) },
                            valueRange = 0.05f..0.95f
                        )
                        Text("Max Width: ${(fullSettings.maxWidthRatio * 100).toInt()}%")
                        Slider(
                            value = fullSettings.maxWidthRatio,
                            onValueChange = { fullSettings = fullSettings.copy(maxWidthRatio = it) },
                            valueRange = 0.3f..1f
                        )

                        // Delay
                        Text("Delay: ${fullSettings.delayMs} ms")
                        Slider(
                            value = (fullSettings.delayMs / 1000f).coerceIn(-10f, 10f),
                            onValueChange = {
                                fullSettings = fullSettings.copy(delayMs = (it * 1000).roundToInt())
                            },
                            valueRange = -10f..10f
                        )

                        // Rotation presets
                        Text("Rotation: ${rotation.toInt()}°")
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(0f, 90f, 180f, 270f).forEach { ang ->
                                FilterChip(
                                    selected = rotation == ang,
                                    onClick = { rotation = ang },
                                    label = { Text("${ang.toInt()}°") }
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // Reset all
                        Button(
                            onClick = { showResetConfirm = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                        ) {
                            Text("Reset all to default")
                        }
                    }
                }
            }
        }
    }

    // Close confirmation (unsaved changes)
    if (showCloseConfirm) {
        AlertDialog(
            onDismissRequest = { showCloseConfirm = false },
            title = { Text("Unsaved changes") },
            text = { Text("You have unsaved changes. What would you like to do?") },
            confirmButton = {
                TextButton(onClick = {
                    commitDrag()
                    onSave()
                    hasChanges = false
                    showCloseConfirm = false
                    onDismiss()
                }) { Text("Save and close") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showCloseConfirm = false
                        onDismiss()
                    }) { Text("Discard") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { showCloseConfirm = false }) { Text("Cancel") }
                }
            }
        )
    }

    // Rename dialog
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Profile") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Profile name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (renameText.isNotBlank()) {
                        onRenameProfile(activeProfileIndex, renameText.trim())
                    }
                    showRenameDialog = false
                }) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Reset confirmation
    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset settings?") },
            text = { Text("Restore all subtitle settings to default?") },
            confirmButton = {
                TextButton(onClick = {
                    fullSettings = Defaults.FULL_SETTINGS
                    rotation = 0f
                    showResetConfirm = false
                }) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

// ---------- Subtitle Preview with draggable rotation wheel ----------
@Composable
private fun SubtitlePreview(
    settings: SubtitleFullSettings,
    rotation: Float,
    offsetX: Float,
    offsetY: Float,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit = {},
    onTap: () -> Unit,
    showRotateWheel: Boolean,
    onRotate: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val textColor = if (settings.fontColor == 0x00000000L) Color.White else Color(settings.fontColor)
    val bgColor = Color(settings.backgroundColor)
    val outlineColor = Color(settings.outlineColor)

    val dropShadow = if (settings.enableShadow) {
        Shadow(
            color = Color(settings.shadowColor),
            offset = Offset(settings.shadowOffsetX, settings.shadowOffsetY),
            blurRadius = settings.shadowBlur
        )
    } else null

    val outlineBlur = if (settings.enableOutline) settings.outlineWidth * 1.5f else 0f
    var boxWidth by remember { mutableIntStateOf(0) }
    var boxHeight by remember { mutableIntStateOf(0) }

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        (offsetX - boxWidth / 2f).roundToInt(),
                        (offsetY - boxHeight / 2f).roundToInt()
                    )
                }
                .onSizeChanged { boxWidth = it.width; boxHeight = it.height }
                .background(
                    bgColor.copy(alpha = if (settings.backgroundColor == 0x00000000L) 0f else 1f),
                    RoundedCornerShape(4.dp)
                )
                .pointerInput(Unit) { detectTapGestures { onTap() } }
        ) {
            Box(
                modifier = Modifier.pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragEnd,
                        onDrag = { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount.x, dragAmount.y)
                        }
                    )
                }
            ) {
                // Rotated content
                Box(
                    modifier = Modifier
                        .graphicsLayer { rotationZ = rotation }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    // Outline layer
                    if (settings.enableOutline) {
                        Text(
                            text = LOREM_IPSUM,
                            color = outlineColor,
                            fontSize = settings.fontSize.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            style = TextStyle(shadow = Shadow(color = outlineColor, offset = Offset.Zero, blurRadius = outlineBlur))
                        )
                    }
                    // Main text
                    Text(
                        text = LOREM_IPSUM,
                        color = textColor,
                        fontSize = settings.fontSize.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        style = TextStyle(shadow = dropShadow)
                    )
                }

                // Rotation wheel at top-right of the subtitle
                if (showRotateWheel) {
                    RotationWheel(
                        currentAngle = rotation,
                        onAngleChange = onRotate,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 4.dp, y = (-4).dp)
                            .size(40.dp)
                    )
                }
            }
        }
    }
}

// ---------- Rotation wheel (draggable circle with line indicator) ----------
@Composable
private fun RotationWheel(
    currentAngle: Float,
    onAngleChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var center by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.7f), CircleShape)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        center = Offset(size.width / 2f, size.height / 2f)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val touchPos = change.position
                        val delta = touchPos - center
                        val angle = Math.toDegrees(atan2(delta.y.toDouble(), delta.x.toDouble())).toFloat()
                        // Convert to 0-360, then to -180..180
                        var normalized = (angle + 90) % 360
                        if (normalized < 0) normalized += 360
                        val displayAngle = if (normalized <= 180) normalized else normalized - 360
                        onAngleChange(displayAngle)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val radius = size.width / 3f
            val angleRad = Math.toRadians(currentAngle.toDouble() + 90)
            val lineEndX = cx + (radius * cos(angleRad)).toFloat()
            val lineEndY = cy + (radius * sin(angleRad)).toFloat()
            drawCircle(
                color = Color.DarkGray,
                radius = radius,
                center = Offset(cx, cy),
                style = Stroke(width = 2.dp.toPx())
            )
            drawLine(
                color = Color.Red,
                start = Offset(cx, cy),
                end = Offset(lineEndX, lineEndY),
                strokeWidth = 2.dp.toPx()
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.RotateRight,
            contentDescription = "Rotate",
            modifier = Modifier.size(16.dp),
            tint = Color.DarkGray
        )
    }
}

// ---------- Immediate color picker content (used inside dialogs) ----------
@Composable
private fun ImmediateColorPickerContent(
    initialColor: Color,
    onColorChange: (Color) -> Unit,
) {
    var hue by remember { mutableFloatStateOf(0f) }
    var saturation by remember { mutableFloatStateOf(1f) }
    var lightness by remember { mutableFloatStateOf(0.5f) }
    var alpha by remember { mutableFloatStateOf(initialColor.alpha) }

    LaunchedEffect(initialColor) {
        val hsl = rgbToHsl(initialColor)
        hue = hsl[0]
        saturation = hsl[1]
        lightness = hsl[2]
        alpha = hsl[3]
    }

    val currentColor = remember(hue, saturation, lightness, alpha) {
        Color.hsl(hue, saturation, lightness, alpha)
    }

    LaunchedEffect(currentColor) { onColorChange(currentColor) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Palette
        val paletteSize = 200.dp
        Box(
            modifier = Modifier
                .size(paletteSize)
                .clip(RoundedCornerShape(4.dp))
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val xFraction = (offset.x / size.width).coerceIn(0f, 1f)
                        val yFraction = (offset.y / size.height).coerceIn(0f, 1f)
                        hue = xFraction * 360f
                        saturation = 1f
                        lightness = 1f - yFraction
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                for (x in 0 until size.width.toInt() step 2) {
                    val currentHue = x / size.width * 360f
                    drawRect(
                        color = Color.hsl(currentHue, 1f, 0.5f),
                        topLeft = Offset(x.toFloat(), 0f),
                        size = Size(2f, size.height)
                    )
                }
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0f), Color.Black),
                        startY = 0f,
                        endY = size.height
                    )
                )
                val indicatorX = hue / 360f * size.width
                val indicatorY = (1f - lightness) * size.height
                drawCircle(Color.White, radius = 6f, center = Offset(indicatorX, indicatorY), style = Stroke(width = 2f))
                drawCircle(Color.Black, radius = 4f, center = Offset(indicatorX, indicatorY))
            }
        }

        Spacer(Modifier.height(8.dp))

        // Alpha slider with preview
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(currentColor)
                    .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Alpha: ${(alpha * 100).toInt()}%",
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
        }
        Slider(
            value = alpha,
            onValueChange = { alpha = it },
            valueRange = 0f..1f,
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = currentColor.copy(alpha = 0.7f),
                inactiveTrackColor = Color.Gray.copy(alpha = 0.3f)
            )
        )

        // Hex input
        var hex by remember {
            mutableStateOf(
                String.format(
                    "%02X%02X%02X",
                    (currentColor.red * 255).toInt(),
                    (currentColor.green * 255).toInt(),
                    (currentColor.blue * 255).toInt()
                )
            )
        }
        OutlinedTextField(
            value = hex,
            onValueChange = {
                val filtered = it.take(6).uppercase().filter { c -> c in "0123456789ABCDEF" }
                hex = filtered
                if (filtered.length == 6) {
                    val r = filtered.substring(0, 2).toInt(16) / 255f
                    val g = filtered.substring(2, 4).toInt(16) / 255f
                    val b = filtered.substring(4, 6).toInt(16) / 255f
                    val newColor = Color(r, g, b, alpha)
                    val hsl = rgbToHsl(newColor)
                    hue = hsl[0]
                    saturation = hsl[1]
                    lightness = hsl[2]
                }
            },
            label = { Text("RGB Hex") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
    }
}

// Utility: RGB to HSL
private fun rgbToHsl(color: Color): FloatArray {
    val r = color.red
    val g = color.green
    val b = color.blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    val lightness = (max + min) / 2f
    if (delta == 0f) return floatArrayOf(0f, 0f, lightness, color.alpha)
    val saturation = if (lightness <= 0.5f) delta / (max + min) else delta / (2f - max - min)
    val hue = when (max) {
        r -> ((g - b) / delta + if (g < b) 6f else 0f)
        g -> ((b - r) / delta + 2f)
        else -> ((r - g) / delta + 4f)
    } * 60f
    return floatArrayOf(hue.coerceAtLeast(0f), saturation, lightness, color.alpha)
}

// Helper RectF for Canvas
private data class RectF(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun width() = right - left
    fun height() = bottom - top
}