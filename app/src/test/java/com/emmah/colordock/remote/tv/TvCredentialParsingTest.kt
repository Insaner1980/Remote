package com.emmah.colordock.remote.tv

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TvCredentialParsingTest {
    private fun valid() =
        JSONObject()
            .put("address", "192.168.1.9")
            .put("deviceId", "fixture-device")
            .put("name", "Fixture TV")
            .put("fingerprint", "ab".repeat(32))
            .put("clientKey", "fixture-key")

    @Test fun acceptsExistingFormatAndRejectsCoercedOrInvalidTrustFields() {
        assertEquals("fixture-key", TvCredentialStore.decode(valid().toString().toByteArray()).clientKey)
        val invalid =
            listOf(
                "address" to "8.8.8.8",
                "deviceId" to true,
                "deviceId" to "x".repeat(201),
                "name" to "x".repeat(121),
                "fingerprint" to "invalid",
                "clientKey" to 42,
                "clientKey" to " ",
                "clientKey" to "x".repeat(4097),
            )
        for ((field, value) in invalid) {
            assertThrows(Exception::class.java) {
                TvCredentialStore.decode(valid().put(field, value).toString().toByteArray())
            }
        }
    }
}
