package com.emmah.colordock.remote.tv

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LanNetworkTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val network: Network get() = requireNotNull(manager.activeNetwork)

    @Test fun preservesActiveLanRoute() =
        runBlocking {
            val active = network
            val capabilities = requireNotNull(manager.getNetworkCapabilities(active))
            assertTrue(
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET),
            )
            assertEquals(active, lanNetwork(context))
        }

    @Test fun receivesLanThroughPlatformCallback() =
        runBlocking {
            assertNotNull(awaitLanNetwork(manager::registerNetworkCallback, manager::unregisterNetworkCallback))
        }

    @Test fun ignoresNonLanAndRequestsWifiOrEthernetWithoutInternetOrVpnRestrictions() =
        runBlocking {
            val registered = CompletableDeferred<ConnectivityManager.NetworkCallback>()
            var released: ConnectivityManager.NetworkCallback? = null
            val lookup =
                async(start = CoroutineStart.UNDISPATCHED) {
                    awaitLanNetwork(
                        register = { request, callback ->
                            assertTrue(request.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
                            assertTrue(request.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
                            assertFalse(request.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
                            assertFalse(request.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN))
                            registered.complete(callback)
                        },
                        unregister = { released = it },
                    )
                }
            val callback = registered.await()
            val selected = network
            callback.onCapabilitiesChanged(selected, NetworkCapabilities())
            assertFalse(lookup.isCompleted)
            val capabilities = requireNotNull(manager.getNetworkCapabilities(selected))
            callback.onCapabilitiesChanged(selected, capabilities)
            callback.onCapabilitiesChanged(selected, capabilities)
            assertEquals(selected, lookup.await())
            assertSame(callback, released)
        }

    @Test fun immediateCallbackUnregistersOnce() =
        runBlocking {
            val selected = network
            val capabilities = requireNotNull(manager.getNetworkCapabilities(selected))
            var released = 0
            val result =
                awaitLanNetwork(
                    register = { _, callback ->
                        callback.onCapabilitiesChanged(selected, capabilities)
                    },
                    unregister = { released++ },
                )
            assertEquals(selected, result)
            assertEquals(1, released)
        }

    @Test fun timeoutReturnsNoNetworkAndUnregisters() =
        runBlocking {
            var registered: ConnectivityManager.NetworkCallback? = null
            var released: ConnectivityManager.NetworkCallback? = null
            assertNull(
                awaitLanNetwork(
                    register = { _, callback -> registered = callback },
                    unregister = { released = it },
                ),
            )
            assertNotNull(registered)
            assertSame(registered, released)
        }

    @Test fun cancellationUnregistersAndDoesNotReturnAResult() =
        runBlocking {
            var registered: ConnectivityManager.NetworkCallback? = null
            var released: ConnectivityManager.NetworkCallback? = null
            val lookup =
                async(start = CoroutineStart.UNDISPATCHED) {
                    awaitLanNetwork(
                        register = { _, callback -> registered = callback },
                        unregister = { released = it },
                    )
                }
            lookup.cancelAndJoin()
            assertTrue(lookup.isCancelled)
            assertNotNull(registered)
            assertSame(registered, released)
        }

    @Test fun registrationFailurePropagatesWithoutUnregistering() =
        runBlocking {
            var released = false
            val failure = SecurityException("Network permission denied")
            try {
                awaitLanNetwork(
                    register = { _, _ -> throw failure },
                    unregister = { released = true },
                )
                fail("Registration failure must propagate")
            } catch (caught: SecurityException) {
                assertSame(failure, caught)
            }
            assertFalse(released)
        }
}
