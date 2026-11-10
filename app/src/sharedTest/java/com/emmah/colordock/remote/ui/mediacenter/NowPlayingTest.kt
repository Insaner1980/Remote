package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand
import com.emmah.colordock.remote.ui.RemoteTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NowPlayingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun realTransportUsesCurrentSessionAndDisconnectDisablesIt() {
        val state = mutableStateOf(MediaSessionState(snapshot = phoneMediaSnapshot()))
        val sent = mutableListOf<RemoteCommand>()
        compose.setContent {
            RemoteTheme {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                ) { NowPlayingControls(state.value, { sent.add(it) }, {}) }
            }
        }
        compose.onNodeWithText("Pause").performClick()
        assertEquals("session", (sent.single() as RemoteCommand.PlayerPause).params.sessionId)
        compose.onNodeWithText("Mute Mediacenter").performScrollTo().performClick()
        assertTrue((sent.last() as RemoteCommand.PlayerMute).params.muted)
        compose.runOnIdle { state.value = MediaSessionState() }
        compose.onNodeWithText("Pause").performScrollTo().assertIsNotEnabled()
    }

    @Test fun seekDraftSurvivesNativePositionUpdateDuringPointerDrag() {
        val state = mutableStateOf(MediaSessionState(snapshot = phoneMediaSnapshot()))
        val sent = mutableListOf<RemoteCommand>()
        compose.setContent {
            RemoteTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    NowPlayingControls(state.value, { sent.add(it) }, {})
                }
            }
        }
        val seek = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))[0].performScrollTo()
        seek.performTouchInput {
            down(center)
            moveTo(Offset(width * 0.8f, centerY), delayMillis = 32)
        }
        val selected = seek.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current
        assertTrue(selected > 120f)
        compose.runOnIdle {
            val snapshot = state.value.snapshot!!
            state.value =
                state.value.copy(snapshot = snapshot.copy(player = snapshot.player.copy(positionSeconds = 20.0)))
        }
        seek.performTouchInput { up() }
        val request = sent.single() as RemoteCommand.PlayerSeek
        assertEquals("session", request.params.sessionId)
        assertEquals(selected.toDouble(), request.params.positionSeconds, 0.5)
    }

    @Test fun seekGestureIsRetiredWhenPlaybackSessionChangesOrDisconnects() {
        val state = mutableStateOf(MediaSessionState(snapshot = phoneMediaSnapshot()))
        val sent = mutableListOf<RemoteCommand>()
        compose.setContent {
            RemoteTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    NowPlayingControls(state.value, { sent.add(it) }, {})
                }
            }
        }
        for (disconnect in listOf(false, true)) {
            val seek =
                compose
                    .onAllNodes(
                        SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress),
                    )[0]
                    .performScrollTo()
            seek.performTouchInput {
                down(center)
                moveTo(Offset(width * 0.8f, centerY), delayMillis = 32)
            }
            compose.runOnIdle {
                val snapshot = state.value.snapshot!!
                state.value =
                    state.value.copy(
                        snapshot =
                            if (disconnect) {
                                null
                            } else {
                                snapshot.copy(
                                    player = snapshot.player.copy(sessionId = "replacement", positionSeconds = 45.0),
                                )
                            },
                    )
            }
            compose.onRoot().performTouchInput { up() }
            assertTrue(sent.isEmpty())
            if (!disconnect) {
                assertEquals(
                    45f,
                    compose
                        .onAllNodes(
                            SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress),
                        )[0]
                        .fetchSemanticsNode()
                        .config[SemanticsProperties.ProgressBarRangeInfo]
                        .current,
                    0.01f,
                )
            }
        }
    }
}
