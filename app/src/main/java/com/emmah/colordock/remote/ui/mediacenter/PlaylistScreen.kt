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
        TextButton(onClick = actions.back) { Text("Back to Music") }
        if (contents == null) {
            OutlinedTextField(state.playlistQuery, actions.search, label = { Text("Search playlists") })
            Text(if (append == null) "Playlists" else "Choose playlist for ${append.name}")
            state.playlists?.items?.forEach { playlist ->
                TextButton(
                    onClick = { actions.open(playlist.id, null) },
                    enabled = enabled,
                ) { Text("${playlist.name} (${playlist.count})") }
            }
            if (state.playlists?.items?.isEmpty() == true) Text("No playlists. Save your queue to create one.")
            TextButton(onClick = { actions.list(null) }, enabled = enabled) { Text("Refresh playlists") }
            state.playlists?.nextCursor?.let { cursor ->
                TextButton(onClick = { actions.list(cursor) }, enabled = enabled) { Text("Next playlists page") }
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
    Text(contents.playlist.name, style = MaterialTheme.typography.titleLarge)
    var name by remember(
        contents.playlist.id,
        contents.playlist.revision,
    ) { mutableStateOf(contents.playlist.name) }
    OutlinedTextField(name, { name = it }, label = { Text("Playlist name") })
    TextButton(
        onClick = {
            actions.command(
                RemoteCommand.PlaylistsSave(PlaylistsSaveParams.Rename(contents.playlist.id, name)),
            )
        },
        enabled =
            enabled && name.isNotBlank() && name.length <= 200,
    ) { Text("Save name") }
    if (append !=
        null
    ) {
        TextButton(onClick = {
            actions.command(
                RemoteCommand.PlaylistsSave(
                    PlaylistsSaveParams.Append(contents.playlist.id, listOf(append.id)),
                ),
            )
            actions.appended()
        }, enabled = enabled) { Text("Add ${append.name}") }
    }
    TextButton(onClick = actions.play, enabled = enabled) { Text("Play playlist") }
    contents.entries.items.forEach { entry ->
        Text(entry.item.name + if (entry.item.availability != Availability.AVAILABLE) " · Offline" else "")
        TextButton(onClick = {
            actions.command(
                RemoteCommand.PlaylistsSave(
                    PlaylistsSaveParams.Remove(contents.playlist.id, listOf(entry.entryId)),
                ),
            )
        }, enabled = enabled) { Text("Remove ${entry.item.name}") }
    }
    TextButton(
        onClick = { actions.open(contents.playlist.id, null) },
        enabled = enabled,
    ) { Text("Reload playlist / first page") }
    contents.entries.nextCursor?.let { cursor ->
        TextButton(
            onClick = { actions.open(contents.playlist.id, cursor) },
            enabled = enabled,
        ) { Text("Next playlist page") }
    }
    Text(state.message)
}
