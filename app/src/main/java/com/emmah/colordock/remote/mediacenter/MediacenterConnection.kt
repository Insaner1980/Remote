package com.emmah.colordock.remote.mediacenter

import com.emmah.colordock.remote.mediacenter.protocol.Empty
import com.emmah.colordock.remote.mediacenter.protocol.McReply
import com.emmah.colordock.remote.mediacenter.protocol.McRequest
import com.emmah.colordock.remote.mediacenter.protocol.MethodName
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand
import com.emmah.colordock.remote.mediacenter.protocol.RemoteJson
import com.emmah.colordock.remote.mediacenter.protocol.RemoteSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

/** Session owner. Every collector, retry, grace period and snapshot deadline belongs to a generation. */
internal class MediacenterConnection(
    private val control: MediacenterControl,
    private val scope: CoroutineScope,
    private val jitter: () -> Double,
    private val requests: MediacenterRequests,
    val lock: Any,
) {
    private val mutableState = MutableStateFlow(MediacenterConnectionState.Disconnected)
    val state = mutableState.asStateFlow()
    private val mutableSnapshot = MutableStateFlow<RemoteSnapshot?>(null)
    val snapshot = mutableSnapshot.asStateFlow()
    var target: StoredMediacenter? = null
        private set
    private var network: MediacenterNetwork? = null
    var reducer: RemoteStateReducer? = null
        private set
    var inForeground = true
        private set
    private var generation = 0L
    private var attempt = 0
    private var collector: Job? = null
    private var status: Job? = null
    private var retry: Job? = null
    private var grace: Job? = null
    private var deadline: Job? = null
    private var refreshId: String? = null

    fun connect(
        value: StoredMediacenter,
        route: MediacenterNetwork,
    ) = synchronized(lock) {
        target = value
        network = route
        attempt = 0
        open()
    }

    private fun open() {
        val value = target ?: return
        val route = network ?: return
        retire()
        val owner = generation
        reducer = RemoteStateReducer(value.paired.serverDeviceId)
        mutableState.value = MediacenterConnectionState.Connecting
        collector =
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                control.messages.collect { message -> synchronized(lock) { if (owner == generation) receive(message) } }
            }
        control.connect(value, route)
        status =
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                control.state.collect { next -> synchronized(lock) { if (owner == generation) transport(next) } }
            }
    }

    private fun receive(message: String) {
        runCatching {
            val root = JSONObject(message)
            val inbound =
                if (root.has("id")) {
                    if (root.getString("id") != refreshId) {
                        requests.receiveReply(message)
                        return
                    }
                    require(RemoteJson.parseReply(MethodName.STATE_GET, message) is McReply.Success)
                    refreshId = null
                    root.getJSONObject("result").toString()
                } else {
                    if (reducer?.hello == null && root.optString("type") != "hello") return
                    message
                }
            val current = requireNotNull(reducer)
            if (root.optString("type") == "hello" && current.hello != null) {
                requests.invalidate()
                transport(MediacenterConnectionState.Connected)
            }
            when (current.apply(inbound)) {
                StateReduction.Applied -> {
                    val native = current.snapshot
                    mutableSnapshot.value = native.takeIf { inForeground }
                    if (native != null && inForeground) {
                        deadline?.cancel()
                        attempt = 0
                        retry?.cancel()
                        retry = null
                        mutableState.value = MediacenterConnectionState.Connected
                        requests.nativeReady(native)
                    }
                }

                StateReduction.IdentityChanged -> {
                    transport(MediacenterConnectionState.IdentityChanged)
                }

                StateReduction.VersionMismatch -> {
                    transport(MediacenterConnectionState.VersionMismatch)
                }

                StateReduction.ResyncRequired -> {
                    transport(MediacenterConnectionState.ResyncRequired)
                }
            }
        }.onFailure { transport(MediacenterConnectionState.ResyncRequired) }
    }

    private fun transport(next: MediacenterConnectionState) {
        when (next) {
            MediacenterConnectionState.Connected -> {
                mutableState.value = MediacenterConnectionState.Synchronizing
                val owner = generation
                deadline?.cancel()
                deadline =
                    scope.launch {
                        delay(SNAPSHOT_DEADLINE_MS)
                        synchronized(lock) {
                            if (owner ==
                                generation
                            ) {
                                transport(MediacenterConnectionState.ResyncRequired)
                            }
                        }
                    }
            }

            MediacenterConnectionState.Connecting -> {
                Unit
            }

            else -> {
                retire()
                mutableState.value = next
                if (next in STOP_STATES) {
                    retry?.cancel()
                    deadline?.cancel()
                } else {
                    recover()
                }
            }
        }
    }

    private fun recover() {
        if (!inForeground || target == null || retry?.isActive == true) return
        deadline?.cancel()
        if (attempt >= RETRY_DELAYS.size) {
            mutableState.value = MediacenterConnectionState.Failed
            return
        }
        val delayMs = RETRY_DELAYS[attempt++]
        val wait = (delayMs * (1.0 + (jitter().coerceIn(0.0, 1.0) - JITTER_MIDPOINT) * JITTER_RANGE)).toLong()
        val owner = generation
        mutableState.value = MediacenterConnectionState.Reconnecting
        retry =
            scope.launch {
                delay(wait)
                synchronized(lock) { if (owner == generation && inForeground) open() }
            }
    }

    fun foreground() =
        synchronized(lock) {
            if (inForeground) return@synchronized
            inForeground = true
            grace?.cancel()
            grace = null
            if (target == null || mutableState.value in STOP_STATES) return@synchronized
            if (control.state.value == MediacenterConnectionState.Connected) {
                reducer?.invalidate()
                mutableSnapshot.value = null
                transport(MediacenterConnectionState.Connected)
                refreshId = UUID.randomUUID().toString()
                if (!control.send(McRequest(requireNotNull(refreshId), RemoteCommand.StateGet(Empty)))) {
                    transport(MediacenterConnectionState.Failed)
                }
            } else {
                attempt = 0
                open()
            }
        }

    fun background() =
        synchronized(lock) {
            if (!inForeground) return@synchronized
            inForeground = false
            retry?.cancel()
            requests.invalidate()
            reducer?.invalidate()
            mutableSnapshot.value = null
            if (mutableState.value == MediacenterConnectionState.Connected) {
                mutableState.value = MediacenterConnectionState.Synchronizing
            }
            if (mutableState.value in STOP_STATES) return@synchronized
            val owner = generation
            grace =
                scope.launch {
                    delay(BACKGROUND_GRACE_MS)
                    synchronized(lock) {
                        if (owner == generation && !inForeground) {
                            retire()
                            mutableState.value = MediacenterConnectionState.Disconnected
                        }
                    }
                }
        }

    fun disconnect() =
        synchronized(lock) {
            target = null
            network = null
            retire()
            mutableState.value = MediacenterConnectionState.Disconnected
        }

    fun retry() =
        synchronized(lock) {
            if (target != null && inForeground && mutableState.value !in TRUST_STOP_STATES) {
                attempt = 0
                open()
            }
        }

    private fun retire() {
        ++generation
        collector?.cancel()
        status?.cancel()
        retry?.cancel()
        grace?.cancel()
        deadline?.cancel()
        collector = null
        status = null
        retry = null
        grace = null
        deadline = null
        refreshId = null
        reducer = null
        mutableSnapshot.value = null
        requests.invalidate()
        control.disconnect()
    }

    private companion object {
        val RETRY_DELAYS = listOf(1000L, 2000L, 5000L, 10000L, 10000L)
        const val JITTER_RANGE = 0.4
        const val JITTER_MIDPOINT = 0.5
        const val BACKGROUND_GRACE_MS = 5000L
        const val SNAPSHOT_DEADLINE_MS = 5000L
        val TRUST_STOP_STATES =
            setOf(
                MediacenterConnectionState.PairingRequired,
                MediacenterConnectionState.IdentityChanged,
                MediacenterConnectionState.VersionMismatch,
            )
        val STOP_STATES = TRUST_STOP_STATES + MediacenterConnectionState.PermissionRequired
    }
}
