package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
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
    title: String = "Confirm Mediacenter action?",
) {
    AlertDialog(
        onDismissRequest = { if (!busy) actions.cancel() },
        modifier = modifier,
        title = { Text(confirmationTitle(preview.action, title)) },
        text = {
            Column {
                preview.browserContext?.let { Text(it.hostname) }
                preview.affectedLabels.filterNot { it == preview.browserContext?.hostname }.forEach { Text(it) }
                if (preview.action == ActionKind.APPROVE_BROWSER_ORIGIN) {
                    Text("This approval applies to the current browser session.")
                }
                if (preview.action == ActionKind.REMOVE_SOURCE) Text("Media files stay on the drive.")
                if (preview.action ==
                    ActionKind.REATTACH_SOURCE
                ) {
                    Text(
                        "This drive has no filesystem UUID. Confirm this physical attachment; " +
                            "saved media kinds and library records are preserved.",
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = actions.confirm, enabled = !busy) { Text("Confirm") } },
        dismissButton = { TextButton(onClick = actions.cancel, enabled = !busy) { Text("Cancel") } },
    )
}

private fun confirmationTitle(
    action: ActionKind,
    defaultTitle: String,
): String =
    when (action) {
        ActionKind.CHANGE_VPN -> "Change VPN connection?"
        ActionKind.REVOKE_DEVICE -> "Revoke phone access?"
        ActionKind.BEGIN_PAIRING -> "Pair another phone?"
        ActionKind.ADD_SOURCE -> "Add this library folder?"
        ActionKind.OPEN_BROWSER -> "Open the streaming service?"
        ActionKind.APPROVE_BROWSER_ORIGIN -> "Allow input on this site?"
        ActionKind.REMOVE_SOURCE -> "Remove library association?"
        ActionKind.REATTACH_SOURCE -> "Confirm this drive attachment?"
        else -> defaultTitle
    }
