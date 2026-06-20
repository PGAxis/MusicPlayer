package dev.pgaxis.musicaxs.repositories

import dev.pgaxis.musicaxs.models.PodcastFeed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PodcastRepository private constructor() {
    companion object {
        @Volatile
        private var instance: PodcastRepository? = null

        fun getInstance(): PodcastRepository =
            instance ?: synchronized(this) {
                instance ?: PodcastRepository().also { instance = it }
            }
    }

    private val _feeds = MutableStateFlow<List<PodcastFeed>>(emptyList())
    val feeds: StateFlow<List<PodcastFeed>> = _feeds.asStateFlow()

    fun update(feeds: List<PodcastFeed>) { _feeds.value = feeds }

    fun add(feed: PodcastFeed) {
        _feeds.value += feed
    }

    fun remove(url: String) { _feeds.value = _feeds.value.filter { it.url != url } }
}