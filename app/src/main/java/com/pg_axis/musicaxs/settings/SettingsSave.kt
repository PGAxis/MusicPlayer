package com.pg_axis.musicaxs.settings

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class SettingsSave private constructor(context: Context): ISettings {

    companion object {
        @Volatile
        private var instance: SettingsSave? = null

        fun getInstance(context: Context): SettingsSave =
            instance ?: synchronized(this) {
                instance ?: SettingsSave(context.applicationContext).also { instance = it }
            }
    }

    private val settingsPath = context.filesDir.resolve("settings.json")
    private val gson = Gson()


    override var lastTabIndex by mutableIntStateOf(2)
    override var lastSongUri by mutableStateOf("")
    override var lastPositionMs by mutableLongStateOf(0L)
    override var lastDurationMs by mutableLongStateOf(0L)
    override var lastQueueUris by mutableStateOf<List<String>>(emptyList())


    fun save() {
        val data = SettingsData(
            lastTabIndex = lastTabIndex,
            lastSongUri = lastSongUri,
            lastPositionMs = lastPositionMs,
            lastDurationMs = lastDurationMs,
            lastQueueUris = lastQueueUris
        )
        val json = gson.toJson(data)
        Log.d("SettingsSave", "Saving: $json")
        settingsPath.writeText(json)
    }

    fun load() {
        if (!settingsPath.exists()) {
            Log.d("SettingsSave", "No settings file found")
            return
        }
        try {
            val text = settingsPath.readText()
            Log.d("SettingsSave", "Loading: $text")
            val type = object : TypeToken<SettingsData>() {}.type
            gson.fromJson<SettingsData>(text, type)?.let {
                lastTabIndex = it.lastTabIndex
                lastSongUri = it.lastSongUri ?: ""
                lastPositionMs = it.lastPositionMs
                lastDurationMs = it.lastDurationMs
                lastQueueUris = it.lastQueueUris
                Log.d("SettingsSave", "Loaded queue: ${it.lastQueueUris}")
            }
        } catch (e: Exception) {
            Log.e("SettingsSave", "Load failed", e)
        }
    }

    // ─── Data class ───────────────────────────────────────────────────────────

    data class SettingsData(
        val lastTabIndex: Int = 2,
        val lastSongUri: String? = null,
        val lastPositionMs: Long = 0L,
        val lastDurationMs: Long = 0L,
        val lastQueueUris: List<String> = emptyList()
    )

    init {
        load()
    }
}