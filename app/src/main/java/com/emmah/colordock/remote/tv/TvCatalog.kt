package com.emmah.colordock.remote.tv

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.IOException

private const val MAX_CHANNELS = 2000

internal class TvCatalog(
    private val connection: Connection,
) {
    interface Connection {
        val lock: Any
        val session: LgSession?
        val commandLock: Mutex
        val state: TvConnectionState

        suspend fun request(
            endpoint: String,
            params: JSONObject = JSONObject(),
            origin: LgSession?,
            beforeSend: () -> Unit = {},
        ): JSONObject

        suspend fun action(block: suspend () -> Unit): TvResult<Unit>

        fun permissionRequired(): TvResult.Failure
    }

    private var channelList = emptyList<TvChannel>()
    private var appIds = emptySet<String>()
    private var catalogGeneration = 0L

    fun invalidate() {
        appIds = emptySet()
        catalogGeneration++
    }

    fun reset() {
        channelList = emptyList()
        invalidate()
    }

    private fun unavailable() = TvResult.Failure(TvErrorCode.Unavailable, "TV unavailable. Reconnect to continue.")

    suspend fun channels(): TvResult<List<TvChannel>> {
        val origin = connection.session ?: return unavailable()
        return try {
            val list =
                connection.request(TvRead.Channels.endpoint, origin = origin).optJSONArray("channelList")
                    ?: throw IOException("The channel list is unavailable.")
            val parsed =
                (0 until minOf(list.length(), MAX_CHANNELS)).mapNotNull { index ->
                    list.optJSONObject(index)?.let(::parseChannel)
                }
            synchronized(connection.lock) {
                if (connection.session !== origin ||
                    connection.state != TvConnectionState.Ready
                ) {
                    return@synchronized unavailable()
                }
                channelList = parsed
                TvResult.Success(parsed)
            }
        } catch (
            cancelled: CancellationException,
        ) {
            throw cancelled
        } catch (_: SecurityException) {
            connection.permissionRequired()
        } catch (_: Exception) {
            synchronized(connection.lock) {
                if (connection.session === origin) channelList = emptyList()
            }
            TvResult.Failure(TvErrorCode.Unavailable, "The channel list is unavailable. Open Live TV and try again.")
        }
    }

    suspend fun tune(channelId: String): TvResult<Unit> {
        val origin = connection.session
        return connection.commandLock.withLock {
            if (origin == null || origin !== connection.session) return@withLock unavailable()
            if (channelList.none {
                    it.id == channelId
                }
            ) {
                return@withLock TvResult.Failure(
                    TvErrorCode.InvalidRequest,
                    "Choose a channel from your TV's channel list.",
                )
            }
            connection.action {
                connection.request(
                    "system.launcher/launch",
                    JSONObject().put("id", "com.webos.app.livetv"),
                    origin = origin,
                )
                connection.request("tv/openChannel", JSONObject().put("channelId", channelId), origin = origin)
            }
        }
    }

    suspend fun apps(): TvResult<List<TvApp>> {
        val (origin, generation) =
            synchronized(connection.lock) {
                val current = connection.session ?: return unavailable()
                current to ++catalogGeneration
            }
        return try {
            val installed = connection.request("com.webos.applicationManager/listApps", origin = origin)
            // Validate installation evidence before using optional launcher metadata.
            parseTvApps(installed, null)
            val points =
                try {
                    connection.request("com.webos.applicationManager/listLaunchPoints", origin = origin)
                } catch (
                    cancelled: CancellationException,
                ) {
                    throw cancelled
                } catch (_: IOException) {
                    null
                }
            val parsed = parseTvApps(installed, points)
            synchronized(connection.lock) {
                if (connection.session !== origin || connection.state != TvConnectionState.Ready ||
                    generation != catalogGeneration
                ) {
                    return@synchronized unavailable()
                }
                appIds =
                    parsed
                        .map {
                            it.id
                        }.toSet()
                TvResult.Success(parsed)
            }
        } catch (
            cancelled: CancellationException,
        ) {
            throw cancelled
        } catch (
            _: SecurityException,
        ) {
            connection.permissionRequired()
        } catch (
            _: Exception,
        ) {
            TvResult.Failure(TvErrorCode.Unavailable, "TV apps could not be loaded. Try again.")
        }
    }

    suspend fun launchApp(appId: String): TvResult<Unit> {
        val origin = connection.session
        return connection.commandLock.withLock {
            synchronized(connection.lock) {
                if (origin == null || origin !== connection.session ||
                    connection.state != TvConnectionState.Ready
                ) {
                    return@withLock unavailable()
                }
                if (appId !in
                    appIds
                ) {
                    return@withLock TvResult.Failure(
                        TvErrorCode.InvalidRequest,
                        "This app is no longer available in the current TV app list.",
                    )
                }
            }
            connection.action {
                connection.request(
                    "system.launcher/launch",
                    JSONObject().put("id", appId),
                    origin = origin,
                    beforeSend = {
                        if (appId !in appIds) throw IOException("App is no longer available")
                    },
                )
                requireCurrentSession(origin)
            }
        }
    }

    private fun requireCurrentSession(origin: LgSession?) {
        synchronized(connection.lock) {
            if (connection.session !== origin || connection.state != TvConnectionState.Ready) {
                throw IOException("TV unavailable")
            }
        }
    }
}
