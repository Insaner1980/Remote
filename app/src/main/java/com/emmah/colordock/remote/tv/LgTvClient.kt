package com.emmah.colordock.remote.tv

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.emmah.colordock.remote.companion.DockTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import org.json.JSONObject
import java.io.IOException

private const val PAIRING_TIMEOUT_MILLIS = 60_000L
private const val TV_REPLY_TIMEOUT_MILLIS = 5_000L

enum class TvRead(
    val endpoint: String,
) {
    Volume("audio/getVolume"),
    Channels("tv/getChannelList"),
    CurrentChannel("tv/getCurrentChannel"),
    PointerSocket("com.webos.service.networkinput/getPointerInputSocket"),
}

// This facade implements both public TV control and companion transport contracts.
@Suppress("TooManyFunctions")
class LgTvClient internal constructor(
    context: Context,
    private val webSocketFactory: WebSocket.Factory?,
    private val callFactory: Call.Factory?,
) : TvControl,
    DockTransport {
    constructor(context: Context) : this(context, null, null)

    private val application = context.applicationContext
    private val mutableState = MutableStateFlow(TvState())
    override val state = mutableState.asStateFlow()
    private var socket: WebSocket? = null
    private var http: OkHttpClient? = null

    @Volatile private var session: LgSession? = null
    private var registration: CompletableDeferred<Unit>? = null
    private val credentials = TvCredentialStore(application)
    private val commandLock = Mutex()
    private var pointer: PointerInputClient? = null
    private var target: TvTarget? = null
    private val catalog =
        TvCatalog(
            object : TvCatalog.Connection {
                override val lock: Any get() = this@LgTvClient
                override val session: LgSession? get() = this@LgTvClient.session
                override val commandLock: Mutex get() = this@LgTvClient.commandLock
                override val state: TvConnectionState get() = this@LgTvClient.state.value.connection

                override suspend fun request(
                    endpoint: String,
                    params: JSONObject,
                    origin: LgSession?,
                    beforeSend: () -> Unit,
                ): JSONObject = this@LgTvClient.request(endpoint, params, origin, beforeSend)

                override suspend fun action(block: suspend () -> Unit): TvResult<Unit> = this@LgTvClient.action(block)

                override fun permissionRequired(): TvResult.Failure = this@LgTvClient.permissionRequired()
            },
        )
    private val dockChannel =
        LgDockChannel(
            object : LgDockChannel.Connection {
                override val lock: Any get() = this@LgTvClient
                override val token: Any? get() = session
                override val ready: Boolean get() = state.value.connection == TvConnectionState.Ready
                override val state get() = this@LgTvClient.state

                override fun requirePermission() = this@LgTvClient.requirePermission()

                override fun send(text: String): Boolean = socket?.send(text) == true

                override fun subscribe(
                    endpoint: String,
                    params: JSONObject,
                    callback: (JSONObject?) -> Unit,
                ): String = this@LgTvClient.subscribe(endpoint, params, callback)

                override fun unsubscribe(
                    id: String,
                    origin: Any?,
                ) = this@LgTvClient.unsubscribe(id, origin as? LgSession)
            },
        )
    override val dockMessages = dockChannel.dockMessages
    override val dockAvailable = dockChannel.dockAvailable
    internal var identity: String? = null
        private set

    override suspend fun connect(target: TvTarget): TvResult<Unit> {
        disconnect()
        val network =
            connectionNetwork() ?: return TvResult.Failure(
                if (state.value.connection == TvConnectionState.PermissionRequired) {
                    TvErrorCode.PermissionRequired
                } else {
                    TvErrorCode.Unavailable
                },
                state.value.message ?: "Allow local network access to control your TV.",
            )
        return connectSaved(target, network)
    }

    private suspend fun connectionNetwork(): android.net.Network? {
        if (application.checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            mutableState.value = TvState(TvConnectionState.PermissionRequired)
            return null
        }
        val connectivity = application.getSystemService(ConnectivityManager::class.java)
        val network = lanNetwork(application)
        val capabilities = network?.let(connectivity::getNetworkCapabilities)
        val localTransport =
            capabilities?.let {
                it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    it.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            } == true
        return if (network == null || !localTransport) {
            mutableState.value = TvState(TvConnectionState.Failed, "Connect to the same Wi-Fi network as your TV.")
            null
        } else {
            network
        }
    }

    private suspend fun connectSaved(
        target: TvTarget,
        network: android.net.Network,
    ): TvResult<Unit> {
        val saved =
            try {
                credentials.load()
            } catch (_: IOException) {
                mutableState.value =
                    TvState(
                        TvConnectionState.Failed,
                        "Pair with this TV again. Forget the saved TV to start a new pairing.",
                        error = TvErrorCode.PairingRequired,
                    )
                return TvResult.Failure(TvErrorCode.PairingRequired, requireNotNull(mutableState.value.message))
            }
        return openConnection(target, network, saved)
    }

    private suspend fun openConnection(
        target: TvTarget,
        network: android.net.Network,
        saved: TvCredentials?,
    ): TvResult<Unit> {
        val protocol = LgSession()
        val registered = CompletableDeferred<Unit>()
        val listener = LgConnectionListener(application, saved, connectionEvents(protocol, registered))
        val trust = listener.trust
        val client = tvHttpClient(network, target, trust)
        synchronized(this) {
            session = protocol
            this.target = target
            registration = registered
            mutableState.value = TvState(TvConnectionState.Connecting)
            http = client
            socket =
                (webSocketFactory ?: client).newWebSocket(
                    Request.Builder().url(target.gatewayUrl).build(),
                    listener,
                )
        }
        return finishConnection(target, listener, protocol, registered)
    }

    private suspend fun finishConnection(
        target: TvTarget,
        listener: LgConnectionListener,
        protocol: LgSession,
        registered: CompletableDeferred<Unit>,
    ): TvResult<Unit> {
        var saveFailure: TvResult.Failure? = null
        try {
            withTimeout(PAIRING_TIMEOUT_MILLIS) { registered.await() }
            if (session !== protocol || protocol.state != TvConnectionState.Ready) return unavailable()
            saveFailure = savePairing(target, listener, protocol, registered)
            if (saveFailure == null) publishReady(listener, protocol)
        } catch (_: TimeoutCancellationException) {
            if (session === protocol) {
                disconnect()
                mutableState.value =
                    TvState(TvConnectionState.Failed, "Connection timed out. Check your TV and try again.")
            }
        } catch (cancelled: CancellationException) {
            if (session === protocol) disconnect()
            throw cancelled
        } catch (_: Exception) {
            if (session === protocol && mutableState.value.connection != TvConnectionState.Failed) {
                failed(protocol, registered, "The pairing could not be saved. Pair with this TV again.")
            }
        }
        return saveFailure ?: connectionResult(protocol)
    }

    private fun connectionResult(protocol: LgSession): TvResult<Unit> =
        if (session !== protocol) {
            unavailable()
        } else if (state.value.connection == TvConnectionState.Ready) {
            TvResult.Success(Unit)
        } else {
            TvResult.Failure(state.value.error ?: TvErrorCode.Unavailable, state.value.message ?: "TV unavailable")
        }

    private fun publishReady(
        listener: LgConnectionListener,
        protocol: LgSession,
    ) {
        synchronized(this) {
            if (session === protocol && protocol.state == TvConnectionState.Ready) {
                identity = listener.deviceId
                mutableState.value =
                    TvState(TvConnectionState.Ready, name = listener.deviceName, guideAvailable = true)
                subscribe("audio/getMute") { value ->
                    mutableState.value = state.value.copy(muted = value?.opt("mute") as? Boolean)
                }
                subscribe(
                    "tv/getCurrentChannel",
                ) { value ->
                    mutableState.value = state.value.copy(channel = value?.let(::parseChannel))
                }
            }
        }
    }

    private suspend fun savePairing(
        target: TvTarget,
        listener: LgConnectionListener,
        protocol: LgSession,
        registered: CompletableDeferred<Unit>,
    ): TvResult.Failure? {
        try {
            credentials.save(
                TvCredentials(
                    target.address,
                    requireNotNull(listener.deviceId),
                    listener.deviceName,
                    requireNotNull(listener.trust.fingerprint),
                    requireNotNull(protocol.registeredKey),
                ),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (session ===
                protocol
            ) {
                failed(
                    protocol,
                    registered,
                    "The pairing could not be saved. Check storage and try again.",
                    TvErrorCode.StorageUnavailable,
                )
            }
            return TvResult.Failure(
                TvErrorCode.StorageUnavailable,
                "The pairing could not be saved. Check storage and try again.",
            )
        }
        return null
    }

    private fun connectionEvents(
        protocol: LgSession,
        registered: CompletableDeferred<Unit>,
    ): LgConnectionListener.Events =
        object : LgConnectionListener.Events {
            override val lock: Any get() = this@LgTvClient
            override val protocol = protocol
            override val registered = registered

            override fun isCurrent(): Boolean =
                session === protocol && state.value.connection != TvConnectionState.Failed

            override fun pairing() {
                mutableState.value = TvState(TvConnectionState.Pairing)
            }

            override fun dockMessage(message: JSONObject) {
                dockChannel.receive(message)
            }

            override fun permissionRequired() {
                this@LgTvClient.permissionRequired()
            }

            override fun failed(
                message: String,
                code: TvErrorCode,
            ) {
                this@LgTvClient.failed(protocol, registered, message, code)
            }
        }

    suspend fun read(query: TvRead): JSONObject = request(query.endpoint)

    private suspend fun request(
        endpoint: String,
        params: JSONObject = JSONObject(),
        origin: LgSession? = session,
        beforeSend: () -> Unit = {
        },
    ): JSONObject {
        val protocol = origin ?: throw IOException("TV unavailable")
        val (id, reply) =
            synchronized(this) {
                requirePermission()
                requireSession(protocol)
                protocol.request()
            }
        try {
            val request =
                JSONObject()
                    .put(
                        "type",
                        "request",
                    ).put("id", id)
                    .put("uri", "ssap://$endpoint")
                    .put("payload", params)
            synchronized(this) {
                requirePermission()
                beforeSend()
                if (session !== protocol || protocol.state != TvConnectionState.Ready ||
                    socket?.send(request.toString()) != true
                ) {
                    throw IOException("TV unavailable")
                }
            }
            return awaitTvReply(reply)
        } finally {
            protocol.cancel(id)
        }
    }

    private fun requireSession(protocol: LgSession) {
        if (session !== protocol || protocol.state != TvConnectionState.Ready) throw IOException("TV unavailable")
    }

    @Synchronized private fun subscribe(
        endpoint: String,
        params: JSONObject = JSONObject(),
        callback: (JSONObject?) -> Unit,
    ): String {
        requirePermission()
        val protocol = session ?: throw IOException("TV unavailable")
        val id = protocol.subscribe(callback)
        if (socket?.send(
                JSONObject()
                    .put("type", "subscribe")
                    .put("id", id)
                    .put("uri", "ssap://$endpoint")
                    .put("payload", params)
                    .toString(),
            ) !=
            true
        ) {
            protocol.cancel(id)
            throw IOException("TV unavailable")
        }
        return id
    }

    @Synchronized private fun unsubscribe(
        id: String,
        origin: LgSession? = session,
    ) {
        origin?.cancel(id)
        if (session === origin) socket?.send(JSONObject().put("type", "unsubscribe").put("id", id).toString())
    }

    override val connectionToken: Any? get() = session

    override suspend fun joinDockChannel() = dockChannel.joinDockChannel()

    override fun sendDock(
        payload: JSONObject,
        origin: Any?,
    ) = dockChannel.sendDock(payload, origin)

    override fun sendDock(payload: JSONObject) = dockChannel.sendDock(payload)

    override suspend fun send(command: TvCommand): TvResult<Unit> {
        val origin = session
        return commandLock.withLock {
            if (origin == null || origin !== session ||
                state.value.connection != TvConnectionState.Ready
            ) {
                return@withLock unavailable()
            }
            action {
                if (PointerInputClient.frame(command) != null) {
                    sendPointer(command, origin)
                } else {
                    sendCommand(command, origin)
                }
            }
        }
    }

    private fun requireOrigin(origin: LgSession) {
        if (session !== origin) throw IOException("TV unavailable")
    }

    private suspend fun sendPointer(
        command: TvCommand,
        origin: LgSession,
    ) {
        if (pointer == null) {
            val pointerUrl = request(TvRead.PointerSocket.endpoint, origin = origin).getString("socketPath")
            val input =
                synchronized(this) {
                    requireOrigin(origin)
                    PointerInputClient(requireNotNull(http), requireNotNull(target)).also {
                        pointer = it
                    }
                }
            var connected = false
            try {
                input.connect(pointerUrl)
                synchronized(this) {
                    if (session !== origin || pointer !== input) throw IOException("TV unavailable")
                }
                connected = true
            } finally {
                if (!connected) {
                    synchronized(this) { if (pointer === input) pointer = null }
                    input.close()
                }
            }
        }
        synchronized(this) {
            requireOrigin(origin)
            requirePermission()
            var sent = false
            try {
                requireNotNull(pointer).send(command)
                sent = true
            } finally {
                if (!sent) {
                    pointer?.close()
                    pointer = null
                }
            }
        }
    }

    private suspend fun sendCommand(
        command: TvCommand,
        origin: LgSession,
    ) {
        when (command) {
            TvCommand.VolumeUp -> {
                request("audio/volumeUp", origin = origin)
            }

            TvCommand.VolumeDown -> {
                request("audio/volumeDown", origin = origin)
            }

            TvCommand.Mute, TvCommand.Unmute -> {
                request(
                    "audio/setMute",
                    JSONObject().put("mute", command == TvCommand.Mute),
                    origin = origin,
                )
            }

            TvCommand.ChannelUp -> {
                request("tv/channelUp", origin = origin)
            }

            TvCommand.ChannelDown -> {
                request("tv/channelDown", origin = origin)
            }

            TvCommand.LiveTv -> {
                request(
                    "system.launcher/launch",
                    JSONObject().put("id", "com.webos.app.livetv"),
                    origin = origin,
                )
            }

            TvCommand.PowerOff -> {
                request("system/turnOff", origin = origin)
                synchronized(this) {
                    if (session ===
                        origin
                    ) {
                        disconnect()
                    }
                }
            }

            else -> {
                throw IOException("This TV control is unavailable.")
            }
        }
    }

    override suspend fun channels(): TvResult<List<TvChannel>> = catalog.channels()

    override suspend fun tune(channelId: String): TvResult<Unit> = catalog.tune(channelId)

    override suspend fun apps(): TvResult<List<TvApp>> = catalog.apps()

    override suspend fun launchApp(appId: String): TvResult<Unit> = catalog.launchApp(appId)

    internal suspend fun appIcon(url: String): TvResult<ByteArray> {
        val origin = session ?: return unavailable()
        return try {
            val call =
                synchronized(this) {
                    requirePermission()
                    if (session !== origin || state.value.connection != TvConnectionState.Ready ||
                        target?.allowsAppIcon(url) != true
                    ) {
                        null
                    } else {
                        (callFactory ?: http)?.newCall(Request.Builder().url(url).build())
                    }
                }
            if (call == null) {
                unavailable()
            } else {
                val bytes = loadAppIcon(call)
                synchronized(this) {
                    if (session !== origin ||
                        state.value.connection != TvConnectionState.Ready
                    ) {
                        unavailable()
                    } else {
                        TvResult.Success(bytes)
                    }
                }
            }
        } catch (
            cancelled: CancellationException,
        ) {
            throw cancelled
        } catch (
            _: SecurityException,
        ) {
            permissionRequired()
        } catch (_: Exception) {
            TvResult.Failure(TvErrorCode.Unavailable, "TV app artwork is unavailable.")
        }
    }

    override suspend fun launchColorDock(): TvResult<Unit> =
        action {
            request("system.launcher/launch", JSONObject().put("id", "com.emmah.colordock"))
        }

    suspend fun refreshStatus() {
        val origin = session ?: return
        try {
            val audio = request("audio/getMute")
            val muted = audio.opt("mute") as? Boolean
            updateStatus(origin) {
                it.copy(muted = muted)
            }
        } catch (
            cancelled: CancellationException,
        ) {
            throw cancelled
        } catch (_: Exception) {
            updateStatus(origin) {
                it.copy(muted = null)
            }
        }
        if (session !== origin) return
        try {
            val channel = parseChannel(read(TvRead.CurrentChannel))
            updateStatus(origin) {
                it.copy(channel = channel)
            }
        } catch (
            cancelled: CancellationException,
        ) {
            throw cancelled
        } catch (_: Exception) {
            updateStatus(origin) {
                it.copy(channel = null)
            }
        }
    }

    @Synchronized private fun updateStatus(
        origin: LgSession,
        update: (TvState) -> TvState,
    ) {
        if (session === origin &&
            state.value.connection == TvConnectionState.Ready
        ) {
            mutableState.value = update(state.value)
        }
    }

    private suspend fun action(block: suspend () -> Unit): TvResult<Unit> =
        try {
            requirePermission()
            if (state.value.connection != TvConnectionState.Ready) {
                unavailable()
            } else {
                block()
                TvResult.Success(Unit)
            }
        } catch (
            cancelled: CancellationException,
        ) {
            throw cancelled
        } catch (
            _: SecurityException,
        ) {
            permissionRequired()
        } catch (
            _: Exception,
        ) {
            TvResult.Failure(
                TvErrorCode.Unavailable,
                "This TV control is unavailable. Check the connection and try again.",
            )
        }

    private fun requirePermission() {
        if (application.checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            val failure = permissionRequired()
            throw SecurityException(failure.message)
        }
    }

    private fun permissionRequired(): TvResult.Failure {
        disconnect()
        val message = "Allow local network access to control your TV."
        mutableState.value =
            TvState(TvConnectionState.PermissionRequired, message, error = TvErrorCode.PermissionRequired)
        return TvResult.Failure(TvErrorCode.PermissionRequired, message)
    }

    private fun unavailable() = TvResult.Failure(TvErrorCode.Unavailable, "TV unavailable. Reconnect to continue.")

    @Synchronized override fun disconnect() {
        pointer?.close()
        pointer = null
        session?.disconnect()
        session = null
        registration?.cancel()
        registration = null
        socket?.cancel()
        socket = null
        http?.dispatcher?.executorService?.shutdown()
        http?.connectionPool?.evictAll()
        http = null
        target = null
        catalog.reset()
        dockChannel.reset()
        identity = null
        mutableState.value = TvState()
    }

    internal fun reconnecting() {
        mutableState.value = state.value.copy(connection = TvConnectionState.Reconnecting)
    }

    @Synchronized private fun failed(
        protocol: LgSession,
        registered: CompletableDeferred<Unit>,
        message: String = "TV unavailable. Check your network and try again.",
        code: TvErrorCode = TvErrorCode.Unavailable,
    ) {
        if (session !== protocol || state.value.connection == TvConnectionState.Failed) return
        protocol.disconnect()
        catalog.invalidate()
        registered.completeExceptionally(IOException(message))
        mutableState.value = TvState(TvConnectionState.Failed, message, error = code)
        socket?.cancel()
        pointer?.close()
        pointer = null
        dockChannel.unavailable()
        http?.dispatcher?.executorService?.shutdown()
        http?.connectionPool?.evictAll()
    }
}

internal suspend fun awaitTvReply(reply: Deferred<JSONObject>): JSONObject =
    withTimeoutOrNull(TV_REPLY_TIMEOUT_MILLIS) {
        reply.await()
    } ?: throw IOException("The TV did not reply. Check the saved state before trying again.")
