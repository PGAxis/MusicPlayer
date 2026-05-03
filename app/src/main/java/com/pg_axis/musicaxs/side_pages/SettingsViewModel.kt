package com.pg_axis.musicaxs.side_pages

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.pg_axis.musicaxs.settings.SettingsSave

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val context = getApplication<Application>()
    val settings = SettingsSave.getInstance(context)

    fun onHideWhatsAppChanged(value: Boolean, onScan: () -> Unit) {
        settings.hideWhatsAppAudio = value
        settings.save()
        onScan()
    }
}