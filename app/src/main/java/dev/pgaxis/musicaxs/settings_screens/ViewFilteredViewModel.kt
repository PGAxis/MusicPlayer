package dev.pgaxis.musicaxs.settings_screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.pgaxis.musicaxs.models.Song
import dev.pgaxis.musicaxs.repositories.SongRepository
import dev.pgaxis.musicaxs.settings.SettingsSave
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class ViewFilteredViewModel(application: Application) : AndroidViewModel(application) {
    private val context = getApplication<Application>()

    val settings = SettingsSave.getInstance(context)
    private val songRepo = SongRepository.getInstance()

    val songs: StateFlow<List<Song>> = songRepo.filteredSongs
        .map { list -> list.sortedBy { it.title.lowercase() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun remove(id: Long) {
        val newIds = settings.filteredSongs - id
        settings.filteredSongs = newIds
    }
}