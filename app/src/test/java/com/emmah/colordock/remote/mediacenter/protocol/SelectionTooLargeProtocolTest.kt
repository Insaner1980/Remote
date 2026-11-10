package com.emmah.colordock.remote.mediacenter.protocol

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class SelectionTooLargeProtocolTest {
    @Test
    fun oversizedPhotoSelectionErrorRoundTripsWithoutLosingItsTypedCode() {
        val bundle =
            JSONObject(
                javaClass.classLoader!!
                    .getResourceAsStream(
                        "mediacenter-v1.json",
                    )!!
                    .bufferedReader()
                    .use { it.readText() },
            )
        val fixture = bundle.getJSONObject("fixtures").getJSONObject("photoSelectionTooLarge")
        val reply = RemoteJson.parseReply(MethodName.LIBRARY_QUERY, fixture.toString())
        val failure = reply as McReply.Failure
        assertEquals(WireErrorCode.SELECTION_TOO_LARGE, failure.error.code)
        assertEquals(reply, RemoteJson.parseReply(MethodName.LIBRARY_QUERY, RemoteJson.encodeReply(reply)))
    }
}
