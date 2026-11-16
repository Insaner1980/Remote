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
                    androidx.compose.material3.LinearProgressIndicator()
                } else if (queue.items.isEmpty()) {
                    Text("Empty")
                }
                QueueEntries(queue, enabled, actions)
                RefreshAction({ actions.page(null) }, enabled = state.snapshot != null, label = "Refresh queue")
                queue?.nextCursor?.let { cursor ->
                    TextButton(onClick = { actions.page(cursor) }, enabled = enabled) { Text("More") }
                }
                val summary = state.snapshot?.queueSummary
                androidx.compose.foundation.layout.Row {
                    androidx.compose.material3.FilterChip(selected = summary?.shuffle == true, onClick = {
                        actions.command(RemoteCommand.QueueShuffle(QueueShuffleParams(summary?.shuffle != true)))
                    }, enabled = enabled, label = { Text("Shuffle") })
                    val mode = summary?.repeat ?: RepeatMode.OFF
                    TextButton(onClick = {
                        val next =
                            when (mode) {
                                RepeatMode.OFF -> RepeatMode.ALL
                                RepeatMode.ALL -> RepeatMode.ONE
                                RepeatMode.ONE -> RepeatMode.OFF
                            }
                        actions.command(RemoteCommand.QueueRepeat(QueueRepeatParams(next)))
                    }, enabled = enabled) { Text("Repeat ${mode.wire}") }
                }
                var saving by remember { mutableStateOf(false) }
                TextButton(onClick = { saving = !saving }, enabled = enabled) { Text("Save playlist") }
                if (saving) {
                    var name by remember { mutableStateOf("") }
                    OutlinedTextField(
                        name,
                        { name = it.take(MEDIA_QUERY_LIMIT) },
                        label = { Text("Name") },
                        singleLine = true,
                    )
                    TextButton(
                        onClick = { actions.command(RemoteCommand.PlaylistsSave(PlaylistsSaveParams.SaveQueue(name))) },
                        enabled = enabled && name.isNotBlank(),
                    ) { Text("Save") }
                }
                state.notice?.let { Text(it.label) }
            }
        },
        confirmButton = { TextButton(onClick = actions.close) { Text("Close") } },
    )
}

@Composable
private fun QueueEntries(
    queue: com.emmah.colordock.remote.mediacenter.protocol.QueuePage?,
    enabled: Boolean,
    actions: QueueActions,
) {
    Column {
        queue?.items?.forEachIndexed { index, entry ->
            androidx.compose.runtime.key(entry.entryId) {
                val title = mediaTitle(entry.item.name)
                androidx.compose.foundation.layout.Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = { actions.play(entry) },
                        enabled =
                            enabled && entry.item.availability == Availability.AVAILABLE,
                        modifier = Modifier.weight(1f),
                    ) { Text(title, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
                    val options =
                        buildList {
                            add(
                                MenuAction(
                                    "Remove",
                                    enabled,
                                ) { actions.command(RemoteCommand.QueueRemove(QueueRemoveParams(entry.entryId))) },
                            )
                            if (index > 0) {
                                add(
                                    MenuAction("Move up", enabled) {
                                        actions.command(
                                            RemoteCommand.QueueMove(
                                                QueueMoveParams(entry.entryId, queue.items[index - 1].entryId),
                                            ),
                                        )
                                    },
                                )
                            }
                            add(
                                MenuAction(
                                    "Move to end",
                                    enabled,
                                ) { actions.command(RemoteCommand.QueueMove(QueueMoveParams(entry.entryId))) },
                            )
                        }
                    ItemActions(
                        "Options for $title",
                        options,
                        modifier = Modifier.testTag("queue-options-${entry.entryId}"),
                        enabled = enabled,
                    )
                }
                if (entry.item.availability != Availability.AVAILABLE) Text("Offline")
            }
        }
    }
}
