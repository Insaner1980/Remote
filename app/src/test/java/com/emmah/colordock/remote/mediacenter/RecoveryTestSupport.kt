package com.emmah.colordock.remote.mediacenter

import com.emmah.colordock.remote.mediacenter.protocol.McRequest
import com.emmah.colordock.remote.mediacenter.protocol.MethodName
import com.emmah.colordock.remote.mediacenter.protocol.PairedMediacenter
import com.emmah.colordock.remote.mediacenter.protocol.Scope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetAddress
import javax.net.SocketFactory

internal class RecoveryControl : MediacenterControl {
    override val state = MutableStateFlow(MediacenterConnectionState.Disconnected)
    private val incoming = Channel<String>(Channel.UNLIMITED)
    override val messages = incoming.receiveAsFlow()
    val sent = mutableListOf<McRequest>()
    val connections = mutableListOf<Pair<StoredMediacenter, MediacenterNetwork>>()
    var disconnects = 0

    override fun connect(
        value: StoredMediacenter,
        network: MediacenterNetwork,
    ) {
        connections.add(value to network)
        state.value = MediacenterConnectionState.Connecting
    }

    override fun send(request: McRequest): Boolean {
        sent.add(request)
        return true
    }

    override fun disconnect() {
        disconnects++
        state.value = MediacenterConnectionState.Disconnected
    }

    fun emit(text: String) {
        check(incoming.trySend(text).isSuccess)
    }

    fun native(epoch: String = "epoch") {
        state.value = MediacenterConnectionState.Connected
        val hello =
            JSONObject(RecoveryFixture.hello(epoch)).apply {
                getJSONObject("capabilities").put("methods", JSONArray(MethodName.entries.map { it.wire }))
            }
        emit(hello.toString())
        emit(RecoveryFixture.snapshot(epoch))
    }
}

internal class RecoveryRoute : MediacenterNetwork {
    var available = true
    override val socketFactory: SocketFactory = SocketFactory.getDefault()

    override fun resolve(endpoint: MediacenterEndpoint): List<InetAddress> = emptyList()

    override fun available() = available
}

internal class MemoryPending : PendingCommandStore {
    var records = emptyList<PendingCommand>()

    override fun read() = records.toList()

    override fun write(commands: List<PendingCommand>) {
        records = commands.toList()
    }
}

internal fun recoverySaved(address: String = "192.168.1.9") =
    StoredMediacenter(
        requireNotNull(MediacenterEndpoint.parse(address, 443)),
        PairedMediacenter("server", "phone", "A".repeat(43), "ab".repeat(32), Scope.entries),
    )
