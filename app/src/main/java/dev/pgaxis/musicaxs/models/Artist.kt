package dev.pgaxis.musicaxs.models

import dev.pgaxis.musicaxs.ext_funcs.splitByArtistSeparator

data class Artist(
    val name: String,
    val songCount: Int,
    val albumCount: Int
)

fun deriveArtists(songs: List<Song>, albums: List<Album>, separator: Regex?): List<Artist> {
    val albumsById = albums.associateBy { it.id }

    val artistSongs = mutableMapOf<String, MutableList<Song>>()
    for (song in songs) {
        val names = song.artist.splitByArtistSeparator(separator)
        for (name in names) {
            artistSongs.getOrPut(name) { mutableListOf() }.add(song)
        }
    }

    return artistSongs.map { (name, songList) ->
        val albumCount = songList.map { it.albumId }
            .distinct()
            .count { albumsById.containsKey(it) }
        Artist(
            name = name,
            songCount = songList.size,
            albumCount = albumCount
        )
    }.sortedBy { it.name }
}
