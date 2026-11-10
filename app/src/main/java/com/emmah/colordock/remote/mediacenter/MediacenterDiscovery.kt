package com.emmah.colordock.remote.mediacenter

object MediacenterDiscovery {
    fun candidates(
        type: String,
        attributes: Map<String, ByteArray>,
        addresses: List<String>,
        port: Int,
        expectedDeviceId: String,
    ): List<MediacenterEndpoint> {
        if (type.trimEnd('.') != "_emma-mc._tcp" || attributes["v"]?.toString(Charsets.UTF_8) != "1" ||
            attributes["id"]?.toString(Charsets.UTF_8) != expectedDeviceId
        ) {
            return emptyList()
        }
        return addresses
            .mapNotNull {
                MediacenterEndpoint.parse(
                    it.substringBefore('%'),
                    port,
                )
            }.distinctBy { it.address }
    }
}
