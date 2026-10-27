package com.emmah.colordock.remote.tv

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class TvCatalogTest {
    private class Connection : TvCatalog.Connection {
        override val lock = Any()
        override var session: LgSession? = LgSession()
        override val commandLock = Mutex()
        override var state = TvConnectionState.Ready
        val requests = mutableListOf<Pair<String, JSONObject>>()
        var response: (String) -> JSONObject = { endpoint ->
            when (endpoint) {
                TvRead.Channels.endpoint -> {
                    JSONObject(
                        """{"channelList":[{"channelId":"one","channelNumber":"1","channelName":"First"},
                            null,{}, {"channelId":"","channelNumber":"2"}]}""",
                    )
                }

                "com.webos.applicationManager/listApps" -> {
                    JSONObject(
                        """{"apps":[{"id":"netflix","title":"Netflix","visible":true}]}""",
                    )
                }

                "com.webos.applicationManager/listLaunchPoints" -> {
                    JSONObject().put("launchPoints", JSONArray())
                }

                else -> {
                    JSONObject()
                }
            }
        }
        var beforeSendAction: () -> Unit = {}

        override suspend fun request(
            endpoint: String,
            params: JSONObject,
            origin: LgSession?,
            beforeSend: () -> Unit,
        ): JSONObject {
            beforeSendAction()
            beforeSend()
            requests.add(endpoint to params)
            return response(endpoint)
        }

        override suspend fun action(block: suspend () -> Unit): TvResult<Unit> =
            try {
                block()
                TvResult.Success(Unit)
            } catch (_: IOException) {
                TvResult.Failure(TvErrorCode.Unavailable, "fixture")
            }

        override fun permissionRequired() = TvResult.Failure(TvErrorCode.PermissionRequired, "permission")
    }

    @Test fun channelsFilterMalformedRecordsAndTuneOnlyKnownChannels() =
        runTest {
            val connection = Connection()
            val catalog = TvCatalog(connection)
            assertTrue(catalog.tune("one") is TvResult.Failure)
            val loaded = catalog.channels() as TvResult.Success
            assertEquals(listOf(TvChannel("one", "1", "First")), loaded.value)
            assertTrue(catalog.tune("one") is TvResult.Success)
            assertEquals(
                listOf("system.launcher/launch", "tv/openChannel"),
                connection.requests.takeLast(2).map { it.first },
            )
            assertEquals(
                "one",
                connection.requests
                    .last()
                    .second
                    .getString("channelId"),
            )
            catalog.reset()
            assertTrue(catalog.tune("one") is TvResult.Failure)
            connection.session = null
            assertTrue(catalog.channels() is TvResult.Failure)
            assertTrue(catalog.tune("one") is TvResult.Failure)
            assertTrue(catalog.apps() is TvResult.Failure)
            assertTrue(catalog.launchApp("netflix") is TvResult.Failure)
        }

    @Test fun channelsDiscardRepliesFromChangedOrUnreadyConnections() =
        runTest {
            for (replace in listOf(true, false)) {
                val connection = Connection()
                val original = connection.response
                connection.response = {
                    if (replace) connection.session = LgSession() else connection.state = TvConnectionState.Disconnected
                    original(it)
                }
                assertTrue(TvCatalog(connection).channels() is TvResult.Failure)
            }
        }

    @Test fun channelErrorsClearCachedListAndCancellationPropagates() =
        runTest {
            val connection = Connection()
            val catalog = TvCatalog(connection)
            catalog.channels()
            connection.response = { JSONObject() }
            assertTrue(catalog.channels() is TvResult.Failure)
            assertTrue(catalog.tune("one") is TvResult.Failure)
            connection.response = { throw SecurityException() }
            assertEquals(TvErrorCode.PermissionRequired, (catalog.channels() as TvResult.Failure).code)
            connection.response = { throw CancellationException() }
            assertTrue(runCatching { catalog.channels() }.exceptionOrNull() is CancellationException)
        }

    @Test fun installedAppsWorkWithoutOptionalLauncherMetadataAndInvalidateBeforeLaunch() =
        runTest {
            val connection = Connection()
            val original = connection.response
            connection.response = {
                if (it.endsWith("listLaunchPoints")) throw IOException("optional")
                original(it)
            }
            val catalog = TvCatalog(connection)
            assertEquals("netflix", (catalog.apps() as TvResult.Success).value.single().id)
            assertTrue(catalog.launchApp("netflix") is TvResult.Success)
            assertTrue(catalog.launchApp("unknown") is TvResult.Failure)
            connection.beforeSendAction = { catalog.invalidate() }
            assertTrue(catalog.launchApp("netflix") is TvResult.Failure)
        }

    @Test fun appRepliesCannotPublishAcrossSessionOrCatalogChanges() =
        runTest {
            for (mode in 0..2) {
                val connection = Connection()
                val catalog = TvCatalog(connection)
                val original = connection.response
                connection.response = {
                    if (it.endsWith("listLaunchPoints")) {
                        when (mode) {
                            0 -> connection.session = LgSession()
                            1 -> connection.state = TvConnectionState.Disconnected
                            else -> catalog.invalidate()
                        }
                    }
                    original(it)
                }
                assertTrue(catalog.apps() is TvResult.Failure)
            }
        }

    @Test fun appErrorsAndPermissionDenialDoNotBecomeSuccessfulEmptyLists() =
        runTest {
            val connection = Connection()
            val catalog = TvCatalog(connection)
            connection.response = { throw IOException() }
            assertTrue(catalog.apps() is TvResult.Failure)
            connection.response = { throw SecurityException() }
            assertEquals(TvErrorCode.PermissionRequired, (catalog.apps() as TvResult.Failure).code)
            connection.response = { throw CancellationException() }
            assertTrue(runCatching { catalog.apps() }.exceptionOrNull() is CancellationException)
        }

    @Test fun sessionChangeDuringLaunchCannotReportSuccess() =
        runTest {
            val connection = Connection()
            val catalog = TvCatalog(connection)
            catalog.apps()
            connection.response = {
                connection.session = LgSession()
                JSONObject()
            }
            assertTrue(catalog.launchApp("netflix") is TvResult.Failure)
            connection.state = TvConnectionState.Disconnected
            assertTrue(catalog.launchApp("netflix") is TvResult.Failure)
        }
}
