package com.emmah.colordock.remote.ui.mediacenter

import com.emmah.colordock.remote.mediacenter.PendingCommand
import com.emmah.colordock.remote.mediacenter.SourceScanWork
import com.emmah.colordock.remote.mediacenter.protocol.McReply
import com.emmah.colordock.remote.mediacenter.protocol.OperationState
import com.emmah.colordock.remote.mediacenter.protocol.OperationStatus
import com.emmah.colordock.remote.mediacenter.protocol.RemoteResult
import com.emmah.colordock.remote.mediacenter.protocol.RemoteSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A disk tag is provenance, never evidence that native work is still running. */
internal class RecoveredSourceScans {
    private val mutable = MutableStateFlow<List<OperationState>>(emptyList())
    val state = mutable.asStateFlow()
    private var context: Triple<String, String, String>? = null
    private var candidates = emptyList<PendingCommand>()
    private val observed = mutableMapOf<String, OperationState>()

    fun observe(
        snapshot: RemoteSnapshot?,
        commands: List<PendingCommand>,
        pairedDeviceId: String?,
    ) {
        val current =
            snapshot?.let { native ->
                pairedDeviceId?.let { Triple(native.connection.serverDeviceId, native.serverEpoch, it) }
            }
        if (context != current) observed.clear()
        context = current
        candidates =
            commands
                .filter { record ->
                    current != null && record.serverId == current.first && record.serverEpoch == current.second &&
                        record.pairedDeviceId == current.third && !record.resolved &&
                        record.sourceScanWork == SourceScanWork.SCAN
                }.take(MEDIA_PAGE_SIZE)
        observed.keys.retainAll(candidates.map { it.requestId }.toSet())
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
        val candidate = candidates.firstOrNull { it.requestId == operation.requestId } ?: return
        if (candidate.operationId != null && candidate.operationId != operation.operationId) return
        val old = observed[operation.requestId]
        val terminal = old != null && !old.running()
        val older = old?.status == OperationStatus.RUNNING && operation.status == OperationStatus.ACCEPTED
        if (!terminal && !older) {
            observed[operation.requestId] =
                operation.copy(
                    cancellationRequested =
                        operation.cancellationRequested || old?.cancellationRequested == true,
                )
        }
    }

    private fun publish() {
        mutable.value = observed.values.filter { it.running() }.toList()
    }
}

private fun OperationState.running() = status == OperationStatus.ACCEPTED || status == OperationStatus.RUNNING
