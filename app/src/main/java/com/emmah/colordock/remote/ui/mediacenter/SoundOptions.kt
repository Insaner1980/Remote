package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@Composable
internal fun SoundOptions(
    state: AudioSettingsState,
    actions: SoundActions,
    modifier: Modifier = Modifier,
    showConfirmation: Boolean = true,
    initiallyOpen: Boolean = false,
) {
    var open by remember { mutableStateOf(initiallyOpen) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!initiallyOpen) {
            TextButton(onClick = {
                open = true
                actions.refresh()
            }) { Text("Sound") }
        }
        if (open) SoundOutputList(state, actions)
    }
    if (showConfirmation) {
        state.preview?.let {
            ConfirmationSheet(
                it,
                state.outputs == null || state.busy,
                ConfirmationActions(actions.confirm, actions.cancel),
                title = "Change sound output?",
            )
        }
    }
}

@Composable
private fun SoundOutputList(
    state: AudioSettingsState,
    actions: SoundActions,
) {
    val outputs = state.outputs
    val selected = outputs?.requestedOutputId ?: outputs?.effectiveOutputId
    val names = soundOutputLabels(outputs?.outputs.orEmpty())
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            RefreshAction(actions.refresh, enabled = !state.busy, label = "Refresh sound outputs")
        }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (outputs?.unavailable == true) Text("Output unavailable")
        state.notice?.let { Text(it.label) }
        if (outputs?.outputs.isNullOrEmpty()) Text("No sound outputs")
        outputs?.outputs?.forEach { output ->
            androidx.compose.runtime.key(output.id) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .selectable(
                            output.id == selected,
                            enabled = output.available && !state.busy,
                            role = Role.RadioButton,
                            onClick = { actions.choose(output.id) },
                        ).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(names.getValue(output.id), Modifier.weight(1f))
                    if (!output.available) Text("Unavailable")
                    RadioButton(
                        selected = output.id == selected,
                        onClick = null,
                        enabled =
                            output.available && !state.busy,
                    )
                }
            }
        }
        val chosen = outputs?.outputs?.firstOrNull { it.id == selected && it.available }
        Button(onClick = { chosen?.let { actions.test(it.id) } }, enabled = chosen != null && !state.busy) {
            Text("Test sound")
        }
    }
}
