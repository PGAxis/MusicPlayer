package com.pg_axis.musicaxs.tabs

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pg_axis.musicaxs.models.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.pg_axis.musicaxs.repositories.SongRepository

sealed interface SongsUiState {
    data object Loading : SongsUiState
    data object PermissionRequired : SongsUiState
    data class Ready(val songs: List<Song>) : SongsUiState
    data class Error(val message: String) : SongsUiState
}

class SongsViewModel(app: Application) : AndroidViewModel(app) {
    private val songRepo = SongRepository.getInstance()

    private val _uiState = MutableStateFlow<SongsUiState>(SongsUiState.Loading)
    val uiState: StateFlow<SongsUiState> = _uiState.asStateFlow()

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
        viewModelScope.launch {
            songRepo.songs.collect { songs ->
                if (songs.isNotEmpty()) _uiState.value = SongsUiState.Ready(songs)
                else if (!hasPermission()) _uiState.value = SongsUiState.PermissionRequired
            }
        }
    }

    fun scanSongs(scan: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = SongsUiState.Loading

            try {
                scan()
            } catch (_: SecurityException) {
                _uiState.value = SongsUiState.PermissionRequired
            } catch (e: Exception) {
                _uiState.value = SongsUiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}