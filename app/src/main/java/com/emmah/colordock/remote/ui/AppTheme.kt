package com.emmah.colordock.remote.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emmah.colordock.remote.R

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

internal val WarmLightColors =
    lightColorScheme(
        // Darker orange keeps text links readable on cream; filled accents use vermillion.
        primary = Color(LIGHT_PRIMARY_ARGB),
        onPrimary = OrchidWhite,
        primaryContainer = Vermillion,
        onPrimaryContainer = WarmBlack,
        secondary = Masala,
        onSecondary = OrchidWhite,
        secondaryContainer = Color(LIGHT_SECONDARY_CONTAINER_ARGB),
        onSecondaryContainer = Color(LIGHT_ON_SECONDARY_CONTAINER_ARGB),
        tertiary = Masala,
        onTertiary = OrchidWhite,
        tertiaryContainer = ChromeWhite,
        onTertiaryContainer = WarmBlack,
        background = OrchidWhite,
        onBackground = WarmBlack,
        surface = OrchidWhite,
        onSurface = WarmBlack,
        surfaceVariant = Color(LIGHT_CONTAINER_HIGH_ARGB),
        onSurfaceVariant = Color(LIGHT_ON_SURFACE_VARIANT_ARGB),
        surfaceTint = Color.Transparent,
        surfaceBright = OrchidWhite,
        surfaceDim = Color(LIGHT_CONTAINER_HIGH_ARGB),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(LIGHT_CONTAINER_LOW_ARGB),
        surfaceContainer = Color(LIGHT_CONTAINER_ARGB),
        surfaceContainerHigh = Color(LIGHT_CONTAINER_HIGH_ARGB),
        surfaceContainerHighest = Color(LIGHT_CONTAINER_HIGHEST_ARGB),
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
        secondaryContainer = Color(DARK_SECONDARY_CONTAINER_ARGB),
        onSecondaryContainer = Color(DARK_ON_SECONDARY_CONTAINER_ARGB),
        tertiary = ChromeWhite,
        onTertiary = WarmBlack,
        tertiaryContainer = Masala,
        onTertiaryContainer = OrchidWhite,
        background = WarmBlack,
        onBackground = OrchidWhite,
        surface = WarmBlack,
        onSurface = OrchidWhite,
        surfaceVariant = Masala,
        onSurfaceVariant = ChromeWhite,
        surfaceTint = Color.Transparent,
        surfaceBright = Color(DARK_CONTAINER_HIGHEST_ARGB),
        surfaceDim = Color(DARK_CONTAINER_LOWEST_ARGB),
        surfaceContainerLowest = Color(DARK_CONTAINER_LOWEST_ARGB),
        surfaceContainerLow = Color(DARK_CONTAINER_LOW_ARGB),
        surfaceContainer = Color(DARK_CONTAINER_ARGB),
        surfaceContainerHigh = Masala,
        surfaceContainerHighest = Color(DARK_CONTAINER_HIGHEST_ARGB),
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
        typography = RemoteTypography,
        shapes = RemoteShapes,
        colorScheme = if (theme == AppTheme.WarmLight) WarmLightColors else SoftDarkColors,
        content = content,
    )
}

private const val ORCHID_WHITE_ARGB = 0xFFFFFCF2
private const val CHROME_WHITE_ARGB = 0xFFCCC5B9
private const val MASALA_ARGB = 0xFF403D39
private const val WARM_BLACK_ARGB = 0xFF252422
private const val VERMILLION_ARGB = 0xFFEB5E28
private const val LIGHT_PRIMARY_ARGB = 0xFFA63C12
private const val LIGHT_OUTLINE_ARGB = 0xFF79736A
private const val LIGHT_ERROR_ARGB = 0xFFB3261E
private const val LIGHT_ERROR_CONTAINER_ARGB = 0xFFFFDAD6
private const val LIGHT_ON_ERROR_CONTAINER_ARGB = 0xFF410002
private const val DARK_PRIMARY_ARGB = 0xFFFF9B75
private const val DARK_OUTLINE_ARGB = 0xFF9B9488
private const val DARK_OUTLINE_VARIANT_ARGB = 0xFF5C5750
private const val DARK_ERROR_ARGB = 0xFFFFB4AB
private const val DARK_ON_ERROR_ARGB = 0xFF690005
private const val DARK_ERROR_CONTAINER_ARGB = 0xFF93000A

