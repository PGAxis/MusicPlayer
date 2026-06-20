package dev.pgaxis.musicaxs.tabs

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.pgaxis.musicaxs.models.PodcastFeed
import dev.pgaxis.musicaxs.repositories.PodcastRepository
import dev.pgaxis.musicaxs.services.PodcastFetcher
import dev.pgaxis.musicaxs.settings.SettingsSave
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

class PodcastViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = SettingsSave.getInstance(application)
    private val repo = PodcastRepository.getInstance()

    val feeds: StateFlow<List<PodcastFeed>> = repo.feeds

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        if (repo.feeds.value.isEmpty()) {
            repo.update(settings.podcastFeeds)
        }
    }

    fun addFeed(url: String) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return
        if (settings.podcastFeeds.any { it.url == trimmed }) {
            showError("Feed already added")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                val feed = PodcastFetcher.fetchFeed(trimmed)
                repo.add(feed)
                settings.podcastFeeds += feed
                Log.d("PodcastDebug", "Saved feeds: ${settings.podcastFeeds}")
            } catch (_: Exception) {
                showError("Couldn't load feed — check the URL and try again")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun removeFeed(url: String) {
        repo.remove(url)
        settings.podcastFeeds = settings.podcastFeeds.filter { it.url != url }
    }

    private fun showError(message: String) {
        viewModelScope.launch {
            _error.value = message
            delay(4.seconds)
            _error.value = null
        }
    }
}