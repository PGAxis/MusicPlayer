package com.pg_axis.musicaxs.side_pages

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import com.pg_axis.musicaxs.services.MusicService
import com.pg_axis.musicaxs.settings.SettingsSave
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class QueueViewModel(application: Application) : AndroidViewModel(application) {
    val settings = SettingsSave.getInstance(application)
    val queue = mutableStateListOf<MediaItem>()

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    init {
        viewModelScope.launch {
            MusicService.queueState.collectLatest { items ->
                queue.clear()
                queue.addAll(items)
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
        MusicService.moveQueueItem(from, to)

        settings.lastQueueUris = queue.mapNotNull { it.localConfiguration?.uri?.toString() }
        settings.save()
    }

    fun removeAt(index: Int) {
        MusicService.removeFromQueue(index)
    }
}