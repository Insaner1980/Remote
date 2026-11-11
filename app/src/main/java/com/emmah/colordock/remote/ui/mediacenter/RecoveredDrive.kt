package com.emmah.colordock.remote.ui.mediacenter

import com.emmah.colordock.remote.mediacenter.DriveWork
import com.emmah.colordock.remote.mediacenter.protocol.OperationState
import com.emmah.colordock.remote.mediacenter.protocol.OperationStatus
import com.emmah.colordock.remote.mediacenter.protocol.WireErrorCode

internal data class RecoveredDrive(
    val kind: DriveWork,
    val operation: OperationState,
) {
    val running: Boolean get() = operation.status in listOf(OperationStatus.ACCEPTED, OperationStatus.RUNNING)
    val message: String get() =
        when {
            running && operation.cancellationRequested -> {
                "Cancellation requested. Waiting for native drive completion."
            }

            running -> {
                "Earlier drive operation is still running. Wait for native completion."
            }

            operation.status == OperationStatus.OUTCOME_UNKNOWN -> {
                "Earlier drive operation outcome is unknown. Refresh drives and inspect the computer before retrying."
            }

            operation.status == OperationStatus.CANCELLED -> {
                "Earlier drive operation was cancelled. Refresh drives."
            }

            operation.status == OperationStatus.SUCCEEDED && kind == DriveWork.EJECT -> {
                "Earlier safe-removal operation completed. Refresh drives to check the current connection."
            }

            operation.status == OperationStatus.SUCCEEDED -> {
                "Earlier mount operation completed. Refresh drives."
            }

            operation.errorCode == WireErrorCode.BUSY -> {
                "Earlier drive operation failed: another application may be using it. " +
                    "Close its files and review removal again."
            }

            operation.errorCode == WireErrorCode.LOCAL_AUTHORIZATION_REQUIRED -> {
                "Earlier drive operation needs authorization on the computer. Authorize it there, then refresh drives."
            }

            else -> {
                "Earlier drive operation failed. Refresh drives and review the action again."
            }
        }
}
