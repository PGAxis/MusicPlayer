package dev.pgaxis.musicaxs.services

import android.content.Context
import android.os.Environment
import dev.pgaxis.musicaxs.models.PodcastEpisode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
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
        metaFileFor(context, audioUrl).delete()
    }

    /**
     * Stores episode metadata next to the downloaded file so the episode
     * can be listed again later without re-fetching or parsing the feed.
     */
    fun saveMetadata(context: Context, feedUrl: String, episode: PodcastEpisode) {
        val json = JSONObject().apply {
            put("feedUrl", feedUrl)
            put("title", episode.title)
            put("description", episode.description)
            put("audioUrl", episode.audioUrl)
            put("publishDate", episode.publishDate)
            put("durationMs", episode.durationMs)
        }
        metaFileFor(context, episode.audioUrl).apply {
            parentFile?.mkdirs()
            writeText(json.toString())
        }
    }

    /**
     * Episodes already downloaded for [feedUrl], read purely from local
     * metadata — no network involved. Used to populate the episode list
     * instantly while (or even if) the live feed fetch is still pending.
     */
    fun downloadedEpisodes(context: Context, feedUrl: String): List<PodcastEpisode> {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_PODCASTS)
            ?: context.filesDir.resolve("podcasts")
        val metaFiles = dir.listFiles { f -> f.extension == "meta" } ?: return emptyList()

        return metaFiles.mapNotNull { f ->
            try {
                val json = JSONObject(f.readText())
                if (json.optString("feedUrl") != feedUrl) return@mapNotNull null

                val audioUrl = json.getString("audioUrl")
                // Skip stale metadata whose actual audio file was deleted
                // outside the app (or partially cleaned up).
                if (!fileFor(context, audioUrl).exists()) return@mapNotNull null

                PodcastEpisode(
                    title = json.getString("title"),
                    description = json.optString("description"),
                    audioUrl = audioUrl,
                    publishDate = json.optString("publishDate"),
                    durationMs = json.optLong("durationMs")
                )
            } catch (_: Exception) {
                null
            }
        }
    }

    internal fun fileFor(context: Context, audioUrl: String): File {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_PODCASTS)
            ?: context.filesDir.resolve("podcasts")
        return File(dir, "${baseName(audioUrl)}.mp3")
    }

    private fun metaFileFor(context: Context, audioUrl: String): File {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_PODCASTS)
            ?: context.filesDir.resolve("podcasts")
        return File(dir, "${baseName(audioUrl)}.meta")
    }

    private fun baseName(audioUrl: String): String =
        audioUrl.hashCode().toString().replace("-", "n")
}