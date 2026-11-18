package com.emmah.colordock.remote.ui.mediacenter

import android.app.Application
import com.emmah.colordock.remote.mediacenter.AndroidMediacenterNetwork
import com.emmah.colordock.remote.mediacenter.MediacenterConnectionState
import com.emmah.colordock.remote.mediacenter.MediacenterControl
import com.emmah.colordock.remote.mediacenter.MediacenterCredentials
import com.emmah.colordock.remote.mediacenter.MediacenterEndpoint
import com.emmah.colordock.remote.mediacenter.MediacenterNetworks
import com.emmah.colordock.remote.mediacenter.MediacenterNsdDiscovery
import com.emmah.colordock.remote.mediacenter.StoredMediacenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class MediacenterConnections(
    private val application: Application,
    private val credentials: MediacenterCredentials,
    private val control: MediacenterControl,
    private val scope: CoroutineScope,
    private val ui: MutableStateFlow<MediacenterSetupState>,
) {
    private val inventory = MediacenterNetworks(application)
    val networks = inventory.networks
    val state = control.state
    private val found = MutableStateFlow<List<MediacenterEndpoint>>(emptyList())
    val candidates = found.asStateFlow()
    private var selected: AndroidMediacenterNetwork? = null
    private var saved: StoredMediacenter? = null
    private var discovery: MediacenterNsdDiscovery? = null
    private var reconnect: Job? = null
    private var loadGeneration = 0L
    private var forgetting = false
    private var reloadBlocked = false
    private var forgettingServerId: String? = null

    fun open(resumeConnection: Boolean = false) {
        fun reconnectSaved() {
            val connection = state.value
            val disconnected =
                connection == MediacenterConnectionState.Disconnected ||
                    connection == MediacenterConnectionState.NetworkUnavailable
            if (reconnect == null || !disconnected) return
            val record = saved?.takeUnless { reloadBlocked } ?: return
            val currentRoute = selected?.takeIf { it.available() }
            val route = currentRoute ?: networks.value.singleOrNull()?.takeIf { it.available() }
            if (route != null) {
                selected = route
                ui.update { it.copy(networkName = route.label, status = "") }
                control.connect(record, route)
            }
        }

        if (reloadBlocked) return
        if (resumeConnection && reconnect == null) {
            reconnect = scope.launch { networks.collect { reconnectSaved() } }
        }
        val owner = ++loadGeneration
        scope.launch {
            try {
                val loaded = credentials.load()
                if (owner == loadGeneration) {
                    saved = loaded
                    ui.update { it.copy(saved = loaded != null) }
                    reconnectSaved()
                }
            } catch (_: Exception) {
                if (owner == loadGeneration) ui.update { it.copy(status = "Pair with Mediacenter again.") }
            }
        }
    }

    fun refreshPermission(): Boolean {
        var granted = AndroidMediacenterNetwork.hasLanPermission(application)
        ui.update { it.copy(lanGranted = granted) }
        if (granted) {
            try {
                inventory.start()
            } catch (_: SecurityException) {
                granted = false
            }
        }
        if (!granted) {
            close(disconnect = true)
            selected = null
            ui.update { it.copy(networkName = null, status = "Allow local network access to connect to Mediacenter.") }
        }
        ui.update { it.copy(lanGranted = granted) }
        return granted
    }

    fun select(network: AndroidMediacenterNetwork) {
        if (forgetting) return
        val previous = selected
        val changed =
            previous != null &&
                (previous.network != network.network || previous.interfaceName != network.interfaceName)
        if (changed) {
            // Retire the route before disconnect observers can request another connection on it.
            selected = null
            control.disconnect()
        }
        stopDiscovery()
        selected = network
        ui.update { it.copy(networkName = network.label, status = "") }
    }

    fun selectedNetwork(): AndroidMediacenterNetwork? =
        selected?.takeIf { !forgetting && it.available() }.also {
            if (it == null &&
                !forgetting
            ) {
                ui.update { state -> state.copy(status = "Select your local Wi-Fi network first.") }
            }
        }

    fun paired(
        value: StoredMediacenter,
        network: AndroidMediacenterNetwork,
    ) {
        if (forgetting) return
        ++loadGeneration
        reloadBlocked = false
        saved = value
        forgettingServerId = null
        ui.update { it.copy(saved = true, forgetFailed = false, status = "Paired with Mediacenter. Connecting…") }
        control.connect(value, network)
    }

    fun connect(endpoint: MediacenterEndpoint? = null) {
        val record = saved ?: return
        val network = selectedNetwork() ?: return
        control.connect(if (endpoint == null) record else StoredMediacenter(endpoint, record.paired), network)
    }

    fun find() {
        val record = saved ?: return
        val network = selectedNetwork() ?: return
        stopDiscovery()
        discovery =
            MediacenterNsdDiscovery(
                application,
                network,
                record.paired.serverDeviceId,
                { found.value = it },
                { message -> ui.update { it.copy(status = message) } },
            ).also { it.start() }
    }

    fun forget() {
        if (forgetting) return
        ++loadGeneration
        forgetting = true
        reloadBlocked = true
        forgettingServerId = saved?.paired?.serverDeviceId ?: forgettingServerId
        saved = null
        stopDiscovery()
        val recoveryCleared = control.forget(forgettingServerId)
        ui.update { it.copy(saved = false, forgetting = true, forgetFailed = false, status = "") }
        scope.launch {
            try {
                if (!recoveryCleared) throw java.io.IOException("Recovery metadata could not be removed.")
                credentials.forget()
                forgettingServerId = null
                reloadBlocked = false
                ui.update { it.copy(status = "Forgot Mediacenter on this phone.") }
            } catch (_: Exception) {
                ui.update {
                    it.copy(
                        forgetFailed = true,
                        status = "Saved Mediacenter data could not be removed. Retry forgetting on this phone.",
                    )
                }
            } finally {
                forgetting = false
                ui.update { it.copy(forgetting = false) }
            }
        }
    }

    fun stopDiscovery() {
        discovery?.close()
        discovery = null
        found.value = emptyList()
    }

    fun close(disconnect: Boolean = false) {
        reconnect?.cancel()
        reconnect = null
        ++loadGeneration
        stopDiscovery()
        inventory.close()
        if (disconnect) control.disconnect()
    }
}
