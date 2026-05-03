package com.pg_axis.musicaxs.side_pages

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pg_axis.musicaxs.models.Album
import com.pg_axis.musicaxs.models.Song
import com.pg_axis.musicaxs.repositories.AlbumRepository
import com.pg_axis.musicaxs.repositories.SongRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class AlbumDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val _albumId = MutableStateFlow<Long?>(null)

    val album: StateFlow<Album?> = combine(
        _albumId,
        AlbumRepository.getInstance().albums
    ) { id, albums -> albums.find { it.id == id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val songs: StateFlow<List<Song>> = combine(
        _albumId,
        SongRepository.getInstance().songs
    ) { id, songs ->
        id ?: return@combine emptyList()
        songs.filter { it.albumId == id }.sortedBy { it.track }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun init(albumId: Long) {
        _albumId.value = albumId
    }
}