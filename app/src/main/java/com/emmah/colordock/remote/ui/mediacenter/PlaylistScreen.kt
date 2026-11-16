package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.emmah.colordock.remote.mediacenter.protocol.Availability
import com.emmah.colordock.remote.mediacenter.protocol.MediaItem
import com.emmah.colordock.remote.mediacenter.protocol.PlaylistsSaveParams
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand

@Composable
internal fun PlaylistScreen(
    state: MediaSessionState,
    append: MediaItem?,
    actions: PlaylistActions,
    modifier: Modifier = Modifier,
) {
    val contents = state.playlist
    val enabled = state.snapshot != null && !state.busy
    Column(modifier) {
        TextButton(onClick = actions.back) { Text("Back") }
        if (contents == null) {
            PillSearchField(state.playlistQuery, actions.search, "Search")
            Text(if (append == null) "Playlists" else "Choose playlist")
            state.playlists?.items?.forEach { playlist ->
                TextButton(
                    onClick = { actions.open(playlist.id, null) },
                    enabled = enabled,
                ) { Text(playlist.name) }
            }
            if (state.playlists?.items?.isEmpty() == true) Text("No playlists")
            TextButton(onClick = { actions.list(null) }, enabled = enabled) { Text("Refresh") }
            state.playlists?.nextCursor?.let { cursor ->
                TextButton(onClick = { actions.list(cursor) }, enabled = enabled) { Text("More") }
            }
        } else {
            PlaylistDetails(state, append, actions)
        }
    }
}

@Composable
private fun ColumnScope.PlaylistDetails(
    state: MediaSessionState,
    append: MediaItem?,
    actions: PlaylistActions,
) {
    val contents = state.playlist ?: return
    val enabled = state.snapshot != null && !state.busy
    var editing by remember(contents.playlist.id) { mutableStateOf(false) }
    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(contents.playlist.name, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
        ItemActions("Playlist options", listOf(MenuAction("Rename", enabled) { editing = !editing }), enabled = enabled)
    }
    if (editing) {
        var name by remember(
            contents.playlist.id,
            contents.playlist.revision,
        ) { mutableStateOf(contents.playlist.name) }
        OutlinedTextField(name, { name = it.take(MEDIA_QUERY_LIMIT) }, label = { Text("Name") }, singleLine = true)
        TextButton(
            onClick = {
                actions.command(
                    RemoteCommand.PlaylistsSave(PlaylistsSaveParams.Rename(contents.playlist.id, name)),
                )
            },
            enabled = enabled && name.isNotBlank(),
        ) { Text("Save") }
    }
    if (append != null) {
        TextButton(onClick = {
            actions.command(
                RemoteCommand.PlaylistsSave(PlaylistsSaveParams.Append(contents.playlist.id, listOf(append.id))),
            )
            actions.appended()
        }, enabled = enabled) { Text("Add to playlist") }
    }
    TextButton(onClick = actions.play, enabled = enabled) { Text("Play") }
    PlaylistEntries(contents, enabled, actions)
    contents.entries.nextCursor?.let { cursor ->
        TextButton(onClick = { actions.open(contents.playlist.id, cursor) }, enabled = enabled) { Text("More") }
    }
    state.notice?.let { Text(it.label) }
}

@Composable
private fun PlaylistEntries(
    contents: com.emmah.colordock.remote.mediacenter.protocol.PlaylistContents,
    enabled: Boolean,
    actions: PlaylistActions,
) {
    contents.entries.items.forEach { entry ->
        androidx.compose.runtime.key(entry.entryId) {
            androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                val title = mediaTitle(entry.item.name)
                Text(
                    title,
                    Modifier.weight(1f),
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                ItemActions(
                    "Options for $title",
                    listOf(
                        MenuAction("Remove", enabled) {
                            actions.command(
                                RemoteCommand.PlaylistsSave(
                                    PlaylistsSaveParams.Remove(contents.playlist.id, listOf(entry.entryId)),
                                ),
                            )
                        },
                    ),
                    enabled = enabled,
                )
            }
            if (entry.item.availability != Availability.AVAILABLE) Text("Offline")
        }
    }
    RefreshAction({ actions.open(contents.playlist.id, null) }, enabled = enabled, label = "Refresh playlist")
}
