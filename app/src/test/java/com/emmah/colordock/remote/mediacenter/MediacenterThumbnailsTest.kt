package com.emmah.colordock.remote.mediacenter

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.emmah.colordock.remote.mediacenter.protocol.PairedMediacenter
import com.emmah.colordock.remote.mediacenter.protocol.Scope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.net.InetAddress
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory

@RunWith(AndroidJUnit4::class)
class MediacenterThumbnailsTest {
    @Test fun fourRequestsMaximumAndForgetRetiresInflightAndQueuedCacheWrites() =
        runBlocking {
            fixture { server, cache, _ ->
                repeat(5) {
                    server.enqueue(
                        MockResponse
                            .Builder()
                            .addHeader(
                                "Content-Type",
                                "image/png",
                            ).body(Buffer().write(png()))
                            .bodyDelay(1, TimeUnit.SECONDS)
                            .build(),
                    )
                }
                val work = (1..5).map { n -> async { cache.load(n.toString(16).padStart(64, '0')) } }
                withTimeout(4000) { while (server.requestCount < 4) delay(10) }
                assertEquals(4, server.requestCount)
                cache.forget()
                work.forEach { assertNull(it.await()) }
                assertEquals(4, server.requestCount)
            }
        }

    @Test fun authenticatedPinnedRouteCachesBoundedImagesAndEvictsOldestBytes() =
        runBlocking {
            fixture { server, cache, root ->
                val old = File(root, "old").apply { mkdirs() }
                repeat(5) { index ->
                    RandomAccessFile(File(old, "$index"), "rw").use { it.setLength(25L * 1024 * 1024) }
                    File(old, "$index").setLastModified(index.toLong() + 1)
                }
                val bytes = png()
                server.enqueue(
                    MockResponse
                        .Builder()
                        .addHeader("Content-Type", "image/png")
                        .body(Buffer().write(bytes))
                        .build(),
                )
                val id = "a".repeat(64)
                assertArrayEquals(bytes, cache.load(id))
                assertArrayEquals(bytes, cache.load(id))
                assertEquals(1, server.requestCount)
                val request = server.takeRequest()
                assertEquals("/remote/v1/thumbnails/$id", request.url.encodedPath)
                assertEquals("Bearer " + "a".repeat(43), request.headers["Authorization"])
                assertTrue(root.walkTopDown().filter { it.isFile }.sumOf { it.length() } <= 100L * 1024 * 1024)
                assertFalse(File(old, "0").exists())
                cache.forget()
                assertFalse(root.exists())
            }
        }

    @Test fun oversizedOrNonImageResponsesNeverEnterCache() =
        runBlocking {
            fixture { server, cache, root ->
                server.enqueue(
                    MockResponse
                        .Builder()
                        .addHeader("Content-Type", "image/png")
                        .body("x".repeat(512 * 1024 + 1))
                        .build(),
                )
                assertNull(cache.load("b".repeat(64)))
                server.enqueue(
                    MockResponse
                        .Builder()
                        .addHeader("Content-Type", "image/png")
                        .body(Buffer().write(png(513)))
                        .build(),
                )
                assertNull(cache.load("c".repeat(64)))
                assertFalse(root.walkTopDown().any { it.isFile })
            }
        }

    private suspend fun fixture(block: suspend (MockWebServer, MediacenterThumbnails, File) -> Unit) {
        val cert = HeldCertificate.Builder().build()
        val root = Files.createTempDirectory("phone-thumbnails").toFile().resolve("mediacenter-thumbnails")
        MockWebServer()
            .apply {
                useHttps(
                    HandshakeCertificates
                        .Builder()
                        .heldCertificate(cert)
                        .build()
                        .sslSocketFactory(),
                )
                start()
            }.use { server ->
                val network =
                    object : MediacenterNetwork {
                        override val socketFactory: SocketFactory = SocketFactory.getDefault()

                        override fun available() = true

                        override fun resolve(endpoint: MediacenterEndpoint) =
                            listOf(InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)))
                    }
                val value =
                    StoredMediacenter(
                        MediacenterEndpoint.parse("192.168.1.2", server.port)!!,
                        PairedMediacenter(
                            "server",
                            "phone",
                            "a".repeat(43),
                            CertificateObservation.fingerprint(cert.certificate.encoded),
                            listOf(Scope.BROWSE),
                        ),
                    )
                val cache = MediacenterThumbnails(root)
                try {
                    cache.select(value, network)
                    block(server, cache, root)
                } finally {
                    cache.forget()
                    root.parentFile?.deleteRecursively()
                }
            }
    }

    private fun png(edge: Int = 1): ByteArray =
        ByteArrayOutputStream().use { output ->
            Bitmap.createBitmap(edge, 1, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, output)
            output.toByteArray()
        }
}
