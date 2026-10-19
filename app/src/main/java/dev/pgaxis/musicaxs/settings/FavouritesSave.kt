package dev.pgaxis.musicaxs.settings

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshotFlow
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import dev.pgaxis.musicaxs.models.Order
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FavouritesSave private constructor(context: Context) {

    companion object {
        @Volatile
        private var instance: FavouritesSave? = null

        fun getInstance(context: Context): FavouritesSave =
            instance ?: synchronized(this) {
                instance ?: FavouritesSave(context.applicationContext)
                    .also { instance = it }
            }
    }

    private val path = context.filesDir.resolve("favourites.json")
    private val gson = Gson()

    private val _favourites = mutableStateMapOf<Long, Long>()

    private val _order = MutableStateFlow(Order.CUSTOM)
    val orderFlow: StateFlow<Order> = _order.asStateFlow()

    fun isFavourite(uri: Uri): Boolean = idOf(uri) in _favourites

    fun toggle(uri: Uri, liked: Boolean) {
        val id = idOf(uri)

        if (liked) {
            if (id in _favourites) return

            _favourites[id] = _favourites.size.toLong()
        } else {
            if (_favourites.remove(id) == null) return

            normalizeOrder()
        }

        save()
    }

    val order: Order
        get() = _order.value

    fun setOrderState(order: Order) {
        if (_order.value == order) return
        _order.value = order
        save()
    }

    val orderedIdsFlow: Flow<List<Long>> = snapshotFlow {
        _favourites.entries
            .sortedBy { it.value }
            .map { it.key }
    }

    fun save() {
        val favouritesType = object : TypeToken<Map<Long, Long>>() {}.type

        val json = JsonObject().apply {
            add("favourites", gson.toJsonTree(_favourites.toMap(), favouritesType))
            addProperty("order", order.name)
        }

        path.writeText(gson.toJson(json))
    }

    fun load() {
        if (!path.exists()) return

        try {
            val json = gson.fromJson(path.readText(), JsonObject::class.java)
                ?: return

            if (json.has("favourites") && json.get("favourites").isJsonObject) {
                val favouritesType = object : TypeToken<Map<Long, Long>>() {}.type

                val loaded = gson.fromJson<Map<Long, Long>>(
                    json.getAsJsonObject("favourites"),
                    favouritesType
                )

                _order.value = json.get("order")
                    ?.takeUnless { it.isJsonNull }
                    ?.asString
                    ?.let { name ->
                        runCatching { Order.valueOf(name) }.getOrNull()
                    }
                    ?: Order.CUSTOM

                _favourites.clear()

                loaded?.entries
                    ?.sortedBy { it.value }
                    ?.forEachIndexed { index, entry ->
                        _favourites[entry.key] = index.toLong()
                    }
            } else {
                val favouritesType = object : TypeToken<Map<Long, Long>>() {}.type
                val loaded = gson.fromJson<Map<Long, Long>>(json, favouritesType)

                _order.value = Order.CUSTOM
                _favourites.clear()

                loaded?.entries
                    ?.sortedBy { it.value }
                    ?.forEachIndexed { index, entry ->
                        _favourites[entry.key] = index.toLong()
                    }

                save()
            }
        } catch (e: Exception) {
            Log.e("FavSave", "Failed to load favourites", e)
        }
    }

    fun reorder(songIds: List<Long>) {
        if (songIds.size != _favourites.size ||
            songIds.toSet() != _favourites.keys
        ) {
            throw IllegalArgumentException(
                "New order must contain every favourite ID exactly once"
            )
        }

        songIds.forEachIndexed { index, id ->
            _favourites[id] = index.toLong()
        }

        save()
    }

    fun removeDeletedSongs(context: Context) {
        val deletedIds = _favourites.keys - queryExistingSongIds(context)

        if (deletedIds.isEmpty()) return

        deletedIds.forEach { id ->
            _favourites.remove(id)
        }

        normalizeOrder()
        save()
    }

    private fun normalizeOrder() {
        _favourites.entries
            .sortedBy { it.value }
            .map { it.key }
            .forEachIndexed { index, id ->
                _favourites[id] = index.toLong()
            }
    }

    private fun queryExistingSongIds(context: Context): Set<Long> {
        val ids = mutableSetOf<Long>()

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Media._ID),
            null,
            null,
            null
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)

            while (cursor.moveToNext()) {
                ids.add(cursor.getLong(idCol))
            }
        }

        return ids
    }

    private fun idOf(uri: Uri): Long =
        uri.lastPathSegment?.toLongOrNull()
            ?: throw IllegalArgumentException("Not a MediaStore URI: $uri")

    init {
        load()
    }
}