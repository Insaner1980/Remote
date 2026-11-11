package com.emmah.colordock.remote.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
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
    val name = if (state.selected == ControlTarget.LgTv) tvName else ControlTarget.Mediacenter.label
    Column(modifier) {
        TextButton(
            onClick = { open = true },
            modifier = Modifier.fillMaxWidth().testTag("target-selector"),
            contentPadding = PaddingValues(0.dp),
        ) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    "${state.selected.label} · ${if (state.ready(state.selected)) "Connected" else "Unavailable"} ▾",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (open) {
            AlertDialog(
                onDismissRequest = { open = false },
                title = { Text("Control device") },
                text = {
                    Column {
                        ControlTarget.entries.forEach { target ->
                            TextButton(onClick = {
                                open = false
                                onSelect(target)
                            }) {
                                Text("${target.label} · ${if (state.ready(target)) "Connected" else "Unavailable"}")
                            }
                        }
                        TextButton(onClick = {
                            open = false
                            onConnection()
                        }) { Text("${state.selected.label} connection") }
                    }
                },
                confirmButton = { TextButton(onClick = { open = false }) { Text("Close") } },
            )
        }
    }
}
