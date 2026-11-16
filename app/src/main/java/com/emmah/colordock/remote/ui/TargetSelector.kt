package com.emmah.colordock.remote.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.emmah.colordock.remote.R

/** The device-name area of each target's existing header owns selection and connection access. */
@Composable
internal fun TargetSelector(
    state: ControlTargetState,
    tvName: String,
    onSelect: (ControlTarget) -> Unit,
    onConnection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val name =
        if (state.selected ==
            ControlTarget.LgTv
        ) {
            tvName.ifBlank { ControlTarget.LgTv.label }
        } else {
            ControlTarget.Mediacenter.label
        }
    Column(modifier) {
        OutlinedButton(
            onClick = { open = true },
            modifier = Modifier.fillMaxWidth().testTag("target-selector"),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Change device", style = MaterialTheme.typography.labelMedium)
                Text(
                    name,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (state.ready(state.selected)) "Connected" else "Not connected",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DockIcon(R.drawable.ic_keyboard_arrow_down)
        }
        if (open) {
            TargetSelectionDialog(state, { target ->
                open = false
                onSelect(target)
            }, {
                open = false
                onConnection()
            }, { open = false })
        }
    }
}

@Composable
private fun TargetSelectionDialog(
    state: ControlTargetState,
    onSelect: (ControlTarget) -> Unit,
    onConnection: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Control device") },
        text = {
            Column {
                ControlTarget.entries.forEach { target ->
                    TextButton(onClick = { onSelect(target) }, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(target.label, Modifier.weight(1f))
                            Text(
                                if (state.ready(target)) "Connected" else "Not connected",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                TextButton(onClick = onConnection) { Text("${state.selected.label} connection") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
