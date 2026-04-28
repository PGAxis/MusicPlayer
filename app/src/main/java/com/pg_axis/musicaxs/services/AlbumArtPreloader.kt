package com.pg_axis.musicaxs.services

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.core.net.toUri
import com.pg_axis.musicaxs.models.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

object AlbumArtPreloader {
    suspend fun preloadAll(context: Context, songs: List<Song>) =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "album_art").also { it.mkdirs() }

            songs
                .filter {
                    // skip songs already cached or already known to have no art
                    !File(dir, "song_${it.id}.jpg").exists() &&
                            !File(dir, "song_${it.id}.none").exists()
                }
                .forEach { song ->
                    launch { extractAndCache(context, song, dir) }
                }
        }

    private fun extractAndCache(context: Context, song: Song, dir: File) {
        val cacheFile = File(dir, "song_${song.id}.jpg")
        val sentinel  = File(dir, "song_${song.id}.none")

        // 1. Try the fast albumart URI first
        val albumId = resolveAlbumId(context, song.uri)
        if (albumId != null) {
            try {
                val uri = "content://media/external/audio/albumart/$albumId".toUri()
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bytes = stream.readBytes()
                    if (bytes.isNotEmpty()) {
                        cacheFile.writeBytes(bytes)
                        return
                    }
                }
            } catch (_: Exception) {}
        }

        // 2. Fall back to MMR (only once, ever)
        try {
            val mmr = MediaMetadataRetriever()
            mmr.setDataSource(context, song.uri)
            val art = mmr.embeddedPicture
            mmr.release()
            if (art != null && art.isNotEmpty()) {
                cacheFile.writeBytes(art)
                return
            }
        } catch (_: Exception) {}

        // 3. No art found — write sentinel so we never probe again
        sentinel.createNewFile()
    }

    private fun resolveAlbumId(context: Context, uri: Uri): String? {
        val projection = arrayOf(MediaStore.Audio.Media.ALBUM_ID)
        return context.contentResolver.query(uri, projection, null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0).toString() else null
            }
    }
}