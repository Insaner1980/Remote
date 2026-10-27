package com.emmah.colordock.remote.tv

import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LgTvClientTest {
    @Test fun ownRequestDeadlineIsAFailureWhileParentCancellationPropagates() =
        runTest {
            val own =
                runCatching {
                    awaitTvReply(kotlinx.coroutines.CompletableDeferred())
                }.exceptionOrNull()
            assertTrue(own is java.io.IOException)
            val parent =
                runCatching {
                    kotlinx.coroutines.withTimeout(100) {
                        awaitTvReply(kotlinx.coroutines.CompletableDeferred())
                    }
                }.exceptionOrNull()
            assertTrue(parent is kotlinx.coroutines.TimeoutCancellationException)
        }

    @Test fun unsuccessfulSsApPayloadFailsTheRequestWithoutDisconnectingOrdinaryControl() {
        val session = LgSession()
        val id = session.register()
        session.receive(
            JSONObject()
                .put(
                    "id",
                    id,
                ).put("type", "registered")
                .put("payload", JSONObject().put("client-key", "fixture-only")),
        )
        val request = session.request()
        session.receive(
            JSONObject()
                .put(
                    "id",
                    request.first,
                ).put("type", "response")
                .put("payload", JSONObject().put("returnValue", false)),
        )
        assertTrue(request.second.isCancelled)
        assertEquals(TvConnectionState.Ready, session.state)
    }

    @Test fun subscriptionsDeliverChangesUntilRemovedAndDisconnectDiscardsThem() {
        val session = LgSession()
        val values = mutableListOf<Boolean>()
        val id =
            session.subscribe { value ->
                value?.let {
                    values.add(it.getBoolean("mute"))
                }
            }
        for (value in listOf(
            false,
            true,
        )) {
            session.receive(
                JSONObject().put("type", "response").put("id", id).put("payload", JSONObject().put("mute", value)),
            )
        }
        session.cancel(id)
        session.receive(
            JSONObject().put("type", "response").put("id", id).put("payload", JSONObject().put("mute", false)),
        )
        assertEquals(listOf(false, true), values)
        val next =
            session.subscribe {
                values.add(false)
            }
        session.disconnect()
        session.receive(JSONObject().put("type", "response").put("id", next).put("payload", JSONObject()))
        assertEquals(2, values.size)
    }

    @Test
    fun appJoinWaitsForConnectedInsteadOfCompletingOnWaitingForApp() =
        runTest {
            val session = LgSession()
            val request =
                session.request {
                    it.optString("state") == "CONNECTED"
                }
            session.receive(
                JSONObject("""{"id":"${request.first}","type":"response","payload":{"state":"WAITING_FOR_APP"}}"""),
            )
            assertFalse(request.second.isCompleted)
            session.receive(
                JSONObject(
                    """{"id":"${request.first}","type":"response","payload":{"state":"CONNECTED",""" +
                        """"appId":"com.emmah.colordock"}}""",
                ),
            )
            assertEquals("com.emmah.colordock", request.second.await().getString("appId"))
        }

    @Test
    fun registrationResponse_doesNotBecomeReadyBeforeRegistered() {
        val session = LgSession()
        val id = session.register()
        session.receive(
            JSONObject("""{"id":"$id","type":"response","payload":{"pairingType":"PROMPT","returnValue":true}}"""),
        )
        assertEquals(TvConnectionState.Pairing, session.state)
        assertNull(session.registeredKey)
        session.receive(
            JSONObject("""{"id":"$id","type":"registered","payload":{"client-key":"fixture-only-not-a-device-key"}}"""),
        )
        assertEquals(TvConnectionState.Ready, session.state)
        assertEquals("fixture-only-not-a-device-key", session.registeredKey)
    }

    @Test
    fun requestResponses_areCorrelatedByIdWhenReceivedOutOfOrder() =
        runTest {
            val session = LgSession()
            val first = session.request()
            val second = session.request()
            assertNotEquals(first.first, second.first)
            session.receive(JSONObject("""{"id":"${second.first}","type":"response","payload":{"volume":12}}"""))
            assertFalse(first.second.isCompleted)
            assertEquals(12, second.second.await().getInt("volume"))
            session.receive(JSONObject("""{"id":"${first.first}","type":"response","payload":{"mute":false}}"""))
            assertFalse(first.second.await().getBoolean("mute"))
        }

    @Test
    fun unrelatedRegisteredMessage_doesNotAcceptAKey() {
        val session = LgSession()
        session.register()
        session.receive(JSONObject("""{"id":"unknown","type":"registered","payload":{"client-key":"unrelated"}}"""))
        assertEquals(TvConnectionState.Pairing, session.state)
        assertNull(session.registeredKey)
    }

    @Test
    fun rejectedRegistration_neverBecomesReady() {
        val session = LgSession()
        val id = session.register()
        session.receive(JSONObject("""{"id":"$id","type":"error","error":"403 User rejected pairing"}"""))
        assertEquals(TvConnectionState.Failed, session.state)
        assertNull(session.registeredKey)
    }

    @Test
    fun nonStringRegistrationKey_isRejected() {
        val session = LgSession()
        val id = session.register()
        session.receive(JSONObject("""{"id":"$id","type":"registered","payload":{"client-key":true}}"""))
        assertEquals(TvConnectionState.Failed, session.state)
        assertNull(session.registeredKey)
    }

    @Test
    fun disconnectFailsPendingRequestsAndDiscardsTheSessionKey() {
        val session = LgSession()
        val id = session.register()
        session.receive(JSONObject("""{"id":"$id","type":"registered","payload":{"client-key":"fixture-only"}}"""))
        val request = session.request()
        session.disconnect()
        assertTrue(request.second.isCancelled)
        assertNull(session.registeredKey)
        assertEquals(TvConnectionState.Disconnected, session.state)
    }
}
