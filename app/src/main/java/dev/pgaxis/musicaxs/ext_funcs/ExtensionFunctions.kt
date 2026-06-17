package dev.pgaxis.musicaxs.ext_funcs

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import dev.pgaxis.musicaxs.models.QueueItem
import dev.pgaxis.musicaxs.models.QueueItemSource
import dev.pgaxis.musicaxs.models.Song

fun String.splitByArtistSeparator(regex: Regex?): List<String> =
    if (regex == null) listOf(trim()).filter { it.isNotEmpty() }
    else split(regex).map { it.trim() }.filter { it.isNotEmpty() }

fun Song.toMediaItem() = MediaItem.Builder()
    .setUri(uri)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .build()
    )
    .build()

fun Song.toQueueItem(source: QueueItemSource): QueueItem {
    return QueueItem(
        uri = uri.toString(),
        title = title,
        artist = artist,
        albumArtUri = albumArtUri?.toString(),
        durationMs = durationMs,
        source = source
    )
}