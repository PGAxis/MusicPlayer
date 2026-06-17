package dev.pgaxis.musicaxs.settings

import android.content.Context
import android.util.Log
import androidx.annotation.Keep
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.pgaxis.axs.AxsBoundObject
import dev.pgaxis.axs.AxsFile
import dev.pgaxis.musicaxs.models.DEFAULT_TABS
import dev.pgaxis.musicaxs.models.TitleVis
import dev.pgaxis.musicaxs.services.QueueSource
import dev.pgaxis.musicaxs.services.Theme
import java.io.File
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty

class SettingsSave private constructor(context: Context): ISettings {

    companion object {
        @Volatile
        private var instance: SettingsSave? = null

        fun getInstance(context: Context): SettingsSave =
            instance ?: synchronized(this) {
                instance ?: SettingsSave(context.applicationContext).also { instance = it }
            }
    }

    private val axsPath = context.filesDir.resolve("settings.axs").path
    private var isInitializing = true

    // --- AXS setup ---
    private val axsFile = AxsFile(axsPath)
    private lateinit var boundSettings: AxsBoundObject<SettingsData>

    // --- Setting fun ---
    private fun <V : Any> setting(
        initial: V,
        prop: KMutableProperty1<SettingsData, V>
    ): ReadWriteProperty<Any?, V> = object : ReadWriteProperty<Any?, V> {
        private var state by mutableStateOf(initial)

        override fun getValue(thisRef: Any?, property: KProperty<*>): V = state

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: V) {
            state = value
            if (::boundSettings.isInitialized && !isInitializing) boundSettings.setValue(prop, value)
        }
    }

    private fun intSetting(
        initial: Int,
        prop: KMutableProperty1<SettingsData, Int>
    ): ReadWriteProperty<Any?, Int> = object : ReadWriteProperty<Any?, Int> {
        private var state by mutableIntStateOf(initial)

        override fun getValue(thisRef: Any?, property: KProperty<*>): Int = state

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
            state = value
            if (::boundSettings.isInitialized && !isInitializing) boundSettings.setValue(prop, value)
        }
    }

    private fun longSetting(
        initial: Long,
        prop: KMutableProperty1<SettingsData, Long>
    ): ReadWriteProperty<Any?, Long> = object : ReadWriteProperty<Any?, Long> {
        private var state by mutableLongStateOf(initial)

        override fun getValue(thisRef: Any?, property: KProperty<*>): Long = state

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Long) {
            state = value
            if (::boundSettings.isInitialized && !isInitializing) boundSettings.setValue(prop, value)
        }
    }

    // media playback persistency
    override var lastTabIndex by intSetting(2, SettingsData::lastTabIndex)
    override var lastSongUri by setting("", SettingsData::lastSongUri)
    override var lastPositionMs by longSetting(0L, SettingsData::lastPositionMs)
    override var lastDurationMs by longSetting(0L, SettingsData::lastDurationMs)
    override var lastPlaylistId by longSetting(-1L, SettingsData::lastPlaylistId) // TODO replace with null once I add null support to AXS
    override var lastQueue by setting(emptyList(), SettingsData::lastQueue)
    override var lastQueueIndex by intSetting(-1, SettingsData::lastQueueIndex)
    override var repeatMode by intSetting(2, SettingsData::repeatMode)
    override var queueSource by setting(QueueSource.MANUAL, SettingsData::queueSource)
    override var podcastFeedUrls by setting(emptyList(), SettingsData::podcastFeedUrls)
    // settings
    override var hideWhatsAppAudio by setting(false, SettingsData::hideWhatsAppAudio)
    override var allowYTCnv by setting(false, SettingsData::allowYTCnv)
    override var theme by setting(Theme.CYAN, SettingsData::theme)
    override var tabs by setting(DEFAULT_TABS, SettingsData::tabs)
    override var artistSeparator by setting(arrayOf(",").toList(), SettingsData::artistSeparator)

    // -- Data class
    @Keep
    data class SettingsData(
        // media playback persistency
        var lastTabIndex: Int = 2,
        var lastSongUri: String = "",
        var lastPositionMs: Long = 0L,
        var lastDurationMs: Long = 0L,
        var lastPlaylistId: Long = -1L,
        var lastQueue: List<QueueEntry> = emptyList(),
        var lastQueueIndex: Int = -1,
        var repeatMode: Int = 2,
        var queueSource: QueueSource = QueueSource.MANUAL,
        var podcastFeedUrls: List<String> = emptyList(),
        // settings
        var hideWhatsAppAudio: Boolean = false,
        var allowYTCnv: Boolean = false,
        var theme: Theme = Theme.CYAN,
        var tabs: List<TitleVis> = DEFAULT_TABS,
        var artistSeparator: List<String> = arrayOf(",").toList(),
    )

    @Keep
    data class QueueEntry(
        val uri: String = "",
        val title: String = "",
        val artist: String = ""
    )

    fun flush() {
        if (::boundSettings.isInitialized) boundSettings.flush()
    }

    val artistSeparatorRegex: Regex? get() {
        val seps = artistSeparator
        return if (seps.isEmpty()) null else seps.joinToString("|") { Regex.escape(it) }.toRegex()
    }

    init {
        axsFile.setLogging(on = false)
        axsFile.open()

        try {
            Log.d("SettingsSaveDebug", "\nAbout to bind file")
            boundSettings = axsFile.bind(SettingsData())
            Log.d("SettingsSaveDebug", "File successfully bound\n")

            val s = boundSettings.get()
            Log.d("SettingsSaveDebug", "$s")

            lastTabIndex = s.lastTabIndex
            lastSongUri = s.lastSongUri
            lastPositionMs = s.lastPositionMs
            lastDurationMs = s.lastDurationMs
            lastPlaylistId = s.lastPlaylistId
            lastQueue = s.lastQueue
            lastQueueIndex = s.lastQueueIndex
            repeatMode = s.repeatMode
            queueSource = s.queueSource
            podcastFeedUrls = s.podcastFeedUrls
            hideWhatsAppAudio = s.hideWhatsAppAudio
            allowYTCnv = s.allowYTCnv
            theme = s.theme
            tabs = s.tabs
            artistSeparator = s.artistSeparator

            val missingTabs = DEFAULT_TABS.filter { default ->
                tabs.none { it.tab == default.tab }
            }
            if (missingTabs.isNotEmpty()) {
                tabs = tabs + missingTabs
            }

            isInitializing = false
        } catch (e: Exception) {
            Log.d("SettingsSaveError", e.toString())
            Log.d("SettingsSaveError", e.stackTraceToString())
            axsFile.close()
            File(axsPath).delete()
            axsFile.open()
            boundSettings = axsFile.bind(SettingsData())
        } finally {
            isInitializing = false // always runs, both paths
        }
    }
}