package com.emmah.colordock.remote.mediacenter

import android.content.Context
import android.net.Network
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo

/** Platform boundary retains Network-specific discovery while allowing deterministic callback-race tests. */
internal interface MediacenterNsdBackend {
    fun discover(
        type: String,
        network: Network,
        listener: NsdManager.DiscoveryListener,
    )

    fun observe(
        info: NsdServiceInfo,
        callback: NsdManager.ServiceInfoCallback,
    )

    fun remove(callback: NsdManager.ServiceInfoCallback)

    fun stop(listener: NsdManager.DiscoveryListener)
}

internal class AndroidMediacenterNsdBackend(
    context: Context,
) : MediacenterNsdBackend {
    private val manager = context.getSystemService(NsdManager::class.java)
    private val executor = context.mainExecutor

    override fun discover(
        type: String,
        network: Network,
        listener: NsdManager.DiscoveryListener,
    ) = manager.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, network, executor, listener)

    override fun observe(
        info: NsdServiceInfo,
        callback: NsdManager.ServiceInfoCallback,
    ) = manager.registerServiceInfoCallback(info, executor, callback)

    override fun remove(callback: NsdManager.ServiceInfoCallback) = manager.unregisterServiceInfoCallback(callback)

    override fun stop(listener: NsdManager.DiscoveryListener) = manager.stopServiceDiscovery(listener)
}
