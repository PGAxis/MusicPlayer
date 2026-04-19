package com.pg_axis.musicaxs

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CurrentSong(
    val title: String = "Song Title",
    val artist: String = "Artist",
    val albumArtPath: String? = null
)

class MainViewModel : ViewModel() {

    val tabs = listOf("Favourites", "Playlists", "Songs", "Albums", "Interprets")

    // Index of the last active tab, persisted via settings later
    private val _currentPageIndex = MutableStateFlow(2) // Default: Songs
    val currentPageIndex: StateFlow<Int> = _currentPageIndex.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentSong = MutableStateFlow(CurrentSong())
    val currentSong: StateFlow<CurrentSong> = _currentSong.asStateFlow()

    // Called when the pager settles on a new page
    fun onPageChanged(index: Int) {
        _currentPageIndex.value = index
    }

    fun onPlayPause() {
        _isPlaying.value = !_isPlaying.value
    }

    fun onPrevious() { /* TODO */ }
    fun onNext()     { /* TODO */ }
    fun onSearch()   { /* TODO */ }
    fun onSettings() { /* TODO */ }
    fun onAddPlaylist() { /* TODO */ }
}