package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
    Column(modifier.verticalScroll(rememberScrollState())) {
        if (!lanGranted) {
            Button(
                onClick = { onPermission(MediacenterSetupPermission.LocalNetwork) },
            ) { Text("Allow Wi-Fi access") }
        }
        Button(
            onClick = { onPermission(MediacenterSetupPermission.Camera) },
            enabled = lanGranted,
        ) { Text("Scan code") }
        if (cameraDenied) Text("Camera unavailable")
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = { manual = !manual }) { Text("Enter address") }
        if (manual) {
            OutlinedTextField(address, { address = it }, label = { Text("Computer address") }, singleLine = true)
            OutlinedTextField(
                port,
                { port = it },
                label = { Text("Port") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Button(
                onClick = { port.toIntOrNull()?.let { onManual(address, it) } },
                enabled = lanGranted && port.toIntOrNull()?.let { MediacenterEndpoint.parse(address, it) } != null,
            ) {
                Text("Connect")
            }
        }
    }
}
