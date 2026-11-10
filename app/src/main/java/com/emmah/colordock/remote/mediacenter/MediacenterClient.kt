package com.emmah.colordock.remote.mediacenter

import com.emmah.colordock.remote.mediacenter.protocol.McRequest
import com.emmah.colordock.remote.mediacenter.protocol.RemoteJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

/** Connected describes transport admission; Task8 owns snapshot readiness and mutation gating. */
enum class MediacenterConnectionState {
    Disconnected,
    Connecting,
    Connected,
    PermissionRequired,
    PairingRequired,
    Failed,
    ResyncRequired,
}

interface MediacenterControl {
    val state: StateFlow<MediacenterConnectionState>
    val messages: Flow<String>

    fun connect(
        value: StoredMediacenter,
        network: MediacenterNetwork,
    )

    fun send(request: McRequest): Boolean

    fun disconnect()
}

/** One MC socket and one selected network, independent of every television session. */
class MediacenterClient : MediacenterControl {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutable = MutableStateFlow(MediacenterConnectionState.Disconnected)
    override val state = mutable.asStateFlow()
    private val incoming = Channel<String>(EVENT_CAPACITY)
    override val messages = incoming.receiveAsFlow()
    private val lock = Any()
    private var generation = 0L
    private var transport: MediacenterTransport? = null
    private var socket: WebSocket? = null
    private var watcher: AutoCloseable? = null
    private var helloDeadline: Job? = null

    override fun connect(
        value: StoredMediacenter,
        network: MediacenterNetwork,
    ) {
        synchronized(lock) {
            closeOwned(MediacenterConnectionState.Disconnected)
            if (!network.available()) {
                mutable.value = MediacenterConnectionState.PermissionRequired
                return
            }
            mutable.value = MediacenterConnectionState.Connecting
            val owner = generation
            try {
                val connection = MediacenterTransport(value.endpoint, network, value.paired.certificateFingerprint)
                val watch =
                    network.watch {
                        terminate(
                            owner,
                            if (network.available()) {
                                MediacenterConnectionState.Failed
                            } else {
                                MediacenterConnectionState.PermissionRequired
                            },
                        )
                    }
                if (owner != generation || !network.available()) {
                    watch.close()
                    connection.close()
                    mutable.value = MediacenterConnectionState.PermissionRequired
                    return
                }
                transport = connection
                watcher = watch
                helloDeadline =
                    scope.launch {
                        delay(HELLO_DEADLINE_MS)
                        terminate(owner, MediacenterConnectionState.Failed)
                    }
                socket = connection.openSocket(value.paired.credential, listener(owner, value.paired.serverDeviceId))
            } catch (_: Exception) {
                closeOwned(MediacenterConnectionState.Failed)
            }
        }
    }

    private fun listener(
        owner: Long,
        serverId: String,
    ): WebSocketListener =
        object : WebSocketListener() {
            private var helloReceived = false

            override fun onMessage(
                webSocket: WebSocket,
                text: String,
            ) {
                synchronized(lock) {
                    if (owner != generation) return
                    if (text.toByteArray(Charsets.UTF_8).size > MAX_MESSAGE_BYTES) {
                        closeOwned(MediacenterConnectionState.ResyncRequired)
                    } else if (!helloReceived) {
                        val hello = runCatching { RemoteJson.parseHello(text) }.getOrNull()
                        if (hello?.deviceId != serverId) {
                            closeOwned(MediacenterConnectionState.Failed)
                        } else {
                            helloReceived = true
                            helloDeadline?.cancel()
                            helloDeadline = null
                            mutable.value = MediacenterConnectionState.Connected
                            enqueue(text)
                        }
                    } else {
                        enqueue(text)
                    }
                }
            }

            private fun enqueue(text: String) {
                if (incoming.trySend(text).isFailure) closeOwned(MediacenterConnectionState.ResyncRequired)
            }

            override fun onMessage(
                webSocket: WebSocket,
                bytes: ByteString,
            ) {
                terminate(owner, MediacenterConnectionState.Failed)
            }

            override fun onFailure(
                webSocket: WebSocket,
                t: Throwable,
                response: Response?,
            ) {
                terminate(
                    owner,
                    if (response?.code ==
                        UNAUTHORIZED
                    ) {
                        MediacenterConnectionState.PairingRequired
                    } else {
                        MediacenterConnectionState.Failed
                    },
                )
            }

            override fun onClosing(
                webSocket: WebSocket,
                code: Int,
                reason: String,
            ) {
                terminate(
                    owner,
                    if (reason ==
                        "unauthorized"
                    ) {
                        MediacenterConnectionState.PairingRequired
                    } else {
                        MediacenterConnectionState.Disconnected
                    },
                )
            }

            override fun onClosed(
                webSocket: WebSocket,
                code: Int,
                reason: String,
            ) {
                terminate(owner, MediacenterConnectionState.Disconnected)
            }
        }

    override fun send(request: McRequest): Boolean =
        synchronized(lock) {
            if (mutable.value != MediacenterConnectionState.Connected) return@synchronized false
            val message = RemoteJson.encodeRequest(request)
            socket?.send(message) == true
        }

    override fun disconnect() {
        synchronized(lock) { closeOwned(MediacenterConnectionState.Disconnected) }
    }

    private fun terminate(
        owner: Long,
        state: MediacenterConnectionState,
    ) {
        synchronized(lock) { if (owner == generation) closeOwned(state) }
    }

    private fun closeOwned(next: MediacenterConnectionState) {
        ++generation
        helloDeadline?.cancel()
        helloDeadline = null
        socket?.cancel()
        socket = null
        transport?.close()
        transport = null
        watcher?.let { runCatching { it.close() } }
        watcher = null
        while (incoming.tryReceive().isSuccess) { /* Discard previous-session state and transient input. */ }
        mutable.value = next
    }

    private companion object {
        const val EVENT_CAPACITY = 256
        const val MAX_MESSAGE_BYTES = 262144
        const val HELLO_DEADLINE_MS = 5000L
        const val UNAUTHORIZED = 401
    }
}
