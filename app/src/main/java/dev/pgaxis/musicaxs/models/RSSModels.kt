package dev.pgaxis.musicaxs.models

import androidx.annotation.Keep

@Keep
data class PodcastFeed(
    val url: String,
    val title: String,
    val description: String,
    val artUrl: String?,
    val episodeCount: Int
)

data class PodcastEpisode(
    val title: String,
    val description: String,
    val audioUrl: String,
    val publishDate: String,
    val durationMs: Long
)