package dev.pgaxis.musicaxs.repositories

import android.net.Uri
import dev.pgaxis.musicaxs.models.Song
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

    val isLoaded = MutableStateFlow(false)

    fun update(songs: List<Song>) {
        _songs.value = songs
        isLoaded.value = true
    }

    fun resolveSong(uri: Uri): Song? =
        songs.value.find { it.uri == uri }

    fun resolveSong(id: Long): Song? =
        songs.value.find { it.id == id }
}