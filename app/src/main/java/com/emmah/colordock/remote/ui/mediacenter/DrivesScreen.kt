package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

@Composable
internal fun DrivesScreen(
    state: DrivesState,
    busy: Boolean,
    actions: DriveActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier.testTag("drive-settings")) {
        Text("USB drives")
        Text("Review all affected volumes and playback before safely removing a drive.")
        if (state.message.isNotEmpty()) Text(state.message)
        TextButton(onClick = actions.refresh, enabled = state.connected && !state.loading) { Text("Refresh drives") }
        if (state.connected && !state.loading && state.items.isEmpty()) Text("No removable drives found.")
        state.items.forEach { drive ->
            Text(drive.label)
            drive.volumes.forEach { volume ->
                Text("${volume.label} · ${if (volume.mounted) "Mounted" else "Not mounted"}")
                if (!volume.mounted) {
                    TextButton(onClick = { actions.mount(volume.id) }, enabled = state.connected && !busy) {
                        Text("Mount ${volume.label}")
                    }
                }
            }
            TextButton(onClick = { actions.eject(drive.id) }, enabled = state.connected && !busy && drive.removable) {
                Text("Safely remove ${drive.label}")
            }
        }
        actions.cancel?.let { cancel ->
            TextButton(onClick = cancel, enabled = state.connected) { Text("Cancel drive operation") }
        }
    }
}
