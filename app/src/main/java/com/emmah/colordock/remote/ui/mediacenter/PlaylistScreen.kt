package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import com.emmah.colordock.remote.R
import com.emmah.colordock.remote.mediacenter.protocol.Availability
import com.emmah.colordock.remote.mediacenter.protocol.MediaItem
import com.emmah.colordock.remote.mediacenter.protocol.PlaylistsSaveParams
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand
import com.emmah.colordock.remote.ui.DockIcon
import com.emmah.colordock.remote.ui.RemoteDimens

@Composable
internal fun PlaylistScreen(
    state: MediaSessionState,
    append: MediaItem?,
    actions: PlaylistActions,
    modifier: Modifier = Modifier,
) {
    val contents = state.playlist
    val enabled = state.snapshot != null && !state.busy
    Column(modifier, verticalArrangement = Arrangement.spacedBy(RemoteDimens.BlockGap)) {
        if (contents == null) {
            MediacenterSubpageHeading(if (append == null) "Playlists" else "Choose playlist", actions.back) {
                RefreshAction({ actions.list(null) }, enabled = enabled)
            }
            PillSearchField(state.playlistQuery, actions.search, "Search")
            val playlists = state.playlists?.items.orEmpty()
            ListGroup {
                playlists.forEachIndexed { index, playlist ->
                    ListRow(
                        playlist.name,
                        null,
                        listRowPosition(index, playlists.size),
                        onClick = { actions.open(playlist.id, null) },
                        enabled = enabled,
                        maxTitleLines = Int.MAX_VALUE,
                    )
                }
            }
            if (state.playlists?.items?.isEmpty() == true) Text("No playlists")
            state.playlists?.nextCursor?.let { cursor ->
                TextButton(
                    onClick = { actions.list(cursor) },
                    Modifier.align(Alignment.End),
                    enabled = enabled,
                ) { Text("More") }
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
    MediacenterSubpageHeading(contents.playlist.name, actions.back) {
        RefreshAction({ actions.open(contents.playlist.id, null) }, enabled = enabled, label = "Refresh playlist")
        ItemActions("Playlist options", listOf(MenuAction("Rename", enabled) { editing = !editing }), enabled = enabled)
    }
    if (editing) {
        var name by remember(
            contents.playlist.id,
            contents.playlist.revision,
        ) { mutableStateOf(contents.playlist.name) }
        OutlinedTextField(name, { name = it.take(MEDIA_QUERY_LIMIT) }, label = { Text("Name") }, singleLine = true)
        Button(
            onClick = {
                actions.command(
                    RemoteCommand.PlaylistsSave(PlaylistsSaveParams.Rename(contents.playlist.id, name)),
                )
            },
            enabled = enabled && name.isNotBlank(),
        ) { Text("Save") }
    }
    if (append != null) {
        Button(onClick = {
            actions.command(
                RemoteCommand.PlaylistsSave(PlaylistsSaveParams.Append(contents.playlist.id, listOf(append.id))),
            )
            actions.appended()
        }, enabled = enabled) { Text("Add to playlist") }
        TextButton(onClick = actions.play, enabled = enabled) { Text("Play") }
    } else {
        Button(onClick = actions.play, enabled = enabled) {
            DockIcon(R.drawable.ic_play_arrow)
            Spacer(Modifier.width(RemoteDimens.ItemGap))
            Text("Play")
        }
    }
    PlaylistEntries(contents, enabled, actions)
    contents.entries.nextCursor?.let { cursor ->
        TextButton(onClick = {
            actions.open(contents.playlist.id, cursor)
        }, Modifier.align(Alignment.End), enabled = enabled) { Text("More") }
    }
    state.notice?.let { Text(it.label) }
}

@Composable
private fun PlaylistEntries(
    contents: com.emmah.colordock.remote.mediacenter.protocol.PlaylistContents,
    enabled: Boolean,
    actions: PlaylistActions,
) {
    val entries = contents.entries.items
    ListGroup {
        entries.forEachIndexed { index, entry ->
            androidx.compose.runtime.key(entry.entryId) {
                val title = mediaTitle(entry.item.name)
                ListRow(
                    title,
                    if (entry.item.availability == Availability.AVAILABLE) null else "Offline",
                    listRowPosition(index, entries.size),
                    maxTitleLines = 2,
                    trailing = {
                        ItemActions(
                            "Options for $title",
                            listOf(
                                MenuAction("Remove", enabled, destructive = true) {
                                    actions.command(
                                        RemoteCommand.PlaylistsSave(
                                            PlaylistsSaveParams.Remove(contents.playlist.id, listOf(entry.entryId)),
                                        ),
                                    )
                                },
                            ),
                            enabled = enabled,
                        )
                    },
                )
            }
        }
    }
}
