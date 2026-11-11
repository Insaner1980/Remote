package com.emmah.colordock.remote.ui.mediacenter

import android.Manifest
import android.app.Application
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.emmah.colordock.remote.mediacenter.AndroidMediacenterNetwork
import com.emmah.colordock.remote.mediacenter.CertificateObservation
import com.emmah.colordock.remote.mediacenter.MediacenterConnectionState
import com.emmah.colordock.remote.mediacenter.MediacenterControl
import com.emmah.colordock.remote.mediacenter.MediacenterCredentials
import com.emmah.colordock.remote.mediacenter.MediacenterNetwork
import com.emmah.colordock.remote.mediacenter.MediacenterPairing
import com.emmah.colordock.remote.mediacenter.StoredMediacenter
import com.emmah.colordock.remote.mediacenter.VerifiedMediacenterChallenge
import com.emmah.colordock.remote.mediacenter.protocol.ManualChallengeResponse
import com.emmah.colordock.remote.mediacenter.protocol.McRequest
import com.emmah.colordock.remote.ui.RemoteTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class MediacenterPairingRetryTest {
    @get:Rule val compose = createComposeRule()

    @Test fun failedChallengeShowsFreshProbeAndRequiresNewPhraseBeforeWorkingNextAction() =
        runTest {
            val fixture = RetryFixture(backgroundScope)
            compose.setContent {
                val ui by fixture.ui.collectAsState()
                RemoteTheme {
                    MediacenterManualSetup(
                        ui,
                        {},
                        fixture.controller::probe,
                        ManualVerificationActions(
                            fixture.controller::confirm,
                            fixture.controller::reject,
                            fixture.controller::pairManual,
                        ),
                    )
                }
            }
            compose.onNodeWithText("Computer address").performTextInput("192.168.1.9")
            compose.onNodeWithText("Compare verification phrase").performClick()
            runCurrent()
            val first = fixture.ui.value.observation!!
            compose.onNodeWithText("These words match").performClick()
            runCurrent()
            assertEquals(1, fixture.challenges)
            assertNull(fixture.ui.value.observation)
            assertFalse(fixture.ui.value.codeReady)
            compose.onNodeWithText("These words match").assertDoesNotExist()
            compose.onNodeWithText("Compare verification phrase").assertIsDisplayed()
            compose.onNodeWithText("Computer address").performTextInput("192.168.1.9")
            compose.onNodeWithText("Compare verification phrase").performClick()
            runCurrent()
            val second = fixture.ui.value.observation!!
            assertTrue(first !== second)
            fixture.controller.confirm(first)
            runCurrent()
            assertEquals(1, fixture.challenges)
            assertSame(second, fixture.ui.value.observation)
            assertFalse(fixture.ui.value.codeReady)
            compose.onNodeWithText("These words match").performClick()
            runCurrent()
            assertEquals(listOf(first.fingerprint, second.fingerprint), fixture.pins)
            assertTrue(fixture.ui.value.codeReady)
            compose.onNodeWithText("One-use code").assertIsDisplayed()
            compose.onNodeWithText("Pair Mediacenter").assertIsNotEnabled()
            assertEquals(0, fixture.saved)
            fixture.controller.cancel()
            assertNull(fixture.ui.value.observation)
        }
}

private class RetryFixture(
    scope: CoroutineScope,
) {
    val ui = MutableStateFlow(MediacenterSetupState(lanGranted = true))
    var probes = 0
    var challenges = 0
    var saved = 0
    val pins = mutableListOf<String>()
    private val words = (0 until 2048).map { "word$it" }
    private val application =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private val credentials =
        object : MediacenterCredentials {
            override suspend fun load(): StoredMediacenter? = null

            override suspend fun save(value: StoredMediacenter) {
                saved++
            }

            override suspend fun forget() = Unit
        }
    private val control =
        object : MediacenterControl {
            override val state = MutableStateFlow(MediacenterConnectionState.Disconnected)
            override val messages = emptyFlow<String>()

            override fun connect(
                value: StoredMediacenter,
                network: MediacenterNetwork,
            ) = Unit

            override fun disconnect() = Unit

            override fun send(request: McRequest): Boolean = false
        }
    private val connections = MediacenterConnections(application, credentials, control, scope, ui)
    val controller: MediacenterPairingController

    init {
        shadowOf(application).grantPermissions(Manifest.permission.ACCESS_LOCAL_NETWORK)
        val network = ShadowNetwork.newInstance(777)
        val manager = shadowOf(application.getSystemService(ConnectivityManager::class.java))
        val capabilities = ShadowNetworkCapabilities.newInstance()
        shadowOf(capabilities).addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        manager.setNetworkCapabilities(network, capabilities)
        manager.setLinkProperties(network, LinkProperties().apply { interfaceName = "retry-fixture" })
        connections.select(AndroidMediacenterNetwork(application, network, "retry-fixture"))
        controller =
            MediacenterPairingController(
                MediacenterPairing(credentials, words),
                connections,
                scope,
                ui,
                probeCertificate = { _, _ -> CertificateObservation.capture(byteArrayOf((++probes).toByte()), words) },
                requestChallenge = { endpoint, route, pin ->
                    pins.add(pin)
                    if (++challenges == 1) throw IOException("Fixture challenge failed")
                    VerifiedMediacenterChallenge(endpoint, route, pin, ManualChallengeResponse(1, "server", "attempt"))
                },
            )
    }
}
