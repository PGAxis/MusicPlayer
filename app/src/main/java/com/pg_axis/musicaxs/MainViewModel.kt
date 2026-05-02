package com.pg_axis.musicaxs

import android.app.Application
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
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
import com.pg_axis.musicaxs.settings.SettingsSave
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.core.net.toUri
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import com.pg_axis.musicaxs.models.Playlist
import com.pg_axis.musicaxs.settings.PlaylistRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class CurrentSong(
    val title: String = "Song Title",
    val artist: String = "Artist",
    val songUri: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val settings = SettingsSave.getInstance(application)
    private val repo = PlaylistRepository.getInstance(application)

    val tabs = listOf("Favourites", "Playlists", "Songs", "Albums", "Artists")

    // Index of the last active tab, persisted via settings
    private val _currentPageIndex = MutableStateFlow(settings.lastTabIndex)
    val currentPageIndex: StateFlow<Int> = _currentPageIndex.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    var isPlaying by mutableStateOf(false)
        private set

    private val _currentSong = MutableStateFlow<CurrentSong?>(null)
    val currentSong: StateFlow<CurrentSong?> = _currentSong.asStateFlow()

    // Called when the pager settles
    fun onPageChanged(index: Int) {
        _currentPageIndex.value = index
        settings.lastTabIndex = index
        settings.save()
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
        settings.lastSongUri = song.uri.toString()
        settings.save()
    }

    fun resolveSongFromUri(context: Context, uri: Uri): Song? {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
        )
        return context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
            val albumId = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID))
            Song(
                id = id,
                title = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE))  ?: "Unknown",
                artist = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)) ?: "Unknown",
                album = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM))  ?: "Unknown",
                uri = uri,
                durationMs = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)),
                albumArtUri = ContentUris.withAppendedId("content://media/external/audio/albumart".toUri(), albumId)
            )
        }
    }

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

                override fun onMediaItemTransition(
                    mediaItem: MediaItem?,
                    reason: Int
                ) {
                    mediaItem ?: return

                    val uri = mediaItem.localConfiguration?.uri ?: return
                    val song = resolveSongFromUri(application, uri) ?: return

                    setSong(song)
                }
            })
        }, ContextCompat.getMainExecutor(context))

        val lastUri = settings.lastSongUri
        if (lastUri.isNotEmpty()) {
            viewModelScope.launch(Dispatchers.IO) {
                val song = resolveSongFromUri(context, lastUri.toUri())
                if (song != null) {
                    _currentSong.value = CurrentSong(
                        title = song.title,
                        artist = song.artist,
                        songUri = song.uri.toString()
                    )
                }
            }
        }
    }

    fun onPrevious() {
        MusicService.previous()
    }
    fun onNext() {
        MusicService.next()
    }
    fun createAndGetPlaylist(name: String): Playlist {
        return repo.create(name)
    }
}