package com.emmah.colordock.remote.ui.mediacenter

import com.emmah.colordock.remote.mediacenter.protocol.OperationState
import com.emmah.colordock.remote.mediacenter.protocol.OperationStatus
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand
import com.emmah.colordock.remote.mediacenter.protocol.WireErrorCode

internal enum class ActionNotice(
    val label: String,
) {
    RETRY("Try again"),
    NOT_CONFIRMED("Not confirmed"),
    WORKING("Working…"),
    STOPPING("Stopping…"),
    SAFE_TO_UNPLUG("Safe to unplug"),
    APPROVE_ON_COMPUTER("Approve on computer"),
    CLOSE_FILES("Close files on computer"),
    NARROW_SEARCH("Narrow your search"),
    SHORTEN_SEARCH("Shorten search"),
    OUTPUT_UNAVAILABLE("Output unavailable"),
}

internal fun errorNotice(
    code: WireErrorCode?,
    drive: Boolean = false,
): ActionNotice =
    when (code) {
        WireErrorCode.LOCAL_AUTHORIZATION_REQUIRED -> ActionNotice.APPROVE_ON_COMPUTER
        WireErrorCode.SELECTION_TOO_LARGE -> ActionNotice.NARROW_SEARCH
        WireErrorCode.AUDIO_OUTPUT_UNAVAILABLE -> ActionNotice.OUTPUT_UNAVAILABLE
        WireErrorCode.BUSY -> if (drive) ActionNotice.CLOSE_FILES else ActionNotice.RETRY
        else -> ActionNotice.RETRY
    }

internal fun operationNotice(
    value: OperationState,
    drive: Boolean = false,
): ActionNotice? =
    when (value.status) {
        OperationStatus.ACCEPTED, OperationStatus.RUNNING -> {
            if (value.cancellationRequested) ActionNotice.STOPPING else ActionNotice.WORKING
        }

        OperationStatus.FAILED -> {
            errorNotice(value.errorCode, drive)
        }

        OperationStatus.OUTCOME_UNKNOWN -> {
            ActionNotice.NOT_CONFIRMED
        }

        OperationStatus.SUCCEEDED, OperationStatus.CANCELLED -> {
            null
        }
    }

internal fun driveCompletionNotice(command: RemoteCommand?): ActionNotice? =
    if (command is RemoteCommand.DrivesConfirmEject) ActionNotice.SAFE_TO_UNPLUG else null
