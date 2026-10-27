package com.emmah.colordock.remote.images

import android.content.ContextWrapper
import android.graphics.Bitmap
import android.util.Base64
import androidx.test.platform.app.InstrumentationRegistry
import com.emmah.colordock.remote.companion.CompanionClient
import com.emmah.colordock.remote.companion.DockResult
import com.emmah.colordock.remote.companion.DockTestFixtures
import com.emmah.colordock.remote.companion.DockTransport
import com.emmah.colordock.remote.companion.WallpaperMetadata
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID

class ThumbnailCacheTest {
    private class Transport(
        private val scope: CoroutineScope,
    ) : DockTransport {
        override val state = MutableStateFlow(TvState(TvConnectionState.Ready))
        override val dockAvailable = MutableStateFlow(true)
        override val dockMessages = MutableSharedFlow<JSONObject>()

        override suspend fun joinDockChannel() = Unit

        lateinit var data: ByteArray
        var chunkSize = 128
        var change: (JSONObject) -> Unit = {
        }
        var hold = false
        val requested = CompletableDeferred<Unit>()

        override fun sendDock(payload: JSONObject) {
            val params = payload.getJSONObject("params")
            val result =
                when (payload.getString("method")) {
                    "hello" -> {
                        DockTestFixtures.hello()
                    }

                    "state.get" -> {
                        DockTestFixtures.snapshot(revision = 1, personal = 1, shelfOrder = emptyList(), title = null)
                    }

                    "gallery.thumbnail" -> {
                        requested.complete(Unit)
                        if (hold) return
                        val index = params.getInt("index")
                        val end = minOf(data.size, (index + 1) * chunkSize)
                        JSONObject()
                            .put(
                                "wallpaperId",
                                params.getString("wallpaperId"),
                            ).put("index", index)
                            .put("hash", digest(data))
                            .put("bytes", data.size)
                            .put(
                                "data",
                                Base64.encodeToString(data.copyOfRange(index * chunkSize, end), Base64.NO_WRAP),
                            ).put("nextIndex", if (end == data.size) JSONObject.NULL else index + 1)
                            .also(change)
                    }

                    else -> {
                        error("Unexpected method")
                    }
                }
            scope.launch {
                dockMessages.emit(DockTestFixtures.reply(payload, result))
            }
        }

        private fun digest(bytes: ByteArray) =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
                "%02x".format(it)
            }
    }

    private fun prefix(
        identity: String,
        id: String,
    ) = MessageDigest.getInstance("SHA-256").digest("$identity:$id".toByteArray()).joinToString("") {
        "%02x".format(it)
    }

    private suspend fun assertRejectedThenRetried(
        cache: ThumbnailCache,
        directory: File,
        image: File,
        item: WallpaperMetadata,
        transport: Transport,
    ) {
        assertTrue(
            runCatching {
                cache.get("TV-A", item)
            }.isFailure,
        )
        assertTrue(directory.listFiles().orEmpty().isEmpty())
        transport.change = {
        }
        assertArrayEquals(image.readBytes(), cache.get("TV-A", item).readBytes())
    }

    private suspend fun fixture(
        download: Boolean = false,
        action: suspend (ThumbnailCache, File, File, String, Transport) -> Unit,
    ) {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val root =
            File(target.cacheDir, "thumbnail-test-${UUID.randomUUID()}").apply {
                check(mkdirs())
            }
        val context =
            object : ContextWrapper(target) {
                override fun getCacheDir() = root
            }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val transport = Transport(scope)
        val client = CompanionClient(transport, scope)
        val image = File(root, "fixture.jpg")
        val bitmap = Bitmap.createBitmap(20, 10, Bitmap.Config.ARGB_8888)
        try {
            image.outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it))
            }
            transport.data = image.readBytes()
            if (download) assertTrue(client.join() is DockResult.Success)
            val cache = ThumbnailCache(context, client)
            action(cache, File(root, "thumbnails"), image, ImagePreparer.sha256(image), transport)
        } finally {
            bitmap.recycle()
            client.disconnect()
            scope.cancel()
            root.deleteRecursively()
        }
    }

    @Test fun validHitsAreSeparatedByTvAndExplicitBuiltinHashDoesNotUseOldFallback() =
        runBlocking {
            fixture { cache, directory, image, hash, _ ->
                val personal = WallpaperMetadata("user:fixture", "Fixture", "cover", null, null, hash)
                val saved =
                    File(directory, "${prefix("TV-A",personal.id)}-$hash.jpg").apply {
                        image.copyTo(this)
                    }
                assertEquals(saved, cache.get("TV-A", personal))
                assertTrue(
                    runCatching {
                        cache.get("TV-B", personal)
                    }.exceptionOrNull() is IOException,
                )
                val builtin = personal.copy(id = "builtin:fixture", thumbnailHash = null)
                val fallback =
                    File(directory, "${prefix("TV-A",builtin.id)}-$hash.jpg").apply {
                        image.copyTo(this)
                    }
                assertEquals(fallback, cache.get("TV-A", builtin))
                assertTrue(
                    runCatching {
                        cache.get("TV-A", builtin.copy(thumbnailHash = "ab".repeat(32)))
                    }.exceptionOrNull() is IOException,
                )
            }
        }

    @Test fun malformedMetadataHashesNeverResolveAsCachePaths() =
        runBlocking {
            fixture { cache, directory, image, _, _ ->
                val id = "user:fixture"
                val key = prefix("TV-A", id)
                File(directory, "$key-..").mkdirs()
                val outside =
                    File(directory.parentFile, "outside.jpg").apply {
                        image.copyTo(this)
                    }
                assertEquals(outside.canonicalFile, File(directory, "$key-../../../outside.jpg").canonicalFile)
                for (hash in listOf("bad", "A".repeat(64), "x".repeat(4096), "../../../outside")) {
                    assertTrue(
                        runCatching {
                            cache.get("TV-A", WallpaperMetadata(id, "Fixture", "cover", null, null, hash))
                        }.exceptionOrNull() is IllegalArgumentException,
                    )
                }
                assertTrue(outside.exists())
            }
        }

    private fun malformedChunks(): List<(JSONObject) -> Unit> =
        listOf(
            {
                it.put("wallpaperId", "wrong")
            },
            {
                it.put("index", 0.5)
            },
            {
                it.put("index", "0")
            },
            {
                it.put("bytes", 4294967296L + it.getLong("bytes"))
            },
            {
                it.put("bytes", 131073)
            },
            {
                it.put("nextIndex", 0)
            },
            {
                it.put("nextIndex", 2)
            },
            {
                it.put("nextIndex", JSONObject.NULL)
            },
            {
                it.put("data", "!")
            },
            {
                it.put("data", Base64.encodeToString(ByteArray(12289), Base64.NO_WRAP))
            },
            {
                it.put("hash", "ab".repeat(32))
            },
            {
                if (it.getInt("index") > 0) it.put("hash", "ab".repeat(32))
            },
            {
                if (it.getInt("index") > 0) it.put("bytes", it.getInt("bytes") + 1)
            },
        )

    @Test fun malformedChunksPublishNothingAndValidRetryAssemblesExactBytes() =
        runBlocking {
            for (change in malformedChunks()) {
                fixture(true) { cache, directory, image, hash, transport ->
                    transport.change = change
                    val item = WallpaperMetadata("user:fixture", "Fixture", "cover", null, null, hash)
                    assertRejectedThenRetried(cache, directory, image, item, transport)
                }
            }
            fixture(true) { cache, directory, image, hash, transport ->
                transport.chunkSize = transport.data.size
                val item = WallpaperMetadata("user:fixture", "Fixture", "cover", null, null, hash)
                transport.change = {
                    it.put("nextIndex", 1)
                }
                assertRejectedThenRetried(cache, directory, image, item, transport)
            }
            fixture(true) { cache, directory, image, _, transport ->
                transport.change = {
                    it.put("hash", "ab".repeat(32))
                }
                assertRejectedThenRetried(
                    cache,
                    directory,
                    image,
                    WallpaperMetadata("builtin:fixture", "Fixture", "cover", null, null),
                    transport,
                )
            }
        }

    @Test fun cancelledAssemblyPublishesNothingAndCanBeRetried() =
        runBlocking {
            fixture(true) { cache, directory, image, hash, transport ->
                transport.hold = true
                val item = WallpaperMetadata("user:fixture", "Fixture", "cover", null, null, hash)
                val job =
                    launch {
                        cache.get("TV-A", item)
                    }
                withTimeout(5000) {
                    transport.requested.await()
                }
                job.cancelAndJoin()
                assertTrue(directory.listFiles().orEmpty().isEmpty())
                transport.hold = false
                assertArrayEquals(image.readBytes(), cache.get("TV-A", item).readBytes())
            }
        }

    @Test fun corruptHitsAreReplacedAndEvictionProtectsReturnedFile() =
        runBlocking {
            fixture(true) { cache, directory, image, hash, _ ->
                val item = WallpaperMetadata("user:fixture", "Fixture", "cover", null, null, hash)
                val file = File(directory, "${prefix("TV-A",item.id)}-$hash.jpg")
                for (bytes in listOf(byteArrayOf(), image.readBytes().copyOf(20))) {
                    file.writeBytes(bytes)
                    assertArrayEquals(image.readBytes(), cache.get("TV-A", item).readBytes())
                }
                file.delete()
                val old =
                    File(directory, "old.jpg").apply {
                        java.io.RandomAccessFile(this, "rw").use {
                            it.setLength(20L * 1024 * 1024)
                        }
                        setLastModified(1)
                    }
                val newer =
                    File(directory, "newer.jpg").apply {
                        writeBytes(ByteArray(100))
                        setLastModified(2)
                    }
                assertArrayEquals(image.readBytes(), cache.get("TV-A", item).readBytes())
                assertFalse(old.exists())
                assertTrue(newer.exists())
                assertTrue(file.exists())
                assertTrue(
                    directory.listFiles().orEmpty().sumOf {
                        it.length()
                    } <= 20L * 1024 * 1024,
                )
            }
        }

    @Test fun failedPublicationLeavesNoPartialFileAndCanRetry() =
        runBlocking {
            fixture(true) { cache, directory, image, hash, _ ->
                val item = WallpaperMetadata("user:fixture", "Fixture", "cover", null, null, hash)
                val blocked =
                    File(directory, "${prefix("TV-A",item.id)}-$hash.jpg").apply {
                        check(mkdir())
                        File(this, "keep").writeText("block rename")
                    }
                assertTrue(
                    runCatching {
                        cache.get("TV-A", item)
                    }.isFailure,
                )
                assertTrue(
                    directory.listFiles().orEmpty().none {
                        it.extension == "pending"
                    },
                )
                check(blocked.deleteRecursively())
                assertArrayEquals(image.readBytes(), cache.get("TV-A", item).readBytes())
            }
        }
}
