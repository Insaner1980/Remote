package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.emmah.colordock.remote.mediacenter.protocol.Availability
import com.emmah.colordock.remote.mediacenter.protocol.OperationStatus
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
    Column(modifier.testTag("sources-screen"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Folders", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            RefreshAction(actions.refresh, enabled = state.connected && !state.loading, label = "Refresh folders")
        }
        TextButton(onClick = {
            browsing = true
            actions.folders.browse(null)
        }, enabled = state.connected) { Text("Add folder") }
        ListStatus(state.loading, state.connected, state.message, state.items.isEmpty(), "folders")
        state.items.forEach {
            androidx.compose.runtime.key(it.id) {
                SourceRow(it, state.connected && !busy, actions)
            }
        }
        if (state.cursor != null) TextButton(onClick = actions.next, enabled = !state.loading) { Text("More") }
        actions.cancelScan?.let { cancel -> TextButton(onClick = cancel) { Text("Cancel update") } }
        RecoveredScanControls(state, actions.cancelRecoveredScan)
        if (browsing) FolderChooser(folders, busy, actions.folders) { browsing = false }
    }
}

@Composable
private fun SourceRow(
    source: Source,
    enabled: Boolean,
    actions: SourceActions,
) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(source.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (source.scanning) {
                Text("Updating…", style = MaterialTheme.typography.bodySmall)
            } else if (source.availability !=
                Availability.AVAILABLE
            ) {
                Text("Offline", style = MaterialTheme.typography.bodySmall)
            }
        }
        ItemActions(
            "Folder options for ${source.label}",
            listOf(
                MenuAction("Update", enabled && !source.scanning && source.availability == Availability.AVAILABLE) {
                    actions.scan(source.id)
                },
                MenuAction("Remove", enabled) { actions.remove(source.id) },
            ),
            enabled = enabled,
        )
    }
}

@Composable
private fun RecoveredScanControls(
    state: SourcesState,
    cancel: (String) -> Unit,
) {
    Column {
        state.recoveredScans
            .filter {
                it.status !in
                    listOf(
                        OperationStatus.SUCCEEDED,
                        OperationStatus.CANCELLED,
                    )
            }.forEach { scan ->
                androidx.compose.runtime.key(scan.operationId) {
                    Text(if (scan.cancellationRequested) "Stopping…" else operationLabel(scan.status))
                    if (scan.status in listOf(OperationStatus.ACCEPTED, OperationStatus.RUNNING)) {
                        TextButton(
                            onClick = { cancel(scan.operationId) },
                            enabled =
                                state.connected && !scan.cancellationRequested,
                        ) {
                            Text("Cancel update")
                        }
                    }
                }
            }
    }
}

@Composable
private fun FolderChooser(
    state: FolderState,
    busy: Boolean,
    actions: FolderActions,
    dismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Choose folder") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                FolderBrowser(
                    state,
                    busy,
                    actions.copy(choose = { node, kinds ->
                        actions.choose(node, kinds)
                        dismiss()
                    }),
                )
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text("Close") } },
    )
}
