package dev.pgaxis.musicaxs.services

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
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
import dev.pgaxis.musicaxs.repositories.SongRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AlbumArtFetcher(
    private val uri: Uri,
    private val context: Context
) : Fetcher {
    private val songRepo = SongRepository.getInstance()

    override suspend fun fetch(): FetchResult? {
        if (uri.authority != "media") return null

        val songId = uri.lastPathSegment?.toLongOrNull() ?: return null
        val song = songRepo.resolveSong(songId) ?: return null
        val key = "song_${song.id}_${song.dateModified}"

        Log.d("AlbumArtFetcher", "fetch called for $key uri=$uri")

        val cacheFile = cacheFileFor(key)

        if (cacheFile.exists() && cacheFile.length() > 0) {
            return sourceResult(cacheFile, DataSource.DISK)
        }

        val sentinel = noArtSentinelFor(key)
        if (sentinel.exists()) {
            Log.d("AlbumArtFetcher", "sentinel exists, skipping: $key")
            return null
        }

        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(context, uri)
            val art = mmr.embeddedPicture
            Log.d("AlbumArtFetcher", "MMR result for $songId: ${if (art != null) "${art.size} bytes" else "null"}")
            if (art != null && art.isNotEmpty()) {
                cacheFile.writeBytes(art)
                return sourceResult(cacheFile, DataSource.DISK)
            }
        } catch (e: Exception) {
            Log.d("AlbumArtFetcher", "MMR exception for $key", e)
        } finally { mmr.release() }

        val albumId = resolveAlbumId(uri)
        if (albumId != null) {
            try {
                context.contentResolver.openInputStream(
                    "content://media/external/audio/albumart/$albumId".toUri()
                )?.use { stream ->
                    val bytes = stream.readBytes()
                    if (bytes.isNotEmpty()) {
                        cacheFile.writeBytes(bytes)
                        return sourceResult(cacheFile, DataSource.NETWORK)
                    }
                }
            } catch (e: Exception) {
                Log.d("AlbumArtFetcher", "Album art fallback failed for $key", e)
            }
        }

        withContext(Dispatchers.IO) {
            sentinel.parentFile?.mkdirs()
            sentinel.createNewFile()
        }

        return null
    }

    private fun noArtSentinelFor(key: String) =
        File(context.cacheDir, "album_art/$key.none")

    private fun resolveAlbumId(uri: Uri): String? {
        val projection = arrayOf(MediaStore.Audio.Media.ALBUM_ID)

        return context.contentResolver.query(uri, projection, null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0).toString()
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

    class Factory(
        private val context: Context
    ) : Fetcher.Factory<Uri> {

        override fun create(
            data: Uri,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher? {
            if (data.authority != "media") return null
            if (!data.pathSegments.contains("audio")) return null

            return AlbumArtFetcher(data, context)
        }
    }
}