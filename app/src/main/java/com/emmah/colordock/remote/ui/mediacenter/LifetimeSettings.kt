package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand
import com.emmah.colordock.remote.mediacenter.protocol.SettingsEndSessionParams
import com.emmah.colordock.remote.mediacenter.protocol.SettingsStartAtLoginSetParams

@Composable
internal fun LifetimeSettings(
    state: MediaSessionState,
    ready: Boolean,
    send: (RemoteCommand) -> Unit,
) {
    Column {
        state.settings.native?.let { native ->
            Text("Remote availability")
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Start after computer login", Modifier.weight(1f))
                Switch(
                    checked = native.startAtLogin,
                    onCheckedChange = {
                        send(
                            RemoteCommand.SettingsStartAtLoginSet(SettingsStartAtLoginSetParams(it)),
                        )
                    },
                    enabled = ready,
                    modifier = Modifier.semantics { contentDescription = "Start after computer login" },
                )
            }
            Text("Viewing idle inhibition: ${native.idleInhibition?.wire ?: "unavailable"}")
            TextButton(
                enabled = ready,
                onClick = { send(RemoteCommand.SettingsEndSession(SettingsEndSessionParams())) },
            ) { Text("End session") }
            Text("Stops owned playback after confirmation. Mediacenter stays available for Remote.")
        }
    }
}
