package com.emmah.colordock.remote.ui

import android.graphics.Bitmap
import android.net.Uri
import com.emmah.colordock.remote.companion.DockMethod
import com.emmah.colordock.remote.companion.DockPatch
import com.emmah.colordock.remote.companion.DockResult
import com.emmah.colordock.remote.companion.PendingUploadStore
import com.emmah.colordock.remote.tv.TvConnectionState
import com.emmah.colordock.remote.tv.TvState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.yield
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WallpaperViewModelTest {
    private fun ViewModelDockFixture.picture(): File {
        val file = File(directory, "upload.png")
        val bitmap = Bitmap.createBitmap(24, 16, Bitmap.Config.ARGB_8888)
        try {
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally {
            bitmap.recycle()
        }
        return file
    }

    @Test fun failedBatchRetainsPictureAndSuccessfulRetryRemovesTemporaryFiles() =
        dockScenario {
            dock.join()
            val model = wallpaper()
            model.select(listOf(Uri.fromFile(picture())))
            val image =
                model.ui
                    .first { !it.preparing && it.prepared.isNotEmpty() }
                    .prepared
                    .single()
            failure = "UPLOAD_TOO_LARGE"
            yield()
            model.send()
            val failed = model.ui.first { it.upload == null && it.outcome?.contains("Failed:") == true }
            assertEquals(image.uploadId, failed.prepared.single().uploadId)
            assertTrue(image.image.exists())
            failure = null
            response = { method, _ ->
                if (method == "upload.begin") {
                    JSONObject().put("status", "committed").put("wallpaperId", "user:one")
                } else {
                    ViewModelDockFixture.page(listOf("user:one"))
                }
            }
            yield()
            model.send()
            val complete = model.ui.first { it.upload == null && it.prepared.isEmpty() }
            assertEquals("Sent: Picture 1", complete.outcome)
            assertFalse(image.image.exists())
            assertFalse(image.thumbnail.exists())
        }

    @Test fun backgroundCancelsActiveUploadAndBusySendCannotDuplicateIt() =
        dockScenario {
            dock.join()
            val model = wallpaper()
            model.select(listOf(Uri.fromFile(picture())))
            model.ui.first { !it.preparing && it.prepared.isNotEmpty() }
            response = { method, _ -> if (method == "upload.begin") null else JSONObject() }
            yield()
            model.send()
            model.ui.first { it.upload != null }
            val count = requests.count { it.optString("method") == "upload.begin" }
            yield()
            model.send()
            assertEquals(count, requests.count { it.optString("method") == "upload.begin" })
            model.background()
            assertTrue(
                model.ui
                    .first { it.upload == null && it.outcome != null }
                    .outcome!!
                    .contains("cancelled"),
            )
            assertEquals(1, model.ui.value.prepared.size)
            model.abandon()
            assertTrue(
                model.ui.value.prepared
                    .isEmpty(),
            )
        }

    @Test fun thumbnailIsCachedAndDisconnectClearsPresentation() =
        dockScenario {
            val source = picture()
            val bytes = source.readBytes()
            val hash =
                com.emmah.colordock.remote.images.ImagePreparer
                    .sha256(source)
            val item =
                com.emmah.colordock.remote.companion
                    .WallpaperMetadata("user:one", "One", "cover", null, null, hash)
            dock.join()
            val model = wallpaper()
            model.ui.first { it.items.isNotEmpty() }
            response = { method, params ->
                if (method == "gallery.thumbnail") {
                    JSONObject()
                        .put("wallpaperId", params.getString("wallpaperId"))
                        .put("index", 0)
                        .put("hash", hash)
                        .put("bytes", bytes.size)
                        .put("data", android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP))
                        .put("nextIndex", JSONObject.NULL)
                } else {
                    ViewModelDockFixture.page(listOf("user:one"))
                }
            }
            model.thumbnail(item)
            val cached = model.thumbnails.first { it.containsKey(item.id) }.getValue(item.id)
            assertTrue(cached.isFile)
            val count = requests.size
            model.thumbnail(item)
            assertEquals(count, requests.size)
            identity = null
            model.thumbnail(item.copy(id = "other"))
            assertEquals(count, requests.size)
            tvState.value = TvState(TvConnectionState.Disconnected)
            model.thumbnails.first { it.isEmpty() }
        }

    @Test fun galleryPaginationDeduplicatesAndDisconnectClearsSelection() =
        dockScenario {
            response = { method, params ->
                if (method == "gallery.list" && !params.isNull("cursor")) {
                    ViewModelDockFixture.page(listOf("user:one", "user:two"))
                } else {
                    ViewModelDockFixture.page(listOf("user:one"), "next")
                }
            }
            assertTrue(dock.join() is DockResult.Success)
            val model = wallpaper()
            model.ui.first { !it.loading && it.cursor == "next" }
            model.load(true)
            val loaded = model.ui.first { !it.loading && it.items.size == 2 }
            assertEquals(listOf("user:one", "user:two"), loaded.items.map { it.id })
            assertNull(loaded.cursor)
            model.detail(loaded.items.first())
            assertNotNull(model.ui.value.detail)
            tvState.value = TvState(TvConnectionState.Disconnected)
            model.ui.first { it.items.isEmpty() && it.detail == null }
            assertFalse(model.ui.value.saving)
        }

    @Test fun builtinCollectionsLoadAllPagesAndRejectCursorCycles() =
        dockScenario {
            assertTrue(dock.join() is DockResult.Success)
            val model = wallpaper()
            model.ui.first { it.items.isNotEmpty() }
            response = { _, params ->
                if (params.isNull("cursor")) {
                    ViewModelDockFixture.page(listOf("a"), "second")
                } else {
                    ViewModelDockFixture.page(listOf("b"))
                }
            }
            model.source("builtin")
            assertEquals(
                listOf("a", "b"),
                model.ui
                    .first { !it.loading && it.items.size == 2 }
                    .items
                    .map { it.id },
            )
            val count = requests.size
            model.source("builtin")
            assertEquals(count, requests.size)
            response = { _, _ -> ViewModelDockFixture.page(listOf("a"), "loop") }
            model.load(false)
            assertEquals("The picture list could not be read.", model.ui.first { it.error != null }.error)
        }

    @Test fun changedGalleryRevisionRestartsPagination() =
        dockScenario {
            var initial = true
            response = { _, params ->
                when {
                    !params.isNull("cursor") -> ViewModelDockFixture.page(listOf("stale"), revision = 2)
                    initial -> ViewModelDockFixture.page(listOf("old"), "next").also { initial = false }
                    else -> ViewModelDockFixture.page(listOf("fresh"), revision = 2)
                }
            }
            dock.join()
            val model = wallpaper()
            model.ui.first { it.cursor == "next" }
            model.load(true)
            assertEquals(
                listOf("fresh"),
                model.ui
                    .first {
                        !it.loading &&
                            it.items.any { item ->
                                item.id == "fresh"
                            }
                    }.items
                    .map { it.id },
            )
        }

    @Test fun invalidGalleryAndProtocolFailuresRemainRetryable() =
        dockScenario {
            dock.join()
            val model = wallpaper()
            model.ui.first { it.items.isNotEmpty() }
            response = { _, _ -> JSONObject() }
            model.load(false)
            assertEquals("The picture list could not be read.", model.ui.first { it.error != null }.error)
            model.clearError()
            assertNull(model.ui.value.error)
            failure = "NOT_FOUND"
            model.load(false)
            assertTrue(
                model.ui
                    .first { it.error != null }
                    .error!!
                    .contains("no longer"),
            )
            failure = null
            response = { _, _ -> ViewModelDockFixture.page(listOf("recovered")) }
            model.load(false)
            assertEquals(
                "recovered",
                model.ui
                    .first { !it.loading && it.error == null }
                    .items
                    .single()
                    .id,
            )
        }

    @Test fun imageActionsUpdateDetailOnlyAfterAcknowledgement() =
        dockScenario {
            dock.join()
            val model = wallpaper()
            val image =
                model.ui
                    .first { it.items.isNotEmpty() }
                    .items
                    .single()
            model.detail(image)
            model.imageAction(DockMethod.Fit, image, "contain")
            assertEquals(
                "contain",
                model.ui
                    .first { !it.saving && it.detail?.fit == "contain" }
                    .detail
                    ?.fit,
            )
            assertEquals(
                "contain",
                requests
                    .first {
                        it.optString("method") == "wallpaper.fit"
                    }.getJSONObject("params")
                    .getString("fit"),
            )
            model.imageAction(DockMethod.Delete, image)
            model.ui.first { !it.saving && it.detail == null }
            model.next()
            model.ui.first { !it.saving }
            assertTrue(requests.any { it.optString("method") == "wallpaper.next" })
            model.patch(DockPatch.Wallpaper(mode = "shuffle"))
            model.ui.first { !it.saving }
            assertTrue(requests.any { it.optString("method") == "settings.patch" })
            failure = "NOT_FOUND"
            model.imageAction(DockMethod.Use, image)
            assertNotNull(model.ui.first { !it.saving && it.error != null }.error)
        }

    @Test fun disconnectedActionsDoNotSendRequests() =
        dockScenario {
            val model = wallpaper()
            model.patch(DockPatch.Wallpaper(mode = "shuffle"))
            model.next()
            yield()
            model.send()
            model.select(emptyList())
            model.cancelUpload()
            model.background()
            assertTrue(requests.isEmpty())
            assertFalse(model.ui.value.preparing)
        }

    @Test fun selectedPicturesCanChangeFitAndBeAbandoned() =
        dockScenario {
            val source = File(directory, "selected.png")
            val bitmap = Bitmap.createBitmap(24, 16, Bitmap.Config.ARGB_8888)
            try {
                source.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            } finally {
                bitmap.recycle()
            }
            val model = wallpaper()
            model.select(listOf(Uri.fromFile(source), Uri.fromFile(File(directory, "missing.png"))))
            val prepared = model.ui.first { !it.preparing && it.prepared.isNotEmpty() }
            assertTrue(prepared.error!!.startsWith("Picture 2:"))
            val image = prepared.prepared.single()
            assertTrue(image.image.isFile)
            model.preparedFit("unrelated", "contain")
            assertEquals(
                "cover",
                model.ui.value.prepared
                    .single()
                    .fit,
            )
            model.preparedFit(image.uploadId, "contain")
            assertEquals(
                "contain",
                model.ui.value.prepared
                    .single()
                    .fit,
            )
            model.abandon()
            assertTrue(
                model.ui.value.prepared
                    .isEmpty(),
            )
            assertFalse(image.image.exists())
            assertFalse(image.thumbnail.exists())
        }

    @Test fun previousUploadRecoveryChecksStatusBeforeClearingPendingRecord() =
        dockScenario {
            val pending = PendingUploadStore(application)
            val id = "00000000-0000-4000-8000-000000000123"
            pending.save(id, "Saved picture")
            response = { method, _ ->
                if (method == "upload.status") {
                    JSONObject().put("status", "committed").put("wallpaperId", "user:one")
                } else {
                    ViewModelDockFixture.page(listOf("user:one"))
                }
            }
            val model = wallpaper()
            model.join()
            assertEquals("Saved picture was saved on the TV.", model.ui.first { it.outcome != null }.outcome)
            assertNull(pending.read())
            model.clearError()
            assertNull(model.ui.value.outcome)
        }
}
