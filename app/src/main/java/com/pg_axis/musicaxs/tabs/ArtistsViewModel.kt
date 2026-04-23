package com.pg_axis.musicaxs.tabs

import android.app.Application
import android.content.ContentUris
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pg_axis.musicaxs.models.Artist
import com.pg_axis.musicaxs.models.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.core.net.toUri

sealed interface ArtistsUiState {
    data object Loading : ArtistsUiState
    data class Ready(val interprets: List<Artist>) : ArtistsUiState
    data class Error(val message: String) : ArtistsUiState
}

// Flat list item for the detail screen LazyColumn
sealed interface ArtistDetailItem {
    data class AlbumHeader(val albumName: String) : ArtistDetailItem
    data class SongItem(val song: Song) : ArtistDetailItem
}

class ArtistsViewModel(app: Application) : AndroidViewModel(app) {

    private val _uiState = MutableStateFlow<ArtistsUiState>(ArtistsUiState.Loading)
    val uiState: StateFlow<ArtistsUiState> = _uiState.asStateFlow()

    private val _selectedInterpret = MutableStateFlow<Artist?>(null)
    val selectedInterpret: StateFlow<Artist?> = _selectedInterpret.asStateFlow()

    private val _detailItems = MutableStateFlow<List<ArtistDetailItem>>(emptyList())
    val detailItems: StateFlow<List<ArtistDetailItem>> = _detailItems.asStateFlow()

    init {
        loadInterprets()
    }

    fun onInterpretSelected(interpret: Artist) {
        _selectedInterpret.value = interpret
        loadDetailItems(interpret)
    }

    fun onBackFromDetail() {
        _selectedInterpret.value = null
        _detailItems.value = emptyList()
    }

    private fun loadInterprets() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.value = ArtistsUiState.Ready(queryInterprets())
            } catch (e: Exception) {
                _uiState.value = ArtistsUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun queryInterprets(): List<Artist> {
        val context = getApplication<Application>()

        val projection = arrayOf(
            MediaStore.Audio.Artists._ID,
            MediaStore.Audio.Artists.ARTIST,
            MediaStore.Audio.Artists.NUMBER_OF_TRACKS,
            MediaStore.Audio.Artists.NUMBER_OF_ALBUMS,
        )

        val interprets = mutableListOf<Artist>()

        context.contentResolver.query(
            MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Audio.Artists.ARTIST} ASC"
        )?.use { cursor ->
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Artists.ARTIST)
            val songCountCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Artists.NUMBER_OF_TRACKS)
            val albumCountCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Artists.NUMBER_OF_ALBUMS)

            while (cursor.moveToNext()) {
                interprets.add(
                    Artist(
                        name = cursor.getString(nameCol) ?: "Unknown",
                        songCount = cursor.getInt(songCountCol),
                        albumCount = cursor.getInt(albumCountCol)
                    )
                )
            }
        }

        return interprets
    }

    private fun loadDetailItems(interpret: Artist) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val songs = querySongsForArtist(interpret.name)

                // Group songs by album, preserving track order within each album
                val grouped = songs
                    .groupBy { it.album }
                    .toSortedMap()

                val items = mutableListOf<ArtistDetailItem>()
                grouped.forEach { (albumName, albumSongs) ->
                    items.add(ArtistDetailItem.AlbumHeader(albumName))
                    albumSongs.forEach { items.add(ArtistDetailItem.SongItem(it)) }
                }

                _detailItems.value = items
            } catch (_: Exception) {
                _detailItems.value = emptyList()
            }
        }
    }

    private fun querySongsForArtist(artistName: String): List<Song> {
        val context = getApplication<Application>()

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
        )

        val songs = mutableListOf<Song>()

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.Audio.Media.ARTIST} = ? AND ${MediaStore.Audio.Media.IS_MUSIC} != 0",
            arrayOf(artistName),
            "${MediaStore.Audio.Media.ALBUM} ASC, ${MediaStore.Audio.Media.TRACK} ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val albumId = cursor.getLong(albumIdCol)

                songs.add(
                    Song(
                        id = id,
                        title = cursor.getString(titleCol) ?: "Unknown",
                        artist = cursor.getString(artistCol) ?: "Unknown",
                        album = cursor.getString(albumCol) ?: "Unknown",
                        uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                        durationMs  = cursor.getLong(durationCol),
                        albumArtUri = ContentUris.withAppendedId("content://media/external/audio/albumart".toUri(), albumId)
                    )
                )
            }
        }

        return songs
    }
}