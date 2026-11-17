package com.emmah.colordock.remote.ui.mediacenter

import android.Manifest
import android.content.Intent
import android.provider.Settings
import android.view.TextureView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.emmah.colordock.remote.R
import com.emmah.colordock.remote.mediacenter.AndroidMediacenterNetwork
import com.emmah.colordock.remote.mediacenter.CertificateObservation
import com.emmah.colordock.remote.mediacenter.MediacenterCameraScanner
import com.emmah.colordock.remote.mediacenter.MediacenterConnectionState
import com.emmah.colordock.remote.mediacenter.MediacenterEndpoint
import com.emmah.colordock.remote.mediacenter.protocol.PairingCandidate
import com.emmah.colordock.remote.ui.DockIcon
import com.emmah.colordock.remote.ui.RemoteDimens
import java.util.Locale

@Composable
fun MediacenterSetupDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    model: @Composable () -> MediacenterViewModel = { viewModel() },
) {
    val viewModel = model()
    val setup by viewModel.ui.collectAsStateWithLifecycle()
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
                        viewModel.background()
                    }

                    else -> {}
                }
            }
        lifecycle.lifecycle.addObserver(observer)
        onDispose {
            lifecycle.lifecycle.removeObserver(observer)
            viewModel.closeSetup()
        }
    }
    AlertDialog(onDismissRequest = onDismiss, modifier = modifier, title = {
        Text(if (setup.saved) "Connection" else "Add computer")
    }, text = {
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
            val pairing = viewModel.pairing
            ManualVerificationActions(pairing::confirm, pairing::reject, pairing::pairManual)
        }
    val connectedNetwork =
        ui.networkName.takeIf { name ->
            connection == MediacenterConnectionState.Connected && networks.any { it.label == name }
        }
    Column(
        modifier.fillMaxWidth().heightIn(max = 560.dp),
        verticalArrangement = Arrangement.spacedBy(RemoteDimens.BlockGap),
    ) {
        MediacenterNetworkOptions(networks, ui, connectedNetwork, {
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

            !ui.saved || ui.observation != null -> {
                MediacenterManualSetup(
                    ui,
                    onPermission,
                    viewModel.pairing::probe,
                    manualActions,
                    Modifier.weight(1f, fill = false),
                )
            }
        }
        if (!ui.lanGranted) MediacenterLanSettings()
        if (ui.busy) androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
        SetupNotice(ui, connection)
        if (connectedNetwork == null) MediacenterConnectionStatus(connection)
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
    connectedNetwork: String?,
    onSelect: (AndroidMediacenterNetwork) -> Unit,
) {
    Column {
        if (networks.isEmpty()) {
            Text("Wi-Fi", style = MaterialTheme.typography.titleMedium)
            if (ui.lanGranted) Text("Connect to Wi-Fi")
        }
        networks.forEach { network ->
            MediacenterDialogAction(
                network.label.substringBefore(" ("),
                R.drawable.ic_wifi,
                onClick = { onSelect(network) },
                enabled = !ui.busy,
                supporting = "Connected".takeIf { connectedNetwork == network.label },
                trailingIcon = R.drawable.ic_check.takeIf { ui.networkName == network.label },
            )
        }
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
                "Connected"
            }

            MediacenterConnectionState.Connecting -> {
                "Connecting…"
            }

            MediacenterConnectionState.Synchronizing -> {
                "Connecting…"
            }

            MediacenterConnectionState.Reconnecting -> {
                "Connecting…"
            }

            MediacenterConnectionState.IdentityChanged -> {
                "Computer changed. Pair again."
            }

            MediacenterConnectionState.VersionMismatch -> {
                "Update Remote and Mediacenter"
            }

            MediacenterConnectionState.NetworkUnavailable -> {
                "Choose Wi-Fi"
            }

            MediacenterConnectionState.Failed -> {
                "Couldn't connect"
            }

            MediacenterConnectionState.PairingRequired -> {
                "Pair again"
            }

            MediacenterConnectionState.ResyncRequired -> {
                "Reconnect"
            }

            MediacenterConnectionState.PermissionRequired -> {
                "Allow Wi-Fi access"
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
            MediacenterDialogAction("Reconnect", R.drawable.ic_refresh, { connections.connect() })
            MediacenterDialogAction("Find computer", R.drawable.ic_search, connections::find)
            candidates.forEach { endpoint ->
                MediacenterDialogAction("Connect", R.drawable.ic_wifi, { connections.connect(endpoint) })
            }
            MediacenterDialogAction("Remove computer", R.drawable.ic_delete, connections::forget, destructive = true)
        } else {
            TextButton(onClick = connections::forget) { Text("Try again") }
        }
    }
}

internal class ManualVerificationActions(
    val confirm: (CertificateObservation) -> Unit,
    val reject: () -> Unit,
    val pair: (String) -> Unit,
)

@Composable
internal fun MediacenterManualSetup(
    ui: MediacenterSetupState,
    onPermission: (MediacenterSetupPermission) -> Unit,
    onProbe: (String, Int) -> Unit,
    actions: ManualVerificationActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(RemoteDimens.BlockGap)) {
        val observation = ui.observation
        if (observation != null) {
            ManualVerification(observation, ui, actions)
        } else {
            MediacenterSetupScreen(ui.lanGranted, ui.cameraDenied, onPermission, onProbe)
        }
    }
}

@Composable
private fun ManualVerification(
    observation: CertificateObservation,
    ui: MediacenterSetupState,
    actions: ManualVerificationActions,
    modifier: Modifier = Modifier,
) {
    var code by remember(observation) { mutableStateOf("") }
    Column(
        modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(RemoteDimens.BlockGap),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(RemoteDimens.ItemGap)) {
            Text("Match the words on the computer")
            Text(observation.phrase.joinToString(" "), style = MaterialTheme.typography.titleLarge)
        }
        if (ui.codeReady) {
            OutlinedTextField(
                code,
                { code = it.uppercase(Locale.ROOT).take(MANUAL_CODE_LENGTH) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Code") },
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
            ) { Text("Connect") }
        } else {
            Button(onClick = { actions.confirm(observation) }, enabled = !ui.busy) { Text("These words match") }
        }
        TextButton(onClick = actions.reject) { Text("Cancel") }
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

@Composable
private fun SetupNotice(
    ui: MediacenterSetupState,
    connection: MediacenterConnectionState,
) {
    if (ui.forgetFailed) {
        Text("Couldn't remove computer")
    } else if (!ui.saved && !ui.busy) {
        if (ui.status.isNotEmpty() && connection != MediacenterConnectionState.Connected) Text("Try again")
    }
}
