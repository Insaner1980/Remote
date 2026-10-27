package com.emmah.colordock.remote.tv

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.ext.DefaultHandler2
import java.io.ByteArrayInputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.SocketTimeoutException
import java.net.URI
import java.util.concurrent.TimeUnit
import javax.xml.parsers.SAXParserFactory
import kotlin.coroutines.coroutineContext

private const val DISCOVERY_TIMEOUT_NANOS = 8_000_000_000L
private const val LAN_NETWORK_TIMEOUT_MILLIS = 1_000L
private const val DESCRIPTION_RESERVE_NANOS = 2_000_000_000L
private const val SOCKET_POLL_MILLIS = 250
private const val SSDP_PORT = 1900
private const val MAX_SSDP_BYTES = 8192
private const val MAX_DISCOVERED_TVS = 16
private const val DESCRIPTION_TIMEOUT_MILLIS = 1000L
private const val MAX_DESCRIPTION_BYTES = 65536
private const val MAX_DESCRIPTION_URL_LENGTH = 2048
private const val MAX_PORT = 65535
private const val MAX_ADVERTISED_ID_LENGTH = 512
private const val MAX_DISCOVERY_NAME_LENGTH = 120

data class DiscoveredTv(
    val address: String,
    val name: String,
    val model: String,
    val advertisedId: String,
)

internal data class SsdpAdvertisement(
    val address: String,
    val description: URI,
    val identity: String,
)

internal suspend fun lanNetwork(context: Context): Network? {
    val manager = context.getSystemService(ConnectivityManager::class.java)
    val active = manager.activeNetwork
    if (active != null && manager.getNetworkCapabilities(active)?.isLan() == true) return active
    return awaitLanNetwork(manager::registerNetworkCallback, manager::unregisterNetworkCallback)
}

internal suspend fun awaitLanNetwork(
    register: (NetworkRequest, ConnectivityManager.NetworkCallback) -> Unit,
    unregister: (ConnectivityManager.NetworkCallback) -> Unit,
): Network? {
    val available = CompletableDeferred<Network>()
    val callback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                if (networkCapabilities.isLan()) available.complete(network)
            }
        }
    val request =
        NetworkRequest
            .Builder()
            .clearCapabilities()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .build()
    register(request, callback)
    return try {
        withTimeoutOrNull(LAN_NETWORK_TIMEOUT_MILLIS) { available.await() }
    } finally {
        unregister(callback)
    }
}

private fun NetworkCapabilities.isLan(): Boolean =
    hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)

