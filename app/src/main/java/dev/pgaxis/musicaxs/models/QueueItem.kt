package dev.pgaxis.musicaxs.models

enum class QueueItemSource { LOCAL, PODCAST, GUEST }

data class QueueItem(
    val uri: String,
    val title: String,
    val artist: String,
    val albumArtUri: String? = null,
    val durationMs: Long = 0L,
    val source: QueueItemSource = QueueItemSource.LOCAL,
    val deviceId: String? = null
)