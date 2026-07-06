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
import dev.pgaxis.musicaxs.models.deriveArtists
import dev.pgaxis.musicaxs.repositories.AlbumRepository
import dev.pgaxis.musicaxs.repositories.ArtistRepository
import dev.pgaxis.musicaxs.repositories.SongRepository
import dev.pgaxis.musicaxs.services.MusicAxsContract
import dev.pgaxis.musicaxs.services.Theme
import dev.pgaxis.musicaxs.settings.SettingsSave
import java.util.Locale

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val context = getApplication<Application>()
    val settings = SettingsSave.getInstance(context)

    val songRepo = SongRepository.getInstance()
    val albumRepo = AlbumRepository.getInstance()
    val artistRepo = ArtistRepository.getInstance()

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

    val predefinedSeparators = listOf(",", "&", "feat.")
    val customSeparators: List<String>
        get() = settings.artistSeparator.filter { it !in predefinedSeparators }

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

    fun onArtistSeparatorToggled(separator: String, enabled: Boolean) {
        val current = settings.artistSeparator.toMutableList()
        if (enabled) { if (separator !in current) current.add(separator) }
        else current.remove(separator)
        settings.artistSeparator = current
        rederiveArtists()
    }

    fun onCustomSeparatorAdded(raw: String) {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return
        if (trimmed in predefinedSeparators) {
            onArtistSeparatorToggled(trimmed, true)
            return
        }
        val current = settings.artistSeparator.toMutableList()
        if (trimmed !in current) current.add(trimmed)
        settings.artistSeparator = current
        rederiveArtists()
    }

    fun onCustomSeparatorRemoved(separator: String) {
        val current = settings.artistSeparator.toMutableList()
        current.remove(separator)
        settings.artistSeparator = current
        rederiveArtists()
    }

    fun onSmartLimitChanged(raw: String) {
        settings.smartLimitInput = raw
        raw.toIntOrNull()?.let { settings.smartLimit = it }
    }

    fun onSmartLimitCleared() {
        settings.smartLimitInput = "50"
        settings.smartLimit = 50
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

    fun rederiveArtists() {
        val songs = songRepo.songs.value
        val albums = albumRepo.albums.value
        artistRepo.update(deriveArtists(songs, albums, settings.artistSeparatorRegex))
    }
}