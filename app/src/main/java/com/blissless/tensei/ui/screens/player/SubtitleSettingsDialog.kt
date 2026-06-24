package com.blissless.tensei.ui.screens.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.blissless.tensei.data.models.SubtitleSettings
import kotlin.math.*

private val LOREM_IPSUM = "The quick brown fox jumps over the lazy dog.\nThis is a second line for testing."

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

private val EXTENDED_COLORS = listOf(
    "White" to 0xFFFFFFFFL,
    "Black" to 0xFF000000L,
    "Red" to 0xFFFF0000L,
    "Lime" to 0xFF00FF00L,
    "Blue" to 0xFF0000FFL,
    "Yellow" to 0xFFFFFF00L,
    "Cyan" to 0xFF00FFFFL,
    "Magenta" to 0xFFFF00FFL,
    "Orange" to 0xFFFF8C00L,
    "Purple" to 0xFF800080L,
    "Brown" to 0xFFA52A2AL,
    "Gray" to 0xFF808080L,
    "Pink" to 0xFFFFC0CBL,
    "SpringGreen" to 0xFF00FF7FL,
    "BlueViolet" to 0xFF8A2BE2L,
    "Chocolate" to 0xFFD2691EL,
)

private val BG_COLOR_PRESETS = listOf(
    "None" to 0x00000000L,
    "Black 25%" to 0x40000000L,
    "Black 50%" to 0x80000000L,
    "Black 75%" to 0xC0000000L,
    "White 25%" to 0x40FFFFFFL,
    "White 50%" to 0x80FFFFFFL,
)

enum class ResizeMode { Fill, Fit, Stretch }

// Local RectF for background scaling
private data class RectF(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun width() = right - left
    fun height() = bottom - top
}

