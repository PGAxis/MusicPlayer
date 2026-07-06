package dev.pgaxis.musicaxs.ext_funcs

import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import dev.pgaxis.musicaxs.models.PodcastEpisode
import dev.pgaxis.musicaxs.models.PodcastFeed
import dev.pgaxis.musicaxs.models.QueueItem
import dev.pgaxis.musicaxs.models.QueueItemSource
import dev.pgaxis.musicaxs.models.Song

fun String.splitByArtistSeparator(regex: Regex?): List<String> =
    if (regex == null) listOf(trim()).filter { it.isNotEmpty() }
    else split(regex).map { it.trim() }.filter { it.isNotEmpty() }

@OptIn(UnstableApi::class)
fun Song.toMediaItem(
    source: QueueItemSource = QueueItemSource.LOCAL,
    deviceId: String? = null
) = MediaItem.Builder()
    .setUri(uri)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setArtworkUri(uri)
            .setExtras(Bundle().apply {
                putString("source", source.name)
                deviceId?.let { putString("deviceId", it) }
            })
            .build()
    )
    .build()

fun Song.toQueueItem(source: QueueItemSource = QueueItemSource.LOCAL): QueueItem {
    return QueueItem(
        uri = uri.toString(),
        title = title,
        artist = artist,
        albumArtUri = albumArtUri?.toString(),
        durationMs = durationMs,
        source = source
    )
}

fun PodcastEpisode.toMediaItem(feed: PodcastFeed) = MediaItem.Builder()
    .setUri(audioUrl)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(feed.title)
            .setArtworkUri(feed.artUrl?.toUri())
            .setExtras(Bundle().apply {
                putString("source", QueueItemSource.PODCAST.name)
            })
            .build()
    )
    .build()

fun PodcastEpisode.toQueueItem(feed: PodcastFeed): QueueItem {
    return QueueItem(
        uri = audioUrl,
        title = title,
        artist = feed.title,
        albumArtUri = feed.artUrl,
        durationMs = durationMs,
        source = QueueItemSource.PODCAST
    )
}

fun MediaItem.toQueueItem(): QueueItem {
    val extras = mediaMetadata.extras
    val source = extras?.getString("source")
        ?.let { runCatching { QueueItemSource.valueOf(it) }.getOrNull() }
        ?: QueueItemSource.LOCAL
    val deviceId = extras?.getString("deviceId")
    return QueueItem(
        uri = localConfiguration?.uri?.toString() ?: "",
        title = mediaMetadata.title?.toString() ?: "",
        artist = mediaMetadata.artist?.toString() ?: "",
        albumArtUri = mediaMetadata.artworkUri?.toString(),
        durationMs = 0L,
        source = source,
        deviceId = deviceId
    )
}

/*private fun getBitmapData(context: Context, uri: Uri): ByteArray {
    val mmr = MediaMetadataRetriever()
    val bitmap = try {
        mmr.setDataSource(context, uri)
        mmr.embeddedPicture ?: throw IllegalStateException("No artwork found for $uri")
    } finally {
        mmr.release()
    }

    return bitmap
}*/