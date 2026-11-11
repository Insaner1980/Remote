package com.emmah.colordock.remote.mediacenter

import com.emmah.colordock.remote.mediacenter.protocol.McReply
import com.emmah.colordock.remote.mediacenter.protocol.MethodName
import com.emmah.colordock.remote.mediacenter.protocol.OperationStatus
import com.emmah.colordock.remote.mediacenter.protocol.PlayerPhase
import com.emmah.colordock.remote.mediacenter.protocol.RemoteJson
import com.emmah.colordock.remote.mediacenter.protocol.RemoteResult
import com.emmah.colordock.remote.mediacenter.protocol.WireErrorCode
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** Consumes freshly exported Rust bytes; absence is a failure, never a fixture fallback. */
class RustInteropTest {
    private fun corpus(): JSONObject =
        JSONObject(
            requireNotNull(javaClass.classLoader?.getResourceAsStream("mediacenter-rust-interop-v1.json")) {
                "Run the Task21 Rust exporter and guarded install before interoperability tests."
            }.bufferedReader().use { it.readText() },
        )

    private fun method(wire: String): MethodName = MethodName.entries.single { it.wire == wire }

    // Android JSONObject has no similar(). Match the existing protocol-test canonical pattern.
    private fun canonical(value: Any): String =
        when (value) {
            is JSONObject -> {
                value.keys().asSequence().toList().sorted().joinToString(",", "{", "}") {
                    JSONObject.quote(it) + ":" + canonical(value.get(it))
                }
            }

            is org.json.JSONArray -> {
                (0 until value.length()).joinToString(",", "[", "]") { canonical(value.get(it)) }
            }

            is String -> {
                JSONObject.quote(value)
            }

            is Number -> {
                value
                    .toString()
                    .toBigDecimal()
                    .stripTrailingZeros()
                    .toPlainString()
            }

            else -> {
                value.toString()
            }
        }

    @Test
    fun actualRustEnvelopesDecodeThroughClientCodecForEveryMethod() {
        val source = corpus()
        assertEquals("mediacenter-rust-interop-v1", source.getString("format"))
        val requests = source.getJSONArray("requests")
        assertEquals(88, requests.length())
        val expectedMethods = MethodName.entries.map { it.wire }.toSet()
        val requestMethods = (0 until requests.length()).map { requests.getJSONObject(it).getString("method") }.toSet()
        assertEquals(expectedMethods, requestMethods)
        for (index in 0 until requests.length()) {
            val entry = requests.getJSONObject(index)
            val decoded = RemoteJson.parseRequest(entry.getString("wire"))
            assertEquals(entry.getString("method"), decoded.command.method.wire)
            assertEquals(decoded, RemoteJson.parseRequest(RemoteJson.encodeRequest(decoded)))
            assertEquals(
                canonical(JSONObject(entry.getString("wire"))),
                canonical(JSONObject(RemoteJson.encodeRequest(decoded))),
            )
        }
        val replies = source.getJSONArray("replies")
        assertEquals(101, replies.length())
        val replyMethods = (0 until 88).map { replies.getJSONObject(it).getString("method") }.toSet()
        assertEquals(expectedMethods, replyMethods)
        for (index in 0 until replies.length()) {
            val entry = replies.getJSONObject(index)
            val decoded = RemoteJson.parseReply(method(entry.getString("method")), entry.getString("wire"))
            assertEquals(
                decoded,
                RemoteJson.parseReply(method(entry.getString("method")), RemoteJson.encodeReply(decoded)),
            )
            assertEquals(
                canonical(JSONObject(entry.getString("wire"))),
                canonical(JSONObject(RemoteJson.encodeReply(decoded))),
            )
        }
        val hello = RemoteJson.parseHello(source.getString("hello"))
        assertEquals("9007199254740993", hello.snapshotSequence)
        assertEquals("18446744073709551615", RemoteJson.parseHello(source.getString("helloMax")).snapshotSequence)
        val snapshots = source.getJSONArray("snapshots")
        assertEquals(2, snapshots.length())
        for (index in 0 until snapshots.length()) assertNotNull(RemoteJson.parseSnapshot(snapshots.getString(index)))
        assertEquals("18446744073709551615", RemoteJson.parseSnapshot(source.getString("snapshotMax")).sequence)
    }

