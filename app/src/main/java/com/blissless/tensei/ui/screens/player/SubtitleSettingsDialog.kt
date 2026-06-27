package com.blissless.tensei.ui.screens.player

import android.app.Activity
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
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

data class SubtitleFullSettings(
    val fontSize: Float = 22f,
    val fontColor: Long = 0xFFFFFFFFL,
    val enableOutline: Boolean = true,
    val outlineWidth: Float = 2f,
    val outlineColor: Long = 0xFF000000L,
    val enableShadow: Boolean = false,
    val shadowBlur: Float = 2f,
    val shadowOffsetX: Float = 2f,
    val shadowOffsetY: Float = 2f,
    val shadowColor: Long = 0xFF000000L,
    val backgroundColor: Long = 0x00000000L,
    val verticalPosition: Float = 0.9f,
    val horizontalPosition: Float = 0.5f,
    val maxWidthRatio: Float = 0.95f,
    val delayMs: Int = 0,
    val rotation: Float = 0f,
    val profileName: String = "Default"
) {
    fun toLegacy(): SubtitleSettings = SubtitleSettings(
        fontSize = fontSize,
        fontColor = fontColor,
        enableShadow = enableShadow,
        enableOutline = enableOutline,
        outlineWidth = outlineWidth,
        outlineColor = outlineColor,
        shadowBlur = shadowBlur,
        shadowOffsetX = shadowOffsetX,
        shadowOffsetY = shadowOffsetY,
        shadowColor = shadowColor,
        rotation = rotation,
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

private object SubColors {
    val surfacePanel = Color(0xFF121220)
    val divider = Color(0xFF2A2A40)
    val accent = Color(0xFF42A5F5)
    val accentGlow = Color(0x2042A5F5)
}

private val panelShape = RoundedCornerShape(20.dp, 20.dp, 0.dp, 0.dp)
private val cardShape = RoundedCornerShape(14.dp)
private val chipShape = RoundedCornerShape(10.dp)
private val toolbarShape = RoundedCornerShape(16.dp)

// ---------------------------------------------------------------------------
// Entry point
// ---------------------------------------------------------------------------
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
        modifier = modifier.fillMaxSize().background(Color.Black)
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
            shadowColor = currentSettings.shadowColor,
            backgroundColor = currentSettings.backgroundColor,
            verticalPosition = currentSettings.verticalPosition,
            horizontalPosition = currentSettings.horizontalPosition,
            maxWidthRatio = currentSettings.maxWidthRatio,
            delayMs = currentSettings.delayMs,
            rotation = currentSettings.rotation,
            profileName = currentSettings.profileName
        )
    ) }

    var selectedTemplateIndex by remember { mutableIntStateOf(0) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var resizeMode by remember { mutableStateOf(ResizeMode.Fit16x9) }
    var showRotationWheel by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var showCloseConfirm by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var hasChanges by remember { mutableStateOf(false) }
    var activePanel by remember { mutableStateOf<Panel?>(null) }

    // Sync fullSettings when the external profile changes
    LaunchedEffect(activeProfileIndex) {
        val p = currentSettings
        fullSettings = SubtitleFullSettings(
            fontSize = p.fontSize,
            fontColor = p.fontColor,
            enableOutline = p.enableOutline,
            outlineWidth = p.outlineWidth,
            outlineColor = p.outlineColor,
            enableShadow = p.enableShadow,
            shadowBlur = Defaults.FULL_SETTINGS.shadowBlur,
            shadowOffsetX = Defaults.FULL_SETTINGS.shadowOffsetX,
            shadowOffsetY = Defaults.FULL_SETTINGS.shadowOffsetY,
            shadowColor = p.shadowColor,
            backgroundColor = p.backgroundColor,
            verticalPosition = p.verticalPosition,
            horizontalPosition = p.horizontalPosition,
            maxWidthRatio = p.maxWidthRatio,
            delayMs = p.delayMs,
            rotation = p.rotation,
            profileName = p.profileName
        )
        dragOffsetX = 0f
        dragOffsetY = 0f
    }

    LaunchedEffect(fullSettings, dragOffsetX, dragOffsetY) {
        hasChanges = true
    }

    var actualWidth by remember { mutableIntStateOf(1080) }
    var actualHeight by remember { mutableIntStateOf(1920) }

    LaunchedEffect(fullSettings) {
        onSettingsChange(fullSettings.toLegacy())
    }

    fun commitDrag() {
        if (dragOffsetX != 0f || dragOffsetY != 0f) {
            val newV = ((fullSettings.verticalPosition * actualHeight + dragOffsetY) / actualHeight).coerceIn(0.05f, 0.95f)
            val newH = ((fullSettings.horizontalPosition * actualWidth + dragOffsetX) / actualWidth).coerceIn(0.05f, 0.95f)
            fullSettings = fullSettings.copy(verticalPosition = newV, horizontalPosition = newH)
            dragOffsetX = 0f
            dragOffsetY = 0f
        }
    }

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
        // Background canvas
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
            drawRect(
                brush = gradient,
                topLeft = Offset(dest.left, dest.top),
                size = Size(dest.width(), dest.height())
            )
        }

        val destRect = remember(actualWidth, actualHeight, resizeMode) {
            val cw = actualWidth.toFloat()
            val ch = actualHeight.toFloat()
            val aspect = 16f / 9f
            when (resizeMode) {
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
        }

        // ── Top bar ─────────────────────────────────────────────────────────
        var showProfileDropdown by remember { mutableStateOf(false) }
        Surface(
            color = Color.Black.copy(alpha = 0.5f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    Box(
                        modifier = Modifier
                            .clip(chipShape)
                            .background(Color.White.copy(alpha = 0.08f))
                            .clickable { showProfileDropdown = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = profiles.getOrNull(activeProfileIndex)?.profileName ?: "Profile",
                                color = Color.White.copy(alpha = 0.9f),
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "▾",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 10.sp
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = showProfileDropdown,
                        onDismissRequest = { showProfileDropdown = false },
                        shape = cardShape
                    ) {
                        profiles.forEachIndexed { idx, p ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        p.profileName,
                                        fontWeight = if (idx == activeProfileIndex) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    commitDrag()
                                    onProfileSelect(idx)
                                    showProfileDropdown = false
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    ToolbarIconButton(Icons.Default.Edit, "Rename") {
                        renameText = profiles.getOrNull(activeProfileIndex)?.profileName ?: ""
                        showRenameDialog = true
                    }
                    ToolbarIconButton(Icons.Default.Save, "Save") {
                        commitDrag()
                        onSave()
                        hasChanges = false
                    }
                    ToolbarIconButton(Icons.Default.Close, "Close") {
                        if (hasChanges) showCloseConfirm = true
                        else onDismiss()
                    }
                    ToolbarIconButton(Icons.Default.RestartAlt, "Reset") {
                        showResetConfirm = true
                    }
                }
            }
        }

        // ── Bottom toolbar (floating pill) ──────────────────────────────────
        Surface(
            color = Color.Black.copy(alpha = 0.65f),
            shape = toolbarShape,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomToolbarButton(Icons.Default.Palette, "Template", activePanel == Panel.Template) {
                    activePanel = if (activePanel == Panel.Template) null else Panel.Template
                }
                BottomToolbarButton(
                    if (resizeMode == ResizeMode.Fit16x9) Icons.Default.FitScreen else Icons.Default.AspectRatio,
                    "Resize"
                ) {
                    resizeMode = if (resizeMode == ResizeMode.Fit16x9) ResizeMode.Stretch else ResizeMode.Fit16x9
                }
                BottomToolbarButton(Icons.Default.FormatSize, "Size", activePanel == Panel.TextSize) {
                    activePanel = if (activePanel == Panel.TextSize) null else Panel.TextSize
                }
                BottomToolbarButton(Icons.Default.BorderColor, "Outline", activePanel == Panel.Outline) {
                    activePanel = if (activePanel == Panel.Outline) null else Panel.Outline
                }
                BottomToolbarButton(Icons.Default.BlurOn, "Shadow", activePanel == Panel.Shadow) {
                    activePanel = if (activePanel == Panel.Shadow) null else Panel.Shadow
                }
                BottomToolbarButton(Icons.Default.FormatColorFill, "BG", activePanel == Panel.Bg) {
                    activePanel = if (activePanel == Panel.Bg) null else Panel.Bg
                }
                BottomToolbarButton(Icons.Default.FormatColorText, "Color", activePanel == Panel.FontColor) {
                    activePanel = if (activePanel == Panel.FontColor) null else Panel.FontColor
                }
                BottomToolbarButton(Icons.Default.Tune, "Advanced", activePanel == Panel.Advanced) {
                    activePanel = if (activePanel == Panel.Advanced) null else Panel.Advanced
                }
            }
        }

        // ── Subtitle preview (rendered AFTER UI so it's on top visually) ────
        val baseX = fullSettings.horizontalPosition * actualWidth
        val baseY = fullSettings.verticalPosition * actualHeight
        SubtitlePreview(
            settings = fullSettings,
            rotation = fullSettings.rotation,
            offsetX = baseX + dragOffsetX,
            offsetY = baseY + dragOffsetY,
            onDrag = { dx, dy, bw, bh ->
                val bx = fullSettings.horizontalPosition * actualWidth
                val by = fullSettings.verticalPosition * actualHeight
                val newOx = (bx + dragOffsetX + dx).coerceIn(destRect.left + bw / 2f, destRect.right - bw / 2f)
                val newOy = (by + dragOffsetY + dy).coerceIn(destRect.top + bh / 2f, destRect.bottom - bh / 2f)
                dragOffsetX = newOx - bx
                dragOffsetY = newOy - by
            },
            onDragEnd = ::commitDrag,
            onTap = { showRotationWheel = !showRotationWheel },
            showRotateWheel = showRotationWheel,
            onRotate = { fullSettings = fullSettings.copy(rotation = it) },
            modifier = Modifier.fillMaxSize()
        )

        // ── Bottom panels (slide up) ────────────────────────────────────────
        activePanel?.let { panel ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    ) { activePanel = null }
            ) {
                AnimatedVisibility(
                    visible = true,
                    enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(300)),
                    exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(250)),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Surface(
                        color = SubColors.surfacePanel,
                        shape = panelShape,
                        shadowElevation = 24.dp
                    ) {
                        when (panel) {
                            Panel.Template -> TemplatePanel(
                                selectedIndex = selectedTemplateIndex,
                                onSelect = { idx ->
                                    selectedTemplateIndex = idx
                                    activePanel = null
                                },
                                onDismiss = { activePanel = null }
                            )
                            Panel.TextSize -> TextSizePanel(
                                currentSize = fullSettings.fontSize,
                                onSizeChange = { fullSettings = fullSettings.copy(fontSize = it) },
                                onReset = { fullSettings = fullSettings.copy(fontSize = Defaults.FULL_SETTINGS.fontSize) },
                                onDismiss = { activePanel = null }
                            )
                            Panel.Outline -> OutlinePanel(
                                enabled = fullSettings.enableOutline,
                                onToggle = { fullSettings = fullSettings.copy(enableOutline = it) },
                                width = fullSettings.outlineWidth,
                                onWidthChange = { fullSettings = fullSettings.copy(outlineWidth = it) },
                                color = fullSettings.outlineColor,
                                onColorChange = { fullSettings = fullSettings.copy(outlineColor = it) },
                                onResetWidth = { fullSettings = fullSettings.copy(outlineWidth = Defaults.FULL_SETTINGS.outlineWidth) },
                                onResetColor = { fullSettings = fullSettings.copy(outlineColor = Defaults.FULL_SETTINGS.outlineColor) },
                                onDismiss = { activePanel = null }
                            )
                            Panel.Shadow -> ShadowPanel(
                                enabled = fullSettings.enableShadow,
                                onToggle = { fullSettings = fullSettings.copy(enableShadow = it) },
                                blur = fullSettings.shadowBlur,
                                onBlurChange = { fullSettings = fullSettings.copy(shadowBlur = it) },
                                offsetX = fullSettings.shadowOffsetX,
                                onOffsetXChange = { fullSettings = fullSettings.copy(shadowOffsetX = it) },
                                offsetY = fullSettings.shadowOffsetY,
                                onOffsetYChange = { fullSettings = fullSettings.copy(shadowOffsetY = it) },
                                color = fullSettings.shadowColor,
                                onColorChange = { fullSettings = fullSettings.copy(shadowColor = it) },
                                onResetBlur = { fullSettings = fullSettings.copy(shadowBlur = Defaults.FULL_SETTINGS.shadowBlur) },
                                onResetOffsetX = { fullSettings = fullSettings.copy(shadowOffsetX = Defaults.FULL_SETTINGS.shadowOffsetX) },
                                onResetOffsetY = { fullSettings = fullSettings.copy(shadowOffsetY = Defaults.FULL_SETTINGS.shadowOffsetY) },
                                onResetColor = { fullSettings = fullSettings.copy(shadowColor = Defaults.FULL_SETTINGS.shadowColor) },
                                onDismiss = { activePanel = null }
                            )
                            Panel.Bg -> BgPanel(
                                bgColor = fullSettings.backgroundColor,
                                onColorChange = { fullSettings = fullSettings.copy(backgroundColor = it) },
                                onReset = { fullSettings = fullSettings.copy(backgroundColor = Defaults.FULL_SETTINGS.backgroundColor) },
                                onDismiss = { activePanel = null }
                            )
                            Panel.FontColor -> FontColorPanel(
                                currentColor = fullSettings.fontColor,
                                onColorChange = { fullSettings = fullSettings.copy(fontColor = it) },
                                onReset = { fullSettings = fullSettings.copy(fontColor = Defaults.FULL_SETTINGS.fontColor) },
                                onDismiss = { activePanel = null }
                            )
                            Panel.Advanced -> AdvancedPanel(
                                verticalPosition = fullSettings.verticalPosition,
                                onVerticalChange = { fullSettings = fullSettings.copy(verticalPosition = it) },
                                horizontalPosition = fullSettings.horizontalPosition,
                                onHorizontalChange = { fullSettings = fullSettings.copy(horizontalPosition = it) },
                                maxWidthRatio = fullSettings.maxWidthRatio,
                                onMaxWidthChange = { fullSettings = fullSettings.copy(maxWidthRatio = it) },
                                delayMs = fullSettings.delayMs,
                                onDelayChange = { fullSettings = fullSettings.copy(delayMs = it) },
                                rotation = fullSettings.rotation,
                                onRotationChange = { fullSettings = fullSettings.copy(rotation = it) },
                                onResetVertical = { fullSettings = fullSettings.copy(verticalPosition = Defaults.FULL_SETTINGS.verticalPosition) },
                                onResetHorizontal = { fullSettings = fullSettings.copy(horizontalPosition = Defaults.FULL_SETTINGS.horizontalPosition) },
                                onResetMaxWidth = { fullSettings = fullSettings.copy(maxWidthRatio = Defaults.FULL_SETTINGS.maxWidthRatio) },
                                onResetDelay = { fullSettings = fullSettings.copy(delayMs = Defaults.FULL_SETTINGS.delayMs) },
                                onResetRotation = { fullSettings = fullSettings.copy(rotation = Defaults.FULL_SETTINGS.rotation) },
                                onDismiss = { activePanel = null }
                            )
                        }
                    }
                }
            }
        }
    }

    // Confirmation dialogs
    if (showCloseConfirm) {
        ConfirmationOverlay(
            title = "Unsaved changes",
            message = "You have unsaved changes. What would you like to do?",
            confirmLabel = "Save & close",
            onConfirm = {
                commitDrag(); onSave(); hasChanges = false; showCloseConfirm = false; onDismiss()
            },
            dismissLabel = "Discard",
            onDismiss = { showCloseConfirm = false; onDismiss() },
            cancelLabel = "Cancel",
            onCancel = { showCloseConfirm = false }
        )
    }

    if (showResetConfirm) {
        ConfirmationOverlay(
            title = "Reset settings?",
            message = "Restore all subtitle settings to default?",
            confirmLabel = "Reset",
            onConfirm = {
                fullSettings = Defaults.FULL_SETTINGS; showResetConfirm = false
            },
            onDismiss = { showResetConfirm = false }
        )
    }

    if (showRenameDialog) {
        RenameOverlay(
            currentName = renameText,
            onNameChange = { renameText = it },
            onConfirm = {
                if (renameText.isNotBlank()) onRenameProfile(activeProfileIndex, renameText.trim())
                showRenameDialog = false
            },
            onDismiss = { showRenameDialog = false }
        )
    }
}

