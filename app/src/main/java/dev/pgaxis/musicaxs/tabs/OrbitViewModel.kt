package dev.pgaxis.musicaxs.tabs

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.pgaxis.musicaxs.models.OrbitSong
import dev.pgaxis.musicaxs.repositories.OrbitRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class OrbitOwner(val deviceId: String, val displayName: String)

sealed interface OrbitSongsUiState {
    data object Loading : OrbitSongsUiState
    data object PermissionRequired : OrbitSongsUiState
    data object NoPeers : OrbitSongsUiState
    data class Ready(
        val owners: List<OrbitOwner>,
        val selectedOwner: OrbitOwner,
        val songs: List<OrbitSong>
    ) : OrbitSongsUiState
}

class OrbitViewModel(application: Application) : AndroidViewModel(application) {
    private val context = getApplication<Application>()
    private val orbitRepo = OrbitRepository.getInstance()

    private val discoveryPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        Manifest.permission.NEARBY_WIFI_DEVICES
    else
        Manifest.permission.ACCESS_FINE_LOCATION

    private val _hasPermission = MutableStateFlow(hasDiscoveryPermission())
    private val _selectedOwnerId = MutableStateFlow<String?>(null)

    val uiState: StateFlow<OrbitSongsUiState> = combine(
        _hasPermission,
        orbitRepo.songs,
        orbitRepo.deviceNames,
        _selectedOwnerId
    ) { hasPermission, songs, deviceNames, selectedId ->
        if (!hasPermission) return@combine OrbitSongsUiState.PermissionRequired

        val owners = songs
            .map { it.ownerDeviceId }
            .distinct()
            .map { id -> OrbitOwner(id, deviceNames[id] ?: id.take(6)) }

        if (owners.isEmpty()) {
            OrbitSongsUiState.NoPeers
        } else {
            val selected = owners.find { it.deviceId == selectedId } ?: owners.first()
            OrbitSongsUiState.Ready(
                owners = owners,
                selectedOwner = selected,
                songs = songs.filter { it.ownerDeviceId == selected.deviceId }
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = OrbitSongsUiState.Loading
    )

    fun onDiscoveryPermissionGranted() {
        _hasPermission.value = true
    }

    fun selectOwner(deviceId: String) {
        _selectedOwnerId.value = deviceId
    }

    private fun hasDiscoveryPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, discoveryPermission) ==
                PackageManager.PERMISSION_GRANTED
}