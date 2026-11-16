package com.emmah.colordock.remote.ui.mediacenter

import com.emmah.colordock.remote.mediacenter.protocol.ConfirmationPreview
import com.emmah.colordock.remote.mediacenter.protocol.Delivery
import com.emmah.colordock.remote.mediacenter.protocol.McRequest
import com.emmah.colordock.remote.mediacenter.protocol.MediaItem
import com.emmah.colordock.remote.mediacenter.protocol.OperationState
import com.emmah.colordock.remote.mediacenter.protocol.OperationStatus
import com.emmah.colordock.remote.mediacenter.protocol.Playlist
import com.emmah.colordock.remote.mediacenter.protocol.PlaylistContents
import com.emmah.colordock.remote.mediacenter.protocol.PlaylistPage
import com.emmah.colordock.remote.mediacenter.protocol.QueuePage
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand
import com.emmah.colordock.remote.mediacenter.protocol.RemoteSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal enum class MediaDestination { Remote, Library, Settings }

internal data class MediaSessionState(
    val snapshot: RemoteSnapshot? = null,
    val settings: RoutineSettingsState = RoutineSettingsState(),
    val destination: MediaDestination = MediaDestination.Remote,
    val message: String = "Connect to Mediacenter.",
    // Only the native operation that produced the current message; local feedback clears this.
    val messageOperation: OperationState? = null,
    val notice: ActionNotice? = null,
    val resume: MediaItem? = null,
    val preview: ConfirmationPreview? = null,
    val queue: QueuePage? = null,
    val queueOpen: Boolean = false,
    val playlistQuery: String = "",
    val playlists: PlaylistPage? = null,
    val playlist: PlaylistContents? = null,
    val busy: Boolean = false,
    val showingMenus: Boolean = false,
)

