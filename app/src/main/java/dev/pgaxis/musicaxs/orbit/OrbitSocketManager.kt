package dev.pgaxis.musicaxs.orbit

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.time.Duration.Companion.milliseconds

data class OrbitPeerIdentity(
    val deviceId: String,
    val deviceName: String,
    val protocolVersion: Int = OrbitProtocol.VERSION
)

data class IncomingMessage(val from: OrbitPeerIdentity, val message: OrbitMessage)

private fun Socket.closeQuietly() {
    try { close() } catch (_: IOException) { }
}

class OrbitSocketManager(private val scope: CoroutineScope) {

    companion object {
        private const val PORT = 8988
        private const val CONNECT_RETRY_ATTEMPTS = 10
        private const val CONNECT_RETRY_DELAY_MS = 1000L
        private const val CONNECT_TIMEOUT_MS = 3000
    }

    private class PeerConnection(val socket: Socket) {
        val input = DataInputStream(socket.getInputStream())
        val output = DataOutputStream(socket.getOutputStream())

        val outbox = Channel<OrbitMessage>(Channel.UNLIMITED)

        @Volatile
        var identity: OrbitPeerIdentity? = null

        fun close() = socket.closeQuietly()
    }

    private var serverSocket: ServerSocket? = null
    private var hostJob: Job? = null
    private var guestJob: Job? = null

    @Volatile
    private var pendingSocket: Socket? = null

    private val connections = mutableListOf<PeerConnection>()

    private val _peers = MutableStateFlow<List<OrbitPeerIdentity>>(emptyList())
    val peers: StateFlow<List<OrbitPeerIdentity>> = _peers.asStateFlow()

    private val _incoming = MutableSharedFlow<IncomingMessage>(extraBufferCapacity = 256)
    val incoming: SharedFlow<IncomingMessage> = _incoming.asSharedFlow()

    private var localDeviceId = ""
    private var localDeviceName = ""

    @Volatile
    private var isHost = false

    // ---- Sending -------------------------------------------------------------------------

    fun sendTo(deviceId: String, message: OrbitMessage): Boolean {
        val target = synchronized(connections) {
            connections.firstOrNull { it.identity?.deviceId == deviceId }
        } ?: return false
        return target.outbox.trySend(message).isSuccess
    }

    fun broadcast(message: OrbitMessage, exceptDeviceId: String? = null) {
        val targets = synchronized(connections) {
            connections.filter {
                val id = it.identity?.deviceId
                id != null && id != exceptDeviceId
            }
        }
        targets.forEach { it.outbox.trySend(message) }
    }

    // ---- Hosting / joining ---------------------------------------------------------------

    fun startAsHost(deviceId: String, deviceName: String) {
        localDeviceId = deviceId
        localDeviceName = deviceName
        isHost = true

        hostJob?.cancel()
        hostJob = scope.launch(Dispatchers.IO) {
            val server = ServerSocket()
            try {
                server.reuseAddress = true
                server.bind(InetSocketAddress(PORT))
            } catch (e: IOException) {
                Log.e("Orbit", "Failed to open ServerSocket: ${e.message}")
                try { server.close() } catch (_: IOException) { }
                return@launch
            }

            if (!isActive) {
                try { server.close() } catch (_: IOException) { }
                return@launch
            }
            serverSocket = server
            Log.d("Orbit", "Listening for peers on port $PORT")

            while (isActive) {
                val socket = try {
                    server.accept()
                } catch (e: IOException) {
                    Log.d("Orbit", "ServerSocket closed: ${e.message}")
                    break
                }
                Log.d("Orbit", "Accepted connection from ${socket.inetAddress}")
                launch(Dispatchers.IO) {
                    val connection = try {
                        PeerConnection(socket)
                    } catch (_: IOException) {
                        socket.closeQuietly()
                        return@launch
                    }
                    handleConnection(connection)
                }
            }
        }
    }

