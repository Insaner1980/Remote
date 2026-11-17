package com.emmah.colordock.remote.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.emmah.colordock.remote.tv.TvChannel
import com.emmah.colordock.remote.tv.TvCommand
import com.emmah.colordock.remote.tv.TvConnectionState
import com.emmah.colordock.remote.tv.TvState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RemoteScreenTest {
    @get:Rule val compose = createComposeRule()
    private val sent = mutableListOf<TvCommand>()
    private val held = mutableListOf<TvCommand>()
    private var released = 0

    @Test fun transportTileHeightsMatchAtNormalText() = verifyTransportTileHeights(1f)

    @Test fun transportTileHeightsMatchAtPhoneText() = verifyTransportTileHeights(1.15f)

    private fun verifyTransportTileHeights(scale: Float) {
        showConfigured(scale)
        val heights =
            listOf("Mute TV sound", "Numbers", "Live TV", "All apps").map {
                compose
                    .onNodeWithText(it)
                    .performScrollTo()
                    .fetchSemanticsNode()
                    .boundsInRoot.height
            }
        assertTrue("All four transport tiles need equal heights: $heights", heights.max() - heights.min() < 1f)
    }

    @Test fun rockersShowVolumeAndChannelLabels() {
        showConfigured(1f)
        compose.onNodeWithText("VOL").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("CH").performScrollTo().assertIsDisplayed()
    }

    @Test fun textAt140PercentUsesStackedControls() {
        showConfigured(1.4f)
        compose.onNodeWithTag("compact-controls").assertDoesNotExist()
        val home =
            compose
                .onNodeWithText("LG Home")
                .performScrollTo()
                .fetchSemanticsNode()
                .boundsInRoot
        val dock =
            compose
                .onNodeWithText("Color Dock")
                .performScrollTo()
                .fetchSemanticsNode()
                .boundsInRoot
        assertTrue("Large text needs vertically stacked action buttons", home.top < dock.top)
    }

    private fun showConfigured(scale: Float) {
        compose.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(412.dp, 900.dp)) then
                    DeviceConfigurationOverride.FontScale(scale),
            ) {
                RemoteTheme {
                    RemoteScreen(TvState(TvConnectionState.Ready), emptyList(), sent::add, held::add, {
                        released++
                    }, {}, {}, {})
                }
            }
        }
    }

    private fun show(
        width: Int = 400,
        scale: Float = 1f,
        direction: LayoutDirection = LayoutDirection.Ltr,
    ) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, scale),
                LocalLayoutDirection provides direction,
            ) {
                RemoteTheme(
                    AppTheme.fromStored(
                        androidx.test.platform.app.InstrumentationRegistry
                            .getArguments()
                            .getString("theme"),
                    ),
                ) {
                    Surface {
                        Box(Modifier.width(width.dp)) {
                            RemoteScreen(TvState(TvConnectionState.Ready), emptyList(), sent::add, held::add, {
                                released++
                            }, {}, {}, {})
                        }
                    }
                }
            }
        }
    }

    private fun checkLayout(
        width: Int,
        scale: Float,
    ) {
        show(width, scale)
        compose.onNodeWithText("TV volume").assertDoesNotExist()
        compose.onNodeWithText("Channel").assertDoesNotExist()
        compose.onNodeWithContentDescription("Up").performScrollTo()
        val up = compose.onNodeWithContentDescription("Up").fetchSemanticsNode().boundsInRoot
        val ok = compose.onNodeWithContentDescription("OK").fetchSemanticsNode().boundsInRoot
        val down = compose.onNodeWithContentDescription("Down").fetchSemanticsNode().boundsInRoot
        assertTrue(up.bottom <= ok.top)
        assertTrue(ok.bottom <= down.top)
        assertHorizontalDirectionOrder()
        listOf("TV volume up", "TV volume down", "Channel up", "Channel down").forEach { label ->
            compose
                .onNodeWithContentDescription(
                    label,
                ).performScrollTo()
                .assertIsDisplayed()
                .assertHasClickAction()
                .assertHeightIsAtLeast(48.dp)
        }
        compose
            .onNodeWithText("Numbers")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        compose.onNodeWithText("Open Live TV and tune").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Cancel").performScrollTo().performClick()
        assertTrue(sent.isEmpty())
    }

    @Test fun remoteAt320dp() = checkLayout(320, 1f)

    @Test fun remoteAt200PercentFont() = checkLayout(400, 2f)

    @Test fun remoteAt320dpAnd200PercentFont() = checkLayout(320, 2f)

    private fun assertHorizontalDirectionOrder() {
        val left = compose.onNodeWithContentDescription("Left").fetchSemanticsNode().boundsInRoot
        val ok = compose.onNodeWithContentDescription("OK").fetchSemanticsNode().boundsInRoot
        val right = compose.onNodeWithContentDescription("Right").fetchSemanticsNode().boundsInRoot
        assertTrue(left.right <= ok.left)
        assertTrue(ok.right <= right.left)
    }

    @Test fun rtlKeepsPhysicalLeftAndRightCommandsInPlace() {
        show(320, 2f, LayoutDirection.Rtl)
        compose.onNodeWithContentDescription("Up").performScrollTo()
        assertHorizontalDirectionOrder()
        compose.onNodeWithContentDescription("Left").performTouchInput {
            down(center)
            up()
        }
        compose.onNodeWithContentDescription("Right").performTouchInput {
            down(center)
            up()
        }
        compose.runOnIdle {
            assertEquals(listOf(TvCommand.Left, TvCommand.Right), held)
            assertTrue(sent.isEmpty())
        }
    }

    @Test fun touchPressDoesNotAlsoSendTheClickCommand() {
        show()
        compose.onNodeWithContentDescription("Up").performScrollTo().performTouchInput {
            down(center)
            up()
        }
        compose.runOnIdle {
            assertEquals(listOf(TvCommand.Up), held)
            assertTrue(sent.isEmpty())
            assertTrue(released > 0)
        }
    }

    @Test fun unknownMuteIsExplicitAndUnavailableGuideIsDisabled() {
        show()
        compose.onNodeWithText("TV Guide").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Mute TV sound").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(listOf(TvCommand.Mute), sent)
        }
    }

    @Test fun powerRequiresConfirmationAndPhoneBackDoesNotSendTvBack() {
        show()
        compose.onNodeWithContentDescription("Turn off TV").performScrollTo().performClick()
        compose.runOnIdle {
            assertTrue(sent.isEmpty())
        }
        pressBack()
        compose.onNodeWithText("Turn off the TV?").assertDoesNotExist()
        compose.runOnIdle {
            assertTrue(sent.isEmpty())
        }
        compose.onNodeWithContentDescription("Turn off TV").performScrollTo().performClick()
        compose.onNodeWithText("Turn off", useUnmergedTree = true).performClick()
        compose.runOnIdle {
            assertEquals(listOf(TvCommand.PowerOff), sent)
        }
    }

    @Test fun duplicateChannelNumbersRequireExplicitChoiceAndDigitsStayLocal() {
        var tuned: String? = null
        compose.setContent {
            RemoteTheme(
                AppTheme.fromStored(
                    androidx.test.platform.app.InstrumentationRegistry
                        .getArguments()
                        .getString("theme"),
                ),
            ) {
                NumberPadSheet(listOf(TvChannel("news", "12", "News"), TvChannel("local", "12", "Local")), {}, {
                    tuned =
                        it
                })
            }
        }
        compose.onNodeWithText("1").performScrollTo().performClick()
        compose.onNodeWithText("2").performScrollTo().performClick()
        compose.onNodeWithText("Open Live TV and tune").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle {
            assertNull(tuned)
        }
        compose.onNodeWithText("12: Local").performScrollTo().performClick()
        compose.onNodeWithText("Open Live TV and tune").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals("local", tuned)
        }
    }
}
