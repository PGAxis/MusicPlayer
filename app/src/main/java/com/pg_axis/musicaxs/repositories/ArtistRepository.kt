package com.pg_axis.musicaxs.repositories

import com.pg_axis.musicaxs.models.Artist
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ArtistRepository private constructor() {

    companion object {
        @Volatile
        private var instance: ArtistRepository? = null

        fun getInstance(): ArtistRepository =
            instance ?: synchronized(this) {
                instance ?: ArtistRepository().also { instance = it }
            }
    }

    private val _artists = MutableStateFlow<List<Artist>>(emptyList())
    val artists: StateFlow<List<Artist>> = _artists.asStateFlow()

    fun update(artists: List<Artist>) {
        _artists.value = artists
    }
}