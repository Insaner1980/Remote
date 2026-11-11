package com.emmah.colordock.remote.ui.mediacenter

import com.emmah.colordock.remote.mediacenter.RecoveryFixture
import com.emmah.colordock.remote.mediacenter.protocol.AcceptedResult
import com.emmah.colordock.remote.mediacenter.protocol.DrivesMountParams
import com.emmah.colordock.remote.mediacenter.protocol.ErrorPayload
import com.emmah.colordock.remote.mediacenter.protocol.McReply
import com.emmah.colordock.remote.mediacenter.protocol.McRequest
import com.emmah.colordock.remote.mediacenter.protocol.OperationAccepted
import com.emmah.colordock.remote.mediacenter.protocol.OperationState
import com.emmah.colordock.remote.mediacenter.protocol.OperationStatus
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand
import com.emmah.colordock.remote.mediacenter.protocol.RemoteJson
import com.emmah.colordock.remote.mediacenter.protocol.RemoteResult
import com.emmah.colordock.remote.mediacenter.protocol.WireErrorCode
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
class MediaSessionMessageTest {
    @Test fun nativeMessagesCarryExactOperationAndGenericFeedbackClearsOnlyMessageProvenance() =
        runTest {
            val sent = mutableListOf<McRequest>()
            val media = MediaSessionController(backgroundScope) { sent.add(it) }
            media.observe(RemoteJson.parseSnapshot(RecoveryFixture.snapshot()))
            media.command(RemoteCommand.DrivesMount(DrivesMountParams("volume")))
            val running = acceptMount(media, sent.last())
            assertEquals(running, media.state.value.messageOperation)
            media.operation(running.copy(requestId = "other-request"))
            assertEquals(running, media.state.value.messageOperation)
            media.message("Refresh connection settings.")
            assertNull(media.state.value.messageOperation)
            assertEquals(running, media.observedOperation)
            assertTrue(media.state.value.busy)
            val failed = running.copy(status = OperationStatus.FAILED, errorCode = WireErrorCode.BUSY)
            media.operation(failed)
            assertEquals(failed, media.state.value.messageOperation)
            assertFalse(media.state.value.busy)
            media.observe(null)
            assertNull(media.state.value.messageOperation)
        }

    @Test fun refusedSendClearsOldMessageProofWithoutClearingCompletedOperation() =
        runTest {
            val sent = mutableListOf<McRequest>()
            var ready = true
            val media =
                MediaSessionController(backgroundScope) {
                    sent.add(it)
                    ready
                }
            media.observe(RemoteJson.parseSnapshot(RecoveryFixture.snapshot()))
            val command = RemoteCommand.DrivesMount(DrivesMountParams("volume"))
            media.command(command)
            val done = acceptMount(media, sent.last()).copy(status = OperationStatus.SUCCEEDED)
            media.operation(done)
            ready = false
            media.command(command)
            assertEquals(2, sent.size)
            assertNull(media.state.value.messageOperation)
            assertEquals(done, media.observedOperation)
            assertEquals(command, media.operationCommand)
            assertTrue(
                media.state.value.message
                    .contains("not ready"),
            )
        }

    @Test fun newAdmissionFailureAndTimeoutNeverInheritPriorOperationProof() =
        runTest {
            val sent = mutableListOf<McRequest>()
            val media = MediaSessionController(backgroundScope) { sent.add(it) }
            media.observe(RemoteJson.parseSnapshot(RecoveryFixture.snapshot()))
            val command = RemoteCommand.DrivesMount(DrivesMountParams("volume"))
            media.command(command)
            media.operation(acceptMount(media, sent.last()).copy(status = OperationStatus.SUCCEEDED))
            media.command(command)
            assertNull(media.state.value.messageOperation)
            media.receive(
                McReply.Failure(
                    sent.last().id,
                    ErrorPayload(WireErrorCode.NOT_FOUND, "Drive removed.", false, emptyList()),
                ),
            )
            assertNull(media.state.value.messageOperation)
            assertEquals("Drive removed.", media.state.value.message)
            media.command(command)
            runCurrent()
            advanceTimeBy(MEDIA_REQUEST_TIMEOUT_MS)
            runCurrent()
            assertNull(media.state.value.messageOperation)
            assertTrue(
                media.state.value.message
                    .contains("Acknowledgement missing"),
            )
            assertEquals(3, sent.size)
            assertTrue(media.state.value.busy)
        }

    private fun acceptMount(
        media: MediaSessionController,
        request: McRequest,
    ): OperationState {
        media.receive(
            McReply.Success(
                request.id,
                RemoteResult.DrivesMount(AcceptedResult(OperationAccepted.ACCEPTED, request.id)),
            ),
        )
        val running = OperationState(request.id, request.id, OperationStatus.RUNNING, cancellationRequested = false)
        media.operation(running)
        return running
    }
}
