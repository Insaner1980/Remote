package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.emmah.colordock.remote.R
import com.emmah.colordock.remote.ui.DockIcon
import com.emmah.colordock.remote.ui.RemoteDimens

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
            MediaKey("Sound", R.drawable.ic_volume_up, true, {
                open = true
                actions.refresh()
            })
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
    val items = outputs?.outputs.orEmpty()
    val names = soundOutputLabels(items)
    Column(verticalArrangement = Arrangement.spacedBy(RemoteDimens.BlockGap)) {
        SoundOutputStatus(state)
        ListGroup {
            items.forEachIndexed { index, output ->
                androidx.compose.runtime.key(output.id) {
                    ListRow(
                        names.getValue(output.id),
                        "Unavailable".takeUnless { output.available },
                        listRowPosition(index, items.size),
                        Modifier.semantics { this.selected = output.id == selected },
                        onClick = { actions.choose(output.id) },
                        enabled = output.available && !state.busy,
                        role = Role.RadioButton,
                        trailing = {
                            RadioButton(
                                output.id == selected,
                                onClick = null,
                                enabled = output.available && !state.busy,
                            )
                        },
                    )
                }
            }
        }
        val chosen = items.firstOrNull { it.id == selected && it.available }
        Button(
            onClick = { chosen?.let { actions.test(it.id) } },
            enabled = chosen != null && !state.busy,
            shape = CircleShape,
        ) {
            DockIcon(R.drawable.ic_volume_up)
            Spacer(Modifier.width(8.dp))
            Text("Test sound")
        }
    }
}

@Composable
private fun ColumnScope.SoundOutputStatus(state: AudioSettingsState) {
    val outputs = state.outputs
    val items = outputs?.outputs.orEmpty()
    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (outputs?.unavailable == true) {
        Text(
            "Output unavailable",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    state.notice?.let {
        Text(
            it.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (items.isEmpty()) {
        Text(
            "No sound outputs",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
