package com.pg_axis.musicaxs.models

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore

data class Playlist(
    val id: Long,
    val name: String,
    val songIds: List<Long>
) {
    fun getSongCount(): Int {
        return songIds.size
    }

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