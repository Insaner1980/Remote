package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.emmah.colordock.remote.mediacenter.protocol.QueueEntry
import com.emmah.colordock.remote.mediacenter.protocol.QueueMoveParams
import com.emmah.colordock.remote.mediacenter.protocol.QueuePage
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand
import com.emmah.colordock.remote.ui.RemoteTheme
import com.emmah.colordock.remote.ui.assertDestructiveIcon
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QueueTest {
    @get:Rule val compose = createComposeRule()

    @Test fun renderedCanonicalQueueMovesAndRemovesStableDuplicateEntries() {
        val commands = mutableListOf<RemoteCommand>()
        val queue =
            QueuePage(
                "7",
                listOf(QueueEntry("entry-a", phoneMediaItem("first")), QueueEntry("entry-b", phoneMediaItem("second"))),
            )
        compose.setContent {
            RemoteTheme {
                QueueSheet(
                    MediaSessionState(snapshot = phoneMediaSnapshot(), queue = queue, queueOpen = true),
                    QueueActions(
                        {
                            commands.add(it)
                        },
                        {},
                        {},
                        {},
                    ),
                )
            }
        }
        compose.onAllNodesWithTag("queue-editor").assertCountEquals(1)
        compose.onNodeWithTag("queue-options-entry-b").performScrollTo().performClick()
        compose.onNodeWithText("Move up").performClick()
        assertEquals(QueueMoveParams("entry-b", "entry-a"), (commands.last() as RemoteCommand.QueueMove).params)
        compose.onNodeWithTag("queue-options-entry-b").performScrollTo().performClick()
        compose.assertDestructiveIcon("Remove")
        compose.onNodeWithText("Remove").performClick()
        assertEquals("entry-b", (commands.last() as RemoteCommand.QueueRemove).params.entryId)
        compose.onNodeWithText("Save playlist").performScrollTo().performClick()
        compose.onNodeWithText("Save").performScrollTo().assertIsNotEnabled()
    }
}
