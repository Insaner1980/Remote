package com.emmah.colordock.remote.ui.mediacenter

import com.emmah.colordock.remote.mediacenter.PendingCommand
import com.emmah.colordock.remote.mediacenter.protocol.McReply
import com.emmah.colordock.remote.mediacenter.protocol.OperationState
import com.emmah.colordock.remote.mediacenter.protocol.OperationStatus
import com.emmah.colordock.remote.mediacenter.protocol.RemoteResult
import com.emmah.colordock.remote.mediacenter.protocol.RemoteSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Only exact current native observations permit cancellation; persisted terminal facts survive reconstruction. */
internal class RecoveredDrives {
    private val mutable = MutableStateFlow<List<RecoveredDrive>>(emptyList())
    val state = mutable.asStateFlow()
    private var context: Triple<String, String, String>? = null
    private var candidates = emptyList<PendingCommand>()
    private val observed = mutableMapOf<String, OperationState>()

    fun observe(
        snapshot: RemoteSnapshot?,
        commands: List<PendingCommand>,
        phone: String?,
    ) {
        val current =
            snapshot?.let { native ->
                phone?.let { Triple(native.connection.serverDeviceId, native.serverEpoch, it) }
            }
        if (current != context) observed.clear()
        context = current
        candidates =
            commands
                .filter {
                    current != null && it.serverId == current.first && it.serverEpoch == current.second &&
                        it.pairedDeviceId == current.third && it.driveWork != null
                }.take(MEDIA_PAGE_SIZE)
        observed.keys.retainAll(candidates.map { it.requestId }.toSet())
        candidates.filter { it.resolved && !it.driveObserved }.forEach { observed.remove(it.requestId) }
        candidates.filter { it.resolved && it.driveObserved && it.operationId != null }.forEach { saved ->
            remember(
                OperationState(
                    requireNotNull(saved.operationId),
                    saved.requestId,
                    saved.status,
                    saved.driveErrorCode,
                    saved.cancellationRequested,
                ),
            )
        }
        snapshot?.operations?.items?.forEach(::remember)
        publish()
    }

    fun receive(reply: McReply) {
        val operation = ((reply as? McReply.Success)?.result as? RemoteResult.OperationsGet)?.value
        if (context != null && operation != null) {
            remember(operation)
            publish()
        }
    }

    private fun remember(operation: OperationState) {
        val record = candidates.firstOrNull { it.requestId == operation.requestId } ?: return
        if (record.operationId != null && record.operationId != operation.operationId) return
        val old = observed[operation.requestId]
        val terminal = old != null && old.status !in listOf(OperationStatus.ACCEPTED, OperationStatus.RUNNING)
        val older = old?.status == OperationStatus.RUNNING && operation.status == OperationStatus.ACCEPTED
        if (!terminal && !older) {
            observed[operation.requestId] =
                operation.copy(
                    cancellationRequested = operation.cancellationRequested || old?.cancellationRequested == true,
                )
        }
    }

    private fun publish() {
        val work =
            candidates.mapNotNull { record ->
                observed[record.requestId]?.let { RecoveredDrive(requireNotNull(record.driveWork), it) }
            }
        // Keep current work and the latest completed result; avoid a growing history on Settings.
        val latest = work.lastOrNull { !it.running }
        mutable.value = work.filter { it.running || it == latest }
    }
}
