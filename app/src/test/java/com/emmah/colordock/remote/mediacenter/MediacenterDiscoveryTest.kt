package com.emmah.colordock.remote.mediacenter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediacenterDiscoveryTest {
    @Test fun discoveryIsOnlyAnAddressHintForAlreadyPinnedDevice() {
        val attributes =
            mapOf(
                "v" to "1".toByteArray(),
                "id" to "fixture-server".toByteArray(),
                "name" to "Fixture".toByteArray(),
            )
        val candidates =
            MediacenterDiscovery.candidates(
                "_emma-mc._tcp.",
                attributes,
                listOf("192.168.1.9", "8.8.8.8", "fe80::2"),
                47831,
                "fixture-server",
            )
        assertEquals(2, candidates.size)
        assertTrue(
            MediacenterDiscovery
                .candidates(
                    "_emma-mc._tcp.",
                    attributes,
                    listOf("192.168.1.9"),
                    47831,
                    "other-server",
                ).isEmpty(),
        )
        assertTrue(
            MediacenterDiscovery
                .candidates(
                    "_http._tcp.",
                    attributes,
                    listOf("192.168.1.9"),
                    47831,
                    "fixture-server",
                ).isEmpty(),
        )
        assertTrue(
            MediacenterDiscovery
                .candidates(
                    "_emma-mc._tcp.",
                    attributes + ("v" to "2".toByteArray()),
                    listOf("192.168.1.9"),
                    47831,
                    "fixture-server",
                ).isEmpty(),
        )
    }
}
