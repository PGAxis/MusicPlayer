package com.pg_axis.musicaxs.settings

import com.pg_axis.musicaxs.services.QueueSource

interface ISettings {
    // media playback persistency
    var lastTabIndex: Int
    var lastSongUri: String
    var lastPositionMs: Long
    var lastDurationMs: Long
    var lastQueueUris: List<String>
    var lastQueueTitles: List<String>
    var lastQueueArtists: List<String>
    var repeatMode: Int
    var queueSource: QueueSource

    // settings
    var hideWhatsAppAudio: Boolean
}