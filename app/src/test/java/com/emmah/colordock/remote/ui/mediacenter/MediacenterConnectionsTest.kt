package com.emmah.colordock.remote.ui.mediacenter

import android.Manifest
import android.app.Application
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.emmah.colordock.remote.mediacenter.AndroidMediacenterNetwork
import com.emmah.colordock.remote.mediacenter.MediacenterConnectionState
import com.emmah.colordock.remote.mediacenter.MediacenterControl
import com.emmah.colordock.remote.mediacenter.MediacenterCredentials
import com.emmah.colordock.remote.mediacenter.MediacenterEndpoint
import com.emmah.colordock.remote.mediacenter.MediacenterNetwork
import com.emmah.colordock.remote.mediacenter.MediacenterPairing
import com.emmah.colordock.remote.mediacenter.StoredMediacenter
import com.emmah.colordock.remote.mediacenter.protocol.McRequest
import com.emmah.colordock.remote.mediacenter.protocol.PairedMediacenter
import com.emmah.colordock.remote.mediacenter.protocol.Scope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class MediacenterConnectionsTest {
    private class Control : MediacenterControl {
        override val state = MutableStateFlow(MediacenterConnectionState.Disconnected)
        override val messages = emptyFlow<String>()
        var active: MediacenterNetwork? = null
        var onDisconnect: () -> Unit = {}

        override fun connect(
            value: StoredMediacenter,
            network: MediacenterNetwork,
        ) {
            active = network
            state.value = MediacenterConnectionState.Connected
        }

        override fun send(request: McRequest): Boolean = active != null

        override fun disconnect() {
            active = null
            state.value = MediacenterConnectionState.Disconnected
            onDisconnect()
        }
    }

    private class Credentials(
        initial: StoredMediacenter,
    ) : MediacenterCredentials {
        var stored: StoredMediacenter? = initial
        var failure: IOException? = null
        var suspendLoad = false
        val loadStarted = CompletableDeferred<Unit>()
        val releaseLoad = CompletableDeferred<Unit>()
        val forgetStarted = CompletableDeferred<Unit>()
        val releaseForget = CompletableDeferred<Unit>()
        var forgetCalls = 0

        override suspend fun load(): StoredMediacenter? {
            val captured = stored
            if (suspendLoad) {
                loadStarted.complete(Unit)
                releaseLoad.await()
            }
            return captured
        }

        override suspend fun save(value: StoredMediacenter) {
            stored = value
        }

        override suspend fun forget() {
            forgetCalls++
            forgetStarted.complete(Unit)
            releaseForget.await()
            failure?.let { throw it }
            stored = null
        }
    }

    private class Fixture(
        scope: CoroutineScope,
    ) {
        private val application =
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        private val connectivity = shadowOf(application.getSystemService(ConnectivityManager::class.java))
        val record =
            StoredMediacenter(
                requireNotNull(MediacenterEndpoint.parse("192.168.1.9")),
                PairedMediacenter(
                    "fixture-server",
                    "fixture-phone",
                    "A".repeat(43),
                    "ab".repeat(32),
                    listOf(Scope.BROWSE),
                ),
            )
        val store = Credentials(record)
        val control = Control()
        val ui = MutableStateFlow(MediacenterSetupState())
        val connections = MediacenterConnections(application, store, control, scope, ui)
        val first: AndroidMediacenterNetwork
        val second: AndroidMediacenterNetwork

        init {
            shadowOf(application).grantPermissions(Manifest.permission.ACCESS_LOCAL_NETWORK)
            first = route(7, "wlanA")
            second = route(8, "wlanB")
            connections.select(first)
            connections.paired(record, first)
        }

        private fun route(
            id: Int,
            interfaceName: String,
        ): AndroidMediacenterNetwork {
            val network = ShadowNetwork.newInstance(id)
            val capabilities = ShadowNetworkCapabilities.newInstance()
            shadowOf(capabilities).addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            connectivity.setNetworkCapabilities(network, capabilities)
            connectivity.setLinkProperties(network, LinkProperties().apply { this.interfaceName = interfaceName })
            return AndroidMediacenterNetwork(application, network, interfaceName)
        }

        fun sameSecondRoute() = AndroidMediacenterNetwork(application, second.network, second.interfaceName)
    }

    @Test fun selectingAnotherNetworkRetiresTheOldOwnerBeforeDisplayingTheNewRoute() =
        runTest {
            val fixture = Fixture(this)
            var labelAtRetirement: String? = null
            fixture.control.onDisconnect = {
                labelAtRetirement = fixture.ui.value.networkName
                fixture.connections.connect()
            }
            fixture.connections.select(fixture.second)
            assertNull(fixture.control.active)
            assertEquals(fixture.first.label, labelAtRetirement)
            assertEquals(fixture.second.label, fixture.ui.value.networkName)
            assertSame(fixture.second, fixture.connections.selectedNetwork())
            fixture.connections.connect()
            assertSame(fixture.second, fixture.control.active)
            fixture.connections.select(fixture.sameSecondRoute())
            assertSame(fixture.second, fixture.control.active)
        }

    @Test fun suspendedForgetBlocksReconnectReloadAndDuplicateDeletion() =
        runTest {
            val fixture = Fixture(this)
            try {
                fixture.connections.forget()
                runCurrent()
                assertTrue(fixture.store.forgetStarted.isCompleted)
                fixture.connections.connect()
                assertNull(fixture.control.active)
                fixture.connections.open()
                fixture.connections.forget()
                runCurrent()
                fixture.connections.connect()
                assertNull(fixture.control.active)
                assertFalse(fixture.ui.value.saved)
                assertTrue(fixture.ui.value.busy)
                assertEquals(1, fixture.store.forgetCalls)
            } finally {
                fixture.store.releaseForget.complete(Unit)
            }
            runCurrent()
            fixture.connections.connect()
            assertNull(fixture.control.active)
            assertNull(fixture.store.stored)
            assertFalse(fixture.ui.value.saved)
            assertFalse(fixture.ui.value.busy)
        }

    @Test fun forgetInvalidatesCachedOwnershipBeforeDisconnectObserversCanReconnect() =
        runTest {
            val fixture = Fixture(this)
            fixture.control.onDisconnect = { fixture.connections.connect() }
            try {
                fixture.connections.forget()
                assertNull(fixture.control.active)
                assertFalse(fixture.ui.value.saved)
                assertTrue(fixture.ui.value.busy)
            } finally {
                fixture.store.releaseForget.complete(Unit)
            }
            runCurrent()
            assertNull(fixture.control.active)
        }

    @Test fun failedForgetKeepsCredentialUnavailableAndAllowsExplicitDeletionRetry() =
        runTest {
            val fixture = Fixture(this)
            fixture.store.failure = IOException("fixture storage unavailable")
            fixture.connections.forget()
            runCurrent()
            fixture.store.releaseForget.complete(Unit)
            runCurrent()
            fixture.connections.connect()
            assertNull(fixture.control.active)
            assertNotNull(fixture.store.stored)
            assertFalse(fixture.ui.value.saved)
            assertFalse(fixture.ui.value.busy)
            assertTrue(
                fixture.ui.value.status
                    .isNotBlank(),
            )
            fixture.connections.open()
            runCurrent()
            fixture.connections.connect()
            assertNull(fixture.control.active)
            fixture.store.failure = null
            fixture.connections.forget()
            runCurrent()
            assertEquals(2, fixture.store.forgetCalls)
            assertFalse(fixture.ui.value.forgetFailed)
            assertNull(fixture.store.stored)
            assertFalse(fixture.ui.value.saved)
            assertFalse(fixture.ui.value.busy)
            fixture.connections.connect()
            assertNull(fixture.control.active)
        }

    @Test fun aLoadSuspendedBeforeForgetCannotRestoreTheDeletedCredential() =
        runTest {
            val fixture = Fixture(this)
            fixture.store.suspendLoad = true
            fixture.connections.open()
            runCurrent()
            assertTrue(fixture.store.loadStarted.isCompleted)
            try {
                fixture.connections.forget()
                runCurrent()
                fixture.store.releaseForget.complete(Unit)
                runCurrent()
            } finally {
                fixture.store.releaseForget.complete(Unit)
                fixture.store.releaseLoad.complete(Unit)
            }
            runCurrent()
            fixture.connections.connect()
            assertNull(fixture.control.active)
            assertNull(fixture.store.stored)
            assertFalse(fixture.ui.value.saved)
        }
}