// Default values for per‑setting reset
private object Defaults {
    const val FONT_SIZE = 16f
    const val ENABLE_SHADOW = true
    const val ENABLE_OUTLINE = false
    const val OUTLINE_WIDTH = 2f
    const val OUTLINE_COLOR: Long = 0xFF000000L
    const val FONT_COLOR: Long = 0xFFFFFFFFL
    const val BACKGROUND_COLOR: Long = 0x00000000L
    const val VERTICAL_POS = 0.85f
    const val HORIZONTAL_POS = 0.5f
    const val MAX_WIDTH = 0.8f
    const val DELAY_MS = 0
}

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
    // Full‑screen, non‑dismissible dialog
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)   // behind the template
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
}

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
    var selectedTemplateIndex by remember { mutableIntStateOf(0) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var rotation by remember { mutableFloatStateOf(0f) }
    var lockRotation by remember { mutableStateOf(true) }
    var resizeMode by remember { mutableStateOf(ResizeMode.Stretch) }

    // Sheets / dialogs visibility
    var showProfileSheet by remember { mutableStateOf(false) }
    var showOutlineSheet by remember { mutableStateOf(false) }
    var showShadowSheet by remember { mutableStateOf(false) }
    var showBgSheet by remember { mutableStateOf(false) }
    var showTextSizeSheet by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showPositionSheet by remember { mutableStateOf(false) }
    var showDelaySheet by remember { mutableStateOf(false) }
    var showRotationSheet by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var showTemplatePicker by remember { mutableStateOf(false) }
    var showFullColorPicker by remember { mutableStateOf<((Long) -> Unit)?>(null) }

    var actualWidth by remember { mutableIntStateOf(1080) }
    var actualHeight by remember { mutableIntStateOf(1920) }

    LaunchedEffect(currentSettings) { onSave() }

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
        // ====== BACKGROUND WITH PROPER 16:9 SCALING ======
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val targetAspect = 16f / 9f
            val currentAspect = canvasWidth / canvasHeight

            val destRect = when (resizeMode) {
                ResizeMode.Fill -> {
                    if (currentAspect > targetAspect) {
                        val scale = canvasHeight / (canvasWidth / targetAspect)
                        val w = canvasWidth * scale
                        val h = canvasHeight
                        RectF((canvasWidth - w) / 2, 0f, (canvasWidth + w) / 2, canvasHeight)
                    } else {
                        val scale = canvasWidth / (canvasHeight * targetAspect)
                        val w = canvasWidth
                        val h = canvasHeight * scale
                        RectF(0f, (canvasHeight - h) / 2, canvasWidth, (canvasHeight + h) / 2)
                    }
                }
                ResizeMode.Fit -> {
                    if (currentAspect > targetAspect) {
                        val h = canvasHeight
                        val w = h * targetAspect
                        RectF((canvasWidth - w) / 2, 0f, (canvasWidth + w) / 2, canvasHeight)
                    } else {
                        val w = canvasWidth
                        val h = w / targetAspect
                        RectF(0f, (canvasHeight - h) / 2, canvasWidth, (canvasHeight + h) / 2)
                    }
                }
                ResizeMode.Stretch -> {
                    RectF(0f, 0f, canvasWidth, canvasHeight)
                }
            }
            drawRect(
                brush = gradient,
                size = Size(destRect.width(), destRect.height()),
                topLeft = Offset(destRect.left, destRect.top)
            )
        }

        // Subtitle overlay
        val baseX = currentSettings.horizontalPosition * actualWidth
        val baseY = currentSettings.verticalPosition * actualHeight
        val totalOffsetX = baseX + dragOffsetX
        val totalOffsetY = baseY + dragOffsetY

        SubtitlePreview(
            settings = currentSettings,
            rotation = rotation,
            offsetX = totalOffsetX,
            offsetY = totalOffsetY,
            onDrag = { dx, dy ->
                dragOffsetX += dx
                dragOffsetY += dy
            },
            modifier = Modifier.fillMaxSize()
        )

        // ----- Top‑left: Profile button -----
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .statusBarsPadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .clickable { showProfileSheet = true }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = profiles.getOrNull(activeProfileIndex)?.profileName ?: "Profile $activeProfileIndex",
                    color = Color.White.copy(alpha = 0.9f),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // ----- Top‑right: Control buttons -----
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .statusBarsPadding(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Template background picker
            IconButton(
                onClick = { showTemplatePicker = true },
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), CircleShape)
            ) { Icon(Icons.Default.Palette, "Templates", tint = MaterialTheme.colorScheme.onSurface) }

            // Resize mode
            IconButton(
                onClick = {
                    resizeMode = when (resizeMode) {
                        ResizeMode.Fill -> ResizeMode.Fit
                        ResizeMode.Fit -> ResizeMode.Stretch
                        ResizeMode.Stretch -> ResizeMode.Fill
                    }
                },
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), CircleShape)
            ) {
                Icon(
                    when (resizeMode) {
                        ResizeMode.Fill -> Icons.Default.Crop
                        ResizeMode.Fit -> Icons.Default.FitScreen
                        ResizeMode.Stretch -> Icons.Default.AspectRatio
                    }, "Resize", tint = MaterialTheme.colorScheme.onSurface
                )
            }

            // Rotation quick preset menu
            IconButton(
                onClick = { showRotationSheet = true },
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), CircleShape)
            ) { Icon(Icons.Default.RotateRight, "Rotation", tint = MaterialTheme.colorScheme.onSurface) }

            // Text size
            IconButton(
                onClick = { showTextSizeSheet = true },
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), CircleShape)
            ) { Icon(Icons.Default.FormatSize, "Size", tint = MaterialTheme.colorScheme.onSurface) }

            // Outline
            IconButton(
                onClick = { showOutlineSheet = true },
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), CircleShape)
            ) { Icon(Icons.Default.BorderColor, "Outline", tint = MaterialTheme.colorScheme.onSurface) }

            // Shadow
            IconButton(
                onClick = { showShadowSheet = true },
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), CircleShape)
            ) { Icon(Icons.Default.BlurOn, "Shadow", tint = MaterialTheme.colorScheme.onSurface) }

            // Subtitle background
            IconButton(
                onClick = { showBgSheet = true },
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), CircleShape)
            ) { Icon(Icons.Default.FormatColorFill, "BG", tint = MaterialTheme.colorScheme.onSurface) }

            // Text color → full color picker
            IconButton(
                onClick = {
                    showFullColorPicker = { newColor ->
                        onSettingsChange(currentSettings.copy(fontColor = newColor))
                        showFullColorPicker = null
                    }
                },
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), CircleShape)
            ) { Icon(Icons.Default.FormatColorText, "Text Color", tint = MaterialTheme.colorScheme.onSurface) }

            // More menu
            Box {
                IconButton(
                    onClick = { showMoreMenu = true },
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), CircleShape)
                ) { Icon(Icons.Default.MoreVert, "More", tint = MaterialTheme.colorScheme.onSurface) }
                DropdownMenu(
                    expanded = showMoreMenu,
                    onDismissRequest = { showMoreMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Position & Size") },
                        onClick = {
                            showMoreMenu = false
                            showPositionSheet = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delay") },
                        onClick = {
                            showMoreMenu = false
                            showDelaySheet = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Rotation") },
                        onClick = {
                            showMoreMenu = false
                            showRotationSheet = true
                        }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Reset all to default") },
                        onClick = {
                            showMoreMenu = false
                            showResetConfirm = true
                        }
                    )
                }
            }

            // Close
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), CircleShape)
            ) { Icon(Icons.Default.Close, "Close", tint = MaterialTheme.colorScheme.onSurface) }
        }

        // Reset drag offset
        IconButton(
            onClick = { dragOffsetX = 0f; dragOffsetY = 0f },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
                .size(36.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), CircleShape)
        ) { Icon(Icons.Default.Refresh, "Reset drag", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp)) }
    }

    // ========== MODAL SHEETS ==========

    // Profile selection sheet
    if (showProfileSheet) {
        ModalBottomSheet(
            onDismissRequest = { showProfileSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Choose Profile", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                ProfileSelector(
                    profiles = profiles,
                    activeIndex = activeProfileIndex,
                    onSelect = {
                        onProfileSelect(it)
                        showProfileSheet = false
                    },
                    onRename = onRenameProfile
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Outline sheet
    if (showOutlineSheet) {
        ModalBottomSheet(
            onDismissRequest = { showOutlineSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                Text("Outline", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Enable", modifier = Modifier.weight(1f))
                    Switch(
                        checked = currentSettings.enableOutline,
                        onCheckedChange = { onSettingsChange(currentSettings.copy(enableOutline = it)) }
                    )
                }
                if (currentSettings.enableOutline) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Width: ${currentSettings.outlineWidth.toInt()}px")
                    Slider(
                        value = currentSettings.outlineWidth,
                        onValueChange = { onSettingsChange(currentSettings.copy(outlineWidth = it)) },
                        valueRange = 1f..6f,
                        steps = 4
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Color")
                    Button(
                        onClick = {
                            showFullColorPicker = { newColor ->
                                onSettingsChange(currentSettings.copy(outlineColor = newColor))
                                showFullColorPicker = null
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Colorize, null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pick Color")
                    }
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(Color(currentSettings.outlineColor), CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        onSettingsChange(
                            currentSettings.copy(
                                enableOutline = Defaults.ENABLE_OUTLINE,
                                outlineWidth = Defaults.OUTLINE_WIDTH,
                                outlineColor = Defaults.OUTLINE_COLOR
                            )
                        )
                        showOutlineSheet = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Reset to default") }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Shadow sheet
    if (showShadowSheet) {
        ModalBottomSheet(
            onDismissRequest = { showShadowSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Shadow", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Enable", modifier = Modifier.weight(1f))
                    Switch(
                        checked = currentSettings.enableShadow,
                        onCheckedChange = { onSettingsChange(currentSettings.copy(enableShadow = it)) }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        onSettingsChange(currentSettings.copy(enableShadow = Defaults.ENABLE_SHADOW))
                        showShadowSheet = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Reset to default") }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Subtitle background sheet
    if (showBgSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBgSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                Text("Subtitle Background", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Color & Opacity")
                Button(
                    onClick = {
                        showFullColorPicker = { newColor ->
                            onSettingsChange(currentSettings.copy(backgroundColor = newColor))
                            showFullColorPicker = null
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Colorize, null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Pick Color")
                }
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(Color(currentSettings.backgroundColor), CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        onSettingsChange(currentSettings.copy(backgroundColor = Defaults.BACKGROUND_COLOR))
                        showBgSheet = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Reset to default") }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Text size sheet
    if (showTextSizeSheet) {
        ModalBottomSheet(
            onDismissRequest = { showTextSizeSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Text Size", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("${currentSettings.fontSize.toInt()} sp")
                Slider(
                    value = currentSettings.fontSize,
                    onValueChange = { onSettingsChange(currentSettings.copy(fontSize = it)) },
                    valueRange = 10f..48f,
                    steps = 37
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        onSettingsChange(currentSettings.copy(fontSize = Defaults.FONT_SIZE))
                        showTextSizeSheet = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Reset to default") }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Position & size sheet
    if (showPositionSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPositionSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                Text("Position & Size", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Vertical: ${(currentSettings.verticalPosition * 100).toInt()}%")
                Slider(
                    value = currentSettings.verticalPosition,
                    onValueChange = { onSettingsChange(currentSettings.copy(verticalPosition = it)) },
                    valueRange = 0.05f..0.95f
                )
                Text("Horizontal: ${(currentSettings.horizontalPosition * 100).toInt()}%")
                Slider(
                    value = currentSettings.horizontalPosition,
                    onValueChange = { onSettingsChange(currentSettings.copy(horizontalPosition = it)) },
                    valueRange = 0.05f..0.95f
                )
                Text("Max Width: ${(currentSettings.maxWidthRatio * 100).toInt()}%")
                Slider(
                    value = currentSettings.maxWidthRatio,
                    onValueChange = { onSettingsChange(currentSettings.copy(maxWidthRatio = it)) },
                    valueRange = 0.3f..1f
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        onSettingsChange(
                            currentSettings.copy(
                                verticalPosition = Defaults.VERTICAL_POS,
                                horizontalPosition = Defaults.HORIZONTAL_POS,
                                maxWidthRatio = Defaults.MAX_WIDTH
                            )
                        )
                        showPositionSheet = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Reset to default") }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Delay sheet
    if (showDelaySheet) {
        ModalBottomSheet(
            onDismissRequest = { showDelaySheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Subtitle Delay", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                val delaySec = currentSettings.delayMs / 1000f
                Text("${if (delaySec >= 0) "+" else ""}${"%.1f".format(delaySec)}s")
                Slider(
                    value = delaySec,
                    onValueChange = { onSettingsChange(currentSettings.copy(delayMs = (it * 1000).toInt())) },
                    valueRange = -10f..10f,
                    steps = 39
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        onSettingsChange(currentSettings.copy(delayMs = Defaults.DELAY_MS))
                        showDelaySheet = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Reset to default") }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Rotation sheet
    if (showRotationSheet) {
        ModalBottomSheet(
            onDismissRequest = { showRotationSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Rotation", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Angle: ${rotation.toInt()}°", modifier = Modifier.weight(1f))
                    Switch(checked = lockRotation, onCheckedChange = { lockRotation = it })
                }
                if (!lockRotation) {
                    Slider(
                        value = rotation,
                        onValueChange = { rotation = it },
                        valueRange = -180f..180f,
                        steps = 71
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0f, 90f, 180f, 270f).forEach { angle ->
                        FilterChip(
                            selected = rotation == angle && !lockRotation,
                            onClick = { rotation = angle },
                            label = { Text("${angle.toInt()}°") }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        rotation = 0f
                        lockRotation = true
                        showRotationSheet = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Reset to default (0°, locked)") }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Template picker modal
    if (showTemplatePicker) {
        ModalBottomSheet(
            onDismissRequest = { showTemplatePicker = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Background Templates", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                val rows = TEMPLATES.chunked(3)
                rows.forEach { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        rowItems.forEach { tmpl ->
                            val idx = TEMPLATES.indexOf(tmpl)
                            val bgMod = if (tmpl.colors.size == 1) Modifier.background(tmpl.colors[0]) else Modifier.background(Brush.verticalGradient(tmpl.colors))
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .then(bgMod)
                                    .border(
                                        if (idx == selectedTemplateIndex) 2.dp else 0.dp,
                                        MaterialTheme.colorScheme.primary,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        selectedTemplateIndex = idx
                                        showTemplatePicker = false
                                    }
                            )
                        }
                        repeat(3 - rowItems.size) { Spacer(modifier = Modifier.size(56.dp)) }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Full color picker modal
    if (showFullColorPicker != null) {
        FullColorPickerModal(
            onDismiss = { showFullColorPicker = null },
            onColorSelected = { color -> showFullColorPicker!!.invoke(color.toArgb().toLong()) }
        )
    }

    // Global reset confirmation
    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset all settings?") },
            text = { Text("This will restore the default subtitle settings for the active profile.") },
            confirmButton = {
                TextButton(onClick = {
                    onResetProfile(activeProfileIndex)
                    showResetConfirm = false
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") } }
        )
    }
}

// ---------- Full Color Picker Modal (manual HSL conversion) ----------
@Composable
private fun FullColorPickerModal(
    onDismiss: () -> Unit,
    onColorSelected: (Color) -> Unit
) {
    var hue by remember { mutableFloatStateOf(0f) }
    var lightness by remember { mutableFloatStateOf(0.5f) }
    var alpha by remember { mutableFloatStateOf(1f) }

    // Build current color from HSL
    val currentColor = remember(hue, lightness, alpha) {
        Color.hsl(hue, 1f, lightness, alpha)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("Pick a Color", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            // 2D palette: hue on X, lightness on Y
            val paletteSize = 256.dp
            Box(
                modifier = Modifier
                    .size(paletteSize)
                    .clip(RoundedCornerShape(4.dp))
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val xFraction = (offset.x / size.width).coerceIn(0f, 1f)
                            val yFraction = (offset.y / size.height).coerceIn(0f, 1f)
                            hue = xFraction * 360f
                            lightness = 1f - yFraction   // top = light, bottom = dark
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Draw horizontal hue gradient
                    for (x in 0 until size.width.toInt() step 2) {
                        val currentHue = x / size.width * 360f
                        drawRect(
                            color = Color.hsl(currentHue, 1f, 0.5f),
                            topLeft = Offset(x.toFloat(), 0f),
                            size = Size(2f, size.height)
                        )
                    }
                    // Overlay vertical lightness gradient
                    drawRect(
                        brush = Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0f), Color.Black),
                            startY = 0f,
                            endY = size.height
                        )
                    )
                    // Current color indicator
                    val indicatorX = hue / 360f * size.width
                    val indicatorY = (1f - lightness) * size.height
                    drawCircle(
                        color = Color.White,
                        radius = 6f,
                        center = Offset(indicatorX, indicatorY),
                        style = Stroke(width = 2f)
                    )
                    drawCircle(
                        color = Color.Black,
                        radius = 4f,
                        center = Offset(indicatorX, indicatorY)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Alpha slider
            Text("Alpha", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = alpha,
                onValueChange = { alpha = it },
                valueRange = 0f..1f,
                modifier = Modifier.fillMaxWidth()
            )

            // Hex input
            var hex by remember { mutableStateOf(String.format("%08X", currentColor.toArgb())) }
            OutlinedTextField(
                value = hex,
                onValueChange = {
                    hex = it.take(8).uppercase().filter { c -> c in "0123456789ABCDEF" }
                    if (hex.length == 8) {
                        try {
                            val newColor = Color(hex.toLong(16))
                            // Extract HSL from new color
                            val hsl = rgbToHsl(newColor)
                            hue = hsl[0]
                            lightness = hsl[2]
                            alpha = hsl[3]
                        } catch (_: Exception) {}
                    }
                },
                label = { Text("ARGB Hex") },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                TextButton(onClick = {
                    onColorSelected(currentColor)
                    onDismiss()
                }) { Text("OK") }
            }
        }
    }
}

// Manual RGB→HSL conversion (returns hue, saturation, lightness, alpha)
private fun rgbToHsl(color: Color): FloatArray {
    val r = color.red
    val g = color.green
    val b = color.blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    val lightness = (max + min) / 2f

    if (delta == 0f) {
        // Achromatic
        return floatArrayOf(0f, 0f, lightness, color.alpha)
    }

    val saturation = if (lightness <= 0.5f) delta / (max + min) else delta / (2f - max - min)
    val hue = when (max) {
        r -> ((g - b) / delta + if (g < b) 6f else 0f)
        g -> ((b - r) / delta + 2f)
        else -> ((r - g) / delta + 4f)
    } * 60f

    return floatArrayOf(hue.coerceAtLeast(0f), saturation, lightness, color.alpha)
}

// ---------- SubtitlePreview ----------
@Composable
private fun SubtitlePreview(
    settings: SubtitleSettings,
    rotation: Float,
    offsetX: Float,
    offsetY: Float,
    onDrag: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val textColor = if (settings.fontColor == 0x00000000L) Color.White else Color(settings.fontColor)
    val bgColor = Color(settings.backgroundColor)
    val outlineColor = Color(settings.outlineColor)

    val dropShadow = if (settings.enableShadow) Shadow(color = Color.Black.copy(alpha = 0.4f), offset = Offset(2f, 2f), blurRadius = 2f) else null
    val outlineBlur = if (settings.enableOutline) settings.outlineWidth * 1.5f else 0f

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .graphicsLayer { rotationZ = rotation }
                .background(
                    bgColor.copy(alpha = if (settings.backgroundColor == 0x00000000L) 0f else 1f),
                    RoundedCornerShape(4.dp)
                )
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount -> change.consume(); onDrag(dragAmount.x, dragAmount.y) }
                }
        ) {
            Box {
                if (settings.enableOutline) {
                    Text(
                        text = LOREM_IPSUM, color = outlineColor, fontSize = settings.fontSize.sp, textAlign = TextAlign.Center, maxLines = 2,
                        style = TextStyle(shadow = Shadow(color = outlineColor, offset = Offset.Zero, blurRadius = outlineBlur))
                    )
                }
                Text(
                    text = LOREM_IPSUM, color = textColor, fontSize = settings.fontSize.sp, textAlign = TextAlign.Center, maxLines = 2,
                    style = TextStyle(shadow = dropShadow)
                )
            }
        }
    }
}

// Profile selector
@Composable
private fun ProfileSelector(
    profiles: List<SubtitleSettings>,
    activeIndex: Int,
    onSelect: (Int) -> Unit,
    onRename: (Int, String) -> Unit,
) {
    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        profiles.forEachIndexed { index, profile ->
            FilterChip(
                selected = index == activeIndex,
                onClick = { onSelect(index) },
                label = { Text(profile.profileName, maxLines = 1) }
            )
        }
    }
}