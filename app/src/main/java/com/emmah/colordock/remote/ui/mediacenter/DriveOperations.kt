package com.emmah.colordock.remote.ui.mediacenter

import com.emmah.colordock.remote.mediacenter.protocol.OperationState
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand
import com.emmah.colordock.remote.mediacenter.protocol.WireErrorCode

internal fun driveFailure(
    command: RemoteCommand?,
    code: WireErrorCode?,
): String? {
    if (command?.method?.wire?.startsWith("drives.") != true) return null
    return when (code) {
        WireErrorCode.LOCAL_AUTHORIZATION_REQUIRED -> {
            "Use the computer to authorize this drive operation, then refresh drives."
        }

        WireErrorCode.BUSY -> {
            "Another application may be using the drive. Close its files, then review removal again."
        }

        WireErrorCode.CONFIRMATION_STALE -> {
            "Drive or playback changed. Refresh drives and review removal again."
        }

        else -> {
            null
        }
    }
}

internal fun driveSuccess(command: RemoteCommand?): String =
    when (command) {
        is RemoteCommand.DrivesConfirmEject -> "Safe removal completed. You can unplug the drive."
        is RemoteCommand.DrivesMount -> "Drive mounted. Refresh drives to see its volumes."
        else -> ""
    }

internal fun MediaSessionController.driveProgress(value: OperationState) {
    if (operationCommand is RemoteCommand.DrivesMount || operationCommand is RemoteCommand.DrivesConfirmEject) {
        message(
            if (value.cancellationRequested) {
                "Cancellation requested. Waiting for native completion."
            } else {
                "Drive operation running. Wait for confirmed completion before unplugging."
            },
            operation = value,
            notice = if (value.cancellationRequested) ActionNotice.STOPPING else ActionNotice.WORKING,
        )
    }
}