private enum class Panel {
    Template, TextSize, Outline, Shadow, Bg, FontColor, Advanced
}

// ===========================================================================
// Reusable UI components
// ===========================================================================

@Composable
private fun PanelHeader(
    title: String,
    onDismiss: () -> Unit,
    onReset: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.weight(1f))
        onReset?.let {
            ResetTextButton(onClick = it)
        }
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Close",
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun ResetTextButton(onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(
            Icons.Default.Refresh,
            contentDescription = "Reset",
            modifier = Modifier.size(14.dp),
            tint = Color.White.copy(alpha = 0.5f)
        )
        Spacer(Modifier.width(4.dp))
        Text("Reset", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
    }
}

@Composable
private fun SectionDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(1.dp)
            .background(SubColors.divider)
    )
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 14.sp
        )
        Spacer(Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = SubColors.accent,
                checkedTrackColor = SubColors.accentGlow,
                uncheckedThumbColor = Color.White.copy(alpha = 0.3f),
                uncheckedTrackColor = Color.White.copy(alpha = 0.08f)
            )
        )
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    displayValue: String
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = displayValue,
                color = SubColors.accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = SubColors.accent,
                activeTrackColor = SubColors.accent,
                inactiveTrackColor = Color.White.copy(alpha = 0.08f)
            )
        )
    }
}