class TvDiscovery(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun find(): List<DiscoveredTv> =
        withContext(ioDispatcher) {
            check(
                context.checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) ==
                    PackageManager.PERMISSION_GRANTED,
            )
            val network =
                lanNetwork(context) ?: throw java.io.IOException("Connect to the same Wi-Fi network as your TV.")
            val wifi = context.getSystemService(WifiManager::class.java)
            val lock =
                wifi.createMulticastLock("RemoteDiscovery").apply {
                    setReferenceCounted(false)
                    acquire()
                }
            val deadline = System.nanoTime() + DISCOVERY_TIMEOUT_NANOS
            val found =
                try {
                    discoverAdvertisements(network, deadline)
                } finally {
                    if (lock.isHeld) lock.release()
                }
            loadDescriptions(network, found, deadline)
        }

    private suspend fun discoverAdvertisements(
        network: Network,
        deadline: Long,
    ): Collection<SsdpAdvertisement> {
        val found = linkedMapOf<String, SsdpAdvertisement>()
        DatagramSocket(null).use { socket ->
            network.bindSocket(socket)
            socket.bind(InetSocketAddress(0))
            socket.soTimeout = SOCKET_POLL_MILLIS
            val payload =
                (
                    "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\n" +
                        "MAN: \"ssdp:discover\"\r\nMX: 2\r\nST: $SERVICE\r\n\r\n"
                ).toByteArray(Charsets.US_ASCII)
            socket.send(
                DatagramPacket(payload, payload.size, InetAddress.getByName("239.255.255.250"), SSDP_PORT),
            )
            val bytes = ByteArray(MAX_SSDP_BYTES + 1)
            while (System.nanoTime() <
                deadline - DESCRIPTION_RESERVE_NANOS
            ) {
                coroutineContext.ensureActive()
                val advertisement = receiveAdvertisement(socket, bytes)
                if (advertisement != null && found.size < MAX_DISCOVERED_TVS) {
                    found[advertisement.address] = advertisement
                }
            }
        }
        return found.values
    }

    private fun receiveAdvertisement(
        socket: DatagramSocket,
        bytes: ByteArray,
    ): SsdpAdvertisement? =
        try {
            val packet = DatagramPacket(bytes, bytes.size)
            socket.receive(packet)
            if (packet.length <= MAX_SSDP_BYTES) {
                parsePacket(String(bytes, 0, packet.length, Charsets.UTF_8), packet.address.hostAddress.orEmpty())
            } else {
                null
            }
        } catch (_: SocketTimeoutException) {
            null
        }

    private suspend fun loadDescriptions(
        network: Network,
        found: Collection<SsdpAdvertisement>,
        deadline: Long,
    ): List<DiscoveredTv> {
        val client =
            OkHttpClient
                .Builder()
                .socketFactory(
                    network.socketFactory,
                ).proxy(Proxy.NO_PROXY)
                .followRedirects(false)
                .followSslRedirects(false)
                .build()
        return try {
            found.map { advertisement ->
                coroutineContext.ensureActive()
                try {
                    val remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())
                    require(remaining > 0)
                    val call = client.newCall(Request.Builder().url(advertisement.description.toString()).build())
                    call.timeout().timeout(minOf(remaining, DESCRIPTION_TIMEOUT_MILLIS), TimeUnit.MILLISECONDS)
                    call.execute().use { response ->
                        require(response.isSuccessful && response.body.contentLength() <= MAX_DESCRIPTION_BYTES)
                        val bytes = response.body.byteStream().readNBytes(MAX_DESCRIPTION_BYTES + 1)
                        require(bytes.size <= MAX_DESCRIPTION_BYTES)
                        val (name, model) = parseDescription(bytes)
                        DiscoveredTv(advertisement.address, name, model, advertisement.identity)
                    }
                } catch (_: Exception) {
                    DiscoveredTv(advertisement.address, "LG TV", "", advertisement.identity)
                }
            }
        } finally {
            client.dispatcher.cancelAll()
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }

    companion object {
        const val SERVICE = "urn:lge-com:service:webos-second-screen:1"

        internal fun parsePacket(
            text: String,
            address: String,
        ): SsdpAdvertisement? =
            runCatching {
                require(text.length <= MAX_SSDP_BYTES && TvTarget.parse(address) != null)
                val target = requireNotNull(TvTarget.parse(address))
                val lines = text.split("\r\n")
                require(lines.first() == "HTTP/1.1 200 OK")
                val headers = mutableMapOf<String, String>()
                for (line in lines.drop(1)) {
                    if (line.isEmpty()) break
                    val colon = line.indexOf(':')
                    require(colon > 0)
                    val key = line.substring(0, colon).lowercase()
                    require(key !in headers)
                    headers[key] = line.substring(colon + 1).trim()
                }
                require(headers["st"] == SERVICE)
                val url = URI(requireNotNull(headers["location"]))
                require(
                    url.toString().length <= MAX_DESCRIPTION_URL_LENGTH && url.scheme in
                        listOf(
                            "http",
                            "https",
                        ) && target.matchesHost(url.host) && url.userInfo == null &&
                        url.fragment == null &&
                        (url.port == -1 || url.port in 1..MAX_PORT),
                )
                val identity = headers["usn"].orEmpty().take(MAX_ADVERTISED_ID_LENGTH)
                SsdpAdvertisement(target.address, url, identity)
            }.getOrNull()

        internal fun parseDescription(bytes: ByteArray): Pair<String, String> {
            require(bytes.size <= MAX_DESCRIPTION_BYTES)
            val values = mutableMapOf<String, StringBuilder>()
            val active = mutableMapOf<String, Int>()
            var depth = 0
            val handler =
                object : DefaultHandler2() {
                    override fun startDTD(
                        name: String?,
                        publicId: String?,
                        systemId: String?,
                    ): Unit = throw SAXException("DOCTYPE is not allowed")

                    override fun resolveEntity(
                        publicId: String?,
                        systemId: String?,
                    ): InputSource = throw SAXException("External entities are not allowed")

                    override fun startElement(
                        uri: String?,
                        localName: String,
                        qName: String?,
                        attributes: Attributes?,
                    ) {
                        depth++
                        if (localName in listOf("friendlyName", "modelName") &&
                            localName !in values
                        ) {
                            values[localName] = StringBuilder()
                            active[localName] = depth
                        }
                    }

                    override fun characters(
                        chars: CharArray,
                        start: Int,
                        length: Int,
                    ) {
                        active.keys.forEach {
                            values.getValue(it).append(chars, start, length)
                        }
                    }

                    override fun endElement(
                        uri: String?,
                        localName: String?,
                        qName: String?,
                    ) {
                        active.entries.removeAll {
                            it.value == depth
                        }
                        depth--
                    }
                }
            val reader =
                SAXParserFactory
                    .newInstance()
                    .apply {
                        isNamespaceAware = true
                    }.newSAXParser()
                    .xmlReader
            reader.setFeature("http://xml.org/sax/features/external-general-entities", false)
            reader.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            reader.setProperty("http://xml.org/sax/properties/lexical-handler", handler)
            reader.contentHandler = handler
            reader.entityResolver = handler
            reader.parse(InputSource(ByteArrayInputStream(bytes)))

            fun text(name: String) =
                values[name]
                    ?.toString()
                    ?.trim()
                    ?.take(MAX_DISCOVERY_NAME_LENGTH)
                    .orEmpty()
            return text("friendlyName").ifBlank {
                "LG TV"
            } to text("modelName")
        }
    }
}
