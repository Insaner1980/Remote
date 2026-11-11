package com.emmah.colordock.remote.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.emmah.colordock.remote.mediacenter.MediacenterConnectionState
import com.emmah.colordock.remote.mediacenter.MediacenterRepository
import com.emmah.colordock.remote.mediacenter.MemoryPending
import com.emmah.colordock.remote.mediacenter.RecoveryControl
import com.emmah.colordock.remote.mediacenter.RecoveryFixture
import com.emmah.colordock.remote.mediacenter.RecoveryRoute
import com.emmah.colordock.remote.mediacenter.protocol.AcceptedResult
import com.emmah.colordock.remote.mediacenter.protocol.ActionKind
import com.emmah.colordock.remote.mediacenter.protocol.ConfirmMethod
import com.emmah.colordock.remote.mediacenter.protocol.ConfirmationPreview
import com.emmah.colordock.remote.mediacenter.protocol.Empty
import com.emmah.colordock.remote.mediacenter.protocol.Expected
import com.emmah.colordock.remote.mediacenter.protocol.McReply
import com.emmah.colordock.remote.mediacenter.protocol.McRequest
import com.emmah.colordock.remote.mediacenter.protocol.OperationAccepted
import com.emmah.colordock.remote.mediacenter.protocol.PairingPreparation
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand
import com.emmah.colordock.remote.mediacenter.protocol.RemoteJson
import com.emmah.colordock.remote.mediacenter.protocol.RemoteResult
import com.emmah.colordock.remote.mediacenter.recoverySaved
import com.emmah.colordock.remote.tv.TvConnectionState
import com.emmah.colordock.remote.ui.mediacenter.MediacenterNavigation
import com.emmah.colordock.remote.ui.mediacenter.MediacenterTargetPage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ControlTargetLifecycleTest {
    @get:Rule val compose = createComposeRule()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val control = RecoveryControl()
    private val journal = MemoryPending()
    private val repository = MediacenterRepository(control, scope, journal)
    private val targets = ControlTargetViewModel()
    private var initialDisconnects = 0
    private val navigation = MediacenterNavigation()

    @After fun close() {
        scope.cancel()
    }

    @Test fun switchingDuringAcceptedWorkKeepsJournalAndOriginalTargetWithoutReplay() {
        mount()
        val request =
            McRequest(
                "switch-work",
                RemoteCommand.QueueNext(Empty),
                Expected(serverEpoch = "epoch", sessionId = "session", queueRevision = "0"),
            )
        compose.runOnIdle {
            assertTrue(repository.send(request))
            control.emit(
                RemoteJson.encodeReply(
                    McReply.Success(
                        request.id,
                        RemoteResult.QueueNext(AcceptedResult(OperationAccepted.ACCEPTED, "queue-operation")),
                    ),
                ),
            )
        }
        compose.waitUntil { repository.pending.value.any { it.requestId == request.id && it.operationId != null } }
        switch(ControlTarget.LgTv)
        compose.onNodeWithText("LG screen").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals("queue-operation", journal.records.single().operationId)
            assertFalse(journal.records.single().resolved)
            assertEquals(initialDisconnects, control.disconnects)
        }
        switch(ControlTarget.Mediacenter)
        compose.onNodeWithText("Mediacenter is reconciling 1 action(s).").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, control.sent.count { it.command is RemoteCommand.QueueNext })
            assertEquals(MediacenterConnectionState.Connected, repository.state.value)
        }
    }

    @Test fun switchingWithNativeConfirmationCancelsOnlyPreviewAndDropsVolatileSheet() {
        mount(modal = "native-preview")
        compose.waitUntil { control.sent.any { it.command is RemoteCommand.ConfirmationsGet } }
        val request = control.sent.last { it.command is RemoteCommand.ConfirmationsGet }
        val preview =
            ConfirmationPreview(
                "native-preview",
                "phone",
                ActionKind.CHANGE_VPN,
                listOf("Native preview belongs to Mediacenter"),
                Expected(serverEpoch = "epoch"),
                60000,
                confirmMethod = ConfirmMethod.VPN_CONFIRM_CHANGE,
            )
        compose.runOnIdle {
            control.emit(
                RemoteJson.encodeReply(
                    McReply.Success(
                        request.id,
                        RemoteResult.ConfirmationsGet(preview),
                    ),
                ),
            )
        }
        compose.onNodeWithText("Change VPN connection?").assertIsDisplayed()
        switch(ControlTarget.LgTv)
        compose.onNodeWithText("Change VPN connection?").assertDoesNotExist()
        compose.waitUntil { control.sent.any { it.command is RemoteCommand.ConfirmationsCancel } }
        compose.runOnIdle {
            val cancel =
                control.sent.single { it.command is RemoteCommand.ConfirmationsCancel }.command
                    as RemoteCommand.ConfirmationsCancel
            assertEquals("native-preview", cancel.params.confirmationId)
            assertFalse(control.sent.any { it.command is RemoteCommand.VpnConfirmChange })
            assertEquals(initialDisconnects, control.disconnects)
        }
        switch(ControlTarget.Mediacenter)
        compose.onNodeWithText("Change VPN connection?").assertDoesNotExist()
    }

    @Test fun switchingAwayHidesPairingQrAndReturningNeverIssuesAnotherCandidate() {
        mount()
        showPairingQr()
        switch(ControlTarget.LgTv)
        compose.onNodeWithText("Hide code").assertDoesNotExist()
        switch(ControlTarget.Mediacenter)
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Hide code").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, control.sent.count { it.command is RemoteCommand.PairingBegin }) }
    }

    @Test fun openingBlockingContextHidesQrKeepsSettingsAndRejectsStaleOpener() {
        mount()
        showPairingQr()
        val origin = targets.lease()
        compose.runOnIdle { assertTrue(targets.retireInput(origin) {}) }
        compose.onNodeWithText("Hide code").assertDoesNotExist()
        compose.onNodeWithText("Mediacenter settings").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(ControlTarget.Mediacenter, targets.state.value.selected)
            assertFalse(targets.retireInput(origin) { throw AssertionError("Stale setup opener ran") })
            assertEquals(initialDisconnects, control.disconnects)
            assertEquals(1, control.sent.count { it.command is RemoteCommand.PairingBegin })
        }
    }

    @Test fun backgroundRetiresRootQrAndHeldInputBeforeSocketGrace() {
        mount()
        showPairingQr()
        val old = targets.lease()
        var cancelled = 0
        compose.runOnIdle {
            targets.background({
                assertEquals(old, targets.lease())
                cancelled++
            }, repository::background)
        }
        compose.onNodeWithText("Hide code").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, cancelled)
            assertFalse(targets.dispatch(old) { throw AssertionError("Stale input resumed") })
            assertEquals(initialDisconnects, control.disconnects)
            assertEquals(1, control.sent.count { it.command is RemoteCommand.PairingBegin })
            repository.foreground()
        }
        compose.onNodeWithText("Hide code").assertDoesNotExist()
    }

    private fun showPairingQr() {
        compose.onNodeWithText("Settings").performClick()
        compose.waitUntil { control.sent.any { it.command is RemoteCommand.SettingsGet } }
        val settingsRequest = control.sent.last { it.command is RemoteCommand.SettingsGet }
        compose.runOnIdle {
            control.emit(
                RemoteJson.encodeReply(
                    McReply.Success(
                        settingsRequest.id,
                        RemoteResult.SettingsGet(
                            com.emmah.colordock.remote.mediacenter.protocol.SettingsState(
                                "1",
                                com.emmah.colordock.remote.mediacenter.protocol.Theme.DARK,
                                false,
                            ),
                        ),
                    ),
                ),
            )
        }
        compose
            .onNodeWithText("Pair another phone")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        compose.waitUntil { control.sent.any { it.command is RemoteCommand.PairingBegin } }
        val request = control.sent.last { it.command is RemoteCommand.PairingBegin }
        val fixture =
            JSONObject(checkNotNull(javaClass.getResource("/mediacenter-v1.json")).readText())
                .getJSONObject("fixtures")
        val candidate = RemoteJson.parsePairingCandidate(fixture.getJSONObject("pairingCandidate").toString())
        compose.runOnIdle {
            control.emit(
                RemoteJson.encodeReply(
                    McReply.Success(
                        request.id,
                        RemoteResult.PairingBegin(
                            PairingPreparation.Ready(
                                "pair-operation",
                                candidate,
                                List(21) { "0".repeat(21) },
                            ),
                        ),
                    ),
                ),
            )
        }
        compose.onNodeWithText("Hide code").assertIsDisplayed()
    }

    private fun switch(target: ControlTarget) {
        compose.runOnIdle { targets.select(target) {} }
    }

    private fun mount(modal: String? = null) {
        targets.observe(TvConnectionState.Ready, MediacenterConnectionState.Connected)
        targets.select(ControlTarget.Mediacenter) {}
        compose.setContent {
            val state by targets.state.collectAsState()
            val lease = ControlTargetLease(state.selected, state.generation)
            val send =
                remember(lease) {
                    { request: McRequest ->
                        var sent = false
                        targets.dispatch(lease) { sent = repository.send(request) }
                        sent
                    }
                }
            RemoteTheme {
                TargetAppShell(state, RemoteTab.Remote, {}, mediacenter = { modifier ->
                    MediacenterTargetPage(repository, modifier, send = send, navigation = navigation)
                }) { Text("LG screen") }
            }
        }
        compose.runOnIdle {
            repository.connect(recoverySaved(), RecoveryRoute())
            control.native()
            if (modal != null) {
                val snapshot = JSONObject(RecoveryFixture.snapshot(sequence = "1"))
                snapshot.getJSONObject("ui").put("modalId", modal)
                control.emit(snapshot.toString())
            }
        }
        compose.waitUntil { repository.state.value == MediacenterConnectionState.Connected }
        compose.runOnIdle { initialDisconnects = control.disconnects }
    }
}
