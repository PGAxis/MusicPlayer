package com.pg_axis.musicaxs.services

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.util.Log
import com.pg_axis.musicaxs.repositories.PlaylistRepository
import com.pg_axis.musicaxs.settings.SettingsSave

class MusicAxsProvider : ContentProvider() {

    private val repo by lazy { PlaylistRepository.getInstance(context!!) }

    override fun onCreate() = true

    override fun query(
        uri: Uri, projection: Array<out String>?,
        selection: String?, selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        if (!isAllowed()) return null

        return when (uri) {
            MusicAxsContract.Playlists.URI -> {
                val playlists = repo.playlists.value
                val matrix = MatrixCursor(
                    arrayOf(
                        MusicAxsContract.Playlists.ID,
                        MusicAxsContract.Playlists.NAME,
                        MusicAxsContract.Playlists.SONG_COUNT
                    )
                )
                playlists.forEach { playlist ->
                    matrix.addRow(arrayOf<Any>(playlist.id, playlist.name, playlist.getSongCount()))
                }
                matrix
            }
            else -> null
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        Log.d("MusicAxsProvider", "insert called, uri=$uri")
        Log.d("MusicAxsProvider", "isAllowed=${isAllowed()}")
        if (!isAllowed()) return null
        values ?: return null

        return when (uri) {
            MusicAxsContract.Songs.URI -> {
                val playlistId = values.getAsLong(MusicAxsContract.Songs.PLAYLIST_ID) ?: return null
                val songId = values.getAsLong(MusicAxsContract.Songs.SONG_ID) ?: return null
                repo.addSong(playlistId, songId)
                MusicAxsContract.Songs.URI
            }
            else -> null
        }
    }

    private fun isAllowed(): Boolean {
        val settings = context?.let { SettingsSave.getInstance(it) } ?: return false
        return settings.allowYTCnv
    }

    override fun getType(uri: Uri) = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}