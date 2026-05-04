package com.pg_axis.musicaxs.settings

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshotFlow
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow

class FavouritesSave private constructor(context: Context) {

    companion object {
        @Volatile
        private var instance: FavouritesSave? = null

        fun getInstance(context: Context): FavouritesSave =
            instance ?: synchronized(this) {
                instance ?: FavouritesSave(context.applicationContext).also { instance = it }
            }
    }

    private val path = context.filesDir.resolve("favourites.json")
    private val gson = Gson()

    private val _favourites = mutableStateMapOf<Long, Long>()

    fun isFavourite(uri: Uri): Boolean = idOf(uri) in _favourites

    fun toggle(uri: Uri, liked: Boolean) {
        val id = idOf(uri)
        if (liked) {
            if (id in _favourites) return
            else _favourites[id] = System.currentTimeMillis()
            save()
        }
        else {
            if (id in _favourites) _favourites.remove(id)
            save()
        }
    }

    // Returns favourite IDs sorted newest-first.
    fun orderedIds(): List<Long> =
        _favourites.entries.sortedBy { it.value }.map { it.key }

    val orderedIdsFlow: Flow<List<Long>> = snapshotFlow {
        _favourites.entries
            .sortedBy { it.value }
            .map { it.key }
    }


    fun save() {
        val type = object : TypeToken<Map<Long, Long>>() {}.type
        path.writeText(gson.toJson(_favourites.toMap(), type))
    }

    fun load() {
        if (!path.exists()) return
        try {
            val type = object : TypeToken<Map<Long, Long>>() {}.type
            gson.fromJson<Map<Long, Long>>(path.readText(), type)?.let {
                _favourites.clear()
                _favourites.putAll(it)
            }
        } catch (_: Exception) { }
    }

    private fun idOf(uri: Uri): Long =
        uri.lastPathSegment?.toLongOrNull()
            ?: throw IllegalArgumentException("Not a MediaStore URI: $uri")

    init { load() }
}