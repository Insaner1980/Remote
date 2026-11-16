package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import com.emmah.colordock.remote.R
import com.emmah.colordock.remote.mediacenter.protocol.MediaKind
import com.emmah.colordock.remote.ui.DockIcon

@Composable
internal fun FolderBrowser(
    state: FolderState,
    busy: Boolean,
    actions: FolderActions,
    modifier: Modifier = Modifier,
) {
    var kinds by remember(state.selected?.nodeId) { mutableStateOf(setOf(MediaKind.VIDEO)) }
    Column(modifier.testTag("folder-browser"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row {
            MediaIconButton(R.drawable.ic_home, "Folders", { actions.browse(null) }, state.connected)
            RefreshAction(actions.refresh, enabled = state.connected, label = "Refresh folders")
        }
        if (state.connected &&
            state.message.isNotEmpty()
        ) {
            Text(
                "Try again",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.loading) {
            Text(
                "Loading…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.selected?.let { selected ->
            Text(selected.label, style = MaterialTheme.typography.titleMedium)
            ListGroup {
                MediaKind.entries.forEachIndexed { index, kind ->
                    ListRow(mediaKindLabel(kind), null, listRowPosition(index, MediaKind.entries.size), trailing = {
                        Checkbox(checked = kind in kinds, onCheckedChange = { checked ->
                            kinds = if (checked) kinds + kind else kinds - kind
                        })
                    })
                }
            }
            Button(
                onClick = { actions.choose(selected, kinds.toList()) },
                enabled =
                    state.connected && !busy && !state.loading && kinds.isNotEmpty(),
            ) {
                DockIcon(R.drawable.ic_add)
                Spacer(Modifier.width(8.dp))
                Text("Add folder")
            }
        }
        ListGroup {
            state.items.forEachIndexed { index, node ->
                ListRow(
                    node.label,
                    null,
                    listRowPosition(index, state.items.size),
                    onClick = { actions.browse(node) },
                    enabled = !state.loading && !busy,
                )
            }
        }
        if (state.cursor != null) MediaKey("More", null, !state.loading, actions.next, Modifier.fillMaxWidth())
    }
}
