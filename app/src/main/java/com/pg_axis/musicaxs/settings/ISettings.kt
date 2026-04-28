package com.pg_axis.musicaxs.settings

interface ISettings {
    var lastTabIndex: Int
    var lastSongUri: String
    var lastPositionMs: Long
    var lastDurationMs: Long
    var lastQueueUris: List<String>
    var lastQueueTitles: List<String>
    var lastQueueArtists: List<String>
    var repeatMode: Int
}