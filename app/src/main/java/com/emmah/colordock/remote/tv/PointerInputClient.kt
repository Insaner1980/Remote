package com.emmah.colordock.remote.tv

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException

internal class PointerInputClient(private val http: OkHttpClient, private val target: TvTarget) {
    private var socket: WebSocket? = null
    @Volatile private var ready = false

    suspend fun connect(url: String) {
        if (!target.allowsPointer(url)) throw IOException("The TV returned an unsupported control connection.")
        close()
        val opened = CompletableDeferred<Unit>()
        socket = http.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { ready = true; opened.complete(Unit) }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                ready = false; opened.completeExceptionally(IOException("TV controls are unavailable."))
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { ready = false }
        })
        try { withTimeout(5_000) { opened.await() } }
        catch (error: Exception) { close(); throw error }
    }

    fun send(command: TvCommand) {
        val message = frame(command) ?: throw IOException("Unsupported pointer command")
        if (!ready || socket?.send(message) != true) throw IOException("TV controls are unavailable.")
    }

    fun close() { ready = false; socket?.cancel(); socket = null }

    companion object {
        fun frame(command: TvCommand): String? = when (command) {
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
