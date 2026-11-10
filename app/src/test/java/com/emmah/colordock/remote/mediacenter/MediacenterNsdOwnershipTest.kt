package com.emmah.colordock.remote.mediacenter

import android.net.Network
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetAddress

@RunWith(AndroidJUnit4::class)
class MediacenterNsdOwnershipTest {
    private class Backend : MediacenterNsdBackend {
        lateinit var listener: NsdManager.DiscoveryListener
        val observed = mutableListOf<NsdManager.ServiceInfoCallback>()
        val removed = mutableListOf<NsdManager.ServiceInfoCallback>()

        override fun discover(
            type: String,
            network: Network,
            listener: NsdManager.DiscoveryListener,
        ) {
            this.listener =
                listener
        }

        override fun observe(
            info: NsdServiceInfo,
            callback: NsdManager.ServiceInfoCallback,
        ) {
            observed.add(callback)
        }

        override fun remove(callback: NsdManager.ServiceInfoCallback) {
            removed.add(callback)
        }

        override fun stop(listener: NsdManager.DiscoveryListener) = Unit
    }

    private fun info(
        network: Network,
        lastOctet: Byte,
    ) = NsdServiceInfo().apply {
        serviceName = "fixture"
        serviceType = "_emma-mc._tcp."
        port = 47831
        this.network = network
        hostAddresses = listOf(InetAddress.getByAddress(byteArrayOf(192.toByte(), 168.toByte(), 1, lastOctet)))
        setAttribute("v", "1")
        setAttribute("id", "fixture-server")
    }

    @Test fun staleCallbacksCannotReplaceOrRemoveTheCurrentRegistration() {
        val backend = Backend()
        val network =
            org.robolectric.shadows.ShadowNetwork
                .newInstance(7)
        val published = mutableListOf<List<String>>()
        val errors = mutableListOf<String>()
        val discovery =
            MediacenterNsdDiscovery(
                backend,
                network,
                { true },
                "fixture-server",
                { endpoints -> published.add(endpoints.map { it.address }) },
                errors::add,
            )
        discovery.start()
        val first = info(network, 9)
        backend.listener.onServiceFound(first)
        val old = backend.observed.last()
        old.onServiceUpdated(first)
        backend.listener.onServiceLost(first)
        val replacement = info(network, 10)
        backend.listener.onServiceFound(replacement)
        val current = backend.observed.last()
        current.onServiceUpdated(replacement)
        assertEquals(listOf("192.168.1.10"), published.last())
        old.onServiceUpdated(first)
        assertEquals(listOf("192.168.1.10"), published.last())
        old.onServiceLost()
        assertEquals(listOf("192.168.1.10"), published.last())
        old.onServiceInfoCallbackRegistrationFailed(NsdManager.FAILURE_PERMISSION_DENIED)
        assertTrue(errors.isEmpty())
        discovery.close()
        assertTrue(backend.removed.contains(current))
        val count = published.size
        current.onServiceUpdated(replacement)
        current.onServiceLost()
        current.onServiceInfoCallbackRegistrationFailed(NsdManager.FAILURE_PERMISSION_DENIED)
        backend.listener.onServiceFound(first)
        assertEquals(count, published.size)
        assertEquals(2, backend.observed.size)
        assertTrue(errors.isEmpty())
    }
}
