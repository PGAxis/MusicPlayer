package com.pg_axis.musicaxs.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pg_axis.musicaxs.services.QueueSource

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

    // media playback persistency
    override var lastTabIndex by mutableIntStateOf(2)
    override var lastSongUri by mutableStateOf("")
    override var lastPositionMs by mutableLongStateOf(0L)
    override var lastDurationMs by mutableLongStateOf(0L)
    override var lastQueueUris by mutableStateOf<List<String>>(emptyList())
    override var lastQueueTitles by mutableStateOf<List<String>>(emptyList())
    override var lastQueueArtists by mutableStateOf<List<String>>(emptyList())
    override var repeatMode by mutableIntStateOf(2)
    override var queueSource by mutableStateOf(QueueSource.MANUAL)
    // settings
    override var hideWhatsAppAudio by mutableStateOf(false)

    fun save() {
        val data = SettingsData(
            lastTabIndex = lastTabIndex,
            lastSongUri = lastSongUri,
            lastPositionMs = lastPositionMs,
            lastDurationMs = lastDurationMs,
            lastQueueUris = lastQueueUris,
            lastQueueTitles = lastQueueTitles,
            lastQueueArtists = lastQueueArtists,
            repeatMode = repeatMode,
            queueSource = queueSource,
            hideWhatsAppAudio = hideWhatsAppAudio
        )
        val json = gson.toJson(data)
        settingsPath.writeText(json)
    }

    fun load() {
        if (!settingsPath.exists()) {
            return
        }
        try {
            val text = settingsPath.readText()
            val type = object : TypeToken<SettingsData>() {}.type
            gson.fromJson<SettingsData>(text, type)?.let {
                lastTabIndex = it.lastTabIndex
                lastSongUri = it.lastSongUri ?: ""
                lastPositionMs = it.lastPositionMs
                lastDurationMs = it.lastDurationMs
                lastQueueUris = it.lastQueueUris
                lastQueueTitles = it.lastQueueTitles
                lastQueueArtists = it.lastQueueArtists
                repeatMode = it.repeatMode
                queueSource = it.queueSource
                hideWhatsAppAudio = it.hideWhatsAppAudio
            }
        } catch (_: Exception) {
        }
    }

    // ─── Data class ───────────────────────────────────────────────────────────

    data class SettingsData(
        // media playback persistency
        val lastTabIndex: Int = 2,
        val lastSongUri: String? = null,
        val lastPositionMs: Long = 0L,
        val lastDurationMs: Long = 0L,
        val lastQueueUris: List<String> = emptyList(),
        val lastQueueTitles: List<String> = emptyList(),
        val lastQueueArtists: List<String> = emptyList(),
        val repeatMode: Int = 2,
        val queueSource: QueueSource = QueueSource.MANUAL,
        // settings
        val hideWhatsAppAudio: Boolean = false
    )

    init {
        load()
    }
}