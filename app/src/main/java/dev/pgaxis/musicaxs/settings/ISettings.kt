package dev.pgaxis.musicaxs.settings

import dev.pgaxis.musicaxs.models.TitleVis
import dev.pgaxis.musicaxs.services.QueueSource
import dev.pgaxis.musicaxs.services.Theme

interface ISettings {
    // media playback persistency
    var lastTabIndex: Int
    var lastSongUri: String
    var lastPositionMs: Long
    var lastDurationMs: Long
    var lastPlaylistId: Long
    var lastQueue: List<SettingsSave.QueueEntry>
    var lastQueueIndex: Int
    var repeatMode: Int
    var queueSource: QueueSource
    var podcastFeedUrls: List<String>

    // settings
    var hideWhatsAppAudio: Boolean
    var allowYTCnv: Boolean
    var theme: Theme
    var tabs: List<TitleVis>
    var artistSeparator: List<String>
}