@Composable
private fun ToolbarIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
        Icon(
            icon,
            contentDescription = label,
            tint = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun BottomToolbarButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    val bgColor = if (isActive) SubColors.accent.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.04f)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (isActive) SubColors.accent else Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                color = if (isActive) SubColors.accent else Color.White.copy(alpha = 0.5f),
                fontSize = 9.sp,
                fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun ColorSwatchRow(
    colors: List<Color>,
    selectedColor: Long,
    onColorSelect: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        colors.forEach { c ->
            val isSelected = selectedColor == c.toArgb().toLong()
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(c)
                    .then(
                        if (isSelected) Modifier.border(2.dp, SubColors.accent, CircleShape)
                        else Modifier.border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                    )
                    .clickable { onColorSelect(c.toArgb().toLong()) },
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = if (c == Color.Black || c == Color(0xFF333333) || c == Color(0xFF555555)) Color.White else Color.Black,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeColorItem(
    name: String,
    colors: List<Color>,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val bgBrush = if (colors.size == 1) Brush.verticalGradient(listOf(colors[0], colors[0]))
                      else Brush.verticalGradient(colors)
        val borderMod = if (isSelected) Modifier.border(2.dp, SubColors.accent, RoundedCornerShape(12.dp))
                        else Modifier.border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(bgBrush)
                .then(borderMod)
                .clickable(onClick = onClick)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = name,
            color = if (isSelected) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.45f),
            fontSize = 10.sp,
            maxLines = 1
        )
    }
}

