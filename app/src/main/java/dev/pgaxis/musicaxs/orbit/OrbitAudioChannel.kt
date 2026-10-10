package dev.pgaxis.musicaxs.orbit

import android.content.Context
import android.util.Log
import dev.pgaxis.musicaxs.repositories.SongRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

private data class GetRequestData(
    val ownerId: String,
    val songId: Long,
    val position: Long,
    val out: DataOutputStream
)

object OrbitAudioProtocol {
    const val SCHEME = "orbitsong"
    const val PORT = 8989
    const val MAGIC = 0x4F524254
    const val TYPE_GET = 1
    const val TYPE_SERVE = 2

    fun writeGet(out: DataOutputStream, ownerDeviceId: String, localSongId: Long, position: Long) {
        out.writeInt(MAGIC)
        out.writeByte(TYPE_GET)
        out.writeUTF(ownerDeviceId)
        out.writeLong(localSongId)
        out.writeLong(position)
        out.flush()
    }
}

class OrbitAudioChannel(
    private val context: Context,
    private val scope: CoroutineScope,
    private val localDeviceId: () -> String,
    private val sendControl: (deviceId: String, message: OrbitMessage) -> Boolean
) {
    private companion object {
        const val TAG = "OrbitAudio"
        const val CONNECT_TIMEOUT_MS = 3000
        const val IO_TIMEOUT_MS = 15_000
        const val SERVE_WAIT_MS = 5000L
        const val BUFFER = 32 * 1024
    }

    private val songRepo = SongRepository.getInstance()
    private val pendingServes = ConcurrentHashMap<String, CompletableDeferred<Socket>>()
    private val openSockets: MutableSet<Socket> = Collections.newSetFromMap(ConcurrentHashMap())

    private var serverJob: Job? = null
    @Volatile private var serverSocket: ServerSocket? = null

    // ---- Host side -----------------------------------------------------------------------

    fun startServer() {
        if (serverJob?.isActive == true) return
        serverJob = scope.launch(Dispatchers.IO) {
            val server = ServerSocket()
            try {
                server.reuseAddress = true
                server.bind(InetSocketAddress(OrbitAudioProtocol.PORT))
            } catch (e: IOException) {
                Log.e(TAG, "Failed to open audio port: ${e.message}")
                try { server.close() } catch (_: IOException) { }
                return@launch
            }
            serverSocket = server
            Log.d(TAG, "Audio server listening on ${OrbitAudioProtocol.PORT}")

            while (isActive) {
                val socket = try {
                    server.accept()
                } catch (_: IOException) {
                    break
                }
                launch { handleConnection(socket) }
            }
        }
    }

    private suspend fun handleConnection(socket: Socket) {
        openSockets.add(socket)
        var handedOver = false
        try {
            socket.soTimeout = IO_TIMEOUT_MS
            withContext(Dispatchers.IO) {
                val input = DataInputStream(socket.getInputStream())
                if (input.readInt() != OrbitAudioProtocol.MAGIC) return@withContext
                when (input.readUnsignedByte()) {
                    OrbitAudioProtocol.TYPE_GET -> handleGet(socket, input)
                    OrbitAudioProtocol.TYPE_SERVE -> handedOver = handleServe(socket, input)
                }
            }
        } catch (e: IOException) {
            Log.d(TAG, "Audio connection ended: ${e.message}")
        } finally {
            if (!handedOver) close(socket)
        }
    }

    private suspend fun handleGet(requester: Socket, input: DataInputStream) {
        val (ownerId, songId, position, out) = withContext(Dispatchers.IO) {
            val ownerId = input.readUTF()
            val songId = input.readLong()
            val position = input.readLong()
            val out = DataOutputStream(
                BufferedOutputStream(requester.getOutputStream(), BUFFER)
            )

            GetRequestData(ownerId, songId, position, out)
        }

        if (ownerId == localDeviceId()) {
            streamSong(songId, position, out)
            return
        }

        val requestId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<Socket>()
        pendingServes[requestId] = deferred

        val ownerSocket = try {
            if (!sendControl(ownerId, OrbitMessage.StreamRequest(requestId, songId, position))) null
            else withTimeoutOrNull(SERVE_WAIT_MS.milliseconds) { deferred.await() }
        } finally {
            pendingServes.remove(requestId)
        }

        if (ownerSocket == null) {
            deferred.cancel()
            withContext(Dispatchers.IO) {
                out.writeLong(-1L)
                out.flush()
            }
            return
        }

        try {
            withContext(Dispatchers.IO) {
                val src = ownerSocket.getInputStream()
                val buf = ByteArray(BUFFER)
                while (true) {
                    val n = src.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                }
                out.flush()
            }
        } finally {
            close(ownerSocket)
        }
    }

    private fun handleServe(socket: Socket, input: DataInputStream): Boolean {
        val requestId = input.readUTF()
        val deferred = pendingServes.remove(requestId) ?: return false
        return deferred.complete(socket)
    }

    // ---- Owner side ----------------------------------------------------------------------

    fun serveRequest(requestId: String, localSongId: Long, position: Long, hostAddress: String) {
        scope.launch(Dispatchers.IO) {
            val socket = Socket()
            try {
                socket.connect(InetSocketAddress(hostAddress, OrbitAudioProtocol.PORT), CONNECT_TIMEOUT_MS)
                openSockets.add(socket)
                val out = DataOutputStream(BufferedOutputStream(socket.getOutputStream(), BUFFER))
                out.writeInt(OrbitAudioProtocol.MAGIC)
                out.writeByte(OrbitAudioProtocol.TYPE_SERVE)
                out.writeUTF(requestId)
                out.flush()
                streamSong(localSongId, position, out)
            } catch (e: IOException) {
                Log.d(TAG, "Serving $localSongId ended: ${e.message}")
            } finally {
                close(socket)
            }
        }
    }

    // ---- Shared --------------------------------------------------------------------------

    private fun streamSong(localSongId: Long, position: Long, out: DataOutputStream) {
        val afd = songRepo.resolveSong(localSongId)?.let {
            try { context.contentResolver.openAssetFileDescriptor(it.uri, "r") } catch (_: Exception) { null }
        }
        if (afd == null) {
            out.writeLong(-1L)
            out.flush()
            return
        }
        afd.use {
            val total = afd.length.takeIf { it >= 0 } ?: afd.parcelFileDescriptor.statSize
            out.writeLong(total)
            afd.createInputStream().use { input ->
                input.channel.position(afd.startOffset + position.coerceIn(0L, total))
                val buf = ByteArray(BUFFER)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                }
            }
            out.flush()
        }
    }

    private fun close(socket: Socket) {
        openSockets.remove(socket)
        try { socket.close() } catch (_: IOException) { }
    }

    fun stop() {
        serverJob?.cancel()
        serverJob = null
        try { serverSocket?.close() } catch (_: IOException) { }
        serverSocket = null
        pendingServes.values.forEach { it.cancel() }
        pendingServes.clear()
        openSockets.toList().forEach { close(it) }
    }
}