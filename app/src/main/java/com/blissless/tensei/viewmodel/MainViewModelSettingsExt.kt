package com.blissless.tensei.viewmodel

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.viewModelScope
import com.blissless.tensei.MainViewModel
import com.blissless.tensei.widget.AiringScheduleWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Settings-related passthroughs for [MainViewModel].
 *
 * Extracted from MainViewModel.kt to keep the god-object manageable.
 * All public signatures are preserved as extension functions, so existing
 * call sites (e.g. `viewModel.setThemeMode(mode)`) continue to compile
 * without modification.
 */

// ─── Pure one-liner delegations to UserPreferences ─────────────────────────

fun MainViewModel.setDisableMaterialColors(enabled: Boolean) =
    userPreferences.setDisableMaterialColors(enabled)

fun MainViewModel.setPreferredCategory(category: String) =
    userPreferences.setPreferredCategory(category)

fun MainViewModel.setShowStatusColors(enabled: Boolean) =
    userPreferences.setShowStatusColors(enabled)

fun MainViewModel.setShowAnimeCardButtons(enabled: Boolean) =
    userPreferences.setShowAnimeCardButtons(enabled)

fun MainViewModel.setPreferEnglishTitles(enabled: Boolean) =
    userPreferences.setPreferEnglishTitles(enabled)

fun MainViewModel.setPreventScheduleSync(enabled: Boolean) =
    userPreferences.setPreventScheduleSync(enabled)

fun MainViewModel.setTrackingPercentage(percentage: Int) =
    userPreferences.setTrackingPercentage(percentage)

fun MainViewModel.setForwardSkipSeconds(seconds: Int) =
    userPreferences.setForwardSkipSeconds(seconds)

fun MainViewModel.setBackwardSkipSeconds(seconds: Int) =
    userPreferences.setBackwardSkipSeconds(seconds)

fun MainViewModel.setSimplifyEpisodeMenu(enabled: Boolean) =
    userPreferences.setSimplifyEpisodeMenu(enabled)

fun MainViewModel.setAutoSkipOpening(enabled: Boolean) =
    userPreferences.setAutoSkipOpening(enabled)

fun MainViewModel.setAutoSkipEnding(enabled: Boolean) =
    userPreferences.setAutoSkipEnding(enabled)

fun MainViewModel.setAutoPlayNextEpisode(enabled: Boolean) =
    userPreferences.setAutoPlayNextEpisode(enabled)

fun MainViewModel.setDefaultSubtitleLang(lang: String) =
    userPreferences.setDefaultSubtitleLang(lang)

fun MainViewModel.setDownloadPreferredCategory(category: String) =
    userPreferences.setDownloadPreferredCategory(category)

fun MainViewModel.setDownloadSubtitleLang(lang: String) =
    userPreferences.setDownloadSubtitleLang(lang)

fun MainViewModel.setHideAdultContent(enabled: Boolean) =
    userPreferences.setHideAdultContent(enabled)

fun MainViewModel.setStartupScreen(screen: Int) =
    userPreferences.setStartupScreen(screen)

fun MainViewModel.setBufferAheadSeconds(seconds: Int) =
    userPreferences.setBufferAheadSeconds(seconds)

fun MainViewModel.setBufferSizeMb(sizeMb: Int) =
    userPreferences.setBufferSizeMb(sizeMb)

fun MainViewModel.setShowBufferIndicator(show: Boolean) =
    userPreferences.setShowBufferIndicator(show)

fun MainViewModel.setCheckUpdatesOnStart(enabled: Boolean) =
    userPreferences.setCheckUpdatesOnStart(enabled)

fun MainViewModel.setAutoUpdateExtensions(enabled: Boolean) =
    userPreferences.setAutoUpdateExtensions(enabled)

fun MainViewModel.setSwipeVolume(enabled: Boolean) =
    userPreferences.setSwipeVolume(enabled)

fun MainViewModel.setSwipeBrightness(enabled: Boolean) =
    userPreferences.setSwipeBrightness(enabled)

fun MainViewModel.setSwipeSwap(enabled: Boolean) =
    userPreferences.setSwipeSwap(enabled)

fun MainViewModel.setStreamMethod(method: String) =
    userPreferences.setStreamMethod(method)

fun MainViewModel.setDownloadDirectoryUri(uri: String?) =
    userPreferences.setDownloadDirectoryUri(uri)

fun MainViewModel.setKeepDownloadedFiles(enabled: Boolean) =
    userPreferences.setKeepDownloadedFiles(enabled)

fun MainViewModel.setDefaultMagnetExtension(authority: String) =
    userPreferences.setDefaultMagnetExtension(authority)

// ─── Settings with side effects ────────────────────────────────────────────

fun MainViewModel.setThemeMode(mode: String) {
    userPreferences.setThemeMode(mode)
    viewModelScope.launch { AiringScheduleWidget.updateAll(context) }
}

fun MainViewModel.setDefaultExtensionPackage(packageName: String) {
    clearAllExtensionStreamCaches()
    invalidateAllStreamCaches()
    cacheManager.clearVideoCache(context)
    userPreferences.setDefaultExtensionPackage(packageName)
    cacheManager.initializeVideoCache(context)
}

fun MainViewModel.invalidateAllStreamCaches() {
    cacheManager.invalidateAllStreamCaches()
}
