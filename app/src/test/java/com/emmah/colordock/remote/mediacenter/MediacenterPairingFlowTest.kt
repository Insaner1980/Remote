package com.emmah.colordock.remote.mediacenter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class MediacenterPairingFlowTest {
    @Test fun confirmingAnOldOrUnmatchedPhraseCannotTrustAReplacementProbe() {
        val words = (0 until 2048).map { "word$it" }
        val first = CertificateObservation.capture(byteArrayOf(1), words)
        val second = CertificateObservation.capture(byteArrayOf(2), words)
        val session = ManualPairingSession()
        session.show(first)
        session.show(second)
        assertThrows(IllegalArgumentException::class.java) { session.confirm(first, true) }
        assertNull(session.confirmedPin)
        session.confirm(second, true)
        assertEquals(second.fingerprint, session.confirmedPin)
        session.clear()
        assertNull(session.observation)
        assertNull(session.confirmedPin)
    }

    @Test fun scannerBoundsQrOnlyPayloadsAndReleasesOnceOnDismissal() {
        var candidates = 0
        var releases = 0
        val scanner = QrScanSession({ candidates++ }, { releases++ })
        scanner.receive("https://example.com")
        scanner.receive("x".repeat(9000))
        val qr = """{"v":1,"deviceId":"server","addresses":["192.168.1.9"],"port":47831,"certificateFingerprint":"${"ab".repeat(
            32,
        )}","attemptId":"attempt","secret":"${"A".repeat(43)}"}"""
        scanner.receive(qr)
        assertEquals(1, candidates)
        scanner.close()
        scanner.close()
        scanner.receive(qr)
        assertEquals(1, releases)
        assertEquals(1, candidates)
    }

    @Test fun scannerClosesOwnedResourcesAndLateCameraCallbacksExactlyOnce() {
        val resources = MediacenterScannerResources()
        val closed = mutableListOf<String>()
        resources.own(AutoCloseable { closed.add("decoder") })
        resources.own(AutoCloseable { closed.add("image-reader") })
        resources.own(AutoCloseable { closed.add("camera") })
        resources.close()
        resources.close()
        org.junit.Assert.assertFalse(resources.own(AutoCloseable { closed.add("late-session") }))
        assertEquals(listOf("camera", "image-reader", "decoder", "late-session"), closed)
    }
}
