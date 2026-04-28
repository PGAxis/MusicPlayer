package com.pg_axis.musicaxs.settings

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pg_axis.musicaxs.models.Playlist
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class PlaylistRepository private constructor(context: Context) {

    companion object {
        @Volatile
        private var instance: PlaylistRepository? = null

        fun getInstance(context: Context): PlaylistRepository =
            instance ?: synchronized(this) {
                instance ?: PlaylistRepository(context.applicationContext).also { instance = it }
            }
    }

    private val gson = Gson()
    private val playlistsDir = context.filesDir.resolve("playlists").also { it.mkdirs() }

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    init {
        loadAll()
    }

    // ── CRUD ──────────────────────────────────────────────────────────────────

    fun create(name: String): Playlist {
        val id = System.currentTimeMillis()
        val playlist = Playlist(id = id, name = name, songIds = emptyList())
        save(playlist)
        return playlist
    }

    fun create(name: String, songIds: List<Long>): Playlist {
        val id = System.currentTimeMillis()
        val playlist = Playlist(id = id, name = name, songIds = songIds)
        save(playlist)
        return playlist
    }

    fun playlistById(id: Long): Playlist? {
        return _playlists.value.find { it.id == id }
    }

    fun addSong(playlistId: Long, songId: Long) {
        val playlist = _playlists.value.find { it.id == playlistId } ?: return
        if (songId in playlist.songIds) return
        save(playlist.copy(songIds = playlist.songIds + songId))
    }

    fun removeSong(playlistId: Long, songId: Long) {
        val playlist = _playlists.value.find { it.id == playlistId } ?: return
        save(playlist.copy(songIds = playlist.songIds - songId))
    }

    fun rename(playlistId: Long, name: String) {
        val playlist = _playlists.value.find { it.id == playlistId } ?: return
        save(playlist.copy(name = name))
    }

    fun delete(playlistId: Long) {
        fileFor(playlistId).delete()
        _playlists.value = _playlists.value.filter { it.id != playlistId }
    }

    fun reorderSongs(playlistId: Long, songIds: List<Long>) {
        val playlist = _playlists.value.find { it.id == playlistId } ?: return
        save(playlist.copy(songIds = songIds))
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private fun save(playlist: Playlist) {
        fileFor(playlist.id).writeText(gson.toJson(PlaylistData.from(playlist)))
        _playlists.value = (_playlists.value.filter { it.id != playlist.id } + playlist)
            .sortedBy { it.name }
    }

    private fun loadAll() {
        val loaded = playlistsDir.listFiles()
            ?.filter { it.extension == "json" }
            ?.mapNotNull { file ->
                try {
                    val type = object : TypeToken<PlaylistData>() {}.type
                    gson.fromJson<PlaylistData>(file.readText(), type)?.toPlaylist()
                } catch (_: Exception) { null }
            }
            ?.sortedBy { it.name }
            ?: emptyList()
        _playlists.value = loaded
    }

    private fun fileFor(id: Long): File = playlistsDir.resolve("$id.json")

    // ── Serialization model ───────────────────────────────────────────────────

    private data class PlaylistData(
        val id: Long = 0,
        val name: String = "",
        val songIds: List<Long> = emptyList()
    ) {
        fun toPlaylist() = Playlist(id = id, name = name, songIds = songIds)

        companion object {
            fun from(p: Playlist) = PlaylistData(id = p.id, name = p.name, songIds = p.songIds)
        }
    }
}