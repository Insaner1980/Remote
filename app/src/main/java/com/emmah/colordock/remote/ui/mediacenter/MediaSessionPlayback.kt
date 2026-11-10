package com.emmah.colordock.remote.ui.mediacenter

import com.emmah.colordock.remote.mediacenter.protocol.ActivityKind
import com.emmah.colordock.remote.mediacenter.protocol.ActivityMode
import com.emmah.colordock.remote.mediacenter.protocol.ActivityPrepareParams
import com.emmah.colordock.remote.mediacenter.protocol.Availability
import com.emmah.colordock.remote.mediacenter.protocol.MediaItem
import com.emmah.colordock.remote.mediacenter.protocol.MediaKind
import com.emmah.colordock.remote.mediacenter.protocol.PhotosShowParams
import com.emmah.colordock.remote.mediacenter.protocol.PlaybackStart
import com.emmah.colordock.remote.mediacenter.protocol.Playlist
import com.emmah.colordock.remote.mediacenter.protocol.PlaylistsPlayParams
import com.emmah.colordock.remote.mediacenter.protocol.QueuePlayEntryParams
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand
import com.emmah.colordock.remote.mediacenter.protocol.RemoteSnapshot

internal fun MediaSessionController.choose(
    item: MediaItem,
    selection: String? = null,
    entry: String? = null,
) {
    if (item.availability != Availability.AVAILABLE ||
        state.value.busy
    ) {
        message("This media is offline or another command is pending.")
        return
    }
    if (item.kind == MediaKind.VIDEO &&
        (item.resumePositionSeconds ?: 0.0) > 0
    ) {
        mutable.value = state.value.copy(resume = item)
        return
    }
    play(item, PlaybackStart.BEGINNING, selection, entry)
}

internal fun MediaSessionController.resume(start: PlaybackStart) {
    state.value.resume?.let { play(it, start) }
    mutable.value =
        state.value.copy(resume = null)
}

internal fun MediaSessionController.dismissResume() {
    mutable.value = state.value.copy(resume = null)
}

internal fun MediaSessionController.play(
    item: MediaItem,
    start: PlaybackStart,
    selection: String? = null,
    entry: String? = null,
    playlist: Playlist? = null,
) {
    val snapshot = state.value.snapshot ?: return
    if (item.kind == MediaKind.PHOTO &&
        selection == null
    ) {
        message("Refresh Photos before showing this image.")
        return
    }
    val previousSession =
        if (item.kind ==
            MediaKind.PHOTO
        ) {
            snapshot.slideshow.sessionId
        } else {
            snapshot.player.sessionId
        }
    handoff = MediaSessionController.Handoff(item, entry, selection, previousSession, playlist)
    handoffReady = false
    if (ownsPhotoSelection(snapshot, item, selection)) {
        command(RemoteCommand.PhotosShow(PhotosShowParams(item.id, selection!!)))
        handoff = null
    } else {
        val kind =
            when (item.kind) {
                MediaKind.VIDEO -> ActivityKind.VIDEO
                MediaKind.MUSIC -> ActivityKind.MUSIC
                MediaKind.PHOTO -> ActivityKind.PHOTO
            }
        command(RemoteCommand.ActivityPrepare(ActivityPrepareParams(kind, item.id, start)))
    }
}

internal fun MediaSessionController.finishHandoff() {
    val pending = handoff ?: return
    val snapshot = state.value.snapshot
    if (snapshot != null && handoffReady) {
        val next = handoffCommand(pending, snapshot)
        if (next != null) {
            if (pending.item.kind == MediaKind.PHOTO) {
                photoSelection = pending.selection
                photoSession = snapshot.slideshow.sessionId
            }
            handoff = null
            handoffReady = false
            command(next)
        } else if (pending.entry == null && pending.selection == null && pending.playlist == null) {
            handoff = null
        }
    }
}

internal fun MediaSessionController.ownsPhotoSelection(
    snapshot: RemoteSnapshot,
    item: MediaItem,
    selection: String?,
): Boolean {
    val sameSelection = photoSelection == selection && photoSession == snapshot.slideshow.sessionId
    return item.kind == MediaKind.PHOTO && snapshot.activity.mode == ActivityMode.PHOTOS && sameSelection
}

internal fun MediaSessionController.handoffCommand(
    pending: MediaSessionController.Handoff,
    snapshot: RemoteSnapshot,
): RemoteCommand? {
    val playerReady =
        snapshot.player.itemId == pending.item.id &&
            snapshot.player.sessionId != pending.previousSession
    val photoReady =
        snapshot.slideshow.currentItemId == pending.item.id &&
            snapshot.slideshow.sessionId != pending.previousSession
    return when {
        pending.playlist != null && playerReady -> {
            playlistHandoff(pending.playlist)
        }

        pending.item.kind == MediaKind.MUSIC && pending.entry != null && playerReady -> {
            RemoteCommand.QueuePlayEntry(QueuePlayEntryParams(pending.entry))
        }

        pending.item.kind == MediaKind.PHOTO && pending.selection != null && photoReady -> {
            RemoteCommand.PhotosShow(PhotosShowParams(pending.item.id, pending.selection))
        }

        else -> {
            null
        }
    }
}

internal fun MediaSessionController.playlistHandoff(playlist: Playlist): RemoteCommand? =
    if (state.value.playlist
            ?.playlist
            ?.revision != playlist.revision
    ) {
        handoff = null
        message("Playlist changed. Reload and choose Play again.")
        null
    } else {
        RemoteCommand.PlaylistsPlay(PlaylistsPlayParams(playlist.id))
    }
