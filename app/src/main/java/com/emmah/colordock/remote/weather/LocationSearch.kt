package com.emmah.colordock.remote.weather

import com.emmah.colordock.remote.companion.DockCodec
import com.emmah.colordock.remote.companion.WeatherLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONObject
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

class LocationSearch {
    private val client=OkHttpClient.Builder().callTimeout(10,TimeUnit.SECONDS).followRedirects(false).build()
    suspend fun search(query:String):List<WeatherLocation> = withContext(Dispatchers.IO) {
        require(query.trim().length in 2..120)
        val url="https://geocoding-api.open-meteo.com/v1/search".toHttpUrl().newBuilder().addQueryParameter("name",query.trim()).addQueryParameter("count","10").addQueryParameter("language","en").addQueryParameter("format","json").build()
        val call=client.newCall(Request.Builder().url(url).build())
        val job=coroutineContext[kotlinx.coroutines.Job]?.invokeOnCompletion {if(it!=null)call.cancel()}
        try {call.execute().use {response->
            require(response.isSuccessful)
            val body=response.body?:throw java.io.IOException("No location results.")
            require(body.contentLength()<=262144)
            val bytes=body.byteStream().readNBytes(262145);require(bytes.size<=262144);coroutineContext.ensureActive()
            val results=JSONObject(String(bytes,Charsets.UTF_8)).optJSONArray("results")?:return@use emptyList()
            (0 until minOf(results.length(),10)).mapNotNull {index->runCatching {
                val item=results.getJSONObject(index);val latitude=item.getDouble("latitude");val longitude=item.getDouble("longitude");val timezone=item.getString("timezone");ZoneId.of(timezone)
                require(latitude.isFinite()&&latitude in -90.0..90.0&&longitude.isFinite()&&longitude in -180.0..180.0)
                WeatherLocation(item.getString("name").take(120),item.optString("admin1").take(120),item.optString("country").take(120),latitude,longitude,timezone)
            }.getOrNull()}
        }}finally{job?.dispose()}
    }
    fun close(){client.dispatcher.cancelAll();client.dispatcher.executorService.shutdown();client.connectionPool.evictAll()}
}
