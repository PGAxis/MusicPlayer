package dev.pgaxis.musicaxs.settings_screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import dev.pgaxis.musicaxs.models.deriveArtists
import dev.pgaxis.musicaxs.repositories.AlbumRepository
import dev.pgaxis.musicaxs.repositories.ArtistRepository
import dev.pgaxis.musicaxs.repositories.SongRepository
import dev.pgaxis.musicaxs.settings.SettingsSave

class ArtistSeparatorsViewModel(application: Application) : AndroidViewModel(application) {
    val context = getApplication<Application>()
    val settings = SettingsSave.getInstance(context)

    val songRepo = SongRepository.getInstance()
    val albumRepo = AlbumRepository.getInstance()
    val artistRepo = ArtistRepository.getInstance()

    val predefinedSeparators = listOf(",", "&", "feat.")
    val customSeparators: List<String>
        get() = settings.artistSeparator.filter { it !in predefinedSeparators }

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

    fun rederiveArtists() {
        val songs = songRepo.songs.value
        val albums = albumRepo.albums.value
        artistRepo.update(deriveArtists(songs, albums, settings.artistSeparatorRegex))
    }
}