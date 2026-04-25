package com.pg_axis.musicaxs.tabs

import android.app.Application
import android.content.ContentUris
import android.provider.MediaStore
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pg_axis.musicaxs.models.Playlist
import com.pg_axis.musicaxs.models.Song
import com.pg_axis.musicaxs.settings.FavouritesSave
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

sealed interface FavouritesUiState {
    data object Loading : FavouritesUiState
    data class Ready(val playlists: List<Playlist>) : FavouritesUiState
    data class Error(val message: String) : FavouritesUiState
}

class FavouritesViewModel(app: Application) : AndroidViewModel(app) {
    private val favourites = FavouritesSave.getInstance(getApplication())

    private val _uiState = MutableStateFlow<FavouritesUiState>(FavouritesUiState.Loading)
    val uiState: StateFlow<FavouritesUiState> = _uiState.asStateFlow()

    private val _selectedPlaylist= MutableStateFlow<Playlist?>(null)
    val selectedPlaylist: StateFlow<Playlist?> = _selectedPlaylist.asStateFlow()

    private val _playlistSongs = MutableStateFlow<List<Song>>(emptyList())
    val playlistSongs: StateFlow<List<Song>> = _playlistSongs.asStateFlow()

    init {
        observeFavourites()
    }

    fun onPlaylistSelected(playlist: Playlist) {
        _selectedPlaylist.value = playlist
        loadSongsForPlaylist(playlist)
    }

    private fun observeFavourites() {
        viewModelScope.launch {
            favourites.orderedIdsFlow.collectLatest { ids ->
                val playlists = buildList {
                    if (ids.isNotEmpty()) add(Playlist(id = 0, name = "Favourite tracks", songIds = ids))
                }

                _uiState.value = FavouritesUiState.Ready(playlists)
            }
        }
    }

    fun loadSongsForPlaylist(playlist: Playlist) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _playlistSongs.value = querySongsForPlaylist(playlist.songIds)
            } catch (_: Exception) {
                _playlistSongs.value = emptyList()
            }
        }
    }

    private fun querySongsForPlaylist(songIds: List<Long>): List<Song> {
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
            "${MediaStore.Audio.Media._ID} IN (${songIds.joinToString(",")}) AND ${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            "${MediaStore.Audio.Media._ID} ASC" // sort by id
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

    fun onBackFromDetail() {
        _selectedPlaylist.value = null
        _playlistSongs.value = emptyList()
    }
}