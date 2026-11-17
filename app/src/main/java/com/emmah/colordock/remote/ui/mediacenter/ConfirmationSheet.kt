package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.testTag
import com.emmah.colordock.remote.R
import com.emmah.colordock.remote.mediacenter.protocol.ActionKind
import com.emmah.colordock.remote.mediacenter.protocol.ConfirmationPreview
import com.emmah.colordock.remote.ui.DockIcon
import com.emmah.colordock.remote.ui.RemoteDimens

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
            val icon =
                when (preview.action) {
                    ActionKind.REMOVE_SOURCE, ActionKind.REVOKE_DEVICE -> R.drawable.ic_delete
                    ActionKind.EJECT_DRIVE -> R.drawable.ic_eject
                    ActionKind.END_SESSION -> R.drawable.ic_stop
                    else -> null
                }
            val colors = MaterialTheme.colorScheme
            Button(
                onClick = actions.confirm,
                enabled = !busy,
                colors =
                    if (icon != null) {
                        ButtonDefaults.buttonColors(containerColor = colors.error, contentColor = colors.onError)
                    } else {
                        ButtonDefaults.buttonColors()
                    },
            ) {
                if (icon != null) {
                    DockIcon(icon, Modifier.testTag("destructive-action-icon"))
                    Spacer(Modifier.width(RemoteDimens.ItemGap))
                }
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
    Column(
        Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(RemoteDimens.ItemGap),
    ) {
        labels.forEach { label ->
            Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
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
