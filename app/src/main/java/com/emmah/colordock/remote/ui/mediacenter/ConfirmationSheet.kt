package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    title: String = "Continue?",
) {
    AlertDialog(
        onDismissRequest = { if (!busy) actions.cancel() },
        modifier = modifier,
        title = { Text(confirmationTitle(preview.action, title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                preview.browserContext?.let { Text(it.hostname) }
                preview.affectedLabels.filterNot { it == preview.browserContext?.hostname }.forEach {
                    Text(
                        if (preview.action == ActionKind.CHANGE_AUDIO) {
                            soundOutputName(it)
                        } else if (preview.action == ActionKind.REPLACE_ACTIVITY) {
                            mediaTitle(it)
                        } else {
                            it
                        },
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
