package dev.pgaxis.musicaxs.side_pages

import android.app.Application
import android.content.ContentUris
import android.provider.MediaStore
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.pgaxis.musicaxs.R
import dev.pgaxis.musicaxs.models.Order
import dev.pgaxis.musicaxs.models.Playlist
import dev.pgaxis.musicaxs.models.Song
import dev.pgaxis.musicaxs.settings.FavouritesSave
import dev.pgaxis.musicaxs.settings.PlayCountTracker
import dev.pgaxis.musicaxs.repositories.PlaylistRepository
import dev.pgaxis.musicaxs.repositories.SongRepository
import dev.pgaxis.musicaxs.settings.SettingsSave
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface DetailUiState {
    data object Loading : DetailUiState
    data class Ready(val name: String, val order: Order, val songs: List<Song>) : DetailUiState
}

class PlaylistsDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val context = getApplication<Application>()

    private val _uiState = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    val settings = SettingsSave.getInstance(context)
    private val repo = PlaylistRepository.getInstance(context)
    private val songRepo = SongRepository.getInstance()
    private val tracker = PlayCountTracker.getInstance(context)
    val favSave = FavouritesSave.getInstance(context)

    private var currentPlaylistId: Long = -1L

    fun initPlaylist(id: Long) {
        currentPlaylistId = id
        when (id) {
            0L -> {
                viewModelScope.launch {
                    combine(
                        favSave.orderedIdsFlow,
                        favSave.orderFlow
                    ) { ids, order ->
                        ids to order
                    }
                        .distinctUntilChanged()
                        .collectLatest { (ids, order) ->
                            val songs = withContext(Dispatchers.IO) {
                                ids.mapNotNull { id ->
                                    val uri = ContentUris.withAppendedId(
                                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                        id
                                    )
                                    songRepo.resolveSong(uri)
                                }
                            }

                            _uiState.value = DetailUiState.Ready(
                                context.getString(R.string.pls_scr_favourites),
                                order,
                                songs.order(order)
                            )
                        }
                }
            }
            1L, 2L, 3L, 4L -> {
                viewModelScope.launch(Dispatchers.IO) {
                    val songs = when (id) {
                        1L -> queryRecentlyAdded(getLimit())
                        2L -> getSongsForPlaylist(tracker.recentlyPlayed(getLimit()))
                        else -> getSongsForPlaylist(tracker.topPlayed(getLimit()))
                    }
                    val name = when (id) {
                        1L -> context.getString(R.string.pls_scr_rec_added)
                        2L -> context.getString(R.string.pls_scr_rec_played)
                        else -> context.getString(R.string.pls_scr_most_played)
                    }
                    _uiState.value = DetailUiState.Ready(name, Order.CUSTOM, songs)
                }
            }
            else -> {
                viewModelScope.launch {
                    repo.playlists.collectLatest { playlists ->
                        val playlist = playlists.find { it.id == id } ?: return@collectLatest
                        val songs = withContext(Dispatchers.IO) {
                            getSongsForPlaylist(playlist)
                        }.order(playlist.order)
                        _uiState.value = DetailUiState.Ready(playlist.name, playlist.order, songs)
                    }
                }
            }
        }
    }

    private fun queryRecentlyAdded(limit: Int = 50): List<Song> =
        songRepo.songs.value
            .sortedByDescending { it.dateAdded }
            .take(limit)

    fun getSongsForPlaylist(list: List<String>): List<Song> {
        return list.mapNotNull { uriString ->
            val uri = uriString.toUri()
            songRepo.resolveSong(uri)
        }
    }

    fun getSongsForPlaylist(playlist: Playlist): List<Song> {
        return playlist.songIds.mapNotNull { id ->
            val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
            songRepo.resolveSong(uri)
        }
    }

    fun removeSong(playlistId: Long, index: Int) {
        viewModelScope.launch(Dispatchers.IO) { repo.removeSongAt(playlistId, index) }
    }

    fun moveSong(playlistId: Long, reorderedSongs: List<Song>) {
        viewModelScope.launch(Dispatchers.IO) {
            if (playlistId == 0L) favSave.reorder(reorderedSongs.map { it.id })
            else repo.reorderSongs(playlistId, reorderedSongs.map { it.id })
        }
    }

    fun setOrder(playlistId: Long, order: Order) {
        viewModelScope.launch(Dispatchers.IO) {
            if (playlistId == 0L) favSave.setOrderState(order)
            else repo.changeDefOrder(playlistId, order)
        }
    }

    private fun getLimit(): Int {
        return if (settings.smartLimit == 0) Int.MAX_VALUE else settings.smartLimit
    }

    private fun List<Song>.order(order: Order): List<Song> {
        return when (order) {
            Order.CUSTOM -> this
            Order.ALPHABETICAL -> this.sortedBy { it.title.lowercase() }
            Order.ALPHABETICAL_R -> this.sortedByDescending { it.title.lowercase() }
            Order.DATE_ADD -> this.sortedBy { it.dateAdded }
            Order.DATE_ADD_R -> this.sortedByDescending { it.dateAdded }
        }
    }
}