package com.emmah.colordock.remote.tv

import okhttp3.tls.HeldCertificate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.security.MessageDigest
import java.security.cert.CertificateException

class TvCertificateTrustTest {
    private val first =
        HeldCertificate
            .Builder()
            .commonName("TV fixture one")
            .build()
            .certificate
    private val second =
        HeldCertificate
            .Builder()
            .commonName("TV fixture two")
            .build()
            .certificate

    private fun hash(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
            "%02x".format(it)
        }

    @Test fun savedPinMismatch_rejectsThePeerAndKeepsTheOriginalPin() {
        val original = hash(first.encoded)
        val policy = TvCertificateTrust(original, false)
        assertThrows(CertificateException::class.java) {
            policy.checkServerTrusted(arrayOf(second), "RSA")
        }
        assertEquals(original, policy.fingerprint)
        policy.checkServerTrusted(arrayOf(first), "RSA")
    }

    @Test fun firstUseCandidate_cannotChangeWithinThePairingAttempt() {
        val policy = TvCertificateTrust(null, true)
        policy.checkServerTrusted(arrayOf(first), "RSA")
        assertThrows(CertificateException::class.java) {
            policy.checkServerTrusted(arrayOf(second), "RSA")
        }
        assertEquals(hash(first.encoded), policy.fingerprint)
    }

    @Test fun unpinnedConnection_requiresExplicitPairing() {
        val policy = TvCertificateTrust(null, false)
        assertThrows(CertificateException::class.java) {
            policy.checkServerTrusted(arrayOf(first), "RSA")
        }
        assertNull(policy.fingerprint)
    }
}
