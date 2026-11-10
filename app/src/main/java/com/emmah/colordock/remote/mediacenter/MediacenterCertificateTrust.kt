package com.emmah.colordock.remote.mediacenter

import android.annotation.SuppressLint
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.X509TrustManager

/** Complete leaf pin authenticates this one self-signed MC server, never a general web client. */
@SuppressLint("CustomX509TrustManager")
internal class MediacenterCertificateTrust(
    private val pin: String?,
    private val words: List<String> = emptyList(),
) : X509TrustManager {
    private val observed = AtomicReference<CertificateObservation?>()
    val observation: CertificateObservation? get() = observed.get()

    init {
        require((pin == null && words.size == WORD_COUNT) || pin?.matches(Regex("[a-f0-9]{64}")) == true)
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

    override fun checkClientTrusted(
        chain: Array<out X509Certificate>,
        authType: String,
    ): Unit = throw CertificateException("Client certificates are not accepted")

    override fun checkServerTrusted(
        chain: Array<out X509Certificate>,
        authType: String,
    ) {
        val leaf = chain.firstOrNull() ?: throw CertificateException("Mediacenter certificate missing")
        if (pin == null) {
            observed.compareAndSet(null, CertificateObservation.capture(leaf.encoded, words))
            // A probe ALWAYS rejects TLS: no challenge, code, secret, or other application data can leave.
            throw CertificateException("Compare verification words before pairing")
        }
        leaf.checkValidity()
        if (!MessageDigest.isEqual(
                pin.toByteArray(Charsets.US_ASCII),
                CertificateObservation.fingerprint(leaf.encoded).toByteArray(Charsets.US_ASCII),
            )
        ) {
            throw MediacenterIdentityChangedException()
        }
    }

    private companion object {
        const val WORD_COUNT = 2048
    }
}

internal class MediacenterIdentityChangedException :
    CertificateException("Mediacenter identity changed. Pair again explicitly.")
