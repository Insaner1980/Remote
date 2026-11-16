package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.emmah.colordock.remote.mediacenter.protocol.SupportedState

@Composable
internal fun StreamingServices(
    streaming: StreamingState,
    enabled: Boolean,
    open: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val catalog = streaming.capabilities ?: return
    if (catalog.services.isEmpty()) return
    var showing by remember { mutableStateOf(false) }
    Column(modifier) {
        OutlinedButton(onClick = {
            showing = true
        }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("Streaming services") }
        if (showing && enabled) {
            AlertDialog(
                onDismissRequest = { showing = false },
                title = { Text("Streaming services") },
                text = {
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                        if (!catalog.browserBridge) {
                            Text("Open the enrolled Chrome profile on the computer to use streaming controls.")
                        }
                        catalog.services.forEach { service ->
                            val serviceId = service.serviceId
                            TextButton(
                                enabled = streaming.canLaunch(serviceId),
                                onClick = {
                                    showing = false
                                    open(serviceId)
                                },
                            ) { Text(serviceLabel(serviceId)) }
                            val limitation =
                                service.limitation ?: when (service.support) {
                                    SupportedState.UNSUPPORTED -> "Unavailable from Remote"
                                    SupportedState.PARTIAL -> "Website controls"
                                    SupportedState.SUPPORTED -> null
                                }
                            if (limitation != null) Text(limitation, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showing = false }) { Text("Done") } },
            )
        }
    }
}

internal fun serviceLabel(serviceId: String): String =
    when (serviceId) {
        "netflix" -> "Netflix"
        "youtube" -> "YouTube"
        "hbo_max" -> "HBO Max"
        "viaplay" -> "Viaplay"
        "mtv_katsomo" -> "MTV Katsomo"
        "ruutu" -> "Ruutu"
        "yle_areena" -> "Yle Areena"
        "plex" -> "Plex"
        else -> serviceId.replace('_', ' ').replaceFirstChar(Char::titlecase)
    }
