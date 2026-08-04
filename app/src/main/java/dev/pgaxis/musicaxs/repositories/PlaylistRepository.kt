package dev.pgaxis.musicaxs.repositories

import android.content.Context
import android.util.Log
import androidx.annotation.Keep
import com.google.gson.JsonParser
import dev.pgaxis.axs.AxsBoundObject
import dev.pgaxis.axs.AxsFile
import dev.pgaxis.musicaxs.models.Playlist
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

    private val playlistsDir = context.filesDir.resolve("playlists").also { it.mkdirs() }

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val axsFiles = mutableMapOf<Long, AxsFile>()
    private val boundPlaylists = mutableMapOf<Long, AxsBoundObject<PlaylistData>>()

    init {
        migrateLegacyJsonPlaylists()
        loadAll()
    }

    fun create(name: String): Playlist {
        val id = System.currentTimeMillis()
        val playlist = Playlist(id = id, name = name, songIds = emptyList())
        bindPlaylist(id, PlaylistData(id = id, name = playlist.name, songIds = playlist.songIds))
        _playlists.value = (_playlists.value + playlist).sortedBy { it.name }
        return playlist
    }

    fun create(name: String, songIds: List<Long>): Playlist {
        val id = System.currentTimeMillis()
        val playlist = Playlist(id = id, name = name, songIds = songIds)
        bindPlaylist(id, PlaylistData(id = id, name = playlist.name, songIds = playlist.songIds))
        _playlists.value = (_playlists.value + playlist).sortedBy { it.name }
        return playlist
    }

    fun playlistById(id: Long): Playlist? {
        return _playlists.value.find { it.id == id }
    }

    fun addSong(playlistId: Long, songId: Long) {
        val playlist = _playlists.value.find { it.id == playlistId } ?: return
        val newIds = playlist.songIds + songId
        playlist.setSongCount(newIds.size)
        save(playlist.copy(songIds = newIds))
    }

    fun removeSong(playlistId: Long, songId: Long) {
        val playlist = _playlists.value.find { it.id == playlistId } ?: return
        val newIds = playlist.songIds.filter { it != songId }
        playlist.setSongCount(newIds.size)
        save(playlist.copy(songIds = newIds))
    }

    fun removeSongAt(playlistId: Long, index: Int) {
        val playlist = _playlists.value.find { it.id == playlistId } ?: return
        if (index !in playlist.songIds.indices) return
        val newIds = playlist.songIds.toMutableList().also { it.removeAt(index) }
        playlist.setSongCount(newIds.size)
        save(playlist.copy(songIds = newIds))
    }

    fun rename(playlistId: Long, name: String) {
        val playlist = _playlists.value.find { it.id == playlistId } ?: return
        save(playlist.copy(name = name))
    }

    fun delete(playlistId: Long) {
        boundPlaylists.remove(playlistId)
        axsFiles.remove(playlistId)?.close()
        fileFor(playlistId).delete()
        _playlists.value = _playlists.value.filter { it.id != playlistId }
    }

    fun reorderSongs(playlistId: Long, songIds: List<Long>) {
        val playlist = _playlists.value.find { it.id == playlistId } ?: return
        playlist.setSongCount(songIds.size)
        save(playlist.copy(songIds = songIds))
    }

    private fun save(playlist: Playlist) {
        val bound = boundPlaylists[playlist.id]
            ?: bindPlaylist(playlist.id, PlaylistData(id = playlist.id, name = playlist.name, songIds = playlist.songIds))

        bound.setValue(PlaylistData::name, playlist.name)
        bound.setValue(PlaylistData::songIds, playlist.songIds)

        _playlists.value = (_playlists.value.filter { it.id != playlist.id } + playlist)
            .sortedBy { it.name }
    }

    private fun bindPlaylist(id: Long, initial: PlaylistData): AxsBoundObject<PlaylistData> {
        val axsFile = AxsFile(fileFor(id).path)
        axsFile.setLogging(on = false)
        axsFile.open()
        val bound = axsFile.bind(initial)
        axsFiles[id] = axsFile
        boundPlaylists[id] = bound
        return bound
    }

    // -- Legacy .json -> .axs migration --
    private fun migrateLegacyJsonPlaylists() {
        val legacyFiles = playlistsDir.listFiles()
            ?.filter { it.extension == "json" }
            ?: return

        Log.d("PlaylistRepository", "Legacy playlists: $legacyFiles")

        if (legacyFiles.isEmpty()) return

        for (file in legacyFiles) {
            try {
                val json = JsonParser.parseString(file.readText()).asJsonObject

                val id = json.get("id")?.takeIf { it.isJsonPrimitive }?.asLong ?: 0L
                val name = json.get("name")?.takeIf { it.isJsonPrimitive }?.asString ?: ""
                val songIds = json.get("songIds")?.takeIf { it.isJsonArray }
                    ?.asJsonArray
                    ?.mapNotNull { runCatching { it.asLong }.getOrNull() }
                    ?: emptyList()

                if (id == 0L) {
                    Log.w("PlaylistRepository", "Deleting corrupt legacy playlist file: ${file.name}")
                    file.delete()
                    continue
                }

                val axsTarget = fileFor(id)
                if (!axsTarget.exists()) {
                    val axsFile = AxsFile(axsTarget.path)
                    axsFile.setLogging(on = false)
                    axsFile.open()
                    axsFile.bind(PlaylistData(id = id, name = name, songIds = songIds))
                    axsFile.close()
                    Log.i("PlaylistRepository", "Migrated legacy playlist $id ($name) to .axs")
                }

                file.delete()
            } catch (e: Exception) {
                Log.w("PlaylistRepository", "Failed to migrate legacy playlist file: ${file.name}", e)
            }
        }
    }

    private fun loadAll() {
        val loaded = playlistsDir.listFiles()
            ?.filter { it.extension == "axs" }
            ?.mapNotNull { file ->
                val id = file.nameWithoutExtension.toLongOrNull() ?: return@mapNotNull null
                try {
                    val axsFile = AxsFile(file.path)
                    axsFile.setLogging(on = false)
                    axsFile.open()
                    val bound = axsFile.bind(PlaylistData())
                    val data = bound.get()

                    if (data.id == 0L) {
                        Log.w("PlaylistRepository", "Deleting corrupt playlist file: ${file.name}")
                        axsFile.close()
                        file.delete()
                        return@mapNotNull null
                    }

                    axsFiles[id] = axsFile
                    boundPlaylists[id] = bound
                    data.toPlaylist()
                } catch (e: Exception) {
                    Log.w("PlaylistRepository", "Failed to parse playlist file: ${file.name}", e)
                    null
                }
            }
            ?.sortedBy { it.name }
            ?: emptyList()
        _playlists.value = loaded
    }

    private fun fileFor(id: Long): File = playlistsDir.resolve("$id.axs")

    @Keep
    data class PlaylistData(
        var id: Long = 0,
        var name: String = "",
        var songIds: List<Long> = emptyList()
    ) {
        fun toPlaylist() = Playlist(id = id, name = name, songIds = songIds)
    }
}