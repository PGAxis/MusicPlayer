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