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

@Composable
internal fun MediaRecoveryStatus(
    pending: List<PendingCommand>,
    uncertain: Boolean,
    recoveryAvailable: Boolean,
    modifier: Modifier = Modifier,
    notice: ActionNotice? = null,
) {
    val unresolved = pending.count { !it.resolved }
    val unknown = pending.any { it.resolved && it.status == OperationStatus.OUTCOME_UNKNOWN }
    val problem = notice != null && notice != ActionNotice.SAFE_TO_UNPLUG
    val needed = unresolved > 0 || unknown || uncertain || !recoveryAvailable || problem
    var open by remember(needed) { mutableStateOf(false) }
    if (!needed) return
    IconButton(onClick = { open = true }, modifier = modifier) {
        DockIcon(R.drawable.ic_error, label = "Action needs attention", tint = MaterialTheme.colorScheme.primary)
    }
    if (open) {
        RecoveryDialog(unknown || uncertain, recoveryAvailable, unresolved, notice) { open = false }
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
    val showNotice = state.notice != null && relevant && !representedDrive
    if (!state.busy && !showNotice) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("media-action-progress"))
        if (showNotice) Text(state.notice.label)
    }
}

@Composable
private fun RecoveryDialog(
    unknown: Boolean,
    recoveryAvailable: Boolean,
    unresolved: Int,
    notice: ActionNotice?,
    dismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { dismiss() },
        title = { Text("Action status") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                when {
                    unknown || notice == ActionNotice.NOT_CONFIRMED -> Text("Not confirmed")
                    !recoveryAvailable -> Text("Unavailable")
                    unresolved > 0 -> Text("Working…")
                    else -> notice?.let { Text(it.label) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { dismiss() }) { Text("Close") } },
    )
}
