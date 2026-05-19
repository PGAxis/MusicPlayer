package dev.pgaxis.musicaxs.side_pages

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import dev.pgaxis.musicaxs.services.MusicAxsContract
import dev.pgaxis.musicaxs.services.Theme
import dev.pgaxis.musicaxs.settings.SettingsSave

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val context = getApplication<Application>()
    val settings = SettingsSave.getInstance(context)

    val themeOptions = mapOf(Theme.CYAN to "Cyan", Theme.EMBER to "Ember", Theme.AETHER to "Aether", Theme.PHOSPHOR to "Phosphor", Theme.CHALK to "Chalk", Theme.SOLEIL to "Soleil", Theme.GRAYSCALE to "Grayscale")
    var selectedTheme by mutableStateOf(settings.theme)

    fun onHideWhatsAppChanged(value: Boolean, onScan: () -> Unit) {
        settings.hideWhatsAppAudio = value
        onScan()
    }

    fun onAllowYTCnvChanged(value: Boolean) {
        settings.allowYTCnv = value
    }

    fun onThemeChanged(key: Theme) {
        selectedTheme = key
        settings.theme = key
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
}