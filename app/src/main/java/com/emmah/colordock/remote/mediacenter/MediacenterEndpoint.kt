package com.emmah.colordock.remote.mediacenter

import java.net.Inet6Address
import java.net.InetAddress

/** Numeric LAN addresses only; this policy is intentionally independent of TV targets. */
class MediacenterEndpoint private constructor(
    val address: String,
    val port: Int,
    internal val literal: InetAddress,
) {
    internal fun scopedAddress(interfaceIndex: Int): InetAddress =
        if (literal is Inet6Address &&
            literal.isLinkLocalAddress
        ) {
            require(interfaceIndex > 0) { "Select the phone's Wi-Fi interface." }
            Inet6Address.getByAddress(null, literal.address, interfaceIndex)
        } else {
            literal
        }

    override fun toString(): String = "MediacenterEndpoint([redacted])"

    companion object {
        private const val MAX_PORT = 65535
        private const val MAX_OCTET = 255
        private const val PRIVATE_TEN = 10
        private const val PRIVATE_192 = 192
        private const val PRIVATE_192_SECOND = 168
        private const val PRIVATE_172 = 172
        private const val PRIVATE_172_START = 16
        private const val PRIVATE_172_END = 31
        private const val ULA_MASK = 254
        private const val ULA_PREFIX = 252
        private const val LINK_MASK = 192
        private const val LINK_PREFIX = 128
        private val numericV4 = Regex("(0|[1-9][0-9]{0,2})(\\.(0|[1-9][0-9]{0,2})){3}")

        fun parse(
            address: String,
            port: Int = 47831,
        ): MediacenterEndpoint? {
            if (port !in 1..MAX_PORT || address.isBlank() || address != address.trim()) return null
            val host =
                if (address.startsWith('[') &&
                    address.endsWith(']')
                ) {
                    address.substring(1, address.length - 1)
                } else {
                    address
                }
            val literal = if (':' in host) parseV6(host) else parseV4(host)
            return literal?.let { value -> value.hostAddress?.let { MediacenterEndpoint(it, port, value) } }
        }

        private fun parseV4(host: String): InetAddress? {
            if (!numericV4.matches(host)) return null
            val octets = host.split('.').map(String::toInt)
            val privateAddress =
                octets[0] == PRIVATE_TEN ||
                    (octets[0] == PRIVATE_192 && octets[1] == PRIVATE_192_SECOND) ||
                    (octets[0] == PRIVATE_172 && octets[1] in PRIVATE_172_START..PRIVATE_172_END)
            return if (privateAddress &&
                octets.all { it <= MAX_OCTET }
            ) {
                InetAddress.getByAddress(octets.map(Int::toByte).toByteArray())
            } else {
                null
            }
        }

        private fun parseV6(host: String): InetAddress? {
            if (host.any { it !in "0123456789abcdefABCDEF:" }) return null
            val literal = runCatching { InetAddress.getByName(host) }.getOrNull() as? Inet6Address
            return literal?.takeIf {
                val first = it.address[0].toInt() and MAX_OCTET
                val second = it.address[1].toInt() and MAX_OCTET
                (first and ULA_MASK) == ULA_PREFIX || (first == ULA_MASK && (second and LINK_MASK) == LINK_PREFIX)
            }
        }
    }
}
