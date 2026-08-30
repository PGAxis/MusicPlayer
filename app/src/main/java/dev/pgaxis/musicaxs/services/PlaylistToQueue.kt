package dev.pgaxis.musicaxs.services

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import dev.pgaxis.musicaxs.models.Song
import dev.pgaxis.musicaxs.repositories.PlaylistRepository
import dev.pgaxis.musicaxs.repositories.SongRepository
import dev.pgaxis.musicaxs.settings.SettingsSave

class PlaylistToQueue(
    private val context: Context
) {
    private val settings = SettingsSave.getInstance(context)

    private fun isCurrentPlaylist(playlistId: Long): Boolean {
        val current = settings.lastPlaylistId
        val matched = current == playlistId
        Log.d("PlaylistToQueue", "try: $playlistId, current: $current, matched: $matched")
        return matched
    }

    fun addSongIfCurrent(playlistId: Long, song: Song) {
        if (settings.lastPlaylistId != playlistId) return

        Handler(Looper.getMainLooper()).post {
            if (MusicService.playerInstance == null) return@post
            MusicService.addToQueue(
                context = context,
                song = song,
                applyShuffleRandomness = MusicService.isShuffled,
                resetPlaylist = false
            )
        }
    }

    fun removeIfCurrent(playlistId: Long, song: Song, index: Int): Boolean {
        if (!isCurrentPlaylist(playlistId)) return false
        if (MusicService.isShuffled) {
            MusicService.removeFromQueue(context, song.uri.toString())
        } else {
            MusicService.removeFromQueue(context, index)
        }
        return true
    }

    fun reorderIfCurrent(playlistId: Long, songs: List<Song>): Boolean {
        if (!isCurrentPlaylist(playlistId)) return false
        MusicService.reorderQueueTo(context, songs.map { it.uri.toString() })
        return true
    }

    fun syncQueueWithPlaylist() {
        if (settings.queueSource != QueueSource.PLAYLIST) return
        val playlistId = settings.lastPlaylistId
        val playlist = PlaylistRepository.getInstance(context).playlistById(playlistId) ?: return

        val currentHash = playlist.songIds.stableHash(playlistId)
        if (currentHash == settings.lastSyncedPlaylistHash) return

        val player = MusicService.playerInstance ?: return
        val songsById = SongRepository.getInstance().songs.value.associateBy { it.id }
        val playlistSongs = playlist.songIds.mapNotNull { songsById[it] }
        if (playlistSongs.isEmpty()) return

        val liveUris = (0 until player.mediaItemCount)
            .mapNotNull { player.getMediaItemAt(it).localConfiguration?.uri?.toString() }
        val availableByUri = liveUris.groupingBy { it }.eachCount().toMutableMap()

        val missing = mutableListOf<Pair<Int, Song>>()
        for ((index, song) in playlistSongs.withIndex()) {
            val uri = song.uri.toString()
            val remaining = availableByUri.getOrDefault(uri, 0)
            if (remaining > 0) {
                availableByUri[uri] = remaining - 1
            } else {
                missing.add(index to song)
            }
        }
        if (missing.isEmpty()) return

        Handler(Looper.getMainLooper()).post {
            MusicService.syncMissingFromPlaylist(context, missing)
        }

        settings.lastSyncedPlaylistHash = currentHash
        settings.flush()
    }
}