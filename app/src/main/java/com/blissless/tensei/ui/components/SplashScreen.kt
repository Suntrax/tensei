package com.blissless.tensei.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * Splash screen shown on app launch.
 *
 * Extracted from MainActivity.kt. Animates a logo image with a scale + fade
 * effect, then dismisses itself. Dismissal is triggered by whichever fires
 * first:
 *   - the [splashReady] flow becoming true (data is loaded), OR
 *   - a 1.8s absolute timeout (failsafe so the user never stares at the
 *     splash forever if data loading hangs).
 *
 * @param splashReady A lambda returning true when the app is ready to show
 *                    the main UI. Polled on each recomposition.
 * @param onFinished  Called once when the splash should be dismissed.
 * @param splashDrawableRes The drawable resource ID for the splash image.
 */
@Composable
fun SplashScreen(
    splashReady: () -> Boolean,
    onFinished: () -> Unit,
    splashDrawableRes: Int,
) {
    var splashProgress by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(splashReady()) {
        if (splashReady()) {
            splashProgress = 2f
            delay(400.milliseconds)
            onFinished()
        }
    }

    LaunchedEffect(Unit) {
        delay(1400.milliseconds)
        splashProgress = 2f
        delay(400.milliseconds)
        onFinished()
    }

    val animatedProgress by animateFloatAsState(
        targetValue = splashProgress,
        animationSpec = tween(durationMillis = 400, easing = LinearEasing),
        label = "splash_progress"
    )

    val scale = when {
        animatedProgress < 1f -> 0.85f + (0.15f * animatedProgress)
        else -> 1f + ((animatedProgress - 1f) * 0.15f)
    }
    val alpha = when {
        animatedProgress < 1f -> animatedProgress
        animatedProgress < 2f -> 1f - ((animatedProgress - 1f) * 1f)
        else -> 0f
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Image(
            painter = painterResource(id = splashDrawableRes),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    alpha = alpha.coerceIn(0f, 1f)
                ),
            contentScale = ContentScale.Crop
        )
    }
}
