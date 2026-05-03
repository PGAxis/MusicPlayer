package com.pg_axis.musicaxs.side_pages

import android.app.Application
import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pg_axis.musicaxs.models.Playlist
import com.pg_axis.musicaxs.models.Song
import com.pg_axis.musicaxs.settings.FavouritesSave
import com.pg_axis.musicaxs.settings.PlayCountTracker
import com.pg_axis.musicaxs.repositories.PlaylistRepository
import com.pg_axis.musicaxs.repositories.SongRepository
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
    private val songRepo = SongRepository.getInstance()
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
                                querySong(uri)
                            }
                        }
                        _uiState.value = DetailUiState.Ready("Favourite tracks", songs)
                    }
                }
            }
            1L, 2L, 3L -> {
                viewModelScope.launch(Dispatchers.IO) {
                    val songs = when (id) {
                        1L -> queryRecentlyAdded()
                        2L -> getSongsForPlaylist(tracker.recentlyPlayed())
                        else -> getSongsForPlaylist(tracker.topPlayed())
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
                            getSongsForPlaylist(playlist)
                        }
                        _uiState.value = DetailUiState.Ready(playlist.name, songs)
                    }
                }
            }
        }
    }

    private fun queryRecentlyAdded(limit: Int = 50): List<Song> =
        songRepo.songs.value
            .sortedByDescending { it.dateAdded }
            .take(limit)

    fun getSongsForPlaylist(list: List<String>): List<Song> {
        return list.mapNotNull { uriString ->
            val uri = uriString.toUri()
            querySong(uri)
        }
    }

    fun getSongsForPlaylist(playlist: Playlist): List<Song> {
        return playlist.songIds.mapNotNull { id ->
            val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
            querySong(uri)
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

    private fun querySong(uri: Uri): Song? =
        songRepo.songs.value.find { it.uri == uri }
}