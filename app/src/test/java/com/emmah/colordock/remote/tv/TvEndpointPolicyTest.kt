package com.emmah.colordock.remote.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TvEndpointPolicyTest {
    @Test fun appArtworkRequiresThePairedHttpsResourceOrigin() {
        val target = requireNotNull(TvTarget.parse("192.168.1.10"))
        assertTrue(target.allowsAppIcon("https://192.168.1.10:3001/resources/icons/logo.png"))
        for (url in listOf(
            "http://192.168.1.10:3001/resources/a.png",
            "https://192.168.1.11:3001/resources/a.png",
            "https://192.168.1.10/resources/a.png",
            "https://user@192.168.1.10:3001/resources/a.png",
            "https://192.168.1.10:3001/resources/a.png#fragment",
            "file:///resources/a.png",
            "https://cdn.example/resources/a.png",
            "https://192.168.1.10:3001/resources/../secret",
            "https://192.168.1.10:3001/resources/%2e%2e/secret",
            "https://192.168.1.10:3001/resources/%252e%252e/secret",
            "https://192.168.1.10:3001/resources/a%2f..%2f..%2fsecret",
            "https://192.168.1.10:3001/resources/",
            "https://192.168.1.10:3001/resources/a.png?client-key=secret",
        )) {
            assertFalse(url, target.allowsAppIcon(url))
        }
        assertTrue(requireNotNull(TvTarget.parse("fd12::10")).allowsAppIcon("https://[fd12::10]:3001/resources/a.png"))
    }

    @Test fun equivalentUlaSpellingsShareTheGatewayAndPointerIdentity() {
        val short = requireNotNull(TvTarget.parse("FD12::10"))
        val long = requireNotNull(TvTarget.parse("fd12:0:0:0:0:0:0:10"))
        assertEquals(short.address, long.address)
        val gateway =
            okhttp3.Request
                .Builder()
                .url(long.gatewayUrl)
                .build()
                .url
        assertTrue(long.matchesHost(gateway.host))
        assertTrue(long.allowsPointer("wss://[fd12::10]:3001/pointer"))
        assertFalse(long.allowsPointer("wss://[fd12::11]:3001/pointer"))
        for (invalid in listOf(
            "fd12::10%eth0",
            "::ffff:192.168.1.1",
            "fc00::g",
            "+10.0.0.1",
            "10.0.0.256",
        )) {
            assertNull(TvTarget.parse(invalid))
        }
    }

    @Test
    fun manualAddress_acceptsOnlyLiteralLanAddresses() {
        for (address in listOf("192.168.1.102", "10.0.0.2", "172.16.0.2", "172.31.255.254", "fd12::10")) {
            assertNotNull(address, TvTarget.parse(address))
        }
        for (address in listOf(
            "",
            "localhost",
            "127.0.0.1",
            "0.0.0.0",
            "224.0.0.1",
            "8.8.8.8",
            "172.32.0.1",
            "https://192.168.1.1",
            "192.168.1.1/path",
            "user@192.168.1.1",
            "192.168.01.1",
            "::1",
            "::",
            "ff02::1",
        )) {
            assertNull(address, TvTarget.parse(address))
        }
    }

    @Test
    fun pointerSocket_rejectsCleartextForeignHostsAndCredentials() {
        val target = requireNotNull(TvTarget.parse("192.168.1.102"))
        assertTrue(target.allowsPointer("wss://192.168.1.102:3001/resources/pointer"))
        for (url in listOf(
            "ws://192.168.1.102:3000/resources/pointer",
            "wss://192.168.1.103:3001/pointer",
            "wss://user@192.168.1.102:3001/pointer",
            "wss://192.168.1.102:443/pointer",
        )) {
            assertFalse(url, target.allowsPointer(url))
        }
    }
}
