package com.pg_axis.musicaxs.tabs

import android.Manifest
import android.app.Application
import android.content.ContentUris
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pg_axis.musicaxs.models.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.core.net.toUri
import java.io.File

// MIME types
private val SUPPORTED_MIME_TYPES = setOf(
    "audio/mpeg",       // mp3
    "audio/wav",        // wav
    "audio/x-wav",      // wav (alternate)
    "audio/flac",       // flac
    "audio/x-flac",     // flac (alternate)
    "audio/ogg",        // ogg vorbis
    "audio/mp4",        // m4a / aac
    "audio/m4a",        // m4a (alternate)
)

sealed interface SongsUiState {
    data object Loading : SongsUiState
    data object PermissionRequired : SongsUiState
    data class Ready(val songs: List<Song>) : SongsUiState
    data class Error(val message: String) : SongsUiState
}

class SongsViewModel(app: Application) : AndroidViewModel(app) {

    private val _uiState = MutableStateFlow<SongsUiState>(SongsUiState.Loading)
    val uiState: StateFlow<SongsUiState> = _uiState.asStateFlow()

    // Fires whenever MediaStore's audio table changes (file added / deleted / renamed)
    private val mediaObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            scanSongs()
        }
    }

    private fun hasPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_AUDIO
        else
            Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(
            getApplication(), permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    init {
        getApplication<Application>().contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            true,
            mediaObserver
        )
        if (hasPermission()) {
            scanSongs()
        } else {
            _uiState.value = SongsUiState.PermissionRequired
        }
    }

    fun scanSongs() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = SongsUiState.Loading

            try {
                val songs = querySongs()
                _uiState.value = SongsUiState.Ready(songs)

                prewarmAlbumArtCache(songs)
            } catch (_: SecurityException) {
                // READ_MEDIA_AUDIO / READ_EXTERNAL_STORAGE not granted yet
                _uiState.value = SongsUiState.PermissionRequired
            } catch (e: Exception) {
                _uiState.value = SongsUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun prewarmAlbumArtCache(songs: List<Song>) {
        val context = getApplication<Application>()
        val cacheDir = File(context.cacheDir, "album_art").also { it.mkdirs() }

        val toProcess = songs.filter { it.albumArtUri != null }.distinctBy { it.albumArtUri } +
                songs.filter { it.albumArtUri == null }

        toProcess
            .filter { song ->
                val songId = song.uri.lastPathSegment ?: return@filter false
                !File(cacheDir, "song_$songId.jpg").exists()
            }
            .forEach { song ->
                val songId = song.uri.lastPathSegment ?: return@forEach
                val cacheFile = File(cacheDir, "song_$songId.jpg")

                try {
                    song.albumArtUri?.let { artUri ->
                        context.contentResolver.openInputStream(artUri)?.use { stream ->
                            val bytes = stream.readBytes()
                            if (bytes.isNotEmpty()) {
                                cacheFile.writeBytes(bytes)
                                return@forEach
                            }
                        }
                    }

                    val mmr = MediaMetadataRetriever()
                    mmr.setDataSource(context, song.uri)
                    val art = mmr.embeddedPicture
                    mmr.release()
                    if (art != null && art.isNotEmpty()) {
                        cacheFile.writeBytes(art)
                    }
                } catch (_: Exception) { }
            }
    }

    private fun querySongs(): List<Song> {
        val context = getApplication<Application>()

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.MIME_TYPE,
        )

        val songs = mutableListOf<Song>()

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            "${MediaStore.Audio.Media.TITLE} ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)

            while (cursor.moveToNext()) {
                val mimeType = cursor.getString(mimeCol) ?: continue
                if (mimeType !in SUPPORTED_MIME_TYPES) continue

                val id = cursor.getLong(idCol)
                val albumId = cursor.getLong(albumIdCol)

                songs.add(
                    Song(
                        id = id,
                        title = cursor.getString(titleCol) ?: "Unknown",
                        artist = cursor.getString(artistCol) ?: "Unknown",
                        album = cursor.getString(albumCol) ?: "Unknown",
                        uri = ContentUris.withAppendedId(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                        durationMs = cursor.getLong(durationCol),
                        albumArtUri = ContentUris.withAppendedId(
                            "content://media/external/audio/albumart".toUri(), albumId)
                    )
                )
            }
        }

        return songs
    }

    override fun onCleared() {
        super.onCleared()
        getApplication<Application>().contentResolver.unregisterContentObserver(mediaObserver)
    }
}