package com.pg_axis.musicaxs.side_pages

import android.app.Application
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pg_axis.musicaxs.models.Playlist
import com.pg_axis.musicaxs.models.Song
import com.pg_axis.musicaxs.settings.FavouritesSave
import com.pg_axis.musicaxs.settings.PlayCountTracker
import com.pg_axis.musicaxs.settings.PlaylistRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface DetailUiState {
    data object Loading : DetailUiState
    data class Ready(val name: String, val songs: List<Song>) : DetailUiState
}

class PlaylistsDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    private val repo = PlaylistRepository.getInstance(getApplication())
    private val tracker = PlayCountTracker.getInstance(getApplication())

    private var currentPlaylistId: Long = -1L

    fun initPlaylist(id: Long) {
        currentPlaylistId = id
        when (id) {
            0L -> {
                val favSave = FavouritesSave.getInstance(getApplication())
                viewModelScope.launch {
                    favSave.orderedIdsFlow.collectLatest { ids ->
                        val songs = withContext(Dispatchers.IO) {
                            ids.mapNotNull { id ->
                                val uri = ContentUris.withAppendedId(
                                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                                querySong(getApplication(), uri)
                            }
                        }
                        _uiState.value = DetailUiState.Ready("Favourite tracks", songs)
                    }
                }
            }
            1L, 2L, 3L -> {
                viewModelScope.launch(Dispatchers.IO) {
                    val songs = when (id) {
                        1L -> queryRecentlyAdded(getApplication())
                        2L -> getSongsForPlaylist(getApplication(), tracker.recentlyPlayed())
                        else -> getSongsForPlaylist(getApplication(), tracker.topPlayed())
                    }
                    val name = when (id) {
                        1L -> "Recently Added"
                        2L -> "Recently Played"
                        else -> "Most Played"
                    }
                    _uiState.value = DetailUiState.Ready(name, songs)
                }
            }
            else -> {
                viewModelScope.launch {
                    repo.playlists.collectLatest { playlists ->
                        val playlist = playlists.find { it.id == id } ?: return@collectLatest
                        val songs = withContext(Dispatchers.IO) {
                            getSongsForPlaylist(getApplication(), playlist)
                        }
                        _uiState.value = DetailUiState.Ready(playlist.name, songs)
                    }
                }
            }
        }
    }

    private fun queryRecentlyAdded(context: Context, limit: Int = 50): List<Song> {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED
        )
        val songs = mutableListOf<Song>()
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            null, null,
            "${MediaStore.Audio.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            var count = 0
            while (cursor.moveToNext() && count < limit) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                val albumId = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID))
                songs.add(Song(
                    id = id,
                    title = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE))  ?: "Unknown",
                    artist = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)) ?: "Unknown",
                    album = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM))  ?: "Unknown",
                    uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                    durationMs = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)),
                    albumArtUri = ContentUris.withAppendedId("content://media/external/audio/albumart".toUri(), albumId)
                ))
                count++
            }
        }
        return songs
    }

    fun getSongsForPlaylist(context: Context, list: List<String>): List<Song> {
        return list.mapNotNull { uriString ->
            val uri = uriString.toUri()
            querySong(context, uri)
        }
    }

    fun getSongsForPlaylist(context: Context, playlist: Playlist): List<Song> {
        return playlist.songIds.mapNotNull { id ->
            val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
            querySong(context, uri)
        }
    }

    fun removeSong(playlistId: Long, index: Int) {
        viewModelScope.launch(Dispatchers.IO) { repo.removeSongAt(playlistId, index) }
    }

    fun moveSong(playlistId: Long, reorderedSongs: List<Song>) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.reorderSongs(playlistId, reorderedSongs.map { it.id })
        }
    }

    private fun querySong(context: Context, uri: Uri): Song? {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION
        )
        return context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
            val albumId = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID))
            Song(
                id = id,
                title = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE))  ?: "Unknown",
                artist = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)) ?: "Unknown",
                album = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM))  ?: "Unknown",
                uri = uri,
                durationMs = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)),
                albumArtUri = ContentUris.withAppendedId("content://media/external/audio/albumart".toUri(), albumId)
            )
        }
    }
}