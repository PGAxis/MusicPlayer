package dev.pgaxis.musicaxs.side_pages

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.pgaxis.musicaxs.ext_funcs.toMediaItem
import dev.pgaxis.musicaxs.models.PodcastEpisode
import dev.pgaxis.musicaxs.models.PodcastFeed
import dev.pgaxis.musicaxs.repositories.PodcastRepository
import dev.pgaxis.musicaxs.services.MusicService
import dev.pgaxis.musicaxs.services.PodcastDownloadService
import dev.pgaxis.musicaxs.services.PodcastDownloader
import dev.pgaxis.musicaxs.services.PodcastFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PodcastDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val context = getApplication<Application>()
    private val repo = PodcastRepository.getInstance()

    private val _feed = MutableStateFlow<PodcastFeed?>(null)
    val feed: StateFlow<PodcastFeed?> = _feed.asStateFlow()

    private val _episodes = MutableStateFlow<List<PodcastEpisode>>(emptyList())
    val episodes: StateFlow<List<PodcastEpisode>> = _episodes.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _downloadedUrls = MutableStateFlow<Set<String>>(emptySet())
    val downloadedUrls: StateFlow<Set<String>> = _downloadedUrls.asStateFlow()

    fun isDownloaded(audioUrl: String): Boolean =
        PodcastDownloader.isDownloaded(context, audioUrl)

    val downloading: StateFlow<Set<String>> = PodcastDownloader.downloading

    fun init(feedUrl: String) {
        _feed.value = repo.feeds.value.find { it.url == feedUrl } ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                _episodes.value = PodcastFetcher.fetchEpisodes(feedUrl)
                refreshDownloadedState()
            } catch (_: Exception) {
                _error.value = "Couldn't load episodes — check your connection"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun refreshDownloadedState() {
        _downloadedUrls.value = _episodes.value
            .map { it.audioUrl }
            .filter { PodcastDownloader.isDownloaded(context, it) }
            .toSet()
    }

    fun downloadEpisode(audioUrl: String, title: String) {
        PodcastDownloadService.startDownload(context, audioUrl, title)
        viewModelScope.launch {
            PodcastDownloader.downloading.first { !it.contains(audioUrl) }
            if (PodcastDownloader.isDownloaded(context, audioUrl)) {
                _downloadedUrls.value += audioUrl
            }
        }
    }

    fun deleteEpisode(audioUrl: String) {
        PodcastDownloader.delete(context, audioUrl)
        _downloadedUrls.value -= audioUrl

        // If this episode is currently playing via local file, switch back to online URL
        val localUri = PodcastDownloader.localUriFor(context, audioUrl)
        val player = MusicService.playerInstance ?: return
        val currentItem = player.currentMediaItem ?: return
        val currentUri = currentItem.localConfiguration?.uri?.toString() ?: return

        if (currentUri == localUri) {
            val positionMs = player.currentPosition
            val feed = _feed.value ?: return
            val episode = _episodes.value.find { it.audioUrl == audioUrl } ?: return
            val newItem = episode.toMediaItem(feed) // uses online URL
            viewModelScope.launch(Dispatchers.Main) {
                val index = player.currentMediaItemIndex
                player.replaceMediaItem(index, newItem)
                player.seekTo(index, positionMs)
            }
        }
    }

    fun resolvePlaybackUrl(audioUrl: String): String =
        if (_downloadedUrls.value.contains(audioUrl))
            PodcastDownloader.localUriFor(context, audioUrl)
        else audioUrl
}