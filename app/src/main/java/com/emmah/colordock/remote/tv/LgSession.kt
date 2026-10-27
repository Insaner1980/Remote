package com.emmah.colordock.remote.tv

import kotlinx.coroutines.CompletableDeferred
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

internal class LgSession {
    var state = TvConnectionState.Disconnected
        private set
    var registeredKey: String? = null
        private set
    private val sequence = AtomicLong()
    private var registrationId: String? = null

    private class Pending(
        val reply: CompletableDeferred<JSONObject>,
        val accept: (JSONObject) -> Boolean,
    )

    private val pending = ConcurrentHashMap<String, Pending>()
    private val subscriptions = ConcurrentHashMap<String, (JSONObject?) -> Unit>()

    fun subscribe(callback: (JSONObject?) -> Unit): String =
        "subscription-${sequence.incrementAndGet()}".also {
            subscriptions[it] =
                callback
        }

    fun register(): String {
        state = TvConnectionState.Pairing
        registeredKey = null
        return "register-${sequence.incrementAndGet()}".also {
            registrationId = it
        }
    }

    fun request(
        accept: (JSONObject) -> Boolean = {
            true
        },
    ): Pair<String, CompletableDeferred<JSONObject>> {
        val id = "request-${sequence.incrementAndGet()}"
        val reply = CompletableDeferred<JSONObject>()
        pending[id] = Pending(reply, accept)
        return id to reply
    }

    fun receive(message: JSONObject) {
        val id = message.optString("id")
        val type = message.optString("type")
        when {
            id == registrationId && state == TvConnectionState.Pairing -> receiveRegistration(message, type)
            type == "response" || type == "error" -> receiveResponse(message, id, type)
        }
    }

    private fun receiveRegistration(
        message: JSONObject,
        type: String,
    ) {
        if (type == "registered") {
            val key = message.optJSONObject("payload")?.opt("client-key") as? String ?: ""
            if (key.isNotBlank() && key.length <= MAX_CLIENT_KEY_LENGTH) {
                registeredKey = key
                state = TvConnectionState.Ready
            } else {
                state = TvConnectionState.Failed
            }
        } else if (type == "error") {
            state = TvConnectionState.Failed
        }
    }

    private fun receiveResponse(
        message: JSONObject,
        id: String,
        type: String,
    ) {
        val payload = message.optJSONObject("payload")
        val rejected = type == "error" || payload?.opt("returnValue") == false
        val callback = subscriptions[id]
        if (callback != null) {
            if (rejected) subscriptions.remove(id)
            callback(if (rejected) null else payload)
        } else {
            val request = pending[id] ?: return
            val value = payload ?: JSONObject()
            if (rejected) {
                pending.remove(id)
                request.reply.completeExceptionally(IOException("The TV did not allow this command."))
            } else if (request.accept(value)) {
                pending.remove(id)
                request.reply.complete(value)
            }
        }
    }

    fun cancel(id: String) {
        pending.remove(id)?.reply?.cancel()
        subscriptions.remove(id)
    }

    fun disconnect() {
        state = TvConnectionState.Disconnected
        registeredKey = null
        registrationId = null
        pending.values.forEach {
            it.reply.completeExceptionally(IOException("TV unavailable"))
        }
        pending.clear()
        subscriptions.clear()
    }
}
