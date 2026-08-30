package dev.pgaxis.musicaxs.settings_screens

import android.app.Application
import android.content.pm.PackageInfo
import android.os.Build
import androidx.lifecycle.AndroidViewModel

class AppInfoViewModel(application: Application) : AndroidViewModel(application) {
    val context = getApplication<Application>()

    val packageInfo: PackageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        packageInfo.longVersionCode
    } else {
        @Suppress("DEPRECATION")
        packageInfo.versionCode.toLong()
    }
}