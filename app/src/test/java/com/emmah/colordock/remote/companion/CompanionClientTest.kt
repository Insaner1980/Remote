package com.emmah.colordock.remote.companion

import com.emmah.colordock.remote.companion.DockTestFixtures.hello
import com.emmah.colordock.remote.companion.DockTestFixtures.snapshot
import com.emmah.colordock.remote.tv.TvConnectionState
import com.emmah.colordock.remote.tv.TvState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CompanionClientTest {
    @Test fun snapshotWeatherLocationsCannotFeedMalformedValuesBackIntoPatches() {
        val place = WeatherLocation("Place", "", "Country", -90.0, 180.0, "UTC")

        fun input(location: Any) =
            snapshot().apply {
                getJSONObject("settings").getJSONObject("weather").put("location", location)
            }
        val decoded = DockCodec.snapshot(input(place.json())).location
        assertEquals(place, decoded)
        for (enabled in listOf(false, true)) {
            val patch = DockPatch.Weather(enabled, decoded).json()
            assertEquals(setOf("weather"), patch.keys().asSequence().toSet())
            assertEquals(place.json().toString(), patch.getJSONObject("weather").getJSONObject("location").toString())
            assertEquals(enabled, patch.getJSONObject("weather").getBoolean("enabled"))
        }
        for ((field, bad) in listOf(
            "latitude" to "1",
            "latitude" to 91,
            "longitude" to -181,
            "timezone" to "invalid/zone",
            "name" to 12,
            "name" to " ",
            "country" to "x".repeat(121),
        )) {
            assertTrue(
                runCatching {
                    DockCodec.snapshot(input(place.json().put(field, bad)))
                }.isFailure,
            )
        }
        assertTrue(
            runCatching {
                DockCodec.snapshot(input("wrong type"))
            }.isFailure,
        )
        assertNull(DockCodec.snapshot(input(JSONObject.NULL)).location)
    }

    private class Transport : DockTransport {
        override val state = MutableStateFlow(TvState(TvConnectionState.Ready))
        override val dockMessages = MutableSharedFlow<JSONObject>(extraBufferCapacity = 20)
        override val dockAvailable = MutableStateFlow(true)
        val sent = mutableListOf<JSONObject>()
        var joins = 0
        var joinFails = false
        var joinGate: CompletableDeferred<Unit>? = null

        override suspend fun joinDockChannel() {
            joins++
            joinGate?.await()
            if (joinFails) throw java.io.IOException("Not running")
        }

        override fun sendDock(payload: JSONObject) {
            sent.add(payload)
        }

        suspend fun reply(
            request: JSONObject,
            result: JSONObject,
        ) {
            dockMessages.emit(DockTestFixtures.reply(request, result))
        }
    }

    @Test fun replacedTransportCannotPublishPendingReplyBeforeCollectorDisconnects() =
        runTest {
            val tv = Transport()
            val client = CompanionClient(tv, backgroundScope)
            join(client, tv)
            val old =
                async {
                    client.snapshot()
                }
            runCurrent()
            val request = tv.sent.last()
            tv.state.value = tv.state.value.copy(name = "TV B")
            tv.reply(
                request,
                snapshot().apply {
                    getJSONObject("currentWallpaper").put("title", "Late TV A")
                },
            )
            runCurrent()
            assertTrue(old.await() is DockResult.Failure)
            assertNull(client.state.value.snapshot)
            join(client, tv)
            assertEquals(
                0L,
                client.state.value.snapshot
                    ?.revision,
            )
            assertEquals(
                "First",
                client.state.value.snapshot
                    ?.current
                    ?.title,
            )
        }

    @Test fun retiredOperationCannotSendOnNewSessionOrReplacedTransport() =
        runTest {
            val tv = Transport()
            val client = CompanionClient(tv, backgroundScope)
            join(client, tv)
            val origin = client.sessionGeneration
            client.disconnect()
            join(client, tv)
            val count = tv.sent.size
            assertTrue(client.call(DockMethod.UploadCancel, JSONObject(), origin) is DockResult.Failure)
            assertTrue(client.snapshot(origin) is DockResult.Failure)
            assertEquals(count, tv.sent.size)
            tv.state.value = tv.state.value.copy(name = "Different transport")
            assertTrue(client.call(DockMethod.ClockStart, JSONObject()) is DockResult.Failure)
            assertEquals(count, tv.sent.size)
        }

    @Test fun obsoleteReadsCannotReplaceLaterReadPatchOrClockEvent() =
        runTest {
            val tv = Transport()
            val client = CompanionClient(tv, backgroundScope)
            join(client, tv)
            val first =
                async {
                    client.snapshot()
                }
            val second =
                async {
                    client.snapshot()
                }
            runCurrent()
            val reads = tv.sent.takeLast(2)
            tv.reply(reads[1], snapshot().put("revision", 2))
            runCurrent()
            tv.reply(reads[0], snapshot().put("revision", 1))
            runCurrent()
            first.await()
            second.await()
            assertEquals(
                2L,
                client.state.value.snapshot
                    ?.revision,
            )
            val old =
                async {
                    client.snapshot()
                }
            runCurrent()
            val oldRequest = tv.sent.last()
            val patch =
                async {
                    client.patch(2, DockPatch.Clock(10))
                }
            runCurrent()
            tv.reply(tv.sent.last(), snapshot().put("revision", 3))
            runCurrent()
            tv.reply(oldRequest, snapshot().put("revision", 2))
            runCurrent()
            old.await()
            patch.await()
            assertEquals(
                3L,
                client.state.value.snapshot
                    ?.revision,
            )
            assertClockEventRetiresPendingRead(client, tv)
            client.disconnect()
            join(client, tv)
            assertEquals(
                0L,
                client.state.value.snapshot
                    ?.revision,
            )
        }

    private suspend fun TestScope.assertClockEventRetiresPendingRead(
        client: CompanionClient,
        tv: Transport,
    ) {
        val beforeClock =
            async {
                client.snapshot()
            }
        runCurrent()
        val clockRead = tv.sent.last()
        tv.dockMessages.emit(
            JSONObject()
                .put(
                    "protocol",
                    "color-dock",
                ).put("version", 1)
                .put("event", "clock.changed")
                .put("active", true),
        )
        runCurrent()
        tv.reply(clockRead, snapshot().put("revision", 3))
        runCurrent()
        beforeClock.await()
        assertEquals(
            true,
            client.state.value.snapshot
                ?.clockActive,
        )
    }

    @Test fun callerDeadlinePropagatesAndNextRequestStillWorks() =
        runTest {
            val tv = Transport()
            val client = CompanionClient(tv, backgroundScope)
            join(client, tv)
            val cancelled =
                async {
                    runCatching {
                        withTimeout(100) {
                            client.call(DockMethod.ClockStart, JSONObject())
                        }
                    }
                }
            runCurrent()
            advanceTimeBy(101)
            runCurrent()
            assertTrue(cancelled.await().exceptionOrNull() is TimeoutCancellationException)
            val next =
                async {
                    client.call(DockMethod.ClockStop, JSONObject())
                }
            runCurrent()
            tv.reply(tv.sent.last(), JSONObject())
            runCurrent()
            assertTrue(next.await() is DockResult.Success)
            assertEquals(
                2,
                tv.sent.count {
                    it.getString("method") in listOf("clock.start", "clock.stop")
                },
            )
        }

    @Test fun utf8EnvelopeBoundsAndInvalidVersionDoNotConsumeAValidReply() =
        runTest {
            val tv = Transport()
            val client = CompanionClient(tv, backgroundScope)
            join(client, tv)
            for (size in listOf(32767, 32768, 32769)) {
                val call =
                    async {
                        client.call(DockMethod.ClockStart, JSONObject())
                    }
                runCurrent()
                val request = tv.sent.last()
                val reply = DockTestFixtures.reply(request, JSONObject()).put("padding", "")
                val padding = size - reply.toString().toByteArray().size
                reply.put("padding", "ä".repeat(padding / 2) + if (padding % 2 == 1) "a" else "")
                assertEquals(size, reply.toString().toByteArray().size)
                tv.dockMessages.emit(JSONObject(reply.toString()).put("version", 4294967297L))
                runCurrent()
                assertFalse(call.isCompleted)
                tv.dockMessages.emit(reply)
                runCurrent()
                if (size > 32768) {
                    assertFalse(call.isCompleted)
                    tv.reply(request, JSONObject())
                    runCurrent()
                }
                assertTrue(call.await() is DockResult.Success)
            }
            val invalid =
                JSONObject()
                    .put(
                        "protocol",
                        "color-dock",
                    ).put("version", 1)
                    .put("id", "fixture")
                    .put("ok", "true")
                    .put("result", JSONObject())
            assertThrows(IllegalArgumentException::class.java) {
                DockCodec.reply(invalid, "fixture")
            }
        }

    @Test fun negotiationRejectsUnusableOrCoercedLimitsAndResetsPreviousValues() =
        runTest {
            for ((name, value) in listOf(
                "chunkBytes" to "1024",
                "chunkBytes" to 1024.5,
                "chunkBytes" to 4294968320L,
                "imageBytes" to 0,
                "thumbnailBytes" to -1,
            )) {
                val tv = Transport()
                val client = CompanionClient(tv, backgroundScope)
                val joined =
                    startJoin(
                        client,
                        tv,
                        hello().apply {
                            getJSONObject("limits").put(name, value)
                        },
                    )
                assertTrue(joined.await() is DockResult.Failure)
                assertEquals(DockLimits(), client.limits)
                assertEquals(DockConnectionState.Unavailable, client.state.value.connection)
            }
            for (chunk in listOf(1024, 12288)) {
                val tv = Transport()
                val client = CompanionClient(tv, backgroundScope)
                val joined =
                    startJoin(
                        client,
                        tv,
                        hello().apply {
                            getJSONObject("limits").put("chunkBytes", chunk).put("imageBytes", Long.MAX_VALUE)
                        },
                    )
                tv.reply(tv.sent.last(), snapshot())
                runCurrent()
                assertTrue(joined.await() is DockResult.Success)
                assertEquals(chunk, client.limits.chunkBytes)
                assertEquals(4194304, client.limits.imageBytes)
                client.disconnect()
                assertEquals(DockLimits(), client.limits)
            }
        }

    @Test fun invalidInitialSnapshotLeavesUnavailableAndCanJoinAgain() =
        runTest {
            val tv = Transport()
            val client = CompanionClient(tv, backgroundScope)
            val joined = startJoin(client, tv)
            tv.reply(tv.sent.last(), JSONObject())
            runCurrent()
            assertTrue(joined.await() is DockResult.Failure)
            assertEquals(DockConnectionState.Unavailable, client.state.value.connection)
            assertNotNull(client.state.value.message)
            join(client, tv)
            assertEquals(2, tv.joins)
        }

    @Test fun retiredChannelJoinCannotSendHelloOnReplacementConnection() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val tv =
                Transport().apply {
                    joinGate = gate
                }
            val client = CompanionClient(tv, backgroundScope)
            val old =
                async {
                    client.join()
                }
            runCurrent()
            client.disconnect()
            gate.complete(Unit)
            runCurrent()
            assertTrue(old.await() is DockResult.Failure)
            assertTrue(tv.sent.isEmpty())
            tv.joinGate = null
            join(client, tv)
            assertEquals(DockConnectionState.Ready, client.state.value.connection)
        }

    private suspend fun TestScope.startJoin(
        client: CompanionClient,
        tv: Transport,
        greeting: JSONObject = hello(),
    ): Deferred<DockResult<DockSnapshot>> {
        val joined =
            async {
                client.join()
            }
        runCurrent()
        tv.reply(tv.sent.last(), greeting)
        runCurrent()
        return joined
    }

    private suspend fun TestScope.join(
        client: CompanionClient,
        tv: Transport,
    ) {
        val joined = startJoin(client, tv)
        tv.reply(tv.sent.last(), snapshot())
        runCurrent()
        assertTrue(joined.await() is DockResult.Success)
    }

    @Test fun colorDockUnavailable_doesNotDisconnectTvControl() =
        runTest {
            val tv =
                Transport().apply {
                    joinFails = true
                }
            val client = CompanionClient(tv, backgroundScope)
            assertTrue(client.join() is DockResult.Failure)
            assertEquals(TvConnectionState.Ready, tv.state.value.connection)
            assertEquals(DockConnectionState.Unavailable, client.state.value.connection)
        }

    @Test fun responsesAreCorrelatedAndCompanionDepartureFailsPendingWithoutClosingTv() =
        runTest {
            val tv = Transport()
            val client = CompanionClient(tv, backgroundScope)
            join(client, tv)
            val first =
                async {
                    client.call(DockMethod.ClockStart, JSONObject())
                }
            val second =
                async {
                    client.call(DockMethod.ClockStop, JSONObject())
                }
            runCurrent()
            tv.reply(tv.sent.last(), JSONObject().put("active", false))
            runCurrent()
            assertFalse(first.isCompleted)
            assertTrue(second.await() is DockResult.Success)
            tv.dockAvailable.value = false
            runCurrent()
            assertTrue(first.await() is DockResult.Failure)
            assertEquals(TvConnectionState.Ready, tv.state.value.connection)
        }

    @Test fun fiveSecondTimeoutDoesNotReplayCommandAndRepeatedJoinKeepsOneSubscription() =
        runTest {
            val tv = Transport()
            val client = CompanionClient(tv, backgroundScope)
            join(client, tv)
            val command =
                async {
                    client.call(DockMethod.Next, JSONObject().put("expectedRevision", 0))
                }
            runCurrent()
            val count = tv.sent.size
            advanceTimeBy(5001)
            runCurrent()
            assertEquals("TIMEOUT", (command.await() as DockResult.Failure).code)
            assertEquals(count, tv.sent.size)
            val rejoin =
                async {
                    client.join()
                }
            runCurrent()
            tv.reply(tv.sent.last(), snapshot())
            runCurrent()
            assertTrue(rejoin.await() is DockResult.Success)
            assertEquals(1, tv.joins)
        }

    @Test fun sharedProtocolFixturesMatchWireNamesErrorCodesAndStrictRevisionType() {
        val fixtures = JSONObject(requireNotNull(javaClass.classLoader?.getResource("companion-v1.json")).readText())
        val patch = fixtures.getJSONObject("patch")
        assertEquals(DockMethod.Patch.wire, patch.getString("method"))
        assertEquals(
            DockPatch.Clock(10).json().toString(),
            patch.getJSONObject("params").getJSONObject("patch").toString(),
        )
        assertEquals(1, fixtures.getJSONObject("hello").getInt("version"))
        for (name in listOf("stale", "unknown", "malformed", "unsupported", "expired")) {
            val id = fixtures.getJSONObject(name).getString("id")
            val code = fixtures.getJSONObject("errors").getString(name)
            val reply =
                JSONObject()
                    .put(
                        "protocol",
                        "color-dock",
                    ).put("version", 1)
                    .put("id", id)
                    .put("ok", false)
                    .put("error", JSONObject().put("code", code))
            try {
                DockCodec.reply(reply, id)
                fail("Expected typed error")
            } catch (error: DockException) {
                assertEquals(code, error.code)
            }
        }
        assertEquals(0L, DockCodec.snapshot(snapshot()).revision)
        assertEquals(Long.MAX_VALUE, DockCodec.snapshot(snapshot().put("revision", Long.MAX_VALUE)).revision)
        for (invalid in listOf(
            "0",
            .5,
            -1,
            9.223372036854776E18,
            java.math.BigInteger("9223372036854775808"),
            JSONObject.NULL,
        )) {
            try {
                DockCodec.snapshot(snapshot().put("revision", invalid))
                fail("Expected strict revision")
            } catch (_: IllegalArgumentException) {
            }
        }
    }
}
