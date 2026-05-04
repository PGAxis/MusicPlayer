package com.pg_axis.musicaxs.settings

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshotFlow
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow

class FavouritedPlaylistsSave private constructor(context: Context) {

    companion object {
        @Volatile
        private var instance: FavouritedPlaylistsSave? = null

        fun getInstance(context: Context): FavouritedPlaylistsSave =
            instance ?: synchronized(this) {
                instance ?: FavouritedPlaylistsSave(context.applicationContext).also { instance = it }
            }
    }

    private val path = context.filesDir.resolve("favourited_playlists.json")
    private val gson = Gson()

    private val _favourites = mutableStateMapOf<Long, Long>()

    fun isFavourited(playlistId: Long): Boolean = playlistId in _favourites

    fun toggle(playlistId: Long) {
        if (playlistId in _favourites) _favourites.remove(playlistId)
        else _favourites[playlistId] = System.currentTimeMillis()
        save()
    }

    val idsFlow: Flow<List<Long>> = snapshotFlow {
        _favourites.entries.sortedBy { it.value }.map { it.key }
    }

    private fun save() {
        val type = object : TypeToken<Map<Long, Long>>() {}.type
        path.writeText(gson.toJson(_favourites.toMap(), type))
    }

    init {
        if (path.exists()) {
            try {
                val type = object : TypeToken<Map<Long, Long>>() {}.type
                gson.fromJson<Map<Long, Long>>(path.readText(), type)?.let {
                    _favourites.putAll(it)
                }
            } catch (_: Exception) { }
        }
    }
}