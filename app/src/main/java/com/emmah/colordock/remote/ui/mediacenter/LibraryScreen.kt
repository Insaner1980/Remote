package com.emmah.colordock.remote.ui.mediacenter

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import com.emmah.colordock.remote.mediacenter.MediacenterRepository
import com.emmah.colordock.remote.mediacenter.protocol.Availability
import com.emmah.colordock.remote.mediacenter.protocol.MediaItem
import com.emmah.colordock.remote.mediacenter.protocol.MediaKind
import com.emmah.colordock.remote.mediacenter.protocol.RemoteSnapshot

/** A fresh loader identity invalidates remembered image work when the native owner changes. */
@Composable
internal fun rememberMediacenterThumbnails(
    repository: MediacenterRepository,
    snapshot: RemoteSnapshot?,
): suspend (String) -> ByteArray? =
    remember(repository, snapshot?.connection?.serverDeviceId, snapshot?.serverEpoch) {
        val load: suspend (String) -> ByteArray? = { id -> repository.thumbnail(id) }
        load
    }

@Composable
internal fun LibraryScreen(
    state: LibraryState,
    actions: LibraryActions,
    thumbnail: suspend (String) -> ByteArray?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier,
        verticalArrangement =
            androidx.compose.foundation.layout.Arrangement
                .spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement =
                    androidx.compose.foundation.layout.Arrangement
                        .spacedBy(8.dp),
            ) {
                MediaKind.entries.forEach { kind ->
                    androidx.compose.material3.FilterChip(
                        selected = state.kind == kind,
                        onClick = { actions.search("", kind) },
                        label = { Text(mediaKindLabel(kind)) },
                    )
                }
            }
        }
        OutlinedTextField(
            state.query,
            { actions.search(it, state.kind) },
            label = { Text("Search") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.kind == MediaKind.MUSIC) {
            TextButton(onClick = actions.playlists, enabled = state.connected) { Text("Playlists") }
        }
        LibraryNotice(state)
        LibraryItems(state, actions, thumbnail)
        LibraryPages(state, actions)
    }
}

@Composable
private fun LibraryNotice(state: LibraryState) {
    Column {
        if (state.loading) {
            androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
        } else if (!state.connected) {
            Text("Not connected")
        } else if (state.notice != null) {
            Text(state.notice.label)
        } else if (state.message.isNotEmpty() && state.message != "No matching media.") {
            Text("Couldn't load library")
        } else if (state.items.isEmpty()) {
            Text("No results")
        }
    }
}

@Composable
private fun LibraryPages(
    state: LibraryState,
    actions: LibraryActions,
) {
    Row {
        if (state.hasPrevious) {
            androidx.compose.material3.IconButton(
                onClick = actions.previous,
                enabled = state.connected && !state.loading,
            ) {
                com.emmah.colordock.remote.ui.DockIcon(
                    com.emmah.colordock.remote.R.drawable.ic_keyboard_arrow_left,
                    label = "Previous page",
                )
            }
        }
        if (state.cursor != null) {
            androidx.compose.material3.IconButton(
                onClick = actions.next,
                enabled = state.connected && !state.loading,
            ) {
                com.emmah.colordock.remote.ui.DockIcon(
                    com.emmah.colordock.remote.R.drawable.ic_keyboard_arrow_right,
                    label = "Next page",
                )
            }
        }
    }
}

internal fun mediaKindLabel(kind: MediaKind): String =
    when (kind) {
        MediaKind.VIDEO -> "Videos"
        MediaKind.MUSIC -> "Music"
        MediaKind.PHOTO -> "Photos"
    }

@Composable
private fun LibraryItems(
    state: LibraryState,
    actions: LibraryActions,
    thumbnail: suspend (String) -> ByteArray?,
) {
    if (state.kind == MediaKind.PHOTO) {
        state.items.chunked(2).forEachIndexed { rowIndex, pair ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement =
                    androidx.compose.foundation.layout.Arrangement
                        .spacedBy(8.dp),
            ) {
                pair.forEachIndexed { columnIndex, item ->
                    androidx.compose.runtime.key(item.id) {
                        PhotoTile(
                            IndexedValue(rowIndex * 2 + columnIndex, item),
                            state.connected,
                            actions,
                            thumbnail,
                            Modifier.weight(1f),
                        )
                    }
                }
                if (pair.size == 1) {
                    androidx.compose.foundation.layout
                        .Spacer(Modifier.weight(1f))
                }
            }
        }
    } else {
        state.items.forEach { item ->
            androidx.compose.runtime.key(item.id) {
                LibraryItemRow(item, state.connected, actions, thumbnail)
            }
        }
    }
}
