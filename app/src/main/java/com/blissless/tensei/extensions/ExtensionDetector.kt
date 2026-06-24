package com.blissless.tensei.extensions

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

class ExtensionDetector(private val context: Context) {

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
        val features = pkgInfo.reqFeatures.orEmpty().map { it.name }.toSet()
        val metaData = pkgInfo.applicationInfo?.metaData
        return ANIME_EXTENSION_FEATURE in features ||
                metaData?.containsKey(METADATA_ANIME_SOURCE_CLASS) == true ||
                metaData?.containsKey(METADATA_SOURCE_FACTORY) == true
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


