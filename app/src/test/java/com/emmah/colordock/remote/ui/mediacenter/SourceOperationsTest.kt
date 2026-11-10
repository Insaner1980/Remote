package com.emmah.colordock.remote.ui.mediacenter

import com.emmah.colordock.remote.mediacenter.RecoveryFixture
import com.emmah.colordock.remote.mediacenter.protocol.AcceptedResult
import com.emmah.colordock.remote.mediacenter.protocol.CancellationAck
import com.emmah.colordock.remote.mediacenter.protocol.McReply
import com.emmah.colordock.remote.mediacenter.protocol.McRequest
import com.emmah.colordock.remote.mediacenter.protocol.OperationAccepted
import com.emmah.colordock.remote.mediacenter.protocol.OperationState
import com.emmah.colordock.remote.mediacenter.protocol.OperationStatus
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand
import com.emmah.colordock.remote.mediacenter.protocol.RemoteJson
import com.emmah.colordock.remote.mediacenter.protocol.RemoteResult
import com.emmah.colordock.remote.mediacenter.protocol.SourcesCancelScanParams
import com.emmah.colordock.remote.mediacenter.protocol.SourcesScanParams
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceOperationsTest {
    @Test fun cancelAcknowledgementDoesNotCompleteOrReplaceNativeScan() =
        runTest {
            val sent = mutableListOf<McRequest>()
            val controller = MediaSessionController(backgroundScope) { sent.add(it) }
            controller.observe(RemoteJson.parseSnapshot(RecoveryFixture.snapshot()))
            assertTrue(controller.command(RemoteCommand.SourcesScan(SourcesScanParams("source"))))
            val scan = sent.last().id
            val accepted = RemoteResult.SourcesScan(AcceptedResult(OperationAccepted.ACCEPTED, "scan-op"))
            controller.receive(McReply.Success(scan, accepted))
            controller.operation(
                OperationState("scan-op", scan, OperationStatus.RUNNING, cancellationRequested = false),
            )
            assertTrue(controller.command(RemoteCommand.SourcesCancelScan(SourcesCancelScanParams("scan-op"))))
            controller.receive(McReply.Success(sent.last().id, RemoteResult.SourcesCancelScan(CancellationAck(true))))
            assertEquals(scan, controller.operationRequest)
            assertTrue(controller.state.value.busy)
            controller.operation(
                OperationState("scan-op", scan, OperationStatus.CANCELLED, cancellationRequested = true),
            )
            assertFalse(controller.state.value.busy)
        }
}
