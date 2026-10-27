package com.emmah.colordock.remote.tv

import android.Manifest
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.emmah.colordock.remote.ColorDockApplication
import com.emmah.colordock.remote.companion.CompanionClient
import com.emmah.colordock.remote.companion.DockMethod
import com.emmah.colordock.remote.companion.DockPatch
import com.emmah.colordock.remote.companion.DockResult
import com.emmah.colordock.remote.companion.DockSnapshot
import com.emmah.colordock.remote.companion.PendingUploadStore
import com.emmah.colordock.remote.companion.UploadController
import com.emmah.colordock.remote.companion.WallpaperMetadata
import com.emmah.colordock.remote.images.ImagePreparer
import com.emmah.colordock.remote.images.ThumbnailCache
import com.emmah.colordock.remote.ui.WallpaperViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/** Opt-in physical tests. Use an isolated installation and explicitly authorize TV changes. */
class PhysicalTvReviewTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    private fun target(): TvTarget {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Requires an explicit physical TV test window", args.getString("tvAddress") != null)
        assumeTrue(
            "Requires an explicitly selected isolated installation",
            args.getString("isolatedStorePackage") == context.packageName &&
                context.packageName != "com.emmah.colordock.remote",
        )
        instrumentation.uiAutomation.grantRuntimePermission(
            context.packageName,
            Manifest.permission.ACCESS_LOCAL_NETWORK,
        )
        return requireNotNull(TvTarget.parse(requireNotNull(args.getString("tvAddress"))))
    }

    private fun report(message: String) =
        instrumentation.sendStatus(
            0,
            Bundle().apply {
                putString("stream", "\n$message\n")
            },
        )

    private fun <T> dockValue(result: DockResult<T>): T =
        when (result) {
            is DockResult.Success -> result.value
            is DockResult.Failure -> error("${result.code}: ${result.message}")
        }

    @Test fun discoveryReadCapabilitiesAndSavedReconnect() =
        runBlocking {
            val target = target()
            val found = TvDiscovery(context).find()
            assertTrue(
                "The selected TV must be discoverable",
                found.any {
                    it.address == target.address
                },
            )
            assertTrue(
                "The selected TV description must include its model",
                found
                    .first {
                        it.address == target.address
                    }.model
                    .isNotBlank(),
            )
            report("SSDP selected TV discovered")
            repeat(2) {
                val client = LgTvClient(context)
                try {
                    assertTrue("Saved pairing reconnect", client.connect(target) is TvResult.Success)
                    assertEquals(TvConnectionState.Ready, client.state.value.connection)
                    val volume = client.read(TvRead.Volume)
                    assertTrue(volume.has("volume") || volume.has("volumeStatus"))
                    val channels = client.channels()
                    assertTrue(channels is TvResult.Success && channels.value.isNotEmpty())
                    val pointer = client.read(TvRead.PointerSocket).getString("socketPath")
                    assertTrue("TV pointer endpoint must satisfy production policy", target.allowsPointer(pointer))
                    report("Reconnect ${it + 1}: Ready, volume, channel catalog and allowed TLS pointer endpoint")
                } finally {
                    client.disconnect()
                }
            }
        }

    @Test fun androidDescriptionParserAcceptsSafeXmlAndRejectsEntities() {
        val safe =
            "<root xmlns='urn:schemas-upnp-org:device-1-0'><device>" +
                "<friendlyName>Test TV</friendlyName><modelName>Test model</modelName></device></root>"
        assertEquals("Test TV" to "Test model", TvDiscovery.parseDescription(safe.toByteArray()))
        val hostile =
            "<!DOCTYPE root [<!ENTITY leak SYSTEM 'file:///not-permitted'>]>" +
                "<root><friendlyName>&leak;</friendlyName></root>"
        try {
            TvDiscovery.parseDescription(hostile.toByteArray())
            fail("DOCTYPE must be rejected")
        } catch (
            _: org.xml.sax.SAXException,
        ) {
        }
    }

    @Test fun productionCompanionUploadSettingsAndCleanup() =
        runBlocking {
            val target = target()
            assumeTrue(
                "Requires explicit authorization for reversible TV changes",
                InstrumentationRegistry.getArguments().getString("allowTvChanges") == "true",
            )
            PhysicalReview().run(target)
        }

    private inner class PhysicalReview {
        val client = LgTvClient(context)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val dock = CompanionClient(client, scope)
        val pending = PendingUploadStore(context)
        val source = File(context.cacheDir, "physical-review-source.jpg")
        var image: com.emmah.colordock.remote.images.PreparedImage? = null
        var original: DockSnapshot? = null
        var uploadedId: String? = null
        var clockSettingChanged = false
        var clockModeChanged = false

        suspend fun run(target: TvTarget) {
            try {
                val baseline = connect(target)
                verifyMuteRestoration()
                verifyClockEvents(baseline)
                verifyGallery(baseline)
                verifySettings(baseline)
                val prepared = prepareImage()
                verifyCancelledUpload(prepared)
                verifyCommittedUpload(prepared)
                prepareProcessDeath(prepared, baseline)
                verifyReconnect(target, prepared, baseline)
            } finally {
                cleanup()
            }
        }

        private suspend fun connect(target: TvTarget): DockSnapshot {
            assertTrue(client.connect(target) is TvResult.Success)
            assertTrue(client.launchColorDock() is TvResult.Success)
            var joined: DockResult<DockSnapshot> = dock.join()
            repeat(2) {
                if (joined is DockResult.Failure) {
                    delay(750)
                    joined = dock.join()
                }
            }
            val baseline = dockValue(joined)
            original = baseline
            report("Production v1 hello/state ready")
            return baseline
        }

        private suspend fun verifyMuteRestoration() {
            val muted =
                withTimeout(5000) {
                    client.state.first {
                        it.muted != null
                    }
                }.muted == true
            try {
                assertTrue(client.send(if (muted) TvCommand.Unmute else TvCommand.Mute) is TvResult.Success)
                withTimeout(5000) {
                    client.state.first {
                        it.muted == !muted
                    }
                }
            } finally {
                assertTrue(client.send(if (muted) TvCommand.Mute else TvCommand.Unmute) is TvResult.Success)
                withTimeout(5000) {
                    client.state.first {
                        it.muted == muted
                    }
                }
            }
            report("Mute subscription change/restoration passed")
        }

        private suspend fun verifyClockEvents(baseline: DockSnapshot) {
            clockModeChanged = true
            repeat(4) { index ->
                val active = if (index % 2 == 0) !baseline.clockActive else baseline.clockActive
                dockValue(dock.call(if (active) DockMethod.ClockStart else DockMethod.ClockStop, JSONObject()))
                withTimeout(5000) {
                    dock.state.first {
                        it.snapshot?.clockActive == active
                    }
                }
                assertEquals(active, dockValue(dock.snapshot()).clockActive)
            }
            clockModeChanged = false
            report("Repeated clock events and authoritative snapshots agree; initial mode restored")
        }

        private suspend fun verifyGallery(baseline: DockSnapshot) {
            for ((sourceName, expected) in listOf(
                "builtin" to baseline.builtinCount,
                "personal" to baseline.personalCount,
            )) {
                val ids = mutableSetOf<String>()
                var cursor: String? = null
                do {
                    val page =
                        dockValue(
                            dock.call(
                                DockMethod.Gallery,
                                JSONObject()
                                    .put("source", sourceName)
                                    .put("limit", 24)
                                    .put("cursor", cursor ?: JSONObject.NULL),
                            ),
                        )
                    assertEquals(baseline.revision, page.getLong("galleryRevision"))
                    val items = page.getJSONArray("items")
                    for (index in 0 until items.length()) {
                        assertTrue(
                            ids.add(items.getJSONObject(index).getString("id")),
                        )
                    }
                    cursor = if (page.isNull("nextCursor")) null else page.getString("nextCursor")
                } while (cursor != null)
                assertEquals(expected, ids.size)
                report("$sourceName gallery: ${ids.size} unique items, all pages")
            }
        }

        private suspend fun verifySettings(baseline: DockSnapshot) {
            val changed =
                dockValue(
                    dock.patch(
                        baseline.revision,
                        DockPatch.Clock(
                            if (baseline.idleMinutes ==
                                0
                            ) {
                                5
                            } else {
                                0
                            },
                        ),
                    ),
                )
            clockSettingChanged = true
            assertTrue(changed.revision > baseline.revision)
            val stale = dock.patch(baseline.revision, DockPatch.Clock(baseline.idleMinutes))
            assertTrue(stale is DockResult.Failure && stale.code == "REVISION_CONFLICT")
            val current = dockValue(dock.snapshot())
            dockValue(dock.patch(current.revision, DockPatch.Clock(baseline.idleMinutes)))
            clockSettingChanged = false
            report("Settings write, stale revision rejection and restoration passed")
        }

        private suspend fun prepareImage(): com.emmah.colordock.remote.images.PreparedImage {
            val random = Random(42)
            val bitmap =
                Bitmap.createBitmap(
                    IntArray(640 * 360) {
                        0xff000000.toInt() or random.nextInt(0x1000000)
                    },
                    640,
                    360,
                    Bitmap.Config.ARGB_8888,
                )
            try {
                source.outputStream().use {
                    assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it))
                }
            } finally {
                bitmap.recycle()
            }
            val prepared =
                ImagePreparer(
                    context,
                ).prepare(Uri.fromFile(source), "Remote physical review fixture", "contain")
            image = prepared
            return prepared
        }

        private suspend fun verifyCancelledUpload(prepared: com.emmah.colordock.remote.images.PreparedImage) {
            assertTrue(prepared.image.length() > dock.limits.chunkBytes)
            val cancelled = UploadController(dock, pending).upload(prepared).take(2).toList()
            assertEquals("Sending", cancelled.last().stage)
            val cancelledStatus =
                dockValue(dock.call(DockMethod.UploadStatus, JSONObject().put("uploadId", prepared.uploadId)))
            assertEquals("missing", cancelledStatus.getString("status"))
            assertEquals(prepared.uploadId, pending.read()?.first)
            report(
                "Cancellation after the first acknowledged chunk removed the TV transfer " +
                    "and retained local recovery intent",
            )
        }

        private suspend fun verifyCommittedUpload(prepared: com.emmah.colordock.remote.images.PreparedImage) {
            val progress = UploadController(dock, pending).upload(prepared).toList()
            val complete = progress.last()
            assertEquals(complete.message, "Complete", complete.stage)
            uploadedId = requireNotNull(complete.wallpaperId)
            assertNull(pending.read())
            val status =
                dockValue(dock.call(DockMethod.UploadStatus, JSONObject().put("uploadId", prepared.uploadId)))
            assertEquals("committed", status.getString("status"))
            assertEquals(uploadedId, status.getString("wallpaperId"))
            val repeated = UploadController(dock, pending).upload(prepared).toList().last()
            assertEquals("Complete", repeated.stage)
            assertEquals(uploadedId, repeated.wallpaperId)
            val metadata =
                WallpaperMetadata(
                    requireNotNull(uploadedId),
                    prepared.name,
                    "contain",
                    null,
                    null,
                    prepared.thumbnailHash,
                )
            val thumbnail = ThumbnailCache(context, dock).get(requireNotNull(client.identity), metadata)
            assertEquals(prepared.thumbnailHash, ImagePreparer.sha256(thumbnail))
            report(
                "Production JPEG upload ${prepared.image.length()} bytes; " +
                    "status, idempotent retry and thumbnail SHA-256 passed",
            )
        }

        private suspend fun prepareProcessDeath(
            prepared: com.emmah.colordock.remote.images.PreparedImage,
            baseline: DockSnapshot,
        ) {
            if (InstrumentationRegistry.getArguments().getString("processDeathStage") == "prepare") {
                // Reconstruct a committed server result whose local outcome is still unresolved.
                pending.save(prepared.uploadId, prepared.name)
                File(context.noBackupFilesDir, "physical-review-count").writeText(baseline.personalCount.toString())
                report("READY_FOR_PROCESS_DEATH")
                awaitCancellation()
            }
        }

        private suspend fun verifyReconnect(
            target: TvTarget,
            prepared: com.emmah.colordock.remote.images.PreparedImage,
            baseline: DockSnapshot,
        ) {
            dock.disconnect()
            client.disconnect()
            assertTrue(client.connect(target) is TvResult.Success)
            val rejoined = dockValue(dock.join())
            assertEquals(baseline.personalCount + 1, rejoined.personalCount)
            val recovered =
                dockValue(dock.call(DockMethod.UploadStatus, JSONObject().put("uploadId", prepared.uploadId)))
            assertEquals(uploadedId, recovered.getString("wallpaperId"))
            report("Disconnected/reconnected TV session retained committed upload outcome")
        }

        private suspend fun cleanup() {
            try {
                uploadedId?.let { id ->
                    val current = dockValue(dock.snapshot())
                    dockValue(
                        dock.call(
                            DockMethod.Delete,
                            JSONObject().put("expectedRevision", current.revision).put("wallpaperId", id),
                        ),
                    )
                    report("Owned TV fixture deleted")
                }
                if (clockSettingChanged) {
                    original?.let {
                        dockValue(dock.patch(dockValue(dock.snapshot()).revision, DockPatch.Clock(it.idleMinutes)))
                    }
                }
                if (clockModeChanged) {
                    original?.let {
                        dockValue(
                            dock.call(
                                if (it.clockActive) DockMethod.ClockStart else DockMethod.ClockStop,
                                JSONObject(),
                            ),
                        )
                    }
                }
                original?.let {
                    val restored = dockValue(dock.snapshot())
                    assertEquals(it.personalCount, restored.personalCount)
                    assertEquals(it.wallpaper, restored.wallpaper)
                    assertEquals(it.idleMinutes, restored.idleMinutes)
                    assertEquals(it.weatherEnabled, restored.weatherEnabled)
                    assertEquals(it.location, restored.location)
                    assertEquals(it.shelfOrder, restored.shelfOrder)
                    assertEquals(it.shelfHidden, restored.shelfHidden)
                    report("Original picture selection, image count, clock, weather and shelf preserved")
                }
            } finally {
                image?.delete()
                source.delete()
                dock.disconnect()
                client.disconnect()
                scope.cancel()
            }
        }
    }

    @Test fun recoverCommittedOutcomeAfterProcessDeath() =
        runBlocking {
            assumeTrue(
                "Requires the separate process-death preparation stage",
                InstrumentationRegistry.getArguments().getString("processDeathStage") == "recover",
            )
            val target = target()
            assumeTrue(InstrumentationRegistry.getArguments().getString("allowTvChanges") == "true")
            val app = context.applicationContext as ColorDockApplication
            val container = app.container
            val pending = PendingUploadStore(context)
            val intent = requireNotNull(pending.read())
            assertEquals("Remote physical review fixture", intent.second)
            val countFile = File(context.noBackupFilesDir, "physical-review-count")
            val originalCount = countFile.readText().toInt()
            val models = ViewModelStore()
            var fixtureId: String? = null
            try {
                assertTrue(container.tv.connect(target) is TvResult.Success)
                dockValue(container.dock.join())
                val status =
                    dockValue(container.dock.call(DockMethod.UploadStatus, JSONObject().put("uploadId", intent.first)))
                assertEquals("committed", status.getString("status"))
                fixtureId = status.getString("wallpaperId")
                assertTrue(File(context.cacheDir, "uploads").listFiles().orEmpty().isEmpty())
                verifyRecoveredOutcome(app, models, pending, intent.second)
            } finally {
                try {
                    fixtureId?.let {
                        val current = dockValue(container.dock.snapshot())
                        dockValue(
                            container.dock.call(
                                DockMethod.Delete,
                                JSONObject().put("expectedRevision", current.revision).put("wallpaperId", it),
                            ),
                        )
                        assertEquals(originalCount, dockValue(container.dock.snapshot()).personalCount)
                        report("Process-death fixture deleted; original personal-image count restored")
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        models.clear()
                    }
                    container.dock.disconnect()
                    container.tv.disconnect()
                    File(context.cacheDir, "physical-review-source.jpg").delete()
                    countFile.delete()
                }
            }
        }

    private suspend fun verifyRecoveredOutcome(
        app: ColorDockApplication,
        models: ViewModelStore,
        pending: PendingUploadStore,
        name: String,
    ) {
        val model =
            withContext(Dispatchers.Main) {
                WallpaperViewModel(app).also {
                    models.put("recovery", it)
                    it.join()
                }
            }
        val outcome =
            withTimeout(10000) {
                model.ui.first {
                    it.outcome != null
                }
            }
        assertEquals("$name was saved on the TV.", outcome.outcome)
        assertNull(pending.read())
        report(
            "Fresh process: temporary upload files cleared; " +
                "real ViewModel recovered committed TV outcome and cleared journal",
        )
    }
}
