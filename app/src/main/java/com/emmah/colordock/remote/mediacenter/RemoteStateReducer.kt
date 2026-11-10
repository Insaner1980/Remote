package com.emmah.colordock.remote.mediacenter

import com.emmah.colordock.remote.mediacenter.protocol.Hello
import com.emmah.colordock.remote.mediacenter.protocol.RemoteJson
import com.emmah.colordock.remote.mediacenter.protocol.RemoteSnapshot
import com.emmah.colordock.remote.mediacenter.protocol.StrictJson
import com.emmah.colordock.remote.mediacenter.protocol.Topic
import org.json.JSONObject

private class StateRejected(
    val reduction: StateReduction,
) : IllegalArgumentException()

private fun requireState(
    accepted: Boolean,
    failure: StateReduction = StateReduction.ResyncRequired,
) {
    if (!accepted) throw StateRejected(failure)
}

private val wireCounter = Regex("0|[1-9][0-9]{0,19}")

private fun counter(value: String): ULong {
    require(wireCounter.matches(value))
    return value.toULong()
}

internal enum class StateReduction { Applied, ResyncRequired, IdentityChanged, VersionMismatch }

/** Keeps actual native topic truth; never promotes a previous phone snapshot into a new session. */
internal class RemoteStateReducer(
    private val serverId: String,
) {
    var hello: Hello? = null
        private set
    var snapshot: RemoteSnapshot? = null
        private set
    private var document: JSONObject? = null
    private val buffered = ArrayDeque<JSONObject>()
    val ready: Boolean get() = snapshot != null

    fun apply(message: String): StateReduction =
        try {
            require(message.toByteArray(Charsets.UTF_8).size <= MAX_MESSAGE_BYTES)
            StrictJson.validate(message)
            val source = JSONObject(message)
            when {
                source.has("v") && source.getInt("v") != 1 -> reject(StateReduction.VersionMismatch)
                source.optString("type") == "hello" -> acceptHello(message)
                source.optString("type") == "snapshot" -> acceptSnapshot(source)
                source.has("topic") -> acceptEvent(source)
                else -> reject(StateReduction.ResyncRequired)
            }
        } catch (failure: StateRejected) {
            reject(failure.reduction)
        } catch (_: IllegalArgumentException) {
            reject(StateReduction.ResyncRequired)
        } catch (_: org.json.JSONException) {
            reject(StateReduction.ResyncRequired)
        }

    private fun acceptHello(message: String): StateReduction {
        val value = RemoteJson.parseHello(message)
        invalidate()
        requireState(value.deviceId == serverId, StateReduction.IdentityChanged)
        hello = value
        return StateReduction.Applied
    }

    private fun acceptSnapshot(source: JSONObject): StateReduction {
        val greeting = requireNotNull(hello)
        val next = RemoteJson.parseSnapshot(source.toString())
        requireState(next.connection.serverDeviceId == serverId, StateReduction.IdentityChanged)
        val sequence = counter(next.sequence)
        requireState(next.serverEpoch == greeting.serverEpoch && sequence >= counter(greeting.snapshotSequence))
        val previous = document
        requireState(previous == null || nonRegressing(source, previous))
        document = source
        snapshot = next
        val events = buffered.toList()
        buffered.clear()
        for (event in events) {
            if (counter(event.getString("sequence")) <= sequence) continue
            val result = acceptEvent(event)
            requireState(result == StateReduction.Applied, result)
        }
        return StateReduction.Applied
    }

    private fun nonRegressing(
        next: JSONObject,
        previous: JSONObject,
    ): Boolean =
        next.getString("sequence").toULong() >= previous.getString("sequence").toULong() &&
            Topic.entries.all { topic ->
                next.getJSONObject(topic.wire).getString("revision").toULong() >=
                    previous.getJSONObject(topic.wire).getString("revision").toULong()
            }

    private fun acceptEvent(source: JSONObject): StateReduction {
        val greeting = requireNotNull(hello)
        requireState(source.getString("serverEpoch") == greeting.serverEpoch)
        val sequence = counter(source.getString("sequence"))
        val topic = source.getString("topic")
        require(Topic.entries.any { it.wire == topic })
        val previous = document
        if (previous == null) {
            requireState(buffered.size < EVENT_CAPACITY)
            buffered.addLast(source)
        } else {
            val last = counter(previous.getString("sequence"))
            requireState(last != ULong.MAX_VALUE && sequence == last + 1uL)
            val data = source.getJSONObject("data")
            requireState(
                counter(data.getString("revision")) > counter(previous.getJSONObject(topic).getString("revision")),
            )
            val next = JSONObject(previous.toString()).put("sequence", sequence.toString()).put(topic, data)
            val typed = RemoteJson.parseSnapshot(next.toString())
            requireState(typed.connection.serverDeviceId == serverId, StateReduction.IdentityChanged)
            document = next
            snapshot = typed
        }
        return StateReduction.Applied
    }

    fun invalidate() {
        snapshot = null
        document = null
        buffered.clear()
    }

    private fun reject(result: StateReduction): StateReduction {
        invalidate()
        return result
    }

    private companion object {
        const val EVENT_CAPACITY = 256
        const val MAX_MESSAGE_BYTES = 262144
    }
}
