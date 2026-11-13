package com.emmah.colordock.remote.ui.mediacenter

import com.emmah.colordock.remote.mediacenter.protocol.BrowserAck
import com.emmah.colordock.remote.mediacenter.protocol.BrowserCapabilities
import com.emmah.colordock.remote.mediacenter.protocol.BrowserKey
import com.emmah.colordock.remote.mediacenter.protocol.BrowserState
import com.emmah.colordock.remote.mediacenter.protocol.Delivery
import com.emmah.colordock.remote.mediacenter.protocol.McReply
import com.emmah.colordock.remote.mediacenter.protocol.McRequest
import com.emmah.colordock.remote.mediacenter.protocol.MethodName
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand
import com.emmah.colordock.remote.mediacenter.protocol.RemoteResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BrowserControlsTest {
    @Test fun normalizedPositionClampsToContentAndRejectsAnUnmeasuredPad() {
        assertEquals(BrowserPosition(0.5, 0.25), normalizedBrowserPosition(100f, 25f, 200, 100))
        assertEquals(BrowserPosition(0.0, 1.0), normalizedBrowserPosition(-1f, 300f, 200, 100))
        assertNull(normalizedBrowserPosition(100f, 25f, 0, 100))
        assertNull(normalizedBrowserPosition(Float.NaN, 25f, 200, 100))
    }

    @Test fun coalescesMotionAndDropsQueuedInputOnReleaseAndTargetReplacement() =
        runTest {
            val sent = mutableListOf<McRequest>()
            val input = BrowserInputController(backgroundScope, { sent.add(it) }, { testScheduler.currentTime }, { 0L })
            val target = BrowserTarget("epoch", "browser", "1")
            input.observe(target, caps, browserMethods)
            input.point(target, BrowserPosition(0.1, 0.2))
            input.point(target, BrowserPosition(0.3, 0.4))
            input.point(target, BrowserPosition(0.5, 0.6))
            assertEquals(1, sent.size)
            acknowledgePointer(input, sent.last())
            advanceTimeBy(34)
            runCurrent()
            val points = sent.map { (it.command as RemoteCommand.BrowserPointer).params }
            assertEquals(listOf(0.1, 0.5), points.map { it.x })
            assertTrue(points.last().inputSequence.toULong() > points.first().inputSequence.toULong())
            input.scroll(target, 5.0, 9.0)
            input.endGesture()
            acknowledgePointer(input, sent.last())
            advanceTimeBy(34)
            runCurrent()
            assertEquals(2, sent.size)
            input.point(target, BrowserPosition(0.7, 0.8))
            input.point(target, BrowserPosition(0.9, 1.0))
            input.observe(target.copy(generation = "2"), caps, browserMethods)
            advanceTimeBy(100)
            runCurrent()
            input.click(target, BrowserPosition(0.5, 0.5))
            assertEquals(3, sent.size)
        }

    @Test fun slowMotionAcknowledgementKeepsOnlyOneUnsentSample() =
        runTest {
            val sent = mutableListOf<McRequest>()
            val input =
                BrowserInputController(backgroundScope, { sent.add(it) }, { testScheduler.currentTime }, { 0L })
            val target = BrowserTarget("epoch", "browser", "1")
            input.observe(target, caps, browserMethods)
            input.point(target, BrowserPosition(0.1, 0.1))
            repeat(10) { frame ->
                advanceTimeBy(34)
                input.point(target, BrowserPosition(frame / 10.0, frame / 10.0))
            }
            assertEquals(1, sent.size)
            acknowledgePointer(input, sent.last())
            assertEquals(2, sent.size)
            assertEquals(0.9, (sent.last().command as RemoteCommand.BrowserPointer).params.x, 0.0)
        }

    private fun acknowledgePointer(
        controller: BrowserInputController,
        request: McRequest,
    ) {
        val pointer = (request.command as RemoteCommand.BrowserPointer).params
        val ack = BrowserAck(request.id, pointer.browserSessionId, pointer.targetGeneration)
        controller.receive(McReply.Success(request.id, RemoteResult.BrowserPointer(ack)))
    }

    @Test fun committedTextAndFixedKeysUseOnlyTheVisibleEnabledTarget() =
        runTest {
            val sent = mutableListOf<McRequest>()
            val input = BrowserInputController(backgroundScope, { sent.add(it) }, { testScheduler.currentTime }, { 0L })
            val target = BrowserTarget("epoch", "browser", "1")
            input.observe(target, caps.copy(text = false), browserMethods)
            assertFalse(input.text(target, "secret"))
            assertTrue(input.key(target, BrowserKey.SHIFT_TAB))
            input.observe(target, caps, browserMethods)
            assertTrue(input.text(target, "ä\uD83D\uDD11"))
            assertFalse(input.text(target, "x".repeat(4097)))
            input.observe(null, caps, browserMethods)
            assertFalse(input.key(target, BrowserKey.ENTER))
            val text = sent.last()
            assertEquals(Delivery.TRANSIENT, text.command.delivery)
            assertEquals("1", (text.command as RemoteCommand.BrowserText).params.targetGeneration)
            assertFalse(text.toString().contains("ä"))
            assertEquals(2, sent.size)
        }
}

internal val caps = BrowserCapabilities(true, true, true, false, false, false)
internal val browserMethods = MethodName.entries.filter { it.wire.startsWith("browser.") }

internal fun browserState(generation: String = "1") =
    BrowserState("browser", generation, "youtube", "profile", true, caps)
