package com.pg_axis.musicaxs.repositories

import com.pg_axis.musicaxs.models.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SongRepository private constructor() {

    companion object {
        @Volatile
        private var instance: SongRepository? = null

        fun getInstance(): SongRepository =
            instance ?: synchronized(this) {
                instance ?: SongRepository().also { instance = it }
            }
    }

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    fun update(songs: List<Song>) {
        _songs.value = songs
    }
}