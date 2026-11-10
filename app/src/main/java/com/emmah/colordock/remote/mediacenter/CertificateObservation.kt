package com.emmah.colordock.remote.mediacenter

import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

/** One immutable observation: a subsequent probe cannot replace the displayed identity. */
class CertificateObservation private constructor(
    val fingerprint: String,
    val phrase: List<String>,
) {
    private val consumed = AtomicBoolean(false)

    fun confirm(matches: Boolean): String {
        require(matches) { "The verification words must match." }
        require(consumed.compareAndSet(false, true)) { "Compare the verification words again." }
        return fingerprint
    }

    override fun toString(): String = "CertificateObservation([redacted])"

    companion object {
        private const val WORD_COUNT = 2048
        private const val PHRASE_WORDS = 6
        private const val WORD_BITS = 11
        private const val BYTE_BITS = 8

        fun fingerprint(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
                "%02x".format(it)
            }

        fun capture(
            certificate: ByteArray,
            words: List<String>,
        ): CertificateObservation {
            require(words.size == WORD_COUNT)
            val hash = MessageDigest.getInstance("SHA-256").digest(certificate)
            val phrase =
                (0 until PHRASE_WORDS).map { word ->
                    var index = 0
                    repeat(WORD_BITS) { bit ->
                        val offset = word * WORD_BITS + bit
                        index =
                            (index shl 1) or
                            ((hash[offset / BYTE_BITS].toInt() ushr (BYTE_BITS - 1 - offset % BYTE_BITS)) and 1)
                    }
                    words[index]
                }
            return CertificateObservation(fingerprint(certificate), Collections.unmodifiableList(phrase))
        }
    }
}