private const val DARK_CONTAINER_LOWEST_ARGB = 0xFF201F1D
private const val DARK_CONTAINER_LOW_ARGB = 0xFF2D2B28
private const val DARK_CONTAINER_ARGB = 0xFF34322E
private const val DARK_CONTAINER_HIGHEST_ARGB = 0xFF4B4843
private const val DARK_SECONDARY_CONTAINER_ARGB = 0xFF55372A
private const val DARK_ON_SECONDARY_CONTAINER_ARGB = 0xFFFFDBCC
private const val LIGHT_CONTAINER_LOW_ARGB = 0xFFF8F3E9
private const val LIGHT_CONTAINER_ARGB = 0xFFF1ECE1
private const val LIGHT_CONTAINER_HIGH_ARGB = 0xFFE9E3D7
private const val LIGHT_CONTAINER_HIGHEST_ARGB = 0xFFDED7CB
private const val LIGHT_ON_SURFACE_VARIANT_ARGB = 0xFF5E5952
private const val LIGHT_SECONDARY_CONTAINER_ARGB = 0xFFFFDBCC
private const val LIGHT_ON_SECONDARY_CONTAINER_ARGB = 0xFF5C1F05

@OptIn(ExperimentalTextApi::class)
private fun flex(
    weight: Int,
    width: Float = NORMAL_FONT_WIDTH,
) = Font(
    R.font.roboto_flex,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.width(width)),
)

private const val NORMAL_FONT_WIDTH = 100f
private const val HEADLINE_FONT_WIDTH = 112f
private val RobotoFlex =
    FontFamily(flex(FontWeight.Normal.weight), flex(FontWeight.Medium.weight), flex(FontWeight.SemiBold.weight))
private val RobotoFlexWide =
    FontFamily(
        flex(FontWeight.Medium.weight, HEADLINE_FONT_WIDTH),
        flex(FontWeight.SemiBold.weight, HEADLINE_FONT_WIDTH),
    )
private val BaseTypography = Typography()
private val RemoteTypography =
    Typography(
        displayLarge = BaseTypography.displayLarge.copy(fontFamily = RobotoFlex),
        displayMedium = BaseTypography.displayMedium.copy(fontFamily = RobotoFlex),
        displaySmall = BaseTypography.displaySmall.copy(fontFamily = RobotoFlex),
        headlineLarge = BaseTypography.headlineLarge.copy(fontFamily = RobotoFlex),
        headlineMedium = BaseTypography.headlineMedium.copy(fontFamily = RobotoFlex),
        headlineSmall =
            BaseTypography.headlineSmall.copy(
                fontFamily = RobotoFlexWide,
                fontSize = 24.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.SemiBold,
            ),
        titleLarge =
            BaseTypography.titleLarge.copy(
                fontFamily = RobotoFlexWide,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.SemiBold,
            ),
        titleMedium =
            BaseTypography.titleMedium.copy(
                fontFamily = RobotoFlex,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Medium,
            ),
        titleSmall =
            BaseTypography.titleSmall.copy(
                fontFamily = RobotoFlex,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.SemiBold,
            ),
        bodyLarge =
            BaseTypography.bodyLarge.copy(
                fontFamily = RobotoFlex,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Normal,
            ),
        bodyMedium =
            BaseTypography.bodyMedium.copy(
                fontFamily = RobotoFlex,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Normal,
            ),
        bodySmall =
            BaseTypography.bodySmall.copy(
                fontFamily = RobotoFlex,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Normal,
            ),
        labelLarge =
            BaseTypography.labelLarge.copy(
                fontFamily = RobotoFlex,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
            ),
        labelMedium =
            BaseTypography.labelMedium.copy(
                fontFamily = RobotoFlex,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
            ),
        labelSmall =
            BaseTypography.labelSmall.copy(
                fontFamily = RobotoFlex,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
            ),
    )
private val RemoteShapes =
    Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(22.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )
