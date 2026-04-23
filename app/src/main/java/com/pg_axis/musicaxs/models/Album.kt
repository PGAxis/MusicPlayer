package com.pg_axis.musicaxs.models

import android.net.Uri

data class Album(
    val id: Long,
    val name: String,
    val artist: String,
    val songCount: Int,
    val albumArtUri: Uri?
)
