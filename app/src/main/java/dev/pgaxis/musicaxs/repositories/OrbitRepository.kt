package dev.pgaxis.musicaxs.repositories

import dev.pgaxis.musicaxs.models.OrbitSong
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class OrbitRepository {
    companion object {
        @Volatile
        private var instance: OrbitRepository? = null
        fun getInstance(): OrbitRepository =
            instance ?: synchronized(this) {
                instance ?: OrbitRepository().also { instance = it }
            }
    }

    private val _songs = MutableStateFlow<List<OrbitSong>>(emptyList())
    val songs: StateFlow<List<OrbitSong>> = _songs.asStateFlow()

    private val _deviceNames = MutableStateFlow<Map<String, String>>(emptyMap())
    val deviceNames: StateFlow<Map<String, String>> = _deviceNames.asStateFlow()

    private val _loadedOwners = MutableStateFlow<Set<String>>(emptySet())
    val loadedOwners: StateFlow<Set<String>> = _loadedOwners.asStateFlow()

    private val _sessionEnded = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val sessionEnded: SharedFlow<Unit> = _sessionEnded.asSharedFlow()

    val isLoaded = MutableStateFlow(false)

    fun update(songs: List<OrbitSong>) {
        _songs.value = songs
        isLoaded.value = true
    }

    fun registerDeviceName(deviceId: String, name: String) {
        _deviceNames.update { it + (deviceId to name) }
    }

    fun setMembers(members: Map<String, String>) {
        _deviceNames.value = members
        _songs.update { list -> list.filter { it.ownerDeviceId in members } }
        _loadedOwners.update { owners -> owners.filterTo(mutableSetOf()) { it in members } }
    }

    fun replaceOwner(ownerDeviceId: String, newSongs: List<OrbitSong>) {
        _songs.update { list -> list.filter { it.ownerDeviceId != ownerDeviceId } + newSongs }
        _loadedOwners.update { it + ownerDeviceId }
        isLoaded.value = true
    }

    fun clear() {
        _songs.value = emptyList()
        _deviceNames.value = emptyMap()
        _loadedOwners.value = emptySet()
        isLoaded.value = false
        _sessionEnded.tryEmit(Unit)
    }

    fun songsForOwner(ownerDeviceId: String): List<OrbitSong> =
        songs.value.filter { it.ownerDeviceId == ownerDeviceId }

    fun resolveOrbitSong(orbitId: String): OrbitSong? =
        songs.value.find { it.orbitId == orbitId }
}