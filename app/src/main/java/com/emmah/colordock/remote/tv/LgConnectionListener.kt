package com.emmah.colordock.remote.tv

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.CompletableDeferred
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.security.cert.CertificateException

private const val MAX_MESSAGE_LENGTH = 1_048_576

internal class LgConnectionListener(
    private val application: Context,
    private val saved: TvCredentials?,
    private val events: Events,
) : WebSocketListener() {
    val trust = TvCertificateTrust(saved?.fingerprint, saved == null)
    var deviceId: String? = null
        private set
    var deviceName = "LG TV"
        private set

    interface Events {
        val lock: Any
        val protocol: LgSession
        val registered: CompletableDeferred<Unit>

        fun isCurrent(): Boolean

        fun pairing()

        fun dockMessage(message: JSONObject)

        fun permissionRequired()

        fun failed(
            message: String = "TV unavailable. Check your network and try again.",
            code: TvErrorCode = TvErrorCode.Unavailable,
        )
    }

    override fun onOpen(
        webSocket: WebSocket,
        response: Response,
    ) {
        synchronized(events.lock) {
            if (!events.isCurrent()) {
                webSocket.cancel()
                return
            }
            webSocket.send(
                JSONObject()
                    .put("type", "hello")
                    .put("id", "hello")
                    .put(
                        "payload",
                        JSONObject().put("appId", application.packageName).put("appName", "Remote"),
                    ).toString(),
            )
        }
    }

    override fun onMessage(
        webSocket: WebSocket,
        text: String,
    ) {
        synchronized(events.lock) {
            if (!events.isCurrent()) return
            val message =
                if (text.length <= MAX_MESSAGE_LENGTH) {
                    runCatching { JSONObject(text) }.getOrNull()
                } else {
                    null
                }
            when {
                message == null -> events.failed()
                message.optString("type") == "p2p" -> events.dockMessage(message)
                message.optString("type") == "hello" && deviceId == null -> register(webSocket, message)
                else -> receive(message)
            }
        }
    }

    private fun register(
        webSocket: WebSocket,
        message: JSONObject,
    ) {
        val identity = (message.optJSONObject("payload")?.opt("deviceUUID") as? String).orEmpty()
        val validIdentity = identity.isNotBlank() && identity.length <= MAX_DEVICE_ID_LENGTH
        if (!validIdentity || (saved != null && saved.deviceId != identity)) {
            events.failed("The TV connection has changed. Pair again to continue.", TvErrorCode.IdentityChanged)
            return
        }
        deviceId = identity
        deviceName = message
            .optJSONObject("payload")
            ?.optString("deviceModel", "LG TV")
            ?.take(MAX_DEVICE_NAME_LENGTH) ?: "LG TV"
        val id = events.protocol.register()
        events.pairing()
        val permissions =
            JSONArray(
                listOf(
                    "LAUNCH",
                    "CONTROL_AUDIO",
                    "CONTROL_INPUT_JOYSTICK",
                    "CONTROL_INPUT_TV",
                    "CONTROL_POWER",
                    "READ_CURRENT_CHANNEL",
                    "READ_TV_CHANNEL_LIST",
                    "READ_INSTALLED_APPS",
                    "CONTROL_MOUSE_AND_KEYBOARD",
                    "CONTROL_DISPLAY",
                    "APP_TO_APP",
                ),
            )
        val payload =
            JSONObject().put("pairingType", "PROMPT").put(
                "manifest",
                JSONObject().put("manifestVersion", 1).put("permissions", permissions),
            )
        if (saved != null) payload.put("client-key", saved.clientKey)
        webSocket.send(
            JSONObject()
                .put("id", id)
                .put("type", "register")
                .put("payload", payload)
                .toString(),
        )
    }

    private fun receive(message: JSONObject) {
        events.protocol.receive(message)
        when (events.protocol.state) {
            TvConnectionState.Ready -> {
                events.registered.complete(Unit)
            }

            TvConnectionState.Failed -> {
                events.failed(
                    if (saved != null) "Pair with this TV again." else "The connection was not allowed on the TV.",
                    TvErrorCode.PairingRequired,
                )
            }

            else -> {
                // Registration remains pending until a terminal protocol state.
            }
        }
    }

    override fun onFailure(
        webSocket: WebSocket,
        t: Throwable,
        response: Response?,
    ) {
        synchronized(events.lock) {
            if (!events.isCurrent()) return
            if (application.checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                events.permissionRequired()
            } else {
                val errno =
                    generateSequence(t) { it.cause }
                        .filterIsInstance<android.system.ErrnoException>()
                        .firstOrNull()
                        ?.errno
                android.util.Log.i(
                    "RemoteConnection",
                    "Socket failed: ${t.javaClass.simpleName}; cause=${t.cause?.javaClass?.simpleName}; " +
                        "errno=$errno; http=${response?.code}",
                )
                val identityChanged =
                    saved != null &&
                        generateSequence(t) { it.cause }
                            .any { it is CertificateException }
                events.failed(
                    if (identityChanged) {
                        "The TV connection has changed. Pair again to continue."
                    } else {
                        "TV unavailable. Check your network and try again."
                    },
                    if (identityChanged) TvErrorCode.IdentityChanged else TvErrorCode.Unavailable,
                )
            }
        }
    }

    override fun onClosed(
        webSocket: WebSocket,
        code: Int,
        reason: String,
    ) {
        events.failed()
    }
}
