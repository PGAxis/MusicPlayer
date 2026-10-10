package dev.pgaxis.musicaxs.tabs

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.pgaxis.musicaxs.models.OrbitSong
import dev.pgaxis.musicaxs.orbit.DiscoveredHost
import dev.pgaxis.musicaxs.orbit.OrbitConnectionState
import dev.pgaxis.musicaxs.orbit.OrbitPeerIdentity
import dev.pgaxis.musicaxs.orbit.OrbitSession
import dev.pgaxis.musicaxs.repositories.OrbitRepository
import dev.pgaxis.musicaxs.services.OrbitService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

data class OrbitOwner(
    val deviceId: String,
    val displayName: String,
    val isSynced: Boolean = true
)

sealed interface OrbitSongsUiState {
    data object Loading : OrbitSongsUiState
    data object PermissionRequired : OrbitSongsUiState

    data object NotConnected : OrbitSongsUiState

    data class Session(
        val owners: List<OrbitOwner>,
        val selected: OrbitOwner?,
        val songs: List<OrbitSong>
    ) : OrbitSongsUiState
}

class OrbitViewModel(application: Application) : AndroidViewModel(application) {
    private val context = getApplication<Application>()
    private val orbitRepo = OrbitRepository.getInstance()
    private val orbitService = OrbitService.getInstance(context)

    val connectionState: StateFlow<OrbitConnectionState> = orbitService.connectionState
    val discoveredHosts: StateFlow<List<DiscoveredHost>> = orbitService.discoveredHosts
    val orbitPeers: StateFlow<List<OrbitPeerIdentity>> = orbitService.orbitPeers
    val currentSession: StateFlow<OrbitSession?> = orbitService.currentSession

    private val _discoveryTimedOut = MutableStateFlow(false)
    val discoveryTimedOut: StateFlow<Boolean> = _discoveryTimedOut.asStateFlow()

    private var discoveryTimeoutJob: Job? = null

    private val discoveryPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        Manifest.permission.NEARBY_WIFI_DEVICES
    else
        Manifest.permission.ACCESS_FINE_LOCATION

    private val _hasPermission = MutableStateFlow(hasDiscoveryPermission())
    private val _selectedOwnerId = MutableStateFlow<String?>(null)

    private var userHasChosen = false
    private var autoSwitched = false

    private val sessionState = combine(
        orbitRepo.songs,
        orbitRepo.deviceNames,
        orbitRepo.loadedOwners,
        _selectedOwnerId
    ) { songs, deviceNames, loadedOwners, selectedId ->
        val owners = deviceNames
            .map { (id, name) -> OrbitOwner(id, name.ifBlank { id.take(6) }, id in loadedOwners) }
            .sortedBy { it.displayName.lowercase() }

        val selected = owners.find { it.deviceId == selectedId }

        OrbitSongsUiState.Session(
            owners = owners,
            selected = selected,
            songs = if (selected == null) emptyList()
            else songs.filter { it.ownerDeviceId == selected.deviceId }
        )
    }

    val uiState: StateFlow<OrbitSongsUiState> = combine(
        _hasPermission,
        orbitService.connectionState,
        sessionState
    ) { hasPermission, connection, session ->
        when {
            !hasPermission -> OrbitSongsUiState.PermissionRequired
            connection is OrbitConnectionState.Connected -> session
            else -> OrbitSongsUiState.NotConnected
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = OrbitSongsUiState.Loading
    )

    init {
        viewModelScope.launch {
            orbitRepo.loadedOwners.collect { loaded ->
                if (!userHasChosen && !autoSwitched && loaded.isNotEmpty()) {
                    autoSwitched = true
                    _selectedOwnerId.value = loaded.first()
                }
            }
        }

        viewModelScope.launch {
            orbitService.connectionState.collect { state ->
                if (state !is OrbitConnectionState.Connected) {
                    userHasChosen = false
                    autoSwitched = false
                    _selectedOwnerId.value = null
                }
            }
        }
    }

    fun onDiscoveryPermissionGranted() {
        _hasPermission.value = true
    }

    fun selectSession() {
        userHasChosen = true
        _selectedOwnerId.value = null
    }

    fun selectOwner(deviceId: String) {
        userHasChosen = true
        _selectedOwnerId.value = deviceId
    }

    private fun hasDiscoveryPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, discoveryPermission) ==
                PackageManager.PERMISSION_GRANTED

    fun startSession() {
        _discoveryTimedOut.value = false
        orbitService.startSession()
    }

    fun discoverSessions() {
        _discoveryTimedOut.value = false
        orbitService.discoverSessions()

        discoveryTimeoutJob?.cancel()
        discoveryTimeoutJob = viewModelScope.launch {
            delay(20_000.milliseconds)
            if (orbitService.connectionState.value is OrbitConnectionState.Discovering &&
                orbitService.discoveredHosts.value.isEmpty()
            ) {
                _discoveryTimedOut.value = true
                orbitService.leaveSession()
            }
        }
    }

    fun joinHost(host: DiscoveredHost) {
        discoveryTimeoutJob?.cancel()
        _discoveryTimedOut.value = false
        orbitService.joinSession(host)
    }

    fun buildJoinQrPayload(): String = orbitService.buildJoinQrPayload()

    fun joinFromQr(payload: String) {
        discoveryTimeoutJob?.cancel()
        _discoveryTimedOut.value = false
        orbitService.joinFromQr(payload)
    }

    fun stopSession() {
        discoveryTimeoutJob?.cancel()
        _discoveryTimedOut.value = false
        orbitService.leaveSession()
    }
}