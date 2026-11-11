package com.emmah.colordock.remote.mediacenter

import com.emmah.colordock.remote.mediacenter.protocol.Delivery
import com.emmah.colordock.remote.mediacenter.protocol.McRequest
import com.emmah.colordock.remote.mediacenter.protocol.OperationState
import com.emmah.colordock.remote.mediacenter.protocol.OperationStatus
import com.emmah.colordock.remote.mediacenter.protocol.OperationsGetParams
import com.emmah.colordock.remote.mediacenter.protocol.PairedMediacenter
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal fun PendingCommand.belongsTo(pairing: PairedMediacenter): Boolean =
    serverId == pairing.serverDeviceId && pairedDeviceId == pairing.deviceId

internal class PendingRecovery(
    private val store: PendingCommandStore,
) {
    private val restored = runCatching { store.read() }
    private val healthy = MutableStateFlow(restored.isSuccess)
    val available = healthy.asStateFlow()
    private val mutable = MutableStateFlow(restored.getOrDefault(emptyList()))
    val pending = mutable.asStateFlow()

    fun record(
        request: McRequest,
        pairing: PairedMediacenter,
        epoch: String,
    ): Boolean =
        when {
            request.command.delivery != Delivery.DURABLE -> {
                true
            }

            !healthy.value -> {
                false
            }

            mutable.value.any { it.belongsTo(pairing) && it.requestId == request.id } -> {
                false
            }

            else -> {
                val previous = mutable.value
                val completed = previous.indexOfFirst { it.resolved }
                if (previous.size >= FilePendingCommandStore.MAX_COMMANDS && completed < 0) {
                    false
                } else {
                    val keep =
                        if (previous.size <
                            FilePendingCommandStore.MAX_COMMANDS
                        ) {
                            previous
                        } else {
                            previous.filterIndexed { index, _ ->
                                index !=
                                    completed
                            }
                        }
                    publish(
                        keep +
                            PendingCommand(
                                pairing.serverDeviceId,
                                request.id,
                                epoch,
                                pairedDeviceId = pairing.deviceId,
                                sourceScanWork = sourceScanWork(request.command),
                                driveWork = driveWork(request.command),
                            ),
                    )
                    true
                }
            }
        }

    fun accepted(
        pairing: PairedMediacenter,
        requestId: String,
        operationId: String,
    ) {
        publish(
            mutable.value.map { command ->
                val canAccept = !command.resolved && command.status != OperationStatus.RUNNING
                if (command.belongsTo(pairing) && command.requestId == requestId && canAccept) {
                    command.copy(operationId = operationId, status = OperationStatus.ACCEPTED)
                } else {
                    command
                }
            },
        )
    }

    fun observed(
        pairing: PairedMediacenter,
        operation: OperationState,
    ) {
        publish(
            mutable.value.map { command ->
                if (command.belongsTo(pairing) && command.requestId == operation.requestId) {
                    require(command.operationId == null || command.operationId == operation.operationId)
                    val nonterminal = operation.status in setOf(OperationStatus.ACCEPTED, OperationStatus.RUNNING)
                    val stale =
                        (command.driveObserved && command.resolved) ||
                            (command.resolved && nonterminal) ||
                            (command.status == OperationStatus.RUNNING && operation.status == OperationStatus.ACCEPTED)
                    if (stale) {
                        command
                    } else {
                        command.copy(
                            operationId = operation.operationId,
                            status = operation.status,
                            resolved = !nonterminal,
                            cancellationRequested = operation.cancellationRequested,
                            driveObserved = command.driveWork != null,
                            driveErrorCode = operation.errorCode.takeIf { command.driveWork != null },
                        )
                    }
                } else {
                    command
                }
            },
        )
    }

    fun finish(
        pairing: PairedMediacenter,
        requestId: String,
        status: OperationStatus,
    ) {
        publish(
            mutable.value.map { command ->
                if (command.belongsTo(pairing) && command.requestId == requestId) {
                    if (command.driveObserved && command.resolved) {
                        command
                    } else {
                        command.copy(
                            status = status,
                            resolved = true,
                            driveObserved = false,
                            driveErrorCode = null,
                        )
                    }
                } else {
                    command
                }
            },
        )
    }

    fun lookups(pairing: PairedMediacenter): List<Pair<String, RemoteCommand.OperationsGet>> =
        mutable.value.filter { it.belongsTo(pairing) && !it.resolved }.map { command ->
            command.requestId to
                RemoteCommand.OperationsGet(
                    if (command.operationId == null) {
                        OperationsGetParams(requestId = command.requestId)
                    } else {
                        OperationsGetParams(operationId = command.operationId)
                    },
                )
        }

    fun forget(server: String) {
        val keep = mutable.value.filterNot { it.serverId == server }
        store.write(keep)
        mutable.value = keep
        healthy.value = true
    }

    private fun publish(commands: List<PendingCommand>) {
        if (commands == mutable.value) return
        try {
            check(healthy.value)
            store.write(commands)
            mutable.value = commands
        } catch (failure: java.io.IOException) {
            healthy.value = false
            throw failure
        } catch (failure: IllegalArgumentException) {
            healthy.value = false
            throw failure
        }
    }
}

private fun sourceScanWork(command: RemoteCommand): SourceScanWork? =
    when (command) {
        is RemoteCommand.SourcesScan, is RemoteCommand.FoldersConfirmAdd -> SourceScanWork.SCAN
        else -> null
    }

private fun driveWork(command: RemoteCommand): DriveWork? =
    when (command) {
        is RemoteCommand.DrivesMount -> DriveWork.MOUNT
        is RemoteCommand.DrivesConfirmEject -> DriveWork.EJECT
        else -> null
    }
