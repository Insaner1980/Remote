package com.emmah.colordock.remote.ui.mediacenter

import com.emmah.colordock.remote.mediacenter.DriveWork
import com.emmah.colordock.remote.mediacenter.PendingCommand
import com.emmah.colordock.remote.mediacenter.RecoveryFixture
import com.emmah.colordock.remote.mediacenter.protocol.McReply
import com.emmah.colordock.remote.mediacenter.protocol.OperationState
import com.emmah.colordock.remote.mediacenter.protocol.OperationStatus
import com.emmah.colordock.remote.mediacenter.protocol.OperationsTopic
import com.emmah.colordock.remote.mediacenter.protocol.RemoteJson
import com.emmah.colordock.remote.mediacenter.protocol.RemoteResult
import com.emmah.colordock.remote.mediacenter.protocol.WireErrorCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveredDrivesTest {
    private val snapshot = RemoteJson.parseSnapshot(RecoveryFixture.snapshot())
    private val saved =
        PendingCommand(
            "server",
            "request",
            "epoch",
            "op",
            OperationStatus.RUNNING,
            pairedDeviceId = "phone",
            driveWork = DriveWork.EJECT,
            driveObserved = true,
        )
    private val running = OperationState("op", "request", OperationStatus.RUNNING, cancellationRequested = false)

    @Test fun cancellationNeedsFreshObservationAfterEveryDisconnect() {
        val recovery = RecoveredDrives()
        recovery.observe(snapshot, listOf(saved), "phone")
        assertTrue(recovery.state.value.isEmpty())
        recovery.receive(McReply.Success("lookup", RemoteResult.OperationsGet(running)))
        assertTrue(
            recovery.state.value
                .single()
                .running,
        )
        recovery.observe(null, listOf(saved), "phone")
        assertTrue(recovery.state.value.isEmpty())
        recovery.observe(snapshot, listOf(saved), "phone")
        assertTrue(recovery.state.value.isEmpty())
        recovery.observe(snapshot.copy(operations = OperationsTopic("1", listOf(running))), listOf(saved), "phone")
        assertTrue(
            recovery.state.value
                .single()
                .running,
        )
    }

    @Test fun wrongPhoneServerEpochRequestAndOperationCannotBeAdopted() {
        val recovery = RecoveredDrives()
        val attempts =
            listOf(
                saved.copy(pairedDeviceId = "other"),
                saved.copy(serverId = "other"),
                saved.copy(serverEpoch = "other"),
                saved.copy(driveWork = null),
                saved.copy(operationId = "other"),
            )
        attempts.forEach { record ->
            recovery.observe(snapshot, listOf(record), "phone")
            recovery.receive(McReply.Success("lookup", RemoteResult.OperationsGet(running)))
            assertTrue(recovery.state.value.isEmpty())
        }
        recovery.observe(snapshot, listOf(saved), "phone")
        recovery.receive(McReply.Success("lookup", RemoteResult.OperationsGet(running.copy(requestId = "other"))))
        assertTrue(recovery.state.value.isEmpty())
    }

    @Test fun terminalDoesNotRegressAndUncertifiedStatusDoesNotBecomeCompletion() {
        val recovery = RecoveredDrives()
        recovery.observe(snapshot, listOf(saved), "phone")
        val terminal = running.copy(status = OperationStatus.FAILED, errorCode = WireErrorCode.BUSY)
        recovery.receive(McReply.Success("lookup", RemoteResult.OperationsGet(terminal)))
        recovery.receive(McReply.Success("older", RemoteResult.OperationsGet(running)))
        assertEquals(
            terminal,
            recovery.state.value
                .single()
                .operation,
        )
        val falseCompletion = saved.copy(status = OperationStatus.SUCCEEDED, resolved = true, driveObserved = false)
        recovery.observe(snapshot, listOf(falseCompletion), "phone")
        assertTrue(recovery.state.value.isEmpty())
    }

    @Test fun reconstructedDriveControllerDisplaysPriorCompletionWithoutAssertingCurrentDriveSafety() {
        val sent = mutableListOf<com.emmah.colordock.remote.mediacenter.protocol.McRequest>()
        val controller = DrivesController { sent.add(it) }
        controller.recovery.observe(
            snapshot,
            listOf(saved.copy(status = OperationStatus.SUCCEEDED, resolved = true)),
            "phone",
        )
        val work =
            controller.recovery.state.value
                .single()
        assertTrue(work.message.contains("Earlier safe-removal operation completed"))
        assertFalse(work.message.contains("You can unplug"))
        assertFalse(work.running)
        assertTrue(sent.isEmpty())
    }
}
