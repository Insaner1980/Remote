package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TextInputSheet(
    sendText: (String) -> Boolean,
    dismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val field = remember { TextFieldState() }
    val text = field.text
    val committed =
        field.composition == null && Character.codePointCount(text, 0, text.length) in 1..MAX_BROWSER_TEXT_LENGTH
    AlertDialog(
        onDismissRequest = dismiss,
        modifier = modifier,
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        title = { Text("Type on computer") },
        text = {
            OutlinedSecureTextField(state = field, label = { Text("Text for the selected browser field") })
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val current = field.text
                    if (field.composition == null && sendText(current.toString())) dismiss()
                },
                enabled = committed,
            ) { Text("Send text") }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } },
    )
}
