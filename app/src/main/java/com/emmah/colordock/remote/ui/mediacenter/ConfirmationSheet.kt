package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.emmah.colordock.remote.mediacenter.protocol.ActionKind
import com.emmah.colordock.remote.mediacenter.protocol.ConfirmationPreview

@Composable
internal fun ConfirmationSheet(
    preview: ConfirmationPreview,
    busy: Boolean,
    actions: ConfirmationActions,
    modifier: Modifier = Modifier,
    title: String = "Continue?",
) {
    AlertDialog(
        onDismissRequest = { if (!busy) actions.cancel() },
        modifier = modifier,
        title = { Text(confirmationTitle(preview.action, title)) },
        text = { ConfirmationRows(preview) },
        confirmButton = {
            val destructive =
                when (preview.action) {
                    ActionKind.REMOVE_SOURCE,
                    ActionKind.REVOKE_DEVICE,
                    ActionKind.EJECT_DRIVE,
                    ActionKind.END_SESSION,
                    -> true

                    else -> false
                }
            val colors = MaterialTheme.colorScheme
            Button(
                onClick = actions.confirm,
                enabled = !busy,
                colors =
                    if (destructive) {
                        ButtonDefaults.buttonColors(containerColor = colors.error, contentColor = colors.onError)
                    } else {
                        ButtonDefaults.buttonColors()
                    },
            ) {
                Text("Confirm")
            }
        },
        dismissButton = { TextButton(onClick = actions.cancel, enabled = !busy) { Text("Cancel") } },
    )
}

@Composable
private fun ConfirmationRows(preview: ConfirmationPreview) {
    val hostname = preview.browserContext?.hostname
    val labels =
        listOfNotNull(hostname) +
            preview.affectedLabels.filterNot { it == hostname }.map { label ->
                when (preview.action) {
                    ActionKind.CHANGE_AUDIO -> soundOutputName(label)
                    ActionKind.REPLACE_ACTIVITY -> mediaTitle(label)
                    else -> label
                }
            }
    ListGroup(Modifier.verticalScroll(rememberScrollState())) {
        labels.forEachIndexed { index, label ->
            ListRow(label, null, listRowPosition(index, labels.size), maxTitleLines = Int.MAX_VALUE)
        }
    }
}

private fun confirmationTitle(
    action: ActionKind,
    defaultTitle: String,
): String =
    when (action) {
        ActionKind.CHANGE_VPN -> "Change VPN?"
        ActionKind.REVOKE_DEVICE -> "Remove phone?"
        ActionKind.BEGIN_PAIRING -> "Add phone?"
        ActionKind.ADD_SOURCE -> "Add folder?"
        ActionKind.OPEN_BROWSER -> "Open app?"
        ActionKind.APPROVE_BROWSER_ORIGIN -> "Allow controls on this website?"
        ActionKind.REMOVE_SOURCE -> "Remove from library?"
        ActionKind.REATTACH_SOURCE -> "Use this drive?"
        ActionKind.EJECT_DRIVE -> "Eject drive?"
        ActionKind.END_SESSION -> "Stop playback?"
        ActionKind.CHANGE_AUDIO -> "Change sound output?"
        ActionKind.REPLACE_ACTIVITY -> "Play this instead?"
        else -> defaultTitle
    }
