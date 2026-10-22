package com.emmah.colordock.remote.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeTest {
    @Test fun storedChoicesRoundTripAndMissingOrUnknownValuesKeepDarkDefault() {
        AppTheme.entries.forEach { assertEquals(it, AppTheme.fromStored(it.name)) }
        assertEquals(AppTheme.SoftDark, AppTheme.fromStored(null))
        assertEquals(AppTheme.SoftDark, AppTheme.fromStored("unknown"))
    }

    @Test fun textAndFilledAccentPairsHaveReadableContrastInBothThemes() {
        listOf(WarmLightColors, SoftDarkColors).forEach { colors ->
            listOf(
                colors.onBackground to colors.background,
                colors.onSurface to colors.surface,
                colors.onSurfaceVariant to colors.surface,
                colors.primary to colors.background,
                colors.primary to colors.surface,
                colors.onPrimary to colors.primary,
                colors.onPrimaryContainer to colors.primaryContainer,
                colors.onSecondaryContainer to colors.secondaryContainer,
                colors.error to colors.surface,
            ).forEach { (foreground, background) ->
                assertTrue("Low contrast for $foreground on $background", contrast(foreground, background) >= 4.5f)
            }
        }
    }

    private fun contrast(first: Color, second: Color): Float {
        val a = first.luminance()
        val b = second.luminance()
        return (maxOf(a, b) + .05f) / (minOf(a, b) + .05f)
    }
}
