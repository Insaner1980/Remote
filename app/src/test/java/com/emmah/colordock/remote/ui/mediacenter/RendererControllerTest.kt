package com.emmah.colordock.remote.ui.mediacenter

import com.emmah.colordock.remote.mediacenter.RecoveryFixture
import com.emmah.colordock.remote.mediacenter.protocol.Direction
import com.emmah.colordock.remote.mediacenter.protocol.McReply
import com.emmah.colordock.remote.mediacenter.protocol.McRequest
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand
import com.emmah.colordock.remote.mediacenter.protocol.RemoteJson
import com.emmah.colordock.remote.mediacenter.protocol.RemoteResult
import com.emmah.colordock.remote.mediacenter.protocol.Route
import com.emmah.colordock.remote.mediacenter.protocol.UiAck
import com.emmah.colordock.remote.mediacenter.protocol.UiTopic
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RendererControllerTest {
    @Test fun holdUses450Then180AndHasNoQueuedRepeatsAfterModalOrDisconnect() =
        runTest {
            val sent = mutableListOf<McRequest>()
            val c = RendererController(backgroundScope) { sent.add(it) }
            c.observe(snapshot())
            c.press(Direction.DOWN)
            assertEquals(1, sent.size)
            c.receive(McReply.Success(sent.last().id, RemoteResult.UiMoveFocus(UiAck(sent.last().id, "renderer", "1"))))
            advanceTimeBy(449)
            runCurrent()
            assertEquals(1, sent.size)
            advanceTimeBy(1)
            runCurrent()
            assertEquals(2, sent.size)
            advanceTimeBy(1000)
            runCurrent()
            assertEquals(2, sent.size)
            c.observe(snapshot().copy(ui = snapshot().ui.copy(modalId = "sheet")))
            advanceTimeBy(1000)
            runCurrent()
            assertEquals(2, sent.size)
            c.observe(null)
            c.press(Direction.UP)
            advanceTimeBy(1000)
            runCurrent()
            assertEquals(2, sent.size)
            c.stop()
        }

    @Test fun compositionStaysLocalCommittedSearchDebouncesAndSequencesIncrease() =
        runTest {
            val sent = mutableListOf<McRequest>()
            val c = RendererController(backgroundScope) { sent.add(it) }
            c.observe(snapshot())
            c.committed("old")
            c.composing()
            advanceTimeBy(200)
            runCurrent()
            assertTrue(sent.isEmpty())
            c.committed("new")
            advanceTimeBy(149)
            runCurrent()
            assertTrue(sent.isEmpty())
            advanceTimeBy(1)
            runCurrent()
            val first = sent.single()
            assertEquals("1", (first.command as RemoteCommand.UiSearch).params.editSequence)
            c.receive(McReply.Success(first.id, RemoteResult.UiSearch(UiAck(first.id, "renderer", "2"))))
            c.committed("next")
            advanceTimeBy(150)
            runCurrent()
            assertEquals("2", (sent.last().command as RemoteCommand.UiSearch).params.editSequence)
            c.stop()
        }

    @Test fun latestUnsentSearchWaitsForNavigationAckButDoesNotSurviveDisconnect() =
        runTest {
            val sent = mutableListOf<McRequest>()
            val c = RendererController(backgroundScope) { sent.add(it) }
            c.observe(snapshot())
            c.move(Direction.DOWN)
            c.committed("old")
            advanceTimeBy(150)
            runCurrent()
            c.committed("latest")
            advanceTimeBy(150)
            runCurrent()
            assertEquals(1, sent.size)
            c.receive(McReply.Success(sent.last().id, RemoteResult.UiMoveFocus(UiAck(sent.last().id, "renderer", "2"))))
            assertEquals("latest", (sent.last().command as RemoteCommand.UiSearch).params.text)
            c.committed("not replayed")
            advanceTimeBy(150)
            runCurrent()
            c.observe(null)
            c.observe(snapshot())
            advanceTimeBy(5000)
            runCurrent()
            assertEquals(2, sent.size)
            c.stop()
        }

    private fun snapshot() =
        RemoteJson
            .parseSnapshot(
                RecoveryFixture.snapshot(),
            ).copy(ui = UiTopic("1", true, "renderer", Route.MUSIC, focusId = "search", focusRevision = "1"))
}
