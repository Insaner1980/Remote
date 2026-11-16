package com.emmah.colordock.remote.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.emmah.colordock.remote.mediacenter.MediacenterConnectionState
import com.emmah.colordock.remote.mediacenter.protocol.McRequest
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand
import com.emmah.colordock.remote.tv.TvCommand
import com.emmah.colordock.remote.tv.TvConnectionState
import com.emmah.colordock.remote.tv.TvState
import com.emmah.colordock.remote.ui.mediacenter.AudioSettingsController
import com.emmah.colordock.remote.ui.mediacenter.AudioSettingsState
import com.emmah.colordock.remote.ui.mediacenter.LibraryState
import com.emmah.colordock.remote.ui.mediacenter.LibraryViewModel
import com.emmah.colordock.remote.ui.mediacenter.MediaSessionController
import com.emmah.colordock.remote.ui.mediacenter.MediaTargetControllers
import com.emmah.colordock.remote.ui.mediacenter.MediaTargetState
import com.emmah.colordock.remote.ui.mediacenter.MediacenterTargetContent
import com.emmah.colordock.remote.ui.mediacenter.RendererController
import com.emmah.colordock.remote.ui.mediacenter.phoneMediaSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ControlTargetTest {
    @get:Rule val compose = createComposeRule()
    private val targets = ControlTargetViewModel()
    private val tvSent = mutableListOf<TvCommand>()
    private var releases = 0
    private val mcSent = mutableListOf<McRequest>()

    @Test fun connectedMediacenterWorksWithoutTvAndOwnsOnlyThreeDestinations() {
        targets.observe(TvConnectionState.Disconnected, MediacenterConnectionState.Connected)
        compose.setContent { Fixture() }
        choose("Mediacenter")
        compose.onNodeWithTag("bottom-navigation").assertDoesNotExist()
        compose.onNodeWithText("Pause").performScrollTo().performClick()
        compose.runOnIdle {
            assertTrue(mcSent.single().command is RemoteCommand.PlayerPause)
            assertTrue(tvSent.isEmpty())
        }
        compose.onNodeWithTag("media-nav-Library").performClick()
        compose.onNodeWithTag("transport-controls").assertDoesNotExist()
        compose.onNodeWithTag("media-nav-Settings").performClick()
        compose.onNodeWithTag("transport-controls").assertDoesNotExist()
        compose.onNodeWithTag("settings-section-Devices").performClick()
        compose.onNodeWithText("Mediacenter Connection").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Wallpapers").assertDoesNotExist()
        compose.onNodeWithText("Color Dock").assertDoesNotExist()
    }

    @Test fun connectedTvStillWorksWhenMediacenterIsDisconnected() {
        targets.observe(TvConnectionState.Ready, MediacenterConnectionState.Disconnected)
        compose.setContent { Fixture() }
        choose("Mediacenter")
        compose.onNodeWithText("Pause").assertDoesNotExist()
        choose("LG TV")
        compose.onNodeWithContentDescription("TV volume up").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(listOf(TvCommand.VolumeUp), tvSent)
            assertTrue(mcSent.isEmpty())
        }
        compose.onAllNodesWithText("Wallpapers").assertCountEquals(1)
        compose.onAllNodesWithText("Color Dock").assertCountEquals(2)
    }

    @Test fun switchingTargetsDiscardsTvPowerConfirmationAndNeverSendsPcPower() {
        targets.observe(TvConnectionState.Ready, MediacenterConnectionState.Connected)
        compose.setContent { Fixture() }
        compose.onNodeWithContentDescription("Turn off TV").performScrollTo().performClick()
        compose.onNodeWithText("Turn off the TV?").assertIsDisplayed()
        compose.runOnIdle { targets.select(ControlTarget.Mediacenter) {} }
        compose.onNodeWithText("Turn off the TV?").assertDoesNotExist()
        compose.runOnIdle { targets.select(ControlTarget.LgTv) {} }
        compose.onNodeWithText("Turn off the TV?").assertDoesNotExist()
        compose.onNodeWithContentDescription("Turn off TV").performScrollTo().performClick()
        compose.onNodeWithText("Turn off").performClick()
        compose.runOnIdle {
            assertEquals(listOf(TvCommand.PowerOff), tvSent)
            assertTrue(mcSent.isEmpty())
        }
    }

    @Test fun rapidReturnDisposesHeldGestureAndPowerDialogBeforeFreshTvInput() {
        targets.observe(TvConnectionState.Ready, MediacenterConnectionState.Connected)
        compose.setContent { Fixture() }
        compose.onNodeWithContentDescription("TV volume up").performScrollTo().performTouchInput { down(center) }
        var releasedBefore = 0
        compose.runOnIdle {
            assertEquals(listOf(TvCommand.VolumeUp), tvSent)
            releasedBefore = releases
            targets.select(ControlTarget.Mediacenter) {}
            targets.select(ControlTarget.LgTv) {}
        }
        compose.runOnIdle { assertTrue(releases > releasedBefore) }
        compose.onRoot().performTouchInput { cancel() }
        compose.onNodeWithContentDescription("TV volume up").performScrollTo().performTouchInput {
            down(center)
            up()
        }
        compose.runOnIdle { assertEquals(listOf(TvCommand.VolumeUp, TvCommand.VolumeUp), tvSent) }
        compose.onNodeWithContentDescription("Turn off TV").performScrollTo().performClick()
        compose.onNodeWithText("Turn off the TV?").assertIsDisplayed()
        compose.runOnIdle {
            targets.select(ControlTarget.Mediacenter) {}
            targets.select(ControlTarget.LgTv) {}
        }
        compose.onNodeWithText("Turn off the TV?").assertDoesNotExist()
        compose.runOnIdle { assertTrue(mcSent.isEmpty()) }
    }

    private fun choose(label: String) {
        compose.onNodeWithTag("target-selector").performClick()
        compose.onNodeWithText(label, useUnmergedTree = true).performClick()
    }

    @Composable
    private fun Fixture() {
        val state by targets.state.collectAsState()
        val lease = ControlTargetLease(state.selected, state.generation)
        val selector: @Composable () -> Unit = {
            TargetSelector(state, "Fixture TV", { targets.select(it) {} }, {})
        }
        RemoteTheme {
            TargetAppShell(state, RemoteTab.Remote, {}, mediacenter = { modifier ->
                Column(modifier) {
                    selector()
                    MediaContent()
                }
            }) { modifier ->
                RemoteScreen(
                    TvState(if (state.tvReady) TvConnectionState.Ready else TvConnectionState.Disconnected),
                    emptyList(),
                    { command ->
                        targets.dispatch(lease) { tvSent.add(command) }
                    },
                    { command ->
                        targets.dispatch(lease) { tvSent.add(command) }
                    },
                    { releases++ },
                    {},
                    {},
                    {},
                    modifier,
                    targetSelector = selector,
                )
            }
        }
    }

    @Composable
    private fun MediaContent() {
        val scope = rememberCoroutineScope()
        val lease = targets.lease()
        val media =
            remember {
                MediaSessionController(scope) { request ->
                    var sent = false
                    targets.dispatch(lease) { sent = mcSent.add(request) }
                    sent
                }.apply { observe(phoneMediaSnapshot().takeIf { targets.state.value.mediacenterReady }) }
            }
        val state by media.state.collectAsState()
        val controllers =
            remember {
                MediaTargetControllers(
                    media,
                    LibraryViewModel(scope) { true },
                    RendererController(scope) { true },
                    AudioSettingsController { true },
                )
            }
        MediacenterTargetContent(
            MediaTargetState(state, LibraryState(), AudioSettingsState()),
            controllers,
            { null },
            settings = { androidx.compose.material3.Text("Mediacenter Connection") },
        )
    }
}
