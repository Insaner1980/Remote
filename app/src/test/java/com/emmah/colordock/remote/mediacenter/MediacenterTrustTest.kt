package com.emmah.colordock.remote.mediacenter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MediacenterTrustTest {
    @Test fun phraseUsesFirst66HashBitsAndConfirmationRetainsImmutableObservation() {
        val words = (0 until 2048).map { "word$it" }
        // SHA256(empty) = e3b0c44298fc1c149afbf4c8996fb924...
        val observation = CertificateObservation.capture(byteArrayOf(), words)
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", observation.fingerprint)
        assertEquals(listOf("word1821", "word1073", "word133", "word399", "word1550", "word82"), observation.phrase)
        CertificateObservation.capture(byteArrayOf(1), words)
        assertEquals(observation.fingerprint, observation.confirm(true))
        assertThrows(IllegalArgumentException::class.java) { observation.confirm(false) }
    }
}
