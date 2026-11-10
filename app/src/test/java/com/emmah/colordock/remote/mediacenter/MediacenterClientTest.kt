package com.emmah.colordock.remote.mediacenter

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.Socket
import java.security.MessageDigest
import javax.net.SocketFactory

class MediacenterClientTest {
    private class Route(
        private val unreachable: Set<String> = emptySet(),
    ) : MediacenterNetwork {
        val lookups = mutableListOf<String>()
        var sockets = 0
        var granted = true
        var onLoss: (() -> Unit)? = null

        override fun watch(onLost: () -> Unit): AutoCloseable {
            onLoss = onLost
            return AutoCloseable { onLoss = null }
        }

        override val socketFactory =
            object : SocketFactory() {
                override fun createSocket(): Socket {
                    sockets++
                    return Socket()
                }

                override fun createSocket(
                    host: String,
                    port: Int,
                ) = error("Unexpected unbound socket")

                override fun createSocket(
                    host: String,
                    port: Int,
                    local: InetAddress,
                    localPort: Int,
                ) = error("Unexpected unbound socket")

                override fun createSocket(
                    host: InetAddress,
                    port: Int,
                ) = error("Unexpected unbound socket")

                override fun createSocket(
                    host: InetAddress,
                    port: Int,
                    local: InetAddress,
                    localPort: Int,
                ) = error("Unexpected unbound socket")
            }

        override fun resolve(endpoint: MediacenterEndpoint): List<InetAddress> {
            lookups.add(endpoint.address)
            if (endpoint.address in unreachable) throw java.io.IOException("Fixture unreachable")
            return listOf(InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)))
        }

        override fun permissionGranted() = granted

        override fun available() = granted
    }

    private fun pin(certificate: HeldCertificate) =
        MessageDigest.getInstance("SHA-256").digest(certificate.certificate.encoded).joinToString("") {
            "%02x".format(it)
        }

    private fun server(certificate: HeldCertificate) =
        MockWebServer().apply {
            useHttps(
                HandshakeCertificates
                    .Builder()
                    .heldCertificate(certificate)
                    .build()
                    .sslSocketFactory(),
            )
            start()
        }

    @Test fun selectedNetworkAndExactCertificateAreRequiredBeforePairingSecrets() =
        runBlocking {
            val certificate = HeldCertificate.Builder().commonName("Fixture").build()
            server(certificate).use { server ->
                server.enqueue(MockResponse.Builder().body("paired").build())
                val route = Route()
                val endpoint = requireNotNull(MediacenterEndpoint.parse("192.168.1.9", server.port))
                val wrong = MediacenterTransport(endpoint, route, "ab".repeat(32))
                assertTrue(runCatching { wrong.post("one-use-secret") }.isFailure)
                assertEquals(0, server.requestCount)
                wrong.close()
                val right = MediacenterTransport(endpoint, route, pin(certificate))
                assertEquals("paired", right.post("one-use-secret"))
                val request = server.takeRequest()
                assertEquals("/remote/v1/pair", request.url.encodedPath)
                assertEquals("one-use-secret", request.body!!.utf8())
                assertNull(request.headers["Authorization"])
                assertTrue(route.sockets >= 2)
                right.close()
            }
        }

    @Test fun publicRedirectIsNeverFollowedAndLanRevocationBlocksNewRequests() =
        runBlocking {
            val certificate = HeldCertificate.Builder().build()
            server(certificate).use { server ->
                server.enqueue(
                    MockResponse
                        .Builder()
                        .code(307)
                        .addHeader("Location", "https://8.8.8.8/steal")
                        .build(),
                )
                val route = Route()
                val transport =
                    MediacenterTransport(
                        requireNotNull(MediacenterEndpoint.parse("10.0.0.2", server.port)),
                        route,
                        pin(certificate),
                    )
                assertTrue(runCatching { transport.post("one-use-secret") }.isFailure)
                assertEquals(1, server.requestCount)
                route.granted = false
                assertTrue(runCatching { transport.post("another-secret") }.isFailure)
                assertEquals(1, server.requestCount)
                transport.close()
            }
        }

    @Test fun pairingPersistsOnlyAfterIdentityCheckedSuccessfulResponse() =
        runBlocking {
            val certificate = HeldCertificate.Builder().build()
            server(certificate).use { server ->
                val deviceId = "123e4567-e89b-12d3-a456-426614174000"
                val attemptId = "123e4567-e89b-12d3-a456-426614174001"
                val credential = "A".repeat(43)
                val fingerprint = pin(certificate)

                fun response(identity: String) =
                    org.json
                        .JSONObject()
                        .put("serverDeviceId", identity)
                        .put("deviceId", "phone-device")
                        .put("credential", credential)
                        .put("certificateFingerprint", fingerprint)
                        .put("scopes", org.json.JSONArray(listOf("browse", "playback")))
                        .toString()

                val store =
                    object : MediacenterCredentials {
                        var saved: StoredMediacenter? = null

                        override suspend fun load() = saved

                        override suspend fun save(value: StoredMediacenter) {
                            saved = value
                        }

                        override suspend fun forget() {
                            saved = null
                        }
                    }
                val pairing = MediacenterPairing(store, (0 until 2048).map { "word$it" })
                val candidate =
                    com.emmah.colordock.remote.mediacenter.protocol.PairingCandidate(
                        1,
                        deviceId,
                        listOf("192.168.1.9"),
                        server.port,
                        fingerprint,
                        attemptId,
                        "B".repeat(43),
                    )
                val endpoint = requireNotNull(MediacenterEndpoint.parse("192.168.1.9", server.port))
                server.enqueue(MockResponse.Builder().body(response("wrong-server")).build())
                assertTrue(runCatching { pairing.pairQr(candidate, endpoint, Route(), "Fixture phone") }.isFailure)
                assertNull(store.saved)
                assertEquals(1, server.requestCount)
                server.takeRequest()
                server.enqueue(MockResponse.Builder().body(response(deviceId)).build())
                val paired = pairing.pairQr(candidate, endpoint, Route(), "Fixture phone")
                assertEquals(credential, store.saved!!.paired.credential)
                assertEquals(deviceId, paired.paired.serverDeviceId)
                val request = server.takeRequest().body!!.utf8()
                assertTrue(request.contains("\"deviceLabel\":\"Fixture phone\""))
                assertFalse(paired.toString().contains(credential))
            }
        }

    @Test fun manualProbeSendsNoApplicationDataBeforeImmutablePhraseConfirmation() =
        runBlocking {
            val certificate = HeldCertificate.Builder().build()
            server(certificate).use { server ->
                val route = Route()
                val endpoint = requireNotNull(MediacenterEndpoint.parse("192.168.1.9", server.port))
                val store =
                    object : MediacenterCredentials {
                        var saved: StoredMediacenter? = null

                        override suspend fun load() = saved

                        override suspend fun save(value: StoredMediacenter) {
                            saved = value
                        }

                        override suspend fun forget() {
                            saved = null
                        }
                    }
                val pairing = MediacenterPairing(store, (0 until 2048).map { "word$it" })
                val observation = pairing.probe(endpoint, route)
                assertEquals(pin(certificate), observation.fingerprint)
                assertEquals(0, server.requestCount)
                assertNull(store.saved)
                val confirmation = ManualPairingSession()
                confirmation.show(observation)
                confirmation.confirm(observation, true)
                val pin = requireNotNull(confirmation.confirmedPin)
                server.enqueue(
                    MockResponse.Builder().body("""{"v":1,"serverDeviceId":"server","attemptId":"attempt"}""").build(),
                )
                val challenge = pairing.challenge(endpoint, route, pin)
                val challengeRequest = org.json.JSONObject(server.takeRequest().body!!.utf8())
                assertEquals(1, challengeRequest.getInt("v"))
                assertEquals("manual_challenge", challengeRequest.getString("mode"))
                assertEquals(2, challengeRequest.length())
                server.enqueue(
                    MockResponse
                        .Builder()
                        .body(
                            """{"serverDeviceId":"server","deviceId":"phone","credential":"${"A".repeat(
                                43,
                            )}","certificateFingerprint":"$pin","scopes":["browse"]}""",
                        ).build(),
                )
                pairing.pairManual(challenge, "ABCD2345EFGH", "Fixture phone")
                assertEquals("server", store.saved!!.paired.serverDeviceId)
                val request = server.takeRequest().body!!.utf8()
                assertTrue(request.contains("ABCD2345EFGH"))
                assertFalse(request.contains("secret"))
            }
        }

    @Test fun independentControlUsesBearerOnlyAfterPinnedUpgradeAndStopsOnSelectedNetworkLoss() =
        runBlocking {
            val certificate = HeldCertificate.Builder().build()
            server(certificate).use { server ->
                val hello =
                    """{"v":1,"type":"hello","minor":0,"serverEpoch":"epoch", "deviceId":"server", "version":"Fixture",
                    |"capabilities":{"methods":[],"services":[],"browserBridge":false},"snapshotSequence":"0"}
                    """.trimMargin()
                server.enqueue(
                    MockResponse
                        .Builder()
                        .webSocketUpgrade(
                            object : okhttp3.WebSocketListener() {
                                override fun onOpen(
                                    webSocket: okhttp3.WebSocket,
                                    response: okhttp3.Response,
                                ) {
                                    webSocket.send(hello)
                                }
                            },
                        ).build(),
                )
                val route = Route()
                val saved =
                    StoredMediacenter(
                        requireNotNull(MediacenterEndpoint.parse("192.168.1.9", server.port)),
                        com.emmah.colordock.remote.mediacenter.protocol.PairedMediacenter(
                            "server",
                            "phone",
                            "A".repeat(43),
                            pin(certificate),
                            listOf(com.emmah.colordock.remote.mediacenter.protocol.Scope.BROWSE),
                        ),
                    )
                val control = MediacenterClient()
                control.connect(saved, route)
                assertTrue(
                    control.state.value in
                        setOf(MediacenterConnectionState.Connecting, MediacenterConnectionState.Connected),
                )
                withTimeout(5000) { control.state.first { it == MediacenterConnectionState.Connected } }
                assertEquals("Bearer ${"A".repeat(43)}", server.takeRequest().headers["Authorization"])
                route.granted = false
                route.onLoss!!.invoke()
                assertEquals(MediacenterConnectionState.PermissionRequired, control.state.value)
                control.disconnect()
                assertEquals(MediacenterConnectionState.Disconnected, control.state.value)
            }
        }

    private class Credentials : MediacenterCredentials {
        var saved: StoredMediacenter? = null

        override suspend fun load() = saved

        override suspend fun save(value: StoredMediacenter) {
            saved = value
        }

        override suspend fun forget() {
            saved = null
        }
    }

    private fun qrCandidate(
        server: MockWebServer,
        certificate: HeldCertificate,
    ) = com.emmah.colordock.remote.mediacenter.protocol.PairingCandidate(
        1,
        "server",
        listOf("192.168.1.9", "10.0.0.2"),
        server.port,
        pin(certificate),
        "attempt",
        "B".repeat(43),
    )

    private fun challenge(
        serverId: String = "server",
        attemptId: String = "attempt",
    ) = org.json
        .JSONObject()
        .put("v", 1)
        .put("serverDeviceId", serverId)
        .put("attemptId", attemptId)
        .toString()

    private fun pairedResponse(certificate: HeldCertificate) =
        org.json
            .JSONObject()
            .put("serverDeviceId", "server")
            .put("deviceId", "phone")
            .put("credential", "A".repeat(43))
            .put("certificateFingerprint", pin(certificate))
            .put("scopes", org.json.JSONArray(listOf("browse")))
            .toString()

    @Test fun qrTriesAnotherPinnedCandidateBeforeSendingItsSecretOnce() =
        runBlocking {
            val certificate = HeldCertificate.Builder().build()
            server(certificate).use { server ->
                server.enqueue(MockResponse.Builder().body(challenge()).build())
                server.enqueue(MockResponse.Builder().body(pairedResponse(certificate)).build())
                val route = Route(setOf("192.168.1.9"))
                val store = Credentials()
                val pairing = MediacenterPairing(store, (0 until 2048).map { "word$it" })
                val paired = pairing.pairQr(qrCandidate(server, certificate), route, "Fixture phone")
                assertEquals("10.0.0.2", paired.endpoint.address)
                assertEquals("192.168.1.9", route.lookups.first())
                assertEquals(2, server.requestCount)
                assertFalse(org.json.JSONObject(server.takeRequest().body!!.utf8()).has("secret"))
                assertEquals(
                    "B".repeat(43),
                    org.json.JSONObject(server.takeRequest().body!!.utf8()).getString("secret"),
                )
                assertEquals("server", store.saved!!.paired.serverDeviceId)
            }
        }

    @Test fun qrRejectsMismatchedChallengeIdentityOrAttemptWithoutSendingSecret() =
        runBlocking {
            val certificate = HeldCertificate.Builder().build()
            server(certificate).use { server ->
                val store = Credentials()
                val pairing = MediacenterPairing(store, (0 until 2048).map { "word$it" })
                val candidate = qrCandidate(server, certificate).copy(addresses = listOf("192.168.1.9"))
                for ((index, response) in listOf(
                    challenge(serverId = "other-server"),
                    challenge(attemptId = "other-attempt"),
                ).withIndex()) {
                    server.enqueue(MockResponse.Builder().body(response).build())
                    assertTrue(runCatching { pairing.pairQr(candidate, Route(), "Fixture phone") }.isFailure)
                    assertEquals(index + 1, server.requestCount)
                    assertFalse(org.json.JSONObject(server.takeRequest().body!!.utf8()).has("secret"))
                    assertNull(store.saved)
                }
            }
        }

    @Test fun qrNeverTriesAnotherCandidateAfterAnAmbiguousSecretPost() =
        runBlocking {
            val certificate = HeldCertificate.Builder().build()
            server(certificate).use { server ->
                server.enqueue(MockResponse.Builder().body(challenge()).build())
                server.enqueue(
                    MockResponse.Builder().onResponseStart(mockwebserver3.SocketEffect.CloseSocket()).build(),
                )
                val route = Route()
                val store = Credentials()
                val pairing = MediacenterPairing(store, (0 until 2048).map { "word$it" })
                assertTrue(
                    runCatching { pairing.pairQr(qrCandidate(server, certificate), route, "Fixture phone") }.isFailure,
                )
                assertEquals(2, server.requestCount)
                assertFalse(route.lookups.contains("10.0.0.2"))
                server.takeRequest()
                assertTrue(org.json.JSONObject(server.takeRequest().body!!.utf8()).has("secret"))
                assertNull(store.saved)
            }
        }
}
