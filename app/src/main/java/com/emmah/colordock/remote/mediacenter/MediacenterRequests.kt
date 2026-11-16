package com.emmah.colordock.remote.mediacenter

import com.emmah.colordock.remote.mediacenter.protocol.Delivery
import com.emmah.colordock.remote.mediacenter.protocol.McReply
import com.emmah.colordock.remote.mediacenter.protocol.McRequest
import com.emmah.colordock.remote.mediacenter.protocol.MethodName
import com.emmah.colordock.remote.mediacenter.protocol.OperationStatus
import com.emmah.colordock.remote.mediacenter.protocol.PairedMediacenter
import com.emmah.colordock.remote.mediacenter.protocol.RemoteJson
import com.emmah.colordock.remote.mediacenter.protocol.RemoteResult
import com.emmah.colordock.remote.mediacenter.protocol.RemoteSnapshot
import com.emmah.colordock.remote.mediacenter.protocol.WireErrorCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

/** Owns correlation and safe read recovery; never retains a command or its parameters. */
internal class MediacenterRequests(
    private val control: MediacenterControl,
    private val scope: CoroutineScope,
    private val recovery: PendingRecovery,
    private val lock: Any,
    private val pairing: () -> PairedMediacenter?,
    private val ready: () -> Boolean,
) {
    private data class Tracked(
        val method: MethodName,
        val delivery: Delivery,
        val recoveryId: String? = null,
    )

    private val requests = mutableMapOf<String, Tracked>()
    private val deadlines = mutableMapOf<String, Job>()
    private var polling: Job? = null
    private val cooldowns = mutableMapOf<String, Job>()
    private var recoveryCursor = 0
    private var operationsPosition: Pair<String, String>? = null
    private val received = MutableSharedFlow<McReply>(extraBufferCapacity = REPLY_CAPACITY)
    val replies = received.asSharedFlow()
    private val uncertain = MutableStateFlow(false)
    val inputOutcomeUncertain = uncertain.asStateFlow()

    fun submit(
        request: McRequest,
        target: PairedMediacenter,
        epoch: String,
    ): Boolean {
        val capacity = requests.size < MAX_REQUESTS && !requests.containsKey(request.id)
        return capacity &&
            runCatching {
                RemoteJson.encodeRequest(request)
                recovery.record(request, target, epoch) && track(request, null)
            }.getOrDefault(false)
    }

    private fun track(
        request: McRequest,
        recoveryId: String?,
    ): Boolean {
        requests[request.id] = Tracked(request.command.method, request.command.delivery, recoveryId)
        deadlines[request.id] =
            scope.launch {
                delay(ACK_DEADLINE_MS)
                synchronized(lock) {
                    val tracked = requests.remove(request.id)
                    deadlines.remove(request.id)
                    if (tracked?.delivery == Delivery.TRANSIENT) uncertain.value = true
                }
            }
        return control.send(request)
    }

    fun receiveReply(message: String) {
        val root = JSONObject(message)
        val id = root.getString("id")
        val tracked = requests.remove(id) ?: return
        deadlines.remove(id)?.cancel()
        val reply = checkedRemoteReply(tracked.method, message)
        val target = pairing() ?: return
        if (tracked.recoveryId != null) {
            when (reply) {
                is McReply.Success -> {
                    val operation = (reply.result as RemoteResult.OperationsGet).value
                    require(operation.requestId == tracked.recoveryId)
                    recovery.observed(target, operation)
                }

                is McReply.Failure -> {
                    if (reply.error.code ==
                        WireErrorCode.NOT_FOUND
                    ) {
                        recovery.finish(target, tracked.recoveryId, OperationStatus.OUTCOME_UNKNOWN)
                    }
                }
            }
        } else if (tracked.delivery == Delivery.DURABLE) {
            val result = root.optJSONObject("result")
            val acceptedOperation =
                result?.optString("operationId")?.takeIf { it.isNotEmpty() }
                    ?: result?.optJSONObject("operation")?.optString("operationId")?.takeIf { it.isNotEmpty() }
            when {
                reply is McReply.Failure -> {
                    recovery.finish(target, id, OperationStatus.FAILED)
                }

                result?.optString("status") == "accepted" && acceptedOperation != null -> {
                    recovery.accepted(target, id, acceptedOperation)
                }

                else -> {
                    recovery.finish(target, id, OperationStatus.SUCCEEDED)
                }
            }
        }
        received.tryEmit(reply)
    }

    fun nativeReady(native: RemoteSnapshot) {
        val target = pairing() ?: return
        val position = native.serverEpoch to native.operations.revision
        if (position != operationsPosition) {
            native.operations.items.forEach { recovery.observed(target, it) }
            operationsPosition = position
        }
        if (polling?.isActive != true) {
            polling =
                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    while (true) {
                        synchronized(lock) { recoverPending() }
                        delay(RECOVERY_TICK_MS)
                    }
                }
        }
    }

    private fun recoverPending() {
        val target = pairing() ?: return
        val slots = requests.size < MAX_REQUESTS && requests.values.count { it.recoveryId != null } < RECOVERY_IN_FLIGHT
        if (ready() && slots) {
            val candidates = recovery.lookups(target)
            val count = candidates.size
            val eligible =
                (0 until count).map { (recoveryCursor + it) % count }.firstOrNull { index ->
                    val id = candidates[index].first
                    id !in cooldowns && id !in requests && requests.values.none { it.recoveryId == id }
                }
            if (eligible != null) {
                val (requestId, command) = candidates[eligible]
                recoveryCursor = (eligible + 1) % count
                cooldowns[requestId] =
                    scope.launch {
                        delay(OPERATION_POLL_MS)
                        synchronized(lock) { cooldowns.remove(requestId) }
                    }
                track(McRequest(UUID.randomUUID().toString(), command), requestId)
            }
        }
    }

    fun invalidate() {
        if (requests.values.any { it.delivery == Delivery.TRANSIENT }) uncertain.value = true
        requests.clear()
        deadlines.values.forEach { it.cancel() }
        deadlines.clear()
        polling?.cancel()
        polling = null
        cooldowns.values.forEach { it.cancel() }
        cooldowns.clear()
        recoveryCursor = 0
        operationsPosition = null
    }

    fun forget(server: String): Boolean =
        runCatching {
            recovery.forget(server)
            uncertain.value = false
        }.isSuccess

    private companion object {
        const val ACK_DEADLINE_MS = 5000L
        const val OPERATION_POLL_MS = 2000L
        const val RECOVERY_TICK_MS = 250L
        const val RECOVERY_IN_FLIGHT = 4
        const val MAX_REQUESTS = 256
        const val REPLY_CAPACITY = 64
    }
}

private fun checkedRemoteReply(
    method: MethodName,
    message: String,
): McReply =
    runCatching { RemoteJson.parseReply(method, message) }
        .onFailure { protocolTrace("Reply ${method.wire} rejected", it) }
        .getOrThrow()
