package com.blissless.tensei.extensions

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

class ExtensionDetector(private val context: Context) {

    companion object {
        const val MAGNET_BEACON_ACTION = "com.blissless.animeclient.EXTENSION_BEACON"
        const val MANGA_BEACON_ACTION = "com.blissless.mangaclient.EXTENSION_BEACON"
        const val MAGNET_PROVIDER_SUFFIX = ".provider"
        private const val BLISSLESS_PKG = "com.blissless."

        fun extensionDisplayName(packageName: String): String {
            val name = extractExtensionName(packageName)
            return if (name.isNotEmpty()) "Tensei: ${name.replaceFirstChar { it.uppercase() }}" else packageName
        }

        fun extractExtensionName(packageName: String): String {
            val parts = packageName.split(".")
            val blisslessIdx = parts.indexOfFirst { it == "blissless" }
            if (blisslessIdx < 0 || blisslessIdx + 1 >= parts.size) return ""
            return parts[blisslessIdx + 1]
        }

        fun isBlisslessStreamExtension(packageName: String): Boolean =
            packageName.startsWith(BLISSLESS_PKG) && packageName.endsWith(".anime.stream")

        fun isBlisslessTorrentExtension(packageName: String): Boolean =
            packageName.startsWith(BLISSLESS_PKG) && packageName.endsWith(".anime.torrent")

        fun isBlisslessMangaExtension(packageName: String): Boolean =
            packageName.startsWith(BLISSLESS_PKG) && packageName.endsWith(".manga")
    }

    fun detectMagnetExtensions(): List<Pair<String, String>> {
        val results = mutableListOf<Pair<String, String>>()
        val seenPackages = mutableSetOf<String>()

        // 1) Package-name-based detection for *.anime.torrent
        val pm = context.packageManager
        val installedPkgs = try {
            getInstalledPackages(pm)
        } catch (_: Exception) { emptyList() }
        for (pkg in installedPkgs) {
            val pkgName = pkg.packageName
            if (isBlisslessTorrentExtension(pkgName)) {
                seenPackages.add(pkgName)
                results.add(extensionDisplayName(pkgName) to pkgName)
            }
        }

        // 2) Beacon fallback (backward compat)
        val beaconIntent = Intent(MAGNET_BEACON_ACTION)
        val resolveInfoList = try {
            context.packageManager.queryBroadcastReceivers(beaconIntent, 0)
        } catch (_: Exception) { emptyList() }
        for (info in resolveInfoList) {
            val pkg = info.activityInfo.packageName
            if (pkg in seenPackages) continue
            val label = try { info.loadLabel(pm).toString() } catch (_: Exception) { pkg }
            if (label.startsWith("Tensei: ", ignoreCase = true) || label.startsWith("Anime: ", ignoreCase = true)) {
                results.add(label to pkg)
            }
        }

        return results.sortedBy { it.first }
    }

    fun detectStreamExtensions(): List<Pair<String, String>> {
        val results = mutableListOf<Pair<String, String>>()
        val seenPackages = mutableSetOf<String>()
        val pm = context.packageManager
        val installedPkgs = try {
            getInstalledPackages(pm)
        } catch (_: Exception) { emptyList() }
        for (pkg in installedPkgs) {
            val pkgName = pkg.packageName
            if (isBlisslessStreamExtension(pkgName)) {
                seenPackages.add(pkgName)
                results.add(extensionDisplayName(pkgName) to pkgName)
            }
        }
        return results.sortedBy { it.first }
    }

    fun getMagnetAuthority(packageName: String): String {
        if (isBlisslessTorrentExtension(packageName) || isBlisslessStreamExtension(packageName) || isBlisslessMangaExtension(packageName)) {
            return packageName
        }
        return "$packageName$MAGNET_PROVIDER_SUFFIX"
    }

