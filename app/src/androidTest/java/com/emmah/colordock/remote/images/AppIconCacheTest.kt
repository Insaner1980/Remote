package com.emmah.colordock.remote.images

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.emmah.colordock.remote.tv.TvApp
import com.emmah.colordock.remote.tv.TvResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class AppIconCacheTest {
    private val app = TvApp("service", "Service", "https://192.168.1.10:3001/resources/icon.png")

    private fun png(
        width: Int = 130,
        height: Int = 130,
        color: Int = android.graphics.Color.RED,
    ): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        return ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            bitmap.recycle()
            output.toByteArray()
        }
    }

    private fun scenario(block: suspend CoroutineScope.(Context, File) -> Unit) =
        runBlocking {
            val base = InstrumentationRegistry.getInstrumentation().targetContext
            val directory =
                File(base.cacheDir, "app-icons-test-${System.nanoTime()}").apply {
                    mkdirs()
                }
            val context =
                object : ContextWrapper(base) {
                    override fun getCacheDir() = directory
                }
            try {
                withTimeout(10_000) {
                    block(context, directory)
                }
            } finally {
                directory.deleteRecursively()
            }
        }

    @Test fun validImagesAreSampledAndCachedByIdentity() =
        scenario { context, _ ->
            var calls = 0
            val cache =
                AppIconCache(context) {
                    calls++
                    TvResult.Success(png(1024, 512))
                }
            val first = requireNotNull(cache.get("tv-a", app))
            val image = BitmapFactory.decodeFile(first.path)
            assertTrue(image.width <= 256)
            assertTrue(image.height <= 256)
            image.recycle()
            assertEquals(first, cache.get("tv-a", app))
            assertEquals(1, calls)
            assertNotEquals(first, cache.get("tv-b", app))
            assertEquals(2, calls)
            assertNotEquals(first, cache.get("tv-a", app.copy(id = "../../another")))
        }

    @Test fun invalidAndOversizedImagesNeverBecomeCacheEntries() =
        scenario { context, directory ->
            for (data in listOf(byteArrayOf(1, 2, 3), png(4097, 1), ByteArray(1_048_577), png(4001, 4000))) {
                val cache =
                    AppIconCache(context) {
                        TvResult.Success(data)
                    }
                assertNull(cache.get("tv", app, refresh = true))
            }
            assertTrue(File(directory, "tv-app-icons").listFiles().orEmpty().isEmpty())
        }

    @Test fun unchangedUrlRefreshReplacesArtworkAndFailureKeepsAuthenticCache() =
        scenario { context, _ ->
            var data: TvResult<ByteArray> = TvResult.Success(png())
            val cache =
                AppIconCache(context) {
                    data
                }
            val first = requireNotNull(cache.get("tv", app))
            data = TvResult.Success(png(color = android.graphics.Color.BLUE))
            val refreshed = requireNotNull(cache.get("tv", app, refresh = true))
            assertNotEquals(first, refreshed)
            val bitmap = BitmapFactory.decodeFile(refreshed.path)
            assertEquals(android.graphics.Color.BLUE, bitmap.getPixel(0, 0))
            bitmap.recycle()
            data = TvResult.Success(byteArrayOf(0))
            assertEquals(refreshed, cache.get("tv", app, refresh = true))
        }

    @Test fun cancelledFetchLeavesNoPartialFilesAndAtMostTwoFetchesRun() =
        scenario { context, directory ->
            val started = kotlinx.coroutines.channels.Channel<Unit>(2)
            val release = CompletableDeferred<Unit>()
            val active = AtomicInteger()
            val peak = AtomicInteger()
            val cache =
                AppIconCache(context) {
                    val count = active.incrementAndGet()
                    peak.updateAndGet {
                        maxOf(it, count)
                    }
                    started.send(Unit)
                    try {
                        release.await()
                        TvResult.Success(png())
                    } finally {
                        active.decrementAndGet()
                    }
                }
            val jobs =
                (0..3).map { index ->
                    async {
                        cache.get("tv", app.copy(id = "app-$index"))
                    }
                }
            repeat(2) {
                started.receive()
            }
            assertEquals(2, peak.get())
            jobs.forEach {
                it.cancel()
            }
            jobs.forEach {
                it.join()
            }
            assertTrue(File(directory, "tv-app-icons").listFiles().orEmpty().isEmpty())
        }

    @Test fun cacheEvictsOldArtworkBelowFiveMiB() =
        scenario { context, directory ->
            val cacheDir =
                File(directory, "tv-app-icons").apply {
                    mkdirs()
                }
            repeat(6) {
                File(cacheDir, "old-$it.png").writeBytes(ByteArray(1024 * 1024))
            }
            val cache =
                AppIconCache(context) {
                    TvResult.Success(png())
                }
            assertNotNull(cache.get("tv", app))
            assertTrue(
                cacheDir.listFiles().orEmpty().sumOf {
                    it.length()
                } <= 5L * 1024 * 1024,
            )
            assertFalse(
                cacheDir.listFiles().orEmpty().any {
                    it.extension == "pending"
                },
            )
        }

    @Test fun failedCacheWritesRemainLocalAndLeaveNoTemporaryFiles() =
        scenario { context, directory ->
            val cacheDirectory = File(directory, "tv-app-icons")
            cacheDirectory.writeText("blocked directory")
            val cache =
                AppIconCache(context) {
                    TvResult.Success(png())
                }
            assertNull(cache.get("tv", app))
            assertEquals(
                listOf("tv-app-icons"),
                directory.listFiles().orEmpty().map {
                    it.name
                },
            )

            assertTrue(cacheDirectory.delete())
            val destination = requireNotNull(cache.get("tv", app))
            assertTrue(destination.delete())
            assertTrue(destination.mkdir())
            File(destination, "block-replacement").writeText("keep")
            assertNull(cache.get("tv", app, refresh = true))
            assertEquals("keep", File(destination, "block-replacement").readText())
            assertFalse(
                cacheDirectory.listFiles().orEmpty().any {
                    it.extension == "pending"
                },
            )
        }

    @Test fun embeddedSeparatorsCannotAliasAnotherTvCacheKey() =
        scenario { context, _ ->
            val cache =
                AppIconCache(context) {
                    TvResult.Success(png())
                }
            val first = cache.get("tv\u0000service", app.copy(id = "other"))
            val second = cache.get("tv", app.copy(id = "service\u0000other"))
            assertNotNull(first)
            assertNotEquals(first, second)
        }
}
