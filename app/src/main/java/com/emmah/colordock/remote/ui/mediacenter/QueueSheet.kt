package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
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
import com.emmah.colordock.remote.R
import com.emmah.colordock.remote.mediacenter.protocol.Availability
import com.emmah.colordock.remote.mediacenter.protocol.PlaylistsSaveParams
import com.emmah.colordock.remote.mediacenter.protocol.QueueMoveParams
import com.emmah.colordock.remote.mediacenter.protocol.QueueRemoveParams
import com.emmah.colordock.remote.mediacenter.protocol.QueueRepeatParams
import com.emmah.colordock.remote.mediacenter.protocol.QueueShuffleParams
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand
import com.emmah.colordock.remote.mediacenter.protocol.RepeatMode
import com.emmah.colordock.remote.ui.RemoteDimens

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
        title = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Queue", Modifier.weight(1f))
                RefreshAction({ actions.page(null) }, enabled = state.snapshot != null, label = "Refresh queue")
            }
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(RemoteDimens.ItemGap),
            ) {
                if (queue == null) {
                    androidx.compose.material3.LinearProgressIndicator()
                } else if (queue.items.isEmpty()) {
                    Text("Empty")
                }
                QueueEntries(queue, enabled, actions)
                queue?.nextCursor?.let { cursor ->
                    TextButton(onClick = { actions.page(cursor) }, enabled = enabled) { Text("More") }
                }
                QueueModeChips(state, enabled, actions)
                var saving by remember { mutableStateOf(false) }
                MediacenterDialogAction(
                    "Save playlist",
                    R.drawable.ic_queue_music,
                    { saving = !saving },
                    enabled = enabled,
                )
                if (saving) {
                    var name by remember { mutableStateOf("") }
                    OutlinedTextField(
                        name,
                        { name = it.take(MEDIA_QUERY_LIMIT) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Name") },
                        singleLine = true,
                    )
                    Button(
                        onClick = { actions.command(RemoteCommand.PlaylistsSave(PlaylistsSaveParams.SaveQueue(name))) },
                        modifier = Modifier.align(Alignment.End),
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
private fun QueueModeChips(
    state: MediaSessionState,
    enabled: Boolean,
    actions: QueueActions,
) {
    val summary = state.snapshot?.queueSummary
    val mode = summary?.repeat ?: RepeatMode.OFF
    Row(
        Modifier.height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(RemoteDimens.ItemGap),
    ) {
        FilterChip(
            selected = summary?.shuffle == true,
            onClick = { actions.command(RemoteCommand.QueueShuffle(QueueShuffleParams(summary?.shuffle != true))) },
            label = { Text("Shuffle") },
            modifier = Modifier.weight(1f).fillMaxHeight(),
            enabled = enabled,
        )
        FilterChip(
            selected = mode != RepeatMode.OFF,
            onClick = {
                val next =
                    when (mode) {
                        RepeatMode.OFF -> RepeatMode.ALL
                        RepeatMode.ALL -> RepeatMode.ONE
                        RepeatMode.ONE -> RepeatMode.OFF
                    }
                actions.command(RemoteCommand.QueueRepeat(QueueRepeatParams(next)))
            },
            label = { Text("Repeat ${mode.wire}") },
            modifier = Modifier.weight(1f).fillMaxHeight(),
            enabled = enabled,
        )
    }
}

@Composable
private fun QueueEntries(
    queue: com.emmah.colordock.remote.mediacenter.protocol.QueuePage?,
    enabled: Boolean,
    actions: QueueActions,
) {
    val entries = queue?.items.orEmpty()
    ListGroup {
        entries.forEachIndexed { index, entry ->
            androidx.compose.runtime.key(entry.entryId) {
                val title = mediaTitle(entry.item.name)
                val available = entry.item.availability == Availability.AVAILABLE
                ListRow(
                    title,
                    if (available) null else "Offline",
                    listRowPosition(index, entries.size),
                    onClick = { actions.play(entry) },
                    enabled = enabled && available,
                    maxTitleLines = 2,
                    trailing = {
                        ItemActions(
                            "Options for $title",
                            queueOptions(entry.entryId, entries.getOrNull(index - 1)?.entryId, enabled, actions),
                            enabled = enabled,
                            buttonTag = "queue-options-${entry.entryId}",
                        )
                    },
                )
            }
        }
    }
}

private fun queueOptions(
    entryId: String,
    previousEntryId: String?,
    enabled: Boolean,
    actions: QueueActions,
): List<MenuAction> =
    buildList {
        add(
            MenuAction("Remove", enabled, destructive = true) {
                actions.command(RemoteCommand.QueueRemove(QueueRemoveParams(entryId)))
            },
        )
        if (previousEntryId != null) {
            add(
                MenuAction("Move up", enabled) {
                    actions.command(RemoteCommand.QueueMove(QueueMoveParams(entryId, previousEntryId)))
                },
            )
        }
        add(
            MenuAction("Move to end", enabled) {
                actions.command(RemoteCommand.QueueMove(QueueMoveParams(entryId)))
            },
        )
    }
