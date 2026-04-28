package com.pg_axis.musicaxs.services

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import java.io.File
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AlbumArtFetcher(
    private val uri: Uri,
    private val context: Context
) : Fetcher {
    private fun noArtSentinelFor(key: String) =
        File(context.cacheDir, "album_art/$key.none")

    override suspend fun fetch(): FetchResult? {
        if (uri.authority != "media") return null
        val songId = uri.lastPathSegment ?: return null

        val cacheFile = cacheFileFor("song_$songId")
        if (cacheFile.exists() && cacheFile.length() > 0) {
            return sourceResult(cacheFile, DataSource.DISK)
        }

        val sentinel = noArtSentinelFor("song_$songId")
        if (sentinel.exists()) return null

        val albumId = resolveAlbumId(uri)
        if (albumId != null) {
            val albumArtUri =
                "content://media/external/audio/albumart/$albumId".toUri()

            try {
                context.contentResolver.openInputStream(albumArtUri)?.use { stream ->
                    val bytes = stream.readBytes()
                    if (bytes.isNotEmpty()) {
                        cacheFile.writeBytes(bytes)
                        return sourceResult(cacheFile, DataSource.NETWORK)
                    }
                }
            } catch (_: Exception) {}
        }

        return try {
            val mmr = MediaMetadataRetriever()
            mmr.setDataSource(context, uri)
            val art = mmr.embeddedPicture
            mmr.release()

            if (art != null && art.isNotEmpty()) {
                cacheFile.writeBytes(art)
                sourceResult(cacheFile, DataSource.DISK)
            } else{
                withContext(Dispatchers.IO) {
                    sentinel.createNewFile()
                }
                null
            }

        } catch (_: Exception) {
            withContext(Dispatchers.IO) {
                sentinel.createNewFile()
            }
            null
        }
    }

    private fun resolveAlbumId(uri: Uri): String? {
        val projection = arrayOf(MediaStore.Audio.Media.ALBUM_ID)

        return context.contentResolver.query(uri, projection, null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst())
                    cursor.getLong(0).toString()
                else null
            }
    }

    private fun cacheFileFor(key: String): File {
        val dir = File(context.cacheDir, "album_art").also { it.mkdirs() }
        return File(dir, "$key.jpg")
    }

    private fun sourceResult(file: File, dataSource: DataSource): SourceResult {
        return SourceResult(
            source = ImageSource(
                file = file.toOkioPath(),
                fileSystem = FileSystem.SYSTEM
            ),
            mimeType = null,
            dataSource = dataSource
        )
    }

    class Factory(private val context: Context) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (data.authority != "media") return null
            val segments = data.pathSegments
            if (!segments.contains("audio")) return null
            return AlbumArtFetcher(data, context)
        }
    }
}