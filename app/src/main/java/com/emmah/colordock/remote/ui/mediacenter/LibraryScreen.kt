package com.emmah.colordock.remote.ui.mediacenter

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.emmah.colordock.remote.mediacenter.protocol.Availability
import com.emmah.colordock.remote.mediacenter.protocol.MediaItem
import com.emmah.colordock.remote.mediacenter.protocol.MediaKind

@Composable
internal fun LibraryScreen(
    state: LibraryState,
    actions: LibraryActions,
    thumbnail: suspend (String) -> ByteArray?,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Row {
            MediaKind.entries.forEach { kind ->
                TextButton(onClick = { actions.search("", kind) }) {
                    Text(
                        when (kind) {
                            MediaKind.VIDEO -> "Videos"
                            MediaKind.MUSIC -> "Music"
                            MediaKind.PHOTO -> "Photos"
                        },
                    )
                }
            }
        }
        OutlinedTextField(state.query, {
            actions.search(it, state.kind)
        }, label = { Text("Search ${state.kind.wire}") }, singleLine = true)
        if (state.kind ==
            MediaKind.MUSIC
        ) {
            TextButton(onClick = actions.playlists, enabled = state.connected) { Text("Playlists") }
        }
        TextButton(onClick = actions.playback) { Text("Now playing") }
        if (state.loading) Text("Loading library…")
        if (state.message.isNotEmpty()) Text(state.message)
        state.items.forEach { item -> LibraryItemRow(item, state.connected, actions, thumbnail) }
        Row {
            TextButton(onClick = actions.previous, enabled = state.connected && state.hasPrevious && !state.loading) {
                Text("Previous page")
            }
            TextButton(
                onClick = actions.next,
                enabled = state.connected && state.cursor != null && !state.loading,
            ) { Text("Next page") }
            TextButton(
                onClick = actions.refresh,
                enabled = state.connected && !state.loading,
            ) { Text("Refresh library") }
        }
    }
}

@Composable
private fun MediaThumbnail(
    id: String?,
    load: suspend (String) -> ByteArray?,
    modifier: Modifier = Modifier,
) {
    val bytes by produceState<ByteArray?>(null, id, load) { value = id?.let { load(it) } }
    val bitmap = remember(bytes) { bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
    bitmap?.let { Image(it, contentDescription = null, modifier = modifier.size(64.dp)) }
}

@Composable
private fun LibraryItemRow(
    item: MediaItem,
    connected: Boolean,
    actions: LibraryActions,
    thumbnail: suspend (String) -> ByteArray?,
) {
    Row {
        MediaThumbnail(item.thumbnailId, thumbnail)
        Column {
            TextButton(
                onClick = { actions.select(item) },
                enabled =
                    connected && item.availability == Availability.AVAILABLE,
            ) { Text(item.name) }
            if (item.availability != Availability.AVAILABLE) Text("Offline · ${item.availability.wire}")
            if (item.kind == MediaKind.MUSIC) {
                var menu by remember(item.id) { mutableStateOf(false) }
                TextButton(
                    onClick = { menu = !menu },
                    enabled = connected,
                ) { Text("Options for ${item.name}") }
                if (menu) {
                    TextButton(onClick = {
                        actions.enqueue(item)
                        menu = false
                    }) { Text("Add to queue") }
                    TextButton(onClick = {
                        actions.addPlaylist(item)
                        menu = false
                    }) { Text("Add to playlist") }
                }
            }
        }
    }
}
