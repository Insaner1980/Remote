package com.emmah.colordock.remote.mediacenter

import com.emmah.colordock.remote.mediacenter.protocol.RemoteJson
import com.emmah.colordock.remote.mediacenter.protocol.StrictJson
import org.json.JSONObject

object MediacenterCredentialCodec {
    private const val MAX_BYTES = 65536

    fun decode(clear: ByteArray): StoredMediacenter {
        require(clear.size in 1..MAX_BYTES) { "Pair with Mediacenter again." }
        val input = clear.toString(Charsets.UTF_8)
        StrictJson.validate(input)
        val data = JSONObject(input)
        require(data.keys().asSequence().toSet() == setOf("address", "port", "paired"))
        val address = data.opt("address") as? String ?: error("Invalid Mediacenter address")
        val port = data.opt("port") as? Int ?: error("Invalid Mediacenter port")
        val endpoint = requireNotNull(MediacenterEndpoint.parse(address, port))
        return StoredMediacenter(endpoint, RemoteJson.parsePairedMediacenter(data.getJSONObject("paired").toString()))
    }

    fun encode(value: StoredMediacenter): ByteArray =
        JSONObject()
            .put("address", value.endpoint.address)
            .put("port", value.endpoint.port)
            .put("paired", JSONObject(RemoteJson.encodePairedMediacenter(value.paired)))
            .toString()
            .toByteArray(Charsets.UTF_8)
            .also { decode(it) }
}
