package com.emmah.colordock.remote.mediacenter

import com.emmah.colordock.remote.mediacenter.protocol.DrivesConfirmEjectParams
import com.emmah.colordock.remote.mediacenter.protocol.DrivesMountParams
import com.emmah.colordock.remote.mediacenter.protocol.DrivesPrepareEjectParams
import com.emmah.colordock.remote.mediacenter.protocol.Expected
import com.emmah.colordock.remote.mediacenter.protocol.McRequest
import com.emmah.colordock.remote.mediacenter.protocol.OperationState
import com.emmah.colordock.remote.mediacenter.protocol.OperationStatus
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand
import com.emmah.colordock.remote.mediacenter.protocol.WireErrorCode
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class DriveRecoveryMetadataTest {
    @Test fun onlyMountAndConfirmedEjectHaveClosedParameterFreeProvenance() {
        val file = Files.createTempDirectory("drive-tags").resolve("pending.json").toFile()
        val recovery = PendingRecovery(FilePendingCommandStore(file))
        val commands =
            listOf(
                RemoteCommand.DrivesMount(DrivesMountParams("private-volume")),
                RemoteCommand.DrivesConfirmEject(DrivesConfirmEjectParams("private-confirmation")),
                RemoteCommand.DrivesPrepareEject(DrivesPrepareEjectParams("private-drive")),
            )
        commands.forEachIndexed { index, command ->
            assertTrue(
                recovery.record(
                    McRequest("request-$index", command, Expected(serverEpoch = "epoch")),
                    recoverySaved().paired,
                    "epoch",
                ),
            )
        }
        val stored = FilePendingCommandStore(file).read()
        assertEquals(listOf(DriveWork.MOUNT, DriveWork.EJECT, null), stored.map { it.driveWork })
        assertTrue(stored.all { it.sourceScanWork == null && !it.driveObserved && it.driveErrorCode == null })
        assertFalse(file.readText().contains("private-"))
    }

    @Test fun typedTerminalRoundTripAndLegacyRecordsDoNotInventProvenance() {
        val file = Files.createTempDirectory("drive-roundtrip").resolve("pending.json").toFile()
        val store = FilePendingCommandStore(file)
        val saved =
            PendingCommand(
                "server",
                "request",
                "epoch",
                "op",
                OperationStatus.FAILED,
                true,
                "phone",
                driveWork = DriveWork.EJECT,
                driveErrorCode = WireErrorCode.BUSY,
                driveObserved = true,
            )
        store.write(listOf(saved))
        assertEquals(saved, store.read().single())
        val json = JSONArray(file.readText())
        val record = json.getJSONObject(0)
        listOf("driveWork", "driveErrorCode", "driveObserved").forEach(record::remove)
        file.writeText(json.toString())
        val legacy = store.read().single()
        assertNull(legacy.driveWork)
        assertNull(legacy.driveErrorCode)
        assertFalse(legacy.driveObserved)
        record.put("driveWork", "arbitrary-method").put("driveErrorCode", "arbitrary-error")
        file.writeText(json.toString())
        assertNull(store.read().single().driveWork)
        assertNull(store.read().single().driveErrorCode)
    }

    @Test fun localFinishCannotCertifyTerminalAfterEarlierRunningObservation() {
        val store = MemoryPending()
        val recovery = PendingRecovery(store)
        val pairing = recoverySaved().paired
        recovery.record(
            McRequest(
                "request",
                RemoteCommand.DrivesMount(DrivesMountParams("volume")),
                Expected(serverEpoch = "epoch"),
            ),
            pairing,
            "epoch",
        )
        val running = OperationState("op", "request", OperationStatus.RUNNING, cancellationRequested = false)
        recovery.observed(pairing, running)
        assertTrue(store.records.single().driveObserved)
        recovery.finish(pairing, "request", OperationStatus.SUCCEEDED)
        assertFalse(store.records.single().driveObserved)
        assertNull(store.records.single().driveErrorCode)
        recovery.observed(pairing, running.copy(status = OperationStatus.FAILED, errorCode = WireErrorCode.BUSY))
        assertTrue(store.records.single().driveObserved)
        recovery.observed(pairing, running.copy(status = OperationStatus.SUCCEEDED))
        recovery.finish(pairing, "request", OperationStatus.SUCCEEDED)
        assertEquals(OperationStatus.FAILED, store.records.single().status)
        assertEquals(WireErrorCode.BUSY, store.records.single().driveErrorCode)
    }

    @Test fun mismatchedOwnerOrOperationCannotSupplyNativeError() {
        val store = MemoryPending()
        val recovery = PendingRecovery(store)
        val pairing = recoverySaved().paired
        recovery.record(
            McRequest(
                "request",
                RemoteCommand.DrivesMount(DrivesMountParams("volume")),
                Expected(serverEpoch = "epoch"),
            ),
            pairing,
            "epoch",
        )
        recovery.accepted(pairing, "request", "accepted-op")
        val failed = OperationState("accepted-op", "request", OperationStatus.FAILED, WireErrorCode.BUSY, false)
        recovery.observed(pairing.copy(deviceId = "other-phone"), failed)
        recovery.observed(pairing.copy(serverDeviceId = "other-server"), failed)
        assertFalse(store.records.single().driveObserved)
        assertTrue(runCatching { recovery.observed(pairing, failed.copy(operationId = "wrong-op")) }.isFailure)
        assertFalse(store.records.single().driveObserved)
    }

    @Test fun driveMetadataKeepsCapacityByteLimitsAndForgetCleanup() {
        val file = Files.createTempDirectory("drive-limits").resolve("pending.json").toFile()
        val store = FilePendingCommandStore(file)
        val record = PendingCommand("server", "request", "epoch", pairedDeviceId = "phone", driveWork = DriveWork.EJECT)
        store.write(List(FilePendingCommandStore.MAX_COMMANDS) { record.copy(requestId = "request-$it") })
        assertEquals(FilePendingCommandStore.MAX_COMMANDS, store.read().size)
        assertTrue(runCatching { store.write(List(FilePendingCommandStore.MAX_COMMANDS + 1) { record }) }.isFailure)
        PendingRecovery(store).forget("server")
        assertTrue(store.read().isEmpty())
        file.writeBytes(ByteArray(MAX_FILE_BYTES + 1))
        assertTrue(runCatching { store.read() }.isFailure)
    }

    private companion object {
        const val MAX_FILE_BYTES = 65536
    }
}
