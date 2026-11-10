package com.emmah.colordock.remote.ui.mediacenter

import android.Manifest
import android.content.Intent
import android.provider.Settings
import android.view.TextureView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.emmah.colordock.remote.mediacenter.AndroidMediacenterNetwork
import com.emmah.colordock.remote.mediacenter.CertificateObservation
import com.emmah.colordock.remote.mediacenter.MediacenterCameraScanner
import com.emmah.colordock.remote.mediacenter.MediacenterConnectionState
import com.emmah.colordock.remote.mediacenter.MediacenterEndpoint
import com.emmah.colordock.remote.mediacenter.protocol.PairingCandidate
import java.util.Locale

@Composable
fun MediacenterSetupDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    model: @Composable () -> MediacenterViewModel = { viewModel() },
) {
    val viewModel = model()
    var scanning by remember { mutableStateOf(false) }
    val camera =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            viewModel.cameraResult(granted)
            scanning = granted
        }
    val lan =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.refreshPermission() }
    LaunchedEffect(viewModel) { viewModel.open() }
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(viewModel, lifecycle) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> {
                        viewModel.refreshPermission()
                    }

                    Lifecycle.Event.ON_STOP -> {
                        scanning = false
                        viewModel.disconnect()
                    }

                    else -> {
                        Unit
                    }
                }
            }
        lifecycle.lifecycle.addObserver(observer)
        onDispose {
            lifecycle.lifecycle.removeObserver(observer)
            viewModel.closeSetup()
        }
    }
    AlertDialog(onDismissRequest = onDismiss, modifier = modifier, title = { Text("Add Mediacenter") }, text = {
        MediacenterDialogContent(viewModel, scanning, { scanning = it }, { permission ->
            when (permission) {
                MediacenterSetupPermission.Camera -> camera.launch(Manifest.permission.CAMERA)
                MediacenterSetupPermission.LocalNetwork -> lan.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
            }
        })
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } })
}

@Composable
private fun MediacenterDialogContent(
    viewModel: MediacenterViewModel,
    scanning: Boolean,
    onScanningChange: (Boolean) -> Unit,
    onPermission: (MediacenterSetupPermission) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val networks by viewModel.networks.collectAsStateWithLifecycle()
    val candidates by viewModel.candidates.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val manualActions =
        remember(viewModel) {
            ManualVerificationActions(
                viewModel.pairing::confirm,
                viewModel.pairing::reject,
                viewModel.pairing::pairManual,
            )
        }
    Column(modifier.heightIn(max = 560.dp)) {
        MediacenterNetworkOptions(networks, ui, {
            onScanningChange(false)
            viewModel.select(it)
        })
        when {
            scanning -> {
                MediacenterCameraPreview(
                    { candidate ->
                        onScanningChange(false)
                        viewModel.pairing.pairQr(candidate)
                    },
                    { message ->
                        onScanningChange(false)
                        viewModel.cameraError(message)
                    },
                )
                TextButton(onClick = { onScanningChange(false) }) { Text("Pair manually") }
            }

            ui.observation != null -> {
                ManualVerification(ui.observation!!, ui, manualActions)
            }

            else -> {
                MediacenterSetupScreen(
                    ui.lanGranted,
                    ui.cameraDenied,
                    onPermission,
                    viewModel.pairing::probe,
                    Modifier.weight(1f, fill = false),
                )
            }
        }
        if (!ui.lanGranted) MediacenterLanSettings()
        if (ui.busy) Text(if (ui.forgetting) "Removing saved Mediacenter…" else "Connecting securely…")
        if (ui.status.isNotEmpty()) Text(ui.status)
        MediacenterConnectionStatus(connection)
        val browsingSetup = ui.observation == null && !scanning
        val hasSavedAction = ui.saved || ui.forgetFailed
        if (hasSavedAction && !ui.busy && browsingSetup) {
            MediacenterConnectionActions(candidates, viewModel.connections, ui.saved)
        }
    }
}

