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
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.emmah.colordock.remote.mediacenter.protocol.AcceptedResult
import com.emmah.colordock.remote.mediacenter.protocol.ActionKind
import com.emmah.colordock.remote.mediacenter.protocol.ActivityPreparation
import com.emmah.colordock.remote.mediacenter.protocol.Availability
import com.emmah.colordock.remote.mediacenter.protocol.ConfirmMethod
import com.emmah.colordock.remote.mediacenter.protocol.ConfirmationPreview
import com.emmah.colordock.remote.mediacenter.protocol.Drive
import com.emmah.colordock.remote.mediacenter.protocol.DriveVolume
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
                .onNodeWithText("Mount Movies")
                .performScrollTo()
                .assertIsDisplayed()
                .performClick()
            compose
                .onNodeWithText("Safely remove USB")
                .performScrollTo()
                .assertIsDisplayed()
                .performClick()
        }
        compose.runOnIdle {
            assertEquals("opaque-volume", mounted)
            assertEquals("opaque-drive", removed)
            connected.value =
                false
        }
        compose.onNodeWithText("Safely remove USB").assertIsNotEnabled()
    }

    @Test fun targetSettingsShowsNativeSiblingImpactsAndConfirmsOnlyTheId() {
        val sent = mutableListOf<McRequest>()
        lateinit var media: MediaSessionController
        compose.setContent { DriveTarget({ sent.add(it) }, { media = it }) }
        compose.onNodeWithText("Safely remove USB").performScrollTo().performClick()
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
        compose.onNodeWithText("Mount Movies").performScrollTo().performClick()
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
        compose.onNodeWithText("Use the computer to authorize", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Composable
    private fun DriveTarget(
        send: (McRequest) -> Boolean,
        capture: (MediaSessionController) -> Unit,
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
                MediaTargetState(state, LibraryState(), AudioSettingsState(), drives = driveState()),
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
