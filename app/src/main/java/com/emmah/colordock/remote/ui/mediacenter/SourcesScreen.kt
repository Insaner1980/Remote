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
import com.emmah.colordock.remote.R
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
        SectionLabel("Folders", trailing = {
            RefreshAction(actions.refresh, enabled = state.connected && !state.loading, label = "Refresh folders")
        })
        ListStatus(state.loading, state.connected, state.message, state.items.isEmpty(), "folders")
        ListGroup {
            ListRow(
                "Add folder",
                null,
                if (state.items.isEmpty()) ListRowPosition.Single else ListRowPosition.First,
                leadingIcon = R.drawable.ic_add,
                onClick = {
                    browsing = true
                    actions.folders.browse(null)
                },
                enabled = state.connected,
            )
            state.items.forEachIndexed { index, source ->
                androidx.compose.runtime.key(source.id) {
                    SourceRow(
                        source,
                        state.connected && !busy,
                        actions,
                        listRowPosition(index + 1, state.items.size + 1),
                    )
                }
            }
        }
        if (state.cursor != null) MediaKey("More", null, !state.loading, actions.next, Modifier.fillMaxWidth())
        actions.cancelScan?.let { cancel -> MediaKey("Cancel update", null, true, cancel, Modifier.fillMaxWidth()) }
        RecoveredScanControls(state, actions.cancelRecoveredScan)
        if (browsing) FolderChooser(folders, busy, actions.folders) { browsing = false }
    }
}

@Composable
private fun SourceRow(
    source: Source,
    enabled: Boolean,
    actions: SourceActions,
    position: ListRowPosition,
) {
    val supporting =
        when {
            source.scanning -> "Updating…"
            source.availability != Availability.AVAILABLE -> "Offline"
            else -> null
        }
    ListRow(source.label, supporting, position, trailing = {
        ItemActions(
            "Folder options for ${source.label}",
            listOf(
                MenuAction("Update", enabled && !source.scanning && source.availability == Availability.AVAILABLE) {
                    actions.scan(source.id)
                },
                MenuAction("Remove", enabled, destructive = true) { actions.remove(source.id) },
            ),
            enabled = enabled,
        )
    })
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
                    Text(
                        if (scan.cancellationRequested) "Stopping…" else operationLabel(scan.status),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (scan.status in listOf(OperationStatus.ACCEPTED, OperationStatus.RUNNING)) {
                        MediaKey(
                            "Cancel update",
                            null,
                            state.connected && !scan.cancellationRequested,
                            { cancel(scan.operationId) },
                        )
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
