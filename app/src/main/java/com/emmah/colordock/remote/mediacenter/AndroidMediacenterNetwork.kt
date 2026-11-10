package com.emmah.colordock.remote.mediacenter

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.SocketFactory

class AndroidMediacenterNetwork(
    context: Context,
    val network: Network,
    val interfaceName: String,
) : MediacenterNetwork {
    private val application = context.applicationContext
    private val manager = application.getSystemService(ConnectivityManager::class.java)
    val label: String get() = "Wi-Fi · $interfaceName"
    override val socketFactory: SocketFactory get() = network.socketFactory

    override fun permissionGranted(): Boolean = hasLanPermission(application)

    override fun available(): Boolean =
        hasLanPermission(application) &&
            isWifi(manager.getNetworkCapabilities(network)) &&
            manager.getLinkProperties(network)?.interfaceName == interfaceName

    override fun resolve(endpoint: MediacenterEndpoint): List<InetAddress> {
        if (!available()) throw IOException("Selected Wi-Fi is unavailable.")
        val literal = endpoint.literal
        if (literal is Inet6Address && literal.isLinkLocalAddress) {
            val index =
                NetworkInterface.getByName(interfaceName)?.index ?: throw IOException("Wi-Fi interface unavailable")
            if (index <= 0) throw IOException("Wi-Fi interface unavailable")
            return listOf(endpoint.scopedAddress(index))
        }
        return listOf(literal)
    }

    override fun watch(onLost: () -> Unit): AutoCloseable {
        val closed = AtomicBoolean(false)
        val callback =
            object : ConnectivityManager.NetworkCallback() {
                override fun onLost(lost: Network) {
                    if (lost == network && !closed.get()) onLost()
                }

                override fun onCapabilitiesChanged(
                    changed: Network,
                    capabilities: NetworkCapabilities,
                ) {
                    if (changed == network && !isWifi(capabilities) && !closed.get()) onLost()
                }

                override fun onLinkPropertiesChanged(
                    changed: Network,
                    properties: LinkProperties,
                ) {
                    if (changed == network && properties.interfaceName != interfaceName && !closed.get()) onLost()
                }
            }
        manager.registerNetworkCallback(wifiRequest(), callback)
        return AutoCloseable { if (closed.compareAndSet(false, true)) manager.unregisterNetworkCallback(callback) }
    }

    companion object {
        fun hasLanPermission(context: Context): Boolean =
            context.checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED

        internal fun isWifi(capabilities: NetworkCapabilities?): Boolean =
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)

        internal fun wifiRequest(): NetworkRequest =
            NetworkRequest
                .Builder()
                .addTransportType(
                    NetworkCapabilities.TRANSPORT_WIFI,
                ).addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build()
    }
}

/** Callback inventory avoids choosing or rebinding the process-default network. */
class MediacenterNetworks(
    context: Context,
) : AutoCloseable {
    private val application = context.applicationContext
    private val manager = application.getSystemService(ConnectivityManager::class.java)
    private val choices = linkedMapOf<Network, AndroidMediacenterNetwork>()
    private val mutable = MutableStateFlow<List<AndroidMediacenterNetwork>>(emptyList())
    val networks = mutable.asStateFlow()

    @Volatile private var started = false
    private val callback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = refresh(network)

            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities,
            ) = refresh(network)

            override fun onLinkPropertiesChanged(
                network: Network,
                properties: LinkProperties,
            ) = refresh(network)

            override fun onLost(network: Network) {
                synchronized(choices) {
                    choices.remove(network)
                    mutable.value =
                        choices.values.toList()
                }
            }
        }

    fun start() {
        if (!started && AndroidMediacenterNetwork.hasLanPermission(application)) {
            started = true
            try {
                manager.registerNetworkCallback(AndroidMediacenterNetwork.wifiRequest(), callback)
            } catch (
                error: SecurityException,
            ) {
                started = false
                throw error
            }
        }
    }

    private fun refresh(network: Network) {
        if (!started) return
        val name = manager.getLinkProperties(network)?.interfaceName
        synchronized(choices) {
            if (name != null && AndroidMediacenterNetwork.isWifi(manager.getNetworkCapabilities(network)) &&
                AndroidMediacenterNetwork.hasLanPermission(application)
            ) {
                choices[network] = AndroidMediacenterNetwork(application, network, name)
            } else {
                choices.remove(network)
            }
            mutable.value = choices.values.toList()
        }
    }

    override fun close() {
        if (started) {
            started = false
            manager.unregisterNetworkCallback(callback)
        }
        synchronized(choices) {
            choices.clear()
            mutable.value = emptyList()
        }
    }
}
