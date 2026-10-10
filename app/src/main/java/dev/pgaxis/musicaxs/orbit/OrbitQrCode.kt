package dev.pgaxis.musicaxs.orbit

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import com.google.zxing.BarcodeFormat
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import androidx.core.net.toUri

object OrbitQrCode {
    data class JoinInfo(val orbitId: String, val deviceName: String, val sessionName: String)

    fun buildJoinPayload(orbitId: String, deviceName: String, sessionName: String): String =
        Uri.Builder()
            .scheme("orbit")
            .authority("join")
            .appendQueryParameter("id", orbitId)
            .appendQueryParameter("name", deviceName)
            .appendQueryParameter("session", sessionName)
            .build()
            .toString()

    fun parseJoinPayload(payload: String): JoinInfo? = try {
        val uri = payload.toUri()
        if (uri.scheme != "orbit" || uri.authority != "join") null
        else {
            val id = uri.getQueryParameter("id")
            if (id.isNullOrEmpty()) null
            else JoinInfo(
                orbitId = id,
                deviceName = uri.getQueryParameter("name") ?: "Host",
                sessionName = uri.getQueryParameter("session") ?: "Orbit Session"
            )
        }
    } catch (_: Exception) {
        null
    }

    fun generate(payload: String, sizePx: Int = 512): Bitmap? = try {
        val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, sizePx, sizePx)
        createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565).apply {
            for (x in 0 until sizePx) {
                for (y in 0 until sizePx) {
                    this[x, y] = if (matrix[x, y]) Color.BLACK else Color.WHITE
                }
            }
        }
    } catch (_: WriterException) {
        null
    }
}