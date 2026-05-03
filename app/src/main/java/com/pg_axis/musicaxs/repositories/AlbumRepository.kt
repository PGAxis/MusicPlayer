package com.pg_axis.musicaxs.repositories

import com.pg_axis.musicaxs.models.Album
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AlbumRepository private constructor() {

    companion object {
        @Volatile
        private var instance: AlbumRepository? = null

        fun getInstance(): AlbumRepository =
            instance ?: synchronized(this) {
                instance ?: AlbumRepository().also { instance = it }
            }
    }

    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums.asStateFlow()

    fun update(albums: List<Album>) {
        _albums.value = albums
    }
}