package com.blissless.tensei.util

import androidx.annotation.StringRes

/**
 * One-shot UI events that should be shown to the user and then dismissed.
 *
 * These flow through a SharedFlow in the ViewModel and are collected by
 * the Composable layer (usually at the Scaffold/root level) to be shown
 * as Toasts, Snackbars, or dialogs.
 *
 * Using a sealed class instead of raw Strings allows:
 *   - Type-safe handling (Toast vs Snackbar vs Dialog)
 *   - String resources for i18n (resId) instead of hardcoded strings
 *   - Action buttons (e.g. "Undo" on a Snackbar)
 *   - Duration control
 *
 * Usage in ViewModel:
 *   private val _uiEvents = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
 *   val uiEvents = _uiEvents.asSharedFlow()
 *
 *   fun doSomething() {
 *       viewModelScope.launch {
 *           try { ... }
 *           catch (e: Exception) {
 *               _uiEvents.emit(UiEvent.Error("Failed: ${e.message}"))
 *           }
 *       }
 *   }
 *
 * Usage in Composable:
 *   LaunchedEffect(Unit) {
 *       viewModel.uiEvents.collect { event ->
 *           when (event) {
 *               is UiEvent.Toast -> Toast.makeText(context, event.message, event.duration).show()
 *               is UiEvent.Error -> Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
 *               is UiEvent.Info -> Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
 *           }
 *       }
 *   }
 */
sealed class UiEvent {
    /** Duration for the Toast (android.widget.Toast.LENGTH_SHORT / LENGTH_LONG) */
    abstract val duration: Int

    /**
     * An informational Toast (LENGTH_SHORT by default).
     * Use for transient feedback like "URL copied", "Download started".
     */
    data class Info(
        val message: String,
        override val duration: Int = android.widget.Toast.LENGTH_SHORT,
    ) : UiEvent()

    /**
     * An error Toast (LENGTH_LONG by default).
     * Use for failures the user should be aware of, like "Download failed: ...".
     */
    data class Error(
        val message: String,
        override val duration: Int = android.widget.Toast.LENGTH_LONG,
    ) : UiEvent()

    /**
     * A generic Toast with configurable duration.
     * Prefer [Info] or [Error] for clarity; use this only when you need
     * exact control over duration.
     */
    data class Toast(
        val message: String,
        override val duration: Int = android.widget.Toast.LENGTH_SHORT,
    ) : UiEvent()

    /**
     * A Toast backed by a string resource (for i18n).
     * Resolved at the Composable layer where a Context is available.
     */
    data class ResourceToast(
        @StringRes val resId: Int,
        override val duration: Int = android.widget.Toast.LENGTH_SHORT,
    ) : UiEvent()
}
