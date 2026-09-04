package dev.pgaxis.musicaxs.models

import androidx.annotation.Keep
import dev.pgaxis.musicaxs.R

enum class TabType {
    FAVOURITES, PLAYLISTS, SONGS, ALBUMS, ARTISTS, PODCASTS//, ORBIT
}

fun TabType.isMandatory() = this == TabType.SONGS

fun TabType.labelRes() = when (this) {
    TabType.FAVOURITES -> R.string.tab_favourites
    TabType.PLAYLISTS -> R.string.tab_playlists
    TabType.SONGS -> R.string.tab_songs
    TabType.ALBUMS -> R.string.tab_albums
    TabType.ARTISTS -> R.string.tab_artists
    TabType.PODCASTS -> R.string.tab_podcasts
    //TabType.ORBIT -> R.string.tab_orbit
}

val DEFAULT_TABS = listOf(
    TitleVis(TabType.FAVOURITES.name, true),
    TitleVis(TabType.PLAYLISTS.name, true),
    TitleVis(TabType.SONGS.name, true),
    TitleVis(TabType.ALBUMS.name, true),
    TitleVis(TabType.ARTISTS.name, true),
    TitleVis(TabType.PODCASTS.name, true),
    //TitleVis(TabType.ORBIT.name, true)
)

@Keep
data class TitleVis(
    val tab: String = "",
    val visible: Boolean = true
)