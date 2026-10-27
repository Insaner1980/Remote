package com.emmah.colordock.remote.ui

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.emmah.colordock.remote.tv.TvApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class ImageFileTest {
    @get:Rule val compose = createComposeRule()

    @Test fun pictureAndAppIconReloadChangedFileAndHandleMissingImages() {
        val file =
            File.createTempFile(
                "image-state-",
                ".png",
                InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            )

        fun writeImage(color: Int) {
            val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
            try {
                bitmap.eraseColor(color)
                file.outputStream().use {
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
            } finally {
                bitmap.recycle()
            }
        }
        val source = mutableStateOf<ImageFile?>(null)
        val tags = listOf("wallpaper-image", "app-artwork-test")

        fun awaitColor(red: Boolean) {
            compose.waitUntil(5000) {
                tags.all { tag ->
                    val nodes = compose.onAllNodesWithTag(tag, useUnmergedTree = true)
                    if (nodes.fetchSemanticsNodes().isEmpty()) {
                        false
                    } else {
                        val pixels = nodes[0].captureToImage().toPixelMap()
                        val pixel = pixels[pixels.width / 2, pixels.height / 2]
                        if (red) pixel.red > .9f && pixel.blue < .1f else pixel.blue > .9f && pixel.red < .1f
                    }
                }
            }
        }
        try {
            writeImage(Color.RED)
            val original = requireNotNull(file.asImageFile())
            assertEquals(original, File(file.path).asImageFile())
            source.value = original
            compose.setContent {
                RemoteTheme {
                    Column {
                        Picture(source.value, "Preview", modifier = Modifier.size(64.dp))
                        TvAppTile(TvApp("test", "Test app", null), source.value, true, {})
                    }
                }
            }
            awaitColor(red = true)
            writeImage(Color.BLUE)
            check(file.setLastModified(original.lastModified + 2000))
            val updated = requireNotNull(file.asImageFile())
            assertNotEquals(original, updated)
            assertEquals(original.path, updated.path)
            compose.runOnIdle {
                source.value = updated
            }
            awaitColor(red = false)

            check(file.delete())
            compose.runOnIdle {
                source.value = file.asImageFile()
            }
            compose.waitUntil(5000) {
                tags.all {
                    compose.onAllNodesWithTag(it, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
                }
            }
            compose.onNodeWithContentDescription("Preview").assertIsDisplayed()
            compose.onNodeWithText("Test app").assertIsDisplayed()
            compose.runOnIdle {
                source.value = null
            }
            compose.onNodeWithContentDescription("Preview").assertIsDisplayed()
            compose.onNodeWithText("Test app").assertIsDisplayed()
        } finally {
            file.delete()
        }
    }
}
