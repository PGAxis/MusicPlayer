package dev.pgaxis.musicaxs.repositories

import dev.pgaxis.musicaxs.models.OrbitSong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    val isLoaded = MutableStateFlow(false)

    fun update(songs: List<OrbitSong>) {
        _songs.value = songs
        isLoaded.value = true
    }

    fun registerDeviceName(deviceId: String, name: String) {
        _deviceNames.value += (deviceId to name)
    }

    fun songsForOwner(ownerDeviceId: String): List<OrbitSong> =
        songs.value.filter { it.ownerDeviceId == ownerDeviceId }

    fun resolveOrbitSong(orbitId: String): OrbitSong? =
        songs.value.find { it.orbitId == orbitId }
}