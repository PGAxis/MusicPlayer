package com.pg_axis.musicaxs.tabs

import android.app.Application
import android.content.ContentUris
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pg_axis.musicaxs.models.Album
import com.pg_axis.musicaxs.models.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.core.net.toUri

sealed interface AlbumsUiState {
    data object Loading : AlbumsUiState
    data class Ready(val albums: List<Album>) : AlbumsUiState
    data class Error(val message: String) : AlbumsUiState
}

class AlbumsViewModel(app: Application) : AndroidViewModel(app) {

    private val _uiState = MutableStateFlow<AlbumsUiState>(AlbumsUiState.Loading)
    val uiState: StateFlow<AlbumsUiState> = _uiState.asStateFlow()

    private val _selectedAlbum = MutableStateFlow<Album?>(null)
    val selectedAlbum: StateFlow<Album?> = _selectedAlbum.asStateFlow()

    private val _albumSongs = MutableStateFlow<List<Song>>(emptyList())
    val albumSongs: StateFlow<List<Song>> = _albumSongs.asStateFlow()

    init {
        loadAlbums()
    }

    fun onAlbumSelected(album: Album) {
        _selectedAlbum.value = album
        loadSongsForAlbum(album)
    }

    fun onBackFromDetail() {
        _selectedAlbum.value = null
        _albumSongs.value = emptyList()
    }

    private fun loadAlbums() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.value = AlbumsUiState.Ready(queryAlbums())
            } catch (e: Exception) {
                _uiState.value = AlbumsUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun queryAlbums(): List<Album> {
        val context = getApplication<Application>()

        val projection = arrayOf(
            MediaStore.Audio.Albums._ID,
            MediaStore.Audio.Albums.ALBUM,
            MediaStore.Audio.Albums.ARTIST,
            MediaStore.Audio.Albums.NUMBER_OF_SONGS,
        )

        val albums = mutableListOf<Album>()

        context.contentResolver.query(
            MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Audio.Albums.ALBUM} ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.ARTIST)
            val songCountCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.NUMBER_OF_SONGS)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                albums.add(
                    Album(
                        id = id,
                        name = cursor.getString(nameCol) ?: "Unknown",
                        artist = cursor.getString(artistCol) ?: "Unknown",
                        songCount = cursor.getInt(songCountCol),
                        albumArtUri = ContentUris.withAppendedId(
                            "content://media/external/audio/albumart".toUri(), id
                        )
                    )
                )
            }
        }

        return albums
    }

    private fun loadSongsForAlbum(album: Album) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _albumSongs.value = querySongsForAlbum(album.id)
            } catch (_: Exception) {
                _albumSongs.value = emptyList()
            }
        }
    }

    private fun querySongsForAlbum(albumId: Long): List<Song> {
        val context = getApplication<Application>()

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
        )

        val songs = mutableListOf<Song>()

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.Audio.Media.ALBUM_ID} = ? AND ${MediaStore.Audio.Media.IS_MUSIC} != 0",
            arrayOf(albumId.toString()),
            "${MediaStore.Audio.Media.TRACK} ASC" // sort by track number
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val albId = cursor.getLong(albumIdCol)
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
                            "content://media/external/audio/albumart".toUri(), albId)
                    )
                )
            }
        }

        return songs
    }
}