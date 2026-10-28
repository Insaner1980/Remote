package com.emmah.colordock.remote.images

import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ExifInterface
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

@RunWith(AndroidJUnit4::class)
class ImagePreparerTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun missingSourceRequestsReselectionAndLeavesNoTemporaryFiles() =
        runBlocking {
            val root = File(context.cacheDir, "missing-source-${UUID.randomUUID()}").apply { check(mkdir()) }
            val isolated =
                object : ContextWrapper(context) {
                    override fun getCacheDir() = root
                }
            try {
                try {
                    ImagePreparer(isolated).prepare(Uri.fromFile(File(root, "missing.png")), "Missing fixture")
                    fail("Expected an inaccessible source error")
                } catch (error: ImagePreparationException) {
                    assertEquals("This picture is no longer accessible. Select it again.", error.message)
                    assertTrue(generateSequence(error.cause) { it.cause }.any { it is FileNotFoundException })
                }
                assertTrue(File(root, "uploads").listFiles().orEmpty().isEmpty())
            } finally {
                root.deleteRecursively()
            }
        }

    @Test fun cancellationDuringCompletedIoHandoffDeletesUnclaimedOutputs() {
        val root =
            File(context.cacheDir, "prepare-handoff-${UUID.randomUUID()}").apply {
                check(mkdir())
            }
        val isolated =
            object : ContextWrapper(context) {
                override fun getCacheDir() = root
            }
        val source = File(root, "source.png")
        val bitmap = Bitmap.createBitmap(20, 10, Bitmap.Config.ARGB_8888)
        val resumes = LinkedBlockingQueue<Runnable>()
        val dispatcher =
            object : CoroutineDispatcher() {
                override fun dispatch(
                    context: CoroutineContext,
                    block: Runnable,
                ) {
                    resumes.add(block)
                }
            }
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        try {
            source.outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            val original = source.readBytes()
            val job =
                scope.async {
                    ImagePreparer(isolated).prepare(Uri.fromFile(source), "Handoff fixture")
                }
            requireNotNull(resumes.poll(5, TimeUnit.SECONDS)).run()
            val handoff = requireNotNull(resumes.poll(5, TimeUnit.SECONDS))
            assertEquals(2, File(root, "uploads").listFiles().orEmpty().size)
            job.cancel()
            handoff.run()
            while (!job.isCompleted)requireNotNull(resumes.poll(5, TimeUnit.SECONDS)).run()
            assertTrue(job.isCancelled)
            assertTrue(File(root, "uploads").listFiles().orEmpty().isEmpty())
            assertArrayEquals(original, source.readBytes())
        } finally {
            scope.cancel()
            bitmap.recycle()
            root.deleteRecursively()
        }
    }

    @Test fun alphaIsFlattenedNoUpscalingAndOutputsContainNoLocationMetadata() =
        runBlocking {
            val source = java.io.File.createTempFile("image-alpha-", ".png", context.cacheDir)
            val bitmap = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.TRANSPARENT)
            try {
                source.outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                val output = ImagePreparer(context).prepare(Uri.fromFile(source), "Transparent fixture")
                try {
                    assertEquals(120, output.width)
                    assertEquals(80, output.height)
                    assertTrue(output.image.length() <= 4194304)
                    assertTrue(output.thumbnail.length() <= 131072)
                    val decoded = BitmapFactory.decodeFile(output.image.path)
                    val pixel = decoded.getPixel(30, 30)
                    assertTrue(kotlin.math.abs(Color.red(pixel) - 17) <= 3)
                    assertTrue(kotlin.math.abs(Color.green(pixel) - 23) <= 3)
                    assertTrue(kotlin.math.abs(Color.blue(pixel) - 25) <= 3)
                    decoded.recycle()
                    assertNull(ExifInterface(output.image.path).getAttribute(ExifInterface.TAG_GPS_LATITUDE))
                    assertEquals(ImagePreparer.sha256(output.image), output.imageHash)
                } finally {
                    output.delete()
                }
            } finally {
                bitmap.recycle()
                source.delete()
            }
        }

    @Test fun orientationAppliedOnceAndLandscapeIsBounded() =
        runBlocking {
            val source = java.io.File.createTempFile("image-rotate-", ".jpg", context.cacheDir)
            val bitmap = Bitmap.createBitmap(2400, 1200, Bitmap.Config.ARGB_8888)
            try {
                source.outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)
                }
                ExifInterface(source.path).apply {
                    setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
                    setAttribute(ExifInterface.TAG_GPS_LATITUDE, "60/1,0/1,0/1")
                    saveAttributes()
                }
                val output = ImagePreparer(context).prepare(Uri.fromFile(source), "Orientation fixture", "contain")
                try {
                    assertTrue(output.width <= 1920 && output.height <= 1080)
                    assertTrue(output.height > output.width)
                    val orientation =
                        ExifInterface(
                            output.image.path,
                        ).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
                    assertTrue(
                        orientation == ExifInterface.ORIENTATION_NORMAL ||
                            orientation == ExifInterface.ORIENTATION_UNDEFINED,
                    )
                    assertNull(ExifInterface(output.image.path).getAttribute(ExifInterface.TAG_GPS_LATITUDE))
                } finally {
                    output.delete()
                }
            } finally {
                bitmap.recycle()
                source.delete()
            }
        }

    @Test fun corruptSourceIsRejectedAndOriginalRemainsUntouched() =
        runBlocking {
            val source = java.io.File.createTempFile("image-corrupt-", ".jpg", context.cacheDir)
            val bytes = "not an image".toByteArray()
            source.writeBytes(bytes)
            try {
                try {
                    ImagePreparer(context).prepare(Uri.fromFile(source), "Corrupt fixture")
                    fail("Expected a readable image error")
                } catch (error: ImagePreparationException) {
                    assertEquals("This picture is corrupt or its format is not supported.", error.message)
                }
                assertArrayEquals(bytes, source.readBytes())
            } finally {
                source.delete()
            }
        }
}
