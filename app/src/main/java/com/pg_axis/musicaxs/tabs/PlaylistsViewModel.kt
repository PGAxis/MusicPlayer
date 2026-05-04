package com.pg_axis.musicaxs.tabs

import android.app.Application
import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.provider.MediaStore
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pg_axis.musicaxs.models.Playlist
import com.pg_axis.musicaxs.models.Song
import com.pg_axis.musicaxs.settings.PlayCountTracker
import com.pg_axis.musicaxs.repositories.PlaylistRepository
import com.pg_axis.musicaxs.repositories.SongRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlaylistsViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = PlaylistRepository.getInstance(application)
    private val songRepo = SongRepository.getInstance()
    private val tracker = PlayCountTracker.getInstance(application)

    val playlists: StateFlow<List<Playlist>> = repo.playlists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Smart playlists
    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val recentlyAdded: StateFlow<List<Song>> =
        recentlyAddedFlow(getApplication())
            .debounce(300)
            .mapLatest {
                withContext(Dispatchers.IO) {
                    queryRecentlyAdded()
                }
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                emptyList()
            )

    @OptIn(ExperimentalCoroutinesApi::class)
    val recentlyPlayed: StateFlow<List<Song>> =
        tracker.entriesFlow
            .map { entries ->
                entries.entries
                    .sortedByDescending { it.value.lastPlayedMs }
                    .take(50)
                    .map { it.key }
            }
            .mapLatest { uris ->
                withContext(Dispatchers.IO) {
                    resolveUris(uris)
                }
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                emptyList()
            )

    @OptIn(ExperimentalCoroutinesApi::class)
    val mostPlayed: StateFlow<List<Song>> =
        tracker.entriesFlow
            .map { entries ->
                entries.entries
                    .sortedByDescending { it.value.count }
                    .take(50)
                    .map { it.key }
            }
            .mapLatest { uris ->
                withContext(Dispatchers.IO) {
                    resolveUris(uris)
                }
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                emptyList()
            )

    fun createFromImport(name: String, songIds: List<Long>) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.create(name, songIds)
        }
    }

    fun getSongsForExport(playlist: Playlist): List<Song> {
        return playlist.songIds.mapNotNull { id ->
            val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
            querySong(uri)
        }
    }

    fun rename(playlistId: Long, name: String) {
        viewModelScope.launch(Dispatchers.IO) { repo.rename(playlistId, name) }
    }

    fun delete(playlistId: Long) {
        viewModelScope.launch(Dispatchers.IO) { repo.delete(playlistId) }
    }

    fun merge(sourceId: Long, targetId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val source = repo.playlistById(sourceId) ?: return@launch
            val target = repo.playlistById(targetId) ?: return@launch
            val merged = (target.songIds + source.songIds).distinct()
            repo.reorderSongs(targetId, merged)
            repo.delete(sourceId)
        }
    }

    // -- Queries

    private fun queryRecentlyAdded(limit: Int = 50): List<Song> =
        songRepo.songs.value
            .sortedByDescending { it.dateAdded }
            .take(limit)

    private fun resolveUris(uris: List<String>): List<Song> =
        uris.mapNotNull { uri -> querySong(uri.toUri()) }

    private fun querySong(uri: Uri): Song? =
        songRepo.songs.value.find { it.uri == uri }

    private fun recentlyAddedFlow(context: Context): Flow<List<Song>> = callbackFlow {
        val resolver = context.contentResolver

        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                launch(Dispatchers.IO) {
                    trySend(queryRecentlyAdded())
                }
            }
        }

        resolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            true,
            observer
        )

        launch(Dispatchers.IO) {
            trySend(queryRecentlyAdded())
        }

        awaitClose {
            resolver.unregisterContentObserver(observer)
        }
    }
}