    @Suppress("DEPRECATION")
    private val packageFlags = PackageManager.GET_CONFIGURATIONS or
            PackageManager.GET_META_DATA or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                PackageManager.GET_SIGNING_CERTIFICATES else 0)

    fun detectInstalledExtensions(): List<Extension> {
        val pm = context.packageManager
        val installedPackages = try {
            getInstalledPackages(pm)
        } catch (e: SecurityException) {
            Log.e("ExtensionDetector", "Missing QUERY_ALL_PACKAGES permission", e)
            return emptyList()
        }
        return installedPackages
            .filter { isExtension(it) }
            .mapNotNull { pkgInfo ->
                try {
                    toExtension(pkgInfo, pm)
                } catch (e: Exception) {
                    Log.w("ExtensionDetector", "Failed to process extension: ${pkgInfo.packageName}", e)
                    null
                }
            }
            .sortedBy { it.name }
    }

    private fun getInstalledPackages(pm: PackageManager): List<PackageInfo> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(
                PackageManager.PackageInfoFlags.of(packageFlags.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(packageFlags)
        }
    }

    private fun isExtension(pkgInfo: PackageInfo): Boolean {
        val pkgName = pkgInfo.packageName
        if (isBlisslessStreamExtension(pkgName) || isBlisslessTorrentExtension(pkgName) || isBlisslessMangaExtension(pkgName)) {
            return true
        }
        val features = pkgInfo.reqFeatures.orEmpty().map { it.name }.toSet()
        val metaData = pkgInfo.applicationInfo?.metaData
        if (ANIME_EXTENSION_FEATURE in features ||
                metaData?.containsKey(METADATA_ANIME_SOURCE_CLASS) == true ||
                metaData?.containsKey(METADATA_SOURCE_FACTORY) == true) {
            return true
        }
        val pm = context.packageManager
        try {
            val beaconIntents = listOf(MAGNET_BEACON_ACTION, MANGA_BEACON_ACTION)
            for (action in beaconIntents) {
                val intent = Intent(action).setPackage(pkgName)
                val receivers = pm.queryBroadcastReceivers(intent, 0)
                if (receivers.any { it.activityInfo.packageName == pkgName }) return true
            }
        } catch (_: Exception) {}
        return false
    }

    private fun toExtension(pkgInfo: PackageInfo, pm: PackageManager): Extension {
        val ai = pkgInfo.applicationInfo ?: return createFallbackExtension(pkgInfo)
        val metaData = ai.metaData
        val sourceClass = metaData?.getString(METADATA_SOURCE_CLASS)
            ?: metaData?.getString(METADATA_ANIME_SOURCE_CLASS)
            ?: metaData?.getString(METADATA_SOURCE_FACTORY)

        val icon = try {
            ai.loadIcon(pm)
        } catch (_: Exception) {
            null
        }

        return Extension(
            packageName = pkgInfo.packageName,
            name = pm.getApplicationLabel(ai).toString(),
            versionName = pkgInfo.versionName ?: "",
            versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkgInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pkgInfo.versionCode.toLong()
            },
            icon = icon,
            sourceClass = sourceClass,
            isNsfw = isMetadataTrue(metaData, METADATA_NSFW) ||
                    isMetadataTrue(metaData, METADATA_ANIME_NSFW),
            isInstalled = true,
            installTime = pkgInfo.firstInstallTime
        )
    }

    private fun createFallbackExtension(pkgInfo: PackageInfo): Extension {
        return Extension(
            packageName = pkgInfo.packageName,
            name = pkgInfo.packageName,
            versionName = pkgInfo.versionName ?: "",
            versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkgInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pkgInfo.versionCode.toLong()
            },
            icon = null,
            sourceClass = null,
            isNsfw = false,
            isInstalled = true,
            installTime = pkgInfo.firstInstallTime
        )
    }

    private fun isMetadataTrue(metaData: android.os.Bundle?, key: String): Boolean {
        if (metaData == null) return false
        @Suppress("DEPRECATION")
        val value = metaData.get(key)
        return when (value) {
            is Boolean -> value
            is Int -> value != 0
            is String -> value.toBooleanStrictOrNull() ?: false
            else -> false
        }
    }
}
