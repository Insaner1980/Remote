package com.emmah.colordock.remote.mediacenter

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.emmah.colordock.remote.R
import com.emmah.colordock.remote.mediacenter.protocol.ManualChallengeMode
import com.emmah.colordock.remote.mediacenter.protocol.ManualChallengeRequest
import com.emmah.colordock.remote.mediacenter.protocol.RemoteJson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetSocketAddress

/** Explicit read-only physical probe; never pairs, stores trust or sends credentials. */
@RunWith(AndroidJUnit4::class)
class PhysicalMediacenterProbeTest {
    @Test fun selectedWifiInspectsDesktopCertificate() {
        val args = InstrumentationRegistry.getArguments()
        val address = args.getString("mediacenterAddress")
        assumeTrue("Requires an explicit physical desktop test window", address != null)
        val port = requireNotNull(args.getString("mediacenterPort")).toInt()
        val endpoint = requireNotNull(MediacenterEndpoint.parse(requireNotNull(address), port))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val words =
            context.resources
                .openRawResource(
                    R.raw.mediacenter_bip39_en_v1,
                ).bufferedReader()
                .use { it.readLines() }
        val networks = MediacenterNetworks(context)
        try {
            networks.start()
            runBlocking {
                val network = withTimeout(5_000) { networks.networks.first { it.isNotEmpty() }.first() }
                assertTrue(network.available())
                network.socketFactory.createSocket().use {
                    it.connect(InetSocketAddress(network.resolve(endpoint).first(), port), 3_000)
                    assertTrue(it.isConnected)
                }
                val observation = MediacenterTransport.probe(endpoint, network, words)
                assertEquals(6, observation.phrase.size)
                assertTrue(observation.fingerprint.matches(Regex("[a-f0-9]{64}")))
                // The harness must independently compare this pin with the native
                // desktop phrase and open a fresh pairing window before opting in.
                args.getString("expectedFingerprint")?.let { expected ->
                    assertEquals(expected, observation.fingerprint)
                    val body =
                        RemoteJson.encodeManualChallengeRequest(
                            ManualChallengeRequest(1, ManualChallengeMode.MANUAL_CHALLENGE),
                        )
                    val response = MediacenterTransport(endpoint, network, expected).use { it.post(body) }
                    val challenge = RemoteJson.parseManualChallengeResponse(response)
                    assertEquals(1, challenge.v)
                }
            }
        } finally {
            networks.close()
        }
    }
}
