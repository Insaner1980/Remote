package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.emmah.colordock.remote.mediacenter.MediacenterEndpoint
import com.emmah.colordock.remote.ui.RemoteDimens

@Composable
fun MediacenterEntry(
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(onClick = onOpen, modifier = modifier) { Text("Add Mediacenter") }
}

@Composable
fun MediacenterSetupScreen(
    lanGranted: Boolean,
    cameraDenied: Boolean,
    onPermission: (MediacenterSetupPermission) -> Unit,
    onManual: (String, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var manual by remember { mutableStateOf(false) }
    var address by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("47831") }
    Column(
        modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(RemoteDimens.BlockGap),
    ) {
        if (!lanGranted) {
            Button(
                onClick = { onPermission(MediacenterSetupPermission.LocalNetwork) },
            ) { Text("Allow Wi-Fi access") }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SetupAction(
                "Scan code",
                { onPermission(MediacenterSetupPermission.Camera) },
                enabled = lanGranted,
                primary = lanGranted && !manual,
            )
            if (cameraDenied) {
                Text(
                    "Camera unavailable",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        TextButton(onClick = { manual = !manual }) { Text("Enter address") }
        if (manual) {
            OutlinedTextField(
                address,
                { address = it },
                Modifier.fillMaxWidth(),
                label = { Text("Computer address") },
                singleLine = true,
            )
            OutlinedTextField(
                port,
                { port = it },
                Modifier.fillMaxWidth(),
                label = { Text("Port") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            SetupAction(
                "Connect",
                { port.toIntOrNull()?.let { onManual(address, it) } },
                enabled = lanGranted && port.toIntOrNull()?.let { MediacenterEndpoint.parse(address, it) } != null,
                primary = lanGranted,
            )
        }
    }
}

@Composable
private fun SetupAction(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    primary: Boolean,
) {
    if (primary) {
        Button(onClick, enabled = enabled) { Text(label) }
    } else {
        OutlinedButton(onClick, enabled = enabled) { Text(label) }
    }
}
