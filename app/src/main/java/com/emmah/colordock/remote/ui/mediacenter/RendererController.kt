package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.emmah.colordock.remote.mediacenter.protocol.Direction
import com.emmah.colordock.remote.mediacenter.protocol.Expected
import com.emmah.colordock.remote.mediacenter.protocol.McReply
import com.emmah.colordock.remote.mediacenter.protocol.McRequest
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand
import com.emmah.colordock.remote.mediacenter.protocol.RemoteSnapshot
import com.emmah.colordock.remote.mediacenter.protocol.Route
import com.emmah.colordock.remote.mediacenter.protocol.UiActivateParams
import com.emmah.colordock.remote.mediacenter.protocol.UiBackParams
import com.emmah.colordock.remote.mediacenter.protocol.UiMoveFocusParams
import com.emmah.colordock.remote.mediacenter.protocol.UiSearchParams
import com.emmah.colordock.remote.mediacenter.protocol.UiTopic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

internal class RendererController(
    private val scope: CoroutineScope,
    private val send: (McRequest) -> Boolean,
) {
    private var snapshot: RemoteSnapshot? = null
    private var repeat: Job? = null
    private var text: Job? = null
    private var deadline: Job? = null
    private var pending: String? = null
    private var deferredText: Pair<List<Any?>, String>? = null
    private val mutableNotice = kotlinx.coroutines.flow.MutableStateFlow("")
    val notice = mutableNotice.asStateFlow()
    private var sequence = 0UL

    fun observe(value: RemoteSnapshot?) {
        if (rendererContext(value) != rendererContext(snapshot)) stop()
        if (value?.serverEpoch != snapshot?.serverEpoch) sequence = 0UL
        snapshot = value
    }

    fun stop() {
        deferredText = null
        repeat?.cancel()
        text?.cancel()
        deadline?.cancel()
        pending = null
    }

    fun release() {
        repeat?.cancel()
        repeat = null
    }

    fun press(direction: Direction) {
        release()
        move(direction)
        repeat =
            scope.launch {
                delay(MEDIA_REPEAT_DELAY_MS)
                while (true) {
                    move(direction)
                    delay(MEDIA_REPEAT_INTERVAL_MS)
                }
            }
    }

    fun committed(value: String) {
        text?.cancel()
        if (value.codePointCount(0, value.length) > MEDIA_QUERY_LIMIT) return
        val owner = rendererContext(snapshot)
        text =
            scope.launch {
                delay(MEDIA_SEARCH_DEBOUNCE_MS)
                if (owner == rendererContext(snapshot)) {
                    deferredText = owner to value
                    flushText()
                }
            }
    }

    fun composing() {
        text?.cancel()
        deferredText = null
    }

    private fun flushText() {
        val (owner, value) = deferredText ?: return
        if (pending != null) return
        deferredText = null
        val submitted =
            owner == rendererContext(snapshot) &&
                input { ui ->
                    ui.route?.takeIf { it in listOf(Route.VIDEOS, Route.MUSIC, Route.PHOTOS) }?.let { route ->
                        sequence++
                        RemoteCommand.UiSearch(
                            UiSearchParams(
                                ui.rendererGeneration!!,
                                ui.focusRevision!!,
                                ui.modalId,
                                route,
                                value,
                                sequence.toString(),
                            ),
                        )
                    }
                }
        if (!submitted) mutableNotice.value = "Search was not sent. Check connection and desktop search context."
    }

    fun receive(reply: McReply) {
        if (reply.id == pending) {
            deadline?.cancel()
            pending = null
            if (reply is McReply.Failure) {
                release()
                deferredText = null
                mutableNotice.value = reply.error.message
            } else {
                flushText()
            }
        }
    }

    internal fun input(create: (UiTopic) -> RemoteCommand?): Boolean {
        val current = snapshot ?: return false
        val ui = current.ui
        val hasRevision = ui.rendererGeneration != null && ui.focusRevision != null
        val command = if (ui.available && hasRevision && pending == null) create(ui) else null
        val request =
            command?.let {
                McRequest(
                    UUID.randomUUID().toString(),
                    it,
                    Expected(serverEpoch = current.serverEpoch),
                )
            }
        return if (request != null && send(request)) {
            trackInput(request)
            true
        } else {
            false
        }
    }

    private fun trackInput(request: McRequest) {
        pending = request.id
        deadline =
            scope.launch {
                delay(MEDIA_REQUEST_TIMEOUT_MS)
                if (pending ==
                    request.id
                ) {
                    pending = null
                    deferredText = null
                    mutableNotice.value =
                        "Input outcome is uncertain. Inspect the computer before sending again."
                    release()
                }
            }
    }
}

private fun rendererContext(value: RemoteSnapshot?) =
    listOf(
        value?.serverEpoch,
        value?.connection?.serverDeviceId,
        value?.ui?.rendererGeneration,
        value?.ui?.route,
        value?.ui?.modalId,
        value?.ui?.available,
    )

internal fun RendererController.move(direction: Direction) =
    input { ui ->
        RemoteCommand.UiMoveFocus(
            UiMoveFocusParams(ui.rendererGeneration!!, ui.focusRevision!!, ui.modalId, direction),
        )
    }

internal fun RendererController.activate() =
    input { ui ->
        ui.focusId?.let {
            RemoteCommand.UiActivate(UiActivateParams(ui.rendererGeneration!!, ui.focusRevision!!, ui.modalId, it))
        }
    }

internal fun RendererController.back() =
    input { ui -> RemoteCommand.UiBack(UiBackParams(ui.rendererGeneration!!, ui.focusRevision!!, ui.modalId)) }