/** One owner for native media workflows. Acceptance is never shown as playback success. */
internal class MediaSessionController(
    internal val scope: CoroutineScope,
    internal val send: (McRequest) -> Boolean,
) {
    internal val mutable = MutableStateFlow(MediaSessionState())
    val state = mutable.asStateFlow()
    internal val requests = linkedMapOf<String, RemoteCommand>()
    internal val deadlines = mutableMapOf<String, Job>()
    internal var observedOperation: OperationState? = null
    internal var operationCommand: RemoteCommand? = null
    internal var photoSelection: String? = null
    internal var photoSession: String? = null
    internal var operationRequest: String? = null
    internal var handoff: Handoff? = null
    internal var handoffReady = false
    internal var playlistSearch: Job? = null
    internal var playPlaylistAfterRead = false
    internal var discoveredModal: String? = null
    internal var retiredBrowserPreview: String? = null

    internal data class Handoff(
        val item: MediaItem,
        val entry: String?,
        val selection: String?,
        val previousSession: String?,
        val playlist: Playlist? = null,
    )

    val confirmationStatus =
        ConfirmationStatusReconciler(send, {
            state.value.snapshot?.serverEpoch to
                state.value.preview.takeUnless { state.value.busy }
        }, { id ->
            if (state.value.preview?.confirmationId ==
                id
            ) {
                clearConfirmation()
            }
        })

    fun observe(snapshot: RemoteSnapshot?) {
        val old = state.value.snapshot
        if (snapshot == null || old?.serverEpoch != snapshot.serverEpoch ||
            old.connection.serverDeviceId != snapshot.connection.serverDeviceId
        ) {
            resetOwner(snapshot)
        } else {
            val sameActivity = old.activity.sessionId == snapshot.activity.sessionId
            mutable.value =
                state.value.copy(snapshot = snapshot, showingMenus = sameActivity && state.value.showingMenus)
        }
        if (snapshot == null) return
        mutable.value = state.value.copy(settings = state.value.settings.copy(vpn = snapshot.vpn))
        if (photoSession != null && photoSession != snapshot.slideshow.sessionId) {
            photoSelection = null
            photoSession = null
        }
        snapshot.operations.items.forEach(::operation)
        finishHandoff()
        discoverConfirmation(snapshot)
        if (state.value.queueOpen && state.value.queue?.revision != snapshot.queueSummary.revision &&
            requests.values.none { it is RemoteCommand.QueueGet }
        ) {
            refreshQueue()
        }
    }

    fun command(command: RemoteCommand): Boolean {
        val stopping =
            command is RemoteCommand.PlayerStop || command is RemoteCommand.PhotosStop ||
                command is RemoteCommand.SourcesCancelScan || command is RemoteCommand.OperationsCancel
        if (state.value.busy && !stopping) {
            return false
        }
        if (stopping) {
            handoff = null
            handoffReady = false
        }
        return submit(command)
    }

    internal fun submit(command: RemoteCommand): Boolean {
        val request = createRequest(command) ?: return false
        if (!send(request)) {
            message("Mediacenter is not ready for this action. Reconnect or refresh.")
        } else {
            trackRequest(request, command)
        }
        return requests.containsKey(request.id)
    }

    internal fun trackRequest(
        request: McRequest,
        command: RemoteCommand,
    ) {
        requests[request.id] = command
        deadlines[request.id] =
            scope.launch {
                delay(MEDIA_REQUEST_TIMEOUT_MS)
                if (requests.remove(request.id) != null) {
                    deadlines.remove(request.id)
                    if (command.delivery ==
                        Delivery.DURABLE
                    ) {
                        handoff = null
                        message(
                            "Acknowledgement missing. Inspect native state; the action will not be replayed.",
                            notice = ActionNotice.NOT_CONFIRMED,
                        )
                    } else {
                        if (playPlaylistAfterRead && command is RemoteCommand.PlaylistsGet) {
                            playPlaylistAfterRead = false
                            mutable.value = state.value.copy(busy = false)
                        }
                        message("Request timed out. Refresh to retry.")
                    }
                    observedOperation?.let(::operation)
                }
            }
        if (command.delivery == Delivery.DURABLE && command !is RemoteCommand.SourcesCancelScan &&
            command !is RemoteCommand.OperationsCancel
        ) {
            operationRequest = request.id
            operationCommand = command
            observedOperation = null
            mutable.value =
                state.value.copy(
                    busy = true,
                    message = "Waiting for Mediacenter…",
                    messageOperation = null,
                    notice = null,
                )
        }
    }

    fun operation(value: OperationState) {
        if (value.requestId != operationRequest) return
        observedOperation = value
        if (requests.containsKey(value.requestId)) return
        when (value.status) {
            OperationStatus.SUCCEEDED -> {
                operationRequest = null
                handoffReady = handoff != null && state.value.preview == null
                mutable.value =
                    state.value.copy(
                        busy = false,
                        message = driveSuccess(operationCommand),
                        notice = driveCompletionNotice(operationCommand),
                        messageOperation = value,
                    )
                refreshSettingsAfterSuccess(operationCommand)
                if (state.value.queueOpen) refreshQueue()
                if (state.value.playlist != null) refreshPlaylist()
                finishHandoff()
            }

            OperationStatus.FAILED, OperationStatus.CANCELLED, OperationStatus.OUTCOME_UNKNOWN -> {
                operationRequest = null
                handoff = null
                handoffReady = false
                photoSelection = null
                photoSession = null
                val detail =
                    driveFailure(operationCommand, value.errorCode) ?: value.errorCode?.wire
                        ?: "inspect current state before retrying"
                mutable.value =
                    state.value.copy(
                        busy = false,
                        message = "Action ${value.status.wire}: $detail",
                        notice = operationNotice(value, operationCommand?.method?.wire?.startsWith("drives.") == true),
                        messageOperation = value,
                    )
                if (operationCommand?.method?.wire?.startsWith("queue.") == true) refreshQueue()
                if (operationCommand?.method?.wire?.startsWith("playlists.") == true) refreshPlaylist()
            }

            else -> {
                sourceProgress(value)
                driveProgress(value)
            }
        }
    }
}
