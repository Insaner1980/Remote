package com.emmah.colordock.remote.mediacenter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MediacenterEndpointTest {
    @Test fun acceptsOnlyPrivateLiteralLanEndpoints() {
        for (address in listOf("192.168.1.9", "10.0.0.2", "172.31.255.2", "fd12::9", "fe80::9", "[fd12::9]")) {
            assertNotNull(address, MediacenterEndpoint.parse(address))
        }
        for (address in listOf(
            "8.8.8.8",
            "127.0.0.1",
            "0.0.0.0",
            "169.254.1.2",
            "172.32.0.1",
            "::1",
            "::",
            "ff02::1",
            "2001:4860::1",
            "::ffff:192.168.1.9",
            "example.com",
            "192.168.1.9/path",
            "user@192.168.1.9",
            "fe80::9%wlan0",
        )) {
            assertNull(address, MediacenterEndpoint.parse(address))
        }
        assertNull(MediacenterEndpoint.parse("192.168.1.9", 0))
        assertNull(MediacenterEndpoint.parse("192.168.1.9", 65536))
        assertEquals("fd12:0:0:0:0:0:0:9", MediacenterEndpoint.parse("[fd12::9]", 47832)!!.address)
        assertEquals(47832, MediacenterEndpoint.parse("10.0.0.2", 47832)!!.port)
    }

    @Test fun linkLocalScopeComesOnlyFromThePhoneSelectedInterface() {
        val endpoint = requireNotNull(MediacenterEndpoint.parse("[fe80::9]"))
        assertEquals(7, (endpoint.scopedAddress(7) as java.net.Inet6Address).scopeId)
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) { endpoint.scopedAddress(0) }
        assertNull(MediacenterEndpoint.parse("fe80::9%7"))
        assertEquals(
            0,
            (requireNotNull(MediacenterEndpoint.parse("fd12::9")).scopedAddress(0) as java.net.Inet6Address).scopeId,
        )
    }
}
