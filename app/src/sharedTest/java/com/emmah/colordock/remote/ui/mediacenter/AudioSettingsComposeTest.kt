package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.emmah.colordock.remote.mediacenter.protocol.ActivityPreparation
import com.emmah.colordock.remote.mediacenter.protocol.AudioOutput
import com.emmah.colordock.remote.mediacenter.protocol.McReply
import com.emmah.colordock.remote.mediacenter.protocol.McRequest
import com.emmah.colordock.remote.mediacenter.protocol.OutputsTopic
import com.emmah.colordock.remote.mediacenter.protocol.RemoteResult
import com.emmah.colordock.remote.mediacenter.protocol.WireErrorCode
import com.emmah.colordock.remote.ui.RemoteTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AudioSettingsComposeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun soundIsOptionalShowsRequestedAndConfirmedSeparatelyAndDisablesMutationWhenDisconnected() {
        val native =
            OutputsTopic(
                "2",
                "hdmi",
                "laptop",
                false,
                listOf(AudioOutput("hdmi", "TV", true), AudioOutput("laptop", "Laptop speakers", true)),
            )
        val state = mutableStateOf(AudioSettingsState(outputs = native))
        var reads = 0
        var mutations = 0
        val actions =
            SoundActions(
                { reads++ },
                { mutations++ },
                { mutations++ },
                { mutations++ },
                { mutations++ },
                { mutations++ },
            )
        compose.setContent { RemoteTheme { SoundOptions(state.value, actions) } }
        compose.waitForIdle()
        compose.onNodeWithText("Requested: TV").assertDoesNotExist()
        compose.onNodeWithText("Sound").performClick()
        compose.runOnIdle { assertEquals(0, mutations) }
        compose.onNodeWithText("TV").assertIsDisplayed().assertIsSelected()
        assertEquals("laptop", state.value.outputs?.effectiveOutputId)
        compose.onNodeWithText("Laptop speakers").assertIsDisplayed()
        compose.runOnIdle { state.value = AudioSettingsState() }
        compose.onNodeWithText("No sound outputs").assertIsDisplayed()
        compose.onNodeWithText("Test sound").assertIsNotEnabled()
        compose.runOnIdle {
            assertEquals(1, reads)
            assertEquals(0, mutations)
        }
    }

    @Test fun expiredConfirmCancelAndBackAllReleaseRenderedSheetAndAllowNewChoice() {
        val sent = mutableListOf<McRequest>()
        val controller = AudioSettingsController { sent.add(it) }
        controller.observe("epoch", nativeAudioProjection("active"))
        val actions =
            SoundActions({}, {}, { id ->
                if (controller.choose(
                        id,
                    )
                ) {
                    controller.receive(
                        McReply.Success(
                            sent.last().id,
                            RemoteResult.AudioPrepare(
                                ActivityPreparation.ConfirmationRequired(audioConfirmation("sheet-${sent.size}")),
                            ),
                        ),
                    )
                }
            }, {}, {
                if (controller.confirm()) {
                    controller.receive(
                        audioFailure(sent.last().id, WireErrorCode.CONFIRMATION_EXPIRED),
                    )
                }
            }, {
                if (controller.cancel()) {
                    controller.receive(
                        audioFailure(sent.last().id, WireErrorCode.CONFIRMATION_EXPIRED),
                    )
                }
            })
        compose.setContent {
            RemoteTheme {
                val state by controller.state.collectAsState()
                SoundOptions(state, actions)
            }
        }
        compose.onNodeWithText("Sound").performClick()
        for (action in listOf("Confirm", "Cancel", "Back")) {
            compose.onNodeWithText("TV").performClick()
            compose.onNodeWithText("Change sound output?").assertIsDisplayed()
            if (action == "Back") Espresso.pressBack() else compose.onNodeWithText(action).performClick()
            compose.onNodeWithText("Change sound output?").assertDoesNotExist()
            compose.onNodeWithText("TV").assertIsDisplayed()
        }
        compose.onNodeWithText("TV").performClick()
        compose.onNodeWithText("Change sound output?").assertIsDisplayed()
    }

    @Test fun actualNativeIdleAndPreferenceDoNotRenderFailureButLostSinkDoes() {
        val controller = AudioSettingsController { true }
        controller.observe("epoch", nativeAudioProjection("idle"))
        compose.setContent {
            RemoteTheme {
                val state by controller.state.collectAsState()
                SoundOptions(state, SoundActions({}, {}, {}, {}, {}, {}))
            }
        }
        compose.onNodeWithText("Sound").performClick()
        compose.onNodeWithText("No playback stream has been confirmed.").assertDoesNotExist()
        compose.onNodeWithText("Audio output unavailable. Retry or choose a sound output.").assertDoesNotExist()
        compose.runOnIdle { controller.observe("epoch", nativeAudioProjection("preference")) }
        compose.onNodeWithText("Sound preference saved; no playback stream has been confirmed.").assertDoesNotExist()
        compose.onNodeWithText("Audio output unavailable. Retry or choose a sound output.").assertDoesNotExist()
        compose.runOnIdle { controller.observe("epoch", nativeAudioProjection("returnedWithoutStream")) }
        compose.onNodeWithText("Output unavailable").assertIsDisplayed()
        compose.onNodeWithText("Audio output unavailable. Retry or choose a sound output.").assertDoesNotExist()
    }

    @Test fun desktopResolutionClosesTheRenderedServerSheet() {
        val sent = mutableListOf<McRequest>()
        val controller = AudioSettingsController { sent.add(it) }
        controller.observe("epoch", nativeAudioProjection("active"))
        controller.choose("hdmi")
        controller.receive(
            McReply.Success(
                sent.last().id,
                RemoteResult.AudioPrepare(ActivityPreparation.ConfirmationRequired(audioConfirmation("shared"))),
            ),
        )
        compose.setContent {
            RemoteTheme {
                val state by controller.state.collectAsState()
                SoundOptions(state, SoundActions({}, {}, {}, {}, {}, {}))
            }
        }
        compose.onNodeWithText("Sound").performClick()
        compose.onNodeWithText("Change sound output?").assertIsDisplayed()
        compose.runOnIdle {
            controller.confirmationStatus.refresh()
            controller.receive(audioFailure(sent.last().id, WireErrorCode.NOT_FOUND))
        }
        compose.onNodeWithText("Change sound output?").assertDoesNotExist()
        compose.onNodeWithText("TV").assertIsDisplayed()
    }
}
