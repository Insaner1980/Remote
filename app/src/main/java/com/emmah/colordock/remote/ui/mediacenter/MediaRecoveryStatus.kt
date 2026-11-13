package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.emmah.colordock.remote.mediacenter.PendingCommand
import com.emmah.colordock.remote.mediacenter.protocol.OperationStatus

private const val UNCERTAIN_ACTION_MESSAGE =
    "An earlier action has an uncertain outcome. " +
        "Inspect current Mediacenter state before issuing it again."

@Composable
internal fun MediaRecoveryStatus(
    pending: List<PendingCommand>,
    uncertain: Boolean,
    recoveryAvailable: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        val unresolved = pending.count { !it.resolved }
        if (unresolved > 0) Text("Mediacenter is reconciling $unresolved action(s).")
        if (pending.any { it.resolved && it.status == OperationStatus.OUTCOME_UNKNOWN }) {
            Text(UNCERTAIN_ACTION_MESSAGE)
        }
        if (uncertain) Text("Input outcome is uncertain. Inspect the computer before sending again.")
        if (!recoveryAvailable) Text("Command recovery is unavailable. Reconnect after freeing phone storage.")
    }
}
