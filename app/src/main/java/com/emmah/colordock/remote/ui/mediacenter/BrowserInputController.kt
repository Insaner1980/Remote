package com.emmah.colordock.remote.ui.mediacenter

import android.os.SystemClock
import com.emmah.colordock.remote.mediacenter.protocol.BrowserBackParams
import com.emmah.colordock.remote.mediacenter.protocol.BrowserCapabilities
import com.emmah.colordock.remote.mediacenter.protocol.BrowserClickParams
import com.emmah.colordock.remote.mediacenter.protocol.BrowserCloseParams
import com.emmah.colordock.remote.mediacenter.protocol.BrowserKey
import com.emmah.colordock.remote.mediacenter.protocol.BrowserKeyParams
import com.emmah.colordock.remote.mediacenter.protocol.BrowserPointerParams
import com.emmah.colordock.remote.mediacenter.protocol.BrowserReloadParams
import com.emmah.colordock.remote.mediacenter.protocol.BrowserScrollParams
import com.emmah.colordock.remote.mediacenter.protocol.BrowserTextParams
import com.emmah.colordock.remote.mediacenter.protocol.Expected
import com.emmah.colordock.remote.mediacenter.protocol.McReply
import com.emmah.colordock.remote.mediacenter.protocol.McRequest
import com.emmah.colordock.remote.mediacenter.protocol.MethodName
import com.emmah.colordock.remote.mediacenter.protocol.PointerButton
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

private const val MOTION_INTERVAL_MS = 34L
private const val MOTION_ACK_TIMEOUT_MS = 5000L
private const val MAX_SCROLL_DELTA = 4096.0
internal const val MAX_BROWSER_TEXT_LENGTH = 4096

internal data class BrowserTarget(
    val epoch: String,
    val session: String,
    val generation: String,
)

internal data class BrowserPosition(
    val x: Double,
    val y: Double,
)

internal fun normalizedBrowserPosition(
    x: Float,
    y: Float,
    width: Int,
    height: Int,
): BrowserPosition? =
    if (minOf(width, height) > 0 && x.isFinite() && y.isFinite()) {
        BrowserPosition((x.toDouble() / width).coerceIn(0.0, 1.0), (y.toDouble() / height).coerceIn(0.0, 1.0))
    } else {
        null
    }

