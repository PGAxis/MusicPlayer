package com.pg_axis.musicaxs

import android.app.Application
import android.content.ComponentName
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.pg_axis.musicaxs.models.Song
import com.pg_axis.musicaxs.services.MusicService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CurrentSong(
    val title: String = "Song Title",
    val artist: String = "Artist",
    val songUri: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val tabs = listOf("Favourites", "Playlists", "Songs", "Albums", "Interprets")

    // Index of the last active tab, persisted via settings later
    private val _currentPageIndex = MutableStateFlow(2) // Default: Songs
    val currentPageIndex: StateFlow<Int> = _currentPageIndex.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    var isPlaying by mutableStateOf(false)
        private set

    init {
        val context = getApplication<Application>()
        val token = SessionToken(context, ComponentName(context, MusicService::class.java))

        controllerFuture = MediaController.Builder(context, token).buildAsync()
        controllerFuture?.addListener({
            controller = controllerFuture?.get()

            controller?.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }
            })
        }, ContextCompat.getMainExecutor(context))
    }

    private val _currentSong = MutableStateFlow<CurrentSong?>(null)
    val currentSong: StateFlow<CurrentSong?> = _currentSong.asStateFlow()

    // Called when the pager settles on a new page
    fun onPageChanged(index: Int) {
        _currentPageIndex.value = index
    }

    fun onPlayPause() {
        controller?.let { player ->
            if (player.isPlaying) {
                player.pause()
            } else {
                player.play()
            }
        }
    }

    fun setSong(song: Song) {
        _currentSong.value = CurrentSong(title = song.title, artist = song.artist, songUri = song.uri.toString())
    }

    fun onPrevious() { /* TODO */ }
    fun onNext()     { /* TODO */ }
    fun onSearch()   { /* TODO */ }
    fun onSettings() { /* TODO */ }
    fun onAddPlaylist() { /* TODO */ }
}