package com.blissless.tensei.stream

import android.content.Context
import android.util.Log
import dalvik.system.DexClassLoader
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.AnimeSourceFactory
import com.blissless.tensei.extensions.Extension
import java.util.concurrent.ConcurrentHashMap

class ParentFirstClassLoader(apkPath: String, dexOutput: String, parent: ClassLoader) :
    DexClassLoader(apkPath, dexOutput, null, parent) {

    private val parentFirstPackages = setOf("eu.kanade.tachiyomi", "okhttp3", "kotlin")

    override fun loadClass(name: String, resolve: Boolean): Class<*>? {
        for (pkg in parentFirstPackages) {
            if (name.startsWith(pkg)) {
                return try {
                    parent.loadClass(name)
                } catch (_: ClassNotFoundException) {
                    super.loadClass(name, resolve)
                }
            }
        }
        return super.loadClass(name, resolve)
    }
}

class ExtensionLoader(private val context: Context) {

    private val loaderCache = ConcurrentHashMap<String, ParentFirstClassLoader>()

    fun loadSources(extension: Extension): List<AnimeCatalogueSource> {
        val pm = context.packageManager
        val ai = pm.getApplicationInfo(extension.packageName, 0)
        val apkPath = ai.sourceDir

        val sourceClass = extension.sourceClass
        if (sourceClass == null) {
            return emptyList()
        }

        val sourceClassName = if (sourceClass.startsWith(".")) {
            "${extension.packageName}$sourceClass"
        } else {
            sourceClass
        }

        return try {
            val loader = loaderCache.getOrPut(apkPath) {
                val dexOutput = context.codeCacheDir
                ParentFirstClassLoader(apkPath, dexOutput.absolutePath, context.classLoader)
            }
            val clazz = loader.loadClass(sourceClassName)
            when {
                AnimeSourceFactory::class.java.isAssignableFrom(clazz) -> {
                    val factory = clazz.getDeclaredConstructor().newInstance() as AnimeSourceFactory
                    factory.createSources().filterIsInstance<AnimeCatalogueSource>()
                }
                AnimeCatalogueSource::class.java.isAssignableFrom(clazz) -> {
                    val source = clazz.getDeclaredConstructor().newInstance() as AnimeCatalogueSource
                    listOf(source)
                }
                else -> {
                    Log.w("ExtensionLoader", "Source class $sourceClassName does not implement AnimeCatalogueSource or AnimeSourceFactory")
                    emptyList()
                }
            }
        } catch (e: Exception) {
            Log.e("ExtensionLoader", "Failed to load source class $sourceClassName from ${extension.packageName}", e)
            emptyList()
        }
    }

    fun clearCache() {
        loaderCache.clear()
    }
}


