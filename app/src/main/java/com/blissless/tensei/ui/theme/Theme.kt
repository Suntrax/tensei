package com.blissless.tensei.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val OledBlack = Color(0xFF000000)

enum class ThemeMode(val value: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark"),
    OLED("oled");

    companion object {
        fun fromValue(value: String): ThemeMode =
            entries.find { it.value == value } ?: SYSTEM
    }
}

private val MonochromeLightColorScheme = lightColorScheme(
    primary = Color(0xFF1C1C1C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD4D4D4),
    onPrimaryContainer = Color(0xFF1C1C1C),
    secondary = Color(0xFF3B3B3B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8E8E8),
    onSecondaryContainer = Color(0xFF1C1C1C),
    tertiary = Color(0xFF595959),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF0F0F0),
    onTertiaryContainer = Color(0xFF1C1C1C),
    background = Color(0xFFF5F5F5),
    onBackground = Color(0xFF1C1C1C),
    surface = SurfaceWhite,
    onSurface = Color(0xFF1C1C1C),
    surfaceVariant = Color(0xFFE8E8E8),
    onSurfaceVariant = Color(0xFF3B3B3B),
    outline = Color(0xFF9E9E9E),
    outlineVariant = Color(0xFFD4D4D4),
    error = Color(0xFF595959),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF0F0F0),
    onErrorContainer = Color(0xFF1C1C1C)
)

private val MonochromeDarkColorScheme = darkColorScheme(
    primary = Color(0xFFE8E8E8),
    onPrimary = Color(0xFF1C1C1C),
    primaryContainer = Color(0xFF3B3B3B),
    onPrimaryContainer = Color(0xFFE8E8E8),
    secondary = Color(0xFFBBBBBB),
    onSecondary = Color(0xFF1C1C1C),
    secondaryContainer = Color(0xFF2E2E2E),
    onSecondaryContainer = Color(0xFFE8E8E8),
    tertiary = Color(0xFF9E9E9E),
    onTertiary = Color(0xFF1C1C1C),
    tertiaryContainer = Color(0xFF242424),
    onTertiaryContainer = Color(0xFFE8E8E8),
    background = Color(0xFF111111),
    onBackground = Color(0xFFE8E8E8),
    surface = Color(0xFF161616),
    onSurface = Color(0xFFE8E8E8),
    surfaceVariant = Color(0xFF282828),
    onSurfaceVariant = Color(0xFFBBBBBB),
    outline = Color(0xFF6B6B6B),
    outlineVariant = Color(0xFF3B3B3B),
    error = Color(0xFF9E9E9E),
    onError = Color(0xFF1C1C1C),
    errorContainer = Color(0xFF2E2E2E),
    onErrorContainer = Color(0xFFE8E8E8)
)

private val MonochromeOledColorScheme = darkColorScheme(
    primary = Color(0xFFE8E8E8),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF1A1A1A),
    onPrimaryContainer = Color(0xFFE8E8E8),
    secondary = Color(0xFFBBBBBB),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF1A1A1A),
    onSecondaryContainer = Color(0xFFE8E8E8),
    tertiary = Color(0xFF9E9E9E),
    onTertiary = Color(0xFF000000),
    tertiaryContainer = Color(0xFF141414),
    onTertiaryContainer = Color(0xFFE8E8E8),
    background = OledBlack,
    onBackground = Color(0xFFE8E8E8),
    surface = OledBlack,
    onSurface = Color(0xFFE8E8E8),
    surfaceVariant = OledBlack,
    onSurfaceVariant = Color(0xFFBBBBBB),
    outline = Color(0xFF555555),
    outlineVariant = Color(0xFF242424),
    error = Color(0xFF9E9E9E),
    onError = Color(0xFF000000),
    errorContainer = Color(0xFF141414),
    onErrorContainer = Color(0xFFE8E8E8)
)

@Composable
fun AppTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    useMonochrome: Boolean = false,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.OLED -> true
    }

    val colorScheme = when {
        useMonochrome && themeMode == ThemeMode.OLED -> MonochromeOledColorScheme
        useMonochrome && darkTheme -> MonochromeDarkColorScheme
        useMonochrome -> MonochromeLightColorScheme
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }.let { scheme ->
        if (themeMode == ThemeMode.OLED && !useMonochrome) {
            scheme.copy(
                surface = OledBlack,
                background = OledBlack,
                surfaceVariant = OledBlack,
                primaryContainer = scheme.primaryContainer.copy(alpha = 0.2f)
            )
        } else scheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = TenseiTypography,
        content = content
    )
}


