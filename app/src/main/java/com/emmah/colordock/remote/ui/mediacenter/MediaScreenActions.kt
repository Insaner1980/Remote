package com.emmah.colordock.remote.ui.mediacenter

import com.emmah.colordock.remote.mediacenter.protocol.MediaItem
import com.emmah.colordock.remote.mediacenter.protocol.MediaKind
import com.emmah.colordock.remote.mediacenter.protocol.QueueEntry
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand

internal data class LibraryActions(
    val search: (String, MediaKind) -> Unit,
    val select: (MediaItem) -> Unit,
    val enqueue: (MediaItem) -> Unit,
    val addPlaylist: (MediaItem) -> Unit,
    val playlists: () -> Unit,
    val playback: () -> Unit,
    val next: () -> Unit,
    val previous: () -> Unit,
    val refresh: () -> Unit,
)

internal data class PlaylistActions(
    val command: (RemoteCommand) -> Unit,
    val open: (String, String?) -> Unit,
    val list: (String?) -> Unit,
    val search: (String) -> Unit,
    val play: () -> Unit,
    val back: () -> Unit,
    val appended: () -> Unit,
)

internal data class QueueActions(
    val command: (RemoteCommand) -> Unit,
    val play: (QueueEntry) -> Unit,
    val page: (String?) -> Unit,
    val close: () -> Unit,
)

internal data class ConfirmationActions(
    val confirm: () -> Unit,
    val cancel: () -> Unit,
)