// ===========================================================================
// PANELS
// ===========================================================================

@Composable
private fun TemplatePanel(
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
private fun TextSizePanel(
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
private fun OutlinePanel(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    width: Float,
    onWidthChange: (Float) -> Unit,
    color: Long,
    onColorChange: (Long) -> Unit,
    onResetWidth: () -> Unit,
    onResetColor: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
    ) {
        PanelHeader(title = "Outline", onDismiss = onDismiss)
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
private fun ShadowPanel(
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
        PanelHeader(title = "Shadow", onDismiss = onDismiss)
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
private fun BgPanel(
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
                            if (isSelected) Modifier.border(2.dp, SubColors.accent, CircleShape)
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
private fun FontColorPanel(
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
private fun AdvancedPanel(
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
        PanelHeader(title = "Advanced Settings", onDismiss = onDismiss)
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
                    color = if (rotation == ang) SubColors.accent.copy(alpha = 0.25f)
                    else Color.White.copy(alpha = 0.06f)
                ) {
                    Text(
                        text = "${ang.toInt()}°",
                        color = if (rotation == ang) SubColors.accent else Color.White.copy(alpha = 0.6f),
                        fontSize = 13.sp,
                        fontWeight = if (rotation == ang) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

// ===========================================================================
// CONFIRMATION / RENAME OVERLAYS
// ===========================================================================
@Composable
private fun ConfirmationOverlay(
    title: String,
    message: String,
    confirmLabel: String = "Confirm",
    onConfirm: () -> Unit,
    dismissLabel: String? = "Cancel",
    onDismiss: () -> Unit,
    cancelLabel: String? = null,
    onCancel: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(
                indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            ) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = SubColors.surfacePanel,
            shape = cardShape,
            shadowElevation = 32.dp,
            modifier = Modifier.padding(32.dp).width(320.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = message,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (dismissLabel != null) {
                        TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                            Text(dismissLabel, color = Color.White.copy(alpha = 0.6f))
                        }
                    }
                    if (cancelLabel != null && onCancel != null) {
                        TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                            Text(cancelLabel, color = Color.White.copy(alpha = 0.6f))
                        }
                    }
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = SubColors.accent),
                        shape = chipShape
                    ) {
                        Text(confirmLabel, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun RenameOverlay(
    currentName: String,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(
                indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            ) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = SubColors.surfacePanel,
            shape = cardShape,
            shadowElevation = 32.dp,
            modifier = Modifier.padding(32.dp).width(320.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "Rename Profile",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = currentName,
                    onValueChange = onNameChange,
                    label = { Text("Profile name", color = Color.White.copy(alpha = 0.5f)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(color = Color.White),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SubColors.accent,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                        cursorColor = SubColors.accent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Cancel", color = Color.White.copy(alpha = 0.6f))
                    }
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = SubColors.accent),
                        shape = chipShape
                    ) {
                        Text("Rename", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

// ===========================================================================
// Subtitle Preview
// ===========================================================================
@Composable
private fun SubtitlePreview(
    settings: SubtitleFullSettings,
    rotation: Float,
    offsetX: Float,
    offsetY: Float,
    onDrag: (Float, Float, Int, Int) -> Unit,
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
        ) {
            Box(
                modifier = Modifier
                    .pointerInput(Unit) {
                        detectTapGestures { onTap() }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = onDragEnd,
                            onDragCancel = onDragEnd,
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onDrag(dragAmount.x, dragAmount.y, boxWidth, boxHeight)
                            }
                        )
                    }
            ) {
                Box(
                    modifier = Modifier
                        .graphicsLayer { rotationZ = rotation }
                        .background(
                            if (settings.backgroundColor == 0x00000000L) Color.Transparent else bgColor,
                            RoundedCornerShape(4.dp)
                        )
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        if (settings.enableOutline) {
                            Text(
                                text = LOREM_IPSUM,
                                color = outlineColor,
                                fontSize = settings.fontSize.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                style = TextStyle(shadow = Shadow(color = outlineColor, offset = Offset.Zero, blurRadius = settings.outlineWidth))
                            )
                        }
                        Text(
                            text = LOREM_IPSUM,
                            color = textColor,
                            fontSize = settings.fontSize.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            style = TextStyle(shadow = dropShadow)
                        )
                    }
                }

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

// ===========================================================================
// Rotation Wheel
// ===========================================================================
@Composable
private fun RotationWheel(
    currentAngle: Float,
    onAngleChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var center by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.15f), CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset -> center = Offset(size.width / 2f, size.height / 2f) },
                    onDrag = { change, _ ->
                        change.consume()
                        val touchPos = change.position
                        val delta = touchPos - center
                        val angle = Math.toDegrees(atan2(delta.y.toDouble(), delta.x.toDouble())).toFloat()
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
                color = Color.White.copy(alpha = 0.4f),
                radius = radius,
                center = Offset(cx, cy),
                style = Stroke(width = 2.dp.toPx())
            )
            drawLine(
                color = SubColors.accent,
                start = Offset(cx, cy),
                end = Offset(lineEndX, lineEndY),
                strokeWidth = 2.dp.toPx()
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.RotateRight,
            contentDescription = "Rotate",
            modifier = Modifier.size(16.dp),
            tint = Color.White.copy(alpha = 0.6f)
        )
    }
}

// ===========================================================================
// Color Picker
// ===========================================================================
@Composable
private fun ImmediateColorPickerContent(
    initialColor: Color,
    onColorChange: (Color) -> Unit,
) {
    var hue by remember { mutableFloatStateOf(0f) }
    var saturation by remember { mutableFloatStateOf(1f) }
    var lightness by remember { mutableFloatStateOf(0.5f) }
    var alpha by remember { mutableFloatStateOf(initialColor.alpha) }

    LaunchedEffect(Unit) {
        val hsl = rgbToHsl(initialColor)
        hue = hsl[0]; saturation = hsl[1]; lightness = hsl[2]; alpha = hsl[3]
    }

    val currentColor = remember(hue, saturation, lightness, alpha) {
        Color.hsl(hue, saturation, lightness, alpha)
    }

    Column(
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val paletteSize = 220.dp
        Box(
            modifier = Modifier
                .size(paletteSize)
                .clip(RoundedCornerShape(12.dp))
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val xFraction = (offset.x / size.width).coerceIn(0f, 1f)
                        val yFraction = (offset.y / size.height).coerceIn(0f, 1f)
                        val newHue = xFraction * 360f
                        val newLight = 1f - yFraction
                        hue = newHue; saturation = 1f; lightness = newLight
                        onColorChange(Color.hsl(newHue, 1f, newLight, alpha))
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

        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(currentColor)
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
            )
            Spacer(Modifier.width(12.dp))
            Slider(
                value = alpha,
                onValueChange = { alpha = it; onColorChange(Color.hsl(hue, saturation, lightness, it)) },
                valueRange = 0f..1f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = SubColors.accent,
                    activeTrackColor = SubColors.accent.copy(alpha = 0.7f),
                    inactiveTrackColor = Color.White.copy(alpha = 0.08f)
                )
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "${(alpha * 100).toInt()}%",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.width(36.dp),
                textAlign = TextAlign.End
            )
        }

        Spacer(Modifier.height(8.dp))

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
                    hue = hsl[0]; saturation = hsl[1]; lightness = hsl[2]
                    onColorChange(newColor)
                }
            },
            label = { Text("Hex", color = Color.White.copy(alpha = 0.5f)) },
            placeholder = { Text("RRGGBB", color = Color.White.copy(alpha = 0.2f)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = SubColors.accent,
                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                cursorColor = SubColors.accent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedLabelColor = SubColors.accent,
                unfocusedLabelColor = Color.White.copy(alpha = 0.4f)
            )
        )
    }
}

// ===========================================================================
// Utility: RGB → HSL
// ===========================================================================
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

private data class RectF(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun width() = right - left
    fun height() = bottom - top
}
