package com.emmah.colordock.remote.ui

import android.view.KeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
        compose.onNodeWithText("Volume").assertDoesNotExist()
        compose.onNodeWithText("Channel").assertDoesNotExist()
        compose.onNodeWithContentDescription("Up").performScrollTo()
        val up = compose.onNodeWithContentDescription("Up").fetchSemanticsNode().boundsInRoot
        val ok = compose.onNodeWithContentDescription("OK").fetchSemanticsNode().boundsInRoot
        val down = compose.onNodeWithContentDescription("Down").fetchSemanticsNode().boundsInRoot
        assertTrue(up.bottom <= ok.top)
        assertTrue(ok.bottom <= down.top)
        assertHorizontalDirectionOrder()
        listOf("Volume up", "Volume down", "Channel up", "Channel down").forEach { label ->
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
        compose.onNodeWithText("Mute sound").performScrollTo().performClick()
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
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
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
