package com.emmah.colordock.remote.ui.mediacenter

import android.os.SystemClock
import com.emmah.colordock.remote.mediacenter.protocol.ActivityMode
import com.emmah.colordock.remote.mediacenter.protocol.BrowserOpenParams
import com.emmah.colordock.remote.mediacenter.protocol.BrowserState
import com.emmah.colordock.remote.mediacenter.protocol.Capabilities
import com.emmah.colordock.remote.mediacenter.protocol.ConfirmMethod
import com.emmah.colordock.remote.mediacenter.protocol.ConfirmationPreview
import com.emmah.colordock.remote.mediacenter.protocol.Empty
import com.emmah.colordock.remote.mediacenter.protocol.Expected
import com.emmah.colordock.remote.mediacenter.protocol.McReply
import com.emmah.colordock.remote.mediacenter.protocol.McRequest
import com.emmah.colordock.remote.mediacenter.protocol.MethodName
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand
import com.emmah.colordock.remote.mediacenter.protocol.RemoteResult
import com.emmah.colordock.remote.mediacenter.protocol.RemoteSnapshot
import com.emmah.colordock.remote.mediacenter.protocol.SupportedState
import com.emmah.colordock.remote.mediacenter.protocol.WireErrorCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

internal const val STREAMING_REFRESH_MS = 350L
private const val CAPABILITIES_REFRESH_MS = 2100L
private const val READ_DEADLINE_MS = 1750L
private const val AUTOMATIC_PROFILE = "auto"

internal data class StreamingState(
    val capabilities: Capabilities? = null,
    val browser: BrowserState? = null,
    val target: BrowserTarget? = null,
) {
    fun canLaunch(serviceId: String): Boolean =
        capabilities?.browserBridge == true &&
            MethodName.BROWSER_OPEN in capabilities.methods &&
            capabilities.services.any { it.serviceId == serviceId && it.support != SupportedState.UNSUPPORTED }

    fun allows(method: MethodName) = method in capabilities?.methods.orEmpty()
}

/** Reads only while the Remote destination is visible; no browser state is assumed across a connection. */
internal class StreamingController(
    private val send: (McRequest) -> Boolean,
    private val now: () -> Long = SystemClock::elapsedRealtime,
    private val changed: (StreamingState) -> Unit = {},
) {
    private data class Read(
        val id: String,
        val sentAt: Long,
    )

    private val mutable = MutableStateFlow(StreamingState())
    val state = mutable.asStateFlow()
    private var snapshot: RemoteSnapshot? = null
    private var capabilityRead: Read? = null
    private var browserRead: Read? = null
    private var lastCatalog: Long? = null
    private var lastBrowser: Long? = null

    fun observe(snapshot: RemoteSnapshot?) {
        val previous = this.snapshot
        this.snapshot = snapshot
        val ownerChanged =
            previous?.serverEpoch != snapshot?.serverEpoch ||
                previous?.connection?.serverDeviceId != snapshot?.connection?.serverDeviceId
        if (ownerChanged) {
            capabilityRead = null
            lastCatalog = null
            browserRead = null
            lastBrowser = null
            publish(StreamingState())
        }
        if (snapshot == null || previous?.activity?.mode != snapshot.activity.mode ||
            previous.activity.sessionId != snapshot.activity.sessionId
        ) {
            browserRead = null
            lastBrowser = null
            clearTarget()
        }
    }

    fun refresh() {
        val current = snapshot ?: return
        val time = now()
        refreshCapabilities(current.serverEpoch, time)
        refreshBrowser(current, time)
    }

    private fun refreshCapabilities(
        epoch: String,
        time: Long,
    ) {
        val pending = capabilityRead
        if (pending != null && time - pending.sentAt < READ_DEADLINE_MS) return
        if (pending != null) {
            capabilityRead = null
            publish(state.value.copy(capabilities = null))
            clearTarget()
        }
        if (lastCatalog?.let { time - it < CAPABILITIES_REFRESH_MS } == true) return
        capabilityRead = read(RemoteCommand.CapabilitiesGet(Empty), epoch, time)
        lastCatalog = time
    }

    private fun refreshBrowser(
        current: RemoteSnapshot,
        time: Long,
    ) {
        val available = current.activity.mode == ActivityMode.BROWSER && state.value.allows(MethodName.BROWSER_STATE)
        val pending = browserRead
        val waiting = pending != null && time - pending.sentAt < READ_DEADLINE_MS
        if (!available || waiting || lastBrowser?.let { time - it < STREAMING_REFRESH_MS } == true) return
        if (pending != null) clearTarget()
        lastBrowser = time
        browserRead = read(RemoteCommand.BrowserState(Empty), current.serverEpoch, time)
        if (browserRead == null) clearTarget()
    }

    private fun read(
        command: RemoteCommand,
        epoch: String,
        time: Long,
    ): Read? {
        val id = UUID.randomUUID().toString()
        return Read(id, time).takeIf { send(McRequest(id, command, Expected(serverEpoch = epoch))) }
    }

    fun receive(reply: McReply) {
        val result = (reply as? McReply.Success)?.result
        when (reply.id) {
            capabilityRead?.id -> {
                capabilityRead = null
                val capabilities = (result as? RemoteResult.CapabilitiesGet)?.value
                publish(state.value.copy(capabilities = capabilities))
                if (capabilities?.browserBridge != true || !state.value.allows(MethodName.BROWSER_STATE)) {
                    browserRead = null
                    clearTarget()
                }
            }

            browserRead?.id -> {
                browserRead = null
                val browser = (result as? RemoteResult.BrowserState)?.value
                updateTarget(browser)
            }
        }
    }

    private fun updateTarget(browser: BrowserState?) {
        val current = snapshot ?: return
        val session = current.activity.sessionId
        val matching = browser?.takeIf { session != null && it.browserSessionId == session }
        val target =
            matching?.targetGeneration?.takeIf { matching.enrolled }?.let {
                BrowserTarget(current.serverEpoch, requireNotNull(session), it)
            }
        publish(state.value.copy(browser = matching, target = target))
    }

    private fun clearTarget() {
        publish(state.value.copy(browser = null, target = null))
    }

    private fun publish(value: StreamingState) {
        if (value == state.value) return
        mutable.value = value
        changed(value)
    }
}

