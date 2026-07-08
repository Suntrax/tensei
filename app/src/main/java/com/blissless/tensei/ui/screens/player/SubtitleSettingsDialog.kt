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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.blissless.tensei.data.models.SubtitleSettings
import kotlin.math.roundToInt

internal const val LOREM_IPSUM = "The quick brown fox jumps over the lazy dog.\nThis is a second line for testing."

internal data class TemplateBg(val name: String, val colors: List<Color>)

internal val TEMPLATES = listOf(
    TemplateBg("Dark Scene", listOf(Color(0xFF1a1a2e), Color(0xFF16213e))),
    TemplateBg("Daylight", listOf(Color(0xFF87CEEB), Color(0xFF98D8C8))),
    TemplateBg("Sunset", listOf(Color(0xFFFF6B35), Color(0xFFFFD93D))),
    TemplateBg("Forest", listOf(Color(0xFF2D5016), Color(0xFF4A7C2E))),
    TemplateBg("Night Sky", listOf(Color(0xFF0a0a2e), Color(0xFF1a1a4e))),
    TemplateBg("Beach", listOf(Color(0xFF0077B6), Color(0xFFF4A261))),
    TemplateBg("Solid Black", listOf(Color.Black)),
    TemplateBg("Solid White", listOf(Color.White)),
)

internal val OUTLINE_PRESETS = listOf(
    Color.Black, Color.White, Color.Red, Color(0xFFFF8C00),
    Color.Yellow, Color.Green, Color.Blue, Color.Magenta
)

internal val SHADOW_PRESETS = listOf(
    Color.Black, Color.White, Color(0xFF333333), Color(0xFF555555), Color(0xFF777777)
)

internal val BG_SUB_PRESETS = listOf(
    0x00000000L, 0x40000000L, 0x80000000L, 0xC0000000L,
    0x40FFFFFFL, 0x80FFFFFFL, 0xFF000000L, 0xFFFFFFFFL
)

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
    val fontFamily: String = "Default",
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
        fontFamily = fontFamily,
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

// Dark surface for video overlay panels (always dark regardless of theme)
private val PanelSurface = Color(0xFF121220)
// PanelDivider moved to SubtitleSettingsComponents.kt (internal)

