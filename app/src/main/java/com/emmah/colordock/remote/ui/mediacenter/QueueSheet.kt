package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.emmah.colordock.remote.mediacenter.protocol.Availability
import com.emmah.colordock.remote.mediacenter.protocol.PlaylistsSaveParams
import com.emmah.colordock.remote.mediacenter.protocol.QueueMoveParams
import com.emmah.colordock.remote.mediacenter.protocol.QueueRemoveParams
import com.emmah.colordock.remote.mediacenter.protocol.QueueRepeatParams
import com.emmah.colordock.remote.mediacenter.protocol.QueueShuffleParams
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand
import com.emmah.colordock.remote.mediacenter.protocol.RepeatMode

@Composable
internal fun QueueSheet(
    state: MediaSessionState,
    actions: QueueActions,
    modifier: Modifier = Modifier,
) {
    val queue = state.queue
    val enabled = state.snapshot != null && !state.busy && queue?.revision == state.snapshot.queueSummary.revision
    AlertDialog(
        onDismissRequest = actions.close,
        modifier =
            modifier.testTag(
                "queue-editor",
            ),
        title = { Text("Queue") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (queue == null) {
                    Text("Loading queue…")
                } else if (queue.items.isEmpty()) {
                    Text("Queue is empty.")
                }
                QueueEntries(queue, enabled, actions)
                TextButton(
                    onClick = { actions.page(null) },
                    enabled = state.snapshot != null,
                ) { Text("First queue page / refresh") }
                queue?.nextCursor?.let { cursor ->
                    TextButton(onClick = { actions.page(cursor) }, enabled = enabled) { Text("Next queue page") }
                }
                val summary = state.snapshot?.queueSummary
                TextButton(onClick = {
                    actions.command(RemoteCommand.QueueShuffle(QueueShuffleParams(summary?.shuffle != true)))
                }, enabled = enabled) { Text("Shuffle: ${summary?.shuffle == true}") }
                RepeatMode.entries.forEach { mode ->
                    TextButton(onClick = {
                        actions.command(RemoteCommand.QueueRepeat(QueueRepeatParams(mode)))
                    }, enabled = enabled) { Text("Repeat ${mode.wire}${if (summary?.repeat == mode) " ✓" else ""}") }
                }
                var name by remember { mutableStateOf("") }
                OutlinedTextField(name, { name = it }, label = { Text("Playlist name") })
                TextButton(
                    onClick = { actions.command(RemoteCommand.PlaylistsSave(PlaylistsSaveParams.SaveQueue(name))) },
                    enabled =
                        enabled && name.isNotBlank() && name.length <= 200,
                ) { Text("Save queue as playlist") }
                Text(state.message)
            }
        },
        confirmButton = { TextButton(onClick = actions.close) { Text("Close queue") } },
    )
}

@Composable
private fun QueueEntries(
    queue: com.emmah.colordock.remote.mediacenter.protocol.QueuePage?,
    enabled: Boolean,
    actions: QueueActions,
) {
    queue?.items?.forEachIndexed { index, entry ->
        TextButton(
            onClick = { actions.play(entry) },
            enabled =
                enabled && entry.item.availability == Availability.AVAILABLE,
        ) { Text(entry.item.name) }
        if (entry.item.availability != Availability.AVAILABLE) Text("Offline")
        TextButton(onClick = {
            actions.command(RemoteCommand.QueueRemove(QueueRemoveParams(entry.entryId)))
        }, enabled = enabled) { Text("Remove ${entry.item.name}") }
        if (index >
            0
        ) {
            TextButton(onClick = {
                actions.command(
                    RemoteCommand.QueueMove(
                        QueueMoveParams(
                            entry.entryId,
                            queue.items[
                                index -
                                    1,
                            ].entryId,
                        ),
                    ),
                )
            }, enabled = enabled) { Text("Move ${entry.item.name} up") }
        }
        TextButton(onClick = {
            actions.command(RemoteCommand.QueueMove(QueueMoveParams(entry.entryId)))
        }, enabled = enabled) { Text("Move ${entry.item.name} to end") }
    }
}
