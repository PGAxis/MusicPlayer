package dev.pgaxis.musicaxs.side_pages

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.AndroidViewModel
import dev.pgaxis.musicaxs.R
import dev.pgaxis.musicaxs.services.MusicAxsContract
import dev.pgaxis.musicaxs.services.MusicService
import dev.pgaxis.musicaxs.services.OrbitService
import dev.pgaxis.musicaxs.services.Theme
import dev.pgaxis.musicaxs.settings.SettingsSave
import java.util.Locale

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val context = getApplication<Application>()
    val settings = SettingsSave.getInstance(context)

    val themeOptions = mapOf(
        Theme.CYAN to context.getString(R.string.set_vm_cyan),
        Theme.EMBER to context.getString(R.string.set_vm_ember),
        Theme.AETHER to context.getString(R.string.set_vm_aether),
        Theme.PHOSPHOR to context.getString(R.string.set_vm_phosphor),
        Theme.BORDO to context.getString(R.string.set_vm_bordo),
        Theme.VOID to context.getString(R.string.set_vm_void),
        Theme.CHALK to context.getString(R.string.set_vm_chalk),
        Theme.SUNSHINE to context.getString(R.string.set_vm_sunshine),
        Theme.GRAYSCALE to context.getString(R.string.set_vm_grayscale)
    )
    var selectedTheme by mutableStateOf(settings.theme)

    val langOptions = mapOf("en" to "English", "cs" to "Čeština")
    var selectedLang = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        .ifEmpty { Locale.getDefault().language.ifEmpty { langOptions.keys.first() } }!!

    fun onHideWhatsAppChanged(value: Boolean) {
        settings.hideWhatsAppAudio = value
    }

    fun onAllowYTCnvChanged(value: Boolean) {
        settings.allowYTCnv = value
    }

    fun onThemeChanged(key: Theme) {
        selectedTheme = key
        settings.theme = key
    }

    fun onLanguageChange(key: String) {
        selectedLang = key
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(key)
        )
    }

    fun onSmartLimitChanged(raw: String) {
        val clean = sanitizeCount(raw)
        settings.smartLimitInput = clean
        clean.toIntOrNull()?.let { settings.smartLimit = it }
    }

    fun onSmartLimitCleared() {
        settings.smartLimitInput = "50"
        settings.smartLimit = 50
    }

    fun onMaxArtChanged(raw: String) {
        val clean = sanitizeCount(raw)
        settings.maxArtItemsInput = clean
        clean.toIntOrNull()?.let { settings.maxArtItems = it }
    }

    fun onMaxArtCleared() {
        settings.maxArtItemsInput = "10"
        settings.maxArtItems = 10
    }

    fun onRealBarsChanged(value: Boolean) {
        MusicService.setRealBarsEnabled(value)
    }

    fun onPlayFullChanged(value: Boolean) {
        settings.playFull = value
    }

    private val orbitService = OrbitService.getInstance(context)

    var orbitDeviceName by mutableStateOf(orbitService.rawDeviceName)
        private set

    fun onOrbitDeviceNameChanged(value: String) {
        orbitService.deviceName = value
        orbitDeviceName = orbitService.rawDeviceName
    }

    @Suppress("DEPRECATION")
    fun isYTCnvInstalled(context: Context): Boolean {
        return try {
            val info = context.packageManager.getPackageInfo("com.pg_axis.ytcnv", 0)
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                info.longVersionCode else info.versionCode.toLong()
            versionCode >= MusicAxsContract.MIN_YTCNV_VERSION
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun sanitizeCount(raw: String, max: Int = Int.MAX_VALUE): String {
        if (raw.startsWith("-")) return "0"
        val digits = raw.filter { it in '0'..'9' }
        if (digits.isEmpty()) return if (raw.isEmpty()) "" else "0"
        val value = digits.toLongOrNull() ?: Long.MAX_VALUE
        return value.coerceAtMost(max.toLong()).toString()
    }
}