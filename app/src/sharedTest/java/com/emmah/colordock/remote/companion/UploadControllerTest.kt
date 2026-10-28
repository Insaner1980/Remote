package com.emmah.colordock.remote.companion

import android.content.ContextWrapper
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.emmah.colordock.remote.images.ImagePreparer
import com.emmah.colordock.remote.images.PreparedImage
import com.emmah.colordock.remote.tv.TvConnectionState
import com.emmah.colordock.remote.tv.TvState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class UploadControllerTest {
    private class Transport(
        private val scope: CoroutineScope,
    ) : DockTransport {
        override val state = MutableStateFlow(TvState(TvConnectionState.Ready))
        override val dockAvailable = MutableStateFlow(true)
        override val dockMessages = MutableSharedFlow<JSONObject>(extraBufferCapacity = 20)
        val requests = mutableListOf<JSONObject>()
        val chunks = linkedMapOf<String, ByteArray>()
        val chunkArrived = CompletableDeferred<Unit>()
        var loseFirstChunk = false
        var loseCommit = false
        var invalidAck = false
        var holdChunk = false
        var imageLimit = 4194304
        var thumbnailLimit = 131072
        var beginReply: JSONObject? = null
        var ackIndex: (Int) -> Any = {
            it
        }
        var ackStream: (String) -> String = {
            it
        }
        var onBegin: () -> Unit = {
        }
        var commitReply: JSONObject? = null
        var statusReply: JSONObject? = null
        var invalidRefresh = false
        var holdCancel = false
        var failCancel = false
        var onCommit: () -> Unit = {
        }
        private var imageBytes = 0L
        private var committed = false

        override suspend fun joinDockChannel() = Unit

        override fun sendDock(payload: JSONObject) {
            requests.add(JSONObject(payload.toString()))
            val result =
                when (val method = payload.getString("method")) {
                    "hello" -> {
                        DockTestFixtures.hello().also {
                            it
                                .getJSONObject(
                                    "limits",
                                ).put("imageBytes", imageLimit)
                                .put("thumbnailBytes", thumbnailLimit)
                        }
                    }

                    "state.get" -> {
                        if (committed && invalidRefresh) {
                            JSONObject()
                        } else {
                            DockTestFixtures.snapshot(revision = 1, personal = 1)
                        }
                    }

                    else -> {
                        uploadReply(method, payload.getJSONObject("params"))
                    }
                }
            if (result != null) scope.launch { dockMessages.emit(DockTestFixtures.reply(payload, result)) }
        }

        private fun uploadReply(
            method: String,
            params: JSONObject,
        ): JSONObject? =
            when (method) {
                "upload.begin" -> {
                    imageBytes = params.getLong("imageBytes")
                    onBegin()
                    beginReply ?: JSONObject().put("status", "receiving")
                }

                "upload.chunk" -> {
                    chunkReply(params)
                }

                "upload.commit" -> {
                    committed = true
                    onCommit()
                    if (loseCommit) null else commitReply ?: JSONObject().put("id", "user:fixture")
                }

                "upload.status" -> {
                    statusReply ?: JSONObject()
                        .put("status", if (committed) "committed" else "receiving")
                        .put("wallpaperId", "user:fixture")
                }

                "upload.cancel" -> {
                    if (holdCancel) {
                        null
                    } else {
                        if (failCancel) throw java.io.IOException("Fixture cleanup failure")
                        JSONObject().put("cancelled", true)
                    }
                }

                else -> {
                    error("Unexpected method")
                }
            }

        private fun chunkReply(params: JSONObject): JSONObject? {
            chunkArrived.complete(Unit)
            if (holdChunk) return null
            val stream = params.getString("stream")
            val index = params.getInt("index")
            val chunkId = "$stream:$index"
            val bytes = Base64.decode(params.getString("data"), Base64.NO_WRAP)
            chunks[chunkId]?.let { assertArrayEquals(it, bytes) } ?: run { chunks[chunkId] = bytes }
            return if (loseFirstChunk) {
                loseFirstChunk = false
                null
            } else {
                val imageComplete =
                    stream == "image" && chunks
                        .filterKeys { it.startsWith("image:") }
                        .values
                        .sumOf { it.size.toLong() } == imageBytes
                val nextIndex =
                    when {
                        invalidAck -> 999
                        imageComplete -> 0
                        else -> index + 1
                    }
                JSONObject()
                    .put("stream", ackStream(if (imageComplete) "thumbnail" else stream))
                    .put("nextIndex", ackIndex(nextIndex))
            }
        }

        fun count(method: String) =
            requests.count {
                it.getString("method") == method
            }
    }

    private class Harness(
        val transport: Transport,
        val client: CompanionClient,
        val pending: PendingUploadStore,
        val image: PreparedImage,
    ) {
        val controller = UploadController(client, pending)

        fun assertTransferredBytes() {
            assertArrayEquals(
                image.image.readBytes(),
                transport.chunks
                    .filterKeys {
                        it.startsWith("image:")
                    }.values
                    .fold(byteArrayOf()) { all, bytes ->
                        all +
                            bytes
                    },
            )
            assertArrayEquals(image.thumbnail.readBytes(), transport.chunks.getValue("thumbnail:0"))
        }
    }

    private suspend fun harness(
        limits: Pair<Int, Int>? = null,
        imageSize: Int = 12295,
        action: suspend Harness.() -> Unit,
    ) {
        // Instrumentation runs as the target UID. Isolate its journal in an owned cache folder.
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val directory =
            File(target.cacheDir, "upload-test-${UUID.randomUUID()}").apply {
                check(mkdirs())
            }
        val context =
            object : ContextWrapper(target) {
                override fun getNoBackupFilesDir() = directory
            }
        val imageFile =
            File(directory, "image.jpg").apply {
                writeBytes(
                    ByteArray(imageSize) {
                        (it % 251).toByte()
                    },
                )
            }
        val thumbnail =
            File(directory, "thumbnail.jpg").apply {
                writeBytes(
                    ByteArray(111) {
                        (it % 127).toByte()
                    },
                )
            }
        val image =
            PreparedImage(
                UUID.randomUUID().toString(),
                "Controller fixture",
                "contain",
                imageFile,
                thumbnail,
                1920,
                1080,
                ImagePreparer.sha256(imageFile),
                ImagePreparer.sha256(thumbnail),
            )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val transport =
            Transport(scope).apply {
                limits?.let {
                    imageLimit = it.first
                    thumbnailLimit = it.second
                }
            }
        val client = CompanionClient(transport, scope)
        val pending = PendingUploadStore(context)
        try {
            assertTrue(client.join() is DockResult.Success)
            Harness(transport, client, pending, image).action()
        } finally {
            client.disconnect()
            scope.cancel()
            pending.clear()
            directory.deleteRecursively()
        }
    }

    @Test fun lostChunkAcknowledgementRetriesIdenticalBytesThenCommits() =
        runBlocking {
            harness {
                transport.loseFirstChunk = true
                val progress = controller.upload(image).toList()
                assertEquals("Complete", progress.last().stage)
                assertEquals(4, transport.count("upload.chunk"))
                assertEquals(1, transport.count("upload.commit"))
                assertEquals(0, transport.count("upload.cancel"))
                assertNull(pending.read())
                val retries =
                    transport.requests
                        .filter {
                            it.getString("method") == "upload.chunk"
                        }.take(2)
                assertEquals(
                    retries[0].getJSONObject("params").toString(),
                    retries[1].getJSONObject("params").toString(),
                )
                assertTransferredBytes()
            }
        }

    @Test fun lostCommitAcknowledgementChecksStatusWithoutCommittingTwice() =
        runBlocking {
            harness {
                transport.loseCommit = true
                val progress = controller.upload(image).toList()
                assertEquals("Complete", progress.last().stage)
                assertEquals("user:fixture", progress.last().wallpaperId)
                assertEquals(1, transport.count("upload.commit"))
                assertEquals(1, transport.count("upload.status"))
                assertEquals(0, transport.count("upload.cancel"))
                assertNull(pending.read())
            }
        }

    @Test fun invalidAcknowledgementCannotReportSuccessAndKeepsRecoveryJournal() =
        runBlocking {
            harness {
                transport.invalidAck = true
                val progress = controller.upload(image).toList()
                assertEquals("Failed", progress.last().stage)
                assertFalse(
                    progress.any {
                        it.stage == "Complete"
                    },
                )
                assertEquals(0, transport.count("upload.commit"))
                assertEquals(1, transport.count("upload.cancel"))
                assertEquals(image.uploadId, pending.read()?.first)
                assertTrue(image.image.exists())
            }
        }

    @Test fun cancellationStopsTransferAndSendsOneBoundedCancel() =
        runBlocking {
            harness {
                transport.holdChunk = true
                val progress = mutableListOf<UploadProgress>()
                val transfer =
                    launch {
                        controller.upload(image).toList(progress)
                    }
                withTimeout(5000) {
                    transport.chunkArrived.await()
                }
                transfer.cancelAndJoin()
                assertEquals(1, transport.count("upload.chunk"))
                assertEquals(0, transport.count("upload.commit"))
                assertEquals(1, transport.count("upload.cancel"))
                assertFalse(
                    progress.any {
                        it.stage == "Complete"
                    },
                )
            }
        }

    @Test fun fittingSmallerNegotiatedBudgetsPreserveMeasuredLengths() =
        runBlocking {
            harness(20000 to 1000) {
                assertEquals(
                    "Complete",
                    controller
                        .upload(image)
                        .toList()
                        .last()
                        .stage,
                )
                val begin =
                    transport.requests
                        .first {
                            it.getString("method") == "upload.begin"
                        }.getJSONObject("params")
                assertEquals(image.image.length(), begin.getLong("imageBytes"))
                assertEquals(image.thumbnail.length(), begin.getLong("thumbnailBytes"))
                assertEquals(ImagePreparer.sha256(image.image), begin.getString("imageHash"))
                assertEquals(ImagePreparer.sha256(image.thumbnail), begin.getString("thumbnailHash"))
                assertEquals(1920, begin.getInt("width"))
                assertEquals(1080, begin.getInt("height"))
                assertEquals("contain", begin.getString("fit"))
                assertEquals(image.uploadId, begin.getString("uploadId"))
            }
        }

    @Test fun exceededNegotiatedBudgetsFailBeforeJournalOrRemoteWork() =
        runBlocking {
            for (limits in listOf(1000 to 1000, 20000 to 100)) {
                harness(limits) {
                    assertEquals(
                        "Failed",
                        controller
                            .upload(image)
                            .toList()
                            .last()
                            .stage,
                    )
                    assertEquals(0, transport.count("upload.begin"))
                    assertEquals(0, transport.count("upload.chunk"))
                    assertEquals(0, transport.count("upload.cancel"))
                    assertNull(pending.read())
                    assertTrue(image.image.exists())
                    assertTrue(image.thumbnail.exists())
                }
            }
        }

    @Test fun missingOrChangedFilesAndJournalWriteFailureDoNotBegin() =
        runBlocking {
            for (remove in listOf(false, true)) {
                harness {
                    if (remove) image.image.delete() else image.image.appendBytes(byteArrayOf(1))
                    assertEquals(
                        "Failed",
                        controller
                            .upload(image)
                            .toList()
                            .last()
                            .stage,
                    )
                    assertEquals(0, transport.count("upload.begin"))
                    assertEquals(0, transport.count("upload.cancel"))
                    assertNull(pending.read())
                }
            }
            harness {
                val blocked =
                    File(image.image.parentFile, "not-a-directory").apply {
                        writeText("owned fixture")
                    }
                val context =
                    object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
                        override fun getNoBackupFilesDir() = blocked
                    }
                val result = UploadController(client, PendingUploadStore(context)).upload(image).toList()
                assertEquals("Failed", result.last().stage)
                assertEquals(0, transport.count("upload.begin"))
                assertEquals(0, transport.count("upload.cancel"))
            }
        }

    @Test fun alreadyCommittedBeginRequiresRealWallpaperId() =
        runBlocking {
            for (id in listOf<Any>(JSONObject.NULL, "", 123, "user:already")) {
                harness {
                    transport.beginReply = JSONObject().put("status", "committed").put("wallpaperId", id)
                    val result = controller.upload(image).toList()
                    if (id ==
                        "user:already"
                    ) {
                        assertEquals("Complete", result.last().stage)
                        assertEquals(id, result.last().wallpaperId)
                        assertNull(pending.read())
                        assertEquals(0, transport.count("upload.cancel"))
                    } else {
                        assertEquals("Failed", result.last().stage)
                        assertFalse(
                            result.any {
                                it.stage == "Complete"
                            },
                        )
                        assertEquals(image.uploadId, pending.read()?.first)
                    }
                    assertEquals(0, transport.count("upload.cancel"))
                    assertEquals(0, transport.count("upload.chunk"))
                    assertEquals(0, transport.count("upload.commit"))
                }
            }
        }

    @Test fun invalidNumericAcksAndChangedLengthsCannotReachCommit() =
        runBlocking {
            val invalid: List<(Int) -> Any> =
                listOf({
                    it.toString()
                }, {
                    it + 0.5
                }, {
                    it.toLong() + 4294967296L
                }, {
                    0
                }, {
                    2
                })
            for (change in invalid) {
                harness {
                    transport.ackIndex = change
                    assertEquals(
                        "Failed",
                        controller
                            .upload(image)
                            .toList()
                            .last()
                            .stage,
                    )
                    assertEquals(0, transport.count("upload.commit"))
                    assertEquals(1, transport.count("upload.chunk"))
                }
            }
            harness {
                transport.ackStream = {
                    "wrong"
                }
                assertEquals(
                    "Failed",
                    controller
                        .upload(image)
                        .toList()
                        .last()
                        .stage,
                )
                assertEquals(0, transport.count("upload.commit"))
            }
            harness {
                transport.onBegin = {
                    image.image.writeBytes(byteArrayOf())
                }
                assertEquals(
                    "Failed",
                    controller
                        .upload(image)
                        .toList()
                        .last()
                        .stage,
                )
                assertEquals(0, transport.count("upload.chunk"))
                assertEquals(0, transport.count("upload.commit"))
            }
        }

    @Test fun smallEqualAndShortFinalImageChunksReconstructOriginalBytes() =
        runBlocking {
            for (size in listOf(100, 12288, 12295)) {
                harness(imageSize = size) {
                    val result = controller.upload(image).toList()
                    assertEquals("Complete", result.last().stage)
                    assertEquals(1, transport.count("upload.commit"))
                    assertTransferredBytes()
                    assertTrue(
                        result.all {
                            it.fraction in 0f..1f
                        },
                    )
                }
            }
        }

    @Test fun invalidCommitIdsNeverCompleteAndRefreshFailureCannotUndoConfirmedCommit() =
        runBlocking {
            for (id in listOf<Any>(JSONObject.NULL, "", 123)) {
                harness {
                    transport.commitReply = JSONObject().put("id", id)
                    val result = controller.upload(image).toList()
                    assertEquals("Failed", result.last().stage)
                    assertFalse(
                        result.any {
                            it.stage == "Complete"
                        },
                    )
                    assertEquals(image.uploadId, pending.read()?.first)
                    assertEquals(1, transport.count("upload.commit"))
                    assertEquals(0, transport.count("upload.cancel"))
                }
            }
            harness {
                transport.invalidRefresh = true
                val result = controller.upload(image).toList()
                assertEquals(
                    1,
                    result.count {
                        it.stage == "Complete"
                    },
                )
                assertFalse(
                    result.any {
                        it.stage == "Failed"
                    },
                )
                assertNull(pending.read())
                assertEquals(0, transport.count("upload.cancel"))
            }
        }

    @Test fun ambiguousCommitWithIncompleteOrMalformedStatusKeepsJournal() =
        runBlocking {
            for (status in listOf(
                JSONObject().put("status", "receiving"),
                JSONObject().put("status", "unknown"),
                JSONObject().put("status", "committed").put("wallpaperId", ""),
            )) {
                harness {
                    transport.loseCommit = true
                    transport.statusReply = status
                    val result = controller.upload(image).toList()
                    assertEquals("Failed", result.last().stage)
                    assertFalse(
                        result.any {
                            it.stage == "Complete"
                        },
                    )
                    assertEquals(1, transport.count("upload.commit"))
                    assertEquals(1, transport.count("upload.status"))
                    assertEquals(image.uploadId, pending.read()?.first)
                    assertEquals(
                        if (status.opt("status") == "committed") 0 else 1,
                        transport.count("upload.cancel"),
                    )
                    assertEquals(
                        image.uploadId,
                        transport.requests
                            .first {
                                it.getString("method") == "upload.status"
                            }.getJSONObject("params")
                            .getString("uploadId"),
                    )
                }
            }
        }

    @Test fun hangingOrFailingCancelIsBoundedAndPreservesCancellation() =
        runBlocking {
            for (hang in listOf(false, true)) {
                harness {
                    transport.holdChunk = true
                    transport.holdCancel = hang
                    transport.failCancel = !hang
                    val values = mutableListOf<UploadProgress>()
                    val job =
                        launch {
                            controller.upload(image).toList(values)
                        }
                    withTimeout(5000) {
                        transport.chunkArrived.await()
                    }
                    val start = android.os.SystemClock.elapsedRealtime()
                    job.cancelAndJoin()
                    assertTrue(android.os.SystemClock.elapsedRealtime() - start < 7000)
                    assertTrue(job.isCancelled)
                    assertEquals(1, transport.count("upload.cancel"))
                    assertEquals(0, transport.count("upload.commit"))
                    assertFalse(
                        values.any {
                            it.stage ==
                                "Complete" ||
                                it.stage == "Failed"
                        },
                    )
                    assertEquals(image.uploadId, pending.read()?.first)
                    assertTrue(image.image.exists())
                }
            }
            harness {
                transport.invalidAck = true
                transport.failCancel = true
                val values = controller.upload(image).toList()
                assertEquals("Failed", values.last().stage)
                assertEquals(
                    1,
                    values.count {
                        it.stage == "Failed"
                    },
                )
                assertFalse(
                    values
                        .last()
                        .message
                        .orEmpty()
                        .contains("Fixture cleanup"),
                )
            }
        }

    @Test fun localJournalClearFailureCannotUndoConfirmedCommit() =
        runBlocking {
            harness {
                val journal = File(image.image.parentFile, "pending-upload.json")
                transport.onCommit = {
                    check(journal.delete())
                    check(journal.mkdir())
                    File(journal, "block").writeText("owned fixture")
                }
                try {
                    val result = controller.upload(image).toList()
                    assertEquals(
                        1,
                        result.count {
                            it.stage == "Complete"
                        },
                    )
                    assertFalse(
                        result.any {
                            it.stage == "Failed"
                        },
                    )
                    assertEquals(0, transport.count("upload.cancel"))
                } finally {
                    check(journal.deleteRecursively())
                }
            }
        }

    @Test fun sessionReplacementBetweenChunksDoesNotSendOldUploadOrCancelOnNewSession() =
        runBlocking {
            harness {
                var replacementStart = -1
                val result =
                    controller
                        .upload(image)
                        .onEach { progress ->
                            if (progress.stage == "Sending" && replacementStart < 0) {
                                client.disconnect()
                                replacementStart = transport.requests.size
                                assertTrue(client.join() is DockResult.Success)
                            }
                        }.toList()
                assertEquals("Failed", result.last().stage)
                assertTrue(
                    transport.requests.drop(replacementStart).none {
                        it.getString("method").startsWith("upload.")
                    },
                )
                assertEquals(image.uploadId, pending.read()?.first)
                assertTrue(image.image.exists())
            }
        }

    @Test fun retiredPreflightCannotWriteJournalAfterSessionReplacement() =
        runBlocking {
            harness {
                val result =
                    controller
                        .upload(image)
                        .onEach {
                            if (it.stage ==
                                "Preparing"
                            ) {
                                client.disconnect()
                                assertTrue(client.join() is DockResult.Success)
                            }
                        }.toList()
                assertEquals("Failed", result.last().stage)
                assertNull(pending.read())
                assertEquals(0, transport.count("upload.begin"))
                assertEquals(0, transport.count("upload.cancel"))
            }
        }

    @Test fun oldCommitCompletionCannotClearNewerUploadJournal() =
        runBlocking {
            harness {
                val newer = UUID.randomUUID().toString()
                val result =
                    controller
                        .upload(image)
                        .onEach {
                            if (it.stage ==
                                "Committing"
                            ) {
                                pending.save(newer, "Newer upload fixture")
                            }
                        }.toList()
                assertEquals("Complete", result.last().stage)
                assertEquals(newer, pending.read()?.first)
            }
        }
}
