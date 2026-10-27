package com.emmah.colordock.remote.tv

import kotlinx.coroutines.flow.StateFlow
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI

enum class TvConnectionState {
    Disconnected,
    PermissionRequired,
    Discovering,
    Connecting,
    Pairing,
    Ready,
    Reconnecting,
    Failed,
}

data class TvState(
    val connection: TvConnectionState = TvConnectionState.Disconnected,
    val message: String? = null,
    val name: String = "LG TV",
    val muted: Boolean? = null,
    val channel: TvChannel? = null,
    val guideAvailable: Boolean = false,
    val error: TvErrorCode? = null,
)

data class TvChannel(
    val id: String,
    val number: String,
    val name: String,
)

data class TvApp(
    val id: String,
    val title: String,
    val iconUrl: String?,
)

enum class TvCommand(
    val repeatable: Boolean = false,
) {
    Up(true),
    Down(true),
    Left(true),
    Right(true),
    Ok,
    Back,
    Home,
    VolumeUp(true),
    VolumeDown(true),
    Mute,
    Unmute,
    ChannelUp(true),
    ChannelDown(true),
    LiveTv,
    Guide,
    PowerOff,
}

enum class TvErrorCode {
    Unavailable,
    PermissionRequired,
    PairingRequired,
    IdentityChanged,
    Unsupported,
    InvalidRequest,
    StorageUnavailable,
}

sealed interface TvResult<out T> {
    data class Success<T>(
        val value: T,
    ) : TvResult<T>

    data class Failure(
        val code: TvErrorCode,
        val message: String,
    ) : TvResult<Nothing>
}

interface TvControl {
    val state: StateFlow<TvState>

    suspend fun connect(target: TvTarget): TvResult<Unit>

    suspend fun send(command: TvCommand): TvResult<Unit>

    suspend fun channels(): TvResult<List<TvChannel>>

    suspend fun apps(): TvResult<List<TvApp>>

    suspend fun launchApp(appId: String): TvResult<Unit>

    suspend fun tune(channelId: String): TvResult<Unit>

    suspend fun launchColorDock(): TvResult<Unit>

    fun disconnect()
}

class TvTarget private constructor(
    val address: String,
) {
    val gatewayUrl: String get() = "wss://${if (address.contains(':')) "[$address]" else address}:$TV_TLS_PORT"

    fun matchesHost(host: String): Boolean = parse(host.removeSurrounding("[", "]"))?.address == address

    fun allowsPointer(url: String): Boolean =
        runCatching {
            val uri = URI(url)
            uri.scheme == "wss" && matchesHost(uri.host) &&
                uri.port == TV_TLS_PORT && uri.userInfo == null && uri.fragment == null
        }.getOrDefault(false)

    fun allowsAppIcon(url: String): Boolean =
        runCatching {
            val uri = URI(url)
            url.length <= MAX_ICON_URL_LENGTH && validIconOrigin(uri) && validIconPath(uri)
        }.getOrDefault(false)

    private fun validIconOrigin(uri: URI): Boolean =
        uri.scheme == "https" && matchesHost(uri.host) && uri.port == TV_TLS_PORT &&
            uri.userInfo == null && uri.fragment == null && uri.rawQuery == null

    private fun validIconPath(uri: URI): Boolean {
        val path = uri.path.orEmpty()
        return path.startsWith("/resources/") && path.length > "/resources/".length &&
            !path.contains('\\') && !path.contains('%') &&
            !Regex("%(2f|5c)", RegexOption.IGNORE_CASE).containsMatchIn(uri.rawPath) &&
            path.split('/').none { it == "." || it == ".." } && uri.normalize().path == path
    }

    companion object {
        private const val TV_TLS_PORT = 3001
        private const val MAX_ICON_URL_LENGTH = 2048
        private const val IPV6_LOCAL_MASK = 0xfe
        private const val IPV6_LOCAL_PREFIX = 0xfc
        private const val IPV4_PARTS = 4
        private const val MAX_IPV4_BYTE = 255
        private const val PRIVATE_A_PREFIX = 10
        private const val PRIVATE_B_PREFIX = 172
        private const val PRIVATE_B_SECOND_MIN = 16
        private const val PRIVATE_B_SECOND_MAX = 31
        private const val PRIVATE_C_PREFIX = 192
        private const val PRIVATE_C_SECOND = 168
        private val privateIpv4Prefixes =
            listOf(
                PRIVATE_A_PREFIX..PRIVATE_A_PREFIX to 0..MAX_IPV4_BYTE,
                PRIVATE_B_PREFIX..PRIVATE_B_PREFIX to PRIVATE_B_SECOND_MIN..PRIVATE_B_SECOND_MAX,
                PRIVATE_C_PREFIX..PRIVATE_C_PREFIX to PRIVATE_C_SECOND..PRIVATE_C_SECOND,
            )

        fun parse(input: String): TvTarget? {
            val value = input.trim()
            return if (value.contains(':')) parseIpv6(value) else parseIpv4(value)
        }

        private fun parseIpv6(value: String): TvTarget? {
            if (!value.matches(Regex("[0-9a-fA-F:]+"))) return null
            val address = runCatching { InetAddress.getByName(value) }.getOrNull() as? Inet6Address
            return address
                ?.takeIf {
                    (it.address[0].toInt() and IPV6_LOCAL_MASK) == IPV6_LOCAL_PREFIX
                }?.let { TvTarget(requireNotNull(it.hostAddress).lowercase()) }
        }

        private fun parseIpv4(value: String): TvTarget? {
            val parts = value.split('.')
            if (parts.size != IPV4_PARTS || parts.any { !it.matches(Regex("0|[1-9][0-9]{0,2}")) }) {
                return null
            }
            val bytes = parts.map(String::toInt)
            val local =
                bytes.all { it in 0..MAX_IPV4_BYTE } &&
                    privateIpv4Prefixes.any { (first, second) -> bytes[0] in first && bytes[1] in second }
            return if (local) TvTarget(value) else null
        }
    }
}
