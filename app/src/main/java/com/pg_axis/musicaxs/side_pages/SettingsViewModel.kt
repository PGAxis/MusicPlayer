package com.pg_axis.musicaxs.side_pages

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import com.pg_axis.musicaxs.services.MusicAxsContract
import com.pg_axis.musicaxs.settings.SettingsSave

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val context = getApplication<Application>()
    val settings = SettingsSave.getInstance(context)

    fun onHideWhatsAppChanged(value: Boolean, onScan: () -> Unit) {
        settings.hideWhatsAppAudio = value
        settings.save()
        onScan()
    }

    fun onAllowYTCnvChanged(value: Boolean) {
        settings.allowYTCnv = value
        settings.save()
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