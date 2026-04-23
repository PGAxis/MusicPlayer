package com.pg_axis.musicaxs.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.Gson

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


    fun save() {
        val data = SettingsData(
            lastTabIndex = lastTabIndex,
            lastSongUri = lastSongUri,
            lastPositionMs = lastPositionMs
        )
        settingsPath.writeText(gson.toJson(data))
    }

    fun load() {
        if (!settingsPath.exists()) return
        try {
            gson.fromJson(settingsPath.readText(), SettingsData::class.java)?.let {
                lastTabIndex   = it.lastTabIndex
                lastSongUri    = it.lastSongUri ?: ""
                lastPositionMs = it.lastPositionMs
            }
        } catch (_: Exception) {
            // Corrupted file
        }
    }

    // ─── Data class ───────────────────────────────────────────────────────────

    data class SettingsData(
        val lastTabIndex: Int = 2,
        val lastSongUri: String? = null,
        val lastPositionMs: Long = 0L
    )

    init {
        load()
    }
}