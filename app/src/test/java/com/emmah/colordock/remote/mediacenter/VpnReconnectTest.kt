package com.emmah.colordock.remote.mediacenter

import com.emmah.colordock.remote.mediacenter.protocol.Empty
import com.emmah.colordock.remote.mediacenter.protocol.Expected
import com.emmah.colordock.remote.mediacenter.protocol.McRequest
import com.emmah.colordock.remote.mediacenter.protocol.OperationStatus
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand
import com.emmah.colordock.remote.mediacenter.protocol.VpnConfirmChangeParams
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VpnReconnectTest {
    @Test fun droppedVpnChangeQueriesTheOriginalRequestAndNeverChangesAgain() =
        runTest {
            val control = RecoveryControl()
            val journal = MemoryPending()
            val repository = MediacenterRepository(control, backgroundScope, journal) { 0.5 }
            repository.connect(recoverySaved(), RecoveryRoute())
            runCurrent()
            control.native()
            runCurrent()
            val request =
                McRequest(
                    "vpn-request",
                    RemoteCommand.VpnConfirmChange(VpnConfirmChangeParams("preview")),
                    Expected(serverEpoch = "epoch"),
                )
            assertTrue(repository.send(request))
            control.state.value = MediacenterConnectionState.Failed
            runCurrent()
            advanceTimeBy(1000)
            runCurrent()
            control.native()
            runCurrent()
            val lookup = control.sent.last { it.command is RemoteCommand.OperationsGet }
            assertEquals("vpn-request", (lookup.command as RemoteCommand.OperationsGet).params.requestId)
            control.emit(
                """{"v":1,"id":"${lookup.id}","ok":true,
                "result":{"operationId":"vpn-operation","requestId":"vpn-request",
                "status":"outcome_unknown","errorCode":"outcome_unknown","cancellationRequested":false}}""",
            )
            runCurrent()
            assertEquals(
                OperationStatus.OUTCOME_UNKNOWN,
                repository.pending.value
                    .single()
                    .status,
            )
            assertEquals(1, control.sent.count { it.command is RemoteCommand.VpnConfirmChange })
            assertTrue(journal.records.none { it.sourceScanWork != null || it.driveWork != null })
        }

    @Test fun typedSlowProviderReplyKeepsTheAuthenticatedConnectionReady() =
        runTest {
            val control = RecoveryControl()
            val repository = MediacenterRepository(control, backgroundScope, MemoryPending()) { 0.5 }
            repository.connect(recoverySaved(), RecoveryRoute())
            runCurrent()
            control.native()
            runCurrent()
            assertTrue(repository.send(McRequest("settings", RemoteCommand.SettingsGet(Empty))))
            assertTrue(repository.send(McRequest("vpn", RemoteCommand.VpnState(Empty))))
            control.emit(
                """{"v":1,"id":"settings","ok":true,
                "result":{"revision":"1","theme":"dark","startAtLogin":false}}""",
            )
            control.emit(
                """{"v":1,"id":"vpn","ok":false,
                "error":{"code":"unavailable","message":"VPN read timed out","retryable":true,"refresh":[]}}""",
            )
            runCurrent()
            assertEquals(MediacenterConnectionState.Connected, repository.state.value)
            assertTrue(repository.pending.value.isEmpty())
            assertEquals(1, control.connections.size)
        }
}
