package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
        title = { Text(title) },
        text = { Column { preview.affectedLabels.forEach { Text(it) } } },
        confirmButton = { TextButton(onClick = actions.confirm, enabled = !busy) { Text("Confirm") } },
        dismissButton = { TextButton(onClick = actions.cancel, enabled = !busy) { Text("Cancel") } },
    )
}
