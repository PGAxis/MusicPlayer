package dev.pgaxis.musicaxs

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dev.pgaxis.musicaxs.services.MusicService
import dev.pgaxis.musicaxs.services.Theme
import dev.pgaxis.musicaxs.settings.SettingsSave
import dev.pgaxis.musicaxs.ui.theme.MusicAxsAetherScheme
import dev.pgaxis.musicaxs.ui.theme.MusicAxsChalkScheme
import dev.pgaxis.musicaxs.ui.theme.MusicAxsCyanScheme
import dev.pgaxis.musicaxs.ui.theme.MusicAxsEmberScheme
import dev.pgaxis.musicaxs.ui.theme.MusicAxsGrayscaleScheme
import dev.pgaxis.musicaxs.ui.theme.MusicAxsPhosphorScheme
import dev.pgaxis.musicaxs.ui.theme.MusicAxsSoleilScheme
import dev.pgaxis.musicaxs.ui.theme.MusicaxsTheme

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        enableEdgeToEdge()

        setContent {
            val settings = remember { SettingsSave.getInstance(this) }
            val colorScheme = when (settings.theme) {
                Theme.CYAN -> MusicAxsCyanScheme
                Theme.GRAYSCALE -> MusicAxsGrayscaleScheme
                Theme.EMBER -> MusicAxsEmberScheme
                Theme.AETHER -> MusicAxsAetherScheme
                Theme.PHOSPHOR -> MusicAxsPhosphorScheme
                Theme.CHALK -> MusicAxsChalkScheme
                Theme.SOLEIL -> MusicAxsSoleilScheme
            }
            MusicaxsTheme(colorScheme = colorScheme) {
                AppNavigation()
            }
        }
    }

    override fun onResume() {
        super.onResume()

        if (MusicService.playerInstance == null) {
            MusicService.initFromSettings(application)
            MusicService.initializeService(application)
        }
    }
}