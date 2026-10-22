package com.emmah.colordock.remote.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.emmah.colordock.remote.R
import com.emmah.colordock.remote.tv.TvConnectionState
import com.emmah.colordock.remote.tv.TvState
import com.emmah.colordock.remote.tv.DiscoveredTv

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
    connectedContent: @Composable ColumnScope.() -> Unit = {},
) {
    var address by rememberSaveable(initialAddress) { mutableStateOf(initialAddress.orEmpty()) }
    var searched by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(searching) { if(searching)searched=true }
    val busy = searching || state.connection in setOf(TvConnectionState.Connecting, TvConnectionState.Pairing,TvConnectionState.Reconnecting)
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(stringResource(R.string.connect_title), Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
            SettingsButton(onAppSettings)
        }
        Text(stringResource(R.string.connect_explanation))
        Text(stringResource(R.string.permission_explanation), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if(!needsSettings)OutlinedButton(onFind,Modifier.fillMaxWidth().heightIn(min=56.dp),enabled=!busy){Text("Find TVs")}
        if(searching){Text("Looking for TVs…");LinearProgressIndicator(Modifier.fillMaxWidth())}
        if(searched&&!searching&&discovered.isEmpty())Text("No TVs found. Check that your phone and TV are on the same network, or enter the TV IP address.")
        discovered.forEach {tv->OutlinedCard(onClick={onConnect(tv.address)},modifier=Modifier.fillMaxWidth(),enabled=!busy){Column(Modifier.padding(16.dp)){Text(tv.name,style=MaterialTheme.typography.titleMedium);if(tv.model.isNotBlank())Text(tv.model);Text(tv.address,color=MaterialTheme.colorScheme.onSurfaceVariant)}}}
        Text("Enter IP address",style=MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = address,
            onValueChange = { address = it },
            label = { Text(stringResource(R.string.tv_ip)) },
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
                onClick = { onConnect(address) },
                enabled = !busy && address.isNotBlank(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) { Text(if(initialAddress!=null&&address==initialAddress)"Reconnect" else stringResource(R.string.connect)) }
        }
        if (state.connection == TvConnectionState.Pairing) Text(stringResource(R.string.pairing))
        if (state.connection == TvConnectionState.Connecting) {
            CircularProgressIndicator(Modifier.size(32.dp))
            Text("Connecting to your TV…")
        }
        if(state.connection==TvConnectionState.Reconnecting)Text("Reconnecting to your TV…")
        if (state.connection == TvConnectionState.Ready) {
            Text(stringResource(R.string.connected), style = MaterialTheme.typography.titleLarge)
            connectedContent()
        }
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (busy || state.connection == TvConnectionState.Ready) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(if (busy) stringResource(R.string.cancel) else "Disconnect")
            }
        }
    }
}
