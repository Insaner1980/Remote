package com.emmah.colordock.remote.mediacenter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediacenterCredentialCodecTest {
    private fun record(
        address: String = "192.168.1.9",
        port: String = "47831",
    ) =
        """{"address":"$address","port":$port,"paired":{"serverDeviceId":"server","deviceId":"phone","credential":"${"A".repeat(
            43,
        )}","certificateFingerprint":"${"ab".repeat(32)}","scopes":["browse"]}}"""
            .toByteArray()

    @Test fun persistedCredentialsRevalidateEndpointAndTrustMaterialWithoutLeakingToString() {
        val value = MediacenterCredentialCodec.decode(record())
        assertEquals("192.168.1.9", value.endpoint.address)
        assertEquals("A".repeat(43), value.paired.credential)
        assertEquals(
            "ab".repeat(32),
            MediacenterCredentialCodec.decode(MediacenterCredentialCodec.encode(value)).paired.certificateFingerprint,
        )
        assertFalse(value.toString().contains("AAAA"))
        for (bytes in listOf(
            record("8.8.8.8"),
            record(port = "0"),
            record(port = "\"47831\""),
            byteArrayOf(),
            "x".repeat(70000).toByteArray(),
        )) {
            assertTrue(runCatching { MediacenterCredentialCodec.decode(bytes) }.isFailure)
        }
    }
}
