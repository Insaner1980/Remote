package com.emmah.colordock.remote.mediacenter

import com.emmah.colordock.remote.mediacenter.protocol.DrivesConfirmEjectParams
import com.emmah.colordock.remote.mediacenter.protocol.Expected
import com.emmah.colordock.remote.mediacenter.protocol.McRequest
import com.emmah.colordock.remote.mediacenter.protocol.OperationStatus
import com.emmah.colordock.remote.mediacenter.protocol.OperationsCancelParams
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand
import com.emmah.colordock.remote.mediacenter.protocol.WireErrorCode
import com.emmah.colordock.remote.ui.mediacenter.RecoveredDrives
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
class DriveRecoveryTest {
    @Test fun disconnectBeforeCompletionRetainsAuthoritativeBusyReasonWithoutEjectReplay() =
        runTest {
            val session = DriveRecoverySession(this)
            session.result(OperationStatus.FAILED, WireErrorCode.BUSY)
            assertTrue(
                session.projection.state.value
                    .single()
                    .message
                    .contains("Close its files"),
            )
            val record = JSONArray(session.file.readText()).getJSONObject(0)
            assertEquals("busy", record.optString("driveErrorCode"))
            assertEquals("eject", record.optString("driveWork"))
            assertTrue(record.optBoolean("driveObserved"))
            session.assertOneMutation()
        }

    @Test fun terminalSuccessAuthorizationAndUnknownSurviveControllerReconstruction() =
        runTest {
            val cases =
                listOf(
                    Triple(OperationStatus.SUCCEEDED, null, "Earlier safe-removal operation completed"),
                    Triple(
                        OperationStatus.FAILED,
                        WireErrorCode.LOCAL_AUTHORIZATION_REQUIRED,
                        "authorization on the computer",
                    ),
                    Triple(OperationStatus.OUTCOME_UNKNOWN, WireErrorCode.OUTCOME_UNKNOWN, "outcome is unknown"),
                )
            cases.forEach { (status, error, message) ->
                val session = DriveRecoverySession(this)
                session.result(status, error)
                val recreated = RecoveredDrives()
                recreated.observe(
                    session.restored.snapshot.value,
                    FilePendingCommandStore(session.file).read(),
                    "phone",
                )
                assertTrue(
                    recreated.state.value
                        .single()
                        .message
                        .contains(message),
                )
                session.assertOneMutation()
            }
        }

    @Test fun runningRecoveryCancelsNativeIdAndWaitsForTerminalWithoutReplayingEject() =
        runTest {
            val session = DriveRecoverySession(this)
            assertTrue(
                session.projection.state.value
                    .isEmpty(),
            )
            session.result(OperationStatus.RUNNING)
            val running =
                session.projection.state.value
                    .single()
            assertTrue(running.running)
            assertTrue(
                session.restored.send(
                    McRequest(
                        "cancel",
                        RemoteCommand.OperationsCancel(
                            OperationsCancelParams(operationId = running.operation.operationId),
                        ),
                        Expected(serverEpoch = "epoch"),
                    ),
                ),
            )
            session.control.emit("""{"v":1,"id":"cancel","ok":true,"result":{"cancellationRequested":true}}""")
            runCurrent()
            assertFalse(
                session.restored.pending.value
                    .first { it.requestId == "eject-request" }
                    .resolved,
            )
            assertTrue(
                session.projection.state.value
                    .single()
                    .running,
            )
            advanceTimeBy(2000)
            runCurrent()
            session.result(OperationStatus.CANCELLED)
            assertFalse(
                session.projection.state.value
                    .single()
                    .running,
            )
            assertEquals(1, session.control.sent.count { it.command is RemoteCommand.OperationsCancel })
            session.assertOneMutation()
            session.restored.forget("server")
            runCurrent()
            assertTrue(FilePendingCommandStore(session.file).read().isEmpty())
        }
}

@OptIn(ExperimentalCoroutinesApi::class)
private class DriveRecoverySession(
    private val scope: TestScope,
) {
    val file = Files.createTempDirectory("drive-recovery").resolve("pending.json").toFile()
    private val old = RecoveryControl()
    val control = RecoveryControl()
    val restored: MediacenterRepository
    val projection = RecoveredDrives()

    init {
        val first = MediacenterRepository(old, scope.backgroundScope, FilePendingCommandStore(file)) { 0.5 }
        first.connect(recoverySaved(), RecoveryRoute())
        scope.runCurrent()
        old.native()
        scope.runCurrent()
        check(
            first.send(
                McRequest(
                    "eject-request",
                    RemoteCommand.DrivesConfirmEject(DrivesConfirmEjectParams("confirmation")),
                    Expected(serverEpoch = "epoch"),
                ),
            ),
        )
        old.emit(
            """{"v":1,"id":"eject-request","ok":true,"result":{"status":"accepted",""" +
                """"operationId":"eject-op"}}""",
        )
        scope.runCurrent()
        first.disconnect()
        scope.runCurrent()
        restored = MediacenterRepository(control, scope.backgroundScope, FilePendingCommandStore(file)) { 0.5 }
        scope.backgroundScope.launch { restored.replies.collect { projection.receive(it) } }
        restored.connect(recoverySaved(), RecoveryRoute())
        scope.runCurrent()
        control.native()
        scope.runCurrent()
        projection.observe(restored.snapshot.value, restored.pending.value, restored.pairedDeviceId)
    }

    fun result(
        status: OperationStatus,
        error: WireErrorCode? = null,
    ) {
        val lookup = control.sent.last { it.command is RemoteCommand.OperationsGet }
        val result =
            JSONObject()
                .put("operationId", "eject-op")
                .put("requestId", "eject-request")
                .put("status", status.wire)
                .put("errorCode", error?.wire)
                .put("cancellationRequested", false)
        control.emit(
            JSONObject()
                .put("v", 1)
                .put("id", lookup.id)
                .put("ok", true)
                .put("result", result)
                .toString(),
        )
        scope.runCurrent()
        projection.observe(restored.snapshot.value, restored.pending.value, restored.pairedDeviceId)
    }

    fun assertOneMutation() {
        assertEquals(1, (old.sent + control.sent).count { it.command is RemoteCommand.DrivesConfirmEject })
        assertFalse(control.sent.any { it.command is RemoteCommand.DrivesMount })
    }
}