/** Holds one coalesced motion sample. Clicks and committed text are never queued. */
internal class BrowserInputController(
    private val scope: CoroutineScope,
    private val send: (McRequest) -> Boolean,
    private val now: () -> Long = SystemClock::elapsedRealtime,
    private val sequenceClock: () -> Long = SystemClock::elapsedRealtimeNanos,
    private val inputVisible: (BrowserTarget) -> Boolean = { true },
) {
    private var target: BrowserTarget? = null
    private var capabilities: BrowserCapabilities? = null
    private var methods: List<MethodName> = emptyList()
    private var motion: Motion? = null
    private var flush: Job? = null
    private var lastMotion: Long? = null
    private var awaitingMotion: Pair<String, Long>? = null
    private var lastSequence = 0L
    private var latestAction: String? = null
    private val mutableNotice = MutableStateFlow("")
    val notice = mutableNotice.asStateFlow()

    private sealed interface Motion {
        data class Point(
            val position: BrowserPosition,
        ) : Motion

        data class Scroll(
            val x: Double,
            val y: Double,
        ) : Motion
    }

    fun observe(
        target: BrowserTarget?,
        capabilities: BrowserCapabilities?,
        methods: List<MethodName>,
    ) {
        if (this.target != target || this.capabilities != capabilities || this.methods != methods) {
            endGesture()
            awaitingMotion = null
            latestAction = null
            mutableNotice.value = ""
        }
        this.target = target
        this.capabilities = capabilities
        this.methods = methods
    }

    fun endGesture() {
        flush?.cancel()
        flush = null
        motion = null
    }

    fun point(
        target: BrowserTarget,
        position: BrowserPosition,
    ) {
        if (supports(target, MethodName.BROWSER_POINTER)) {
            queueMotion(target, Motion.Point(position))
        }
    }

    fun scroll(
        target: BrowserTarget,
        x: Double,
        y: Double,
    ) {
        val usableDelta = x.isFinite() && y.isFinite() && (x != 0.0 || y != 0.0)
        if (!usableDelta || !supports(target, MethodName.BROWSER_SCROLL)) {
            return
        }
        val previous = motion as? Motion.Scroll
        queueMotion(
            target,
            Motion.Scroll(
                (x + (previous?.x ?: 0.0)).coerceIn(-MAX_SCROLL_DELTA, MAX_SCROLL_DELTA),
                (y + (previous?.y ?: 0.0)).coerceIn(-MAX_SCROLL_DELTA, MAX_SCROLL_DELTA),
            ),
        )
    }

    private fun queueMotion(
        target: BrowserTarget,
        sample: Motion,
    ) {
        motion = sample
        val waiting = awaitingMotion?.let { now() - it.second < MOTION_ACK_TIMEOUT_MS } == true
        if (waiting || flush != null) return
        val wait = lastMotion?.let { MOTION_INTERVAL_MS - (now() - it) } ?: 0
        if (wait <= 0) {
            flushMotion(target)
        } else {
            flush =
                scope.launch {
                    delay(wait)
                    flush = null
                    flushMotion(target)
                }
        }
    }

    private fun flushMotion(target: BrowserTarget) {
        val next = motion ?: return
        motion = null
        val command =
            when (next) {
                is Motion.Point -> {
                    RemoteCommand.BrowserPointer(
                        BrowserPointerParams(
                            target.session,
                            target.generation,
                            next.position.x,
                            next.position.y,
                            sequence(),
                        ),
                    )
                }

                is Motion.Scroll -> {
                    RemoteCommand.BrowserScroll(
                        BrowserScrollParams(target.session, target.generation, next.x, next.y, sequence()),
                    )
                }
            }
        lastMotion = now()
        submit(target, command, motion = true)
    }

    private fun sequence(): String {
        lastSequence = maxOf(lastSequence + 1, sequenceClock())
        return lastSequence.toString()
    }

    internal fun supports(
        target: BrowserTarget,
        method: MethodName,
    ): Boolean =
        this.target == target && method in methods && inputVisible(target) &&
            when (method) {
                MethodName.BROWSER_POINTER, MethodName.BROWSER_CLICK, MethodName.BROWSER_SCROLL -> {
                    capabilities?.pointer == true
                }

                MethodName.BROWSER_KEY -> {
                    capabilities?.keyboard == true
                }

                MethodName.BROWSER_TEXT -> {
                    capabilities?.text == true
                }

                MethodName.BROWSER_BACK, MethodName.BROWSER_RELOAD -> {
                    capabilities?.let { it.pointer || it.keyboard || it.text } == true
                }

                MethodName.BROWSER_CLOSE -> {
                    true
                }

                else -> {
                    false
                }
            }

    internal fun submit(
        target: BrowserTarget,
        command: RemoteCommand,
        motion: Boolean = false,
    ): Boolean {
        if (!supports(target, command.method)) return false
        val id = UUID.randomUUID().toString()
        val submitted = send(McRequest(id, command, Expected(serverEpoch = target.epoch)))
        if (motion) {
            awaitingMotion = (id to now()).takeIf { submitted }
        } else {
            latestAction = id.takeIf { submitted }
            mutableNotice.value = if (submitted) "" else "Browser input is unavailable. Check the computer."
        }
        return submitted
    }

    fun receive(reply: McReply) {
        if (reply.id == awaitingMotion?.first) {
            awaitingMotion = null
            val latest = motion
            val destination = target
            if (latest != null && destination != null) queueMotion(destination, latest)
        }
        if (reply.id != latestAction) return
        latestAction = null
        if (reply is McReply.Failure) mutableNotice.value = "Browser input failed. Check the computer."
    }
}

internal fun BrowserInputController.click(
    target: BrowserTarget,
    position: BrowserPosition,
    button: PointerButton = PointerButton.LEFT,
): Boolean =
    submit(
        target,
        RemoteCommand.BrowserClick(
            BrowserClickParams(target.session, target.generation, position.x, position.y, button),
        ),
    )

internal fun BrowserInputController.key(
    target: BrowserTarget,
    key: BrowserKey,
): Boolean = submit(target, RemoteCommand.BrowserKey(BrowserKeyParams(target.session, target.generation, key)))

internal fun BrowserInputController.text(
    target: BrowserTarget,
    text: String,
): Boolean =
    text.codePointCount(0, text.length) in 1..MAX_BROWSER_TEXT_LENGTH &&
        submit(target, RemoteCommand.BrowserText(BrowserTextParams(target.session, target.generation, text)))

internal fun BrowserInputController.back(target: BrowserTarget) =
    submit(target, RemoteCommand.BrowserBack(BrowserBackParams(target.session, target.generation)))

internal fun BrowserInputController.reload(target: BrowserTarget) =
    submit(target, RemoteCommand.BrowserReload(BrowserReloadParams(target.session, target.generation)))

internal fun BrowserInputController.close(target: BrowserTarget) =
    submit(target, RemoteCommand.BrowserClose(BrowserCloseParams(target.session, target.generation)))
