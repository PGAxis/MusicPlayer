package com.pg_axis.musicaxs.tabs

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pg_axis.musicaxs.models.Album
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.pg_axis.musicaxs.repositories.AlbumRepository

sealed interface AlbumsUiState {
    data object Loading : AlbumsUiState
    data class Ready(val albums: List<Album>) : AlbumsUiState
    data class Error(val message: String) : AlbumsUiState
}

class AlbumsViewModel(app: Application) : AndroidViewModel(app) {
    private val _uiState = MutableStateFlow<AlbumsUiState>(AlbumsUiState.Loading)
    val uiState: StateFlow<AlbumsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            AlbumRepository.getInstance().albums.collect { albums ->
                if (albums.isNotEmpty()) _uiState.value = AlbumsUiState.Ready(albums)
            }
        }
    }
}