package com.emmah.colordock.remote.ui.mediacenter

import com.emmah.colordock.remote.mediacenter.AndroidMediacenterNetwork
import com.emmah.colordock.remote.mediacenter.CertificateObservation
import com.emmah.colordock.remote.mediacenter.ManualPairingSession
import com.emmah.colordock.remote.mediacenter.MediacenterEndpoint
import com.emmah.colordock.remote.mediacenter.MediacenterPairing
import com.emmah.colordock.remote.mediacenter.StoredMediacenter
import com.emmah.colordock.remote.mediacenter.VerifiedMediacenterChallenge
import com.emmah.colordock.remote.mediacenter.protocol.PairingCandidate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class MediacenterPairingController(
    private val service: MediacenterPairing,
    private val connections: MediacenterConnections,
    private val scope: CoroutineScope,
    private val ui: MutableStateFlow<MediacenterSetupState>,
    private val probeCertificate: suspend (MediacenterEndpoint, AndroidMediacenterNetwork) -> CertificateObservation =
        service::probe,
    private val requestChallenge: suspend (MediacenterEndpoint, AndroidMediacenterNetwork, String) ->
    VerifiedMediacenterChallenge = service::challenge,
) {
    private var work: Job? = null
    private var generation = 0L
    private val manual = ManualPairingSession()
    private var probed: ManualProbe? = null
    private var challenge: VerifiedMediacenterChallenge? = null

    fun probe(
        address: String,
        port: Int,
    ) {
        val endpoint = MediacenterEndpoint.parse(address, port) ?: return
        val network = connections.selectedNetwork() ?: return
        clear()
        runWork {
            val observation = probeCertificate(endpoint, network)
            probed = ManualProbe(endpoint, network)
            manual.show(observation)
            ui.update { it.copy(observation = observation) }
        }
    }

    fun confirm(displayed: CertificateObservation) {
        val observed = probed ?: return
        val pin =
            runCatching {
                manual.confirm(displayed, true)
                manual.confirmedPin
            }.getOrNull() ?: return
        runWork {
            challenge = requestChallenge(observed.endpoint, observed.network, pin)
            ui.update { it.copy(codeReady = true) }
        }
    }

    fun reject() {
        cancel()
        ui.update { it.copy(status = "Words did not match. Open a new pairing code on the computer.") }
    }

    fun pairManual(code: String) {
        val current = challenge ?: return
        val observed = probed ?: return
        runWork { finish(service.pairManual(current, code, DEVICE_LABEL), observed.network) }
    }

    fun pairQr(candidate: PairingCandidate) {
        val network = connections.selectedNetwork() ?: return
        clear()
        runWork { finish(service.pairQr(candidate, network, DEVICE_LABEL), network) }
    }

    fun cancel() {
        ++generation
        work?.cancel()
        work = null
        clear()
        ui.update { it.copy(pairingBusy = false) }
    }

    private fun finish(
        value: StoredMediacenter,
        network: AndroidMediacenterNetwork,
    ) {
        clear()
        connections.paired(value, network)
    }

    private fun clear() {
        manual.clear()
        probed = null
        challenge = null
        ui.update { it.copy(observation = null, codeReady = false) }
    }

    private fun runWork(block: suspend () -> Unit) {
        work?.cancel()
        val owner = ++generation
        ui.update { it.copy(pairingBusy = true, status = "") }
        work =
            scope.launch {
                try {
                    block()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (owner == generation) {
                        clear()
                        ui.update {
                            it.copy(
                                status =
                                    "Could not pair securely. Check Wi-Fi and open a new pairing code " +
                                        "on the computer.",
                            )
                        }
                    }
                } finally {
                    if (owner == generation) ui.update { it.copy(pairingBusy = false) }
                }
            }
    }

    private class ManualProbe(
        val endpoint: MediacenterEndpoint,
        val network: AndroidMediacenterNetwork,
    )

    private companion object {
        const val DEVICE_LABEL = "Remote phone"
    }
}
