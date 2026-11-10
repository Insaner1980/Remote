package com.emmah.colordock.remote.mediacenter

import com.emmah.colordock.remote.mediacenter.protocol.Empty
import com.emmah.colordock.remote.mediacenter.protocol.Expected
import com.emmah.colordock.remote.mediacenter.protocol.McRequest
import com.emmah.colordock.remote.mediacenter.protocol.MethodName
import com.emmah.colordock.remote.mediacenter.protocol.OperationStatus
import com.emmah.colordock.remote.mediacenter.protocol.PairedMediacenter
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand
import com.emmah.colordock.remote.mediacenter.protocol.RemoteJson
import com.emmah.colordock.remote.mediacenter.protocol.Scope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.net.SocketFactory

class MediacenterRecoveryTransportTest {
    private class Route : MediacenterNetwork {
        override val socketFactory: SocketFactory = SocketFactory.getDefault()

        override fun resolve(endpoint: MediacenterEndpoint) =
            listOf(InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)))

        override fun available() = true
    }

    @Test fun exactResyncCloseIsNotRevocationOrAnOrdinaryDisconnect() =
        runBlocking {
            fixture { server, pin ->
                server.enqueue(
                    MockResponse
                        .Builder()
                        .webSocketUpgrade(
                            object : WebSocketListener() {
                                override fun onOpen(
                                    webSocket: WebSocket,
                                    response: Response,
                                ) {
                                    webSocket.send(RecoveryFixture.hello())
                                    webSocket.close(1008, "resync_required")
                                }
                            },
                        ).build(),
                )
                val client = MediacenterClient()
                try {
                    client.connect(saved(server, pin), Route())
                    val result =
                        withTimeout(5000) {
                            client.state.first {
                                it !in
                                    setOf(MediacenterConnectionState.Connecting, MediacenterConnectionState.Connected)
                            }
                        }
                    assertEquals(MediacenterConnectionState.ResyncRequired, result)
                } finally {
                    client.disconnect()
                }
            }
        }

    @Test fun changedCertificateProducesIdentityChangedWithoutLeakingBearerOrReplacingPin() =
        runBlocking {
            fixture { server, _ ->
                server.enqueue(MockResponse.Builder().code(401).build())
                val client = MediacenterClient()
                val record = saved(server, "ab".repeat(32))
                try {
                    client.connect(record, Route())
                    val result =
                        withTimeout(5000) { client.state.first { it != MediacenterConnectionState.Connecting } }
                    assertEquals(MediacenterConnectionState.IdentityChanged, result)
                    assertEquals(0, server.requestCount)
                    assertEquals("ab".repeat(32), record.paired.certificateFingerprint)
                } finally {
                    client.disconnect()
                }
            }
        }

    @Test fun authenticatedHelloIdentityMismatchRequiresExplicitPairing() =
        runBlocking {
            fixture { server, pin ->
                server.enqueue(
                    MockResponse
                        .Builder()
                        .webSocketUpgrade(
                            object : WebSocketListener() {
                                override fun onOpen(
                                    webSocket: WebSocket,
                                    response: Response,
                                ) {
                                    webSocket.send(
                                        org.json
                                            .JSONObject(RecoveryFixture.hello())
                                            .put("deviceId", "different-server")
                                            .toString(),
                                    )
                                }
                            },
                        ).build(),
                )
                val client = MediacenterClient()
                try {
                    client.connect(saved(server, pin), Route())
                    val result =
                        withTimeout(5000) { client.state.first { it != MediacenterConnectionState.Connecting } }
                    assertEquals(MediacenterConnectionState.IdentityChanged, result)
                } finally {
                    client.disconnect()
                }
            }
        }

    @Test fun unavailableSelectedWifiIsDistinctFromRuntimePermissionDenial() {
        val unavailable =
            object : MediacenterNetwork by Route() {
                override fun available() = false
            }
        val client = MediacenterClient()
        client.connect(recoverySaved(), unavailable)
        assertEquals(MediacenterConnectionState.NetworkUnavailable, client.state.value)
        client.disconnect()
    }

    @Test fun actualPinnedSocketRecoversLostNextReplyByIdWithoutExecutingNextTwice() =
        runBlocking {
            fixture { server, pin ->
                val skips = AtomicInteger()
                val queried = AtomicReference<String>()

                fun native(webSocket: WebSocket) {
                    val hello =
                        org.json.JSONObject(RecoveryFixture.hello()).apply {
                            getJSONObject(
                                "capabilities",
                            ).put("methods", org.json.JSONArray(MethodName.entries.map { it.wire }))
                        }
                    webSocket.send(hello.toString())
                    webSocket.send(RecoveryFixture.snapshot())
                }
                server.enqueue(
                    MockResponse
                        .Builder()
                        .webSocketUpgrade(
                            object : WebSocketListener() {
                                override fun onOpen(
                                    webSocket: WebSocket,
                                    response: Response,
                                ) = native(webSocket)

                                override fun onMessage(
                                    webSocket: WebSocket,
                                    text: String,
                                ) {
                                    if (RemoteJson.parseRequest(text).command is RemoteCommand.QueueNext) {
                                        skips.incrementAndGet()
                                        webSocket.close(1001, "fixture-network-loss")
                                    }
                                }
                            },
                        ).build(),
                )
                server.enqueue(
                    MockResponse
                        .Builder()
                        .webSocketUpgrade(
                            object : WebSocketListener() {
                                override fun onOpen(
                                    webSocket: WebSocket,
                                    response: Response,
                                ) = native(webSocket)

                                override fun onMessage(
                                    webSocket: WebSocket,
                                    text: String,
                                ) {
                                    val request = RemoteJson.parseRequest(text)
                                    val command = request.command as RemoteCommand.OperationsGet
                                    queried.set(command.params.requestId)
                                    webSocket.send(
                                        org.json
                                            .JSONObject()
                                            .put("v", 1)
                                            .put("id", request.id)
                                            .put("ok", true)
                                            .put(
                                                "result",
                                                org.json
                                                    .JSONObject()
                                                    .put("operationId", "op")
                                                    .put("requestId", "lost-next")
                                                    .put("status", "succeeded")
                                                    .put("cancellationRequested", false),
                                            ).toString(),
                                    )
                                }
                            },
                        ).build(),
                )
                val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
                val repository = MediacenterRepository(MediacenterClient(), scope, MemoryPending()) { 0.5 }
                val record =
                    saved(
                        server,
                        pin,
                    ).let { StoredMediacenter(it.endpoint, it.paired.copy(scopes = Scope.entries)) }
                try {
                    repository.connect(record, Route())
                    withTimeout(5000) { repository.state.first { it == MediacenterConnectionState.Connected } }
                    assertTrue(
                        repository.send(
                            McRequest(
                                "lost-next",
                                RemoteCommand.QueueNext(Empty),
                                Expected(serverEpoch = "epoch", sessionId = "session", queueRevision = "0"),
                            ),
                        ),
                    )
                    withTimeout(
                        10000,
                    ) { repository.pending.first { it.singleOrNull()?.status == OperationStatus.SUCCEEDED } }
                    assertEquals(1, skips.get())
                    assertEquals("lost-next", queried.get())
                    assertEquals(2, server.requestCount)
                    assertEquals(pin, record.paired.certificateFingerprint)
                } finally {
                    repository.disconnect()
                    scope.cancel()
                }
            }
        }

    private suspend fun fixture(block: suspend (MockWebServer, String) -> Unit) {
        val certificate = HeldCertificate.Builder().build()
        MockWebServer()
            .apply {
                useHttps(
                    HandshakeCertificates
                        .Builder()
                        .heldCertificate(certificate)
                        .build()
                        .sslSocketFactory(),
                )
                start()
            }.use { server -> block(server, CertificateObservation.fingerprint(certificate.certificate.encoded)) }
    }

    private fun saved(
        server: MockWebServer,
        pin: String,
    ) = StoredMediacenter(
        requireNotNull(MediacenterEndpoint.parse("192.168.1.9", server.port)),
        PairedMediacenter("server", "phone", "A".repeat(43), pin, listOf(Scope.BROWSE)),
    )
}
