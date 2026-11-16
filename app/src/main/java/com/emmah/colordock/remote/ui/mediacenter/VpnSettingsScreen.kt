package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand
import com.emmah.colordock.remote.mediacenter.protocol.VpnAction
import com.emmah.colordock.remote.mediacenter.protocol.VpnPrepareChangeParams

@Composable
internal fun VpnSettings(
    state: RoutineSettingsState,
    enabled: Boolean,
    send: (RemoteCommand) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var selected by remember(state.countries) { mutableStateOf<String?>(null) }
    Column(modifier) {
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
        ) {
            Text(
                when (state.vpn?.state) {
                    com.emmah.colordock.remote.mediacenter.protocol.VpnState.CONNECTED -> "Connected"
                    com.emmah.colordock.remote.mediacenter.protocol.VpnState.CONNECTING -> "Connecting…"
                    com.emmah.colordock.remote.mediacenter.protocol.VpnState.DISCONNECTED -> "Disconnected"
                    com.emmah.colordock.remote.mediacenter.protocol.VpnState.FAILED -> "Couldn't connect"
                    else -> "Unavailable"
                },
            )
            state.vpn?.country?.let { Text(it) }
        }
        TextButton(onClick = { expanded = true }, enabled = enabled && state.countries.isNotEmpty()) {
            Text(state.countries.firstOrNull { it.id == selected }?.label ?: "Automatic")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Automatic") }, onClick = {
                selected = null
                expanded = false
            })
            state.countries.forEach { country ->
                DropdownMenuItem(text = { Text(country.label) }, onClick = {
                    selected = country.id
                    expanded = false
                })
            }
        }
        TextButton(
            onClick = { send(RemoteCommand.VpnPrepareChange(VpnPrepareChangeParams(VpnAction.CONNECT, selected))) },
            enabled = enabled,
        ) { Text("Connect") }
        TextButton(
            onClick = { send(RemoteCommand.VpnPrepareChange(VpnPrepareChangeParams(VpnAction.DISCONNECT))) },
            enabled = enabled,
        ) { Text("Disconnect") }
    }
}
