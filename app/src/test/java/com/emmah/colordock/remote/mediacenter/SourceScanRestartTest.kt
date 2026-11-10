package com.emmah.colordock.remote.mediacenter

import com.emmah.colordock.remote.mediacenter.protocol.Expected
import com.emmah.colordock.remote.mediacenter.protocol.FoldersConfirmAddParams
import com.emmah.colordock.remote.mediacenter.protocol.McRequest
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand
import com.emmah.colordock.remote.mediacenter.protocol.SourcesCancelScanParams
import com.emmah.colordock.remote.mediacenter.protocol.SourcesScanParams
import com.emmah.colordock.remote.ui.mediacenter.RecoveredSourceScans
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
class SourceScanRestartTest {
    @Test fun scanCanBeCancelledAfterProcessRestartWithoutMutationReplay() =
        runTest {
            restart(RemoteCommand.SourcesScan(SourcesScanParams("source")))
        }

    @Test fun initialAddScanCanBeCancelledAfterProcessRestartWithoutAddingAgain() =
        runTest {
            restart(RemoteCommand.FoldersConfirmAdd(FoldersConfirmAddParams("confirmation")))
        }

    private fun TestScope.restart(command: RemoteCommand) {
        val file = Files.createTempDirectory("scan-process").resolve("pending.json").toFile()
        persistScan(file, command)
        val control = RecoveryControl()
        val restored = MediacenterRepository(control, backgroundScope, FilePendingCommandStore(file)) { 0.5 }
        val scans = RecoveredSourceScans()
        backgroundScope.launch { restored.replies.collect { scans.receive(it) } }
        restored.connect(recoverySaved(), RecoveryRoute())
        runCurrent()
        control.native()
        runCurrent()
        scans.observe(restored.snapshot.value, restored.pending.value, restored.pairedDeviceId)
        assertTrue(scans.state.value.isEmpty())
        val lookup = control.sent.last { it.command is RemoteCommand.OperationsGet }
        control.emit(
            """{"v":1,"id":"${lookup.id}","ok":true,"result":{"operationId":"scan-op",""" +
                """"requestId":"scan-request","status":"running","cancellationRequested":false}}""",
        )
        runCurrent()
        assertEquals(
            "scan-op",
            scans.state.value
                .single()
                .operationId,
        )
        assertFalse(
            control.sent.any {
                it.command is RemoteCommand.SourcesScan ||
                    it.command is RemoteCommand.FoldersConfirmAdd
            },
        )
        assertTrue(
            restored.send(
                McRequest(
                    "cancel-restored",
                    RemoteCommand.SourcesCancelScan(SourcesCancelScanParams("scan-op")),
                    Expected(serverEpoch = "epoch"),
                ),
            ),
        )
        assertEquals(1, control.sent.count { it.command is RemoteCommand.SourcesCancelScan })
        restored.forget("server")
        runCurrent()
        assertTrue(restored.pending.value.isEmpty())
        assertTrue(FilePendingCommandStore(file).read().isEmpty())
    }

    private fun TestScope.persistScan(
        file: File,
        command: RemoteCommand,
    ) {
        val old = RecoveryControl()
        val first = MediacenterRepository(old, backgroundScope, FilePendingCommandStore(file)) { 0.5 }
        first.connect(recoverySaved(), RecoveryRoute())
        runCurrent()
        old.native()
        runCurrent()
        assertTrue(
            first.send(McRequest("scan-request", command, Expected(serverEpoch = "epoch", sourcesRevision = "0"))),
        )
        old.emit("""{"v":1,"id":"scan-request","ok":true,"result":{"status":"accepted","operationId":"scan-op"}}""")
        runCurrent()
        first.disconnect()
    }
}
