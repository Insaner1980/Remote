package com.emmah.colordock.remote.ui.mediacenter

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.emmah.colordock.remote.ColorDockApplication
import com.emmah.colordock.remote.R
import com.emmah.colordock.remote.mediacenter.AndroidMediacenterNetwork
import com.emmah.colordock.remote.mediacenter.CertificateObservation
import com.emmah.colordock.remote.mediacenter.MediacenterControl
import com.emmah.colordock.remote.mediacenter.MediacenterCredentials
import com.emmah.colordock.remote.mediacenter.MediacenterPairing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal data class MediacenterSetupState(
    val status: String = "",
    val pairingBusy: Boolean = false,
    val forgetting: Boolean = false,
    val forgetFailed: Boolean = false,
    val lanGranted: Boolean = false,
    val cameraDenied: Boolean = false,
    val networkName: String? = null,
    val observation: CertificateObservation? = null,
    val codeReady: Boolean = false,
    val saved: Boolean = false,
) {
    val busy: Boolean get() = pairingBusy || forgetting
}

class MediacenterViewModel internal constructor(
    application: Application,
    private val control: MediacenterControl,
    credentials: MediacenterCredentials,
    words: List<String>,
) : AndroidViewModel(application) {
    constructor(application: Application) : this(
        application,
        (application as ColorDockApplication).container.mediacenter,
        application.container.mediacenterCredentials,
        application.resources
            .openRawResource(R.raw.mediacenter_bip39_en_v1)
            .bufferedReader()
            .use { it.readLines() },
    )

    private val mutable = MutableStateFlow(MediacenterSetupState())
    internal val ui = mutable.asStateFlow()
    internal val connections = MediacenterConnections(application, credentials, control, viewModelScope, mutable)
    internal val pairing =
        MediacenterPairingController(MediacenterPairing(credentials, words), connections, viewModelScope, mutable)
    val networks = connections.networks
    val connection = connections.state
    val candidates = connections.candidates

    fun open() {
        refreshPermission()
        connections.open()
    }

    fun refreshPermission() {
        if (!connections.refreshPermission()) pairing.cancel()
    }

    fun select(network: AndroidMediacenterNetwork) {
        pairing.cancel()
        connections.select(network)
    }

    fun cameraResult(granted: Boolean) {
        mutable.update { it.copy(cameraDenied = !granted) }
    }

    fun cameraError(message: String) {
        mutable.update { it.copy(status = message) }
    }

    fun foreground() {
        refreshPermission()
        control.foreground()
        if (mutable.value.lanGranted) connections.open(resumeConnection = true)
    }

    fun background() {
        pairing.cancel()
        connections.close()
        control.background()
    }

    fun closeSetup() {
        pairing.cancel()
        connections.stopDiscovery()
    }

    fun disconnect() {
        background()
        connections.close(disconnect = true)
    }

    override fun onCleared() {
        pairing.cancel()
        connections.close()
    }
}
