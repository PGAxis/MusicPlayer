package dev.pgaxis.musicaxs.services

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.core.net.toUri
import dev.pgaxis.musicaxs.models.Song
import dev.pgaxis.musicaxs.repositories.PlaylistRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

object AlbumArtPreloader {
    private var preloadJob: Job? = null
    private var cleanupJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cacheMutex = Mutex()

    fun preloadAll(context: Context, songs: List<Song>) {
        preloadJob?.cancel()

        val appContext = context.applicationContext
        val requestedSongs = songs.toList()

        preloadJob = scope.launch {
            delay(500.milliseconds)
            cacheMutex.withLock {
                val dir = File(appContext.cacheDir, "album_art")
                    .also { it.mkdirs() }
                Log.d("Preloader", "Started preloading...")

                coroutineScope {
                    requestedSongs
                        .filter {
                            !File(dir, "song_${it.id}_${it.dateModified}.jpg").exists() &&
                            !File(dir, "song_${it.id}_${it.dateModified}.none").exists()
                        }
                        .forEach { song ->
                            ensureActive()
                            launch {
                                extractAndCache(appContext, song, dir)
                            }
                        }
                }
            }
        }
    }

    fun cleanup(context: Context, songs: List<Song>) {
        cleanupJob?.cancel()

        val appContext = context.applicationContext
        val requestedSongs = songs.toList()

        cleanupJob = scope.launch {
            delay(500.milliseconds)
            cacheMutex.withLock {
                ensureActive()

                val dir = File(appContext.cacheDir, "album_art")
                    .takeIf { it.exists() }

                if (dir != null) {
                    val validCacheFiles = requestedSongs.flatMap { song ->
                        listOf(
                            "song_${song.id}_${song.dateModified}.jpg",
                            "song_${song.id}_${song.dateModified}.none"
                        )
                    }.toSet()

                    dir.listFiles()?.forEach { file ->
                        ensureActive()
                        if (file.name !in validCacheFiles) {
                            Log.d("Preloader", "${file.name} is not a valid file anymore")
                            file.delete()
                        }
                    }
                }

                val validIds = requestedSongs.map { it.id }.toSet()
                val playlistRepo = PlaylistRepository.getInstance(appContext)

                playlistRepo.playlists.value.forEach { playlist ->
                    playlist.songIds.toList().forEach { songId ->
                        ensureActive()
                        if (songId !in validIds) {
                            Log.d("Preloader", "$songId")
                            playlistRepo.removeSong(playlist.id, songId)
                        }
                    }
                }
            }
        }
    }

    private fun extractAndCache(context: Context, song: Song, dir: File) {
        val cacheFile = File(dir, "song_${song.id}_${song.dateModified}.jpg")
        val sentinel = File(dir, "song_${song.id}_${song.dateModified}.none")

        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(context, song.uri)
            val art = mmr.embeddedPicture
            if (art != null && art.isNotEmpty()) {
                // needs to delete both .none (if there were any) and .jpg (if there were any)
                deleteOldCacheFiles(dir, song.id)
                cacheFile.writeBytes(art)
                return
            }
        } catch (_: Exception) {}
        finally { mmr.release() }

        val albumId = resolveAlbumId(context, song.uri)
        if (albumId != null) {
            try {
                val uri = "content://media/external/audio/albumart/$albumId".toUri()
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bytes = stream.readBytes()
                    if (bytes.isNotEmpty()) {
                        // needs to delete both .none (if there were any) and .jpg (if there were any)
                        deleteOldCacheFiles(dir, song.id)
                        cacheFile.writeBytes(bytes)
                        return
                    }
                }
            } catch (_: Exception) {}
        }

        // needs to delete both .none (if there were any) and .jpg (if there were any)
        deleteOldCacheFiles(dir, song.id)
        sentinel.createNewFile()
    }

    private fun resolveAlbumId(context: Context, uri: Uri): String? {
        val projection = arrayOf(MediaStore.Audio.Media.ALBUM_ID)
        return context.contentResolver.query(uri, projection, null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0).toString() else null
            }
    }

    private fun deleteOldCacheFiles(dir: File, songId: Long) {
        dir.listFiles { file ->
            file.name.startsWith("song_${songId}") &&
                    (file.extension == "jpg" || file.extension == "none")
        }?.forEach { it.delete() }
    }
}
