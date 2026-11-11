package com.emmah.colordock.remote.mediacenter.protocol

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsProtocolTest {
    private fun fixture(): JSONObject {
        val resource = checkNotNull(javaClass.classLoader?.getResource("mediacenter-v1.json"))
        return JSONObject(resource.readText()).getJSONObject("fixtures")
    }

    @Test fun optionalSettingsReadsPreserveLegacyAndNativeObservations() {
        val fixtures = fixture()
        val legacy = fixtures.getJSONObject("settingsLegacy")
        val reply =
            RemoteJson.parseReply(
                MethodName.SETTINGS_GET,
                """{"v":1,"id":"settings","ok":true,"result":$legacy}""",
            )
        assertEquals(null, ((reply as McReply.Success).result as RemoteResult.SettingsGet).value.servicePreferences)
        val settings = fixtures.getJSONArray("replies")
        val entry =
            (0 until settings.length())
                .map { settings.getJSONObject(it) }
                .first { it.getString("method") == "settings.get" }
        val current =
            RemoteJson.parseReply(
                MethodName.SETTINGS_GET,
                entry.getJSONObject("reply").toString(),
            ) as McReply.Success
        val value = (current.result as RemoteResult.SettingsGet).value
        assertEquals("Netflix", value.servicePreferences?.single()?.label)
        assertTrue(value.servicePreferences?.single()?.vpnRequired == true)
        assertEquals(DiscoveryObservation.ANNOUNCED, value.connectionDiagnostics?.discovery)
    }

    @Test fun pairingQrIsBoundedSquareAndRedacted() {
        val fixture = fixture().getJSONObject("pairingQrReply")
        val parsed = RemoteJson.parseReply(MethodName.PAIRING_BEGIN, fixture.toString()) as McReply.Success
        val ready = (parsed.result as RemoteResult.PairingBegin).value as PairingPreparation.Ready
        assertEquals(21, ready.qrRows?.size)
        assertFalse(ready.toString().contains("000000000000000000000"))
        assertFalse(ready.toString().contains(ready.candidate.secret))
        val invalid =
            listOf(
                List(20) { "0".repeat(20) },
                List(21) { "0".repeat(22) },
                List(21) { "x".repeat(21) },
                List(178) { "0".repeat(178) },
            )
        for (rows in invalid) {
            fixture.getJSONObject("result").put("qrRows", org.json.JSONArray(rows))
            assertThrows(IllegalArgumentException::class.java) {
                RemoteJson.parseReply(MethodName.PAIRING_BEGIN, fixture.toString())
            }
        }
    }
}
