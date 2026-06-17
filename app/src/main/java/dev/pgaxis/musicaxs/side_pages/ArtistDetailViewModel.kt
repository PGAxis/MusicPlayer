package dev.pgaxis.musicaxs.side_pages

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.pgaxis.musicaxs.ext_funcs.splitByArtistSeparator
import dev.pgaxis.musicaxs.models.Artist
import dev.pgaxis.musicaxs.repositories.ArtistRepository
import dev.pgaxis.musicaxs.repositories.SongRepository
import dev.pgaxis.musicaxs.settings.SettingsSave
import dev.pgaxis.musicaxs.tabs.ArtistDetailItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class ArtistDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = SettingsSave.getInstance(getApplication())

    private val _artistName = MutableStateFlow<String?>(null)

    val artist: StateFlow<Artist?> = combine(
        _artistName,
        ArtistRepository.getInstance().artists
    ) { name, artists -> artists.find { it.name == name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val detailItems: StateFlow<List<ArtistDetailItem>> = combine(
        _artistName,
        SongRepository.getInstance().songs
    ) { name, songs ->
        name ?: return@combine emptyList()
        val separator = settings.artistSeparatorRegex
        val grouped = songs
            .filter { song ->
                song.artist.contains(name, ignoreCase = true) &&
                    song.artist.splitByArtistSeparator(separator).any { it.equals(name, ignoreCase = true) }
            }
            .sortedWith(compareBy({ it.album }, { it.track }))
            .groupBy { it.album }


        buildList {
            grouped.forEach { (albumName, albumSongs) ->
                add(ArtistDetailItem.AlbumHeader(albumName))
                albumSongs.forEach { add(ArtistDetailItem.SongItem(it)) }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun init(artistName: String) {
        _artistName.value = artistName
    }
}