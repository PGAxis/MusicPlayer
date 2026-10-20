package dev.pgaxis.musicaxs

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dev.pgaxis.musicaxs.services.AlbumArtFetcher
import dev.pgaxis.musicaxs.services.MusicService
import dev.pgaxis.musicaxs.settings.SettingsSave
import org.jaudiotagger.tag.TagOptionSingleton
import org.jaudiotagger.tag.id3.valuepair.TextEncoding

class MusicAxsApplication : Application(), ImageLoaderFactory {
    companion object {
        @Volatile
        var hasUIBeenActive = false
            private set
    }

    override fun onCreate() {
        super.onCreate()

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                hasUIBeenActive = true
            }
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })

        MusicService.initFromSettings(this)
        MusicService.initializeService(this)

        TagOptionSingleton.getInstance().apply {
            isAndroid = true
            id3v23DefaultTextEncoding = TextEncoding.UTF_16
            id3v24DefaultTextEncoding = TextEncoding.UTF_8
            isResetTextEncodingForExistingFrames = true
        }

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                SettingsSave.getInstance(this@MusicAxsApplication).flush()
            }
        })
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