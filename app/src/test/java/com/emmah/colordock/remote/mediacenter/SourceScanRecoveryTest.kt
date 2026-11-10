package com.emmah.colordock.remote.mediacenter

import com.emmah.colordock.remote.mediacenter.protocol.Empty
import com.emmah.colordock.remote.mediacenter.protocol.Expected
import com.emmah.colordock.remote.mediacenter.protocol.FoldersConfirmAddParams
import com.emmah.colordock.remote.mediacenter.protocol.McRequest
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand
import com.emmah.colordock.remote.mediacenter.protocol.SourcesScanParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class SourceScanRecoveryTest {
    @Test fun closedScanTagSurvivesStorageAndLegacyRemainsUntagged() {
        val file = Files.createTempDirectory("scan-recovery").resolve("pending.json").toFile()
        val store = FilePendingCommandStore(file)
        val tagged =
            PendingCommand("server", "request", "epoch", pairedDeviceId = "phone", sourceScanWork = SourceScanWork.SCAN)
        store.write(listOf(tagged))
        assertEquals(tagged, FilePendingCommandStore(file).read().single())
        val legacyJson = org.json.JSONArray(file.readText())
        legacyJson.getJSONObject(0).remove("sourceScanWork")
        file.writeText(legacyJson.toString())
        val legacy = store.read().single()
        assertNull(legacy.sourceScanWork)
    }

    @Test fun onlyActualDurableScanWorkGetsProvenanceBeforeSend() {
        val store = MemoryPending()
        val recovery = PendingRecovery(store)
        val pairing = recoverySaved().paired
        val commands =
            listOf(
                RemoteCommand.SourcesScan(SourcesScanParams("source")),
                RemoteCommand.FoldersConfirmAdd(FoldersConfirmAddParams("confirmation")),
            )
        commands.forEachIndexed { index, command ->
            recovery.record(
                McRequest("scan-$index", command, Expected(serverEpoch = "epoch", sourcesRevision = "0")),
                pairing,
                "epoch",
            )
        }
        assertEquals(listOf(SourceScanWork.SCAN, SourceScanWork.SCAN), store.records.map { it.sourceScanWork })
        recovery.record(
            McRequest(
                "other",
                RemoteCommand.QueueNext(Empty),
                Expected(serverEpoch = "epoch", sessionId = "session", queueRevision = "0"),
            ),
            pairing,
            "epoch",
        )
        assertNull(store.records.last().sourceScanWork)
        recovery.forget("server")
        assertTrue(store.records.isEmpty())
    }

    @Test fun taggedMetadataKeepsExistingCapacityAndSizeLimits() {
        val file = Files.createTempDirectory("scan-limits").resolve("pending.json").toFile()
        val store = FilePendingCommandStore(file)
        val record =
            PendingCommand("server", "request", "epoch", pairedDeviceId = "phone", sourceScanWork = SourceScanWork.SCAN)
        store.write(List(FilePendingCommandStore.MAX_COMMANDS) { record.copy(requestId = "request-$it") })
        assertEquals(FilePendingCommandStore.MAX_COMMANDS, store.read().size)
        assertTrue(runCatching { store.write(List(FilePendingCommandStore.MAX_COMMANDS + 1) { record }) }.isFailure)
        file.writeBytes(ByteArray(MAX_FILE_BYTES + 1))
        assertTrue(runCatching { store.read() }.isFailure)
    }

    private companion object {
        const val MAX_FILE_BYTES = 65536
    }
}
