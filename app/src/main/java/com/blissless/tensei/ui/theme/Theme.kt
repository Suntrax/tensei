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
    primary = Color(0xFF212121),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE0E0E0),
    onPrimaryContainer = Color(0xFF000000),
    secondary = Color(0xFF424242),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEEEEEE),
    onSecondaryContainer = Color(0xFF000000),
    tertiary = Color(0xFF616161),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF5F5F5),
    onTertiaryContainer = Color(0xFF000000),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF000000),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF000000),
    surfaceVariant = Color(0xFFE0E0E0),
    onSurfaceVariant = Color(0xFF000000),
    outline = Color(0xFF757575),
    outlineVariant = Color(0xFFBDBDBD),
    error = Color(0xFF616161),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF5F5F5),
    onErrorContainer = Color(0xFF000000)
)

private val MonochromeDarkColorScheme = darkColorScheme(
    primary = Color(0xFFE0E0E0),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF424242),
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFFBDBDBD),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF333333),
    onSecondaryContainer = Color(0xFFE0E0E0),
    tertiary = Color(0xFF9E9E9E),
    onTertiary = Color(0xFF000000),
    tertiaryContainer = Color(0xFF262626),
    onTertiaryContainer = Color(0xFFEEEEEE),
    background = Color(0xFF121212),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Color(0xFFE0E0E0),
    outline = Color(0xFF757575),
    outlineVariant = Color(0xFF424242),
    error = Color(0xFF9E9E9E),
    onError = Color(0xFF000000),
    errorContainer = Color(0xFF333333),
    onErrorContainer = Color(0xFFE0E0E0)
)

private val MonochromeOledColorScheme = darkColorScheme(
    primary = Color(0xFFE0E0E0),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF1A1A1A),
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFFBDBDBD),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF1F1F1F),
    onSecondaryContainer = Color(0xFFE0E0E0),
    tertiary = Color(0xFF9E9E9E),
    onTertiary = Color(0xFF000000),
    tertiaryContainer = Color(0xFF1A1A1A),
    onTertiaryContainer = Color(0xFFEEEEEE),
    background = OledBlack,
    onBackground = Color(0xFFFFFFFF),
    surface = OledBlack,
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = OledBlack,
    onSurfaceVariant = Color(0xFFE0E0E0),
    outline = Color(0xFF616161),
    outlineVariant = Color(0xFF2A2A2A),
    error = Color(0xFF9E9E9E),
    onError = Color(0xFF000000),
    errorContainer = Color(0xFF1A1A1A),
    onErrorContainer = Color(0xFFE0E0E0)
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
        content = content
    )
}


