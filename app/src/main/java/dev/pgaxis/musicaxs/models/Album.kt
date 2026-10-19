package dev.pgaxis.musicaxs.models

import android.net.Uri
import dev.pgaxis.musicaxs.funcs.splitByArtistSeparator

data class Album(
    val id: Long,
    val name: String,
    val artist: String?,
    val songCount: Int,
    val albumArtUri: Uri?
)

fun resolveAlbumArtists(
    albums: List<Album>,
    songs: List<Song>,
    separator: Regex?
): List<Album> {
    val songsByAlbum = songs.groupBy { it.albumId }

    return albums.map { album ->
        val albumSongs = songsByAlbum[album.id] ?: return@map album

        val perSong = albumSongs.map { song ->
            song.artist.splitByArtistSeparator(separator)
                .map { it.trim() }
                .filter { it.isNotEmpty() && it != "Unknown" }
        }

        if (perSong.all { it.isEmpty() }) return@map album

        val common = perSong.first().filter { name ->
            perSong.all { names -> names.any { it.equals(name, ignoreCase = true) } }
        }.distinct()

        album.copy(artist = if (common.isEmpty()) null else common.joinToString(", "))
    }
}
