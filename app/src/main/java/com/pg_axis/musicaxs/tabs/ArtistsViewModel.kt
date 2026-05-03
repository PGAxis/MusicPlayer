package com.pg_axis.musicaxs.tabs

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pg_axis.musicaxs.models.Artist
import com.pg_axis.musicaxs.models.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.pg_axis.musicaxs.repositories.ArtistRepository

sealed interface ArtistsUiState {
    data object Loading : ArtistsUiState
    data class Ready(val interprets: List<Artist>) : ArtistsUiState
    data class Error(val message: String) : ArtistsUiState
}

sealed interface ArtistDetailItem {
    data class AlbumHeader(val albumName: String) : ArtistDetailItem
    data class SongItem(val song: Song) : ArtistDetailItem
}

class ArtistsViewModel(app: Application) : AndroidViewModel(app) {
    private val _uiState = MutableStateFlow<ArtistsUiState>(ArtistsUiState.Loading)
    val uiState: StateFlow<ArtistsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            ArtistRepository.getInstance().artists.collect { artists ->
                if (artists.isNotEmpty()) _uiState.value = ArtistsUiState.Ready(artists)
            }
        }
    }
}