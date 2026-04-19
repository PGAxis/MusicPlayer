package com.pg_axis.musicaxs.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val MusicAxsColorScheme = darkColorScheme(
    primary = CyanPrimary,
    onPrimary = TextOnButton,
    primaryContainer = SurfaceVariantDark,
    onPrimaryContainer = TextPrimary,

    secondary = BlueSecondary,
    onSecondary = TextPrimary,
    secondaryContainer = CardDark,
    onSecondaryContainer = TextPrimary,

    tertiary = BlueTertiary,
    onTertiary = TextPrimary,
    tertiaryContainer = SurfaceVariantDark,
    onTertiaryContainer = TextPrimary,

    background = BackgroundDark,
    onBackground = TextPrimary,

    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondary,

    outline = BorderColor,
    outlineVariant = DividerColor,

    error = PopupError,
    onError = TextPrimary,
)

@Composable
fun MusicaxsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MusicAxsColorScheme,
        typography = Typography,
        content = content
    )
}