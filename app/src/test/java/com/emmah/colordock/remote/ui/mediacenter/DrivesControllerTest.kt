package com.emmah.colordock.remote.ui.mediacenter

import com.emmah.colordock.remote.mediacenter.DriveWork
import com.emmah.colordock.remote.mediacenter.PendingCommand
import com.emmah.colordock.remote.mediacenter.RecoveryFixture
import com.emmah.colordock.remote.mediacenter.protocol.AcceptedResult
import com.emmah.colordock.remote.mediacenter.protocol.ActionKind
import com.emmah.colordock.remote.mediacenter.protocol.ActivityPreparation
import com.emmah.colordock.remote.mediacenter.protocol.Availability
import com.emmah.colordock.remote.mediacenter.protocol.CancellationAck
import com.emmah.colordock.remote.mediacenter.protocol.ConfirmMethod
import com.emmah.colordock.remote.mediacenter.protocol.ConfirmationPreview
import com.emmah.colordock.remote.mediacenter.protocol.Drive
import com.emmah.colordock.remote.mediacenter.protocol.DriveList
import com.emmah.colordock.remote.mediacenter.protocol.DriveVolume
import com.emmah.colordock.remote.mediacenter.protocol.DrivesMountParams
import com.emmah.colordock.remote.mediacenter.protocol.DrivesPrepareEjectParams
import com.emmah.colordock.remote.mediacenter.protocol.Expected
import com.emmah.colordock.remote.mediacenter.protocol.McReply
import com.emmah.colordock.remote.mediacenter.protocol.McRequest
import com.emmah.colordock.remote.mediacenter.protocol.OperationAccepted
import com.emmah.colordock.remote.mediacenter.protocol.OperationState
import com.emmah.colordock.remote.mediacenter.protocol.OperationStatus
import com.emmah.colordock.remote.mediacenter.protocol.OperationsCancelParams
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand
import com.emmah.colordock.remote.mediacenter.protocol.RemoteJson
import com.emmah.colordock.remote.mediacenter.protocol.RemoteResult
import com.emmah.colordock.remote.mediacenter.protocol.WireErrorCode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrivesControllerTest {
    @Test fun nativeEjectPreviewUsesSingleConfirmationOwnerAndOpaqueConfirmId() =
        runTest {
            val sent = mutableListOf<McRequest>()
            val controller = MediaSessionController(backgroundScope) { sent.add(it) }
            controller.observe(RemoteJson.parseSnapshot(RecoveryFixture.snapshot()))
            controller.command(RemoteCommand.DrivesPrepareEject(DrivesPrepareEjectParams("drive")))
            val preview =
                ConfirmationPreview(
                    "eject-sheet",
                    "phone",
                    ActionKind.EJECT_DRIVE,
                    listOf("USB", "Movies", "Music", "Local playback"),
                    Expected(serverEpoch = "epoch"),
                    60000,
                    confirmMethod = ConfirmMethod.DRIVES_CONFIRM_EJECT,
                )
            controller.receive(
                McReply.Success(
                    sent.last().id,
                    RemoteResult.DrivesPrepareEject(ActivityPreparation.ConfirmationRequired(preview)),
                ),
            )
            assertEquals(preview, controller.state.value.preview)
            assertFalse(controller.state.value.busy)
            controller.confirm()
            val confirm = sent.last().command
            assertTrue(confirm is RemoteCommand.DrivesConfirmEject)
            assertEquals("eject-sheet", (confirm as RemoteCommand.DrivesConfirmEject).params.confirmationId)
        }

    @Test fun acceptedMountAndCancelAcknowledgementWaitForNativeOutcomeWithoutReplay() =
        runTest {
            val sent = mutableListOf<McRequest>()
            val controller = MediaSessionController(backgroundScope) { sent.add(it) }
            controller.observe(RemoteJson.parseSnapshot(RecoveryFixture.snapshot()))
            controller.command(RemoteCommand.DrivesMount(DrivesMountParams("volume")))
            val request = sent.last().id
            controller.receive(
                McReply.Success(
                    request,
                    RemoteResult.DrivesMount(AcceptedResult(OperationAccepted.ACCEPTED, "op")),
                ),
            )
            assertTrue(controller.state.value.busy)
            controller.operation(OperationState("op", request, OperationStatus.RUNNING, cancellationRequested = false))
            controller.command(RemoteCommand.OperationsCancel(OperationsCancelParams(operationId = "op")))
            controller.receive(McReply.Success(sent.last().id, RemoteResult.OperationsCancel(CancellationAck(true))))
            assertEquals(request, controller.operationRequest)
            assertTrue(controller.state.value.busy)
            controller.operation(OperationState("op", request, OperationStatus.SUCCEEDED, cancellationRequested = true))
            assertFalse(controller.state.value.busy)
            assertTrue(
                controller.state.value.message
                    .contains("Drive mounted"),
            )
            val count = sent.size
            controller.observe(null)
            controller.observe(RemoteJson.parseSnapshot(RecoveryFixture.snapshot()))
            assertEquals(count, sent.size)
        }

    @Test fun terminalFailuresExplainAuthorizationBusyAndStaleRecovery() =
        runTest {
            val command = RemoteCommand.DrivesMount(DrivesMountParams("volume"))
            assertTrue(driveFailure(command, WireErrorCode.LOCAL_AUTHORIZATION_REQUIRED)!!.contains("computer"))
            assertTrue(driveFailure(command, WireErrorCode.BUSY)!!.contains("Close its files"))
            assertTrue(driveFailure(command, WireErrorCode.CONFIRMATION_STALE)!!.contains("review removal"))
            assertEquals(null, driveFailure(null, WireErrorCode.BUSY))
        }

    @Test fun readRefreshDropsRepliesFromPreviousConnectionAndNeverMounts() {
        val sent = mutableListOf<McRequest>()
        val controller = DrivesController { sent.add(it) }
        controller.observe("epoch", "server", "1")
        val old = sent.last().id
        controller.observe(null, null, null)
        controller.observe("new-epoch", "server", "1")
        val page =
            DriveList(
                "1",
                listOf(
                    Drive(
                        "drive",
                        "USB",
                        true,
                        listOf(DriveVolume("volume", "Movies", false, Availability.OFFLINE)),
                    ),
                ),
            )
        controller.receive(McReply.Success(old, RemoteResult.DrivesList(page)))
        assertTrue(
            controller.state.value.items
                .isEmpty(),
        )
        controller.receive(McReply.Success(sent.last().id, RemoteResult.DrivesList(page)))
        assertEquals(
            "Movies",
            controller.state.value.items
                .single()
                .volumes
                .single()
                .label,
        )
        assertTrue(sent.all { it.command is RemoteCommand.DrivesList })
    }

    @Test fun reconstructedControllerShowsPriorNativeOutcomeWithoutSendingAnotherDriveCommand() {
        val sent = mutableListOf<McRequest>()
        val controller = DrivesController { sent.add(it) }
        val record =
            PendingCommand(
                "server",
                "request",
                "epoch",
                "op",
                OperationStatus.FAILED,
                true,
                "phone",
                driveWork = DriveWork.EJECT,
                driveErrorCode = WireErrorCode.LOCAL_AUTHORIZATION_REQUIRED,
                driveObserved = true,
            )
        controller.recovery.observe(RemoteJson.parseSnapshot(RecoveryFixture.snapshot()), listOf(record), "phone")
        assertTrue(
            controller.recovery.state.value
                .single()
                .message
                .contains("authorization on the computer"),
        )
        assertTrue(sent.isEmpty())
    }
}
