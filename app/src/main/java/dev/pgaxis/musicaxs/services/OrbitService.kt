package dev.pgaxis.musicaxs.services

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import dev.pgaxis.musicaxs.models.OrbitSong
import dev.pgaxis.musicaxs.orbit.ConnectedPeer
import dev.pgaxis.musicaxs.orbit.DiscoveredHost
import dev.pgaxis.musicaxs.orbit.IncomingMessage
import dev.pgaxis.musicaxs.orbit.OrbitAudioChannel
import dev.pgaxis.musicaxs.orbit.OrbitConnectionManager
import dev.pgaxis.musicaxs.orbit.OrbitConnectionState
import dev.pgaxis.musicaxs.orbit.OrbitMessage
import dev.pgaxis.musicaxs.orbit.OrbitPeerIdentity
import dev.pgaxis.musicaxs.orbit.OrbitQrCode
import dev.pgaxis.musicaxs.orbit.OrbitSession
import dev.pgaxis.musicaxs.orbit.OrbitSocketManager
import dev.pgaxis.musicaxs.repositories.OrbitRepository
import dev.pgaxis.musicaxs.repositories.SongRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class OrbitService private constructor(context: Context) {

    companion object {
        private const val PREFS_NAME = "orbit_identity"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_DEVICE_NAME = "device_name"
        private const val DEFAULT_SESSION_NAME = "Orbit Session"
        private const val LIBRARY_CHUNK_SIZE = 300

        @Volatile
        private var instance: OrbitService? = null

        fun getInstance(context: Context): OrbitService =
            instance ?: synchronized(this) {
                instance ?: OrbitService(context.applicationContext).also { instance = it }
            }

        fun peekInstance(): OrbitService? = instance
    }

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val orbitRepo = OrbitRepository.getInstance()
    private val songRepo = SongRepository.getInstance()

    private val connectionManager = OrbitConnectionManager(appContext)
    val connectionState: StateFlow<OrbitConnectionState> = connectionManager.state

    private val socketManager = OrbitSocketManager(scope)
    private val audioChannel = OrbitAudioChannel(
        context = appContext,
        scope = scope,
        localDeviceId = { deviceId },
        sendControl = { id, message -> socketManager.sendTo(id, message) }
    )

    val orbitPeers: StateFlow<List<OrbitPeerIdentity>> = socketManager.peers

    private val _currentSession = MutableStateFlow<OrbitSession?>(null)
    val currentSession: StateFlow<OrbitSession?> = _currentSession.asStateFlow()

    val discoveredHosts: StateFlow<List<DiscoveredHost>> = connectionManager.discoveredHosts
    val connectedPeers: StateFlow<List<ConnectedPeer>> = connectionManager.connectedPeers

    val deviceId: String
        get() = prefs.getString(KEY_DEVICE_ID, null) ?: UUID.randomUUID().toString().also {
            prefs.edit { putString(KEY_DEVICE_ID, it) }
        }

    val rawDeviceName: String
        get() = prefs.getString(KEY_DEVICE_NAME, null) ?: ""

    var deviceName: String
        get() = prefs.getString(KEY_DEVICE_NAME, null) ?: "Device-${deviceId.take(4)}"
        set(value) = prefs.edit {
            if (value.isBlank()) remove(KEY_DEVICE_NAME) else putString(KEY_DEVICE_NAME, value)
        }

    private var pendingSessionName: String = DEFAULT_SESSION_NAME
    private var pendingHostDeviceName: String = ""

    private val pendingLibraries = mutableMapOf<String, MutableList<OrbitSong>>()
    private var knownPeerIds: Set<String> = emptySet()

    init {
        connectionManager.start()
        scope.launch {
            connectionManager.state.collect { state ->
                when (state) {
                    is OrbitConnectionState.Connected -> {
                        if (_currentSession.value == null) {
                            _currentSession.value = OrbitSession(
                                sessionName = pendingSessionName,
                                hostDeviceId = if (state.isGroupOwner) deviceId else "",
                                hostDeviceName = if (state.isGroupOwner) deviceName else pendingHostDeviceName,
                                localDeviceId = deviceId,
                                localDeviceName = deviceName,
                                isHost = state.isGroupOwner
                            )
                            if (state.isGroupOwner) {
                                socketManager.startAsHost(deviceId, deviceName)
                                audioChannel.startServer()
                            } else {
                                socketManager.startAsGuest(state.groupOwnerAddress, deviceId, deviceName)
                            }
                        }
                    }
                    is OrbitConnectionState.Idle, is OrbitConnectionState.Failed -> {
                        _currentSession.value = null
                        socketManager.stop()
                        audioChannel.stop()
                        resetSyncState()
                    }
                    else -> Unit
                }
            }
        }

        scope.launch {
            socketManager.peers.collect { peers ->
                val session = _currentSession.value
                if (session == null) {
                    knownPeerIds = emptySet()
                    return@collect
                }
                if (session.isHost) onHostPeersChanged(peers) else onGuestPeersChanged(session, peers)
            }
        }

        scope.launch {
            socketManager.incoming.collect { handleIncoming(it) }
        }

        scope.launch {
            songRepo.songs.collect {
                if (_currentSession.value != null && knownPeerIds.isNotEmpty()) {
                    sendLibrary(toDeviceId = null, ownerId = deviceId, songs = ownLibrary())
                }
            }
        }
    }

    // ---- Library sync --------------------------------------------------------------------

    private fun onHostPeersChanged(peers: List<OrbitPeerIdentity>) {
        orbitRepo.setMembers(peers.associate { it.deviceId to it.deviceName })

        val ids = peers.map { it.deviceId }.toSet()
        val added = ids - knownPeerIds
        knownPeerIds = ids

        for (newId in added) {
            sendLibrary(newId, deviceId, ownLibrary())
            for (otherId in peers.map { it.deviceId }) {
                if (otherId != newId && otherId in orbitRepo.loadedOwners.value) {
                    sendLibrary(newId, otherId, orbitRepo.songsForOwner(otherId))
                }
            }
        }
    }

    private fun onGuestPeersChanged(session: OrbitSession, peers: List<OrbitPeerIdentity>) {
        val host = peers.firstOrNull() ?: return
        _currentSession.value = session.copy(
            hostDeviceId = host.deviceId,
            hostDeviceName = host.deviceName
        )
        if (knownPeerIds.isEmpty()) {
            knownPeerIds = peers.map { it.deviceId }.toSet()
            sendLibrary(toDeviceId = null, ownerId = deviceId, songs = ownLibrary())
        }
    }

    private fun handleIncoming(incoming: IncomingMessage) {
        when (val message = incoming.message) {
            is OrbitMessage.Roster -> {
                if (_currentSession.value?.isHost == false) {
                    orbitRepo.setMembers(
                        message.peers
                            .filter { it.deviceId != deviceId }
                            .associate { it.deviceId to it.deviceName }
                    )
                }
            }

            is OrbitMessage.LibraryChunk -> {
                if (message.ownerDeviceId == deviceId) return

                val buffer = if (message.index == 0) {
                    mutableListOf<OrbitSong>().also { pendingLibraries[message.ownerDeviceId] = it }
                } else {
                    pendingLibraries[message.ownerDeviceId] ?: return
                }
                buffer += message.songs

                if (message.index == message.total - 1) {
                    pendingLibraries.remove(message.ownerDeviceId)
                    orbitRepo.replaceOwner(message.ownerDeviceId, buffer)
                }
            }

            is OrbitMessage.StreamRequest -> {
                if (_currentSession.value?.isHost != false) return
                val host = audioHostAddress() ?: return
                audioChannel.serveRequest(message.requestId, message.localSongId, message.position, host)
            }

            else -> Unit
        }
    }

    private fun ownLibrary(): List<OrbitSong> {
        val id = deviceId
        return songRepo.songs.value.map { song ->
            OrbitSong(
                orbitId = "$id:${song.id}",
                ownerDeviceId = id,
                localSongId = song.id,
                title = song.title,
                artist = song.artist,
                album = song.album,
                durationMs = song.durationMs,
                hasAlbumArt = song.albumArtUri != null
            )
        }
    }

    private fun sendLibrary(toDeviceId: String?, ownerId: String, songs: List<OrbitSong>) {
        val chunks = songs.chunked(LIBRARY_CHUNK_SIZE).ifEmpty { listOf(emptyList()) }
        chunks.forEachIndexed { index, chunk ->
            val message = OrbitMessage.LibraryChunk(ownerId, index, chunks.size, chunk)
            if (toDeviceId == null) socketManager.broadcast(message)
            else socketManager.sendTo(toDeviceId, message)
        }
    }

    private fun resetSyncState() {
        pendingLibraries.clear()
        knownPeerIds = emptySet()
        orbitRepo.clear()
    }

    // ---- Session control -----------------------------------------------------------------

    private fun discoveryPermission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.NEARBY_WIFI_DEVICES
        else
            Manifest.permission.ACCESS_FINE_LOCATION

    private fun hasDiscoveryPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, discoveryPermission()) ==
                PackageManager.PERMISSION_GRANTED

    fun startSession(sessionName: String = DEFAULT_SESSION_NAME) {
        if (!hasDiscoveryPermission()) {
            connectionManager.reportMissingPermission()
            return
        }
        pendingSessionName = sessionName
        connectionManager.startHosting(deviceId, deviceName, sessionName)
    }

    fun discoverSessions() {
        if (!hasDiscoveryPermission()) {
            connectionManager.reportMissingPermission()
            return
        }
        connectionManager.startDiscovering()
    }

    fun joinSession(host: DiscoveredHost, sessionName: String = DEFAULT_SESSION_NAME) {
        if (!hasDiscoveryPermission()) {
            connectionManager.reportMissingPermission()
            return
        }
        pendingSessionName = host.sessionName.ifEmpty { sessionName }
        pendingHostDeviceName = host.deviceName
        connectionManager.joinSession(host.orbitId)
    }

    fun buildJoinQrPayload(): String =
        OrbitQrCode.buildJoinPayload(deviceId, deviceName, pendingSessionName)

    fun joinFromQr(payload: String) {
        val info = OrbitQrCode.parseJoinPayload(payload)
        if (info == null) {
            Log.w("Orbit", "Ignoring unrecognised QR payload: $payload")
            return
        }
        if (!hasDiscoveryPermission()) {
            Log.w("Orbit", "QR join blocked: discovery permission missing")
            connectionManager.reportMissingPermission()
            return
        }
        Log.d("Orbit", "QR join: ${info.deviceName} (${info.orbitId})")
        pendingSessionName = info.sessionName
        pendingHostDeviceName = info.deviceName
        connectionManager.joinSession(info.orbitId, rediscover = true)
    }

    fun audioHostAddress(): String? = when (val s = connectionState.value) {
        is OrbitConnectionState.Connected -> if (s.isGroupOwner) "127.0.0.1" else s.groupOwnerAddress
        else -> null
    }

    fun leaveSession() {
        connectionManager.leaveSession()
        _currentSession.value = null
    }

    fun shutdown() {
        connectionManager.leaveSession()
        _currentSession.value = null
        socketManager.stop()
        audioChannel.stop()
        resetSyncState()
    }
}