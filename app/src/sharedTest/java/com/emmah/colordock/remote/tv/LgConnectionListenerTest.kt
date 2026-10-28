package com.emmah.colordock.remote.tv

import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okio.ByteString
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.security.cert.CertificateException
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket

@RunWith(AndroidJUnit4::class)
class LgConnectionListenerTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private class Events : LgConnectionListener.Events {
        override val lock = Any()
        override val protocol = LgSession()
        override val registered = CompletableDeferred<Unit>()
        var current = true
        var pairings = 0
        var permissionDenied = false
        var failure: TvErrorCode? = null
        var message: JSONObject? = null

        override fun isCurrent() = current

        override fun pairing() {
            pairings++
        }

        override fun dockMessage(message: JSONObject) {
            this.message = message
        }

        override fun permissionRequired() {
            permissionDenied = true
        }

        override fun failed(
            message: String,
            code: TvErrorCode,
        ) {
            failure = code
        }
    }

    private class Socket : WebSocket {
        val sent = mutableListOf<JSONObject>()
        var cancelled = false

        override fun request() = Request.Builder().url("https://192.168.1.10:3001/").build()

        override fun queueSize() = 0L

        override fun send(text: String): Boolean {
            sent.add(JSONObject(text))
            return true
        }

        override fun send(bytes: ByteString) = false

        override fun close(
            code: Int,
            reason: String?,
        ) = true

        override fun cancel() {
            cancelled = true
        }
    }

    private fun saved() = TvCredentials("192.168.1.10", "fixture", "TV", "a".repeat(64), "key")

    private fun opened(socket: Socket) =
        Response
            .Builder()
            .request(socket.request())
            .protocol(Protocol.HTTP_1_1)
            .code(101)
            .message("fixture")
            .build()

    private fun hello(identity: String = "fixture") =
        JSONObject().put("type", "hello").put("payload", JSONObject().put("deviceUUID", identity)).toString()

    @Test fun pairingValidatesIdentityAndCompletesOnlyAfterRegistration() {
        for (credentials in listOf(null, saved())) {
            val events = Events()
            val socket = Socket()
            val listener = LgConnectionListener(context, credentials, events)
            listener.onOpen(socket, opened(socket))
            assertEquals("hello", socket.sent.single().getString("type"))
            listener.onMessage(socket, hello())
            assertEquals(1, events.pairings)
            assertEquals("fixture", listener.deviceId)
            assertEquals("LG TV", listener.deviceName)
            val registration = socket.sent.last()
            assertEquals(credentials != null, registration.getJSONObject("payload").has("client-key"))
            listener.onMessage(
                socket,
                JSONObject().put("type", "response").put("id", registration.getString("id")).toString(),
            )
            assertFalse(events.registered.isCompleted)
            listener.onMessage(
                socket,
                JSONObject()
                    .put(
                        "type",
                        "registered",
                    ).put(
                        "id",
                        registration.getString("id"),
                    ).put("payload", JSONObject().put("client-key", "accepted"))
                    .toString(),
            )
            assertTrue(events.registered.isCompleted)
            assertNull(events.failure)
        }
    }

    @Test fun malformedIdentityAndRejectedPairingCannotBecomeReady() {
        for (identity in listOf("", "x".repeat(201), "changed")) {
            val events = Events()
            val listener = LgConnectionListener(context, saved(), events)
            listener.onMessage(Socket(), hello(identity))
            assertEquals(TvErrorCode.IdentityChanged, events.failure)
            assertNull(listener.deviceId)
        }
        for (credentials in listOf(null, saved())) {
            val events = Events()
            val socket = Socket()
            val listener = LgConnectionListener(context, credentials, events)
            listener.onMessage(socket, hello())
            listener.onMessage(
                socket,
                JSONObject().put("type", "error").put("id", socket.sent.last().getString("id")).toString(),
            )
            assertEquals(TvErrorCode.PairingRequired, events.failure)
        }
    }

    @Test fun staleCallbacksAndMalformedMessagesAreIsolated() {
        val events = Events()
        val socket = Socket()
        val listener = LgConnectionListener(context, null, events)
        listener.onMessage(socket, """{"type":"p2p","payload":{}}""")
        assertEquals("p2p", events.message?.getString("type"))
        listener.onMessage(socket, "invalid")
        assertEquals(TvErrorCode.Unavailable, events.failure)
        events.failure = null
        listener.onMessage(socket, "x".repeat(1_048_577))
        assertEquals(TvErrorCode.Unavailable, events.failure)
        events.failure = null
        events.current = false
        listener.onOpen(socket, opened(socket))
        listener.onMessage(socket, "invalid")
        listener.onFailure(socket, IOException(), null)
        assertTrue(socket.cancelled)
        assertNull(events.failure)
    }

    @Test fun networkFailuresDistinguishPermissionAndCertificateIdentity() {
        var permission = PackageManager.PERMISSION_GRANTED
        val application =
            object : ContextWrapper(context) {
                override fun checkSelfPermission(name: String) = permission
            }
        val events = Events()
        val socket = Socket()
        val listener = LgConnectionListener(application, saved(), events)
        listener.onFailure(socket, IOException("fixture", CertificateException()), opened(socket))
        assertEquals(TvErrorCode.IdentityChanged, events.failure)
        listener.onFailure(socket, IOException("fixture", android.system.ErrnoException("connect", 111)), null)
        assertEquals(TvErrorCode.Unavailable, events.failure)
        permission = PackageManager.PERMISSION_DENIED
        listener.onFailure(socket, IOException(), null)
        assertTrue(events.permissionDenied)
        listener.onClosed(socket, 1000, "closed")
        assertEquals(TvErrorCode.Unavailable, events.failure)
    }

    @Test fun tvHttpClientKeepsModernTlsAndRejectsUnverifiedPeers() {
        val network = requireNotNull(context.getSystemService(ConnectivityManager::class.java).activeNetwork)
        val client =
            tvHttpClient(network, requireNotNull(TvTarget.parse("192.168.1.10")), TvCertificateTrust(null, true))
        try {
            (client.sslSocketFactory.createSocket() as SSLSocket).use { socket ->
                assertTrue(socket.enabledProtocols.contains("TLSv1.3"))
                assertTrue(socket.enabledProtocols.contains("TLSv1.2"))
            }
            val session =
                SSLContext
                    .getInstance("TLSv1.3")
                    .apply { init(null, null, null) }
                    .createSSLEngine()
                    .session
            assertFalse(client.hostnameVerifier.verify("192.168.1.11", session))
            assertFalse(client.hostnameVerifier.verify("192.168.1.10", session))
            assertFalse(client.followRedirects)
            assertFalse(client.followSslRedirects)
        } finally {
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }
}
