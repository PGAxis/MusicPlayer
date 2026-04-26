package com.pg_axis.musicaxs

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.pg_axis.musicaxs.services.AlbumArtFetcher
import com.pg_axis.musicaxs.services.MusicService

class MusicAxsApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        MusicService.initFromSettings(this)
        MusicService.initializeService(this)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(AlbumArtFetcher.Factory(this@MusicAxsApplication))
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(50L * 1024 * 1024)
                    .build()
            }
            .respectCacheHeaders(false)
            .build()
    }
}