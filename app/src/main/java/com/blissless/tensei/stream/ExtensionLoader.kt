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

        val sourceClass = extension.sourceClass ?: return emptyList()

        val sourceClassName = if (sourceClass.startsWith(".")) {
            "${extension.packageName}$sourceClass"
        } else {
            sourceClass
        }

        return try {
            val loader = loaderCache.getOrPut(apkPath) {
                val dexOutput = context.codeCacheDir
                Log.d("ExtensionLoader", "Creating class loader for $apkPath")
                ParentFirstClassLoader(apkPath, dexOutput.absolutePath, context.classLoader)
            }
            val clazz = loader.loadClass(sourceClassName)
            Log.d("ExtensionLoader", "Loaded class $sourceClassName from ${extension.packageName} (pkg=${extension.packageName}, name=${extension.name})")
            val sources = when {
                AnimeSourceFactory::class.java.isAssignableFrom(clazz) -> {
                    Log.d("ExtensionLoader", "$sourceClassName is an AnimeSourceFactory")
                    val factory = clazz.getDeclaredConstructor().newInstance() as AnimeSourceFactory
                    val created = factory.createSources()
                    Log.d("ExtensionLoader", "Factory created ${created.size} sources: ${created.map { it.name }}")
                    created.filterIsInstance<AnimeCatalogueSource>()
                }
                AnimeCatalogueSource::class.java.isAssignableFrom(clazz) -> {
                    Log.d("ExtensionLoader", "$sourceClassName is an AnimeCatalogueSource")
                    val source = clazz.getDeclaredConstructor().newInstance() as AnimeCatalogueSource
                    Log.d("ExtensionLoader", "Source: id=${source.id}, name=${source.name}, lang=${source.lang}")
                    listOf(source)
                }
                else -> {
                    Log.w("ExtensionLoader", "Source class $sourceClassName does not implement AnimeCatalogueSource or AnimeSourceFactory")
                    emptyList()
                }
            }
            if (sources.isNotEmpty()) {
                Log.i("ExtensionLoader", "Successfully loaded ${sources.size} source(s) from ${extension.name}: ${sources.joinToString { "${it.name} (${it.lang})" }}")
            }
            sources
        } catch (e: Exception) {
            Log.e("ExtensionLoader", "Failed to load source class $sourceClassName from ${extension.packageName} (${extension.name})", e)
            emptyList()
        }
    }

}


