package dev.pgaxis.musicaxs.services

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import dev.pgaxis.musicaxs.models.Song
import dev.pgaxis.musicaxs.settings.SettingsSave
import dev.pgaxis.musicaxs.settings.ShuffleSave

class PlaylistToQueue(
    private val context: Context
) {
    private val settings = SettingsSave.getInstance(context)
    private val shuffle = ShuffleSave.getInstance(context)

    fun addSongIfCurrent(playlistId: Long, song: Song): Boolean {
        Log.d("PlaylistToQueue", "try: $playlistId, current: ${settings.lastPlaylistId}, matched: ${playlistId == settings.lastPlaylistId}")
        if (settings.lastPlaylistId != playlistId) return false

        Handler(Looper.getMainLooper()).post {
            MusicService.addToQueue(
                context = context,
                song = song,
                applyShuffleRandomness = MusicService.isShuffled,
                resetPlaylist = false
            )
        }

        return true
    }

    fun removeIfCurrent(playlistId: Long, song: Song, index: Int): Boolean {
        Log.d("PlaylistToQueue", "try: $playlistId, current: ${settings.lastPlaylistId}, matched: ${playlistId == settings.lastPlaylistId}")
        if (settings.lastPlaylistId != playlistId) return false

        if (MusicService.isShuffled) {
            MusicService.removeFromQueue(song.uri.toString())
        } else {
            MusicService.removeFromQueue(index)
        }

        return true
    }

    fun reorderIfCurrent(playlistId: Long, songs: List<Song>): Boolean {
        Log.d("PlaylistToQueue", "try: $playlistId, current: ${settings.lastPlaylistId}, matched: ${playlistId == settings.lastPlaylistId}")
        if (settings.lastPlaylistId != playlistId) return false

        if (MusicService.isShuffled) {
            shuffle.setOriginalQueue(songs.map { it.uri.toString() })
        } else {
            MusicService.replaceQueue(context, songs, playlistId)
        }

        return true
    }
}