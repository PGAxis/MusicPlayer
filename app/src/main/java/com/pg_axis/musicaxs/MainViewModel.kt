package com.pg_axis.musicaxs

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.ContentUris
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.pg_axis.musicaxs.models.Song
import com.pg_axis.musicaxs.services.MusicService
import com.pg_axis.musicaxs.settings.SettingsSave
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.core.net.toUri
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import com.pg_axis.musicaxs.models.Album
import com.pg_axis.musicaxs.models.Artist
import com.pg_axis.musicaxs.models.Playlist
import com.pg_axis.musicaxs.repositories.AlbumRepository
import com.pg_axis.musicaxs.repositories.ArtistRepository
import com.pg_axis.musicaxs.repositories.PlaylistRepository
import com.pg_axis.musicaxs.repositories.SongRepository
import com.pg_axis.musicaxs.services.AlbumArtPreloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class CurrentSong(
    val title: String = "Song Title",
    val artist: String = "Artist",
    val songUri: String? = null
)

private val SUPPORTED_MIME_TYPES = setOf(
    "audio/mpeg",       // mp3
    "audio/wav",        // wav
    "audio/x-wav",      // wav (alternate)
    "audio/flac",       // flac
    "audio/x-flac",     // flac (alternate)
    "audio/ogg",        // ogg vorbis
    "audio/mp4",        // m4a / aac
    "audio/m4a",        // m4a (alternate)
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val settings = SettingsSave.getInstance(application)
    private val repo = PlaylistRepository.getInstance(application)

    private val songRepo = SongRepository.getInstance()
    private val albumRepo = AlbumRepository.getInstance()
    private val artistRepo = ArtistRepository.getInstance()

    val tabs = listOf("Favourites", "Playlists", "Songs", "Albums", "Artists")

    // Index of the last active tab, persisted via settings
    private val _currentPageIndex = MutableStateFlow(settings.lastTabIndex)
    val currentPageIndex: StateFlow<Int> = _currentPageIndex.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    var isPlaying by mutableStateOf(false)
        private set

    private val _currentSong = MutableStateFlow<CurrentSong?>(null)
    val currentSong: StateFlow<CurrentSong?> = _currentSong.asStateFlow()

    private fun hasPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_AUDIO
        else
            Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(
            getApplication(), permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    private val mediaObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            scanAll()
        }
    }

    // Called when the pager settles
    fun onPageChanged(index: Int) {
        _currentPageIndex.value = index
        settings.lastTabIndex = index
        settings.save()
    }

    fun onPlayPause() {
        controller?.let { player ->
            if (player.isPlaying) {
                player.pause()
            } else {
                player.play()
            }
        }
    }

    fun setSong(song: Song) {
        _currentSong.value = CurrentSong(title = song.title, artist = song.artist, songUri = song.uri.toString())
        settings.lastSongUri = song.uri.toString()
        settings.save()
    }

    fun resolveSongFromUri(uri: Uri): Song? =
        SongRepository.getInstance().songs.value.find { it.uri == uri }

    init {
        val context = getApplication<Application>()
        val token = SessionToken(context, ComponentName(context, MusicService::class.java))

        getApplication<Application>().contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            true,
            mediaObserver
        )

        if (hasPermission()) scanAll()

        controllerFuture = MediaController.Builder(context, token).buildAsync()
        controllerFuture?.addListener({
            controller = controllerFuture?.get()
            controller?.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }

                override fun onMediaItemTransition(
                    mediaItem: MediaItem?,
                    reason: Int
                ) {
                    mediaItem ?: return

                    val uri = mediaItem.localConfiguration?.uri ?: return
                    val song = resolveSongFromUri(uri) ?: return

                    setSong(song)
                }
            })
        }, ContextCompat.getMainExecutor(context))

        val lastUri = settings.lastSongUri
        if (lastUri.isNotEmpty()) {
            viewModelScope.launch(Dispatchers.IO) {
                val song = resolveSongFromUri(lastUri.toUri())
                if (song != null) {
                    _currentSong.value = CurrentSong(
                        title = song.title,
                        artist = song.artist,
                        songUri = song.uri.toString()
                    )
                }
            }
        }
    }

    fun onPrevious() {
        MusicService.previous()
    }
    fun onNext() {
        MusicService.next()
    }
    fun createAndGetPlaylist(name: String): Playlist {
        return repo.create(name)
    }

    fun scanAll() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val songs = querySongs()
                val albums = queryAlbums()
                val artists = queryArtists()
                songRepo.update(songs)
                albumRepo.update(albums)
                artistRepo.update(artists)
                prewarmAlbumArtCache(songs)
            } catch (_: SecurityException) {
                // permission not granted yet — SongsScreen will handle asking
            } catch (_: Exception) { }
        }
    }

    private fun prewarmAlbumArtCache(songs: List<Song>) {
        viewModelScope.launch {
            AlbumArtPreloader.preloadAll(context = getApplication(), songs = songs)
            AlbumArtPreloader.cleanup(context = getApplication(), songs = songs)
        }
    }

    private fun querySongs(): List<Song> {
        val context = getApplication<Application>()

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATE_ADDED,
        )

        val songs = mutableListOf<Song>()

        val whatsAppFilter = if (settings.hideWhatsAppAudio)
            " AND ${MediaStore.Audio.Media.ALBUM} != 'WhatsApp Audio'"
        else ""

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0$whatsAppFilter",
            null,
            "${MediaStore.Audio.Media.TITLE} ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

            while (cursor.moveToNext()) {
                val mimeType = cursor.getString(mimeCol) ?: continue
                if (mimeType !in SUPPORTED_MIME_TYPES) continue

                val id = cursor.getLong(idCol)
                val albumId = cursor.getLong(albumIdCol)

                songs.add(
                    Song(
                        id = id,
                        title = cursor.getString(titleCol) ?: "Unknown",
                        artist = cursor.getString(artistCol) ?: "Unknown",
                        album = cursor.getString(albumCol) ?: "Unknown",
                        albumId = albumId,
                        uri = ContentUris.withAppendedId(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                        durationMs = cursor.getLong(durationCol),
                        albumArtUri = ContentUris.withAppendedId(
                            "content://media/external/audio/albumart".toUri(), albumId),
                        track = cursor.getInt(trackCol) % 1000,
                        dateAdded = cursor.getLong(dateAddedCol)
                    )
                )
            }
        }

        return songs
    }

    private fun queryAlbums(): List<Album> {
        val context = getApplication<Application>()

        val projection = arrayOf(
            MediaStore.Audio.Albums._ID,
            MediaStore.Audio.Albums.ALBUM,
            MediaStore.Audio.Albums.ARTIST,
            MediaStore.Audio.Albums.NUMBER_OF_SONGS,
        )

        val albums = mutableListOf<Album>()

        val whatsAppFilter = if (settings.hideWhatsAppAudio)
            "${MediaStore.Audio.Albums.ALBUM} != 'WhatsApp Audio'"
        else null

        context.contentResolver.query(
            MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
            projection,
            whatsAppFilter,
            null,
            "${MediaStore.Audio.Albums.ALBUM} ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.ARTIST)
            val songCountCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.NUMBER_OF_SONGS)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                albums.add(
                    Album(
                        id = id,
                        name = cursor.getString(nameCol) ?: "Unknown",
                        artist = cursor.getString(artistCol) ?: "Unknown",
                        songCount = cursor.getInt(songCountCol),
                        albumArtUri = ContentUris.withAppendedId(
                            "content://media/external/audio/albumart".toUri(), id
                        )
                    )
                )
            }
        }

        return albums
    }

    private fun queryArtists(): List<Artist> {
        val context = getApplication<Application>()

        val projection = arrayOf(
            MediaStore.Audio.Artists._ID,
            MediaStore.Audio.Artists.ARTIST,
            MediaStore.Audio.Artists.NUMBER_OF_TRACKS,
            MediaStore.Audio.Artists.NUMBER_OF_ALBUMS,
        )

        val interprets = mutableListOf<Artist>()

        context.contentResolver.query(
            MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Audio.Artists.ARTIST} ASC"
        )?.use { cursor ->
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Artists.ARTIST)
            val songCountCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Artists.NUMBER_OF_TRACKS)
            val albumCountCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Artists.NUMBER_OF_ALBUMS)

            while (cursor.moveToNext()) {
                interprets.add(
                    Artist(
                        name = cursor.getString(nameCol) ?: "Unknown",
                        songCount = cursor.getInt(songCountCol),
                        albumCount = cursor.getInt(albumCountCol)
                    )
                )
            }
        }

        return interprets
    }

    override fun onCleared() {
        super.onCleared()
        getApplication<Application>().contentResolver.unregisterContentObserver(mediaObserver)
    }
}