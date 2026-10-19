package dev.pgaxis.musicaxs.side_pages

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.pgaxis.musicaxs.models.Song
import dev.pgaxis.musicaxs.repositories.AlbumRepository
import dev.pgaxis.musicaxs.repositories.ArtistRepository
import dev.pgaxis.musicaxs.repositories.SongRepository
import dev.pgaxis.musicaxs.settings.SettingsSave
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

class SearchViewModel(application: Application) : AndroidViewModel(application) {
    val settings = SettingsSave.getInstance(getApplication())
    private val songRepo = SongRepository.getInstance()
    private val albumRepo = AlbumRepository.getInstance()
    private val artistRepo = ArtistRepository.getInstance()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val includeFiltered = MutableStateFlow(false)
    fun setIncludeFiltered(value: Boolean) { includeFiltered.value = value }

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val searched = combine(_query.debounce(300.milliseconds), includeFiltered) { q, inc -> q to inc }
        .mapLatest { (q, inc) ->
            if (q.isBlank()) SearchResults()
            else withContext(Dispatchers.IO) { search(q, inc) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SearchResults())

    fun onQueryChange(q: String) { _query.value = q }

    private fun search(query: String, inc: Boolean): SearchResults {
        val q = query.trim().lowercase()
        val pool =  if (inc) (songRepo.songs.value + songRepo.filteredSongs.value)
                        .sortedBy { it.title.lowercase() }
                    else songRepo.songs.value

        val allAlbums = albumRepo.albums.value
        val allArtists = artistRepo.artists.value

        val matchingSongs = pool.filter { it.title.lowercase().contains(q) }

        val matchingArtists = allArtists
            .filter { it.name.lowercase().contains(q) }
            .map { ArtistResult(it.name, it.songCount) }

        val matchingAlbums = allAlbums.filter { it.name.lowercase().contains(q) }
            .map { AlbumResult(it.name, it.albumArtUri, it.songCount, it.id) }

        return SearchResults(
            songs = matchingSongs,
            artists = matchingArtists,
            albums = matchingAlbums
        )
    }
}

data class SearchResults(
    val songs: List<Song> = emptyList(),
    val artists: List<ArtistResult> = emptyList(),
    val albums: List<AlbumResult> = emptyList()
)

data class ArtistResult(val name: String, val songCount: Int)
data class AlbumResult(val name: String, val artUri: Uri?, val size: Int, val id: Long)