package com.emmah.colordock.remote.ui.mediacenter

import com.emmah.colordock.remote.mediacenter.protocol.ConfirmationPreview
import com.emmah.colordock.remote.mediacenter.protocol.ConfirmationsGetParams
import com.emmah.colordock.remote.mediacenter.protocol.Expected
import com.emmah.colordock.remote.mediacenter.protocol.McReply
import com.emmah.colordock.remote.mediacenter.protocol.McRequest
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand
import com.emmah.colordock.remote.mediacenter.protocol.WireErrorCode
import java.util.UUID

private const val STATUS_TIMEOUT_MS = 5000
internal const val STATUS_INTERVAL_MS = 1000L
private const val NANOS_PER_MILLISECOND = 1_000_000

/** Bounded safe-read reconciliation for a server-owned sheet resolved on another surface. */
internal class ConfirmationStatusReconciler(
    private val send: (McRequest) -> Boolean,
    private val current: () -> Pair<String?, ConfirmationPreview?>,
    private val resolved: (String) -> Unit,
    private val now: () -> Long = { System.nanoTime() / NANOS_PER_MILLISECOND },
) {
    private data class Pending(
        val requestId: String,
        val epoch: String,
        val confirmationId: String,
        val sentAt: Long,
    )

    private var pending: Pending? = null
    private var lastSent: Long? = null

    fun refresh() {
        val (epoch, preview) = current()
        val time = now()
        if (epoch == null || preview == null) {
            pending = null
            lastSent = null
            return
        }
        val previous = pending
        val samePending = previous?.let { it.epoch == epoch && it.confirmationId == preview.confirmationId } == true
        val waiting = samePending && time - previous!!.sentAt < STATUS_TIMEOUT_MS
        val throttled = lastSent?.let { time - it < STATUS_INTERVAL_MS } == true
        if (waiting || throttled) return
        val id = UUID.randomUUID().toString()
        val request =
            McRequest(
                id,
                RemoteCommand.ConfirmationsGet(ConfirmationsGetParams(preview.confirmationId)),
                Expected(serverEpoch = epoch),
            )
        if (send(request)) {
            pending = Pending(id, epoch, preview.confirmationId, time)
            lastSent = time
        }
    }

    fun receive(reply: McReply): Boolean {
        val request = pending?.takeIf { reply.id == it.requestId } ?: return false
        pending = null
        val (epoch, preview) = current()
        val sameSheet = epoch == request.epoch && preview?.confirmationId == request.confirmationId
        val terminal =
            reply is McReply.Failure &&
                reply.error.code in setOf(WireErrorCode.NOT_FOUND, WireErrorCode.CONFIRMATION_EXPIRED)
        if (sameSheet && terminal) resolved(request.confirmationId)
        return true
    }
}
