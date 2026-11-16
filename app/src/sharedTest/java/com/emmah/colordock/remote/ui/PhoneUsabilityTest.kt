package com.emmah.colordock.remote.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.emmah.colordock.remote.tv.TvApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhoneUsabilityTest {
    @get:Rule val compose = createComposeRule()

    @Test fun deviceNameHasSafeInsetsAndOnlyAppearsOnce() {
        compose.setContent {
            RemoteTheme {
                Box(Modifier.width(240.dp)) {
                    TargetSelector(ControlTargetState(tvReady = true), "Living room", {}, {})
                }
            }
        }
        val button =
            compose
                .onNodeWithTag("target-selector")
                .assertHasClickAction()
                .fetchSemanticsNode()
                .boundsInRoot
        val text = compose.onNodeWithText("Living room", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("Title must not touch the rounded clip edge", text.left > button.left + 8f)
        assertTrue(text.right < button.right - 8f)
        compose.onAllNodesWithText("Living room", useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithText("Change device", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("LG TV · Connected ▾", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun longDeviceNameAndTargetChoicesRemainReachableAtLargeText() {
        var selected: ControlTarget? = null
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                RemoteTheme {
                    Box(Modifier.width(240.dp)) {
                        TargetSelector(
                            ControlTargetState(tvReady = true, mediacenterReady = true),
                            "Living room television with a long name",
                            { selected = it },
                            {},
                        )
                    }
                }
            }
        }
        compose.onNodeWithTag("target-selector").performClick()
        compose.onNodeWithText("Mediacenter", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertEquals(ControlTarget.Mediacenter, selected) }
    }

    @Test fun mediacenterPickerIsSheetWithoutCloseAndSelectsOnce() {
        var selected: ControlTarget? = null
        var calls = 0
        compose.setContent {
            RemoteTheme {
                TargetSelector(
                    ControlTargetState(selected = ControlTarget.Mediacenter),
                    "Fixture TV",
                    {
                        selected = it
                        calls++
                    },
                    {},
                )
            }
        }
        compose.onNodeWithTag("target-selector").performClick()
        compose.onNodeWithText("Control device").assertIsDisplayed()
        compose.onNodeWithText("Close").assertDoesNotExist()
        compose.onNodeWithText("LG TV", useUnmergedTree = true).performClick()
        compose.runOnIdle {
            assertEquals(ControlTarget.LgTv, selected)
            assertEquals(1, calls)
        }
        compose.onNodeWithText("Control device").assertDoesNotExist()
    }

    @Test fun lgPickerKeepsCloseAndSelectsOnce() {
        var calls = 0
        compose.setContent {
            RemoteTheme { TargetSelector(ControlTargetState(), "Fixture TV", { calls++ }, {}) }
        }
        compose.onNodeWithTag("target-selector").performClick()
        compose.onNodeWithText("Close").assertIsDisplayed()
        compose.onNodeWithText("Mediacenter", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertEquals(1, calls) }
        compose.onNodeWithText("Control device").assertDoesNotExist()
    }

    @Test fun shortcutIsIconOnlyButKeepsItsAccessibleLaunchAction() {
        var opened = ""
        compose.setContent {
            RemoteTheme {
                TvAppsStrip(
                    TvAppsUiState(apps = listOf(TvApp("netflix", "Netflix", null)), connected = true),
                    emptyMap(),
                    {},
                    { opened = it },
                    {},
                    false,
                )
            }
        }
        compose.onNodeWithText("Netflix", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("Open Netflix on TV").assertHasClickAction().performClick()
        compose.runOnIdle { assertEquals("netflix", opened) }
    }
}
