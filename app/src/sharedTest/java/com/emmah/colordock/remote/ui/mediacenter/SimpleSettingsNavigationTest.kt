package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.emmah.colordock.remote.mediacenter.protocol.McRequest
import com.emmah.colordock.remote.ui.ControlTarget
import com.emmah.colordock.remote.ui.ControlTargetState
import com.emmah.colordock.remote.ui.RemoteTheme
import com.emmah.colordock.remote.ui.TargetSelector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
                ).assertIsDisplayed()
                .performClick()
        }
        compose.onNodeWithText("nordvpn --version").assertDoesNotExist()
        compose.onNodeWithText("Start after computer login").assertDoesNotExist()
        compose.onNodeWithText("Require VPN before opening").assertDoesNotExist()
    }

    @Test fun overviewShowsAvailableSummaryWithoutSeparatorsOrPlaceholderText() {
        showSettings()
        compose.onNodeWithText("1 folder", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Unavailable").assertDoesNotExist()
        val texts =
            compose
                .onAllNodes(
                    SemanticsMatcher.keyIsDefined(SemanticsProperties.Text),
                    useUnmergedTree = true,
                ).fetchSemanticsNodes()
                .flatMap { it.config[SemanticsProperties.Text] }
        assertTrue(texts.none { it.text.contains('·') || it.text.contains('•') })
    }

    @Test fun deviceSelectorIsCompactAndSettingsNavigationLivesAtBottom() {
        showSettings()
        compose.onNodeWithText("Change device", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("mediacenter-bottom-navigation").assertIsDisplayed()
        compose.onNodeWithTag("media-nav-Settings").assertIsSelected()
        compose.onNodeWithTag("media-nav-Remote").performClick().assertIsSelected()
    }

    @Test fun subpageHidesSelectorButKeepsBackAndBottomNavigation() {
        showSettings()
        compose.onNodeWithTag("target-selector").assertIsDisplayed()
        compose.onNodeWithTag("settings-section-Sound").performClick()
        compose.onNodeWithTag("target-selector").assertDoesNotExist()
        compose.onNodeWithContentDescription("Back to settings").assertIsDisplayed().performClick()
        compose.onNodeWithTag("target-selector").assertIsDisplayed()
        compose.onNodeWithTag("mediacenter-bottom-navigation").assertIsDisplayed()
    }

    @Test fun headingSurfaceChangesAtTheScrollEdgeAndRestoresAtTheTop() {
        showSettings(windowSize = DpSize(360.dp, 420.dp))
        val heading = compose.onNodeWithTag("mediacenter-page-heading")
        val scroll = compose.onNode(hasScrollAction() and hasAnyDescendant(hasTestTag("settings-section-Sound")))
        val topColor = heading.captureToImage().toPixelMap()[0, 0]

        scroll.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 60f) }
        val scrolledColor = heading.captureToImage().toPixelMap()[0, 0]
        assertNotEquals(topColor, scrolledColor)

        scroll.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 60f) }
        assertEquals(scrolledColor, heading.captureToImage().toPixelMap()[0, 0])

        scroll.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, -1000f) }
        assertEquals(topColor, heading.captureToImage().toPixelMap()[0, 0])
    }

    @Test fun pageRefreshActionsBelongToHeading() {
        showSettings()
        compose.onNodeWithTag("settings-section-Sound").performClick()
        compose
            .onNode(
                hasContentDescription("Refresh sound outputs") and
                    hasAnyAncestor(hasTestTag("mediacenter-page-heading")),
            ).assertIsDisplayed()
        compose.onNodeWithContentDescription("Back to settings").performClick()
        compose.onNodeWithTag("settings-section-Advanced").performScrollTo().performClick()
        compose
            .onNode(
                hasContentDescription("Refresh") and
                    hasAnyAncestor(hasTestTag("mediacenter-page-heading")),
            ).assertIsDisplayed()
        compose.onNodeWithTag("media-nav-Library").performClick()
        compose
            .onNode(
                hasContentDescription("Refresh library") and
                    hasAnyAncestor(hasTestTag("mediacenter-page-heading")),
            ).assertIsDisplayed()
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
        showSettings(offline = true, fontScale = 2f)
        compose.onNodeWithTag("settings-section-Advanced").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Back to settings").performClick()
        compose.onNodeWithTag("settings-section-VPN").performScrollTo().performClick()
        compose.onNodeWithText("Connect").performScrollTo().assertIsNotEnabled()
        compose
            .onNodeWithTag("media-nav-Remote")
            .assertIsDisplayed()
            .performClick()
            .assertIsSelected()
    }

    @Test fun sixSettingsRowsFitAt140PercentInANarrowWindow() {
        showSettings(windowSize = DpSize(288.dp, 900.dp), fontScale = 1.4f)
        val navigationTop =
            compose
                .onNodeWithTag("mediacenter-bottom-navigation")
                .getUnclippedBoundsInRoot()
                .top
        listOf("Sound", "VPN", "Media", "Devices", "Appearance", "Advanced").forEach { label ->
            val row =
                compose
                    .onNodeWithTag("settings-section-$label")
                    .assertIsDisplayed()
                    .getUnclippedBoundsInRoot()
            assertTrue("$label should fit above navigation", row.top >= 0.dp && row.bottom <= navigationTop)
        }
    }

    private fun showSettings(
        appearance: () -> Unit = {},
        connection: () -> Unit = {},
        offline: Boolean = false,
        fontScale: Float = 1f,
        windowSize: DpSize = DpSize(360.dp, 640.dp),
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
                DeviceConfigurationOverride.ForcedSize(
                    windowSize,
                ) then DeviceConfigurationOverride.FontScale(fontScale),
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
