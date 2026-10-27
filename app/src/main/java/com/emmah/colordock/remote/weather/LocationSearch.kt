package com.emmah.colordock.remote.weather

import com.emmah.colordock.remote.companion.DockCodec
import com.emmah.colordock.remote.companion.WeatherLocation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

private const val SEARCH_TIMEOUT_SECONDS = 10L
private const val MAX_QUERY_LENGTH = 120
private const val MAX_RESPONSE_BYTES = 262144
private const val MAX_RESULTS = 10
private const val MAX_LATITUDE = 90.0
private const val MAX_LONGITUDE = 180.0

class LocationSearch internal constructor(
    private val client: OkHttpClient =
        OkHttpClient
            .Builder()
            .callTimeout(SEARCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .followRedirects(false)
            .build(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun search(query: String): List<WeatherLocation> =
        withContext(ioDispatcher) {
            require(query.trim().length in 2..MAX_QUERY_LENGTH)
            val url =
                "https://geocoding-api.open-meteo.com/v1/search"
                    .toHttpUrl()
                    .newBuilder()
                    .addQueryParameter(
                        "name",
                        query.trim(),
                    ).addQueryParameter("count", "10")
                    .addQueryParameter("language", "en")
                    .addQueryParameter("format", "json")
                    .build()
            val call = client.newCall(Request.Builder().url(url).build())
            coroutineScope {
                val cancellation =
                    launch(start = CoroutineStart.UNDISPATCHED) {
                        try {
                            awaitCancellation()
                        } finally {
                            call.cancel()
                        }
                    }
                try {
                    call.execute().use { response ->
                        require(response.isSuccessful)
                        val body = response.body
                        require(body.contentLength() <= MAX_RESPONSE_BYTES)
                        val bytes = body.byteStream().readNBytes(MAX_RESPONSE_BYTES + 1)
                        require(bytes.size <= MAX_RESPONSE_BYTES)
                        coroutineContext.ensureActive()
                        val results =
                            JSONObject(String(bytes, Charsets.UTF_8)).optJSONArray("results") ?: return@use emptyList()
                        (0 until minOf(results.length(), MAX_RESULTS)).mapNotNull { index ->
                            runCatching {
                                parseLocation(results.getJSONObject(index))
                            }.getOrNull()
                        }
                    }
                } finally {
                    cancellation.cancel()
                    coroutineContext.ensureActive()
                }
            }
        }

    private fun parseLocation(item: JSONObject): WeatherLocation {
        val latitude = (item.get("latitude") as Number).toDouble()
        val longitude = (item.get("longitude") as Number).toDouble()
        val timezone = item.get("timezone") as String
        ZoneId.of(timezone)
        require(
            latitude.isFinite() && latitude in -MAX_LATITUDE..MAX_LATITUDE &&
                longitude.isFinite() &&
                longitude in -MAX_LONGITUDE..MAX_LONGITUDE,
        )
        val name = (item.get("name") as String).take(MAX_QUERY_LENGTH)
        require(name.isNotBlank())

        fun optional(field: String) = if (item.isNull(field)) "" else (item.get(field) as String).take(MAX_QUERY_LENGTH)
        return WeatherLocation(
            name,
            optional("admin1"),
            optional("country"),
            latitude,
            longitude,
            timezone,
        )
    }

    fun close() {
        client.dispatcher.cancelAll()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }
}
