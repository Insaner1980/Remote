package com.emmah.colordock.remote.ui.mediacenter

import com.emmah.colordock.remote.mediacenter.protocol.AcceptedResult
import com.emmah.colordock.remote.mediacenter.protocol.DrivesConfirmEjectParams
import com.emmah.colordock.remote.mediacenter.protocol.DrivesMountParams
import com.emmah.colordock.remote.mediacenter.protocol.McReply
import com.emmah.colordock.remote.mediacenter.protocol.McRequest
import com.emmah.colordock.remote.mediacenter.protocol.OperationAccepted
import com.emmah.colordock.remote.mediacenter.protocol.OperationState
import com.emmah.colordock.remote.mediacenter.protocol.OperationStatus
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand
import com.emmah.colordock.remote.mediacenter.protocol.RemoteResult
import com.emmah.colordock.remote.mediacenter.protocol.WireErrorCode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PlainOperationFeedbackTest {
    @Test fun driveWorkAndCancellationAreNeverPresentedAsRetry() =
        runTest {
            val sent = mutableListOf<McRequest>()
            val media = MediaSessionController(this) { sent.add(it) }.apply { observe(phoneMediaSnapshot()) }
            media.command(RemoteCommand.DrivesMount(DrivesMountParams("volume")))
            val request = sent.last()
            media.receive(
                McReply.Success(request.id, RemoteResult.DrivesMount(AcceptedResult(OperationAccepted.ACCEPTED, "op"))),
            )
            val operation = OperationState("op", request.id, OperationStatus.RUNNING, cancellationRequested = false)
            media.operation(operation)
            assertEquals(
                "Working…",
                media.state.value.notice
                    ?.label,
            )
            media.operation(operation.copy(cancellationRequested = true))
            assertEquals(
                "Stopping…",
                media.state.value.notice
                    ?.label,
            )
        }

    @Test fun confirmedEjectKeepsTheEssentialSafeToUnplugResult() =
        runTest {
            val sent = mutableListOf<McRequest>()
            val media = MediaSessionController(this) { sent.add(it) }.apply { observe(phoneMediaSnapshot()) }
            media.command(RemoteCommand.DrivesConfirmEject(DrivesConfirmEjectParams("confirmation")))
            val request = sent.last()
            media.receive(
                McReply.Success(
                    request.id,
                    RemoteResult.DrivesConfirmEject(AcceptedResult(OperationAccepted.ACCEPTED, "op")),
                ),
            )
            media.operation(OperationState("op", request.id, OperationStatus.SUCCEEDED, cancellationRequested = false))
            assertEquals(
                "Safe to unplug",
                media.state.value.notice
                    ?.label,
            )
        }

    @Test fun requiredComputerApprovalRemainsAnActionableShortNotice() =
        runTest {
            val sent = mutableListOf<McRequest>()
            val media = MediaSessionController(this) { sent.add(it) }.apply { observe(phoneMediaSnapshot()) }
            media.command(RemoteCommand.DrivesMount(DrivesMountParams("volume")))
            val request = sent.last()
            media.receive(
                McReply.Success(request.id, RemoteResult.DrivesMount(AcceptedResult(OperationAccepted.ACCEPTED, "op"))),
            )
            media.operation(
                OperationState(
                    "op",
                    request.id,
                    OperationStatus.FAILED,
                    cancellationRequested = false,
                    errorCode = WireErrorCode.LOCAL_AUTHORIZATION_REQUIRED,
                ),
            )
            assertEquals(
                "Approve on computer",
                media.state.value.notice
                    ?.label,
            )
        }
}