@Composable
private fun MediacenterNetworkOptions(
    networks: List<AndroidMediacenterNetwork>,
    ui: MediacenterSetupState,
    onSelect: (AndroidMediacenterNetwork) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text("Choose your local Wi-Fi network", style = MaterialTheme.typography.titleMedium)
        networks.forEach { network ->
            TextButton(onClick = { onSelect(network) }, enabled = !ui.busy) {
                Text(if (ui.networkName == network.label) "✓ ${network.label}" else network.label)
            }
        }
        if (ui.lanGranted && networks.isEmpty()) Text("Connect your phone to Wi-Fi, then reopen this screen.")
    }
}

@Composable
private fun MediacenterLanSettings(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    TextButton(onClick = {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()),
        )
    }, modifier = modifier) { Text("App permissions") }
}

@Composable
private fun MediacenterConnectionStatus(
    connection: MediacenterConnectionState,
    modifier: Modifier = Modifier,
) {
    val message =
        when (connection) {
            MediacenterConnectionState.Connected -> {
                "Connected to Mediacenter"
            }

            MediacenterConnectionState.Failed -> {
                "Connection failed. Check Wi-Fi and the computer's Remote control settings. " +
                    "A changed identity requires new pairing."
            }

            MediacenterConnectionState.PairingRequired -> {
                "This phone was revoked. Pair with Mediacenter again."
            }

            MediacenterConnectionState.ResyncRequired -> {
                "Reconnect Mediacenter to refresh its state."
            }

            MediacenterConnectionState.PermissionRequired -> {
                "Allow local network access and select Wi-Fi again."
            }

            else -> {
                ""
            }
        }
    if (message.isNotEmpty()) Text(message, modifier)
}

@Composable
private fun MediacenterConnectionActions(
    candidates: List<MediacenterEndpoint>,
    connections: MediacenterConnections,
    canReconnect: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        if (canReconnect) {
            TextButton(onClick = { connections.connect() }) { Text("Reconnect Mediacenter") }
            TextButton(onClick = connections::find) { Text("Find paired Mediacenter") }
            candidates.forEach { endpoint ->
                TextButton(onClick = { connections.connect(endpoint) }) { Text("Connect at ${endpoint.address}") }
            }
        }
        TextButton(onClick = connections::forget) {
            Text(if (canReconnect) "Forget on this phone" else "Retry forgetting on this phone")
        }
    }
}

private class ManualVerificationActions(
    val confirm: (CertificateObservation) -> Unit,
    val reject: () -> Unit,
    val pair: (String) -> Unit,
)

@Composable
private fun ManualVerification(
    observation: CertificateObservation,
    ui: MediacenterSetupState,
    actions: ManualVerificationActions,
    modifier: Modifier = Modifier,
) {
    var code by remember(observation) { mutableStateOf("") }
    Column(modifier.verticalScroll(rememberScrollState())) {
        Text("Compare these six words with the words on your computer or TV.")
        Text(observation.phrase.joinToString(" "), style = MaterialTheme.typography.titleLarge)
        if (ui.codeReady) {
            OutlinedTextField(
                code,
                { code = it.uppercase(Locale.ROOT).take(MANUAL_CODE_LENGTH) },
                label = { Text("One-use code") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
            )
            Button(
                onClick = {
                    val submitted = code
                    code = ""
                    actions.pair(submitted)
                },
                enabled = !ui.busy && code.matches(Regex("[A-Z2-7]{12}")),
            ) { Text("Pair Mediacenter") }
        } else {
            Button(onClick = { actions.confirm(observation) }, enabled = !ui.busy) { Text("These words match") }
        }
        TextButton(onClick = actions.reject) { Text("Words do not match") }
    }
}

@Composable
private fun MediacenterCameraPreview(
    onCandidate: (PairingCandidate) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scanner = remember { mutableStateOf<MediacenterCameraScanner?>(null) }
    AndroidView(factory = {
        TextureView(it).also { view ->
            scanner.value = MediacenterCameraScanner(context, view, onCandidate, onError).also { it.start() }
        }
    }, modifier = modifier.fillMaxWidth().height(260.dp), onRelease = {
        scanner.value?.close()
        scanner.value = null
    })
    DisposableEffect(Unit) {
        onDispose {
            scanner.value?.close()
            scanner.value = null
        }
    }
}

private const val MANUAL_CODE_LENGTH = 12