    fun startAsGuest(hostAddress: String, deviceId: String, deviceName: String) {
        localDeviceId = deviceId
        localDeviceName = deviceName
        isHost = false

        guestJob?.cancel()
        guestJob = scope.launch(Dispatchers.IO) {
            var connected: Socket? = null

            for (attempt in 1..CONNECT_RETRY_ATTEMPTS) {
                if (!isActive) return@launch
                val socket = Socket()
                pendingSocket = socket
                try {
                    socket.connect(InetSocketAddress(hostAddress, PORT), CONNECT_TIMEOUT_MS)
                    connected = socket
                    break
                } catch (e: IOException) {
                    socket.closeQuietly()
                    if (!isActive) return@launch
                    Log.d("Orbit", "Connect attempt $attempt to $hostAddress:$PORT failed: ${e.message}")
                } finally {
                    pendingSocket = null
                }
                delay(CONNECT_RETRY_DELAY_MS.milliseconds)
            }

            if (connected == null) {
                Log.e("Orbit", "Gave up connecting to host at $hostAddress:$PORT")
                return@launch
            }
            if (!isActive) {
                connected.closeQuietly()
                return@launch
            }

            val connection = try {
                PeerConnection(connected)
            } catch (_: IOException) {
                connected.closeQuietly()
                return@launch
            }
            handleConnection(connection)
        }
    }

    // ---- Connection handling -------------------------------------------------------------

    private suspend fun handleConnection(connection: PeerConnection) {
        coroutineScope {
            synchronized(connections) { connections.add(connection) }

            val writer = launch(Dispatchers.IO) {
                try {
                    for (message in connection.outbox) {
                        OrbitFraming.write(connection.output, message)
                    }
                } catch (e: IOException) {
                    Log.d("Orbit", "Write failed: ${e.message}")
                    connection.close()
                }
            }

            try {
                connection.outbox.trySend(OrbitMessage.Hello(localDeviceId, localDeviceName))

                while (true) {
                    val message = OrbitFraming.decode(connection.input) ?: break
                    handleMessage(connection, message)
                }
            } catch (e: IOException) {
                Log.d("Orbit", "Connection closed: ${e.message}")
            } finally {
                synchronized(connections) { connections.remove(connection) }
                connection.outbox.close()
                connection.close()
                writer.cancel()
                publishPeers()
                if (isHost && connection.identity != null) broadcastRoster()
            }
        }
    }

    private suspend fun handleMessage(connection: PeerConnection, message: OrbitMessage) {
        when (message) {
            is OrbitMessage.Hello -> {
                connection.identity = OrbitPeerIdentity(
                    message.deviceId, message.deviceName, message.protocolVersion
                )
                Log.d(
                    "Orbit",
                    "Hello from ${message.deviceName} (${message.deviceId}), proto ${message.protocolVersion}"
                )
                publishPeers()
                if (isHost) broadcastRoster()
            }

            is OrbitMessage.LibraryChunk -> {
                val from = connection.identity ?: return
                if (isHost) broadcast(message, exceptDeviceId = from.deviceId)
                _incoming.emit(IncomingMessage(from, message))
            }

            is OrbitMessage.Roster -> {
                val from = connection.identity ?: return
                _incoming.emit(IncomingMessage(from, message))
            }

            is OrbitMessage.StreamRequest -> {
                val from = connection.identity ?: return
                _incoming.emit(IncomingMessage(from, message))
            }

            is OrbitMessage.Unknown -> Log.d("Orbit", "Ignoring unknown message type")
        }
    }

    private fun identifiedPeers(): List<OrbitPeerIdentity> =
        synchronized(connections) { connections.mapNotNull { it.identity } }

    private fun publishPeers() {
        _peers.value = identifiedPeers()
    }

    private fun broadcastRoster() {
        val members = listOf(OrbitPeerIdentity(localDeviceId, localDeviceName)) + identifiedPeers()
        broadcast(OrbitMessage.Roster(members))
    }

    fun stop() {
        hostJob?.cancel()
        hostJob = null
        guestJob?.cancel()
        guestJob = null

        pendingSocket?.closeQuietly()
        pendingSocket = null

        try { serverSocket?.close() } catch (_: IOException) { }
        serverSocket = null

        synchronized(connections) {
            connections.forEach { it.close() }
            connections.clear()
        }
        _peers.value = emptyList()
    }
}