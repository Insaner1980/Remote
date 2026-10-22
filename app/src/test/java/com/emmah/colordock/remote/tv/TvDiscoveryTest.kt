package com.emmah.colordock.remote.tv

import org.junit.Assert.*
import org.junit.Test

class TvDiscoveryTest {
    private fun packet(location:String="http://192.168.1.2:3000/description.xml")="HTTP/1.1 200 OK\r\nST: ${TvDiscovery.SERVICE}\r\nLOCATION: $location\r\nUSN: uuid:fixture\r\n\r\n"
    @Test fun discoveryAcceptsOnlyExpectedServiceAtAdvertisingPrivateHost(){
        assertNotNull(TvDiscovery.parsePacket(packet(),"192.168.1.2"))
        for(url in listOf("http://example.org/a","http://192.168.1.3/a","file:///secret","http://user:pass@192.168.1.2/a","http://192.168.1.2/a#secret"))assertNull(TvDiscovery.parsePacket(packet(url),"192.168.1.2"))
        assertNull(TvDiscovery.parsePacket(packet().replace(TvDiscovery.SERVICE,"ssdp:all"),"192.168.1.2"))
        assertNull(TvDiscovery.parsePacket(packet(),"8.8.8.8"))
        assertNull(TvDiscovery.parsePacket(packet().replace("USN:","LOCATION: http://192.168.1.2/other\r\nUSN:"),"192.168.1.2"))
    }
    @Test fun xmlTreatsNamesAsTextBoundsMetadataAndRejectsExternalEntities(){
        val xml="<root xmlns='urn:schemas-upnp-org:device-1-0'><device><friendlyName>&lt;script&gt;TV&lt;/script&gt;</friendlyName><modelName>${"X".repeat(200)}</modelName></device></root>"
        val (name,model)=TvDiscovery.parseDescription(xml.toByteArray());assertEquals("<script>TV</script>",name);assertEquals(120,model.length)
        val hostile="<!DOCTYPE root [<!ENTITY leak SYSTEM 'file:///not-permitted'>]><root><friendlyName>&leak;</friendlyName></root>"
        try{TvDiscovery.parseDescription(hostile.toByteArray());fail("DOCTYPE must be rejected")}catch(_:org.xml.sax.SAXException){}
    }
}
