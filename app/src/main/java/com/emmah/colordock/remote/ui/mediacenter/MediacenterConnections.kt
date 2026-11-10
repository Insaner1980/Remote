package com.emmah.colordock.remote.ui.mediacenter

import android.app.Application
import com.emmah.colordock.remote.mediacenter.AndroidMediacenterNetwork
import com.emmah.colordock.remote.mediacenter.MediacenterControl
import com.emmah.colordock.remote.mediacenter.MediacenterCredentials
import com.emmah.colordock.remote.mediacenter.MediacenterEndpoint
import com.emmah.colordock.remote.mediacenter.MediacenterNetworks
import com.emmah.colordock.remote.mediacenter.MediacenterNsdDiscovery
import com.emmah.colordock.remote.mediacenter.StoredMediacenter
import kotlinx.coroutines.CoroutineScope
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
    private var loadGeneration = 0L

    fun open() {
        val owner = ++loadGeneration
        scope.launch {
            try {
                val loaded = credentials.load()
                if (owner == loadGeneration) {
                    saved = loaded
                    ui.update { it.copy(saved = loaded != null) }
                }
            } catch (_: Exception) {
                ui.update { it.copy(status = "Pair with Mediacenter again.") }
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
        stopDiscovery()
        selected = network
        ui.update { it.copy(networkName = network.label, status = "") }
    }

    fun selectedNetwork(): AndroidMediacenterNetwork? =
        selected?.takeIf { it.available() }.also {
            if (it == null) ui.update { state -> state.copy(status = "Select your local Wi-Fi network first.") }
        }

    fun paired(
        value: StoredMediacenter,
        network: AndroidMediacenterNetwork,
    ) {
        ++loadGeneration
        saved = value
        ui.update { it.copy(saved = true, status = "Paired with Mediacenter. Connecting…") }
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
        ++loadGeneration
        stopDiscovery()
        control.disconnect()
        scope.launch {
            try {
                credentials.forget()
                saved = null
                ui.update { it.copy(saved = false, status = "Forgot Mediacenter on this phone.") }
            } catch (_: Exception) {
                ui.update { it.copy(status = "The saved Mediacenter could not be removed.") }
            }
        }
    }

    fun stopDiscovery() {
        discovery?.close()
        discovery = null
        found.value = emptyList()
    }

    fun close(disconnect: Boolean = false) {
        stopDiscovery()
        inventory.close()
        if (disconnect) control.disconnect()
    }
}
