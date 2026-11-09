package com.emmah.colordock.remote.mediacenter.protocol

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RemoteProtocolTest {
    private fun pause() =
        JSONObject(
            """
{
  "v": 1,
  "id": "request-1",
  "method": "player.pause",
  "params": {
    "sessionId": "session-1",
    "paused": true
  },
  "expected": {
    "serverEpoch": "epoch-1",
    "sessionId": "session-1"
  }
}
            """.trimIndent(),
        )

    @Test
    fun rejectsUnknownVersionAndMethod() {
        assertThrows(IllegalArgumentException::class.java) { RemoteJson.parseRequest(pause().put("v", 2).toString()) }
        assertThrows(IllegalArgumentException::class.java) {
            RemoteJson.parseRequest(pause().put("method", "shell.exec").toString())
        }
    }

    @Test
    fun requiresEpochAndMatchingSession() {
        val noEpoch = pause().apply { getJSONObject("expected").remove("serverEpoch") }
        assertThrows(IllegalArgumentException::class.java) { RemoteJson.parseRequest(noEpoch.toString()) }
        val wrongSession = pause().apply { getJSONObject("expected").put("sessionId", "different") }
        assertThrows(IllegalArgumentException::class.java) { RemoteJson.parseRequest(wrongSession.toString()) }
    }

    @Test
    fun rejectsInvalidVolumeAndCoercedBoolean() {
        for (volume in listOf(-1, 101, "50")) {
            val request =
                pause()
                    .put(
                        "method",
                        "player.volume",
                    ).put("params", JSONObject().put("sessionId", "session-1").put("volume", volume))
            assertThrows(IllegalArgumentException::class.java) { RemoteJson.parseRequest(request.toString()) }
        }
        val coerced = pause().apply { getJSONObject("params").put("paused", "true") }
        assertThrows(IllegalArgumentException::class.java) { RemoteJson.parseRequest(coerced.toString()) }
    }

    @Test
    fun permitsUnknownOptionalFields() {
        RemoteJson.parseRequest(pause().put("futureOptional", 1).toString())
    }

    @Test
    fun limitsUnicodeCodePointsAndCompleteMessageBytes() {
        val request = JSONObject("""{"v":1,"id":"r","method":"library.query","params":{"kind":"video","query":""}}""")
        request.getJSONObject("params").put("query", "😀".repeat(200))
        RemoteJson.parseRequest(request.toString())
        request.getJSONObject("params").put("query", "😀".repeat(201))
        assertThrows(IllegalArgumentException::class.java) { RemoteJson.parseRequest(request.toString()) }
        assertThrows(IllegalArgumentException::class.java) {
            RemoteJson.parseRequest(pause().put("extra", "x".repeat(65536)).toString())
        }
    }

    @Test
    fun decimalRevisionsRetainPrecisionAndRejectNumbers() {
        val request =
            JSONObject(
                """
{
  "v": 1,
  "id": "r",
  "method": "queue.remove",
  "params": {
    "entryId": "track-1"
  },
  "expected": {
    "serverEpoch": "e",
    "sessionId": "s",
    "queueRevision": "9007199254740993"
  }
}
                """.trimIndent(),
            )
        RemoteJson.parseRequest(request.toString())
        for (revision in listOf(12, "", "01", "-1", "1.0", "18446744073709551616")) {
            request.getJSONObject("expected").put("queueRevision", revision)
            assertThrows(IllegalArgumentException::class.java) { RemoteJson.parseRequest(request.toString()) }
        }
    }

    private fun bundle(): JSONObject =
        JSONObject(
            requireNotNull(
                javaClass.classLoader?.getResourceAsStream("mediacenter-v1.json"),
            ).bufferedReader().use {
                it.readText()
            },
        )

    @Test
    fun everyMethodFixtureHasAStableTypedRoundtrip() {
        val requests = bundle().getJSONObject("fixtures").getJSONArray("requests")
        for (i in 0 until requests.length()) {
            val fixture = requests.getJSONObject(i)
            val request = RemoteJson.parseRequest(fixture.toString())
            assertEquals(fixture.getString("method"), request.command.method.wire)
            assertEquals(request, RemoteJson.parseRequest(RemoteJson.encodeRequest(request)))
        }
    }

    @Test
    fun decodesHelloAndSnapshotsWithoutLosingCounterPrecision() {
        val fixtures = bundle().getJSONObject("fixtures")
        val hello = RemoteJson.parseHello(fixtures.getJSONObject("hello").toString())
        assertEquals("9007199254740993", hello.snapshotSequence)
        val empty = RemoteJson.parseSnapshot(fixtures.getJSONObject("emptySnapshot").toString())
        assertEquals(ActivityMode.IDLE, empty.activity.mode)
        val populated = RemoteJson.parseSnapshot(fixtures.getJSONObject("populatedSnapshot").toString())
        assertEquals(12.5, populated.player.positionSeconds, 0.0)
        assertEquals(
            "Sample HDMI output",
            populated.outputs.outputs
                .single()
                .name,
        )
    }

    @Test
    fun textIsTransientAndNeverIncludedInDebugOutput() {
        val input =
            """
{
  "v": 1,
  "id": "r",
  "method": "browser.text",
  "params": {
    "browserSessionId": "b",
    "targetGeneration": "1",
    "text": "private-password"
  },
  "expected": {
    "serverEpoch": "e"
  }
}
            """.trimIndent()
        val request = RemoteJson.parseRequest(input)
        assertEquals(Delivery.TRANSIENT, request.command.delivery)
        org.junit.Assert.assertFalse(request.toString().contains("private-password"))
    }

    @Test
    fun sharedMalformedAndBoundaryCorpusIsRejected() {
        val fixtures = bundle().getJSONObject("fixtures")
        val malformed = fixtures.getJSONArray("malformedRequests")
        for (i in 0 until malformed.length()) {
            assertThrows(IllegalArgumentException::class.java) { RemoteJson.parseRequest(malformed.getString(i)) }
        }
        val invalid = fixtures.getJSONArray("invalidRequests")
        for (i in 0 until invalid.length()) {
            assertThrows(
                IllegalArgumentException::class.java,
            ) { RemoteJson.parseRequest(invalid.getJSONObject(i).toString()) }
        }
    }

    @Test
    fun allConfirmationPhasesAreReachable() {
        val phases = bundle().getJSONObject("fixtures").getJSONArray("confirmationPhases")
        for (i in 0 until phases.length()) RemoteJson.parseRequest(phases.getJSONObject(i).toString())
    }

    @Test
    fun methodCorrelatedRepliesHaveStableRoundtrips() {
        val fixtures = bundle().getJSONObject("fixtures")
        val replies = fixtures.getJSONArray("replies")
        for (i in 0 until replies.length()) {
            val entry = replies.getJSONObject(i)
            val method = MethodName.entries.single { it.wire == entry.getString("method") }
            val reply = RemoteJson.parseReply(method, entry.getJSONObject("reply").toString())
            assertEquals(reply, RemoteJson.parseReply(method, RemoteJson.encodeReply(reply)))
        }
        val mixed = fixtures.getJSONObject("accepted").put("error", JSONObject.NULL)
        assertThrows(
            IllegalArgumentException::class.java,
        ) { RemoteJson.parseReply(MethodName.PLAYER_PAUSE, mixed.toString()) }
        assertThrows(IllegalArgumentException::class.java) {
            RemoteJson.parseReply(MethodName.LIBRARY_ITEM, fixtures.getJSONObject("accepted").toString())
        }
    }

    private fun canonical(value: Any): String =
        when (value) {
            is JSONObject -> {
                value.keys().asSequence().toList().sorted().joinToString(",", "{", "}") {
                    JSONObject.quote(it) +
                        ":" +
                        canonical(value.get(it))
                }
            }

            is org.json.JSONArray -> {
                (0 until value.length()).joinToString(",", "[", "]") { canonical(value.get(it)) }
            }

            is String -> {
                JSONObject.quote(value)
            }

            else -> {
                value.toString()
            }
        }

    @Test
    fun schemaHashMatchesSharedCanonicalManifest() {
        val bundle = bundle()
        val bytes = canonical(bundle.getJSONObject("schema")).toByteArray(Charsets.UTF_8)
        val hash =
            java.security.MessageDigest
                .getInstance(
                    "SHA-256",
                ).digest(bytes)
                .joinToString("") { "%02x".format(it) }
        assertEquals(bundle.getJSONObject("manifest").getString("schemaSha256"), hash)
    }

    @Test
    fun idleQueueAndBoundedPlaylistEditsAreReachable() {
        val fixtures = bundle().getJSONObject("fixtures")
        for (key in listOf("idleQueueEdits", "playlistEdits")) {
            val requests = fixtures.getJSONArray(key)
            for (i in 0 until requests.length()) RemoteJson.parseRequest(requests.getJSONObject(i).toString())
        }
    }

    @Test
    fun pairingBoundaryRoundtripsAndRedactsSecrets() {
        val f = bundle().getJSONObject("fixtures")
        val candidate = RemoteJson.parsePairingCandidate(f.getJSONObject("pairingCandidate").toString())
        assertEquals(candidate, RemoteJson.parsePairingCandidate(RemoteJson.encodePairingCandidate(candidate)))
        val manual = RemoteJson.parseManualPairingCandidate(f.getJSONObject("manualPairingCandidate").toString())
        assertEquals(manual, RemoteJson.parseManualPairingCandidate(RemoteJson.encodeManualPairingCandidate(manual)))
        val request = RemoteJson.parsePairRequest(f.getJSONObject("pairRequest").toString())
        assertEquals(request, RemoteJson.parsePairRequest(RemoteJson.encodePairRequest(request)))
        val manualRequest = RemoteJson.parseManualPairRequest(f.getJSONObject("manualPairRequest").toString())
        assertEquals(
            manualRequest,
            RemoteJson.parseManualPairRequest(RemoteJson.encodeManualPairRequest(manualRequest)),
        )
        val challenge = RemoteJson.parseManualChallengeRequest(f.getJSONObject("manualChallengeRequest").toString())
        assertEquals(
            challenge,
            RemoteJson.parseManualChallengeRequest(RemoteJson.encodeManualChallengeRequest(challenge)),
        )
        val challengeResponse =
            RemoteJson.parseManualChallengeResponse(
                f.getJSONObject("manualChallengeResponse").toString(),
            )
        assertEquals(
            challengeResponse,
            RemoteJson.parseManualChallengeResponse(RemoteJson.encodeManualChallengeResponse(challengeResponse)),
        )
        val paired = RemoteJson.parsePairedMediacenter(f.getJSONObject("pairedMediacenter").toString())
        assertEquals(paired, RemoteJson.parsePairedMediacenter(RemoteJson.encodePairedMediacenter(paired)))
        org.junit.Assert.assertFalse(candidate.toString().contains("A".repeat(43)))
        org.junit.Assert.assertFalse(paired.toString().contains("B".repeat(43)))
    }

    @Test
    fun pairingBeginDeliversCandidateOnceAndRedactsIt() {
        val outcomes = bundle().getJSONObject("fixtures").getJSONArray("pairingBeginOutcomes")
        for (i in 0 until outcomes.length()) {
            val fixture = outcomes.getJSONObject(i)
            val reply = RemoteJson.parseReply(MethodName.PAIRING_BEGIN, fixture.toString())
            val encoded = RemoteJson.encodeReply(reply)
            assertEquals(reply, RemoteJson.parseReply(MethodName.PAIRING_BEGIN, encoded))
            org.junit.Assert.assertFalse(reply.toString().contains("A".repeat(43)))
            if (fixture.getJSONObject("result").getString("status") == "already_issued") {
                org.junit.Assert.assertFalse(encoded.contains("candidate"))
                org.junit.Assert.assertFalse(encoded.contains("secret"))
            }
        }
        val missing = outcomes.getJSONObject(1)
        missing.getJSONObject("result").remove("candidate")
        assertThrows(IllegalArgumentException::class.java) {
            RemoteJson.parseReply(MethodName.PAIRING_BEGIN, missing.toString())
        }
    }

    @Test
    fun staleConfirmationPreservesRequiredReplacementPreview() {
        val fixtures = bundle().getJSONObject("fixtures")
        val fixture = fixtures.getJSONObject("confirmationStale")
        val reply = RemoteJson.parseReply(MethodName.CONFIRMATIONS_GET, fixture.toString())
        val encoded = JSONObject(RemoteJson.encodeReply(reply))
        assertEquals(canonical(fixture), canonical(encoded))
        val failure = reply as McReply.Failure
        assertThrows(IllegalArgumentException::class.java) {
            RemoteJson.encodeReply(failure.copy(error = failure.error.copy(replacementPreview = null)))
        }
        assertEquals(
            "confirmation-fresh-2",
            encoded.getJSONObject("error").getJSONObject("replacementPreview").getString("confirmationId"),
        )
        assertEquals(reply, RemoteJson.parseReply(MethodName.CONFIRMATIONS_GET, encoded.toString()))
        val invalid = fixtures.getJSONArray("invalidReplies")
        for (i in 0 until invalid.length()) {
            assertThrows(IllegalArgumentException::class.java) {
                RemoteJson.parseReply(
                    MethodName.CONFIRMATIONS_GET,
                    invalid.getJSONObject(i).getJSONObject("reply").toString(),
                )
            }
        }
    }

    @Test
    fun operationCancellationAcceptsExactlyOneIdentity() {
        val fixtures = bundle().getJSONObject("fixtures")
        val forms = fixtures.getJSONArray("operationCancellationForms")
        for (i in 0 until forms.length()) {
            val request = RemoteJson.parseRequest(forms.getJSONObject(i).toString())
            assertEquals(request, RemoteJson.parseRequest(RemoteJson.encodeRequest(request)))
        }
        for (params in listOf(JSONObject(), JSONObject().put("requestId", "r").put("operationId", "o"))) {
            val request =
                JSONObject()
                    .put(
                        "v",
                        1,
                    ).put(
                        "id",
                        "r",
                    ).put(
                        "method",
                        "operations.cancel",
                    ).put("params", params)
                    .put("expected", JSONObject().put("serverEpoch", "e"))
            assertThrows(IllegalArgumentException::class.java) { RemoteJson.parseRequest(request.toString()) }
        }
    }
}
