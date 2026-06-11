package com.blissless.tensei.extensions

import android.graphics.drawable.Drawable

const val EXTENSION_FEATURE = "tachiyomi.extension"
const val ANIME_EXTENSION_FEATURE = "tachiyomi.animeextension"
val EXTENSION_FEATURES = setOf(EXTENSION_FEATURE, ANIME_EXTENSION_FEATURE)

const val METADATA_SOURCE_CLASS = "tachiyomi.extension.class"
const val METADATA_ANIME_SOURCE_CLASS = "tachiyomi.animeextension.class"
const val METADATA_SOURCE_FACTORY = "tachiyomi.extension.factory"
const val METADATA_NSFW = "tachiyomi.extension.nsfw"
const val METADATA_ANIME_NSFW = "tachiyomi.animeextension.nsfw"

data class Extension(
    val packageName: String,
    val name: String,
    val versionName: String,
    val versionCode: Long,
    val icon: Drawable?,
    val sourceClass: String?,
    val isNsfw: Boolean,
    val isInstalled: Boolean,
    val installTime: Long,
    val sources: List<SourceInfo> = emptyList()
)

data class SourceInfo(
    val id: Long,
    val name: String,
    val lang: String
)


