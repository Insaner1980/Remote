package com.emmah.colordock.remote.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

enum class AppTheme(val label: String) {
    WarmLight("Warm light"), SoftDark("Soft dark");

    companion object {
        fun fromStored(value: String?): AppTheme = entries.firstOrNull { it.name == value } ?: SoftDark
    }
}

internal val OrchidWhite = Color(0xFFFFFCF2)
internal val ChromeWhite = Color(0xFFCCC5B9)
internal val Masala = Color(0xFF403D39)
internal val WarmBlack = Color(0xFF252422)
internal val Vermillion = Color(0xFFEB5E28)
private val LightSurface = Color(0xFFEFEAE0)

internal val WarmLightColors = lightColorScheme(
    // Darker orange keeps text links readable on cream; filled accents use vermillion.
    primary = Color(0xFFA63C12), onPrimary = OrchidWhite,
    primaryContainer = Vermillion, onPrimaryContainer = WarmBlack,
    secondary = Masala, onSecondary = OrchidWhite,
    secondaryContainer = Masala, onSecondaryContainer = OrchidWhite,
    tertiary = Masala, onTertiary = OrchidWhite,
    tertiaryContainer = ChromeWhite, onTertiaryContainer = WarmBlack,
    background = OrchidWhite, onBackground = WarmBlack,
    surface = LightSurface, onSurface = WarmBlack,
    surfaceVariant = ChromeWhite, onSurfaceVariant = Masala,
    surfaceTint = Color.Transparent,
    surfaceBright = OrchidWhite, surfaceDim = ChromeWhite,
    surfaceContainerLowest = OrchidWhite, surfaceContainerLow = OrchidWhite,
    surfaceContainer = LightSurface, surfaceContainerHigh = LightSurface,
    surfaceContainerHighest = ChromeWhite,
    outline = Color(0xFF79736A), outlineVariant = ChromeWhite,
    inverseSurface = WarmBlack, inverseOnSurface = OrchidWhite, inversePrimary = Vermillion,
    error = Color(0xFFB3261E), onError = Color.White,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    scrim = WarmBlack,
)

internal val SoftDarkColors = darkColorScheme(
    primary = Color(0xFFFF9B75), onPrimary = WarmBlack,
    primaryContainer = Vermillion, onPrimaryContainer = WarmBlack,
    secondary = ChromeWhite, onSecondary = WarmBlack,
    secondaryContainer = ChromeWhite, onSecondaryContainer = WarmBlack,
    tertiary = ChromeWhite, onTertiary = WarmBlack,
    tertiaryContainer = Masala, onTertiaryContainer = OrchidWhite,
    background = WarmBlack, onBackground = OrchidWhite,
    surface = Masala, onSurface = OrchidWhite,
    surfaceVariant = Masala, onSurfaceVariant = ChromeWhite,
    surfaceTint = Color.Transparent,
    surfaceBright = Masala, surfaceDim = WarmBlack,
    surfaceContainerLowest = WarmBlack, surfaceContainerLow = WarmBlack,
    surfaceContainer = Masala, surfaceContainerHigh = Masala,
    surfaceContainerHighest = Color(0xFF514D47),
    outline = Color(0xFF9B9488), outlineVariant = Color(0xFF5C5750),
    inverseSurface = OrchidWhite, inverseOnSurface = WarmBlack, inversePrimary = Color(0xFFA63C12),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    scrim = WarmBlack,
)

@Composable
fun RemoteTheme(theme: AppTheme = AppTheme.SoftDark, content: @Composable () -> Unit) {
    MaterialTheme(
        typography = Typography().let { it.copy(headlineSmall = it.headlineSmall.copy(fontWeight = FontWeight.SemiBold)) },
        colorScheme = if (theme == AppTheme.WarmLight) WarmLightColors else SoftDarkColors,
        content = content,
    )
}
