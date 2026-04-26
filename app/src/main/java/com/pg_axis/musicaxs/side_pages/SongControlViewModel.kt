package com.pg_axis.musicaxs.side_pages

import android.app.Application
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pg_axis.musicaxs.models.Song
import com.pg_axis.musicaxs.services.MusicService
import com.pg_axis.musicaxs.settings.FavouritesSave
import com.pg_axis.musicaxs.settings.SettingsSave
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PlayType {
    data object Repeat : PlayType
    data object RepeatOnce : PlayType
    data object Continue : PlayType
}

class SongControlViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = SettingsSave.getInstance(application)
    private val favourites = FavouritesSave.getInstance(application)

    private val _uiState = MutableStateFlow<PlayType>(PlayType.Repeat)
    val uiState: StateFlow<PlayType> = _uiState.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    // Scrubbing state — true while user is dragging the slider
    private val _isScrubbing = MutableStateFlow(false)
    val isScrubbing: StateFlow<Boolean> = _isScrubbing.asStateFlow()

    private var lastScrubPositionMs: Long = 0L

    init {
        _positionMs.value = settings.lastPositionMs
        _durationMs.value = settings.lastDurationMs

        viewModelScope.launch {
            while (true) {
                val player = MusicService.playerInstance
                if (player != null && !_isScrubbing.value) {
                    val duration = player.duration.coerceAtLeast(0L)
                    if (duration > 0L) {
                        _positionMs.value = player.currentPosition
                        _durationMs.value = duration
                        _isPlaying.value  = player.isPlaying
                    }
                }
                delay(200)
            }
        }
    }

    fun onScrub(positionMs: Long) {
        lastScrubPositionMs = positionMs
        _isScrubbing.value = true
        _positionMs.value = positionMs
    }

    fun onScrubStop() {
        _isScrubbing.value = false
        val player = MusicService.playerInstance
        if (player != null && player.duration > 0L) {
            MusicService.seekTo(lastScrubPositionMs)
        } else {
            settings.lastPositionMs = lastScrubPositionMs
            settings.save()
            _positionMs.value = lastScrubPositionMs
        }
    }

    fun onLike() {
        MusicService.like(favourites)
    }

    fun seekBack10() = MusicService.seekBy(-10_000L)
    fun seekForward10() = MusicService.seekBy(10_000L)

    fun changePlayType() { _uiState.value = getNextPlayType() }

    private fun getNextPlayType() = when (_uiState.value) {
        PlayType.Repeat -> PlayType.RepeatOnce
        PlayType.RepeatOnce -> PlayType.Continue
        PlayType.Continue -> PlayType.Repeat
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
                title = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)) ?: "Unknown",
                artist = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)) ?: "Unknown",
                album = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)) ?: "Unknown",
                uri = uri,
                durationMs = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)),
                albumArtUri = ContentUris.withAppendedId("content://media/external/audio/albumart".toUri(), albumId)
            )
        }
    }
}