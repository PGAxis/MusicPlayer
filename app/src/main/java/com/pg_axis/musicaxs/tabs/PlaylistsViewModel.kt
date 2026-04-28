package com.pg_axis.musicaxs.tabs

import android.app.Application
import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pg_axis.musicaxs.models.Playlist
import com.pg_axis.musicaxs.models.Song
import com.pg_axis.musicaxs.settings.PlayCountTracker
import com.pg_axis.musicaxs.settings.PlaylistRepository
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
    private val tracker = PlayCountTracker.getInstance(application)

    val playlists: StateFlow<List<Playlist>> = repo.playlists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Smart playlists — resolved as Song lists
    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val recentlyAdded: StateFlow<List<Song>> =
        recentlyAddedFlow(getApplication()) // emits Unit
            .debounce(300)
            .mapLatest {
                withContext(Dispatchers.IO) {
                    queryRecentlyAdded(getApplication())
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
                    .map { it.key } // List<String> (URIs)
            }
            .mapLatest { uris ->
                withContext(Dispatchers.IO) {
                    resolveUris(getApplication(), uris)
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
                    resolveUris(getApplication(), uris)
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

    fun getSongsForExport(context: Context, playlist: Playlist): List<Song> {
        return playlist.songIds.mapNotNull { id ->
            val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
            querySong(context, uri) // already exists in the VM
        }
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    private fun queryRecentlyAdded(context: Context, limit: Int = 50): List<Song> {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED
        )
        val songs = mutableListOf<Song>()
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            null, null,
            "${MediaStore.Audio.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            var count = 0
            while (cursor.moveToNext() && count < limit) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                val albumId = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID))
                songs.add(Song(
                    id = id,
                    title = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE))  ?: "Unknown",
                    artist = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)) ?: "Unknown",
                    album = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM))  ?: "Unknown",
                    uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                    durationMs = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)),
                    albumArtUri = ContentUris.withAppendedId("content://media/external/audio/albumart".toUri(), albumId)
                ))
                count++
            }
        }
        return songs
    }

    private fun resolveUris(context: Context, uris: List<String>): List<Song> =
        uris.mapNotNull { uri -> querySong(context, Uri.parse(uri)) }

    private fun querySong(context: Context, uri: Uri): Song? {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION
        )
        return context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
            val albumId = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID))
            Song(
                id = id,
                title = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE))  ?: "Unknown",
                artist = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)) ?: "Unknown",
                album = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM))  ?: "Unknown",
                uri = uri,
                durationMs = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)),
                albumArtUri = ContentUris.withAppendedId("content://media/external/audio/albumart".toUri(), albumId)
            )
        }
    }

    private fun recentlyAddedFlow(context: Context): Flow<List<Song>> = callbackFlow {
        val resolver = context.contentResolver

        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                launch(Dispatchers.IO) {
                    trySend(queryRecentlyAdded(context))
                }
            }
        }

        resolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            true,
            observer
        )

        launch(Dispatchers.IO) {
            trySend(queryRecentlyAdded(context))
        }

        awaitClose {
            resolver.unregisterContentObserver(observer)
        }
    }

    private fun String.toUri() = Uri.parse(this)
}