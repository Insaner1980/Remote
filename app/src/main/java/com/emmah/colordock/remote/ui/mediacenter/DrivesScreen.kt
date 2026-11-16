package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.emmah.colordock.remote.mediacenter.protocol.OperationStatus

@Composable
internal fun DrivesScreen(
    state: DrivesState,
    busy: Boolean,
    actions: DriveActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier.testTag("drive-settings"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("USB drives", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            RefreshAction(actions.refresh, enabled = state.connected && !state.loading, label = "Refresh drives")
        }
        ListStatus(state.loading, state.connected, state.message, state.items.isEmpty(), "drives")
        RecoveredDriveRows(state, actions)
        state.items.forEach { drive ->
            androidx.compose.runtime.key(drive.id) {
                DriveRow(drive, state.connected && !busy, actions)
            }
        }
        actions.cancel?.let { cancel -> TextButton(onClick = cancel, enabled = state.connected) { Text("Cancel") } }
    }
}

@Composable
private fun RecoveredDriveRows(
    state: DrivesState,
    actions: DriveActions,
) {
    Column {
        state.recovered
            .filter {
                it.operation.status != OperationStatus.CANCELLED &&
                    (
                        it.operation.status != OperationStatus.SUCCEEDED ||
                            it.kind == com.emmah.colordock.remote.mediacenter.DriveWork.EJECT
                    )
            }.forEach { work ->
                androidx.compose.runtime.key(work.operation.operationId) {
                    Text(
                        when {
                            work.operation.status == OperationStatus.SUCCEEDED -> {
                                "Safe to unplug"
                            }

                            work.operation.cancellationRequested && work.running -> {
                                "Stopping…"
                            }

                            work.operation.status == OperationStatus.FAILED -> {
                                errorNotice(
                                    work.operation.errorCode,
                                    true,
                                ).label
                            }

                            else -> {
                                operationLabel(work.operation.status)
                            }
                        },
                    )
                    if (work.running) {
                        TextButton(
                            onClick = { actions.cancelRecovered(work.operation.operationId) },
                            enabled = state.connected && !work.operation.cancellationRequested,
                        ) { Text("Cancel") }
                    }
                }
            }
    }
}

@Composable
private fun DriveRow(
    drive: com.emmah.colordock.remote.mediacenter.protocol.Drive,
    enabled: Boolean,
    actions: DriveActions,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(drive.label, Modifier.weight(1f))
        ItemActions(
            "Drive options for ${drive.label}",
            listOf(
                MenuAction(
                    "Eject",
                    enabled && drive.removable,
                ) { actions.eject(drive.id) },
            ),
            enabled = enabled,
        )
    }
    drive.volumes.filterNot { it.mounted }.forEach { volume ->
        androidx.compose.runtime.key(volume.id) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (volume.label != drive.label) Text(volume.label, Modifier.weight(1f))
                TextButton(
                    onClick = { actions.mount(volume.id) },
                    enabled = enabled,
                ) { Text("Connect") }
            }
        }
    }
}
