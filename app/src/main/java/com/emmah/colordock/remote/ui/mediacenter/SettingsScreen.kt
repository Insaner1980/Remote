package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.emmah.colordock.remote.mediacenter.AndroidMediacenterNetwork
import com.emmah.colordock.remote.mediacenter.protocol.DevicesRevokeParams
import com.emmah.colordock.remote.mediacenter.protocol.PairingBeginParams
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand
import com.emmah.colordock.remote.mediacenter.protocol.SettingsThemeSetParams
import com.emmah.colordock.remote.mediacenter.protocol.Theme
import com.emmah.colordock.remote.mediacenter.protocol.VpnRequirementSetParams

@Composable
internal fun SettingsScreen(
    state: MediaSessionState,
    actions: RoutineSettingsActions,
    modifier: Modifier = Modifier,
    section: SettingsSection = SettingsSection.Advanced,
) {
    val ready = state.snapshot != null && !state.busy && state.settings.native != null
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (section) {
            SettingsSection.VPN -> {
                VpnSettings(state.settings, ready, actions.send)
                VpnServicePreferences(state, ready, actions.send)
            }

            SettingsSection.Appearance -> {
                ComputerAppearance(state, ready, actions.send)
            }

            SettingsSection.Devices -> {
                PairedPhones(state, ready, actions.send)
                state.settings.pairing?.let { PairingDisplay(it, actions.dismissPairing) }
            }

            SettingsSection.Advanced -> {
                RefreshAction(actions.refresh, enabled = state.snapshot != null)
                LifetimeSettings(state, ready, actions.send)
            }

            SettingsSection.Sound, SettingsSection.Media -> {}
        }
    }
}

@Composable
private fun ComputerAppearance(
    state: MediaSessionState,
    ready: Boolean,
    send: (RemoteCommand) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.settings.native?.let { native ->
            Text("Computer", style = MaterialTheme.typography.titleMedium)
            Row {
                Theme.entries.forEach { theme ->
                    androidx.compose.material3.FilterChip(
                        selected = theme == native.theme,
                        onClick = {
                            if (theme !=
                                native.theme
                            ) {
                                send(RemoteCommand.SettingsThemeSet(SettingsThemeSetParams(theme)))
                            }
                        },
                        enabled = ready,
                        label = { Text(theme.wire.replaceFirstChar { it.uppercase() }) },
                    )
                }
            }
        }
    }
}

@Composable
private fun VpnServicePreferences(
    state: MediaSessionState,
    ready: Boolean,
    send: (RemoteCommand) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("VPN for apps", style = MaterialTheme.typography.titleMedium)
        state.settings.native?.servicePreferences?.forEach { service ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(service.label, Modifier.weight(1f))
                Switch(
                    checked = service.vpnRequired,
                    modifier = Modifier.semantics { contentDescription = "Require VPN for ${service.label}" },
                    onCheckedChange = {
                        send(RemoteCommand.VpnRequirementSet(VpnRequirementSetParams(service.serviceId, it)))
                    },
                    enabled = ready,
                )
            }
        }
    }
}

@Composable
private fun PairedPhones(
    state: MediaSessionState,
    ready: Boolean,
    send: (RemoteCommand) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.settings.devices?.items?.forEach { device ->
            androidx.compose.runtime.key(device.deviceId) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(device.label, Modifier.weight(1f))
                    ItemActions(
                        "Phone options for ${device.label}",
                        listOf(
                            MenuAction("Remove", ready) {
                                send(RemoteCommand.DevicesRevoke(DevicesRevokeParams(deviceId = device.deviceId)))
                            },
                        ),
                        enabled = ready,
                    )
                }
            }
        }
        TextButton(onClick = { send(RemoteCommand.PairingBegin(PairingBeginParams())) }, enabled = ready) {
            Text("Add phone")
        }
    }
}
