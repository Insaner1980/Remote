package com.emmah.colordock.remote.tv

import com.emmah.colordock.remote.companion.DockTransport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.IOException

private const val DOCK_APP_ID = "com.emmah.colordock"
private const val DOCK_JOIN_TIMEOUT_MILLIS = 5_000L

internal class LgDockChannel(
    private val connection: Connection,
) : DockTransport {
    interface Connection {
        val lock: Any
        val token: Any?
        val ready: Boolean
        val state: StateFlow<TvState>

        fun requirePermission()

        fun send(text: String): Boolean

        fun subscribe(
            endpoint: String,
            params: JSONObject,
            callback: (JSONObject?) -> Unit,
        ): String

        fun unsubscribe(
            id: String,
            origin: Any?,
        )
    }

    override val state: StateFlow<TvState> get() = connection.state

    private val incomingDock = MutableSharedFlow<JSONObject>(extraBufferCapacity = 8)
    override val dockMessages = incomingDock.asSharedFlow()
    private var dockPeer: String? = null
    private val mutableDockAvailable = MutableStateFlow(false)
    override val dockAvailable = mutableDockAvailable.asStateFlow()
    private var dockSubscription: String? = null

    fun receive(message: JSONObject) {
        if (dockPeer != null && message.optString("from") == dockPeer) {
            message.optJSONObject("payload")?.let { incomingDock.tryEmit(it) }
        }
    }

    fun unavailable() {
        mutableDockAvailable.value = false
    }

    fun reset() {
        dockPeer = null
        dockSubscription = null
        unavailable()
    }

    override suspend fun joinDockChannel() {
        val origin = connection.token ?: throw IOException("TV unavailable")
        dockSubscription?.let { connection.unsubscribe(it, connection.token) }
        val joined = CompletableDeferred<Unit>()
        val id =
            connection.subscribe("webapp/connectToApp", JSONObject().put("appId", DOCK_APP_ID)) { reply ->
                if (reply?.optString("state") == "CONNECTED" && reply.optString("appId") == DOCK_APP_ID) {
                    dockPeer = DOCK_APP_ID
                    mutableDockAvailable.value = true
                    joined.complete(Unit)
                } else {
                    dockPeer = null
                    mutableDockAvailable.value = false
                    if (reply == null) joined.completeExceptionally(IOException("Open Color Dock to continue."))
                }
            }
        dockSubscription = id
        var connected = false
        try {
            withTimeoutOrNull(DOCK_JOIN_TIMEOUT_MILLIS) {
                joined.await()
            } ?: throw IOException("Open Color Dock to continue.")
            connected = true
        } finally {
            if (!connected) {
                synchronized(connection.lock) {
                    connection.unsubscribe(id, origin)
                    if (connection.token === origin &&
                        dockSubscription == id
                    ) {
                        dockSubscription = null
                    }
                }
            }
        }
    }

    override val connectionToken: Any? get() = connection.token

    override fun sendDock(
        payload: JSONObject,
        origin: Any?,
    ) = synchronized(connection.lock) {
        if (connection.token !== origin) throw IOException("The TV connection changed.")
        sendDock(payload)
    }

    override fun sendDock(payload: JSONObject) =
        synchronized(connection.lock) {
            connection.requirePermission()
            val peer = dockPeer ?: throw IOException("Open Color Dock to continue.")
            if (!connection.ready ||
                connection.send(
                    JSONObject()
                        .put("type", "p2p")
                        .put("to", peer)
                        .put("payload", payload)
                        .toString(),
                ) != true
            ) {
                throw IOException("Color Dock is unavailable.")
            }
        }
}
