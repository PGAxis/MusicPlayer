package dev.pgaxis.musicaxs.orbit

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket

@UnstableApi
class OrbitDataSource(private val hostAddress: () -> String?) : BaseDataSource(/* isNetwork = */ true) {

    private var socket: Socket? = null
    private var input: InputStream? = null
    private var openedUri: Uri? = null
    private var bytesRemaining = 0L
    private var transferStarted = false

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        openedUri = dataSpec.uri

        val owner = dataSpec.uri.authority
        val songId = dataSpec.uri.lastPathSegment?.toLongOrNull()
        val host = hostAddress()
        if (owner == null || songId == null || host == null) {
            throw IOException("Orbit song not reachable: ${dataSpec.uri}")
        }

        val s = Socket()
        try {
            s.connect(InetSocketAddress(host, OrbitAudioProtocol.PORT), 3000)
            s.soTimeout = 15_000
            OrbitAudioProtocol.writeGet(DataOutputStream(s.getOutputStream()), owner, songId, dataSpec.position)

            val stream = BufferedInputStream(s.getInputStream(), 64 * 1024)
            val total = DataInputStream(stream).readLong()
            if (total < 0 || dataSpec.position > total) throw IOException("Owner could not serve song $songId")

            val available = total - dataSpec.position
            bytesRemaining =
                if (dataSpec.length != C.LENGTH_UNSET.toLong()) minOf(dataSpec.length, available) else available
            socket = s
            input = stream
        } catch (e: IOException) {
            try { s.close() } catch (_: IOException) { }
            throw e
        }

        transferStarted = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val n = input!!.read(buffer, offset, minOf(bytesRemaining, length.toLong()).toInt())
        if (n == -1) throw EOFException("Orbit stream ended early")
        bytesRemaining -= n
        bytesTransferred(n)
        return n
    }

    override fun getUri(): Uri? = openedUri

    override fun close() {
        try { socket?.close() } catch (_: IOException) { }
        socket = null
        input = null
        openedUri = null
        if (transferStarted) {
            transferStarted = false
            transferEnded()
        }
    }
}

@UnstableApi
class OrbitRoutingDataSource(
    private val default: DataSource,
    private val orbit: DataSource
) : DataSource {
    private var active: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        default.addTransferListener(transferListener)
        orbit.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val ds = if (dataSpec.uri.scheme == OrbitAudioProtocol.SCHEME) orbit else default
        active = ds
        return ds.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        active!!.read(buffer, offset, length)

    override fun getUri(): Uri? = active?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = active?.responseHeaders ?: emptyMap()

    override fun close() {
        try { active?.close() } finally { active = null }
    }
}