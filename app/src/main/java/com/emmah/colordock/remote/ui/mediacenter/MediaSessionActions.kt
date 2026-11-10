package com.emmah.colordock.remote.ui.mediacenter

import com.emmah.colordock.remote.mediacenter.protocol.ActivityConfirmParams
import com.emmah.colordock.remote.mediacenter.protocol.AudioConfirmParams
import com.emmah.colordock.remote.mediacenter.protocol.ConfirmMethod
import com.emmah.colordock.remote.mediacenter.protocol.ConfirmationsCancelParams
import com.emmah.colordock.remote.mediacenter.protocol.PhotosSlideshowStartParams
import com.emmah.colordock.remote.mediacenter.protocol.QueueEnqueueParams
import com.emmah.colordock.remote.mediacenter.protocol.QueueGetParams
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand

internal fun MediaSessionController.destination(value: MediaDestination) {
    mutable.value = state.value.copy(destination = value)
}

internal fun MediaSessionController.message(value: String) {
    mutable.value = state.value.copy(message = value)
}

internal fun MediaSessionController.confirm() {
    val preview = state.value.preview ?: return
    val next =
        when (preview.confirmMethod) {
            ConfirmMethod.AUDIO_CONFIRM -> {
                RemoteCommand.AudioConfirm(AudioConfirmParams(preview.confirmationId))
            }

            ConfirmMethod.ACTIVITY_CONFIRM -> {
                RemoteCommand.ActivityConfirm(ActivityConfirmParams(preview.confirmationId))
            }

            else -> {
                message("Open this action in its Mediacenter settings page.")
                return
            }
        }
    command(next)
}

internal fun MediaSessionController.cancelConfirmation() {
    state.value.preview?.let {
        command(
            RemoteCommand.ConfirmationsCancel(ConfirmationsCancelParams(it.confirmationId)),
        )
    }
}

internal fun MediaSessionController.queue(open: Boolean) {
    mutable.value = state.value.copy(queueOpen = open)
    if (open) refreshQueue()
}

internal fun MediaSessionController.refreshQueue(cursor: String? = null) {
    submit(RemoteCommand.QueueGet(QueueGetParams(cursor, MEDIA_PAGE_SIZE)))
}

internal fun MediaSessionController.enqueue(ids: List<String>) {
    if (ids.size in
        1..MEDIA_PAGE_SIZE
    ) {
        command(RemoteCommand.QueueEnqueue(QueueEnqueueParams(ids)))
    } else {
        message("Add between 1 and 100 items at a time.")
    }
}

internal fun MediaSessionController.slideshow(
    selection: String?,
    start: String?,
    seconds: Int,
) {
    if (selection == null ||
        seconds !in 1..PHOTO_MAX_INTERVAL
    ) {
        message("Choose a photo selection and an interval from 1 to 60 seconds.")
        return
    }
    command(RemoteCommand.PhotosSlideshowStart(PhotosSlideshowStartParams(selection, start, seconds)))
}
