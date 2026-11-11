package com.emmah.colordock.remote.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.emmah.colordock.remote.companion.DockState
import com.emmah.colordock.remote.tv.TvApp
import com.emmah.colordock.remote.tv.TvConnectionState
import com.emmah.colordock.remote.tv.TvState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExistingTvRegressionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun selectorRetainsTvShortcutAndBothCompanionDestinationsInTheirHeaders() {
        var opened = ""
        var companionOpens = 0
        compose.setContent { TvFixture({ opened = it }, { companionOpens++ }) }
        compose.onNodeWithTag("app-shortcuts").performScrollTo()
        compose
            .onNodeWithContentDescription(
                "Open TV shortcut on TV",
            ).performScrollTo()
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("fixture-app", opened) }
        compose.onNodeWithText("Wallpapers").performClick()
        compose.onAllNodesWithTag("target-selector").assertCountEquals(1)
        compose.onNodeWithText("Open Color Dock").performScrollTo().performClick()
        compose.onNodeWithText("Color Dock").performClick()
        compose.onNodeWithTag("target-selector").assertIsDisplayed()
        compose.onNodeWithText("Open Color Dock").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(2, companionOpens) }
    }

    @Composable
    private fun TvFixture(
        onApp: (String) -> Unit,
        onCompanion: () -> Unit,
    ) {
        var tab by remember { mutableStateOf(RemoteTab.Remote) }
        val selector: @Composable () -> Unit = {
            TargetSelector(ControlTargetState(tvReady = true), "Living room", {}, {})
        }
        RemoteTheme {
            TargetAppShell(ControlTargetState(tvReady = true), tab, { tab = it }, mediacenter = {}) { modifier ->
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
                            modifier,
                            apps =
                                TvAppsUiState(
                                    apps = listOf(TvApp("fixture-app", "TV shortcut", null)),
                                    connected = true,
                                    hasLoaded = true,
                                ),
                            onOpenApp = onApp,
                            targetSelector = selector,
                        )
                    }

                    RemoteTab.Wallpapers -> {
                        Wallpapers(selector, modifier, onCompanion)
                    }

                    RemoteTab.Dock -> {
                        DockScreen(
                            DockState(),
                            DockUiState(),
                            null,
                            onCompanion,
                            {},
                            {},
                            {},
                            { _, _, _ -> },
                            {},
                            {},
                            {},
                            modifier,
                            targetSelector = selector,
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun Wallpapers(
        selector: @Composable () -> Unit,
        modifier: Modifier = Modifier,
        onOpen: () -> Unit,
    ) {
        WallpaperScreen(
            DockState(),
            WallpaperUiState(),
            emptyMap(),
            onOpen,
            {},
            {},
            {},
            {},
            {},
            {},
            {},
            { _, _, _ -> },
            {},
            { _, _ -> },
            {},
            {},
            {},
            modifier,
            targetSelector = selector,
        )
    }
}
