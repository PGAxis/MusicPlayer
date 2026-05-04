package com.pg_axis.musicaxs.side_pages

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.pg_axis.musicaxs.models.Song
import com.pg_axis.musicaxs.repositories.SongRepository
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

    private val _isScrubbing = MutableStateFlow(false)

    private var lastScrubPositionMs: Long = 0L

    init {
        _positionMs.value = settings.lastPositionMs
        _durationMs.value = settings.lastDurationMs

        _uiState.value = getPlayTypeByNum(settings.repeatMode)

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

    fun changePlayType() {
        val next = getNextPlayType()
        Log.d("Repeat type", "$next")
        _uiState.value = next

        MusicService.setRepeatMode(when (next) {
            PlayType.Repeat -> Player.REPEAT_MODE_ALL
            PlayType.RepeatOnce -> Player.REPEAT_MODE_ONE
            PlayType.Continue -> Player.REPEAT_MODE_OFF
        })
    }

    private fun getNextPlayType() = when (_uiState.value) {
        PlayType.Repeat -> PlayType.RepeatOnce
        PlayType.RepeatOnce -> PlayType.Continue
        PlayType.Continue -> PlayType.Repeat
    }

    private fun getPlayTypeByNum(mode: Int) = when (mode) {
        0 -> PlayType.Continue
        1 -> PlayType.RepeatOnce
        2 -> PlayType.Repeat
        else -> PlayType.Continue
    }

    fun resolveSongFromUri(uri: Uri): Song? =
        SongRepository.getInstance().songs.value.find { it.uri == uri }
}