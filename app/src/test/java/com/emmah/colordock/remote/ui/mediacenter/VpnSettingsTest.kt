package com.emmah.colordock.remote.ui.mediacenter

import com.emmah.colordock.remote.mediacenter.RecoveryFixture
import com.emmah.colordock.remote.mediacenter.protocol.ActionKind
import com.emmah.colordock.remote.mediacenter.protocol.ConfirmMethod
import com.emmah.colordock.remote.mediacenter.protocol.ConfirmationPreview
import com.emmah.colordock.remote.mediacenter.protocol.ErrorPayload
import com.emmah.colordock.remote.mediacenter.protocol.Expected
import com.emmah.colordock.remote.mediacenter.protocol.McReply
import com.emmah.colordock.remote.mediacenter.protocol.McRequest
import com.emmah.colordock.remote.mediacenter.protocol.PairingBeginParams
import com.emmah.colordock.remote.mediacenter.protocol.PairingPreparation
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand
import com.emmah.colordock.remote.mediacenter.protocol.RemoteJson
import com.emmah.colordock.remote.mediacenter.protocol.RemoteResult
import com.emmah.colordock.remote.mediacenter.protocol.SettingsState
import com.emmah.colordock.remote.mediacenter.protocol.SettingsThemeSetParams
import com.emmah.colordock.remote.mediacenter.protocol.Theme
import com.emmah.colordock.remote.mediacenter.protocol.WireErrorCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VpnSettingsTest {
    @Test fun vpnConfirmationUsesOnlyTheSavedIdAndCannotBeDoubleSubmitted() =
        runTest {
            val sent = mutableListOf<McRequest>()
            val media = MediaSessionController(backgroundScope) { sent.add(it) }
            media.observe(RemoteJson.parseSnapshot(RecoveryFixture.snapshot()))
            media.mutable.value =
                media.state.value.copy(
                    preview =
                        ConfirmationPreview(
                            "vpn-preview",
                            "phone",
                            ActionKind.CHANGE_VPN,
                            listOf("Phone connection may be interrupted"),
                            Expected(serverEpoch = "epoch"),
                            60000,
                            confirmMethod = ConfirmMethod.VPN_CONFIRM_CHANGE,
                        ),
                )
            media.confirm()
            media.confirm()
            assertEquals(1, sent.size)
            assertTrue(sent.single().command is RemoteCommand.VpnConfirmChange)
            assertEquals("vpn-preview", (sent.single().command as RemoteCommand.VpnConfirmChange).params.confirmationId)
        }

    @Test fun nativeSettingsRevisionIsUsedAndLocalPreferenceNeverChangesOnAcceptance() =
        runTest {
            val sent = mutableListOf<McRequest>()
            val media = MediaSessionController(backgroundScope) { sent.add(it) }
            media.observe(RemoteJson.parseSnapshot(RecoveryFixture.snapshot()))
            media.refreshSettings()
            assertTrue(sent.first().command is RemoteCommand.SettingsGet)
            val refresh = sent.first { it.command is RemoteCommand.VpnState }
            media.receive(
                McReply.Success(
                    refresh.id,
                    RemoteResult.VpnState(
                        media.state.value.snapshot!!
                            .vpn,
                    ),
                ),
            )
            assertTrue(sent.last().command is RemoteCommand.SettingsGet)
            media.receive(
                McReply.Success(sent.last().id, RemoteResult.SettingsGet(SettingsState("55", Theme.DARK, false))),
            )
            media.command(RemoteCommand.SettingsThemeSet(SettingsThemeSetParams(Theme.LIGHT)))
            assertEquals("55", sent.last().expected?.settingsRevision)
            assertEquals(
                Theme.DARK,
                media.state.value.settings.native
                    ?.theme,
            )
        }

    @Test fun pairingQrExpiresAndOwnerChangesClearItWithoutIssuingAgain() =
        runTest {
            val sent = mutableListOf<McRequest>()
            val media = MediaSessionController(backgroundScope) { sent.add(it) }
            media.observe(RemoteJson.parseSnapshot(RecoveryFixture.snapshot()))
            media.destination(MediaDestination.Settings)
            val resource = checkNotNull(javaClass.classLoader?.getResource("mediacenter-v1.json"))
            val fixture = JSONObject(resource.readText()).getJSONObject("fixtures")
            val candidate = RemoteJson.parsePairingCandidate(fixture.getJSONObject("pairingCandidate").toString())
            media.command(RemoteCommand.PairingBegin(PairingBeginParams()))
            val ready = PairingPreparation.Ready("operation", candidate, List(21) { "0".repeat(21) })
            media.receive(McReply.Success(sent.last().id, RemoteResult.PairingBegin(ready)))
            assertEquals(
                candidate.attemptId,
                media.state.value.settings.pairing
                    ?.attemptId,
            )
            runCurrent()
            advanceTimeBy(120000)
            runCurrent()
            assertNull(media.state.value.settings.pairing)
            assertEquals(1, sent.count { it.command is RemoteCommand.PairingBegin })
            val display = PairingDisplayState("other", List(21) { "0".repeat(21) })
            media.mutable.value =
                media.state.value.copy(
                    settings =
                        media.state.value.settings
                            .copy(pairing = display),
                )
            media.observe(null)
            assertNull(media.state.value.settings.pairing)
        }

    @Test fun slowVpnFailureDoesNotBlockTheIndependentNativeSettingsRead() =
        runTest {
            val sent = mutableListOf<McRequest>()
            val media = MediaSessionController(backgroundScope) { sent.add(it) }
            media.observe(RemoteJson.parseSnapshot(RecoveryFixture.snapshot()))
            media.refreshSettings()
            val settings = sent.first { it.command is RemoteCommand.SettingsGet }
            val vpn = sent.first { it.command is RemoteCommand.VpnState }
            val native = SettingsState("77", Theme.DARK, false)
            media.receive(McReply.Success(settings.id, RemoteResult.SettingsGet(native)))
            media.receive(
                McReply.Failure(
                    vpn.id,
                    ErrorPayload(WireErrorCode.UNAVAILABLE, "VPN is taking too long.", true, emptyList()),
                ),
            )
            assertEquals(native, media.state.value.settings.native)
            assertTrue(media.state.value.snapshot != null)
            assertTrue(sent.none { it.command is RemoteCommand.VpnConfirmChange })
        }
}
