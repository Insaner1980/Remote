package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.emmah.colordock.remote.R
import com.emmah.colordock.remote.mediacenter.MediacenterRepository
import com.emmah.colordock.remote.mediacenter.protocol.MediaKind
import com.emmah.colordock.remote.mediacenter.protocol.RemoteSnapshot
import com.emmah.colordock.remote.ui.AdaptivePair
import com.emmah.colordock.remote.ui.DockIcon

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
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PillSearchField(state.query, { actions.search(it, state.kind) }, "Search")
        LibraryKinds(state, actions)
        if (state.kind == MediaKind.MUSIC) {
            ListGroup {
                ListRow(
                    "Playlists",
                    null,
                    ListRowPosition.Single,
                    onClick = actions.playlists,
                    leadingIcon = R.drawable.ic_format_list_bulleted,
                    enabled = state.connected,
                )
            }
        }
        LibraryNotice(state)
        LibraryItems(state, actions, thumbnail)
        LibraryPages(state, actions)
    }
}

@Composable
private fun LibraryKinds(
    state: LibraryState,
    actions: LibraryActions,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MediaKind.entries.forEach { kind ->
            val selected = state.kind == kind
            FilterChip(
                selected = selected,
                onClick = { actions.search("", kind) },
                label = { Text(mediaKindLabel(kind)) },
                leadingIcon =
                    if (selected) {
                        {
                            DockIcon(
                                R.drawable.ic_check,
                                Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    } else {
                        null
                    },
                colors =
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            )
        }
    }
}

@Composable
private fun LibraryNotice(state: LibraryState) {
    val notice =
        when {
            !state.connected -> "Not connected"
            state.notice != null -> state.notice.label
            state.message.isNotEmpty() && state.message != "No matching media." -> "Couldn't load library"
            state.items.isEmpty() -> "No results"
            else -> null
        }
    if (state.loading) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
    } else if (notice != null) {
        Text(notice, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LibraryPages(
    state: LibraryState,
    actions: LibraryActions,
) {
    AdaptivePair(
        first = { modifier ->
            if (state.hasPrevious) {
                MediaKey(
                    "Previous",
                    R.drawable.ic_keyboard_arrow_left,
                    state.connected && !state.loading,
                    actions.previous,
                    modifier,
                    description = "Previous page",
                )
            }
        },
        second = { modifier ->
            if (state.cursor != null) {
                MediaKey(
                    "Next",
                    R.drawable.ic_keyboard_arrow_right,
                    state.connected && !state.loading,
                    actions.next,
                    modifier,
                    description = "Next page",
                )
            }
        },
    )
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
        PhotoGrid(state, actions, thumbnail)
    } else {
        state.items.forEach { item -> key(item.id) { LibraryItemRow(item, state.connected, actions, thumbnail) } }
    }
}

@Composable
private fun PhotoGrid(
    state: LibraryState,
    actions: LibraryActions,
    thumbnail: suspend (String) -> ByteArray?,
) {
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        state.items.chunked(PHOTO_COLUMNS).forEachIndexed { rowIndex, items ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                items.forEachIndexed { columnIndex, item ->
                    key(item.id) {
                        PhotoTile(
                            IndexedValue(rowIndex * PHOTO_COLUMNS + columnIndex, item),
                            state.connected,
                            actions,
                            thumbnail,
                            Modifier.weight(1f),
                        )
                    }
                }
                repeat(PHOTO_COLUMNS - items.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private const val PHOTO_COLUMNS = 3