internal fun MediaSessionController.openBrowser(
    streaming: StreamingState,
    serviceId: String,
) {
    if (state.value.preview == null && streaming.canLaunch(serviceId)) {
        command(RemoteCommand.BrowserOpen(BrowserOpenParams(serviceId = serviceId, profileId = AUTOMATIC_PROFILE)))
    }
}

internal fun MediaSessionController.observeBrowserApproval(
    streaming: StreamingState,
    pairedDeviceId: String?,
) {
    val current = state.value
    val pending = current.preview
    val remote = streaming.browser?.pendingConfirmation
    val target = streaming.target
    val matching = remote?.takeIf { it.matchesTarget(target, pairedDeviceId) }
    if (current.busy || current.destination != MediaDestination.Remote) return
    if (pending?.browserContext != null && pending.confirmationId != matching?.confirmationId) {
        mutable.value = current.copy(preview = null)
    }
    val hasOtherSheet = state.value.preview != null || current.queueOpen || current.resume != null
    if (!hasOtherSheet && matching != null && matching.confirmationId != retiredBrowserPreview) {
        mutable.value = state.value.copy(preview = matching)
    }
}

private fun ConfirmationPreview.matchesTarget(
    target: BrowserTarget?,
    pairedDeviceId: String?,
): Boolean =
    target != null && ownerDeviceId == pairedDeviceId && confirmMethod == ConfirmMethod.BROWSER_OPEN &&
        expected.serverEpoch == target.epoch && browserContext?.browserSessionId == target.session &&
        browserContext.targetGeneration == target.generation

internal fun MediaSessionController.retireFailedBrowserPreview(reply: McReply.Failure) {
    val previous = state.value.preview
    val resolved = reply.error.code in setOf(WireErrorCode.NOT_FOUND, WireErrorCode.CONFIRMATION_EXPIRED)
    if (previous?.confirmMethod == ConfirmMethod.BROWSER_OPEN && (resolved || reply.error.replacementPreview != null)) {
        retiredBrowserPreview = previous.confirmationId
    }
}

internal fun MediaSessionController.receiveBrowserResult(result: RemoteResult): Boolean {
    when (result) {
        is RemoteResult.BrowserOpen -> {
            if (state.value.preview?.confirmMethod == ConfirmMethod.BROWSER_OPEN) {
                retiredBrowserPreview = state.value.preview?.confirmationId
                mutable.value = state.value.copy(preview = null)
            }
            receivePreparation(result.value)
        }

        is RemoteResult.ActivityShowMenu -> {
            mutable.value = state.value.copy(showingMenus = true)
        }

        is RemoteResult.ActivityShowPlayback -> {
            mutable.value = state.value.copy(showingMenus = false)
        }

        else -> {
            return false
        }
    }
    return true
}

internal fun MediaSessionState.browserInputVisible() =
    snapshot?.activity?.mode == ActivityMode.BROWSER &&
        destination == MediaDestination.Remote && !showingMenus && !busy && preview == null && !queueOpen &&
        resume == null
