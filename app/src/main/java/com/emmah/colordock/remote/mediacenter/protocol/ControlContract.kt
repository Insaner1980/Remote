package com.emmah.colordock.remote.mediacenter.protocol

import kotlinx.coroutines.flow.StateFlow

/** Transport-neutral boundary; the repository will own epochs, revisions and pending operations. */
interface MediacenterControl {
    val state: StateFlow<MediacenterState>

    suspend fun connect(deviceId: String): McResult<Unit>

    suspend fun request(request: McRequest): McResult<McReply>

    fun disconnect()
}

interface MediacenterPairing {
    suspend fun pair(candidate: PairingCandidate): McResult<PairedMediacenter>
}

sealed interface MediacenterState {
    data object Disconnected : MediacenterState

    data object Connecting : MediacenterState

    data class Ready(
        val snapshot: RemoteSnapshot,
    ) : MediacenterState

    data class Failed(
        val error: McError,
    ) : MediacenterState
}

sealed interface ControlTarget {
    data object LgTv : ControlTarget

    data class Mediacenter(
        val deviceId: String,
    ) : ControlTarget
}
