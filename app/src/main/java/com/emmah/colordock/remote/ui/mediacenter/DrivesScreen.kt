package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import com.emmah.colordock.remote.R
import com.emmah.colordock.remote.mediacenter.protocol.OperationStatus

@Composable
internal fun DrivesScreen(
    state: DrivesState,
    busy: Boolean,
    actions: DriveActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier.testTag("drive-settings")) {
        SectionLabel("USB drives", trailing = {
            RefreshAction(actions.refresh, enabled = state.connected && !state.loading, label = "Refresh drives")
        })
        Column {
            ListStatus(state.loading, state.connected, state.message, false, "drives")
            RecoveredDriveRows(state, actions)
            ListGroup {
                val loaded = state.connected && !state.loading && state.message.isEmpty()
                if (loaded && state.items.isEmpty()) {
                    EmptyListRow("No USB drive")
                }
                state.items.forEachIndexed { index, drive ->
                    androidx.compose.runtime.key(drive.id) {
                        DriveRow(drive, state.connected && !busy, actions, listRowPosition(index, state.items.size))
                    }
                }
            }
        }
        actions.cancel?.let { cancel -> MediaKey("Cancel", null, state.connected, cancel) }
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
                        MediaKey(
                            "Cancel",
                            null,
                            state.connected && !work.operation.cancellationRequested,
                            { actions.cancelRecovered(work.operation.operationId) },
                        )
                    }
                }
            }
    }
}

@Composable
private fun ColumnScope.DriveRow(
    drive: com.emmah.colordock.remote.mediacenter.protocol.Drive,
    enabled: Boolean,
    actions: DriveActions,
    position: ListRowPosition,
) {
    val volumes = drive.volumes.filterNot { it.mounted }
    val headerPosition =
        if (volumes.isEmpty()) {
            position
        } else {
            when (position) {
                ListRowPosition.Single -> ListRowPosition.First
                ListRowPosition.Last -> ListRowPosition.Middle
                else -> position
            }
        }
    ListRow(drive.label, null, headerPosition, trailing = {
        ItemActions(
            "Drive options for ${drive.label}",
            listOf(
                MenuAction(
                    "Eject",
                    enabled && drive.removable,
                    destructive = true,
                    destructiveIcon = R.drawable.ic_eject,
                ) {
                    actions.eject(drive.id)
                },
            ),
            enabled = enabled,
        )
    })
    volumes.forEachIndexed { index, volume ->
        androidx.compose.runtime.key(volume.id) {
            val rowPosition =
                if (index == volumes.lastIndex &&
                    position in listOf(ListRowPosition.Single, ListRowPosition.Last)
                ) {
                    ListRowPosition.Last
                } else {
                    ListRowPosition.Middle
                }
            if (volume.label == drive.label) {
                ListRow(
                    "Connect",
                    null,
                    rowPosition,
                    leadingIcon = R.drawable.ic_add,
                    onClick = { actions.mount(volume.id) },
                    enabled = enabled,
                    trailing = {},
                )
            } else {
                ListRow(volume.label, null, rowPosition, trailing = {
                    MediaKey("Connect", null, enabled, { actions.mount(volume.id) })
                })
            }
        }
    }
}
