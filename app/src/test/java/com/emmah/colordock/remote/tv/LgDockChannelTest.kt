package com.emmah.colordock.remote.tv

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class LgDockChannelTest {
    private class Connection : LgDockChannel.Connection {
        override val lock = Any()
        override var token: Any? = Any()
        override var ready = true
        override val state = MutableStateFlow(TvState(TvConnectionState.Ready))
        var allowed = true
        var sentSuccessfully = true
        val sent = mutableListOf<JSONObject>()
        val unsubscribed = mutableListOf<Pair<String, Any?>>()
        val callbacks = mutableListOf<(JSONObject?) -> Unit>()
        var reply: JSONObject? = connected()
        var replyImmediately = true

        override fun requirePermission() {
            if (!allowed) throw SecurityException("fixture")
        }

        override fun send(text: String): Boolean {
            sent.add(JSONObject(text))
            return sentSuccessfully
        }

        override fun subscribe(
            endpoint: String,
            params: JSONObject,
            callback: (JSONObject?) -> Unit,
        ): String {
            assertEquals("webapp/connectToApp", endpoint)
            assertEquals("com.emmah.colordock", params.getString("appId"))
            callbacks.add(callback)
            if (replyImmediately) callback(reply)
            return "subscription-${callbacks.size}"
        }

        override fun unsubscribe(
            id: String,
            origin: Any?,
        ) {
            unsubscribed.add(id to origin)
        }
    }

    @Test fun joinRoutesOnlyMatchingPeerMessagesAndReplacesSubscription() =
        runTest {
            val connection = Connection()
            val channel = LgDockChannel(connection)
            assertSame(connection.state, channel.state)
            assertSame(connection.token, channel.connectionToken)
            channel.receive(JSONObject().put("from", "com.emmah.colordock"))
            channel.joinDockChannel()
            assertTrue(channel.dockAvailable.value)
            channel.sendDock(JSONObject().put("action", "test"), connection.token)
            assertEquals("p2p", connection.sent.single().getString("type"))
            assertEquals("com.emmah.colordock", connection.sent.single().getString("to"))
            val incoming = async(start = CoroutineStart.UNDISPATCHED) { channel.dockMessages.first() }
            channel.receive(JSONObject().put("from", "wrong").put("payload", JSONObject()))
            channel.receive(JSONObject().put("from", "com.emmah.colordock"))
            assertFalse(incoming.isCompleted)
            channel.receive(
                JSONObject().put("from", "com.emmah.colordock").put("payload", JSONObject().put("value", 7)),
            )
            assertEquals(7, incoming.await().getInt("value"))
            channel.joinDockChannel()
            assertEquals("subscription-1", connection.unsubscribed.single().first)
            channel.unavailable()
            assertFalse(channel.dockAvailable.value)
            channel.reset()
            assertThrows(IOException::class.java) { channel.sendDock(JSONObject()) }
        }

    @Test fun sendingRequiresPermissionCurrentSessionAndReadyTransport() =
        runTest {
            val connection = Connection()
            val channel = LgDockChannel(connection)
            assertThrows(IOException::class.java) { channel.sendDock(JSONObject()) }
            channel.joinDockChannel()
            assertThrows(IOException::class.java) { channel.sendDock(JSONObject(), Any()) }
            connection.allowed = false
            assertThrows(SecurityException::class.java) { channel.sendDock(JSONObject()) }
            connection.allowed = true
            connection.ready = false
            assertThrows(IOException::class.java) { channel.sendDock(JSONObject()) }
            assertTrue(connection.sent.isEmpty())
            connection.ready = true
            connection.sentSuccessfully = false
            assertThrows(IOException::class.java) { channel.sendDock(JSONObject()) }
        }

    @Test fun missingAndInvalidJoinResponsesFailAndReleaseSubscription() =
        runTest {
            for (reply in listOf(null, JSONObject().put("state", "DISCONNECTED"), connected().put("appId", "other"))) {
                val connection = Connection().apply { this.reply = reply }
                val channel = LgDockChannel(connection)
                assertTrue(runCatching { channel.joinDockChannel() }.exceptionOrNull() is IOException)
                assertFalse(channel.dockAvailable.value)
                assertEquals(1, connection.unsubscribed.size)
            }
            val connection = Connection().apply { token = null }
            assertTrue(runCatching { LgDockChannel(connection).joinDockChannel() }.exceptionOrNull() is IOException)
            assertTrue(connection.callbacks.isEmpty())
        }

    @Test fun cancelledOldJoinDoesNotUnsubscribeReplacementSession() =
        runTest {
            val connection = Connection().apply { replyImmediately = false }
            val channel = LgDockChannel(connection)
            val origin = connection.token
            val joining = async { channel.joinDockChannel() }
            runCurrent()
            connection.token = Any()
            joining.cancelAndJoin()
            assertTrue(joining.getCompletionExceptionOrNull() is CancellationException)
            assertSame(origin, connection.unsubscribed.single().second)
            connection.replyImmediately = true
            channel.joinDockChannel()
            assertTrue(channel.dockAvailable.value)
        }

    companion object {
        private fun connected() = JSONObject().put("state", "CONNECTED").put("appId", "com.emmah.colordock")
    }
}
