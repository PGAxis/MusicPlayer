package dev.pgaxis.musicaxs.services

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import dev.pgaxis.musicaxs.models.Song
import dev.pgaxis.musicaxs.repositories.PlaylistRepository
import dev.pgaxis.musicaxs.repositories.SongRepository
import dev.pgaxis.musicaxs.settings.SettingsSave
import dev.pgaxis.musicaxs.settings.SettingsSave.QueueEntry
import dev.pgaxis.musicaxs.settings.ShuffleSave

class PlaylistToQueue(
    private val context: Context
) {
    private val settings = SettingsSave.getInstance(context)
    private val shuffle = ShuffleSave.getInstance(context)

    fun addSongIfCurrent(playlistId: Long, song: Song): Boolean {
        Log.d("PlaylistToQueue", "try: $playlistId, current: ${settings.lastPlaylistId} - ${PlaylistRepository.getInstance(context).playlistById(settings.lastPlaylistId)?.name}, matched: ${playlistId == settings.lastPlaylistId}")
        if (settings.lastPlaylistId != playlistId) return false

        if (MusicService.playerInstance == null) {
            return true
        }

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
        Log.d("PlaylistToQueue", "try: $playlistId, current: ${settings.lastPlaylistId} - ${PlaylistRepository.getInstance(context).playlistById(settings.lastPlaylistId)?.name}, matched: ${playlistId == settings.lastPlaylistId}")
        if (settings.lastPlaylistId != playlistId) return false

        if (MusicService.isShuffled) {
            MusicService.removeFromQueue(context, song.uri.toString())
        } else {
            MusicService.removeFromQueue(context, index)
        }

        return true
    }

    fun reorderIfCurrent(playlistId: Long, songs: List<Song>): Boolean {
        Log.d("PlaylistToQueue", "try: $playlistId, current: ${settings.lastPlaylistId}, matched: ${playlistId == settings.lastPlaylistId}")
        if (settings.lastPlaylistId != playlistId) return false

        if (MusicService.isShuffled) {
            shuffle.setOriginalQueue(songs.map { it.uri.toString() })
        } else {
            val player = MusicService.playerInstance ?: return false
            val targetUris = songs.map { it.uri.toString() }

            targetUris.forEachIndexed { targetIndex, uri ->
                val currentIndex = (0 until player.mediaItemCount)
                    .firstOrNull { player.getMediaItemAt(it).localConfiguration?.uri?.toString() == uri }
                    ?: return@forEachIndexed
                if (currentIndex != targetIndex) {
                    MusicService.reorderQueue(currentIndex, targetIndex)
                }
            }
        }

        return true
    }

    fun syncQueueWithPlaylist() {
        if (settings.queueSource != QueueSource.PLAYLIST) return
        val playlistId = settings.lastPlaylistId

        val playlist = PlaylistRepository.getInstance(context).playlistById(playlistId) ?: return

        val songsById = SongRepository.getInstance().songs.value.associateBy { it.id }
        val playlistSongs = playlist.songIds.mapNotNull { songsById[it] }
        if (playlistSongs.isEmpty()) return

        val currentQueue = settings.lastQueue
        val availableByUri = currentQueue
            .groupingBy { it.uri }
            .eachCount()
            .toMutableMap()

        data class Missing(val playlistIndex: Int, val song: Song)
        val missing = mutableListOf<Missing>()

        for ((index, song) in playlistSongs.withIndex()) {
            val uri = song.uri.toString()
            val remaining = availableByUri.getOrDefault(uri, 0)
            if (remaining > 0) {
                availableByUri[uri] = remaining - 1
            } else {
                missing.add(Missing(index, song))
            }
        }

        if (missing.isEmpty()) return

        val workingQueue = currentQueue.toMutableList()

        if (shuffle.isShuffled) {
            for (m in missing) {
                val entry =
                    QueueEntry(m.song.uri.toString(), m.song.title, m.song.artist)
                val insertAt = if (workingQueue.isEmpty()) 0 else (0..workingQueue.size).random()
                workingQueue.add(insertAt, entry)
                shuffle.addToOriginal(m.song.uri.toString())
            }
        } else {
            for ((insertedSoFar, m) in missing.withIndex()) {
                val entry = QueueEntry(m.song.uri.toString(), m.song.title, m.song.artist)
                val insertAt = (m.playlistIndex + insertedSoFar).coerceIn(0, workingQueue.size)
                workingQueue.add(insertAt, entry)
            }
        }

        settings.lastQueue = workingQueue
        settings.flush()
    }
}