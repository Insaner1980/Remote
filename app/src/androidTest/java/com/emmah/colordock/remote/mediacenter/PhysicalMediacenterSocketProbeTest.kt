package com.emmah.colordock.remote.mediacenter

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in read-only subscription using only the isolated fixture's existing trust. */
@RunWith(AndroidJUnit4::class)
class PhysicalMediacenterSocketProbeTest {
    @Test fun storedPairingReceivesNativeSnapshot() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("probeStoredConnection") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        require(context.packageName.endsWith(".task7fixture"))
        val networks = MediacenterNetworks(context)
        try {
            networks.start()
            runBlocking {
                val stored = requireNotNull(MediacenterCredentialStore(context).load())
                val network = withTimeout(5_000) { networks.networks.first { it.isNotEmpty() }.first() }
                val result = CompletableDeferred<String>()
                val reducer = RemoteStateReducer(stored.paired.serverDeviceId)
                MediacenterTransport(stored.endpoint, network, stored.paired.certificateFingerprint).use { transport ->
                    val socket =
                        transport.openSocket(
                            stored.paired.credential,
                            object : WebSocketListener() {
                                override fun onMessage(
                                    webSocket: WebSocket,
                                    text: String,
                                ) {
                                    try {
                                        val type =
                                            when (org.json.JSONObject(text).optString("type")) {
                                                "hello" -> "hello"
                                                "snapshot" -> "snapshot"
                                                else -> "other"
                                            }
                                        val step = reducer.apply(text)
                                        if (step != StateReduction.Applied) result.complete("reduction:$type:$step")
                                        if (reducer.ready) result.complete("ready")
                                    } catch (failure: Exception) {
                                        result.complete("message:${failure.javaClass.simpleName}")
                                    }
                                }

                                override fun onFailure(
                                    webSocket: WebSocket,
                                    t: Throwable,
                                    response: Response?,
                                ) {
                                    result.complete("failure:http=${response?.code},kind=${t.javaClass.simpleName}")
                                }

                                override fun onClosing(
                                    webSocket: WebSocket,
                                    code: Int,
                                    reason: String,
                                ) {
                                    result.complete("closed:$code")
                                }
                            },
                        )
                    try {
                        assertEquals("ready", withTimeout(10_000) { result.await() })
                    } finally {
                        socket.cancel()
                    }
                }
            }
        } finally {
            networks.close()
        }
    }
}
