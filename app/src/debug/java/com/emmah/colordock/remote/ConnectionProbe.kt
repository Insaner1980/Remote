package com.emmah.colordock.remote

import com.emmah.colordock.remote.tv.LgTvClient
import com.emmah.colordock.remote.tv.TvRead
import com.emmah.colordock.remote.tv.TvTarget
import java.net.URI

/** Read-only diagnostics. Never returns credentials or token-bearing socket paths. */
object ConnectionProbe {
    suspend fun read(client: LgTvClient, target: TvTarget): Map<String, String> = buildMap {
        for (query in TvRead.entries) {
            try {
                val reply = client.read(query)
                val summary = when (query) {
                    TvRead.Volume -> "fields=${reply.keys().asSequence().joinToString(",")}; volumeStatusFields=${reply.optJSONObject("volumeStatus")?.keys()?.asSequence()?.joinToString(",")}"
                    TvRead.Channels -> "count=${reply.optJSONArray("channelList")?.length() ?: 0}"
                    TvRead.CurrentChannel -> "channelId=${reply.has("channelId")}; number=${reply.has("channelNumber")}"
                    TvRead.PointerSocket -> {
                        val url = reply.optString("socketPath")
                        val uri = URI(url)
                        "scheme=${uri.scheme}; sameHost=${uri.host == target.address}; port=${uri.port}; allowed=${target.allowsPointer(url)}"
                    }
                }
                put(query.name, summary)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                put(query.name, "NOT AVAILABLE")
            }
        }
    }
}
