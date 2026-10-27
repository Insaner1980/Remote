package com.emmah.colordock.remote.tv

import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class TransferGateTest {
    @Test fun acknowledgedJpegTransferKeepsControlRepliesResponsive() {
        val address = InstrumentationRegistry.getArguments().getString("tvAddress")
        assumeTrue("Requires explicit physical-device gates", address != null)
        runBlocking {
            val client = LgTvClient(InstrumentationRegistry.getInstrumentation().targetContext)
            val gate = GateSession(client)
            try {
                client.connect(requireNotNull(TvTarget.parse(requireNotNull(address))))
                assertEquals(TvConnectionState.Ready, client.state.value.connection)
                client.joinDockChannel()
                val image = fixtureImage()
                val hash =
                    MessageDigest.getInstance("SHA-256").digest(image).joinToString("") {
                        "%02x".format(it)
                    }
                val start = SystemClock.elapsedRealtime()
                gate.call("begin", JSONObject().put("bytes", image.size).put("sha256", hash))
                var index = 0
                var offset = 0
                var maximumControlMs = 0L
                while (offset < image.size) {
                    val length = minOf(12 * 1024, image.size - offset)
                    val encoded = Base64.encodeToString(image, offset, length, Base64.NO_WRAP)
                    val ack =
                        gate
                            .call(
                                "chunk",
                                JSONObject().put("index", index).put("data", encoded),
                            ).getJSONObject("result")
                    assertEquals(index + 1, ack.getInt("nextIndex"))
                    offset += length
                    index++
                    if (index % 8 == 0) {
                        val before = SystemClock.elapsedRealtime()
                        gate.call("ping")
                        maximumControlMs = maxOf(maximumControlMs, SystemClock.elapsedRealtime() - before)
                    }
                }
                val result = gate.call("commit").getJSONObject("result")
                assertEquals(image.size, result.getInt("bytes"))
                assertEquals(hash, result.getString("sha256"))
                android.util.Log.i(
                    "RemoteDeviceGate",
                    "G4 bytes=${image.size}; chunks=$index; elapsedMs=${SystemClock.elapsedRealtime() - start}; " +
                        "maxControlMs=$maximumControlMs; tvDurationMs=${result.getInt("durationMs")}; " +
                        "maxFrameGapMs=${result.getInt("maxFrameGapMs")}; heapBefore=${result.opt("heapBefore")}; " +
                        "heapAfter=${result.opt("heapAfter")}",
                )
            } finally {
                client.disconnect()
            }
        }
    }

    private class GateSession(
        private val client: LgTvClient,
    ) {
        var sequence = 0

        suspend fun call(
            method: String,
            params: JSONObject = JSONObject(),
        ): JSONObject =
            coroutineScope {
                val id = "gate-${++sequence}"
                val reply =
                    async(start = CoroutineStart.UNDISPATCHED) {
                        withTimeout(5_000) {
                            client.dockMessages.first {
                                it.optString("id") ==
                                    id
                            }
                        }
                    }
                params.put("protocol", "color-dock-gate").put("id", id).put("method", method)
                client.sendDock(params)
                reply.await().also {
                    assertTrue(it.toString().take(120), it.getBoolean("ok"))
                }
            }
    }

    private fun fixtureImage(): ByteArray {
        val pixels = IntArray(1920 * 1080)
        val random = Random(42)
        for (index in pixels.indices) pixels[index] = 0xff000000.toInt() or random.nextInt(0x1000000)
        val bitmap = Bitmap.createBitmap(pixels, 1920, 1080, Bitmap.Config.ARGB_8888)
        pixels.fill(0)
        val image =
            ByteArrayOutputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
                output.toByteArray()
            }
        bitmap.recycle()
        return image
    }
}
