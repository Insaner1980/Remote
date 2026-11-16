package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.emmah.colordock.remote.mediacenter.PendingCommand
import com.emmah.colordock.remote.mediacenter.SourceScanWork
import com.emmah.colordock.remote.mediacenter.protocol.AcceptedResult
import com.emmah.colordock.remote.mediacenter.protocol.Availability
import com.emmah.colordock.remote.mediacenter.protocol.McReply
import com.emmah.colordock.remote.mediacenter.protocol.McRequest
import com.emmah.colordock.remote.mediacenter.protocol.MediaKind
import com.emmah.colordock.remote.mediacenter.protocol.OperationAccepted
import com.emmah.colordock.remote.mediacenter.protocol.OperationState
import com.emmah.colordock.remote.mediacenter.protocol.OperationStatus
import com.emmah.colordock.remote.mediacenter.protocol.OperationsTopic
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand
import com.emmah.colordock.remote.mediacenter.protocol.RemoteResult
import com.emmah.colordock.remote.mediacenter.protocol.Source
import com.emmah.colordock.remote.ui.RemoteTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SourcesScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun runningNativeScanExposesWorkingCancelThroughTargetSettings() {
        val sent = mutableListOf<McRequest>()
        lateinit var media: MediaSessionController
        compose.setContent { ScanTarget({ sent.add(it) }, capture = { media = it }) }
        compose.onNodeWithTag("settings-section-Media").performScrollTo().performClick()
        compose.onNodeWithText("Scan Music").performScrollTo().performClick()
        compose.runOnIdle {
            val scan = sent.last().id
            media.receive(
                McReply.Success(scan, RemoteResult.SourcesScan(AcceptedResult(OperationAccepted.ACCEPTED, "scan-op"))),
            )
            media.operation(OperationState("scan-op", scan, OperationStatus.RUNNING, cancellationRequested = false))
        }
        compose
            .onNodeWithText("Cancel scan")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        compose.runOnIdle { assertTrue(sent.last().command is RemoteCommand.SourcesCancelScan) }
    }

    @Test fun recoveredScanCancelUsesNativeOperationFromTargetSettings() {
        val sent = mutableListOf<McRequest>()
        val recovery = RecoveredSourceScans()
        val operation =
            OperationState("recovered-op", "original", OperationStatus.RUNNING, cancellationRequested = false)
        val pending =
            PendingCommand(
                "server",
                "original",
                "epoch",
                "recovered-op",
                pairedDeviceId = "phone",
                sourceScanWork = SourceScanWork.SCAN,
            )
        recovery.observe(
            phoneMediaSnapshot().copy(operations = OperationsTopic("1", listOf(operation))),
            listOf(pending),
            "phone",
        )
        compose.setContent {
            val scans by recovery.state.collectAsState()
            ScanTarget({ sent.add(it) }, capture = {}, recovered = scans)
        }
        compose.onNodeWithTag("settings-section-Media").performScrollTo().performClick()
        compose.onNodeWithText("Cancel recovered scan").performScrollTo().performClick()
        compose.runOnIdle {
            val command = sent.single().command as RemoteCommand.SourcesCancelScan
            assertTrue(command.params.operationId == "recovered-op")
            recovery.receive(
                McReply.Success(
                    "lookup",
                    RemoteResult.OperationsGet(operation.copy(status = OperationStatus.CANCELLED)),
                ),
            )
        }
        compose.onNodeWithText("Cancel recovered scan").assertDoesNotExist()
    }

    @Composable
    private fun ScanTarget(
        send: (McRequest) -> Boolean,
        capture: (MediaSessionController) -> Unit,
        recovered: List<OperationState> = emptyList(),
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
                    LibraryViewModel(scope) {
                        true
                    },
                    RendererController(scope) {
                        true
                    },
                    AudioSettingsController { true },
                    FolderController { true },
                    SourcesController { true },
                )
            }
        RemoteTheme {
            MediacenterTargetContent(
                MediaTargetState(
                    state,
                    LibraryState(),
                    AudioSettingsState(),
                    sources =
                        SourcesState(
                            items =
                                listOf(
                                    Source(
                                        "music",
                                        "Music",
                                        listOf(MediaKind.MUSIC),
                                        Availability.AVAILABLE,
                                        false,
                                    ),
                                ),
                            connected = true,
                            recoveredScans = recovered,
                        ),
                ),
                controllers,
                { null },
            )
        }
    }
}
