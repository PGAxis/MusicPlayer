package dev.pgaxis.musicaxs.models

data class OrbitSong(
    val orbitId: String,
    val ownerDeviceId: String,
    val localSongId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val hasAlbumArt: Boolean
)