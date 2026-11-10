package com.emmah.colordock.remote.mediacenter

import com.emmah.colordock.remote.mediacenter.protocol.ManualChallengeMode
import com.emmah.colordock.remote.mediacenter.protocol.ManualChallengeRequest
import com.emmah.colordock.remote.mediacenter.protocol.ManualChallengeResponse
import com.emmah.colordock.remote.mediacenter.protocol.ManualPairRequest
import com.emmah.colordock.remote.mediacenter.protocol.PairRequest
import com.emmah.colordock.remote.mediacenter.protocol.PairedMediacenter
import com.emmah.colordock.remote.mediacenter.protocol.PairingCandidate
import com.emmah.colordock.remote.mediacenter.protocol.RemoteJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

class StoredMediacenter(
    val endpoint: MediacenterEndpoint,
    val paired: PairedMediacenter,
) {
    override fun toString(): String = "StoredMediacenter([redacted])"
}

interface MediacenterCredentials {
    suspend fun load(): StoredMediacenter?

    suspend fun save(value: StoredMediacenter)

    suspend fun forget()
}

class VerifiedMediacenterChallenge internal constructor(
    internal val endpoint: MediacenterEndpoint,
    internal val network: MediacenterNetwork,
    internal val pin: String,
    internal val response: ManualChallengeResponse,
) {
    override fun toString(): String = "VerifiedMediacenterChallenge([redacted])"
}

class MediacenterPairing(
    private val store: MediacenterCredentials,
    private val words: List<String>,
) {
    suspend fun pairQr(
        candidate: PairingCandidate,
        network: MediacenterNetwork,
        label: String,
    ): StoredMediacenter =
        coroutineScope {
            RemoteJson.parsePairingCandidate(RemoteJson.encodePairingCandidate(candidate))
            RemoteJson.parsePairRequest(
                RemoteJson.encodePairRequest(PairRequest(candidate.attemptId, candidate.secret, label)),
            )
            val owner = coroutineContext.job
            network.watch { owner.cancel(CancellationException("Selected Wi-Fi unavailable")) }.use {
                val endpoint =
                    withTimeoutOrNull(PROBE_BUDGET_MS) { qrEndpoint(candidate, network) }
                        ?: throw IOException(
                            "No verified Mediacenter address was reachable. Open a new code and check Wi-Fi.",
                        )
                // Only the secret-free probes are retried. An uncertain secret POST is never replayed.
                pairQr(candidate, endpoint, network, label)
            }
        }

    private suspend fun qrEndpoint(
        candidate: PairingCandidate,
        network: MediacenterNetwork,
    ): MediacenterEndpoint? {
        val endpoints =
            candidate.addresses
                .mapNotNull { MediacenterEndpoint.parse(it, candidate.port) }
                .distinctBy { it.address }
        for (endpoint in endpoints) {
            if (!network.available()) throw IOException("Selected local network unavailable")
            val observed =
                try {
                    challenge(endpoint, network, candidate.certificateFingerprint)
                } catch (
                    _: IOException,
                ) {
                    null
                }
            val matches =
                observed?.response?.let {
                    it.serverDeviceId == candidate.deviceId && it.attemptId == candidate.attemptId
                } ==
                    true
            if (matches) return endpoint
        }
        return null
    }

    suspend fun pairQr(
        candidate: PairingCandidate,
        endpoint: MediacenterEndpoint,
        network: MediacenterNetwork,
        label: String,
    ): StoredMediacenter {
        // Revalidate caller-created DTOs as strictly as scanner JSON, before opening a socket.
        RemoteJson.parsePairingCandidate(RemoteJson.encodePairingCandidate(candidate))
        require(
            candidate.port == endpoint.port &&
                candidate.addresses.any { MediacenterEndpoint.parse(it, candidate.port)?.address == endpoint.address },
        )
        val body = RemoteJson.encodePairRequest(PairRequest(candidate.attemptId, candidate.secret, label))
        RemoteJson.parsePairRequest(body)
        val response = MediacenterTransport(endpoint, network, candidate.certificateFingerprint).use { it.post(body) }
        return persist(response, candidate.deviceId, candidate.certificateFingerprint, endpoint)
    }

    suspend fun probe(
        endpoint: MediacenterEndpoint,
        network: MediacenterNetwork,
    ): CertificateObservation = MediacenterTransport.probe(endpoint, network, words)

    suspend fun challenge(
        endpoint: MediacenterEndpoint,
        network: MediacenterNetwork,
        pin: String,
    ): VerifiedMediacenterChallenge {
        val body =
            RemoteJson.encodeManualChallengeRequest(
                ManualChallengeRequest(1, ManualChallengeMode.MANUAL_CHALLENGE),
            )
        val response =
            MediacenterTransport(endpoint, network, pin).use {
                RemoteJson.parseManualChallengeResponse(it.post(body))
            }
        return VerifiedMediacenterChallenge(endpoint, network, pin, response)
    }

    suspend fun pairManual(
        challenge: VerifiedMediacenterChallenge,
        code: String,
        label: String,
    ): StoredMediacenter {
        val response = challenge.response
        RemoteJson.parseManualChallengeResponse(RemoteJson.encodeManualChallengeResponse(response))
        val body = RemoteJson.encodeManualPairRequest(ManualPairRequest(response.attemptId, code, label))
        RemoteJson.parseManualPairRequest(body)
        val paired = MediacenterTransport(challenge.endpoint, challenge.network, challenge.pin).use { it.post(body) }
        return persist(paired, response.serverDeviceId, challenge.pin, challenge.endpoint)
    }

    private suspend fun persist(
        response: String,
        serverId: String,
        pin: String,
        endpoint: MediacenterEndpoint,
    ): StoredMediacenter {
        val paired = RemoteJson.parsePairedMediacenter(response)
        if (paired.serverDeviceId != serverId ||
            paired.certificateFingerprint != pin
        ) {
            throw IOException("The pairing response did not match the verified computer.")
        }
        currentCoroutineContext().ensureActive()
        return StoredMediacenter(endpoint, paired).also { store.save(it) }
    }

    private companion object {
        const val PROBE_BUDGET_MS = 10000L
    }
}
