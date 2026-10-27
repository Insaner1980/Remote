package com.emmah.colordock.remote.tv

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException

private const val CONTROLS_UNAVAILABLE = "TV controls are unavailable."
private const val POINTER_CONNECT_TIMEOUT_MILLIS = 5_000L

internal class PointerInputClient(
    private val http: WebSocket.Factory,
    private val target: TvTarget,
) {
    private var socket: WebSocket? = null
    private var ready = false
    private var generation = 0L
    private var opening: CompletableDeferred<Unit>? = null

    suspend fun connect(url: String) {
        if (!target.allowsPointer(url)) throw IOException("The TV returned an unsupported control connection.")
        val opened = CompletableDeferred<Unit>()
        val token =
            synchronized(this) {
                close()
                val token = generation
                opening = opened
                socket =
                    http.newWebSocket(
                        Request.Builder().url(url).build(),
                        listener(token, opened),
                    )
                token
            }
        var connected = false
        try {
            withTimeoutOrNull(POINTER_CONNECT_TIMEOUT_MILLIS) {
                opened.await()
            } ?: throw IOException("TV controls did not reply.")
            connected = true
        } finally {
            if (!connected) {
                synchronized(this) {
                    if (generation == token) close()
                }
            }
        }
    }

    private fun listener(
        token: Long,
        opened: CompletableDeferred<Unit>,
    ): WebSocketListener =
        object : WebSocketListener() {
            override fun onOpen(
                webSocket: WebSocket,
                response: Response,
            ) {
                synchronized(this@PointerInputClient) {
                    if (generation != token) {
                        webSocket.cancel()
                        return
                    }
                    ready = true
                    opened.complete(Unit)
                }
            }

            override fun onFailure(
                webSocket: WebSocket,
                t: Throwable,
                response: Response?,
            ) {
                synchronized(this@PointerInputClient) {
                    if (generation != token) return
                    ready = false
                    opened.completeExceptionally(IOException(CONTROLS_UNAVAILABLE))
                }
            }

            override fun onClosed(
                webSocket: WebSocket,
                code: Int,
                reason: String,
            ) {
                synchronized(this@PointerInputClient) {
                    if (generation != token) return
                    ready = false
                    opened.completeExceptionally(IOException(CONTROLS_UNAVAILABLE))
                }
            }
        }

    @Synchronized fun send(command: TvCommand) {
        val message = frame(command) ?: throw IOException("Unsupported pointer command")
        if (!ready || socket?.send(message) != true) throw IOException(CONTROLS_UNAVAILABLE)
    }

    @Synchronized fun close() {
        generation++
        ready = false
        opening?.completeExceptionally(IOException(CONTROLS_UNAVAILABLE))
        opening = null
        socket?.cancel()
        socket = null
    }

    companion object {
        fun frame(command: TvCommand): String? =
            when (command) {
                TvCommand.Ok -> "type:button\nname:ENTER\n\n"
                TvCommand.Up -> "type:button\nname:UP\n\n"
                TvCommand.Down -> "type:button\nname:DOWN\n\n"
                TvCommand.Left -> "type:button\nname:LEFT\n\n"
                TvCommand.Right -> "type:button\nname:RIGHT\n\n"
                TvCommand.Back -> "type:button\nname:BACK\n\n"
                TvCommand.Home -> "type:button\nname:HOME\n\n"
                TvCommand.Guide -> "type:button\nname:GUIDE\n\n"
                else -> null
            }
    }
}
