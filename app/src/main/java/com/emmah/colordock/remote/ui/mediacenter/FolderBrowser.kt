package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.emmah.colordock.remote.mediacenter.protocol.MediaKind

@Composable
internal fun FolderBrowser(
    state: FolderState,
    busy: Boolean,
    actions: FolderActions,
    modifier: Modifier = Modifier,
) {
    var kinds by remember(state.selected?.nodeId) { mutableStateOf(setOf(MediaKind.VIDEO)) }
    Column(modifier.testTag("folder-browser")) {
        Row {
            androidx.compose.material3.IconButton(onClick = { actions.browse(null) }, enabled = state.connected) {
                com.emmah.colordock.remote.ui
                    .DockIcon(com.emmah.colordock.remote.R.drawable.ic_home, label = "Folders")
            }
            RefreshAction(actions.refresh, enabled = state.connected, label = "Refresh folders")
        }
        if (state.connected && state.message.isNotEmpty()) Text("Try again")
        if (state.loading) Text("Loading…")
        state.selected?.let { selected ->
            Text(selected.label)
            MediaKind.entries.forEach { kind ->
                Row {
                    Checkbox(checked = kind in kinds, onCheckedChange = { checked ->
                        kinds =
                            if (checked) kinds + kind else kinds - kind
                    })
                    Text(mediaKindLabel(kind))
                }
            }
            TextButton(
                onClick = { actions.choose(selected, kinds.toList()) },
                enabled =
                    state.connected && !busy && !state.loading && kinds.isNotEmpty(),
            ) {
                Text("Add folder")
            }
        }
        state.items.forEach { node ->
            TextButton(onClick = { actions.browse(node) }, enabled = !state.loading && !busy) { Text(node.label) }
        }
        if (state.cursor != null) TextButton(onClick = actions.next, enabled = !state.loading) { Text("More") }
    }
}
