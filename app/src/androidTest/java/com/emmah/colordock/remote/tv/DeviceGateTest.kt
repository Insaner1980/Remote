package com.emmah.colordock.remote.tv

import android.Manifest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.emmah.colordock.remote.ConnectionProbe
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Only runs when explicitly given tvAddress; no TV app launches or key commands. */
@RunWith(AndroidJUnit4::class)
class DeviceGateTest {
    @Test fun appChannelRoundTrip() {
        val address = InstrumentationRegistry.getArguments().getString("tvAddress")
        assumeTrue("Requires an explicit physical TV test window", address != null)
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val client = LgTvClient(context)
            try {
                client.connect(requireNotNull(TvTarget.parse(requireNotNull(address))))
                assertEquals(client.state.value.message, TvConnectionState.Ready, client.state.value.connection)
                client.joinDockChannel()
                val reply =
                    async(start = CoroutineStart.UNDISPATCHED) {
                        withTimeout(5_000) {
                            client.dockMessages.first {
                                it.optString("id") == "g2-ping"
                            }
                        }
                    }
                client.sendDock(
                    JSONObject()
                        .put(
                            "protocol",
                            "color-dock-gate",
                        ).put("method", "ping")
                        .put("id", "g2-ping")
                        .put("from", "caller-supplied-spoof"),
                )
                assertTrue(reply.await().getBoolean("ok"))
                android.util.Log.i("RemoteDeviceGate", "G2 paired app-channel request/reply PASS")
            } finally {
                client.disconnect()
            }
        }
    }

    @Test fun pairedTlsAndReadOnlyCapabilities() {
        val address = InstrumentationRegistry.getArguments().getString("tvAddress")
        assumeTrue("Requires an explicit physical TV test window", address != null)
        runBlocking {
            val target = requireNotNull(TvTarget.parse(requireNotNull(address)))
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.uiAutomation.grantRuntimePermission(
                instrumentation.targetContext.packageName,
                Manifest.permission.ACCESS_LOCAL_NETWORK,
            )
            val client = LgTvClient(instrumentation.targetContext)
            try {
                client.connect(target)
                assertEquals(client.state.value.message, TvConnectionState.Ready, client.state.value.connection)
                val report = ConnectionProbe.read(client, target)
                for ((query, result) in report) android.util.Log.i("RemoteDeviceGate", "$query: $result")
                assertEquals(TvRead.entries.size, report.size)
            } finally {
                client.disconnect()
            }
        }
    }
}
