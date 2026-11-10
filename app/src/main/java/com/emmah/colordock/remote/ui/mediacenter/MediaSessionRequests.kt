package com.emmah.colordock.remote.ui.mediacenter

import com.emmah.colordock.remote.mediacenter.protocol.ConfirmationsGetParams
import com.emmah.colordock.remote.mediacenter.protocol.Delivery
import com.emmah.colordock.remote.mediacenter.protocol.Expected
import com.emmah.colordock.remote.mediacenter.protocol.McRequest
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand
import com.emmah.colordock.remote.mediacenter.protocol.RemoteSnapshot
import java.util.UUID

internal fun MediaSessionController.resetOwner(snapshot: RemoteSnapshot?) {
    photoSelection = null
    photoSession = null
    playlistSearch?.cancel()
    playPlaylistAfterRead = false
    deadlines.values.forEach { it.cancel() }
    deadlines.clear()
    requests.clear()
    observedOperation = null
    operationRequest =
        null
    handoff = null
    handoffReady = false
    discoveredModal = null
    mutable.value =
        MediaSessionState(
            snapshot = snapshot,
            destination = state.value.destination,
            message =
                if (snapshot ==
                    null
                ) {
                    "Connect to Mediacenter."
                } else {
                    ""
                },
        )
}

internal fun MediaSessionController.discoverConfirmation(snapshot: RemoteSnapshot) {
    val modal = snapshot.ui.modalId
    if (modal != null && modal != discoveredModal && state.value.preview == null) {
        discoveredModal = modal
        submit(RemoteCommand.ConfirmationsGet(ConfirmationsGetParams(modal)))
    }
    if (modal == null) discoveredModal = null
}

internal fun MediaSessionController.createRequest(command: RemoteCommand): McRequest? {
    val snapshot = state.value.snapshot ?: return null
    if (command.delivery == Delivery.READ) {
        requests.filterValues { it.method == command.method }.keys.toList().forEach { id ->
            requests.remove(id)
            deadlines.remove(id)?.cancel()
        }
    }
    return if (requests.size >= MEDIA_REQUEST_LIMIT) {
        message("Wait for Mediacenter to respond.")
        null
    } else {
        val expected =
            Expected(
                serverEpoch = snapshot.serverEpoch,
                sessionId =
                    when {
                        command is RemoteCommand.ActivityShowPlayback -> snapshot.activity.sessionId
                        command.method.wire.startsWith("photos.") -> snapshot.slideshow.sessionId
                        else -> snapshot.player.sessionId
                    },
                queueRevision = snapshot.queueSummary.revision,
                playlistRevision =
                    state.value.playlist
                        ?.playlist
                        ?.revision,
            )
        McRequest(UUID.randomUUID().toString(), command, expected)
    }
}
