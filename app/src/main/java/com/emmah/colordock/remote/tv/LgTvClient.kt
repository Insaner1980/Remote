package com.emmah.colordock.remote.tv

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.emmah.colordock.remote.companion.DockTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.security.cert.CertificateException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.net.ssl.SSLContext

enum class TvRead(val endpoint: String) {
    Volume("audio/getVolume"),
    Channels("tv/getChannelList"),
    CurrentChannel("tv/getCurrentChannel"),
    PointerSocket("com.webos.service.networkinput/getPointerInputSocket"),
}

class LgTvClient(context: Context) : TvControl, DockTransport {
    private val application = context.applicationContext
    private val mutableState = MutableStateFlow(TvState())
    override val state = mutableState.asStateFlow()
    private var socket: WebSocket? = null
    private var http: OkHttpClient? = null
    private var session: LgSession? = null
    private var registration: CompletableDeferred<Unit>? = null
    private val credentials = TvCredentialStore(application)
    private val commandLock = Mutex()
    private var pointer: PointerInputClient? = null
    private var target: TvTarget? = null
    private var channelList = emptyList<TvChannel>()
    private val incomingDock = MutableSharedFlow<JSONObject>(extraBufferCapacity = 8)
    override val dockMessages = incomingDock.asSharedFlow()
    private var dockPeer: String? = null
    private val mutableDockAvailable = MutableStateFlow(false)
    override val dockAvailable = mutableDockAvailable.asStateFlow()
    private var dockSubscription: String? = null
    internal var identity: String? = null
        private set

