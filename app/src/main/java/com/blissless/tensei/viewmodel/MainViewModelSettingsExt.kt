package com.blissless.tensei.viewmodel

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.viewModelScope
import com.blissless.tensei.MainActivity
import com.blissless.tensei.MainViewModel
import com.blissless.tensei.widget.AiringScheduleWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
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

fun MainViewModel.setShowMangaCardButtons(enabled: Boolean) =
    userPreferences.setShowMangaCardButtons(enabled)

fun MainViewModel.setShowMangaStatusColors(enabled: Boolean) =
    userPreferences.setShowMangaStatusColors(enabled)

fun MainViewModel.setMaxPerformance(enabled: Boolean) =
    userPreferences.setMaxPerformance(enabled)

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

fun MainViewModel.setSupportsPiP(enabled: Boolean) =
    userPreferences.setSupportsPiP(enabled)

fun MainViewModel.setDiscordRichPresence(enabled: Boolean) {
    userPreferences.setDiscordRichPresence(enabled)
    if (enabled) {
        com.blissless.tensei.discord.DiscordRichPresence.connect()
    } else {
        com.blissless.tensei.discord.DiscordRichPresence.clearPresence()
        com.blissless.tensei.discord.DiscordRichPresence.disconnect()
    }
}

fun MainViewModel.setDefaultSubtitleLang(lang: String) =
    userPreferences.setDefaultSubtitleLang(lang)

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

fun MainViewModel.setAutoSyncCrossProviderStartup(enabled: Boolean) =
    userPreferences.setAutoSyncCrossProviderStartup(enabled)

fun MainViewModel.setAutoSyncCrossProviderDirection(toMal: Boolean) =
    userPreferences.setAutoSyncCrossProviderDirection(toMal)

fun MainViewModel.setMalAsMainProvider(enabled: Boolean) =
    userPreferences.setMalAsMainProvider(enabled)

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

fun MainViewModel.setMangaReaderMode(mode: String) =
    userPreferences.setMangaReaderMode(mode)

fun MainViewModel.setMangaDataSaver(enabled: Boolean) =
    userPreferences.setMangaDataSaver(enabled)

fun MainViewModel.setMangaPageIndicator(enabled: Boolean) =
    userPreferences.setMangaPageIndicator(enabled)

fun MainViewModel.setMangaLockRotation(enabled: Boolean) =
    userPreferences.setMangaLockRotation(enabled)

fun MainViewModel.setMangaFullscreen(enabled: Boolean) =
    userPreferences.setMangaFullscreen(enabled)

fun MainViewModel.setMangaAutoAdvance(enabled: Boolean) =
    userPreferences.setMangaAutoAdvance(enabled)

fun MainViewModel.setDefaultMagnetExtension(authority: String) =
    userPreferences.setDefaultMagnetExtension(authority)

fun MainViewModel.setDefaultStreamExtension(authority: String?) =
    userPreferences.setDefaultStreamExtension(authority)

// ─── Settings with side effects ────────────────────────────────────────────

fun MainViewModel.setThemeMode(mode: String) {
    userPreferences.setThemeMode(mode)
    viewModelScope.launch { AiringScheduleWidget.updateAll(context) }
}

/**
 * Switches the launcher icon. Persists the choice and toggles the matching
 * [activity-alias] so exactly one icon variant is enabled at a time.
 * The app then restarts so launchers pick up the new icon.
 */
fun MainViewModel.setAppIcon(key: String) {
    if (key == userPreferences.appIcon.value) return
    userPreferences.setAppIcon(key)
    val pm = context.packageManager
    val aliases = mapOf(
        "default" to ComponentName(context, "com.blissless.tensei.MainActivityDefault"),
        "wob" to ComponentName(context, "com.blissless.tensei.MainActivityWob"),
        "bow" to ComponentName(context, "com.blissless.tensei.MainActivityBow")
    )
    aliases.forEach { (aliasKey, component) ->
        val state = if (aliasKey == key) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        pm.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
    }
    val intent = pm.getLaunchIntentForPackage(context.packageName)?.apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    } ?: Intent(context, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
    context.startActivity(intent)
    viewModelScope.launch {
        delay(400)
        Runtime.getRuntime().exit(0)
    }
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
