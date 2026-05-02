package com.pg_axis.musicaxs.side_pages

import android.app.Application
import android.app.PendingIntent
import android.content.ContentValues
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import java.io.File
import kotlin.arrayOf
import kotlin.math.pow

class SongDetailViewModel(app: Application) : AndroidViewModel(app) {

    private val context = getApplication<Application>()

    var title by mutableStateOf("")
        private set
    var artist by mutableStateOf("")
        private set
    var album by mutableStateOf("")
        private set
    var track by mutableStateOf("")
        private set
    var duration by mutableStateOf("")
        private set
    var fileSize by mutableStateOf("")
        private set
    var mimeType by mutableStateOf("")
        private set
    var filePath: String? by mutableStateOf("")
        private set

    var isLoading by mutableStateOf(true)
        private set

    var isSaving by mutableStateOf(false)
        private set

    var isEditable by mutableStateOf(false)
        private set

    private var originalTitle = ""
    private var originalArtist = ""
    private var originalAlbum = ""
    private var originalTrack = ""

    private var currentUri: Uri? = null

    val isModified: Boolean
        get() = title != originalTitle ||
                artist != originalArtist ||
                album != originalAlbum ||
                track != originalTrack

    fun load(uri: Uri) {
        currentUri = uri

        viewModelScope.launch(Dispatchers.IO) {
            val projection = arrayOf(
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.TRACK,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.MIME_TYPE,
                MediaStore.Audio.Media.SIZE,
            )

            context.contentResolver.query(uri, projection, null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                        val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                        val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                        val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                        val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                        val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                        val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)

                        val t = cursor.getString(titleCol) ?: ""
                        val a = cursor.getString(artistCol) ?: ""
                        val al = cursor.getString(albumCol) ?: ""
                        val tr = decodeTrackNumber(cursor.getInt(trackCol)).toString()
                        val d = formatDuration(cursor.getLong(durationCol))
                        val m = cursor.getString(mimeCol) ?: ""
                        val s = formatBytes(cursor.getLong(sizeCol))

                        withContext(Dispatchers.Main) {
                            title = t
                            artist = a
                            album = al
                            track = tr
                            duration = d
                            mimeType = m
                            fileSize = s

                            isEditable = mimeType == "audio/mpeg"

                            originalTitle = t
                            originalArtist = a
                            originalAlbum = al
                            originalTrack = tr

                            filePath = resolvePath(uri)

                            isLoading = false
                        }
                    }
                }
        }
    }

    fun updateTitle(v: String) { title = v }
    fun updateArtist(v: String) { artist = v }
    fun updateAlbum(v: String) { album = v }
    fun updateTrack(v: String) { track = v.filter { it.isDigit() } }

    fun createWriteRequest(): PendingIntent? {
        val uri = currentUri ?: return null

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            MediaStore.createWriteRequest(
                context.contentResolver,
                listOf(uri)
            )
        } else null
    }

    fun safeSet(tag: Tag, key: FieldKey, value: String) {
        try {
            tag.setField(key, value)
        } catch (_: Exception) {
            tag.deleteField(key)
            tag.setField(key, value)
        }
    }

    fun save(onDone: () -> Unit = {}) {
        val uri = currentUri ?: return
        isSaving = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val path = resolveSystemPath(uri)

                if (path != null) {
                    val cacheFile = File(context.cacheDir, "edit_tmp_${System.currentTimeMillis()}.mp3")
                    try {
                        File(path).copyTo(cacheFile, overwrite = true)

                        val audio = AudioFileIO.read(cacheFile)
                        val tag = audio.tagOrCreateAndSetDefault
                        safeSet(tag, FieldKey.TITLE, title)
                        safeSet(tag, FieldKey.ARTIST, artist)
                        safeSet(tag, FieldKey.ALBUM, album)
                        safeSet(tag, FieldKey.TRACK, track.ifBlank { "0" })
                        audio.commit()

                        context.contentResolver.openOutputStream(uri, "rwt")?.use { out ->
                            cacheFile.inputStream().use { it.copyTo(out) }
                        }
                    } finally {
                        cacheFile.delete()
                    }

                    MediaScannerConnection.scanFile(context, arrayOf(path), null, null)

                } else {
                    val values = ContentValues().apply {
                        put(MediaStore.Audio.Media.TITLE, title)
                        put(MediaStore.Audio.Media.ARTIST, artist)
                        put(MediaStore.Audio.Media.ALBUM, album)
                        put(MediaStore.Audio.Media.TRACK, track.toIntOrNull() ?: 0)
                    }
                    context.contentResolver.update(uri, values, null, null)
                }

                withContext(Dispatchers.Main) {
                    originalTitle = title
                    originalArtist = artist
                    originalAlbum = album
                    originalTrack = track
                    isSaving = false
                    onDone()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) { isSaving = false }
            }
        }
    }

    private fun resolveSystemPath(uri: Uri): String? {
        val projection = arrayOf(MediaStore.Audio.Media.DATA)

        context.contentResolver.query(uri, projection, null, null, null)
            ?.use { cursor ->
                return if (cursor.moveToFirst()) {
                    cursor.getString(0)
                } else null
            }

        return null
    }

    private fun resolvePath(uri: Uri): String? {
        val projection = arrayOf(MediaStore.Audio.Media.DATA)

        val rawPath = context.contentResolver.query(uri, projection, null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)
                } else null
            }

        return rawPath?.let { formatStoragePath(it) }
    }

    private fun formatStoragePath(path: String): String {
        return when {
            path.startsWith("/storage/emulated/0") ->
                "/Internal storage" + path.removePrefix("/storage/emulated/0")

            path.startsWith("/storage/") -> {
                val withoutPrefix = path.removePrefix("/storage/")
                val firstSlash = withoutPrefix.indexOf('/')

                if (firstSlash != -1) {
                    val relative = withoutPrefix.substring(firstSlash)
                    "/SD card$relative"
                } else {
                    "/SD card"
                }
            }

            else -> path
        }
    }

    private fun formatDuration(ms: Long): String {
        val totalSeconds = (ms + 500) / 1000

        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s)
        else "%d:%02d".format(m, s)
    }

    private fun decodeTrackNumber(raw: Int): Int = raw % 1000

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"

        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (kotlin.math.log10(bytes.toDouble()) / kotlin.math.log10(1000.0)).toInt()

        val value = bytes / 1000.0.pow(digitGroups.toDouble())

        return if (value >= 10 || value % 1 == 0.0) {
            "%d %s".format(value.toLong(), units[digitGroups])
        } else {
            "%.1f %s".format(value, units[digitGroups])
        }
    }
}