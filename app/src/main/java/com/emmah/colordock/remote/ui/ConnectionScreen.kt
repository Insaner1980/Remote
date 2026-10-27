package com.emmah.colordock.remote.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.emmah.colordock.remote.R
import com.emmah.colordock.remote.tv.DiscoveredTv
import com.emmah.colordock.remote.tv.TvConnectionState
import com.emmah.colordock.remote.tv.TvState

// Explicit state and callbacks preserve the hoisted Compose API.
@Suppress("LongParameterList")
@Composable
fun ConnectionScreen(
    state: TvState,
    needsSettings: Boolean,
    onConnect: (String) -> Unit,
    onCancel: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    initialAddress: String? = null,
    searching: Boolean = false,
    discovered: List<DiscoveredTv> = emptyList(),
    onFind: () -> Unit = {},
    onAppSettings: () -> Unit = {},
    onForget: (() -> Unit)? = null,
    connectedContent: @Composable ColumnScope.() -> Unit = {},
) {
    var address by rememberSaveable(initialAddress) { mutableStateOf(initialAddress.orEmpty()) }
    var searched by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(searching) {
        if (searching) searched = true
    }
    val busy =
        searching ||
            state.connection in
            setOf(TvConnectionState.Connecting, TvConnectionState.Pairing, TvConnectionState.Reconnecting)
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(
                stringResource(R.string.connect_title),
                Modifier.weight(1f),
                style = MaterialTheme.typography.headlineMedium,
            )
            SettingsButton(onAppSettings)
        }
        Text(stringResource(R.string.connect_explanation))
        Text(stringResource(R.string.permission_explanation), color = MaterialTheme.colorScheme.onSurfaceVariant)
        DiscoveryResults(needsSettings, searching, searched, discovered, busy, onFind, onConnect)
        ConnectionAddress(
            state,
            needsSettings,
            address,
            { address = it },
            initialAddress,
            busy,
            onConnect,
            onOpenSettings,
        )
        ConnectionStatus(state, connectedContent)
        onForget?.let { forget ->
            OutlinedButton(forget, Modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = !busy) {
                Text("Forget TV")
            }
        }
        if (busy || state.connection == TvConnectionState.Ready) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(if (busy) stringResource(R.string.cancel) else "Disconnect")
            }
        }
    }
}

// State and callbacks remain explicit to preserve the hoisted Compose API.
@Suppress("LongParameterList")
@Composable
private fun ColumnScope.DiscoveryResults(
    needsSettings: Boolean,
    searching: Boolean,
    searched: Boolean,
    discovered: List<DiscoveredTv>,
    busy: Boolean,
    onFind: () -> Unit,
    onConnect: (String) -> Unit,
) {
    if (!needsSettings) {
        OutlinedButton(onFind, Modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = !busy) {
            Text("Find TVs")
        }
    }
    if (searching) {
        Text("Looking for TVs…")
        LinearProgressIndicator(Modifier.fillMaxWidth())
    }
    if (searched && !searching &&
        discovered.isEmpty()
    ) {
        Text("No TVs found. Check that your phone and TV are on the same network, or enter the TV IP address.")
    }
    discovered.forEach { tv ->
        OutlinedCard(onClick = {
            onConnect(tv.address)
        }, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
            Column(Modifier.padding(16.dp)) {
                Text(tv.name, style = MaterialTheme.typography.titleMedium)
                if (tv.model.isNotBlank()) Text(tv.model)
                Text(tv.address, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// State and callbacks remain explicit to preserve the hoisted Compose API.
@Suppress("LongParameterList")
@Composable
private fun ColumnScope.ConnectionAddress(
    state: TvState,
    needsSettings: Boolean,
    address: String,
    onAddress: (String) -> Unit,
    initialAddress: String?,
    busy: Boolean,
    onConnect: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Text("Enter IP address", style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(
        value = address,
        onValueChange = {
            onAddress(it)
        },
        label = {
            Text(stringResource(R.string.tv_ip))
        },
        enabled = !busy && state.connection != TvConnectionState.Ready,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
        modifier = Modifier.fillMaxWidth(),
    )
    if (needsSettings) {
        Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(stringResource(R.string.open_settings))
        }
    } else if (state.connection != TvConnectionState.Ready) {
        Button(
            onClick = {
                onConnect(address)
            },
            enabled = !busy && address.isNotBlank(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) {
            Text(
                if (initialAddress != null &&
                    address == initialAddress
                ) {
                    "Reconnect"
                } else {
                    stringResource(R.string.connect)
                },
            )
        }
    }
}

@Composable
private fun ColumnScope.ConnectionStatus(
    state: TvState,
    connectedContent: @Composable ColumnScope.() -> Unit,
) {
    if (state.connection == TvConnectionState.Pairing) Text(stringResource(R.string.pairing))
    if (state.connection == TvConnectionState.Connecting) {
        CircularProgressIndicator(Modifier.size(32.dp))
        Text("Connecting to your TV…")
    }
    if (state.connection == TvConnectionState.Reconnecting) Text("Reconnecting to your TV…")
    if (state.connection == TvConnectionState.Ready) {
        Text(stringResource(R.string.connected), style = MaterialTheme.typography.titleLarge)
        connectedContent()
    }
    state.message?.let {
        Text(it, color = MaterialTheme.colorScheme.error)
    }
}
