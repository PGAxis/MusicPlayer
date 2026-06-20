package dev.pgaxis.musicaxs.services

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

object PodcastDownloader {
    private val _downloading = MutableStateFlow<Set<String>>(emptySet())
    val downloading: StateFlow<Set<String>> = _downloading.asStateFlow()

    fun setDownloading(url: String, isDownloading: Boolean) {
        _downloading.value = if (isDownloading)
            _downloading.value + url
        else
            _downloading.value - url
    }

    fun isDownloaded(context: Context, audioUrl: String): Boolean =
        fileFor(context, audioUrl).exists()

    fun localUriFor(context: Context, audioUrl: String): String =
        fileFor(context, audioUrl).toURI().toString()

    fun delete(context: Context, audioUrl: String) {
        fileFor(context, audioUrl).delete()
    }

    internal fun fileFor(context: Context, audioUrl: String): File {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_PODCASTS)
            ?: context.filesDir.resolve("podcasts")
        val name = audioUrl.hashCode().toString().replace("-", "n") + ".mp3"
        return File(dir, name)
    }
}