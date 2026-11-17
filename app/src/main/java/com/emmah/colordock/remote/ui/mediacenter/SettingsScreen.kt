package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.emmah.colordock.remote.R
import com.emmah.colordock.remote.mediacenter.AndroidMediacenterNetwork
import com.emmah.colordock.remote.mediacenter.protocol.DevicesRevokeParams
import com.emmah.colordock.remote.mediacenter.protocol.PairingBeginParams
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand
import com.emmah.colordock.remote.mediacenter.protocol.SettingsThemeSetParams
import com.emmah.colordock.remote.mediacenter.protocol.Theme
import com.emmah.colordock.remote.mediacenter.protocol.VpnRequirementSetParams
import com.emmah.colordock.remote.ui.RemoteShapes

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
    Column {
        state.settings.native?.let { native ->
            SectionLabel("Computer")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Theme.entries.forEachIndexed { index, theme ->
                    SegmentedButton(
                        selected = theme == native.theme,
                        onClick = {
                            if (theme !=
                                native.theme
                            ) {
                                send(RemoteCommand.SettingsThemeSet(SettingsThemeSetParams(theme)))
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, Theme.entries.size),
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
    Column {
        SectionLabel("VPN for apps")
        ListGroup {
            val services =
                state.settings.native
                    ?.servicePreferences
                    .orEmpty()
            services.forEachIndexed { index, service ->
                ListRow(
                    service.label,
                    null,
                    listRowPosition(index, services.size),
                    modifier =
                        Modifier.testTag("vpn-service-${service.serviceId}").semantics {
                            contentDescription = "Require VPN for ${service.label}"
                            toggleableState = if (service.vpnRequired) ToggleableState.On else ToggleableState.Off
                        },
                    leading = {
                        Image(
                            painterResource(serviceIcon(service.serviceId)),
                            null,
                            Modifier.size(36.dp).clip(RemoteShapes.Inner),
                        )
                    },
                    onClick = {
                        send(
                            RemoteCommand.VpnRequirementSet(
                                VpnRequirementSetParams(service.serviceId, !service.vpnRequired),
                            ),
                        )
                    },
                    enabled = ready,
                    role = Role.Switch,
                    verticalPadding = 10.dp,
                    trailing = {
                        // The whole row owns the accessible56dp target; this switch only draws its state.
                        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                            Switch(service.vpnRequired, onCheckedChange = null, enabled = ready)
                        }
                    },
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
    ListGroup {
        val devices =
            state.settings.devices
                ?.items
                .orEmpty()
        devices.forEachIndexed { index, device ->
            androidx.compose.runtime.key(device.deviceId) {
                ListRow(
                    device.label,
                    null,
                    listRowPosition(index, devices.size + 1),
                    leadingIcon = R.drawable.ic_smartphone,
                    trailing = {
                        ItemActions(
                            "Phone options for ${device.label}",
                            listOf(
                                MenuAction("Remove", ready, destructive = true) {
                                    send(RemoteCommand.DevicesRevoke(DevicesRevokeParams(deviceId = device.deviceId)))
                                },
                            ),
                            enabled = ready,
                        )
                    },
                )
            }
        }
        ListRow(
            "Add phone",
            null,
            if (devices.isEmpty()) ListRowPosition.Single else ListRowPosition.Last,
            leadingIcon = R.drawable.ic_add,
            onClick = { send(RemoteCommand.PairingBegin(PairingBeginParams())) },
            enabled = ready,
        )
    }
}
