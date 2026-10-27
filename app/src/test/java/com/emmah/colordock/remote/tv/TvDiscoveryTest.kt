package com.emmah.colordock.remote.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class TvDiscoveryTest {
    @Test fun discoveryMatchesEquivalentUlaHostSpellings() {
        val advertisement =
            TvDiscovery.parsePacket(
                packet("http://[fd12::10]:3000/description.xml"),
                "fd12:0:0:0:0:0:0:10",
            )
        assertNotNull(advertisement)
        assertEquals(TvTarget.parse("fd12::10")?.address, advertisement?.address)
        assertNull(TvDiscovery.parsePacket(packet("http://[fd12::11]:3000/description.xml"), "fd12::10"))
    }

    private fun packet(location: String = "http://192.168.1.2:3000/description.xml") =
        "HTTP/1.1 200 OK\r\nST: ${TvDiscovery.SERVICE}\r\nLOCATION: $location\r\nUSN: uuid:fixture\r\n\r\n"

    @Test fun discoveryAcceptsOnlyExpectedServiceAtAdvertisingPrivateHost() {
        assertNotNull(TvDiscovery.parsePacket(packet(), "192.168.1.2"))
        for (url in listOf(
            "http://example.org/a",
            "http://192.168.1.3/a",
            "file:///secret",
            "http://user:pass@192.168.1.2/a",
            "http://192.168.1.2/a#secret",
        )) {
            assertNull(TvDiscovery.parsePacket(packet(url), "192.168.1.2"))
        }
        assertNull(TvDiscovery.parsePacket(packet().replace(TvDiscovery.SERVICE, "ssdp:all"), "192.168.1.2"))
        assertNull(TvDiscovery.parsePacket(packet(), "8.8.8.8"))
        assertNull(
            TvDiscovery.parsePacket(
                packet().replace("USN:", "LOCATION: http://192.168.1.2/other\r\nUSN:"),
                "192.168.1.2",
            ),
        )
    }

    @Test fun xmlTreatsNamesAsTextBoundsMetadataAndRejectsExternalEntities() {
        val xml =
            "<root xmlns='urn:schemas-upnp-org:device-1-0'><device>" +
                "<friendlyName>&lt;script&gt;TV&lt;/script&gt;</friendlyName>" +
                "<modelName>${"X".repeat(200)}</modelName></device></root>"
        val (name, model) = TvDiscovery.parseDescription(xml.toByteArray())
        assertEquals("<script>TV</script>", name)
        assertEquals(120, model.length)
        val hostile =
            "<!DOCTYPE root [<!ENTITY leak SYSTEM 'file:///not-permitted'>]>" +
                "<root><friendlyName>&leak;</friendlyName></root>"
        try {
            TvDiscovery.parseDescription(hostile.toByteArray())
            fail("DOCTYPE must be rejected")
        } catch (_: org.xml.sax.SAXException) {
        }
    }

    @Test fun xmlPreservesFirstNamesAndRejectsEncodedDoctypesAndOversizeInput() {
        val xml =
            "<root xmlns='urn:test'><friendlyName> First <b>TV</b> </friendlyName>" +
                "<friendlyName>Second</friendlyName></root>"
        assertEquals("First TV" to "", TvDiscovery.parseDescription(xml.toByteArray()))
        assertEquals("LG TV" to "", TvDiscovery.parseDescription("<root/>".toByteArray()))
        for (declaration in listOf(
            "<!DOCTYPE root SYSTEM 'http://127.0.0.1:9/forbidden'>",
            "<!DOCTYPE root [<!ENTITY local 'expanded'>]>",
        )) {
            for (charset in listOf(Charsets.UTF_8, Charsets.UTF_16)) {
                try {
                    TvDiscovery.parseDescription((declaration + "<root/>").toByteArray(charset))
                    fail("DOCTYPE must be rejected before resolution")
                } catch (_: org.xml.sax.SAXException) {
                }
            }
        }
        val exact = "<root>" + " ".repeat(65536 - 13) + "</root>"
        assertEquals("LG TV" to "", TvDiscovery.parseDescription(exact.toByteArray()))
        try {
            TvDiscovery.parseDescription((exact + " ").toByteArray())
            fail("Oversize XML must be rejected")
        } catch (_: IllegalArgumentException) {
        }
        try {
            TvDiscovery.parseDescription("<root>".toByteArray())
            fail("Malformed XML must be rejected")
        } catch (_: org.xml.sax.SAXException) {
        }
    }
}
