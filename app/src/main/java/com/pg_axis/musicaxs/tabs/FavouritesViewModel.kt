package com.pg_axis.musicaxs.tabs

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pg_axis.musicaxs.models.Playlist
import com.pg_axis.musicaxs.settings.FavouritedPlaylistsSave
import com.pg_axis.musicaxs.settings.FavouritesSave
import com.pg_axis.musicaxs.repositories.PlaylistRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

sealed interface FavouritesUiState {
    data object Loading : FavouritesUiState
    data class Ready(val playlists: List<Playlist>) : FavouritesUiState
    data class Error(val message: String) : FavouritesUiState
}

class FavouritesViewModel(app: Application) : AndroidViewModel(app) {
    private val favourites = FavouritesSave.getInstance(getApplication())
    private val favPlaylists = FavouritedPlaylistsSave.getInstance(getApplication())
    private val repo = PlaylistRepository.getInstance(getApplication())

    private val _uiState = MutableStateFlow<FavouritesUiState>(FavouritesUiState.Loading)
    val uiState: StateFlow<FavouritesUiState> = _uiState.asStateFlow()

    private val _selectedPlaylist= MutableStateFlow<Playlist?>(null)
    val selectedPlaylist: StateFlow<Playlist?> = _selectedPlaylist.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                favourites.orderedIdsFlow,
                favPlaylists.idsFlow,
                repo.playlists
            ) { trackIds, favPlaylistIds, allPlaylists ->
                buildList {
                    if (trackIds.isNotEmpty())
                        add(Playlist(id = 0, name = "Favourite tracks", songIds = trackIds))
                    favPlaylistIds.forEach { id ->
                        allPlaylists.find { it.id == id }?.let { add(it) }
                    }
                }
            }.collectLatest { playlists ->
                _uiState.value = FavouritesUiState.Ready(playlists)
            }
        }
    }

    fun onBackFromDetail() {
        _selectedPlaylist.value = null
    }
}