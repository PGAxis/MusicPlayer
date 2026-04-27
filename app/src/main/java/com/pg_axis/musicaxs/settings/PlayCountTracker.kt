package com.pg_axis.musicaxs.settings

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class PlayCountTracker private constructor(context: Context) {

    companion object {
        @Volatile
        private var instance: PlayCountTracker? = null

        fun getInstance(context: Context): PlayCountTracker =
            instance ?: synchronized(this) {
                instance ?: PlayCountTracker(context.applicationContext).also { instance = it }
            }
    }

    private val gson = Gson()
    private val file = context.filesDir.resolve("play_counts.json")

    // uri string -> PlayEntry
    private val entries = mutableMapOf<String, PlayEntry>()

    private val _entriesFlow = MutableStateFlow<Map<String, PlayEntry>>(emptyMap())
    val entriesFlow: StateFlow<Map<String, PlayEntry>> = _entriesFlow

    init { load() }

    data class PlayEntry(
        val count: Int = 0,
        val lastPlayedMs: Long = 0L
    )

    fun recordPlay(uri: Uri) {
        val key = uri.toString()
        val existing = entries[key] ?: PlayEntry()

        entries[key] = existing.copy(
            count = existing.count + 1,
            lastPlayedMs = System.currentTimeMillis()
        )

        _entriesFlow.value = entries.toMap()
        save()
    }

    fun getCount(uri: Uri): Int = entries[uri.toString()]?.count ?: 0

    fun getLastPlayed(uri: Uri): Long = entries[uri.toString()]?.lastPlayedMs ?: 0L

    /** Returns URIs sorted by play count descending */
    fun topPlayed(limit: Int = 50): List<String> =
        entries.entries
            .sortedByDescending { it.value.count }
            .take(limit)
            .map { it.key }

    /** Returns URIs sorted by last played descending */
    fun recentlyPlayed(limit: Int = 50): List<String> =
        entries.entries
            .sortedByDescending { it.value.lastPlayedMs }
            .take(limit)
            .map { it.key }

    private fun save() {
        val type = object : TypeToken<Map<String, PlayEntry>>() {}.type
        file.writeText(gson.toJson(entries, type))
    }

    private fun load() {
        if (!file.exists()) return
        try {
            val type = object : TypeToken<Map<String, PlayEntry>>() {}.type
            gson.fromJson<Map<String, PlayEntry>>(file.readText(), type)?.let {
                entries.putAll(it)
            }
        } catch (_: Exception) { }
    }
}