private val panelShape = RoundedCornerShape(20.dp, 20.dp, 0.dp, 0.dp)
private val cardShape = RoundedCornerShape(14.dp)
internal val chipShape = RoundedCornerShape(10.dp)

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
            shadowBlur = currentSettings.shadowBlur,
            shadowOffsetX = currentSettings.shadowOffsetX,
            shadowOffsetY = currentSettings.shadowOffsetY,
            shadowColor = currentSettings.shadowColor,
            backgroundColor = currentSettings.backgroundColor,
            verticalPosition = currentSettings.verticalPosition,
            horizontalPosition = currentSettings.horizontalPosition,
            maxWidthRatio = currentSettings.maxWidthRatio,
            delayMs = currentSettings.delayMs,
            fontFamily = currentSettings.fontFamily,
            profileName = currentSettings.profileName
        )
    ) }

    var selectedTemplateIndex by remember { mutableIntStateOf(0) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var showCloseConfirm by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var hasChanges by remember { mutableStateOf(false) }
    var showSaved by remember { mutableStateOf(false) }
    var activePanel by remember { mutableStateOf<Panel?>(null) }

    LaunchedEffect(showSaved) { if (showSaved) { kotlinx.coroutines.delay(2000); showSaved = false } }

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
            shadowBlur = p.shadowBlur,
            shadowOffsetX = p.shadowOffsetX,
            shadowOffsetY = p.shadowOffsetY,
            shadowColor = p.shadowColor,
            backgroundColor = p.backgroundColor,
            verticalPosition = p.verticalPosition,
            horizontalPosition = p.horizontalPosition,
            maxWidthRatio = p.maxWidthRatio,
            delayMs = p.delayMs,
            fontFamily = p.fontFamily,
            profileName = p.profileName
        )
        dragOffsetX = 0f
        dragOffsetY = 0f
        hasChanges = false
    }

    // Helper: mark state as user-changed
    fun uiChange(block: () -> Unit) {
        block()
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
            hasChanges = true
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
            val dest = run {
                val curr = cw / ch
                if (curr > aspect) {
                    val w = ch * aspect
                    RectF((cw - w) / 2, 0f, (cw + w) / 2, ch)
                } else {
                    val h = cw / aspect
                    RectF(0f, (ch - h) / 2, cw, (ch + h) / 2)
                }
            }
            drawRect(
                brush = gradient,
                topLeft = Offset(dest.left, dest.top),
                size = Size(dest.width(), dest.height())
            )
        }

        val destRect = remember(actualWidth, actualHeight) {
            val cw = actualWidth.toFloat()
            val ch = actualHeight.toFloat()
            val aspect = 16f / 9f
            val curr = cw / ch
            if (curr > aspect) {
                val w = ch * aspect
                RectF((cw - w) / 2, 0f, (cw + w) / 2, ch)
            } else {
                val h = cw / aspect
                RectF(0f, (ch - h) / 2, cw, (ch + h) / 2)
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
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
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

                ToolbarIconButton(Icons.Default.Edit, "Rename") {
                    renameText = profiles.getOrNull(activeProfileIndex)?.profileName ?: ""
                    showRenameDialog = true
                }
                ToolbarIconButton(if (showSaved) Icons.Default.Check else Icons.Default.Save, "Save") {
                    if (!showSaved) { commitDrag(); onSave(); hasChanges = false; showSaved = true }
                }
                ToolbarIconButton(Icons.Default.Close, "Close") {
                    if (hasChanges) showCloseConfirm = true
                    else onDismiss()
                }
                ToolbarIconButton(Icons.Default.RestartAlt, "Reset") {
                    showResetConfirm = true
                }

                Spacer(Modifier.weight(1f))

                BottomToolbarButton(Icons.Default.Palette, "Template", activePanel == Panel.Template) {
                    activePanel = if (activePanel == Panel.Template) null else Panel.Template
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
                BottomToolbarButton(Icons.Default.Tune, "Font", activePanel == Panel.FontFamily) {
                    activePanel = if (activePanel == Panel.FontFamily) null else Panel.FontFamily
                }
            }
        }

        // ── Subtitle preview (rendered AFTER UI so it's on top visually) ────
        val baseX = fullSettings.horizontalPosition * actualWidth
        val baseY = fullSettings.verticalPosition * actualHeight
        SubtitlePreview(
            settings = fullSettings,
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
            modifier = Modifier.fillMaxSize()
        )

        // ── Bottom panels (slide up) ────────────────────────────────────────
        activePanel?.let { panel ->
            Box(modifier = Modifier.fillMaxSize()) {
                AnimatedVisibility(
                    visible = true,
                    enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(300)),
                    exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(250)),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Surface(
                        color = PanelSurface,
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
                                onSizeChange = { uiChange { fullSettings = fullSettings.copy(fontSize = it) } },
                                onReset = { uiChange { fullSettings = fullSettings.copy(fontSize = Defaults.FULL_SETTINGS.fontSize) } },
                                onDismiss = { activePanel = null }
                            )
                            Panel.Outline -> OutlinePanel(
                                enabled = fullSettings.enableOutline,
                                onToggle = { uiChange { fullSettings = fullSettings.copy(enableOutline = it, outlineWidth = 2f) } },
                                color = fullSettings.outlineColor,
                                onColorChange = { uiChange { fullSettings = fullSettings.copy(outlineColor = it) } },
                                onResetAll = { uiChange { fullSettings = fullSettings.copy(enableOutline = true, outlineWidth = 2f, outlineColor = Defaults.FULL_SETTINGS.outlineColor) } },
                                onResetColor = { uiChange { fullSettings = fullSettings.copy(outlineColor = Defaults.FULL_SETTINGS.outlineColor) } },
                                onDismiss = { activePanel = null }
                            )
                            Panel.Shadow -> ShadowPanel(
                                enabled = fullSettings.enableShadow,
                                onToggle = { uiChange { fullSettings = fullSettings.copy(enableShadow = it) } },
                                blur = fullSettings.shadowBlur,
                                onBlurChange = { uiChange { fullSettings = fullSettings.copy(shadowBlur = it) } },
                                offsetX = fullSettings.shadowOffsetX,
                                onOffsetXChange = { uiChange { fullSettings = fullSettings.copy(shadowOffsetX = it) } },
                                offsetY = fullSettings.shadowOffsetY,
                                onOffsetYChange = { uiChange { fullSettings = fullSettings.copy(shadowOffsetY = it) } },
                                color = fullSettings.shadowColor,
                                onColorChange = { uiChange { fullSettings = fullSettings.copy(shadowColor = it) } },
                                onResetAll = { uiChange { fullSettings = fullSettings.copy(enableShadow = false, shadowBlur = Defaults.FULL_SETTINGS.shadowBlur, shadowOffsetX = Defaults.FULL_SETTINGS.shadowOffsetX, shadowOffsetY = Defaults.FULL_SETTINGS.shadowOffsetY, shadowColor = Defaults.FULL_SETTINGS.shadowColor) } },
                                onResetBlur = { uiChange { fullSettings = fullSettings.copy(shadowBlur = Defaults.FULL_SETTINGS.shadowBlur) } },
                                onResetOffsetX = { uiChange { fullSettings = fullSettings.copy(shadowOffsetX = Defaults.FULL_SETTINGS.shadowOffsetX) } },
                                onResetOffsetY = { uiChange { fullSettings = fullSettings.copy(shadowOffsetY = Defaults.FULL_SETTINGS.shadowOffsetY) } },
                                onResetColor = { uiChange { fullSettings = fullSettings.copy(shadowColor = Defaults.FULL_SETTINGS.shadowColor) } },
                                onDismiss = { activePanel = null }
                            )
                            Panel.Bg -> BgPanel(
                                bgColor = fullSettings.backgroundColor,
                                onColorChange = { uiChange { fullSettings = fullSettings.copy(backgroundColor = it) } },
                                onReset = { uiChange { fullSettings = fullSettings.copy(backgroundColor = Defaults.FULL_SETTINGS.backgroundColor) } },
                                onDismiss = { activePanel = null }
                            )
                            Panel.FontColor -> FontColorPanel(
                                currentColor = fullSettings.fontColor,
                                onColorChange = { uiChange { fullSettings = fullSettings.copy(fontColor = it) } },
                                onReset = { uiChange { fullSettings = fullSettings.copy(fontColor = Defaults.FULL_SETTINGS.fontColor) } },
                                onDismiss = { activePanel = null }
                            )
                            Panel.FontFamily -> FontFamilyPanel(
                                currentFont = fullSettings.fontFamily,
                                onFontChange = { uiChange { fullSettings = fullSettings.copy(fontFamily = it) } },
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
    Template, TextSize, Outline, Shadow, Bg, FontColor, FontFamily
}

// Reusable UI components moved to SubtitleSettingsComponents.kt
// (PanelHeader, ResetTextButton, SectionDivider, ToggleRow, SliderRow,
//  ToolbarIconButton, BottomToolbarButton, ColorSwatchRow, ThemeColorItem)

// Panels moved to SubtitleSettingsPanels.kt
// (TemplatePanel, TextSizePanel, OutlinePanel, ShadowPanel, BgPanel,
//  FontColorPanel, FontFamilyPanel)

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
            color = PanelSurface,
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
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
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
            color = PanelSurface,
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
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                        cursorColor = MaterialTheme.colorScheme.primary,
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
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
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
    offsetX: Float,
    offsetY: Float,
    onDrag: (Float, Float, Int, Int) -> Unit,
    onDragEnd: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val textColor = if (settings.fontColor == 0x00000000L) Color.White else Color(settings.fontColor)
    val bgColor = Color(settings.backgroundColor)
    val outlineColor = Color(settings.outlineColor)

    val fontFamily = when (settings.fontFamily) {
        "Serif" -> FontFamily.Serif
        "Monospace" -> FontFamily.Monospace
        "Sans Serif Light" -> FontFamily.SansSerif
        "Cursive" -> FontFamily.Cursive
        else -> FontFamily.SansSerif
    }
    val fontWeight = if (settings.fontFamily == "Sans Serif Light") FontWeight.Light else FontWeight.Normal

    var boxWidth by remember { mutableIntStateOf(0) }
    var boxHeight by remember { mutableIntStateOf(0) }
    val textMeasurer = rememberTextMeasurer()
    val defaultStyle = LocalTextStyle.current
    val shadowColor = Color(settings.shadowColor).copy(alpha = 0.35f)
    val shadowOffsetPx = with(LocalDensity.current) { Offset(settings.shadowOffsetX.dp.toPx(), settings.shadowOffsetY.dp.toPx()) }
    val outlineWidthPx = with(LocalDensity.current) { settings.outlineWidth.dp.toPx() }

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
                        .background(
                            if (settings.backgroundColor == 0x00000000L) Color.Transparent else bgColor,
                            RoundedCornerShape(4.dp)
                        )
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = LOREM_IPSUM,
                            color = textColor,
                            fontSize = settings.fontSize.sp,
                            fontFamily = fontFamily,
                            fontWeight = fontWeight,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            modifier = Modifier.drawWithContent {
                                val textWidth = size.width.toInt()
                                val base = defaultStyle.merge(
                                    TextStyle(
                                        fontSize = settings.fontSize.sp,
                                        fontFamily = fontFamily,
                                        fontWeight = fontWeight,
                                        textAlign = TextAlign.Center
                                    )
                                )
                                if (settings.enableShadow) {
                                    val shadowLayout = textMeasurer.measure(
                                        text = AnnotatedString(LOREM_IPSUM),
                                        style = base.copy(color = shadowColor),
                                        constraints = Constraints(maxWidth = textWidth),
                                        maxLines = 2
                                    )
                                    drawText(shadowLayout, topLeft = shadowOffsetPx)
                                }
                                if (settings.enableOutline) {
                                    val outlineLayout = textMeasurer.measure(
                                        text = AnnotatedString(LOREM_IPSUM),
                                        style = base.copy(
                                            color = outlineColor,
                                            drawStyle = Stroke(width = outlineWidthPx)
                                        ),
                                        constraints = Constraints(maxWidth = textWidth),
                                        maxLines = 2
                                    )
                                    drawText(outlineLayout)
                                }
                                drawContent()
                            }
                        )
                    }
                }
            }
        }
    }
}

// ===========================================================================
// Color Picker
// ===========================================================================
@Composable
internal fun ImmediateColorPickerContent(
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
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
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
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                cursorColor = MaterialTheme.colorScheme.primary,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
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
