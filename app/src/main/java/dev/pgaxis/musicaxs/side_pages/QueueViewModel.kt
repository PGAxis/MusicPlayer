package dev.pgaxis.musicaxs.side_pages

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import dev.pgaxis.musicaxs.ext_funcs.toQueueItem
import dev.pgaxis.musicaxs.models.QueueItem
import dev.pgaxis.musicaxs.services.MusicService
import dev.pgaxis.musicaxs.settings.SettingsSave
import dev.pgaxis.musicaxs.settings.SettingsSave.QueueEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class QueueViewModel(application: Application) : AndroidViewModel(application) {
    val settings = SettingsSave.getInstance(application)
    val queue = mutableStateListOf<MediaItem>()
    val queueItems = mutableStateListOf<QueueItem>()

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

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
}