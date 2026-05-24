package dev.pgaxis.musicaxs.settings

import dev.pgaxis.musicaxs.services.QueueSource
import dev.pgaxis.musicaxs.services.Theme

interface ISettings {
    // media playback persistency
    var lastTabIndex: Int
    var lastSongUri: String
    var lastPositionMs: Long
    var lastDurationMs: Long
    var lastPlaylistId: Long
    var lastQueueUris: List<String>
    var lastQueueTitles: List<String>
    var lastQueueArtists: List<String>
    var lastQueueIndex: Int
    var repeatMode: Int
    var queueSource: QueueSource

    // settings
    var hideWhatsAppAudio: Boolean
    var allowYTCnv: Boolean
    var theme: Theme
}