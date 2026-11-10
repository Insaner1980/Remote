package com.emmah.colordock.remote.mediacenter.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundProtocolTest {
    @Test
    fun foregroundingAcknowledgesThisRequestWithoutAnOperation() {
        listOf(MethodName.ACTIVITY_SHOW_MENU, MethodName.ACTIVITY_SHOW_PLAYBACK).forEach { method ->
            val reply =
                RemoteJson.parseReply(
                    method,
                    """{"v":1,"id":"focus","ok":true,"result":{"requestId":"focus"}}""",
                )
            assertTrue(reply is McReply.Success)
            val encoded = RemoteJson.encodeReply(reply)
            assertTrue(encoded.contains("requestId"))
            assertTrue(!encoded.contains("operationId"))
            assertEquals(reply, RemoteJson.parseReply(method, encoded))
            listOf("{}", """{"status":"accepted","operationId":"fake"}""").forEach { result ->
                assertThrows(IllegalArgumentException::class.java) {
                    RemoteJson.parseReply(method, """{"v":1,"id":"focus","ok":true,"result":$result}""")
                }
            }
        }
        assertEquals(Delivery.TRANSIENT, RemoteCommand.ActivityShowMenu(Empty).delivery)
        assertEquals(Delivery.TRANSIENT, RemoteCommand.ActivityShowPlayback(Empty).delivery)
    }
}
