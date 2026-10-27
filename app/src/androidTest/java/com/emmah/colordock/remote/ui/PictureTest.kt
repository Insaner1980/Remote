package com.emmah.colordock.remote.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PictureTest {
    @Test fun decoderBoundsWideImagesAndRejectsInvalidOrExcessiveHeaders() {
        val file =
            File.createTempFile(
                "picture-",
                ".jpg",
                InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            )
        val source = Bitmap.createBitmap(6000, 10, Bitmap.Config.ARGB_8888)
        try {
            file.outputStream().use {
                check(source.compress(Bitmap.CompressFormat.JPEG, 90, it))
            }
            val bytes = file.readBytes()
            val decoded = requireNotNull(decodePicture(file))
            try {
                assertTrue(decoded.width <= 1920)
                assertTrue(decoded.height <= 1080)
            } finally {
                decoded.recycle()
            }
            val oversized = bytes.copyOf()
            val marker =
                (0 until oversized.size - 8).first {
                    oversized[it] == 0xff.toByte() && oversized[it + 1] == 0xc0.toByte()
                }
            oversized[marker + 5] = 0x75
            oversized[marker + 6] = 0x30
            oversized[marker + 7] = 0x75
            oversized[marker + 8] = 0x30
            file.writeBytes(oversized)
            val bounds =
                BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
            BitmapFactory.decodeFile(file.path, bounds)
            assertEquals(30000, bounds.outWidth)
            assertEquals(30000, bounds.outHeight)
            assertNull(decodePicture(file))
            file.writeText("corrupt")
            assertNull(decodePicture(file))
            file.delete()
            assertNull(decodePicture(file))
        } finally {
            source.recycle()
            file.delete()
        }
    }
}
