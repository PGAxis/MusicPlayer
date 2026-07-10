package dev.pgaxis.musicaxs.side_pages

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import dev.pgaxis.musicaxs.R
import dev.pgaxis.musicaxs.ext_funcs.toQueueItem
import dev.pgaxis.musicaxs.models.QueueItem
import dev.pgaxis.musicaxs.repositories.PlaylistRepository
import dev.pgaxis.musicaxs.services.MusicService
import dev.pgaxis.musicaxs.services.QueueSource
import dev.pgaxis.musicaxs.settings.SettingsSave
import dev.pgaxis.musicaxs.settings.SettingsSave.QueueEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class QueueViewModel(application: Application) : AndroidViewModel(application) {
    val context = getApplication<Application>()
    val settings = SettingsSave.getInstance(context)
    val playlistRepo = PlaylistRepository.getInstance(context)
    val queue = mutableStateListOf<MediaItem>()
    val queueItems = mutableStateListOf<QueueItem>()

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _currentTitle = MutableStateFlow(context.getString(R.string.queue))
    val currentTitle: StateFlow<String> = _currentTitle.asStateFlow()

    init {
        viewModelScope.launch {
            MusicService.queueState.collectLatest { items ->
                queue.clear()
                queue.addAll(items)
                queueItems.clear()
                queueItems.addAll(items.map { it.toQueueItem() })
            }
        }
        viewModelScope.launch {
            MusicService.currentIndexState.collectLatest {
                _currentIndex.value = it
            }
        }
        viewModelScope.launch {
            MusicService.queueSourceState.collectLatest { source ->
                when(source) {
                    QueueSource.MANUAL -> _currentTitle.value = context.getString(R.string.queue)
                    QueueSource.PLAYLIST -> _currentTitle.value = getPlaylistName()
                }
            }
        }
    }

    fun onMove(from: Int, to: Int) {
        queue.add(to, queue.removeAt(from))
        queueItems.add(to, queueItems.removeAt(from))
        MusicService.moveQueueItem(from, to)

        settings.lastQueue = queue.mapNotNull {
            val item = it.toQueueItem()
            val uri = it.localConfiguration?.uri?.toString() ?: return@mapNotNull null
            QueueEntry(
                uri = uri,
                title = item.title,
                artist = item.artist,
                albumArtUri = item.albumArtUri,
                durationMs = item.durationMs,
                source = item.source,
                deviceId = item.deviceId
            )
        }
    }

    fun removeAt(index: Int) {
        MusicService.removeFromQueue(getApplication(), index)
    }

    private fun getPlaylistName(): String {
        return when(settings.lastPlaylistId) {
            -1L -> context.getString(R.string.queue)
            0L -> context.getString(R.string.tab_favourites)
            1L -> context.getString(R.string.pls_scr_rec_added)
            2L -> context.getString(R.string.pls_scr_rec_played)
            3L -> context.getString(R.string.pls_scr_most_played)
            4L -> context.getString(R.string.tab_podcasts)
            else -> {
                val playlist = playlistRepo.playlistById(settings.lastPlaylistId)
                if (playlist != null) {
                    "Playlist: ${playlist.name}"
                } else {
                    context.getString(R.string.queue)
                }
            }
        }
    }
}