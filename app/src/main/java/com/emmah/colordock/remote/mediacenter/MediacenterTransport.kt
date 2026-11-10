package com.emmah.colordock.remote.mediacenter

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.ConnectionSpec
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import java.net.InetAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.SocketFactory
import javax.net.ssl.SSLContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface MediacenterNetwork {
    val socketFactory: SocketFactory

    fun resolve(endpoint: MediacenterEndpoint): List<InetAddress>

    fun available(): Boolean

    fun watch(onLost: () -> Unit): AutoCloseable = AutoCloseable { }
}

/** Every socket belongs to the selected Network. No proxy, redirect, public DNS, or retry fallback. */
class MediacenterTransport private constructor(
    val endpoint: MediacenterEndpoint,
    val network: MediacenterNetwork,
    private val trust: MediacenterCertificateTrust,
    private val fingerprint: String?,
) : AutoCloseable {
    constructor(endpoint: MediacenterEndpoint, network: MediacenterNetwork, fingerprint: String) :
        this(endpoint, network, MediacenterCertificateTrust(fingerprint), fingerprint)

    private val closed = AtomicBoolean(false)
    private val client = createClient()

    private fun createClient(): OkHttpClient {
        val tls = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }
        return OkHttpClient
            .Builder()
            .socketFactory(network.socketFactory)
            .dns(
                Dns { host ->
                    if (host != INTERNAL_HOST ||
                        !network.available()
                    ) {
                        throw IOException("Selected local network unavailable")
                    }
                    network.resolve(endpoint)
                },
            ).sslSocketFactory(tls.socketFactory, trust)
            .hostnameVerifier { host, session ->
                host == INTERNAL_HOST && fingerprint != null &&
                    runCatching {
                        CertificateObservation.fingerprint(session.peerCertificates.first().encoded) == fingerprint
                    }.getOrDefault(false)
            }.proxy(Proxy.NO_PROXY)
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .protocols(listOf(Protocol.HTTP_1_1))
            .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS))
            .connectTimeout(DEADLINE_SECONDS, TimeUnit.SECONDS)
            .readTimeout(DEADLINE_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(DEADLINE_SECONDS, TimeUnit.SECONDS)
            .callTimeout(DEADLINE_SECONDS, TimeUnit.SECONDS)
            .pingInterval(PING_SECONDS, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                if (closed.get() ||
                    !network.available()
                ) {
                    throw IOException("Allow local network access and select Wi-Fi again.")
                }
                chain.proceed(chain.request())
            }.build()
    }

    private fun url(path: String): HttpUrl =
        HttpUrl
            .Builder()
            .scheme("https")
            .host(INTERNAL_HOST)
            .port(endpoint.port)
            .encodedPath(path)
            .build()

    suspend fun post(body: String): String {
        require(body.toByteArray(Charsets.UTF_8).size <= PAIR_BYTES)
        val request =
            Request
                .Builder()
                .url(url("/remote/v1/pair"))
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(
                        call: Call,
                        e: IOException,
                    ) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(
                                IOException("Mediacenter could not be reached securely.", e),
                            )
                        }
                    }

                    override fun onResponse(
                        call: Call,
                        response: Response,
                    ) {
                        val result =
                            runCatching {
                                response.use {
                                    if (!it.isSuccessful) {
                                        throw IOException(
                                            "Pairing is unavailable. Open a new code on the computer.",
                                        )
                                    }
                                    val source = it.body.source()
                                    if (source.request(PAIR_BYTES + 1L)) throw IOException("Invalid pairing response")
                                    source.readUtf8()
                                }
                            }
                        if (continuation.isActive) result.fold(continuation::resume, continuation::resumeWithException)
                    }
                },
            )
        }
    }

    fun openSocket(
        credential: String,
        listener: WebSocketListener,
    ): WebSocket {
        require(credential.matches(Regex("[A-Za-z0-9_-]{43}")))
        if (closed.get() || !network.available()) throw IOException("Selected local network unavailable")
        return client.newWebSocket(
            Request
                .Builder()
                .url(url("/remote/v1/ws"))
                .header("Authorization", "Bearer $credential")
                .build(),
            listener,
        )
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            client.dispatcher.cancelAll()
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }

    companion object {
        private const val INTERNAL_HOST = "mediacenter.invalid"
        private const val DEADLINE_SECONDS = 5L
        private const val PING_SECONDS = 10L
        private const val PAIR_BYTES = 65536

        suspend fun probe(
            endpoint: MediacenterEndpoint,
            network: MediacenterNetwork,
            words: List<String>,
        ): CertificateObservation {
            val trust = MediacenterCertificateTrust(null, words)
            MediacenterTransport(endpoint, network, trust, null).use { transport ->
                try {
                    transport.post("{}")
                } catch (_: IOException) {
                    // TLS rejection is deliberate.
                }
                return trust.observation ?: throw IOException("The computer's certificate could not be inspected.")
            }
        }
    }
}
