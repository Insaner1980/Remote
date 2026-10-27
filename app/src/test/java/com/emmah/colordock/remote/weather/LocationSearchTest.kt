package com.emmah.colordock.remote.weather

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class LocationSearchTest {
    @Test fun invalidLocationsAreSkippedWithoutCoercionOrLosingValidSiblings() =
        runBlocking {
            fun place() =
                org.json
                    .JSONObject()
                    .put("name", "Same")
                    .put("latitude", 90)
                    .put("longitude", -180)
                    .put("timezone", "UTC")
            val entries = org.json.JSONArray().put(place())
            for ((key, value) in listOf(
                "latitude" to "12",
                "latitude" to "NaN",
                "latitude" to 91,
                "longitude" to 181,
                "timezone" to "invalid/zone",
                "name" to 12,
                "name" to " ",
                "country" to true,
            )) {
                entries.put(place().put(key, value))
            }
            entries.put(
                place()
                    .put("latitude", -90)
                    .put("longitude", 180)
                    .put("admin1", "Same")
                    .put("country", "x".repeat(121)),
            )
            entries.put(place().put("name", "Outside cap"))
            var json =
                org.json
                    .JSONObject()
                    .put("results", entries)
                    .toString()
            val service =
                LocationSearch(
                    client {
                        response(it, Body(json.toByteArray()))
                    },
                )
            try {
                val results = service.search("Same")
                assertEquals(2, results.size)
                assertEquals(90.0, results[0].latitude, 0.0)
                assertEquals(-90.0, results[1].latitude, 0.0)
                assertEquals("Same, " + "x".repeat(120), results[1].label)
                for (empty in listOf("{}", "{\"results\":[]}")) {
                    json = empty
                    assertTrue(service.search("Same").isEmpty())
                }
            } finally {
                service.close()
            }
        }

    private class Body(
        private val bytes: ByteArray,
        private val declared: Long = bytes.size.toLong(),
        private val readBlock: () -> Unit = {},
    ) : ResponseBody() {
        var closed = false
        private val input =
            object : Source {
                val buffer = Buffer().write(bytes)

                override fun read(
                    sink: Buffer,
                    byteCount: Long,
                ): Long {
                    readBlock()
                    return buffer.read(sink, byteCount)
                }

                override fun timeout() = Timeout.NONE

                override fun close() {
                    closed = true
                }
            }.buffer()

        override fun contentType(): MediaType? = null

        override fun contentLength() = declared

        override fun source(): BufferedSource = input
    }

    private fun client(block: (Interceptor.Chain) -> Response) =
        OkHttpClient
            .Builder()
            .callTimeout(10, TimeUnit.SECONDS)
            .followRedirects(false)
            .addInterceptor {
                block(it)
            }.build()

    private fun response(
        chain: Interceptor.Chain,
        body: ResponseBody,
        code: Int = 200,
    ) = Response
        .Builder()
        .request(
            chain.request(),
        ).protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("fixture")
        .body(body)
        .build()

    @Test fun reservedAndUnicodeQueriesStayOnFixedEndpointAndRedirectIsNotFollowed() =
        runBlocking {
            val requests = mutableListOf<Request>()
            var status = 200
            val service =
                LocationSearch(
                    client { chain ->
                        requests.add(chain.request())
                        response(
                            chain,
                            Body("{}".toByteArray()),
                            status,
                        ).newBuilder().header("Location", "https://unexpected.invalid/").build()
                    },
                )
            try {
                for (query in listOf("  New York  ", "Hämeenlinna", "A & ? # / B")) {
                    assertTrue(service.search(query).isEmpty())
                    val url = requests.last().url
                    assertEquals("https", url.scheme)
                    assertEquals("geocoding-api.open-meteo.com", url.host)
                    assertEquals("/v1/search", url.encodedPath)
                    assertEquals(query.trim(), url.queryParameter("name"))
                    assertEquals(setOf("name", "count", "language", "format"), url.queryParameterNames)
                    assertEquals("10", url.queryParameter("count"))
                    assertEquals("en", url.queryParameter("language"))
                    assertEquals("json", url.queryParameter("format"))
                }
                status = 302
                assertTrue(
                    runCatching {
                        service.search("Paris")
                    }.isFailure,
                )
                assertEquals(4, requests.size)
            } finally {
                service.close()
            }
        }

    @Test fun effectiveReadBoundAndResponseClosureCoverUnknownOrInaccurateLengths() =
        runBlocking {
            for ((size, declared) in listOf(262144 to -1L, 262145 to -1L, 262145 to 100L, 262145 to 262145L)) {
                val bytes = ("{}" + " ".repeat(size - 2)).toByteArray()
                val body = Body(bytes, declared)
                val service =
                    LocationSearch(
                        client {
                            response(it, body)
                        },
                    )
                try {
                    val result =
                        runCatching {
                            service.search("Paris")
                        }
                    assertEquals(size == 262144, result.isSuccess)
                    assertTrue(body.closed)
                } finally {
                    service.close()
                }
            }
            val body =
                Body("{}".toByteArray(), -1) {
                    throw IOException("Fixture premature EOF")
                }
            val service =
                LocationSearch(
                    client {
                        response(it, body)
                    },
                )
            try {
                assertTrue(
                    runCatching {
                        service.search("Paris")
                    }.isFailure,
                )
                assertTrue(body.closed)
            } finally {
                service.close()
            }
        }

    @Test fun callerCancellationCancelsCallWhileHeadersOrBodyAreBlocked() =
        runBlocking {
            for (blockBody in listOf(false, true)) {
                val entered = CountDownLatch(1)
                val release = CountDownLatch(1)
                var call: Call? = null
                var body: Body? = null

                fun waitForCancellation() {
                    entered.countDown()
                    val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
                    while (release.count > 0 && call?.isCanceled() != true && System.nanoTime() < end)Thread.sleep(5)
                    if (call?.isCanceled() == true) throw IOException("Fixture call cancelled")
                }
                val service =
                    LocationSearch(
                        client { chain ->
                            call = chain.call()
                            if (!blockBody) waitForCancellation()
                            val value =
                                Body("{}".toByteArray(), -1) {
                                    if (blockBody) waitForCancellation()
                                }
                            body = value
                            response(chain, value)
                        },
                    )
                val operation =
                    launch(Dispatchers.Default) {
                        service.search("Paris")
                    }
                try {
                    assertTrue(entered.await(2, TimeUnit.SECONDS))
                    operation.cancel()
                    withTimeout(1000) {
                        operation.join()
                    }
                    assertTrue(call?.isCanceled() == true)
                    if (blockBody) assertTrue(body?.closed == true)
                } finally {
                    release.countDown()
                    operation.cancelAndJoin()
                    service.close()
                }
            }
        }
}
