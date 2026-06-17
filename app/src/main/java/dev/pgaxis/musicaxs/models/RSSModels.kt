package dev.pgaxis.musicaxs.models

data class PodcastFeed(
    val url: String,
    val title: String,
    val description: String,
    val artUrl: String?
)

data class PodcastEpisode(
    val title: String,
    val description: String,
    val audioUrl: String,
    val publishDate: String,
    val durationMs: Long
)