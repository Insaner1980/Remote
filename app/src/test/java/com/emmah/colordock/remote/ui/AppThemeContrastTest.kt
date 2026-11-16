package com.emmah.colordock.remote.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

class AppThemeContrastTest {
    @Test fun normalTextRolesStayReadableOnTheirActualContainers() {
        listOf(WarmLightColors, SoftDarkColors).forEach { colors ->
            listOf(
                colors.onBackground to colors.background,
                colors.onSurface to colors.surfaceContainer,
                colors.onSurface to colors.surfaceContainerHigh,
                colors.onSurfaceVariant to colors.surfaceContainer,
                colors.onSurfaceVariant to colors.surfaceContainerHigh,
                colors.onPrimary to colors.primary,
                colors.onSecondaryContainer to colors.secondaryContainer,
                colors.primary to colors.background,
            ).forEach { (foreground, background) ->
                assertTrue(
                    "Unreadable normal text: $foreground on $background",
                    contrast(foreground, background) >= 4.5f,
                )
            }
        }
    }

    @Test fun directionPadLargeOkLabelHasLargeTextContrast() {
        listOf(WarmLightColors, SoftDarkColors).forEach { colors ->
            assertTrue(contrast(colors.onPrimaryContainer, colors.primaryContainer) >= 3f)
        }
    }

    @Test fun fiveContainerLevelsDoNotCollapseIntoTheSameVisibleSurface() {
        listOf(WarmLightColors, SoftDarkColors).forEach { colors ->
            val levels =
                listOf(
                    colors.surfaceContainerLowest,
                    colors.surfaceContainerLow,
                    colors.surfaceContainer,
                    colors.surfaceContainerHigh,
                    colors.surfaceContainerHighest,
                )
            assertEquals("Raised groups must keep five separate tones", 5, levels.distinct().size)
        }
    }

    private fun contrast(
        foreground: Color,
        background: Color,
    ): Float {
        val a = foreground.luminance()
        val b = background.luminance()
        return (max(a, b) + .05f) / (min(a, b) + .05f)
    }
}
