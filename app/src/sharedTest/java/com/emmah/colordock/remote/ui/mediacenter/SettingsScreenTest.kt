package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.emmah.colordock.remote.mediacenter.protocol.ConnectionDiagnostics
import com.emmah.colordock.remote.mediacenter.protocol.DeviceList
import com.emmah.colordock.remote.mediacenter.protocol.DiscoveryObservation
import com.emmah.colordock.remote.mediacenter.protocol.ListenerObservation
import com.emmah.colordock.remote.mediacenter.protocol.PairedDevice
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand
import com.emmah.colordock.remote.mediacenter.protocol.Scope
import com.emmah.colordock.remote.mediacenter.protocol.ServicePreference
import com.emmah.colordock.remote.mediacenter.protocol.SettingsState
import com.emmah.colordock.remote.mediacenter.protocol.Theme
import com.emmah.colordock.remote.mediacenter.protocol.VpnCountry
import com.emmah.colordock.remote.ui.RemoteTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun settingsRemainReachableAcrossSizesAndNeverOptimisticallyChangePreferences() {
        val dimensions = mutableStateOf(DpSize(360.dp, 640.dp))
        val scale = mutableFloatStateOf(1f)
        val state = mutableStateOf(settingsScreenState())
        val sent = mutableListOf<RemoteCommand>()
        compose.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(dimensions.value) then
                    DeviceConfigurationOverride.FontScale(scale.floatValue),
            ) {
                RemoteTheme {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        SettingsScreen(state.value, RoutineSettingsActions({ sent.add(it) }, {}, {}))
                    }
                }
            }
        }
        listOf(
            DpSize(360.dp, 640.dp) to 1f,
            DpSize(640.dp, 360.dp) to 1f,
            DpSize(360.dp, 640.dp) to 2f,
        ).forEach { (size, font) ->
            compose.runOnIdle {
                dimensions.value = size
                scale.floatValue = font
            }
            compose.onNodeWithText("Connect VPN…").performScrollTo().performClick()
            assertTrue(sent.last() is RemoteCommand.VpnPrepareChange)
            compose.onNodeWithContentDescription("Require VPN for Netflix").performScrollTo().performClick()
            assertEquals(false, (sent.last() as RemoteCommand.VpnRequirementSet).params.required)
            compose.onNodeWithText("Revoke Sofa phone").performScrollTo().performClick()
            assertEquals("phone", (sent.last() as RemoteCommand.DevicesRevoke).params.deviceId)
            compose.onNodeWithText("Pair another phone").performScrollTo().assertIsDisplayed()
        }
        compose.runOnIdle { state.value = state.value.copy(snapshot = null) }
        compose.onNodeWithText("Connect VPN…").performScrollTo().assertIsNotEnabled()
        assertTrue(
            state.value.settings.native
                ?.servicePreferences
                ?.single()
                ?.vpnRequired == true,
        )
    }

    @Test fun connectionGuidanceUsesObservedVersionAndReadOnlyHelpWithoutSendingCommands() {
        val state = mutableStateOf(settingsScreenState())
        val sent = mutableListOf<RemoteCommand>()
        compose.setContent {
            RemoteTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    SettingsScreen(state.value, RoutineSettingsActions({ sent.add(it) }, {}, {}))
                }
            }
        }
        compose.onNodeWithText("Installed NordVPN version: 3.20.1").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("nordvpn set lan-discovery --help").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("nordvpn allowlist --help").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("NordVPN Linux setup guide").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            state.value = state.value.copy(snapshot = null, settings = RoutineSettingsState())
        }
        compose.onNodeWithText("Installed NordVPN version: unknown").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("nordvpn --version").performScrollTo().assertIsDisplayed()
        assertTrue(sent.isEmpty())
    }

    @Test fun pairingRowsAreVolatileBoundedAndLegacyRepliesAreHonest() {
        val value = mutableStateOf(PairingDisplayState("attempt", null))
        var hidden = false
        compose.setContent { RemoteTheme { PairingDisplay(value.value, { hidden = true }) } }
        compose
            .onNodeWithText(
                "This computer did not provide a pairing QR code. " +
                    "Update Mediacenter and prepare a new pairing window.",
            ).assertIsDisplayed()
        compose.runOnIdle { value.value = PairingDisplayState("attempt", List(21) { "0".repeat(21) }) }
        compose
            .onNodeWithText(
                "Scan with the other phone within two minutes. This code works once.",
            ).assertIsDisplayed()
        compose.onNodeWithText("Hide code").performClick()
        assertTrue(hidden)
        assertFalse(value.value.toString().contains("000000000000000000000"))
        assertFalse(validPairingRows(List(21) { "1".repeat(22) }))
    }
}

private fun settingsScreenState() =
    MediaSessionState(
        snapshot = phoneMediaSnapshot(),
        settings =
            RoutineSettingsState(
                native =
                    SettingsState(
                        "1",
                        Theme.DARK,
                        false,
                        listOf(ServicePreference("netflix", "Netflix", true)),
                        ConnectionDiagnostics(
                            "wlan0",
                            ListenerObservation.LISTENING,
                            DiscoveryObservation.ANNOUNCED,
                            "3.20.1",
                        ),
                    ),
                vpn = phoneMediaSnapshot().vpn,
                countries = listOf(VpnCountry("Finland", "Finland")),
                devices =
                    DeviceList(
                        listOf(PairedDevice("phone", "Sofa phone", listOf(Scope.SETTINGS), 1, revoked = false)),
                    ),
            ),
    )
