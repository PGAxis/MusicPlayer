package dev.pgaxis.musicaxs.services

import android.app.ActivityManager
import android.content.Context
import androidx.annotation.Keep

fun isAppInForeground(context: Context): Boolean {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val appProcesses = am.runningAppProcesses ?: return false
    val packageName = context.packageName
    return appProcesses.any {
        it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                && it.processName == packageName
    }
}

@Keep
enum class Theme(private val displayName: String) {
    CYAN("theme_cyan"),
    GRAYSCALE("theme_gray"),
    EMBER("theme_ember"),
    AETHER("theme_aether"),
    PHOSPHOR("theme_phosphor"),
    CHALK("theme_chalk"),
    SOLEIL("theme_soleil");

    override fun toString(): String = displayName
}