    override suspend fun connect(target: TvTarget): TvResult<Unit> {
        disconnect()
        if (application.checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) != PackageManager.PERMISSION_GRANTED) {
            mutableState.value = TvState(TvConnectionState.PermissionRequired)
            return TvResult.Failure(TvErrorCode.PermissionRequired, "Allow local network access to control your TV.")
        }
        val connectivity = application.getSystemService(ConnectivityManager::class.java)
        val network = lanNetwork(application)
        val capabilities = network?.let(connectivity::getNetworkCapabilities)
        if (network == null || capabilities == null ||
            (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) && !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))) {
            mutableState.value = TvState(TvConnectionState.Failed, "Connect to the same Wi-Fi network as your TV.")
            return TvResult.Failure(TvErrorCode.Unavailable, requireNotNull(mutableState.value.message))
        }
        val saved = try { credentials.load() } catch (_: IOException) {
            mutableState.value = TvState(TvConnectionState.Failed, "Pair with this TV again. Forget the saved TV to start a new pairing.", error = TvErrorCode.PairingRequired)
            return TvResult.Failure(TvErrorCode.PairingRequired, requireNotNull(mutableState.value.message))
        }
        val protocol = LgSession()
        val registered = CompletableDeferred<Unit>()
        session = protocol
        this.target = target
        registration = registered
        mutableState.value = TvState(TvConnectionState.Connecting)
        val trust = TvCertificateTrust(saved?.fingerprint, saved == null)
        var deviceId: String? = null
        var deviceName = "LG TV"
        val tls = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }
        val client = OkHttpClient.Builder()
            .socketFactory(network.socketFactory)
            .sslSocketFactory(tls.socketFactory, trust)
            .hostnameVerifier { hostname, ssl ->
                hostname == target.address && runCatching { TvCertificateTrust.fingerprint(ssl.peerCertificates[0].encoded) == trust.fingerprint }.getOrDefault(false)
            }
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .build()
        http = client
        socket = client.newWebSocket(Request.Builder().url(target.gatewayUrl).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (session !== protocol) { webSocket.cancel(); return }
                webSocket.send(JSONObject().put("type", "hello").put("id", "hello")
                    .put("payload", JSONObject().put("appId", application.packageName).put("appName", "Remote"))
                    .toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (session !== protocol) return
                if (text.length > 1_048_576) { failed(protocol, registered); return }
                val message = runCatching { JSONObject(text) }.getOrElse { failed(protocol, registered); return }
                if (message.optString("type") == "p2p") {
                    if (dockPeer != null && message.optString("from") == dockPeer) {
                        message.optJSONObject("payload")?.let { incomingDock.tryEmit(it) }
                    }
                    return
                }
                if (message.optString("type") == "hello" && deviceId == null) {
                    val identity = message.optJSONObject("payload")?.optString("deviceUUID").orEmpty()
                    if (identity.isBlank() || identity.length > 200 || (saved != null && saved.deviceId != identity)) {
                        failed(protocol, registered, "The TV connection has changed. Pair again to continue.", TvErrorCode.IdentityChanged)
                        return
                    }
                    deviceId = identity
                    deviceName = message.optJSONObject("payload")?.optString("deviceModel", "LG TV")?.take(120) ?: "LG TV"
                    val id = protocol.register()
                    mutableState.value = TvState(TvConnectionState.Pairing)
                    val permissions = JSONArray(listOf(
                        "LAUNCH", "CONTROL_AUDIO", "CONTROL_INPUT_JOYSTICK", "CONTROL_INPUT_TV",
                        "CONTROL_POWER", "READ_CURRENT_CHANNEL", "READ_TV_CHANNEL_LIST",
                        "CONTROL_MOUSE_AND_KEYBOARD", "CONTROL_DISPLAY", "APP_TO_APP",
                    ))
                    val payload = JSONObject().put("pairingType", "PROMPT").put("manifest",
                        JSONObject().put("manifestVersion", 1).put("permissions", permissions))
                    if (saved != null) payload.put("client-key", saved.clientKey)
                    webSocket.send(JSONObject().put("id", id).put("type", "register").put("payload", payload).toString())
                    return
                }
                protocol.receive(message)
                when (protocol.state) {
                    TvConnectionState.Ready -> {
                        registered.complete(Unit)
                    }
                    TvConnectionState.Failed -> failed(protocol, registered, if(saved!=null) "Pair with this TV again." else "The connection was not allowed on the TV.", TvErrorCode.PairingRequired)
                    else -> Unit
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val errno=generateSequence(t){it.cause}.filterIsInstance<android.system.ErrnoException>().firstOrNull()?.errno
                android.util.Log.i("RemoteConnection", "Socket failed: ${t.javaClass.simpleName}; cause=${t.cause?.javaClass?.simpleName}; errno=$errno; http=${response?.code}")
                val identityChanged = saved != null && generateSequence(t) { it.cause }.any { it is CertificateException }
                failed(protocol, registered, if (identityChanged) "The TV connection has changed. Pair again to continue." else "TV unavailable. Check your network and try again.", if(identityChanged)TvErrorCode.IdentityChanged else TvErrorCode.Unavailable)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                failed(protocol, registered)
            }
        })
        try {
            withTimeout(60_000) { registered.await() }
            credentials.save(TvCredentials(target.address, requireNotNull(deviceId), deviceName, requireNotNull(trust.fingerprint), requireNotNull(protocol.registeredKey)))
            if (session === protocol) {
                identity = deviceId
                mutableState.value = TvState(TvConnectionState.Ready, name = deviceName, guideAvailable=true)
                subscribe("audio/getMute") { value -> mutableState.value = state.value.copy(muted = value?.opt("mute") as? Boolean) }
                subscribe("tv/getCurrentChannel") { value -> mutableState.value = state.value.copy(channel = value?.let(::parseChannel)) }
            }
        } catch (_: TimeoutCancellationException) {
            if (session === protocol) {
                disconnect()
                mutableState.value = TvState(TvConnectionState.Failed, "Connection timed out. Check your TV and try again.")
            }
        } catch (cancelled: CancellationException) {
            if (session === protocol) disconnect()
            throw cancelled
        } catch (_: Exception) {
            if (session === protocol && mutableState.value.connection != TvConnectionState.Failed) {
                failed(protocol, registered, "The pairing could not be saved. Pair with this TV again.")
            }
        }
        return if (state.value.connection == TvConnectionState.Ready) TvResult.Success(Unit)
        else TvResult.Failure(state.value.error ?: TvErrorCode.Unavailable, state.value.message ?: "TV unavailable")
    }

    suspend fun read(query: TvRead): JSONObject = request(query.endpoint)

    private suspend fun request(endpoint: String, params: JSONObject = JSONObject(), subscribe: Boolean = false, accept: (JSONObject) -> Boolean = { true }): JSONObject {
        check(application.checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED) { "Local network access is required." }
        val protocol = session ?: throw IOException("TV unavailable")
        if (protocol.state != TvConnectionState.Ready) throw IOException("TV unavailable")
        val (id, reply) = protocol.request(accept)
        try {
            val request = JSONObject().put("type", if (subscribe) "subscribe" else "request").put("id", id).put("uri", "ssap://$endpoint").put("payload", params)
            if (socket?.send(request.toString()) != true) throw IOException("TV unavailable")
            return withTimeout(5_000) { reply.await() }
        } finally { protocol.cancel(id) }
    }

    override suspend fun joinDockChannel() {
        dockSubscription?.let(::unsubscribe)
        val joined = CompletableDeferred<Unit>()
        val id = subscribe("webapp/connectToApp", JSONObject().put("appId", "com.emmah.colordock")) { reply ->
            if (reply?.optString("state") == "CONNECTED" && reply.optString("appId") == "com.emmah.colordock") {
                dockPeer = "com.emmah.colordock"
                mutableDockAvailable.value = true
                joined.complete(Unit)
            } else {
                dockPeer = null
                mutableDockAvailable.value = false
                if (reply == null) joined.completeExceptionally(IOException("Open Color Dock to continue."))
            }
        }
        dockSubscription = id
        try { withTimeout(5000) { joined.await() } }
        catch (error: Exception) { unsubscribe(id); if (dockSubscription == id) dockSubscription = null; throw error }
    }

    private fun subscribe(endpoint: String, params: JSONObject = JSONObject(), callback: (JSONObject?) -> Unit): String {
        val protocol = session ?: throw IOException("TV unavailable")
        val id = protocol.subscribe(callback)
        if (socket?.send(JSONObject().put("type", "subscribe").put("id", id).put("uri", "ssap://$endpoint").put("payload", params).toString()) != true) {
            protocol.cancel(id)
            throw IOException("TV unavailable")
        }
        return id
    }

    private fun unsubscribe(id: String) {
        session?.cancel(id)
        socket?.send(JSONObject().put("type", "unsubscribe").put("id", id).toString())
    }

    override fun sendDock(payload: JSONObject) {
        val peer = dockPeer ?: throw IOException("Open Color Dock to continue.")
        if (state.value.connection != TvConnectionState.Ready ||
            socket?.send(JSONObject().put("type", "p2p").put("to", peer).put("payload", payload).toString()) != true) {
            throw IOException("Color Dock is unavailable.")
        }
    }

    override suspend fun send(command: TvCommand): TvResult<Unit> {
        val origin = session
        return commandLock.withLock {
            if (origin == null || origin !== session || state.value.connection != TvConnectionState.Ready) return@withLock unavailable()
            action {
                if (PointerInputClient.frame(command) != null) {
                    if (pointer == null) {
                        val selected = requireNotNull(target)
                        val pointerUrl = read(TvRead.PointerSocket).getString("socketPath")
                        val input = PointerInputClient(requireNotNull(http), selected)
                        input.connect(pointerUrl)
                        pointer = input
                    }
                    try { requireNotNull(pointer).send(command) }
                    catch (error: Exception) { pointer?.close(); pointer = null; throw error }
                } else {
                    when (command) {
                        TvCommand.VolumeUp -> request("audio/volumeUp")
                        TvCommand.VolumeDown -> request("audio/volumeDown")
                        TvCommand.Mute, TvCommand.Unmute -> request("audio/setMute", JSONObject().put("mute", command == TvCommand.Mute))
                        TvCommand.ChannelUp -> request("tv/channelUp")
                        TvCommand.ChannelDown -> request("tv/channelDown")
                        TvCommand.LiveTv -> request("system.launcher/launch", JSONObject().put("id", "com.webos.app.livetv"))
                        TvCommand.PowerOff -> { request("system/turnOff"); disconnect() }
                        else -> throw IOException("This TV control is unavailable.")
                    }
                }
            }
        }
    }

    override suspend fun channels(): TvResult<List<TvChannel>> = try {
        val list = read(TvRead.Channels).optJSONArray("channelList") ?: throw IOException("The channel list is unavailable.")
        channelList = (0 until minOf(list.length(), 2000)).mapNotNull { index ->
            list.optJSONObject(index)?.let(::parseChannel)
        }
        TvResult.Success(channelList)
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { TvResult.Failure(TvErrorCode.Unavailable, "The channel list is unavailable. Open Live TV and try again.") }

    override suspend fun tune(channelId: String): TvResult<Unit> {
        if (channelList.none { it.id == channelId }) return TvResult.Failure(TvErrorCode.InvalidRequest, "Choose a channel from your TV's channel list.")
        val origin = session
        return commandLock.withLock {
            if (origin == null || origin !== session) return@withLock unavailable()
            action {
                request("system.launcher/launch", JSONObject().put("id", "com.webos.app.livetv"))
                request("tv/openChannel", JSONObject().put("channelId", channelId))
            }
        }
    }

    override suspend fun launchColorDock(): TvResult<Unit> = action {
        request("system.launcher/launch", JSONObject().put("id", "com.emmah.colordock"))
    }

    suspend fun refreshStatus() {
        try {
            val audio = request("audio/getMute")
            val muted = audio.opt("mute") as? Boolean
            mutableState.value = state.value.copy(muted = muted)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { mutableState.value = state.value.copy(muted = null) }
        try {
            val channel = parseChannel(read(TvRead.CurrentChannel))
            mutableState.value = state.value.copy(channel = channel)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { mutableState.value = state.value.copy(channel = null) }
    }

    private fun parseChannel(value: JSONObject): TvChannel? {
        val id = value.opt("channelId") as? String ?: return null
        val number = value.opt("channelNumber") as? String ?: return null
        if (id.isBlank() || id.length > 512 || number.isBlank() || number.length > 40) return null
        return TvChannel(id, number, value.optString("channelName").take(200))
    }

    private suspend fun action(block: suspend () -> Unit): TvResult<Unit> = try {
        if (state.value.connection != TvConnectionState.Ready) unavailable()
        else { block(); TvResult.Success(Unit) }
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { TvResult.Failure(TvErrorCode.Unavailable, "This TV control is unavailable. Check the connection and try again.") }

    private fun unavailable() = TvResult.Failure(TvErrorCode.Unavailable, "TV unavailable. Reconnect to continue.")

    override fun disconnect() {
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
        channelList = emptyList()
        dockPeer = null
        dockSubscription = null
        mutableDockAvailable.value = false
        identity = null
        mutableState.value = TvState()
    }

    internal fun reconnecting() { mutableState.value = state.value.copy(connection=TvConnectionState.Reconnecting) }

    private fun failed(protocol: LgSession, registered: CompletableDeferred<Unit>, message: String = "TV unavailable. Check your network and try again.", code:TvErrorCode = TvErrorCode.Unavailable) {
        if (session !== protocol) return
        protocol.disconnect()
        registered.completeExceptionally(IOException(message))
        mutableState.value = TvState(TvConnectionState.Failed, message,error=code)
        socket?.cancel()
        pointer?.close(); pointer=null
        mutableDockAvailable.value=false
        http?.dispatcher?.executorService?.shutdown()
        http?.connectionPool?.evictAll()
    }

}

internal class LgSession {
    var state = TvConnectionState.Disconnected
        private set
    var registeredKey: String? = null
        private set
    private val sequence = AtomicLong()
    private var registrationId: String? = null
    private class Pending(val reply: CompletableDeferred<JSONObject>, val accept: (JSONObject) -> Boolean)
    private val pending = ConcurrentHashMap<String, Pending>()
    private val subscriptions = ConcurrentHashMap<String, (JSONObject?) -> Unit>()

    fun subscribe(callback: (JSONObject?) -> Unit): String = "subscription-${sequence.incrementAndGet()}".also { subscriptions[it] = callback }

    fun register(): String {
        state = TvConnectionState.Pairing
        registeredKey = null
        return "register-${sequence.incrementAndGet()}".also { registrationId = it }
    }

    fun request(accept: (JSONObject) -> Boolean = { true }): Pair<String, CompletableDeferred<JSONObject>> {
        val id = "request-${sequence.incrementAndGet()}"
        val reply = CompletableDeferred<JSONObject>()
        pending[id] = Pending(reply, accept)
        return id to reply
    }

    fun receive(message: JSONObject) {
        val id = message.optString("id")
        val type = message.optString("type")
        if (id == registrationId && state == TvConnectionState.Pairing) {
            if (type == "registered") {
                val key = message.optJSONObject("payload")?.opt("client-key") as? String ?: ""
                if (key.isNotBlank() && key.length <= 4096) {
                    registeredKey = key
                    state = TvConnectionState.Ready
                } else {
                    state = TvConnectionState.Failed
                }
            } else if (type == "error") {
                state = TvConnectionState.Failed
            }
            return
        }
        if (type != "response" && type != "error") return
        subscriptions[id]?.let { callback ->
            if (type == "error") { subscriptions.remove(id); callback(null) }
            else {val payload=message.optJSONObject("payload");if(payload?.opt("returnValue")==false){subscriptions.remove(id);callback(null)}else callback(payload)}
            return
        }
        val request = pending[id] ?: return
        if (type == "error") {
            pending.remove(id)
            request.reply.completeExceptionally(IOException("The TV did not allow this command."))
        } else {
            val payload = message.optJSONObject("payload") ?: JSONObject()
            if(payload.opt("returnValue")==false){pending.remove(id);request.reply.completeExceptionally(IOException("The TV did not allow this command."))}
            else if (request.accept(payload)) { pending.remove(id); request.reply.complete(payload) }
        }
    }

    fun cancel(id: String) { pending.remove(id)?.reply?.cancel(); subscriptions.remove(id) }

    fun disconnect() {
        state = TvConnectionState.Disconnected
        registeredKey = null
        registrationId = null
        pending.values.forEach { it.reply.completeExceptionally(IOException("TV unavailable")) }
        pending.clear()
        subscriptions.clear()
    }
}
