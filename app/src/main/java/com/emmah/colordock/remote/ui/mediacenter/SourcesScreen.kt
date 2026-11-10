package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.emmah.colordock.remote.mediacenter.protocol.Availability
import com.emmah.colordock.remote.mediacenter.protocol.Source

@Composable
internal fun SourcesScreen(
    state: SourcesState,
    folders: FolderState,
    busy: Boolean,
    actions: SourceActions,
    modifier: Modifier = Modifier,
) {
    var browsing by remember { mutableStateOf(false) }
    Column(modifier.testTag("sources-screen")) {
        Text("Library sources")
        Text("Removing a source removes its library association. Files stay on the computer or drive.")
        TextButton(onClick = actions.refresh, enabled = state.connected) { Text("Refresh sources") }
        TextButton(onClick = {
            browsing = !browsing
        }, enabled = state.connected) { Text(if (browsing) "Close folder browser" else "Add or reattach folder") }
        if (state.message.isNotEmpty()) Text(state.message)
        if (state.loading) Text("Loading sources…")
        if (state.items.isEmpty() && !state.loading) Text("No library sources.")
        state.items.forEach { source -> SourceRow(source, busy, actions) }
        if (state.cursor != null) TextButton(onClick = actions.next, enabled = !state.loading) { Text("More sources") }
        if (busy) Text("Waiting for native completion. A cancelled scan retains existing metadata.")
        actions.cancelScan?.let { cancel -> TextButton(onClick = cancel) { Text("Cancel scan") } }
        RecoveredScanControls(state, actions.cancelRecoveredScan)
        if (browsing) FolderBrowser(folders, busy, actions.folders)
    }
}

@Composable
private fun SourceRow(
    source: Source,
    busy: Boolean,
    actions: SourceActions,
) {
    Column {
        Text(source.label)
        Text(source.kinds.joinToString { it.wire })
        Text(
            if (source.scanning) {
                "Scanning…"
            } else if (source.availability ==
                Availability.AVAILABLE
            ) {
                "Available"
            } else {
                "Unavailable. Reconnect the drive, then refresh. For a drive without a UUID, " +
                    "choose its folder to confirm reattachment."
            },
        )
        TextButton(
            onClick = { actions.scan(source.id) },
            enabled =
                !busy && !source.scanning && source.availability == Availability.AVAILABLE,
        ) { Text("Scan ${source.label}") }
        TextButton(
            onClick = { actions.remove(source.id) },
            enabled = !busy,
        ) { Text("Remove ${source.label} association") }
    }
}

@Composable
private fun RecoveredScanControls(
    state: SourcesState,
    cancel: (String) -> Unit,
) {
    Column {
        state.recoveredScans.forEach { scan ->
            Text(
                if (scan.cancellationRequested) {
                    "Cancellation requested. Waiting for the scan to stop."
                } else {
                    "Recovered scan is pending."
                },
            )
            TextButton(
                onClick = { cancel(scan.operationId) },
                enabled = state.connected && !scan.cancellationRequested,
            ) {
                Text("Cancel recovered scan")
            }
        }
    }
}
