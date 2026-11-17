package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.emmah.colordock.remote.R
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand
import com.emmah.colordock.remote.mediacenter.protocol.VpnAction
import com.emmah.colordock.remote.mediacenter.protocol.VpnPrepareChangeParams
import com.emmah.colordock.remote.mediacenter.protocol.VpnState
import com.emmah.colordock.remote.ui.AdaptivePair
import com.emmah.colordock.remote.ui.DockIcon

@Composable
internal fun VpnSettings(
    state: RoutineSettingsState,
    enabled: Boolean,
    send: (RemoteCommand) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var selected by remember(state.countries) { mutableStateOf<String?>(null) }
    Surface(
        modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        DockIcon(R.drawable.ic_public, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
                Column {
                    Text(vpnStateLabel(state.vpn?.state) ?: "Unavailable", style = MaterialTheme.typography.titleLarge)
                    state.vpn?.country?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            VpnCountryChoice(state, enabled, selected, expanded, { expanded = it }, { selected = it })
            AdaptivePair(first = { keyModifier ->
                Button(onClick = {
                    send(RemoteCommand.VpnPrepareChange(VpnPrepareChangeParams(VpnAction.CONNECT, selected)))
                }, modifier = keyModifier.heightIn(min = 48.dp), enabled = enabled, shape = CircleShape) {
                    Text("Connect")
                }
            }, second = { keyModifier ->
                OutlinedButton(onClick = {
                    send(RemoteCommand.VpnPrepareChange(VpnPrepareChangeParams(VpnAction.DISCONNECT)))
                }, modifier = keyModifier.heightIn(min = 48.dp), enabled = enabled, shape = CircleShape) {
                    Text("Disconnect")
                }
            })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongParameterList")
@Composable
private fun VpnCountryChoice(
    state: RoutineSettingsState,
    enabled: Boolean,
    selected: String?,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val canChoose = enabled && state.countries.isNotEmpty()
    ExposedDropdownMenuBox(expanded, { if (canChoose) onExpandedChange(it) }, modifier) {
        OutlinedTextField(
            value = state.countries.firstOrNull { it.id == selected }?.label ?: "Automatic",
            onValueChange = {},
            readOnly = true,
            label = { Text("Country") },
            enabled = canChoose,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier =
                Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = canChoose)
                    .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded, { onExpandedChange(false) }) {
            DropdownMenuItem(text = { Text("Automatic") }, onClick = {
                onSelect(null)
                onExpandedChange(false)
            })
            state.countries.forEach { country ->
                DropdownMenuItem(text = { Text(country.label) }, onClick = {
                    onSelect(country.id)
                    onExpandedChange(false)
                })
            }
        }
    }
}

internal fun vpnStateLabel(state: VpnState?): String? =
    when (state) {
        VpnState.CONNECTED -> "Connected"
        VpnState.CONNECTING -> "Connecting…"
        VpnState.DISCONNECTED -> "Disconnected"
        VpnState.FAILED -> "Couldn't connect"
        else -> null
    }
