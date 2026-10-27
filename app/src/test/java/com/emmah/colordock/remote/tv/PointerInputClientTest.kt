package com.emmah.colordock.remote.tv

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class PointerInputClientTest {
    private class FakeSocket(
        val req: Request,
    ) : WebSocket {
        var cancelled = false
        val sent = mutableListOf<String>()

        override fun request() = req

        override fun queueSize() = 0L

        override fun send(text: String): Boolean {
            sent += text
            return !cancelled
        }

        override fun send(bytes: ByteString) = false

        override fun close(
            code: Int,
            reason: String?,
        ) = true

        override fun cancel() {
            cancelled = true
        }
    }

    private class Factory : WebSocket.Factory {
        val calls = mutableListOf<Pair<FakeSocket, WebSocketListener>>()

        override fun newWebSocket(
            request: Request,
            listener: WebSocketListener,
        ): WebSocket =
            FakeSocket(request).also {
                calls += it to listener
            }

        fun open(index: Int) {
            val (socket, listener) = calls[index]
            listener.onOpen(
                socket,
                Response
                    .Builder()
                    .request(socket.req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(101)
                    .message("fixture")
                    .build(),
            )
        }
    }

    @Test fun closeAndReplacementTerminateOldWaitersAndIgnoreOldCallbacks() =
        runTest {
            val factory = Factory()
            val pointer = PointerInputClient(factory, requireNotNull(TvTarget.parse("192.168.1.9")))
            val first =
                async {
                    runCatching {
                        pointer.connect("wss://192.168.1.9:3001/pointer")
                    }
                }
            runCurrent()
            val second =
                async {
                    pointer.connect("wss://192.168.1.9:3001/pointer")
                }
            runCurrent()
            assertTrue(first.await().exceptionOrNull() is IOException)
            factory.open(1)
            second.await()
            factory.open(0)
            factory.calls[0].second.onFailure(factory.calls[0].first, IOException("fixture"), null)
            pointer.send(TvCommand.Up)
            assertEquals(listOf("type:button\nname:UP\n\n"), factory.calls[1].first.sent)
            pointer.close()
            assertThrows(IOException::class.java) {
                pointer.send(TvCommand.Up)
            }
            assertTrue(
                factory.calls.all {
                    it.first.cancelled
                },
            )
        }

    @Test fun openDeadlineClosesItsSocketWithoutSwallowingParentCancellation() =
        runTest {
            val factory = Factory()
            val pointer = PointerInputClient(factory, requireNotNull(TvTarget.parse("192.168.1.9")))
            assertTrue(
                runCatching {
                    pointer.connect("wss://192.168.1.9:3001/pointer")
                }.exceptionOrNull() is IOException,
            )
            assertTrue(
                factory.calls
                    .single()
                    .first.cancelled,
            )
            assertTrue(
                runCatching {
                    kotlinx.coroutines.withTimeout(100) {
                        pointer.connect("wss://192.168.1.9:3001/pointer")
                    }
                }.exceptionOrNull() is kotlinx.coroutines.TimeoutCancellationException,
            )
            assertTrue(
                factory.calls.all {
                    it.first.cancelled
                },
            )
        }

    @Test fun directionalKeyUsesTheLgFrameAndTerminatingBlankLine() {
        assertEquals("type:button\nname:UP\n\n", PointerInputClient.frame(TvCommand.Up))
        assertEquals("type:button\nname:BACK\n\n", PointerInputClient.frame(TvCommand.Back))
        assertEquals("type:button\nname:HOME\n\n", PointerInputClient.frame(TvCommand.Home))
        assertEquals("type:button\nname:GUIDE\n\n", PointerInputClient.frame(TvCommand.Guide))
    }

    @Test fun okUsesTheFocusedEnterKeyAndVolumeCannotEnterThePointerChannel() {
        assertEquals("type:button\nname:ENTER\n\n", PointerInputClient.frame(TvCommand.Ok))
        assertNull(PointerInputClient.frame(TvCommand.VolumeUp))
        assertNull(PointerInputClient.frame(TvCommand.PowerOff))
    }
}
