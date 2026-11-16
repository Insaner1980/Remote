package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.emmah.colordock.remote.mediacenter.protocol.ActivityMode
import com.emmah.colordock.remote.mediacenter.protocol.McRequest
import com.emmah.colordock.remote.mediacenter.protocol.PlayerPhase
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand
import com.emmah.colordock.remote.ui.RemoteTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediacenterUsabilityTest {
    @get:Rule val compose = createComposeRule()

    @Test fun directionPadIsCenteredInAvailableWidth() {
        compose.setContent {
            val scope = rememberCoroutineScope()
            val controller = remember { RendererController(scope) { true } }
            RemoteTheme {
                Box(Modifier.width(380.dp).testTag("available-width")) {
                    RendererControls(phoneMediaSnapshot(), controller, Modifier.width(380.dp))
                }
            }
        }
        val parent = compose.onNodeWithTag("available-width").fetchSemanticsNode().boundsInRoot
        val pad = compose.onNodeWithTag("direction-pad").fetchSemanticsNode().boundsInRoot
        assertEquals(parent.center.x, pad.center.x, 1f)
    }

    @Test fun idleScreenOffersLibraryEntryAndQueriesItWithoutPlaybackCommands() {
        val sent = mutableListOf<McRequest>()
        compose.setContent { IdleTarget { sent.add(it) } }
        compose.onNodeWithText("idle").assertDoesNotExist()
        compose.onNodeWithText("Pause").assertDoesNotExist()
        compose
            .onNodeWithText(
                "Browse your videos, music and photos, or choose a streaming service.",
            ).assertDoesNotExist()
        compose.onNodeWithTag("media-nav-Library").performClick()
        compose.onNodeWithTag("media-nav-Library").assertIsSelected()
        compose.waitUntil(5_000) { sent.any { it.command is RemoteCommand.LibraryQuery } }
        compose.runOnIdle { assertTrue(sent.none { it.command is RemoteCommand.PlayerPause }) }
    }

    @Test fun currentNavigationDestinationIsMarkedAndChangesOnSelection() {
        compose.setContent { IdleTarget { true } }
        compose.onNodeWithTag("media-nav-Remote").assertIsSelected()
        compose.onNodeWithTag("media-nav-Settings").performClick()
        compose.onNodeWithTag("media-nav-Settings").assertIsSelected()
    }

    @Composable
    private fun IdleTarget(send: (McRequest) -> Boolean) {
        val scope = rememberCoroutineScope()
        val controllers =
            remember {
                val base = phoneMediaSnapshot()
                val idle =
                    base.copy(
                        activity = base.activity.copy(mode = ActivityMode.IDLE, title = null, sessionId = null),
                        player = base.player.copy(sessionId = null, itemId = null, phase = PlayerPhase.IDLE),
                        queueSummary = base.queueSummary.copy(count = "0", sessionId = null, currentEntryId = null),
                    )
                MediaTargetControllers(
                    MediaSessionController(scope, send).apply { observe(idle) },
                    LibraryViewModel(scope, send).apply { observe("epoch", "server") },
                    RendererController(scope, send),
                    AudioSettingsController(send),
                )
            }
        val state by controllers.media.state.collectAsState()
        val library by controllers.library.state.collectAsState()
        RemoteTheme {
            MediacenterTargetContent(MediaTargetState(state, library, AudioSettingsState()), controllers, { null })
        }
    }
}
