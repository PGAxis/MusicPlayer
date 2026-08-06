package dev.pgaxis.musicaxs.settings_screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import dev.pgaxis.musicaxs.settings.SettingsSave

class TabOrderViewModel(application: Application) : AndroidViewModel(application) {
    val context = getApplication<Application>()

    val settings = SettingsSave.getInstance(context)
}