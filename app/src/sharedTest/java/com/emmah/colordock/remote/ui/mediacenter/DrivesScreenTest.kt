package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.emmah.colordock.remote.mediacenter.DriveWork
import com.emmah.colordock.remote.mediacenter.PendingCommand
import com.emmah.colordock.remote.mediacenter.protocol.AcceptedResult
import com.emmah.colordock.remote.mediacenter.protocol.ActionKind
import com.emmah.colordock.remote.mediacenter.protocol.ActivityPreparation
import com.emmah.colordock.remote.mediacenter.protocol.Availability
import com.emmah.colordock.remote.mediacenter.protocol.CancellationAck
import com.emmah.colordock.remote.mediacenter.protocol.ConfirmMethod
import com.emmah.colordock.remote.mediacenter.protocol.ConfirmationPreview
import com.emmah.colordock.remote.mediacenter.protocol.Drive
import com.emmah.colordock.remote.mediacenter.protocol.DriveVolume
import com.emmah.colordock.remote.mediacenter.protocol.DrivesConfirmEjectParams
import com.emmah.colordock.remote.mediacenter.protocol.ErrorPayload
import com.emmah.colordock.remote.mediacenter.protocol.Expected
import com.emmah.colordock.remote.mediacenter.protocol.McReply
import com.emmah.colordock.remote.mediacenter.protocol.McRequest
import com.emmah.colordock.remote.mediacenter.protocol.OperationAccepted
import com.emmah.colordock.remote.mediacenter.protocol.OperationState
import com.emmah.colordock.remote.mediacenter.protocol.OperationStatus
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand
import com.emmah.colordock.remote.mediacenter.protocol.RemoteResult
import com.emmah.colordock.remote.mediacenter.protocol.WireErrorCode
import com.emmah.colordock.remote.ui.RemoteTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DrivesScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun portraitLandscapeAndLargeFontKeepOpaqueMountAndRemovalReachable() {
        val size = mutableStateOf(DpSize(360.dp, 640.dp))
        val font = mutableFloatStateOf(1f)
        val connected = mutableStateOf(true)
        var mounted = ""
        var removed = ""
        compose.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(size.value) then
                    DeviceConfigurationOverride.FontScale(font.floatValue),
            ) {
                RemoteTheme {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        DrivesScreen(
                            driveState().copy(connected = connected.value),
                            false,
                            DriveActions({}, { mounted = it }, { removed = it }),
                        )
                    }
                }
            }
        }
        listOf(
            DpSize(360.dp, 640.dp) to 1f,
            DpSize(640.dp, 360.dp) to 1f,
            DpSize(360.dp, 640.dp) to 2f,
        ).forEach { (dimensions, scale) ->
            compose.runOnIdle {
                size.value = dimensions
                font.floatValue = scale
            }
            compose
                .onNodeWithText("Connect")
                .performScrollTo()
                .assertIsDisplayed()
                .performClick()
            compose
                .onNodeWithContentDescription("Drive options for USB")
                .performScrollTo()
                .assertIsDisplayed()
                .performClick()
            compose.onNodeWithText("Eject").performClick()
        }
        compose.runOnIdle {
            assertEquals("opaque-volume", mounted)
            assertEquals("opaque-drive", removed)
            connected.value =
                false
        }
        compose.onNodeWithContentDescription("Drive options for USB").assertIsNotEnabled()
    }

    @Test fun targetSettingsShowsNativeSiblingImpactsAndConfirmsOnlyTheId() {
        val sent = mutableListOf<McRequest>()
        lateinit var media: MediaSessionController
        compose.setContent { DriveTarget({ sent.add(it) }, { media = it }) }
        compose.onNodeWithTag("settings-section-Media").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Drive options for USB").performScrollTo().performClick()
        compose.onNodeWithText("Eject").performClick()
        compose.runOnIdle {
            assertTrue(sent.last().command is RemoteCommand.DrivesPrepareEject)
            val preview =
                ConfirmationPreview(
                    "eject",
                    "phone",
                    ActionKind.EJECT_DRIVE,
                    listOf("USB", "Sibling volume", "Local playback"),
                    Expected(serverEpoch = "epoch"),
                    60000,
                    confirmMethod = ConfirmMethod.DRIVES_CONFIRM_EJECT,
                )
            media.receive(
                McReply.Success(
                    sent.last().id,
                    RemoteResult.DrivesPrepareEject(ActivityPreparation.ConfirmationRequired(preview)),
                ),
            )
        }
        compose.onNodeWithText("Sibling volume", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Local playback", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Confirm").performClick()
        compose.runOnIdle {
            val command = sent.last().command as RemoteCommand.DrivesConfirmEject
            assertEquals("eject", command.params.confirmationId)
        }
    }

    @Test fun localAuthorizationIsAnActionableTerminalMessage() {
        val sent = mutableListOf<McRequest>()
        lateinit var media: MediaSessionController
        compose.setContent { DriveTarget({ sent.add(it) }, { media = it }) }
        compose.onNodeWithTag("settings-section-Media").performScrollTo().performClick()
        compose.onNodeWithText("Connect").performScrollTo().performClick()
        compose.runOnIdle {
            val request = sent.last().id
            media.receive(
                McReply.Success(
                    request,
                    RemoteResult.DrivesMount(AcceptedResult(OperationAccepted.ACCEPTED, "mount-op")),
                ),
            )
            media.operation(
                OperationState(
                    "mount-op",
                    request,
                    OperationStatus.FAILED,
                    WireErrorCode.LOCAL_AUTHORIZATION_REQUIRED,
                    false,
                ),
            )
        }
        compose.onNodeWithText("Approve on computer", substring = true).assertIsDisplayed()
    }

    @Test fun disconnectedAcceptedEjectShowsRecoveredTerminalOutcomeInCanonicalSettings() {
        val sent = mutableListOf<McRequest>()
        val recovery = RecoveredDrives()
        lateinit var media: MediaSessionController
        compose.setContent {
            val work by recovery.state.collectAsState()
            DriveTarget({ sent.add(it) }, { media = it }, work)
        }
        compose.onNodeWithTag("settings-section-Media").performScrollTo().performClick()
        val cases =
            listOf(
                Triple(OperationStatus.SUCCEEDED, null, "Safe to unplug"),
                Triple(OperationStatus.FAILED, WireErrorCode.BUSY, "Close files on computer"),
                Triple(
                    OperationStatus.FAILED,
                    WireErrorCode.LOCAL_AUTHORIZATION_REQUIRED,
                    "Approve on computer",
                ),
                Triple(OperationStatus.OUTCOME_UNKNOWN, WireErrorCode.OUTCOME_UNKNOWN, "Not confirmed"),
            )
        cases.forEach { (status, code, message) ->
            compose.runOnIdle {
                val request = reconnectAcceptedDrive(media, sent, recovery)
                recovery.receive(
                    McReply.Success(
                        "lookup",
                        RemoteResult.OperationsGet(OperationState("eject-op", request, status, code, false)),
                    ),
                )
            }
            compose.onNodeWithText(message, substring = true).performScrollTo().assertIsDisplayed()
        }
        compose.runOnIdle {
            assertEquals(cases.size, sent.count { it.command is RemoteCommand.DrivesConfirmEject })
            assertFalse(sent.any { it.command is RemoteCommand.DrivesMount })
        }
    }

    @Test fun recoveredRunningDriveUsesNativeCancelIdAndAckDoesNotCompleteIt() {
        val sent = mutableListOf<McRequest>()
        val recovery = RecoveredDrives()
        lateinit var media: MediaSessionController
        var request = ""
        compose.setContent {
            val work by recovery.state.collectAsState()
            DriveTarget({ sent.add(it) }, { media = it }, work)
        }
        compose.onNodeWithTag("settings-section-Media").performScrollTo().performClick()
        compose.runOnIdle {
            request = reconnectAcceptedDrive(media, sent, recovery)
            assertTrue(recovery.state.value.isEmpty())
            recovery.receive(
                McReply.Success(
                    "lookup",
                    RemoteResult.OperationsGet(
                        OperationState("eject-op", request, OperationStatus.RUNNING, cancellationRequested = false),
                    ),
                ),
            )
        }
        compose.onNodeWithText("Cancel").performScrollTo().performClick()
        compose.runOnIdle {
            val cancel = sent.last().command as RemoteCommand.OperationsCancel
            assertEquals("eject-op", cancel.params.operationId)
            media.receive(McReply.Success(sent.last().id, RemoteResult.OperationsCancel(CancellationAck(true))))
            assertTrue(
                recovery.state.value
                    .single()
                    .running,
            )
            recovery.receive(
                McReply.Success(
                    "done",
                    RemoteResult.OperationsGet(
                        OperationState("eject-op", request, OperationStatus.CANCELLED, cancellationRequested = true),
                    ),
                ),
            )
        }
        compose.onNodeWithText("Working…").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(
                OperationStatus.CANCELLED,
                recovery.state.value
                    .single()
                    .operation.status,
            )
        }
        compose.onNodeWithText("Cancel").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, sent.count { it.command is RemoteCommand.DrivesConfirmEject }) }
    }

    @Test fun retainedDriveOutcomeDoesNotHideLaterAdmissionFailure() {
        val sent = mutableListOf<McRequest>()
        val recovered = mutableStateOf(emptyList<RecoveredDrive>())
        lateinit var media: MediaSessionController
        compose.setContent { DriveTarget({ sent.add(it) }, { media = it }, recovered.value) }
        compose.onNodeWithTag("settings-section-Media").performScrollTo().performClick()
        compose.onNodeWithText("Connect").performScrollTo().performClick()
        compose.runOnIdle {
            val request = sent.last().id
            media.receive(
                McReply.Success(
                    request,
                    RemoteResult.DrivesMount(AcceptedResult(OperationAccepted.ACCEPTED, "prior-mount")),
                ),
            )
            val done = OperationState("prior-mount", request, OperationStatus.SUCCEEDED, cancellationRequested = false)
            media.operation(done)
            recovered.value = listOf(RecoveredDrive(DriveWork.MOUNT, done))
        }
        compose.onNodeWithText("Earlier mount operation completed", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Connect").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(2, sent.count { it.command is RemoteCommand.DrivesMount })
            media.receive(
                McReply.Failure(sent.last().id, ErrorPayload(WireErrorCode.BUSY, "busy", true, emptyList())),
            )
        }
        compose
            .onNodeWithText("Close files on computer", substring = true)
            .assertIsDisplayed()
    }

    @Test fun retainedDriveOutcomeDoesNotHideLocalSendRefusal() {
        val sent = mutableListOf<McRequest>()
        val recovered = mutableStateOf(emptyList<RecoveredDrive>())
        var ready = true
        lateinit var media: MediaSessionController
        compose.setContent {
            DriveTarget({
                sent.add(it)
                ready
            }, { media = it }, recovered.value)
        }
        compose.onNodeWithTag("settings-section-Media").performScrollTo().performClick()
        compose.onNodeWithText("Connect").performScrollTo().performClick()
        compose.runOnIdle {
            val done = acceptMount(media, sent.last()).copy(status = OperationStatus.SUCCEEDED)
            media.operation(done)
            recovered.value = listOf(RecoveredDrive(DriveWork.MOUNT, done))
            ready = false
        }
        compose.onNodeWithText("Connect").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(2, sent.count { it.command is RemoteCommand.DrivesMount })
            assertEquals(recovered.value.single().operation, media.observedOperation)
        }
        compose
            .onNodeWithText("Try again", substring = true)
            .assertIsDisplayed()
        compose.onNodeWithText("Earlier mount operation completed", substring = true).assertDoesNotExist()
    }

    @Test fun sameNativeOperationHasOneCanonicalStatusOnlyWhereRecoveryIsRendered() {
        val sent = mutableListOf<McRequest>()
        val recovered = mutableStateOf(emptyList<RecoveredDrive>())
        lateinit var media: MediaSessionController
        lateinit var running: OperationState
        compose.setContent { DriveTarget({ sent.add(it) }, { media = it }, recovered.value) }
        compose.onNodeWithTag("settings-section-Media").performScrollTo().performClick()
        compose.onNodeWithText("Connect").performScrollTo().performClick()
        compose.runOnIdle {
            running = acceptMount(media, sent.last())
            recovered.value = listOf(RecoveredDrive(DriveWork.MOUNT, running))
        }
        compose.onNodeWithText("Drive operation running", substring = true).assertDoesNotExist()
        compose.onAllNodesWithText("Working…").assertCountEquals(1)
        compose.onAllNodesWithText("Cancel").assertCountEquals(1)
        listOf(MediaDestination.Remote, MediaDestination.Library).forEach { destination ->
            compose.runOnIdle { media.destination(destination) }
            compose.onNodeWithText("Working…").assertIsDisplayed()
            compose.onNodeWithText("Earlier drive operation is still running", substring = true).assertDoesNotExist()
        }
        compose.runOnIdle {
            media.destination(MediaDestination.Settings)
            val done = running.copy(status = OperationStatus.SUCCEEDED)
            recovered.value = listOf(RecoveredDrive(DriveWork.MOUNT, done))
            assertEquals(running, media.observedOperation)
            assertTrue(media.state.value.busy)
        }
        compose.onNodeWithTag("settings-section-Media").performScrollTo().performClick()
        compose.onNodeWithText("Drive operation running", substring = true).assertDoesNotExist()
        compose.onAllNodesWithText("Working…").assertCountEquals(0)
        compose.onNodeWithText("Cancel").assertDoesNotExist()
    }

    @Test fun activeControllerCancellationAckKeepsOneCanonicalRecoveredWaitingStatus() {
        val sent = mutableListOf<McRequest>()
        val recovered = mutableStateOf(emptyList<RecoveredDrive>())
        lateinit var media: MediaSessionController
        compose.setContent { DriveTarget({ sent.add(it) }, { media = it }, recovered.value) }
        compose.onNodeWithTag("settings-section-Media").performScrollTo().performClick()
        compose.onNodeWithText("Connect").performScrollTo().performClick()
        compose.runOnIdle {
            val waiting = acceptMount(media, sent.last()).copy(cancellationRequested = true)
            media.operation(waiting)
            recovered.value = listOf(RecoveredDrive(DriveWork.MOUNT, waiting))
            media.command(
                RemoteCommand.OperationsCancel(
                    com.emmah.colordock.remote.mediacenter.protocol
                        .OperationsCancelParams(waiting.operationId),
                ),
            )
            media.receive(McReply.Success(sent.last().id, RemoteResult.OperationsCancel(CancellationAck(true))))
            assertTrue(media.state.value.busy)
        }
        compose.onNodeWithText("Cancellation requested. Waiting for native completion.").assertDoesNotExist()
        compose.onAllNodesWithText("Stopping…").assertCountEquals(1)
        compose.onAllNodesWithText("Cancel").assertCountEquals(1)
    }

    private fun acceptMount(
        media: MediaSessionController,
        request: McRequest,
    ): OperationState {
        media.receive(
            McReply.Success(
                request.id,
                RemoteResult.DrivesMount(AcceptedResult(OperationAccepted.ACCEPTED, "mount-op")),
            ),
        )
        val running = OperationState("mount-op", request.id, OperationStatus.RUNNING, cancellationRequested = false)
        media.operation(running)
        return running
    }

    private fun reconnectAcceptedDrive(
        media: MediaSessionController,
        sent: List<McRequest>,
        recovery: RecoveredDrives,
    ): String {
        media.command(RemoteCommand.DrivesConfirmEject(DrivesConfirmEjectParams("confirmation")))
        val request = sent.last().id
        media.receive(
            McReply.Success(
                request,
                RemoteResult.DrivesConfirmEject(AcceptedResult(OperationAccepted.ACCEPTED, "eject-op")),
            ),
        )
        media.observe(null)
        recovery.observe(null, emptyList(), null)
        media.observe(phoneMediaSnapshot())
        val record =
            PendingCommand(
                "server",
                request,
                "epoch",
                "eject-op",
                OperationStatus.RUNNING,
                pairedDeviceId = "phone",
                driveWork = DriveWork.EJECT,
            )
        recovery.observe(phoneMediaSnapshot(), listOf(record), "phone")
        return request
    }

    @Composable
    private fun DriveTarget(
        send: (McRequest) -> Boolean,
        capture: (MediaSessionController) -> Unit,
        recovered: List<RecoveredDrive> = emptyList(),
    ) {
        val scope = rememberCoroutineScope()
        val media =
            remember {
                MediaSessionController(scope, send).apply {
                    observe(phoneMediaSnapshot())
                    destination(MediaDestination.Settings)
                }
            }
        capture(media)
        val state by media.state.collectAsState()
        val controllers =
            remember {
                MediaTargetControllers(
                    media,
                    LibraryViewModel(scope) { true },
                    RendererController(scope) { true },
                    AudioSettingsController { true },
                    drives = DrivesController { true },
                )
            }
        RemoteTheme {
            MediacenterTargetContent(
                MediaTargetState(
                    state,
                    LibraryState(),
                    AudioSettingsState(),
                    drives = driveState().copy(recovered = recovered),
                ),
                controllers,
                { null },
            )
        }
    }
}

private fun driveState() =
    DrivesState(
        items =
            listOf(
                Drive(
                    "opaque-drive",
                    "USB",
                    true,
                    listOf(DriveVolume("opaque-volume", "Movies", false, Availability.OFFLINE)),
                ),
            ),
        connected = true,
        message = "",
    )
