package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.emmah.colordock.remote.mediacenter.protocol.McRequest
import com.emmah.colordock.remote.ui.ControlTarget
import com.emmah.colordock.remote.ui.ControlTargetState
import com.emmah.colordock.remote.ui.RemoteTheme
import com.emmah.colordock.remote.ui.TargetSelector
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SimpleSettingsNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun overviewHasSixSectionsAndEachOpensItsOwnPage() {
        showSettings()
        listOf("Sound", "VPN", "Media", "Devices", "Appearance", "Advanced").forEach { label ->
            compose
                .onNodeWithTag("settings-section-$label")
                .performScrollTo()
                .assertIsDisplayed()
                .performClick()
            compose
                .onNodeWithContentDescription(
                    "Back to settings",
                ).performScrollTo()
                .assertIsDisplayed()
                .performClick()
        }
        compose.onNodeWithText("nordvpn --version").assertDoesNotExist()
        compose.onNodeWithText("Start after computer login").assertDoesNotExist()
        compose.onNodeWithText("Require VPN before opening").assertDoesNotExist()
    }

    @Test fun deviceSelectorIsCompactAndSettingsNavigationLivesAtBottom() {
        showSettings()
        compose.onNodeWithText("Change device", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("mediacenter-bottom-navigation").assertIsDisplayed()
        compose.onNodeWithTag("media-nav-Settings").assertIsSelected()
        compose.onNodeWithTag("media-nav-Remote").performClick().assertIsSelected()
    }

    @Test fun appearanceAndDevicesKeepExistingPhoneActions() {
        var appearance = false
        var connection = false
        showSettings({ appearance = true }, { connection = true })
        compose.onNodeWithTag("settings-section-Appearance").performScrollTo().performClick()
        compose.onNodeWithText("Phone appearance").performClick()
        compose.runOnIdle { assertTrue(appearance) }
        compose.onNodeWithContentDescription("Back to settings").performClick()
        compose.onNodeWithTag("settings-section-Devices").performScrollTo().performClick()
        compose.onNodeWithText("Mediacenter connection").performClick()
        compose.runOnIdle { assertTrue(connection) }
    }

    @Test fun offlineOverviewAndBackRemainUsableAtDoubleTextSize() {
        showSettings(offline = true, largeText = true)
        compose.onNodeWithTag("settings-section-Advanced").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Back to settings").performScrollTo().performClick()
        compose.onNodeWithTag("settings-section-VPN").performScrollTo().performClick()
        compose.onNodeWithText("Connect VPN…").performScrollTo().assertIsNotEnabled()
        compose
            .onNodeWithTag("media-nav-Remote")
            .assertIsDisplayed()
            .performClick()
            .assertIsSelected()
    }

    private fun showSettings(
        appearance: () -> Unit = {},
        connection: () -> Unit = {},
        offline: Boolean = false,
        largeText: Boolean = false,
    ) {
        compose.setContent {
            val scope = rememberCoroutineScope()
            val send: (McRequest) -> Boolean = { true }
            val controllers =
                remember {
                    MediaTargetControllers(
                        MediaSessionController(scope, send).apply {
                            observe(phoneMediaSnapshot().takeUnless { offline })
                            destination(MediaDestination.Settings)
                        },
                        LibraryViewModel(scope, send),
                        RendererController(scope, send),
                        AudioSettingsController(send),
                    )
                }
            val state by controllers.media.state.collectAsState()
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(360.dp, 640.dp)) then
                    DeviceConfigurationOverride.FontScale(if (largeText) 2f else 1f),
            ) {
                RemoteTheme {
                    MediacenterTargetContent(
                        MediaTargetState(state, LibraryState(), AudioSettingsState()),
                        controllers,
                        { null },
                        header = {
                            TargetSelector(ControlTargetState(selected = ControlTarget.Mediacenter), "TV", {}, {})
                        },
                        settings = { section ->
                            if (section ==
                                SettingsSection.Devices
                            ) {
                                androidx.compose.material3.TextButton(onClick = connection) {
                                    androidx.compose.material3.Text("Mediacenter connection")
                                }
                            }
                            if (section ==
                                SettingsSection.Appearance
                            ) {
                                androidx.compose.material3.TextButton(onClick = appearance) {
                                    androidx.compose.material3.Text("Phone appearance")
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}
