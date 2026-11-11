package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
) {
    val ready = state.snapshot != null && !state.busy && state.settings.native != null
    Column(modifier) {
        Text("Mediacenter settings")
        TextButton(onClick = actions.refresh, enabled = state.snapshot != null) { Text("Refresh settings") }
        VpnSettings(state.settings, ready, actions.send)
        state.settings.native?.let { native ->
            Text("Computer appearance")
            Row {
                Theme.entries.forEach { theme ->
                    TextButton(
                        onClick = { actions.send(RemoteCommand.SettingsThemeSet(SettingsThemeSetParams(theme))) },
                        enabled = ready && theme != native.theme,
                    ) { Text(theme.wire.replaceFirstChar { it.uppercase() }) }
                }
            }
            Text("Require VPN before opening")
            native.servicePreferences?.forEach { service ->
                Row {
                    Text(service.label)
                    Switch(
                        checked = service.vpnRequired,
                        modifier = Modifier.semantics { contentDescription = "Require VPN for ${service.label}" },
                        onCheckedChange = {
                            actions.send(
                                RemoteCommand.VpnRequirementSet(VpnRequirementSetParams(service.serviceId, it)),
                            )
                        },
                        enabled = ready,
                    )
                }
            }
        }
        LifetimeSettings(state, ready, actions.send)
        ConnectionSettings(state, ready, actions.send)
        state.settings.pairing?.let { PairingDisplay(it, actions.dismissPairing) }
    }
}

@Composable
private fun ConnectionSettings(
    state: MediaSessionState,
    ready: Boolean,
    send: (RemoteCommand) -> Unit,
) {
    Column {
        Text("Connection")
        val permission = AndroidMediacenterNetwork.hasLanPermission(LocalContext.current)
        Text("Phone LAN permission: ${if (permission) "granted" else "required"}")
        Text("Phone connection: ${if (state.snapshot != null) "connected" else "disconnected"}")
        Text("Phone discovery delivery: not observed by this screen")
        state.settings.native?.connectionDiagnostics?.let { diagnostics ->
            Text("Computer interface: ${diagnostics.selectedInterface ?: "unknown"}")
            Text("Computer listener: ${diagnostics.listener.wire}")
            Text("Computer discovery announcement: ${diagnostics.discovery.wire}")
            Text("An announcement does not confirm that this phone received discovery.")
        }
        LanConnectionGuidance(
            state.settings.native
                ?.connectionDiagnostics
                ?.vpnVersion,
        )
        Text("Paired phones")
        state.settings.devices?.items?.forEach { device ->
            TextButton(
                onClick = { send(RemoteCommand.DevicesRevoke(DevicesRevokeParams(deviceId = device.deviceId))) },
                enabled = ready,
            ) { Text("Revoke ${device.label}") }
        }
        TextButton(onClick = { send(RemoteCommand.PairingBegin(PairingBeginParams())) }, enabled = ready) {
            Text("Pair another phone")
        }
    }
}

private const val NORDVPN_LINUX_HELP =
    "https://support.nordvpn.com/hc/en-us/articles/" +
        "20196094470929-How-to-install-the-NordVPN-app-on-Linux-distributions"

@Composable
private fun LanConnectionGuidance(version: String?) {
    val links = LocalUriHandler.current
    Column {
        Text("Installed NordVPN version: ${version ?: "unknown"}")
        Text("If VPN blocks this phone, open a terminal on the computer and inspect help for its installed version:")
        Text("nordvpn --version")
        Text("nordvpn set lan-discovery --help")
        Text("nordvpn allowlist --help")
        Text("These commands only display version and help; they do not change network settings.")
        Text(
            "On a trusted home network, choose LAN discovery or a narrowly scoped LAN allowlist " +
                "using that version's help. Keep Kill Switch enabled.",
        )
        Text(
            "After setup, use Retry Mediacenter in Connection settings, then Refresh settings here. " +
                "The phone never changes LAN access or protection automatically.",
        )
        TextButton(onClick = { links.openUri(NORDVPN_LINUX_HELP) }) { Text("NordVPN Linux setup guide") }
    }
}
