package com.pg_axis.musicaxs.models

import android.net.Uri

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val uri: Uri,           // content:// URI for playback
    val durationMs: Long,
    val albumArtUri: Uri?,
    val track: Int,
    val dateAdded: Long = 0L
)