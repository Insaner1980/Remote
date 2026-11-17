package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.emmah.colordock.remote.R
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand
import com.emmah.colordock.remote.mediacenter.protocol.SettingsEndSessionParams
import com.emmah.colordock.remote.mediacenter.protocol.SettingsStartAtLoginSetParams
import com.emmah.colordock.remote.ui.DockIcon

@Composable
internal fun LifetimeSettings(
    state: MediaSessionState,
    ready: Boolean,
    send: (RemoteCommand) -> Unit,
) {
    ListGroup {
        state.settings.native?.let { native ->
            ListRow("Start automatically", null, ListRowPosition.First, trailing = {
                Switch(
                    native.startAtLogin,
                    { send(RemoteCommand.SettingsStartAtLoginSet(SettingsStartAtLoginSetParams(it))) },
                    enabled = ready,
                    modifier = Modifier.semantics { contentDescription = "Start automatically" },
                )
            })
            val stopColor = MaterialTheme.colorScheme.error
            ListRow(
                "Stop playback",
                null,
                ListRowPosition.Last,
                titleColor = stopColor,
                leading = {
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        DockIcon(R.drawable.ic_stop, Modifier.size(20.dp), tint = stopColor)
                    }
                },
                trailing = {},
                onClick = { send(RemoteCommand.SettingsEndSession(SettingsEndSessionParams())) },
                enabled = ready,
            )
        }
    }
}
