package com.pg_axis.musicaxs.services

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.pg_axis.musicaxs.models.Song
import java.io.InputStream
import java.io.OutputStream

object M3uHandler {
    fun export(context: Context, songs: List<Song>, out: OutputStream) {
        val writer = out.bufferedWriter()
        writer.write("#EXTM3U\n")
        songs.forEach { song ->
            val durationSec = song.durationMs / 1000
            writer.write("#EXTINF:$durationSec,${song.artist} - ${song.title}\n")
            val path = resolveFilePath(context, song.uri) ?: song.uri.toString()
            writer.write("$path\n")
        }
        writer.flush()
    }

    private fun resolveFilePath(context: Context, uri: Uri): String? {
        val projection = arrayOf(MediaStore.Audio.Media.DATA)
        return context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA))
        }
    }

    fun import(context: Context, input: InputStream): List<Long> {
        val lines = input.bufferedReader().readLines()
        val paths = lines.filter { !it.startsWith("#") && it.isNotBlank() }
        return paths.mapNotNull { path ->
            val filename = path.substringAfterLast("/")
            resolveIdByFilename(context, filename)
        }
    }

    private fun resolveIdByFilename(context: Context, filename: String): Long? {
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DATA)
        val selection = "${MediaStore.Audio.Media.DATA} LIKE ?"
        return context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection, selection, arrayOf("%$filename"), null
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
        }
    }
}