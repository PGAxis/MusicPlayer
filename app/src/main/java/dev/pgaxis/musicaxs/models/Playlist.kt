package dev.pgaxis.musicaxs.models

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Playlist(
    val id: Long,
    val name: String,
    val songIds: List<Long>
) {
    fun getSongCount(): Int {
        return songIds.size
    }

    fun setSongCount(count: Int) {
        _songCount.value = count
        Log.d("Playlist", "${getSongCount()}, $count")
    }

    private val _songCount = MutableStateFlow(getSongCount())
    val songCount: StateFlow<Int> = _songCount.asStateFlow()

    fun getImageUri(): Uri {
        return if (songIds.isNotEmpty()) {
            ContentUris.withAppendedId(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                songIds[0]
            )
        } else {
            ContentUris.withAppendedId(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                id
            )
        }
    }
}