package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.emmah.colordock.remote.mediacenter.protocol.ActivityMode
import com.emmah.colordock.remote.mediacenter.protocol.McRequest
import com.emmah.colordock.remote.mediacenter.protocol.PlayerPhase
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand
import com.emmah.colordock.remote.mediacenter.protocol.RemoteSnapshot
import com.emmah.colordock.remote.ui.RemoteTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediacenterRemoteLayoutTest {
    @get:Rule val compose = createComposeRule()
    private val sent = mutableListOf<McRequest>()

    @Test fun sessionHasOneAccessibleQueueActionAndBackMenuBelowPad() {
        show(phoneMediaSnapshot())
        compose.onAllNodesWithContentDescription("Queue").assertCountEquals(1)
        compose.onAllNodesWithText("Queue").assertCountEquals(0)
        compose.onAllNodesWithText("Back").assertCountEquals(1)
        compose.onAllNodesWithText("Menu").assertCountEquals(1)
        compose.onNodeWithText("Menu").performScrollTo()
        val pad = compose.onNodeWithTag("direction-pad").fetchSemanticsNode().boundsInRoot
        val menu = compose.onNodeWithText("Menu").fetchSemanticsNode().boundsInRoot
        assertTrue(menu.top >= pad.bottom)
    }

    @Test fun idleEmptyQueueHasNoQueueAction() {
        show(idleSnapshot())
        compose.onAllNodesWithContentDescription("Queue").assertCountEquals(0)
        compose.onAllNodesWithText("Queue").assertCountEquals(0)
        compose.onAllNodesWithText("Back").assertCountEquals(1)
        compose.onAllNodesWithText("Menu").assertCountEquals(1)
    }

    @Test fun stoppedPlaybackStillOpensExistingQueueOnce() {
        val idle = idleSnapshot()
        show(idle.copy(queueSummary = idle.queueSummary.copy(count = "2")))
        compose.onNodeWithContentDescription("Queue").performClick()
        compose.runOnIdle { assertEquals(1, sent.count { it.command is RemoteCommand.QueueGet }) }
    }

    @Test fun photosOfferQueueEvenWithoutPlayerSessionOrQueuedItems() {
        val idle = idleSnapshot()
        show(idle.copy(activity = idle.activity.copy(mode = ActivityMode.PHOTOS)))
        compose.onAllNodesWithContentDescription("Queue").assertCountEquals(1)
    }

    @Test fun browserDoesNotExposeNativeQueue() {
        val base = phoneMediaSnapshot()
        show(base.copy(activity = base.activity.copy(mode = ActivityMode.BROWSER)))
        compose.onAllNodesWithContentDescription("Queue").assertCountEquals(0)
    }

    private fun idleSnapshot(): RemoteSnapshot {
        val base = phoneMediaSnapshot()
        return base.copy(
            activity = base.activity.copy(mode = ActivityMode.IDLE, title = null, sessionId = null),
            player = base.player.copy(sessionId = null, itemId = null, phase = PlayerPhase.IDLE),
            queueSummary = base.queueSummary.copy(count = "0", sessionId = null, currentEntryId = null),
        )
    }

    private fun show(snapshot: RemoteSnapshot) {
        compose.setContent {
            val scope = rememberCoroutineScope()
            val controllers =
                remember {
                    val send: (McRequest) -> Boolean = { sent.add(it) }
                    MediaTargetControllers(
                        MediaSessionController(scope, send).apply { observe(snapshot) },
                        LibraryViewModel(scope, send),
                        RendererController(scope, send),
                        AudioSettingsController(send),
                    )
                }
            val state by controllers.media.state.collectAsState()
            RemoteTheme {
                MediacenterTargetContent(
                    MediaTargetState(state, LibraryState(), AudioSettingsState()),
                    controllers,
                    { null },
                )
            }
        }
    }
}
