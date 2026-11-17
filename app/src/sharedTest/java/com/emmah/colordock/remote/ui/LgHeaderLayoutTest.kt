package com.emmah.colordock.remote.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.emmah.colordock.remote.companion.DockConnectionState
import com.emmah.colordock.remote.companion.DockState
import com.emmah.colordock.remote.tv.TvConnectionState
import com.emmah.colordock.remote.tv.TvState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LgHeaderLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun selectorAndThemeControlAlignAcrossTabs() {
        showTabs()
        val baseline = headerBounds()
        listOf(RemoteTab.Wallpapers, RemoteTab.Dock).forEach { tab ->
            compose.onNodeWithText(tab.label).performClick()
            assertEquals(tab.label, baseline, headerBounds())
        }
    }

    @Suppress("LongMethod") // Exercise the three real tab entry points with the same selector.
    private fun showTabs() {
        compose.setContent {
            var tab by remember { mutableStateOf(RemoteTab.Remote) }
            val dock = DockState(DockConnectionState.Unavailable)
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(411.dp, 800.dp)) then
                    DeviceConfigurationOverride.FontScale(1.15f),
            ) {
                RemoteTheme {
                    RemoteAppShell(tab, { tab = it }) { modifier ->
                        val selector = @androidx.compose.runtime.Composable {
                            TargetSelector(ControlTargetState(selected = ControlTarget.LgTv), "LG TV", {}, {})
                        }
                        when (tab) {
                            RemoteTab.Remote -> {
                                RemoteScreen(
                                    TvState(TvConnectionState.Ready),
                                    emptyList(),
                                    {},
                                    {},
                                    {},
                                    {},
                                    {},
                                    {},
                                    modifier = modifier,
                                    targetSelector = selector,
                                )
                            }

                            RemoteTab.Wallpapers -> {
                                WallpaperGallery(
                                    dock,
                                    WallpaperUiState(),
                                    emptyMap(),
                                    "all",
                                    {},
                                    false,
                                    {},
                                    {},
                                    {},
                                    {},
                                    {},
                                    {},
                                    {},
                                    {},
                                    {},
                                    {},
                                    {},
                                    modifier = modifier,
                                    targetSelector = selector,
                                )
                            }

                            RemoteTab.Dock -> {
                                DockScreen(
                                    dock,
                                    DockUiState(),
                                    null,
                                    {},
                                    {},
                                    {},
                                    {},
                                    { _, _, _ -> },
                                    {},
                                    {},
                                    {},
                                    modifier = modifier,
                                    targetSelector = selector,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun headerBounds(): Triple<Float, Float, Float> {
        val selector = compose.onNodeWithTag("target-selector").getUnclippedBoundsInRoot()
        val settings = compose.onNodeWithContentDescription("Settings").getUnclippedBoundsInRoot()
        return Triple(
            selector.top.value,
            (selector.bottom - selector.top).value,
            ((settings.top + settings.bottom) / 2).value,
        )
    }
}
