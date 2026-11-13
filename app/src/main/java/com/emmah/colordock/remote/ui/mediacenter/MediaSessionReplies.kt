package com.emmah.colordock.remote.ui.mediacenter

import com.emmah.colordock.remote.mediacenter.protocol.ActivityPreparation
import com.emmah.colordock.remote.mediacenter.protocol.Availability
import com.emmah.colordock.remote.mediacenter.protocol.McReply
import com.emmah.colordock.remote.mediacenter.protocol.OperationState
import com.emmah.colordock.remote.mediacenter.protocol.PlaybackStart
import com.emmah.colordock.remote.mediacenter.protocol.PlaylistContents
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand
import com.emmah.colordock.remote.mediacenter.protocol.RemoteResult
import com.emmah.colordock.remote.mediacenter.protocol.WireErrorCode

internal fun MediaSessionController.receive(reply: McReply) {
    if (confirmationStatus.receive(reply)) return
    val command = requests.remove(reply.id) ?: return
    deadlines.remove(reply.id)?.cancel()
    when (reply) {
        is McReply.Failure -> receiveFailure(reply, command)
        is McReply.Success -> receiveSuccess(reply.result)
    }
}

internal fun MediaSessionController.receiveFailure(
    reply: McReply.Failure,
    command: RemoteCommand,
) {
    if (command is RemoteCommand.VpnState) {
        submit(
            RemoteCommand.SettingsGet(com.emmah.colordock.remote.mediacenter.protocol.Empty),
        )
    }
    val ownsPlaylistStart = playPlaylistAfterRead && command is RemoteCommand.PlaylistsGet
    if (ownsPlaylistStart) playPlaylistAfterRead = false
    val ownsMutation = operationRequest == reply.id
    if (ownsMutation) {
        operationRequest = null
        if (!preservesConfirmationHandoff(reply, command)) handoff = null
        handoffReady = false
    }
    retireFailedBrowserPreview(reply)
    mutable.value =
        state.value.copy(
            busy = state.value.busy && !ownsMutation && !ownsPlaylistStart,
            preview =
                reply.error.replacementPreview
                    ?: state.value.preview.takeUnless {
                        reply.error.code in
                            listOf(WireErrorCode.NOT_FOUND, WireErrorCode.CONFIRMATION_EXPIRED)
                    },
            message = driveFailure(command, reply.error.code) ?: reply.error.message,
            messageOperation = null,
        )
    if (reply.error.code ==
        WireErrorCode.STALE_STATE
    ) {
        if (command.method.wire.startsWith("queue.")) refreshQueue()
        if (command.method.wire.startsWith("playlists.")) refreshPlaylist()
    }
}

internal fun MediaSessionController.receiveSuccess(result: RemoteResult) {
    if (receiveSourceResult(result) || receiveSettingsResult(result) || receiveBrowserResult(result)) return
    when (result) {
        is RemoteResult.LibraryItem -> {
            choose(result.value)
        }

        is RemoteResult.ActivityPrepare -> {
            receivePreparation(result.value)
        }

        is RemoteResult.ActivityConfirm, is RemoteResult.AudioConfirm -> {
            mutable.value =
                state.value.copy(preview = null, destination = MediaDestination.Remote)
            reconcileOperation()
        }

        is RemoteResult.ConfirmationsGet -> {
            mutable.value = state.value.copy(preview = result.value)
        }

        is RemoteResult.ConfirmationsCancel -> {
            handoff = null
            clearConfirmation()
        }

        is RemoteResult.QueueGet -> {
            mutable.value = state.value.copy(queue = result.value)
        }

        is RemoteResult.PlaylistsList -> {
            mutable.value = state.value.copy(playlists = result.value)
        }

        is RemoteResult.PlaylistsGet -> {
            receivePlaylist(result.value)
        }

        else -> {
            reconcileOperation()
        }
    }
}

internal fun MediaSessionController.reconcileOperation() {
    observedOperation?.let(::operation)
    state.value.snapshot
        ?.operations
        ?.items
        ?.forEach(::operation)
}

internal fun MediaSessionController.recovered(value: com.emmah.colordock.remote.mediacenter.PendingCommand) {
    if (value.resolved && value.requestId == operationRequest &&
        value.serverEpoch == state.value.snapshot?.serverEpoch
    ) {
        operation(
            OperationState(
                value.operationId ?: value.requestId,
                value.requestId,
                value.status,
                cancellationRequested = value.cancellationRequested,
            ),
        )
    }
}

internal fun MediaSessionController.receivePlaylist(value: PlaylistContents) {
    mutable.value = state.value.copy(playlist = value)
    if (playPlaylistAfterRead) {
        playPlaylistAfterRead = false
        mutable.value = state.value.copy(busy = false)
        val first =
            value.entries.items
                .firstOrNull()
                ?.item
        if (first == null ||
            first.availability != Availability.AVAILABLE
        ) {
            message("The first playlist track is unavailable. Edit or choose another playlist.")
        } else {
            play(first, PlaybackStart.BEGINNING, playlist = value.playlist)
        }
    }
}

internal fun MediaSessionController.receivePreparation(value: ActivityPreparation) {
    when (value) {
        is ActivityPreparation.ConfirmationRequired -> {
            operationRequest = null
            mutable.value =
                state.value.copy(busy = false, preview = value.preview)
        }

        is ActivityPreparation.Accepted -> {
            mutable.value =
                state.value.copy(destination = MediaDestination.Remote)
            reconcileOperation()
        }
    }
}

private fun MediaSessionController.preservesConfirmationHandoff(
    reply: McReply.Failure,
    command: RemoteCommand,
): Boolean {
    val previous = state.value.preview
    val replacement = reply.error.replacementPreview
    if (previous == null || replacement == null) return false
    val confirmsCurrent =
        command is RemoteCommand.ActivityConfirm &&
            command.params.confirmationId == previous.confirmationId
    val sameAction =
        reply.error.code == WireErrorCode.CONFIRMATION_STALE &&
            replacement.action == previous.action && replacement.confirmMethod == previous.confirmMethod
    val sameOwner =
        replacement.ownerDeviceId == previous.ownerDeviceId &&
            replacement.expected.serverEpoch == previous.expected.serverEpoch &&
            replacement.expected.serverEpoch == state.value.snapshot?.serverEpoch
    return confirmsCurrent && sameAction && sameOwner
}

internal fun MediaSessionController.receiveSourceResult(result: RemoteResult): Boolean =
    when (result) {
        is RemoteResult.DrivesPrepareEject -> {
            receivePreparation(result.value)
            true
        }

        is RemoteResult.FoldersPrepareAdd -> {
            receivePreparation(result.value)
            true
        }

        is RemoteResult.SourcesPrepareRemove -> {
            receivePreparation(result.value)
            true
        }

        is RemoteResult.DrivesConfirmEject,
        is RemoteResult.FoldersConfirmAdd,
        is RemoteResult.SourcesConfirmRemove,
        is RemoteResult.SourcesConfirmReattachment,
        -> {
            mutable.value = state.value.copy(preview = null)
            reconcileOperation()
            true
        }

        is RemoteResult.OperationsCancel -> {
            reconcileOperation()
            if (state.value.busy && state.value.messageOperation == null) {
                message("Cancellation requested. Waiting for native completion.")
            }
            true
        }

        is RemoteResult.SourcesCancelScan -> {
            reconcileOperation()
            if (state.value.busy) message("Cancellation requested. Waiting for the scan to stop.")
            true
        }

        else -> {
            false
        }
    }
