package com.emmah.colordock.remote.ui.mediacenter

import com.emmah.colordock.remote.mediacenter.RecoveryFixture
import com.emmah.colordock.remote.mediacenter.protocol.AcceptedResult
import com.emmah.colordock.remote.mediacenter.protocol.ActionKind
import com.emmah.colordock.remote.mediacenter.protocol.ActivityPreparation
import com.emmah.colordock.remote.mediacenter.protocol.ConfirmMethod
import com.emmah.colordock.remote.mediacenter.protocol.ConfirmationPreview
import com.emmah.colordock.remote.mediacenter.protocol.Expected
import com.emmah.colordock.remote.mediacenter.protocol.McReply
import com.emmah.colordock.remote.mediacenter.protocol.McRequest
import com.emmah.colordock.remote.mediacenter.protocol.OperationAccepted
import com.emmah.colordock.remote.mediacenter.protocol.OperationState
import com.emmah.colordock.remote.mediacenter.protocol.OperationStatus
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand
import com.emmah.colordock.remote.mediacenter.protocol.RemoteJson
import com.emmah.colordock.remote.mediacenter.protocol.RemoteResult
import com.emmah.colordock.remote.mediacenter.protocol.SettingsEndSessionParams
import com.emmah.colordock.remote.mediacenter.protocol.SettingsStartAtLoginSetParams
import com.emmah.colordock.remote.mediacenter.protocol.SettingsState
import com.emmah.colordock.remote.mediacenter.protocol.Theme
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LifetimeSettingsTest {
    @Test fun endSessionUsesNativeConfirmationAndNeverSubmitsQuit() =
        runTest {
            val sent = mutableListOf<McRequest>()
            val media = MediaSessionController(backgroundScope) { sent.add(it) }
            media.observe(RemoteJson.parseSnapshot(RecoveryFixture.snapshot()))
            media.command(RemoteCommand.SettingsEndSession(SettingsEndSessionParams()))
            val preview =
                ConfirmationPreview(
                    "end-preview",
                    "phone",
                    ActionKind.END_SESSION,
                    listOf("Remote stays available"),
                    Expected(serverEpoch = "epoch"),
                    60000,
                    confirmMethod = ConfirmMethod.SETTINGS_END_SESSION,
                )
            media.receive(
                McReply.Success(
                    sent.single().id,
                    RemoteResult.SettingsEndSession(ActivityPreparation.ConfirmationRequired(preview)),
                ),
            )
            assertEquals(preview, media.state.value.preview)
            media.confirm()
            media.confirm()
            assertEquals(2, sent.size)
            assertEquals("end-preview", (sent.last().command as RemoteCommand.SettingsEndSession).params.confirmationId)
            media.observe(null)
            assertNull(media.state.value.preview)
            assertEquals(2, sent.size)
        }

    @Test fun loginPreferenceWaitsForNativeSuccessThenOnlyReadsSettings() =
        runTest {
            val sent = mutableListOf<McRequest>()
            val media = MediaSessionController(backgroundScope) { sent.add(it) }
            media.observe(RemoteJson.parseSnapshot(RecoveryFixture.snapshot()))
            media.mutable.value =
                media.state.value.copy(settings = RoutineSettingsState(native = SettingsState("7", Theme.DARK, false)))
            media.command(RemoteCommand.SettingsStartAtLoginSet(SettingsStartAtLoginSetParams(true)))
            val request = sent.single()
            assertEquals("7", request.expected?.settingsRevision)
            media.receive(
                McReply.Success(
                    request.id,
                    RemoteResult.SettingsStartAtLoginSet(AcceptedResult(OperationAccepted.ACCEPTED, "operation")),
                ),
            )
            assertFalse(
                media.state.value.settings.native!!
                    .startAtLogin,
            )
            assertEquals(1, sent.size)
            media.operation(
                OperationState("operation", request.id, OperationStatus.SUCCEEDED, cancellationRequested = false),
            )
            assertTrue(sent.last().command is RemoteCommand.SettingsGet)
            assertEquals(2, sent.size)
            media.receive(
                McReply.Success(sent.last().id, RemoteResult.SettingsGet(SettingsState("8", Theme.DARK, true))),
            )
            assertTrue(
                media.state.value.settings.native!!
                    .startAtLogin,
            )
        }
}