    @Test
    fun malformedAndSemanticBoundaryMessagesFailClosedInActualClient() {
        val source = corpus()
        val invalid = source.getJSONArray("invalidRequests")
        assertEquals(44, invalid.length())
        for (index in 0 until invalid.length()) {
            assertThrows(IllegalArgumentException::class.java) { RemoteJson.parseRequest(invalid.getString(index)) }
        }
        val replies = source.getJSONArray("invalidReplies")
        assertEquals(2, replies.length())
        for (index in 0 until replies.length()) {
            val entry = replies.getJSONObject(index)
            assertThrows(IllegalArgumentException::class.java) {
                RemoteJson.parseReply(method(entry.getString("method")), entry.getString("wire"))
            }
        }
        val numeric = JSONObject(source.getString("snapshotMax")).put("sequence", 12)
        assertThrows(IllegalArgumentException::class.java) { RemoteJson.parseSnapshot(numeric.toString()) }
        val overflow = JSONObject(source.getString("helloMax")).put("snapshotSequence", "18446744073709551616")
        assertThrows(IllegalArgumentException::class.java) { RemoteJson.parseHello(overflow.toString()) }
    }

    @Test
    fun rustEventBeforeSnapshotIsAppliedAndSequenceGapRequiresResync() {
        val trace = corpus().getJSONObject("reducer")
        val reducer = RemoteStateReducer("opaque-1")
        assertEquals(StateReduction.Applied, reducer.apply(trace.getString("hello")))
        assertEquals(StateReduction.Applied, reducer.apply(trace.getString("event")))
        assertFalse(reducer.ready)
        assertEquals(StateReduction.Applied, reducer.apply(trace.getString("snapshot")))
        assertEquals("9007199254740994", reducer.snapshot?.sequence)
        assertEquals(PlayerPhase.PAUSED, reducer.snapshot?.player?.phase)
        assertEquals(StateReduction.ResyncRequired, reducer.apply(trace.getString("gap")))
        assertFalse(reducer.ready)
        assertEquals(StateReduction.Applied, reducer.apply(trace.getString("snapshot")))
        assertEquals(true, reducer.ready)
    }

    @Test
    fun realDispatcherLostResultRecoversSameOperationWithoutRepeatedNativeEffect() {
        val trace = corpus().getJSONObject("dispatcher")
        val request = RemoteJson.parseRequest(trace.getString("request"))
        assertEquals(MethodName.PLAYER_PAUSE, request.command.method)
        val accepted = RemoteJson.parseReply(MethodName.PLAYER_PAUSE, trace.getString("accepted"))
        val duplicate = RemoteJson.parseReply(MethodName.PLAYER_PAUSE, trace.getString("duplicate"))
        assertEquals(accepted, duplicate)
        val terminal = RemoteJson.parseReply(MethodName.OPERATIONS_GET, trace.getString("terminal"))
        assertEquals(true, terminal is McReply.Success)
        val acceptedState = ((accepted as McReply.Success).result as RemoteResult.PlayerPause).value
        val operation = ((terminal as McReply.Success).result as RemoteResult.OperationsGet).value
        assertEquals(acceptedState.operationId, operation.operationId)
        assertEquals(OperationStatus.SUCCEEDED, operation.status)
        assertEquals(1, trace.getInt("effectCount"))
        val conflict = RemoteJson.parseReply(MethodName.PLAYER_PAUSE, trace.getString("conflict"))
        assertEquals(WireErrorCode.REQUEST_ID_CONFLICT, (conflict as McReply.Failure).error.code)
    }
}
