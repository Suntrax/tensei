package com.blissless.tensei.extensions

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URL

data class Repo(
    val name: String,
    val description: String,
    val extensions: List<RepoExtension>
) {
    companion object {
        fun fromJson(json: JsonObject): Repo {
            return Repo(
                name = json["name"]?.jsonPrimitive?.content ?: "",
                description = json["description"]?.jsonPrimitive?.content ?: "",
                extensions = (json["extensions"] as? JsonArray)?.map { it.jsonObject.let(RepoExtension::fromJson) } ?: emptyList()
            )
        }
    }
}

data class RepoExtension(
    val name: String,
    val packageName: String,
    val apk: String,
    val icon: String = "",
    val lang: String,
    val version: String,
    val code: Long,
    val nsfw: Boolean,
    val sources: List<RepoSources>
) {
    companion object {
        fun fromJson(json: JsonObject): RepoExtension {
            return RepoExtension(
                name = json["name"]?.jsonPrimitive?.content ?: "",
                packageName = (json["pkg"] ?: json["packageName"])?.jsonPrimitive?.content ?: "",
                apk = json["apk"]?.jsonPrimitive?.content ?: "",
                icon = (json["icon"] ?: json["icons"])?.jsonPrimitive?.content ?: "",
                lang = json["lang"]?.jsonPrimitive?.content ?: "en",
                version = json["version"]?.jsonPrimitive?.content ?: "",
                code = (json["code"] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L,
                nsfw = (json["nsfw"] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: false,
                sources = (json["sources"] as? JsonArray)?.map { it.jsonObject.let(RepoSources::fromJson) } ?: emptyList()
            )
        }
    }
}

data class RepoSources(
    val name: String,
    val lang: String
) {
    companion object {
        fun fromJson(json: JsonObject): RepoSources {
            return RepoSources(
                name = json["name"]?.jsonPrimitive?.content ?: "",
                lang = json["lang"]?.jsonPrimitive?.content ?: "en"
            )
        }
    }
}

fun resolveApkUrl(repoUrl: String, apkPath: String): String {
    if (apkPath.startsWith("http://") || apkPath.startsWith("https://")) {
        return apkPath
    }
    val base = URL(repoUrl)
    val path = if ("/" in apkPath) apkPath else "apk/$apkPath"
    return URL(base, path).toString()
}

fun resolveIconUrl(repoUrl: String, iconPath: String): String {
    if (iconPath.isBlank()) return ""
    if (iconPath.startsWith("http://") || iconPath.startsWith("https://")) {
        return iconPath
    }
    val base = URL(repoUrl)
    val path = if ("/" in iconPath) iconPath else "icon/$iconPath"
    return URL(base, path).toString()
}

fun parseRepoJson(repoUrl: String, jsonElement: JsonElement): Repo {
    val fallbackName = extractRepoName(repoUrl)
    return when (jsonElement) {
        is JsonObject -> {
            val repo = Repo.fromJson(jsonElement)
            if (repo.name.isBlank()) repo.copy(name = fallbackName) else repo
        }
        is JsonArray -> {
            val extensions = jsonElement.map { RepoExtension.fromJson(it.jsonObject) }
            Repo(name = fallbackName, description = "", extensions = extensions)
        }
        else -> throw Exception("Unexpected JSON type: ${jsonElement::class.simpleName}")
    }
}

private fun extractRepoName(repoUrl: String): String {
    return try {
        val url = URL(repoUrl)
        val host = url.host.lowercase()
        val path = url.path.trim('/')
        when (host) {
            "raw.githubusercontent.com" -> {
                val parts = path.split('/')
                if (parts.size >= 2) parts[0] else host
            }
            "github.com" -> {
                val parts = path.split('/')
                if (parts.isNotEmpty()) parts[0] else host
            }
            else -> host
        }
    } catch (_: Exception) {
        repoUrl
    }
}


