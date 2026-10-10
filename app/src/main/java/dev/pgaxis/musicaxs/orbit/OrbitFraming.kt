package dev.pgaxis.musicaxs.orbit

import org.json.JSONException
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream

object OrbitFraming {
    private const val MAX_MESSAGE_BYTES = 5_000_000

    fun encode(message: OrbitMessage): ByteArray {
        val body = message.toJson().toString().toByteArray(Charsets.UTF_8)
        val length = body.size
        return byteArrayOf(
            (length shr 24).toByte(), (length shr 16).toByte(),
            (length shr 8).toByte(), length.toByte()
        ) + body
    }

    fun write(output: OutputStream, message: OrbitMessage) {
        output.write(encode(message))
        output.flush()
    }

    fun decode(input: InputStream): OrbitMessage? {
        val lenBytes = ByteArray(4)
        if (!readFully(input, lenBytes)) return null

        val length = ((lenBytes[0].toInt() and 0xFF) shl 24) or
                ((lenBytes[1].toInt() and 0xFF) shl 16) or
                ((lenBytes[2].toInt() and 0xFF) shl 8) or
                (lenBytes[3].toInt() and 0xFF)

        if (length !in 1..MAX_MESSAGE_BYTES) return null

        val body = ByteArray(length)
        if (!readFully(input, body)) return null

        return try {
            OrbitMessage.fromJson(JSONObject(body.toString(Charsets.UTF_8)))
        } catch (_: JSONException) {
            null
        }
    }

    private fun readFully(input: InputStream, buffer: ByteArray): Boolean {
        var offset = 0
        while (offset < buffer.size) {
            val count = input.read(buffer, offset, buffer.size - offset)
            if (count == -1) return false
            offset += count
        }
        return true
    }
}