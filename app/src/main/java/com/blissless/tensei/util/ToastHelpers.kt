package com.blissless.tensei.util

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Compose-friendly Toast helper.
 *
 * Problem: the codebase has 50+ `Toast.makeText(context, ...).show()`
 * calls scattered across composables. Many use the same message strings
 * and have no centralized control over duration or styling.
 *
 * This helper provides:
 *   - A [rememberToast] composable function that returns a lambda for
 *     showing toasts, memoizing the Context
 *   - Convenience methods [toast] and [longToast] for the common cases
 *
 * Usage:
 *   val showToast = rememberToast()
 *   Button(onClick = { showToast("Copied!") }) { ... }
 *
 * For ViewModel-originated messages, prefer emitting to a SharedFlow
 * (see [UiEvent]) and collecting it once at the root composable, rather
 * than calling Toast directly from the VM.
 */

/**
 * Returns a memoized lambda that shows a short Toast.
 * The Context is captured from the composition, so the lambda can be
 * passed to callbacks safely.
 */
@Composable
fun rememberToast(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { message: String ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}

/**
 * Returns a memoized lambda that shows a Toast with the given duration.
 */
@Composable
fun rememberToastWithDuration(duration: Int = Toast.LENGTH_SHORT): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { message: String ->
            Toast.makeText(context, message, duration).show()
        }
    }
}

/**
 * Returns a pair of lambdas: (showShort, showLong).
 * Useful when a composable needs both short and long toasts.
 */
@Composable
fun rememberToasts(): Pair<(String) -> Unit, (String) -> Unit> {
    val context = LocalContext.current
    return remember(context) {
        val short = { message: String ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
        val long = { message: String ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
        short to long
    }
}

/**
 * Non-composable helper for showing a toast from a Context.
 * Prefer the composable helpers above when in a @Composable function.
 */
fun Context.toast(message: String, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, message, duration).show()
}

/**
 * Non-composable helper for showing a long toast from a Context.
 */
fun Context.longToast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
