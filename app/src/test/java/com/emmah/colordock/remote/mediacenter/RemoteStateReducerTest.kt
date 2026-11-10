package com.emmah.colordock.remote.mediacenter

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal object RecoveryFixture {
    private val fixtures =
        JSONObject(
            requireNotNull(javaClass.getResource("/mediacenter-v1.json")).readText(),
        ).getJSONObject("fixtures")

    fun hello(
        epoch: String = "epoch",
        sequence: String = "0",
    ): String =
        JSONObject(fixtures.getJSONObject("hello").toString())
            .put("deviceId", "server")
            .put("serverEpoch", epoch)
            .put("snapshotSequence", sequence)
            .toString()

    fun snapshot(
        epoch: String = "epoch",
        sequence: String = "0",
    ): String =
        JSONObject(fixtures.getJSONObject("emptySnapshot").toString())
            .apply {
                put("serverEpoch", epoch)
                put("sequence", sequence)
                for (key in keys()) {
                    val topic = optJSONObject(key)
                    if (topic != null && topic.has("revision")) topic.put("revision", "0")
                }
                getJSONObject("connection").put("serverDeviceId", "server")
                getJSONObject("activity").put("mode", "video").put("sessionId", "session")
                getJSONObject("player").put("phase", "playing").put("sessionId", "session")
            }.toString()

    fun event(
        sequence: String,
        revision: String,
        epoch: String = "epoch",
    ): String =
        JSONObject()
            .put("serverEpoch", epoch)
            .put("sequence", sequence)
            .put(
                "topic",
                "connection",
            ).put(
                "data",
                JSONObject().put("revision", revision).put("serverDeviceId", "server").put("displayName", "New name"),
            ).toString()
}

class RemoteStateReducerTest {
    @Test fun transportHelloCannotEnableMutationsBeforeActualNativeSnapshot() {
        val reducer = RemoteStateReducer("server")
        assertEquals(StateReduction.Applied, reducer.apply(RecoveryFixture.hello()))
        assertFalse(reducer.ready)
        assertEquals(StateReduction.Applied, reducer.apply(RecoveryFixture.snapshot()))
        assertTrue(reducer.ready)
        reducer.invalidate()
        assertFalse(reducer.ready)
    }

    @Test fun comparesFullUnsignedCountersNumericallyAndAppliesContiguousEvents() {
        val reducer = RemoteStateReducer("server")
        reducer.apply(RecoveryFixture.hello(sequence = "9223372036854775808"))
        assertEquals(StateReduction.Applied, reducer.apply(RecoveryFixture.snapshot(sequence = "9223372036854775808")))
        assertEquals(
            StateReduction.Applied,
            reducer.apply(RecoveryFixture.event("9223372036854775809", "18446744073709551615")),
        )
        assertEquals("New name", reducer.snapshot!!.connection.displayName)
    }

    @Test fun rejectsGapAndDisablesMutationsWithoutApplyingDelta() {
        val reducer = ready()
        assertEquals(StateReduction.ResyncRequired, reducer.apply(RecoveryFixture.event("2", "2")))
        assertFalse(reducer.ready)
    }

    @Test fun rejectsSequenceAndTopicRevisionRegressions() {
        val reducer = ready()
        assertEquals(StateReduction.Applied, reducer.apply(RecoveryFixture.event("1", "9")))
        assertEquals(StateReduction.ResyncRequired, reducer.apply(RecoveryFixture.event("2", "8")))
        assertFalse(reducer.ready)
        val other = ready()
        assertEquals(StateReduction.ResyncRequired, other.apply(RecoveryFixture.event("0", "9")))
    }

    @Test fun rejectsWrongSnapshotIdentityAndEpochAndProtocolVersion() {
        val wrong = ready()
        val snapshot =
            JSONObject(RecoveryFixture.snapshot()).apply {
                getJSONObject("connection").put("serverDeviceId", "other")
            }
        assertEquals(StateReduction.IdentityChanged, wrong.apply(snapshot.toString()))
        assertFalse(wrong.ready)
        assertEquals(StateReduction.ResyncRequired, ready().apply(RecoveryFixture.snapshot(epoch = "other")))
        assertEquals(
            StateReduction.VersionMismatch,
            RemoteStateReducer("server").apply(JSONObject(RecoveryFixture.hello()).put("v", 2).toString()),
        )
    }

    @Test fun buffersEventsBeforeSnapshotAndAppliesOnlyUpdatesAfterItsSequence() {
        val reducer = RemoteStateReducer("server")
        reducer.apply(RecoveryFixture.hello())
        assertEquals(StateReduction.Applied, reducer.apply(RecoveryFixture.event("1", "9")))
        assertFalse(reducer.ready)
        assertEquals(StateReduction.Applied, reducer.apply(RecoveryFixture.snapshot()))
        assertTrue(reducer.ready)
        assertEquals("9", reducer.snapshot!!.connection.revision)
    }

    @Test fun newEpochRequiresFreshNativeTruthRatherThanCachedState() {
        val reducer = ready()
        assertEquals(StateReduction.Applied, reducer.apply(RecoveryFixture.hello(epoch = "new")))
        assertFalse(reducer.ready)
        assertEquals(StateReduction.ResyncRequired, reducer.apply(RecoveryFixture.event("1", "9")))
    }

    @Test fun rejectsNoncanonicalAndOverflowEventCountersRatherThanNormalizingThem() {
        for (counter in listOf("+1", "01", "18446744073709551616")) {
            val reducer = ready()
            assertEquals(counter, StateReduction.ResyncRequired, reducer.apply(RecoveryFixture.event(counter, "9")))
            assertFalse(reducer.ready)
        }
    }

    private fun ready() =
        RemoteStateReducer("server").apply {
            apply(RecoveryFixture.hello())
            apply(RecoveryFixture.snapshot())
        }
}
