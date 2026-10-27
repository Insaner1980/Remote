package com.emmah.colordock.remote.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

enum class AppTheme(
    val label: String,
) {
    WarmLight("Warm light"),
    SoftDark("Soft dark"),
    ;

    companion object {
        fun fromStored(value: String?): AppTheme =
            entries.firstOrNull {
                it.name == value
            } ?: SoftDark
    }
}

internal val OrchidWhite = Color(ORCHID_WHITE_ARGB)
internal val ChromeWhite = Color(CHROME_WHITE_ARGB)
internal val Masala = Color(MASALA_ARGB)
internal val WarmBlack = Color(WARM_BLACK_ARGB)
internal val Vermillion = Color(VERMILLION_ARGB)
private val LightSurface = Color(LIGHT_SURFACE_ARGB)

internal val WarmLightColors =
    lightColorScheme(
        // Darker orange keeps text links readable on cream; filled accents use vermillion.
        primary = Color(LIGHT_PRIMARY_ARGB),
        onPrimary = OrchidWhite,
        primaryContainer = Vermillion,
        onPrimaryContainer = WarmBlack,
        secondary = Masala,
        onSecondary = OrchidWhite,
        secondaryContainer = Masala,
        onSecondaryContainer = OrchidWhite,
        tertiary = Masala,
        onTertiary = OrchidWhite,
        tertiaryContainer = ChromeWhite,
        onTertiaryContainer = WarmBlack,
        background = OrchidWhite,
        onBackground = WarmBlack,
        surface = LightSurface,
        onSurface = WarmBlack,
        surfaceVariant = ChromeWhite,
        onSurfaceVariant = Masala,
        surfaceTint = Color.Transparent,
        surfaceBright = OrchidWhite,
        surfaceDim = ChromeWhite,
        surfaceContainerLowest = OrchidWhite,
        surfaceContainerLow = OrchidWhite,
        surfaceContainer = LightSurface,
        surfaceContainerHigh = LightSurface,
        surfaceContainerHighest = ChromeWhite,
        outline = Color(LIGHT_OUTLINE_ARGB),
        outlineVariant = ChromeWhite,
        inverseSurface = WarmBlack,
        inverseOnSurface = OrchidWhite,
        inversePrimary = Vermillion,
        error = Color(LIGHT_ERROR_ARGB),
        onError = Color.White,
        errorContainer = Color(LIGHT_ERROR_CONTAINER_ARGB),
        onErrorContainer = Color(LIGHT_ON_ERROR_CONTAINER_ARGB),
        scrim = WarmBlack,
    )

internal val SoftDarkColors =
    darkColorScheme(
        primary = Color(DARK_PRIMARY_ARGB),
        onPrimary = WarmBlack,
        primaryContainer = Vermillion,
        onPrimaryContainer = WarmBlack,
        secondary = ChromeWhite,
        onSecondary = WarmBlack,
        secondaryContainer = ChromeWhite,
        onSecondaryContainer = WarmBlack,
        tertiary = ChromeWhite,
        onTertiary = WarmBlack,
        tertiaryContainer = Masala,
        onTertiaryContainer = OrchidWhite,
        background = WarmBlack,
        onBackground = OrchidWhite,
        surface = Masala,
        onSurface = OrchidWhite,
        surfaceVariant = Masala,
        onSurfaceVariant = ChromeWhite,
        surfaceTint = Color.Transparent,
        surfaceBright = Masala,
        surfaceDim = WarmBlack,
        surfaceContainerLowest = WarmBlack,
        surfaceContainerLow = WarmBlack,
        surfaceContainer = Masala,
        surfaceContainerHigh = Masala,
        surfaceContainerHighest = Color(DARK_SURFACE_HIGHEST_ARGB),
        outline = Color(DARK_OUTLINE_ARGB),
        outlineVariant = Color(DARK_OUTLINE_VARIANT_ARGB),
        inverseSurface = OrchidWhite,
        inverseOnSurface = WarmBlack,
        inversePrimary = Color(LIGHT_PRIMARY_ARGB),
        error = Color(DARK_ERROR_ARGB),
        onError = Color(DARK_ON_ERROR_ARGB),
        errorContainer = Color(DARK_ERROR_CONTAINER_ARGB),
        onErrorContainer = Color(LIGHT_ERROR_CONTAINER_ARGB),
        scrim = WarmBlack,
    )

@Composable
fun RemoteTheme(
    theme: AppTheme = AppTheme.SoftDark,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        typography =
            Typography().let {
                it.copy(headlineSmall = it.headlineSmall.copy(fontWeight = FontWeight.SemiBold))
            },
        colorScheme = if (theme == AppTheme.WarmLight) WarmLightColors else SoftDarkColors,
        content = content,
    )
}

private const val ORCHID_WHITE_ARGB = 0xFFFFFCF2
private const val CHROME_WHITE_ARGB = 0xFFCCC5B9
private const val MASALA_ARGB = 0xFF403D39
private const val WARM_BLACK_ARGB = 0xFF252422
private const val VERMILLION_ARGB = 0xFFEB5E28
private const val LIGHT_SURFACE_ARGB = 0xFFEFEAE0
private const val LIGHT_PRIMARY_ARGB = 0xFFA63C12
private const val LIGHT_OUTLINE_ARGB = 0xFF79736A
private const val LIGHT_ERROR_ARGB = 0xFFB3261E
private const val LIGHT_ERROR_CONTAINER_ARGB = 0xFFFFDAD6
private const val LIGHT_ON_ERROR_CONTAINER_ARGB = 0xFF410002
private const val DARK_PRIMARY_ARGB = 0xFFFF9B75
private const val DARK_SURFACE_HIGHEST_ARGB = 0xFF514D47
private const val DARK_OUTLINE_ARGB = 0xFF9B9488
private const val DARK_OUTLINE_VARIANT_ARGB = 0xFF5C5750
private const val DARK_ERROR_ARGB = 0xFFFFB4AB
private const val DARK_ON_ERROR_ARGB = 0xFF690005
private const val DARK_ERROR_CONTAINER_ARGB = 0xFF93000A
