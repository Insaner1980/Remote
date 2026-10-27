package com.emmah.colordock.remote.tv

import android.annotation.SuppressLint
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.X509TrustManager

/**
 * Trust is scoped to one explicitly selected TV, never to internet requests.
 * webOS uses self-signed certificates; explicit pairing pins the leaf certificate for subsequent connections.
 */
@SuppressLint("CustomX509TrustManager")
internal class TvCertificateTrust(
    savedFingerprint: String?,
    private val pairing: Boolean,
) : X509TrustManager {
    private val accepted = AtomicReference(savedFingerprint)
    val fingerprint: String? get() = accepted.get()

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

    override fun checkClientTrusted(
        chain: Array<out X509Certificate>,
        authType: String,
    ): Unit = throw CertificateException("Client certificates are not accepted")

    // webOS uses self-signed certificates. Pairing pins the first certificate; every later connection must match it.
    override fun checkServerTrusted(
        chain: Array<out X509Certificate>,
        authType: String,
    ) {
        if (chain.isEmpty()) throw CertificateException("TV certificate missing")
        val candidate = fingerprint(chain[0].encoded)
        if (accepted.get() == null && pairing) accepted.compareAndSet(null, candidate)
        if (accepted.get() != candidate) throw CertificateException("TV certificate does not match the saved identity")
    }

    companion object {
        fun fingerprint(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
                "%02x".format(it)
            }
    }
}
