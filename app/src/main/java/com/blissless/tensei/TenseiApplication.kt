package com.blissless.tensei

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.blissless.tensei.torrent.TorrentEngine
import kotlinx.serialization.json.Json

class TenseiApplication : Application(), ImageLoaderFactory {

    lateinit var torrentEngine: TorrentEngine
        private set

    override fun onCreate() {
        super.onCreate()
        uy.kohesive.injekt.Injekt.register(Application::class.java, this)
        uy.kohesive.injekt.Injekt.register(Context::class.java, this)
        uy.kohesive.injekt.Injekt.register(Json::class.java, Json { ignoreUnknownKeys = true; explicitNulls = false })
        torrentEngine = TorrentEngine(this)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25) // Use 25% of available memory
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(512L * 1024 * 1024) // 512MB disk cache
                    .build()
            }
            .respectCacheHeaders(false) // Cache images even if server says not to
            .crossfade(false) // Disable crossfade globally for performance
            .build()
    }
}


