package dev.pgaxis.musicaxs.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

val MusicAxsCyanScheme = darkColorScheme(
    primary = CyanPrimary,
    onPrimary = TextWhitePrimary,
    primaryContainer = SurfaceVariantCyanDark,
    onPrimaryContainer = TextWhitePrimary,

    secondary = BlueSecondary,
    onSecondary = TextWhitePrimary,
    secondaryContainer = CardCyanDark,
    onSecondaryContainer = TextWhitePrimary,

    tertiary = BlueTertiary,
    onTertiary = TextWhitePrimary,
    tertiaryContainer = SurfaceVariantCyanDark,
    onTertiaryContainer = TextWhitePrimary,

    background = BackgroundCyanDark,
    onBackground = TextWhitePrimary,

    surface = SurfaceCyanDark,
    onSurface = TextWhitePrimary,
    surfaceVariant = SurfaceVariantCyanDark,
    onSurfaceVariant = TextCyanSecondary,

    outline = BorderCyanColor,
    outlineVariant = DividerCyanColor,

    error = PopupError,
    onError = TextWhitePrimary,
)

val MusicAxsGrayscaleScheme = darkColorScheme(
    primary = Gray300,
    onPrimary = TextWhitePrimary,
    primaryContainer = Gray900,
    onPrimaryContainer = TextWhitePrimary,

    secondary = Gray400,
    onSecondary = TextWhitePrimary,
    secondaryContainer = OffBlack,
    onSecondaryContainer = TextWhitePrimary,

    tertiary = Gray500,
    onTertiary = TextWhitePrimary,
    tertiaryContainer = Gray900,
    onTertiaryContainer = TextWhitePrimary,

    background = Gray950,
    onBackground = TextWhitePrimary,

    surface = OffBlack,
    onSurface = TextWhitePrimary,
    surfaceVariant = Gray900,
    onSurfaceVariant = TextCyanSecondary,

    outline = Gray200,
    outlineVariant = Gray600,

    error = PopupError,
    onError = TextWhitePrimary,
)

val MusicAxsEmberScheme = darkColorScheme(
    primary = EmberPrimary,
    onPrimary = TextWhitePrimary,
    primaryContainer = EmberSurfaceVariant,
    onPrimaryContainer = TextWhitePrimary,

    secondary = EmberSecondary,
    onSecondary = TextWhitePrimary,
    secondaryContainer = EmberCard,
    onSecondaryContainer = TextWhitePrimary,

    tertiary = EmberTertiary,
    onTertiary = TextWhitePrimary,
    tertiaryContainer = EmberSurfaceVariant,
    onTertiaryContainer = TextWhitePrimary,

    background = EmberBackground,
    onBackground = TextWhitePrimary,

    surface = EmberSurface,
    onSurface = TextWhitePrimary,
    surfaceVariant = EmberSurfaceVariant,
    onSurfaceVariant = TextEmberSecondary,

    outline = EmberBorder,
    outlineVariant = EmberDivider,

    error = PopupError,
    onError = TextWhitePrimary,
)

@Composable
fun MusicaxsTheme(colorScheme: ColorScheme, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}