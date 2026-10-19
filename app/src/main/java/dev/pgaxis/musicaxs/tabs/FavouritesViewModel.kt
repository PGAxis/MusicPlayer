package dev.pgaxis.musicaxs.tabs

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.pgaxis.musicaxs.models.Playlist
import dev.pgaxis.musicaxs.settings.FavouritedPlaylistsSave
import dev.pgaxis.musicaxs.settings.FavouritesSave
import dev.pgaxis.musicaxs.repositories.PlaylistRepository
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
    val context = getApplication<Application>()

    private val favourites = FavouritesSave.getInstance(context)
    private val favPlaylists = FavouritedPlaylistsSave.getInstance(context)
    private val repo = PlaylistRepository.getInstance(context)

    private val _uiState = MutableStateFlow<FavouritesUiState>(FavouritesUiState.Loading)
    val uiState: StateFlow<FavouritesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                favourites.orderedIdsFlow,
                favPlaylists.idsFlow,
                repo.playlists
            ) { trackIds, favPlaylistIds, allPlaylists ->
                buildList {
                    if (trackIds.isNotEmpty())
                        add(Playlist(id = 0, name = "", songIds = trackIds))
                    favPlaylistIds.forEach { id ->
                        allPlaylists.find { it.id == id }?.let { add(it) }
                    }
                }
            }.collectLatest { playlists ->
                _uiState.value = FavouritesUiState.Ready(playlists)
            }
        }
    }
}