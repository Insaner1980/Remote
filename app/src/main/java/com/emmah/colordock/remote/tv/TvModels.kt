package com.emmah.colordock.remote.tv

import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import kotlinx.coroutines.flow.StateFlow

enum class TvConnectionState { Disconnected, PermissionRequired, Discovering, Connecting, Pairing, Ready, Reconnecting, Failed }

data class TvState(
    val connection: TvConnectionState = TvConnectionState.Disconnected,
    val message: String? = null,
    val name: String = "LG TV",
    val muted: Boolean? = null,
    val channel: TvChannel? = null,
    val guideAvailable: Boolean = false,
    val error: TvErrorCode? = null,
)

data class TvChannel(val id: String, val number: String, val name: String)

enum class TvCommand(val repeatable: Boolean = false) {
    Up(true), Down(true), Left(true), Right(true), Ok, Back, Home,
    VolumeUp(true), VolumeDown(true), Mute, Unmute,
    ChannelUp(true), ChannelDown(true), LiveTv, Guide, PowerOff,
}

enum class TvErrorCode { Unavailable, PermissionRequired, PairingRequired, IdentityChanged, Unsupported, InvalidRequest, StorageUnavailable }

sealed interface TvResult<out T> {
    data class Success<T>(val value: T) : TvResult<T>
    data class Failure(val code: TvErrorCode, val message: String) : TvResult<Nothing>
}

interface TvControl {
    val state: StateFlow<TvState>
    suspend fun connect(target: TvTarget): TvResult<Unit>
    suspend fun send(command: TvCommand): TvResult<Unit>
    suspend fun channels(): TvResult<List<TvChannel>>
    suspend fun tune(channelId: String): TvResult<Unit>
    suspend fun launchColorDock(): TvResult<Unit>
    fun disconnect()
}

class TvTarget private constructor(val address: String) {
    val gatewayUrl: String get() = "wss://${if (address.contains(':')) "[$address]" else address}:3001"

    fun allowsPointer(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "wss" && uri.host.removeSurrounding("[", "]") == address &&
            uri.port == 3001 && uri.userInfo == null && uri.fragment == null
    }.getOrDefault(false)

    companion object {
        fun parse(input: String): TvTarget? {
            val value = input.trim()
            if (value.contains(':')) {
                if (!value.matches(Regex("[0-9a-fA-F:]+"))) return null
                val address = runCatching { InetAddress.getByName(value) }.getOrNull() as? Inet6Address ?: return null
                if ((address.address[0].toInt() and 0xfe) != 0xfc) return null
                return TvTarget(value.lowercase())
            }
            val parts = value.split('.')
            if (parts.size != 4 || parts.any { !it.matches(Regex("0|[1-9][0-9]{0,2}")) }) return null
            val bytes = parts.map { it.toIntOrNull() ?: return null }
            if (bytes.any { it !in 0..255 }) return null
            val local = bytes[0] == 10 || (bytes[0] == 172 && bytes[1] in 16..31) ||
                (bytes[0] == 192 && bytes[1] == 168)
            return if (local) TvTarget(value) else null
        }
    }
}
