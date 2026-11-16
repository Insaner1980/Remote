package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.emmah.colordock.remote.R
import com.emmah.colordock.remote.mediacenter.PendingCommand
import com.emmah.colordock.remote.mediacenter.protocol.OperationStatus
import com.emmah.colordock.remote.ui.DockIcon

private const val UNCERTAIN_ACTION_MESSAGE =
    "An earlier action has an uncertain outcome. " +
        "Inspect current Mediacenter state before issuing it again."

internal data class MediaFeedbackState(
    val pending: List<PendingCommand> = emptyList(),
    val uncertain: Boolean = false,
    val recoveryAvailable: Boolean = true,
)

@Composable
internal fun MediaRecoveryStatus(
    pending: List<PendingCommand>,
    uncertain: Boolean,
    recoveryAvailable: Boolean,
    modifier: Modifier = Modifier,
    message: String = "",
) {
    val unresolved = pending.count { !it.resolved }
    val unknown = pending.any { it.resolved && it.status == OperationStatus.OUTCOME_UNKNOWN }
    val needed = unresolved > 0 || unknown || uncertain || !recoveryAvailable || message.isNotBlank()
    var open by remember { mutableStateOf(false) }
    if (!needed) return
    IconButton(onClick = { open = true }, modifier = modifier) {
        DockIcon(R.drawable.ic_error, label = "Action needs attention", tint = MaterialTheme.colorScheme.primary)
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("Action status") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (message.isNotBlank()) Text(message)
                    if (unresolved > 0) Text("Mediacenter is reconciling $unresolved action(s).")
                    if (unknown) Text(UNCERTAIN_ACTION_MESSAGE)
                    if (uncertain) Text("Input outcome is uncertain. Inspect the computer before sending again.")
                    if (!recoveryAvailable) {
                        Text(
                            "Command recovery is unavailable. Reconnect after freeing phone storage.",
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Close") } },
        )
    }
}

@Composable
internal fun InlineMediaStatus(
    model: MediaTargetState,
    section: SettingsSection?,
) {
    val state = model.media
    val mediaDetails = state.destination == MediaDestination.Settings && section == SettingsSection.Media
    val representedDrive =
        mediaDetails &&
            model.drives.recovered.any {
                it.operation.requestId == state.messageOperation?.requestId &&
                    it.operation.operationId == state.messageOperation.operationId
            }
    val relevant = state.messageOperation != null || mediaDetails
    val showMessage = state.message.isNotBlank() && relevant && !representedDrive
    if (!state.busy && !showMessage) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth().testTag("media-action-progress"))
        }
        if (showMessage) Text(state.message)
    }
}
