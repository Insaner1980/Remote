package com.emmah.colordock.remote.mediacenter

import com.emmah.colordock.remote.mediacenter.protocol.PairingCandidate
import com.emmah.colordock.remote.mediacenter.protocol.RemoteJson
import java.util.concurrent.atomic.AtomicBoolean

class ManualPairingSession {
    var observation: CertificateObservation? = null
        private set
    var confirmedPin: String? = null
        private set

    fun show(value: CertificateObservation) {
        observation = value
        confirmedPin = null
    }

    fun confirm(
        displayed: CertificateObservation,
        matches: Boolean,
    ) {
        require(displayed === observation && matches) { "Compare the currently displayed verification words." }
        confirmedPin = displayed.confirm(true)
    }

    fun clear() {
        observation = null
        confirmedPin = null
    }
}

class QrScanSession(
    private val onCandidate: (PairingCandidate) -> Unit,
    private val release: () -> Unit,
) {
    private val closed = AtomicBoolean(false)
    private val emitted = AtomicBoolean(false)

    fun receive(raw: String) {
        if (closed.get() || raw.toByteArray(Charsets.UTF_8).size > MAX_QR_BYTES) return
        val candidate =
            runCatching { RemoteJson.parsePairingCandidate(raw) }
                .getOrNull()
                ?.takeIf { value -> value.addresses.any { MediacenterEndpoint.parse(it, value.port) != null } }
        if (candidate != null && !closed.get() && emitted.compareAndSet(false, true)) {
            close()
            onCandidate(candidate)
        }
    }

    fun close() {
        if (closed.compareAndSet(false, true)) release()
    }

    private companion object {
        const val MAX_QR_BYTES = 8192
    }
}
