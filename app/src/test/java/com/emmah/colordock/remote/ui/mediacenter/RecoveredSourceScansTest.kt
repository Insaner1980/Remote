package com.emmah.colordock.remote.ui.mediacenter

import com.emmah.colordock.remote.mediacenter.PendingCommand
import com.emmah.colordock.remote.mediacenter.RecoveryFixture
import com.emmah.colordock.remote.mediacenter.SourceScanWork
import com.emmah.colordock.remote.mediacenter.protocol.McReply
import com.emmah.colordock.remote.mediacenter.protocol.OperationState
import com.emmah.colordock.remote.mediacenter.protocol.OperationStatus
import com.emmah.colordock.remote.mediacenter.protocol.OperationsTopic
import com.emmah.colordock.remote.mediacenter.protocol.RemoteJson
import com.emmah.colordock.remote.mediacenter.protocol.RemoteResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveredSourceScansTest {
    @Test fun recoveredCancelNeedsCurrentNativeRunningObservationAndExactOwnerEpoch() {
        val record =
            PendingCommand(
                "server",
                "scan",
                "epoch",
                "op",
                OperationStatus.RUNNING,
                pairedDeviceId = "phone",
                sourceScanWork = SourceScanWork.SCAN,
            )
        val empty = RemoteJson.parseSnapshot(RecoveryFixture.snapshot())
        val operation = OperationState("op", "scan", OperationStatus.RUNNING, cancellationRequested = false)
        val running = empty.copy(operations = OperationsTopic("1", listOf(operation)))
        val restored = RecoveredSourceScans()
        restored.observe(empty, listOf(record), "phone")
        assertTrue(restored.state.value.isEmpty())
        restored.observe(running, listOf(record), "phone")
        assertEquals(listOf(operation), restored.state.value)
        restored.observe(null, listOf(record), "phone")
        assertTrue(restored.state.value.isEmpty())
        restored.observe(running, listOf(record), "different-phone")
        assertTrue(restored.state.value.isEmpty())
        restored.observe(running.copy(serverEpoch = "new-epoch"), listOf(record), "phone")
        assertTrue(restored.state.value.isEmpty())
    }

    @Test fun individualNativeLookupRestoresOnlyTaggedWorkAndTerminalIsNotRegressed() {
        val record =
            PendingCommand(
                "server",
                "scan",
                "epoch",
                "op",
                OperationStatus.RUNNING,
                pairedDeviceId = "phone",
                sourceScanWork = SourceScanWork.SCAN,
            )
        val snapshot = RemoteJson.parseSnapshot(RecoveryFixture.snapshot())
        val operation = OperationState("op", "scan", OperationStatus.RUNNING, cancellationRequested = false)
        val recovered = RecoveredSourceScans()
        recovered.observe(snapshot, listOf(record.copy(sourceScanWork = null)), "phone")
        recovered.receive(McReply.Success("lookup", RemoteResult.OperationsGet(operation)))
        assertTrue(recovered.state.value.isEmpty())
        recovered.observe(snapshot, listOf(record), "phone")
        recovered.receive(McReply.Success("lookup", RemoteResult.OperationsGet(operation)))
        assertEquals(listOf(operation), recovered.state.value)
        recovered.receive(
            McReply.Success("done", RemoteResult.OperationsGet(operation.copy(status = OperationStatus.CANCELLED))),
        )
        recovered.observe(snapshot.copy(operations = OperationsTopic("1", listOf(operation))), listOf(record), "phone")
        assertTrue(recovered.state.value.isEmpty())
        recovered.observe(snapshot, listOf(record), "other-phone")
        recovered.receive(McReply.Success("late", RemoteResult.OperationsGet(operation)))
        assertTrue(recovered.state.value.isEmpty())
    }
}
