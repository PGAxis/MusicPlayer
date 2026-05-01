package com.pg_axis.musicaxs.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.Gson

class ShuffleSave private constructor(context: Context) {

    companion object {
        @Volatile
        private var instance: ShuffleSave? = null

        fun getInstance(context: Context): ShuffleSave =
            instance ?: synchronized(this) {
                instance ?: ShuffleSave(context.applicationContext).also { instance = it }
            }
    }

    private val path = context.filesDir.resolve("shuffle.json")
    private val gson = Gson()

    var isShuffled by mutableStateOf(false)
        private set

    private var originalQueue: List<String> = emptyList()

    fun getOriginalQueue(): List<String> = originalQueue

    fun setOriginalQueue(uris: List<String>) {
        originalQueue = uris
        save()
    }

    fun updateShuffled(on: Boolean) {
        isShuffled = on
        save()
    }

    // Called when a song is added to queue while shuffle is on
    fun addToOriginal(uri: String) {
        originalQueue = originalQueue + uri
        save()
    }

    // Called when a song is removed from queue while shuffle is on
    fun removeFromOriginal(uri: String) {
        val index = originalQueue.indexOf(uri)
        if (index != -1) originalQueue = originalQueue.toMutableList().also { it.removeAt(index) }
        save()
    }

    private fun save() {
        path.writeText(gson.toJson(ShuffleData(isShuffled, originalQueue)))
    }

    fun load() {
        if (!path.exists()) return
        try {
            gson.fromJson(path.readText(), ShuffleData::class.java)?.let {
                isShuffled = it.isShuffled
                originalQueue = it.originalQueue
            }
        } catch (_: Exception) { }
    }

    private data class ShuffleData(
        val isShuffled: Boolean = false,
        val originalQueue: List<String> = emptyList()
    )

    init { load() }
}