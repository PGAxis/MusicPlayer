package com.pg_axis.musicaxs.side_pages

import android.app.Application
import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pg_axis.musicaxs.models.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow(SearchResults())
    val results: StateFlow<SearchResults> = _results.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val searched = _query
        .debounce(300)
        .mapLatest { q ->
            if (q.isBlank()) SearchResults()
            else withContext(Dispatchers.IO) { search(q) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SearchResults())

    fun onQueryChange(q: String) { _query.value = q }

    private fun search(query: String): SearchResults {
        val context = getApplication<Application>()
        val q = query.trim().lowercase()

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION
        )

        val allSongs = mutableListOf<Song>()
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null, null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                val albumId = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID))
                allSongs.add(Song(
                    id = id,
                    title = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)) ?: "Unknown",
                    artist = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)) ?: "Unknown",
                    album = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)) ?: "Unknown",
                    uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                    durationMs = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)),
                    albumArtUri = ContentUris.withAppendedId("content://media/external/audio/albumart".toUri(), albumId)
                ))
            }
        }

        val matchingSongs = allSongs.filter { it.title.lowercase().contains(q) }
        val matchingArtists = allSongs
            .filter { it.artist.lowercase().contains(q) }
            .groupBy { it.artist }
            .map { (artist, songs) -> ArtistResult(artist, songs) }
        val matchingAlbums = allSongs
            .filter { it.album.lowercase().contains(q) }
            .groupBy { it.album }
            .map { (album, songs) -> AlbumResult(album, songs.first().albumArtUri, songs) }

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

data class ArtistResult(val name: String, val songs: List<Song>)
data class AlbumResult(val name: String, val artUri: Uri?, val songs: List<Song>)