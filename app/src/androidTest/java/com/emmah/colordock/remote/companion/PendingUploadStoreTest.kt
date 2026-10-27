package com.emmah.colordock.remote.companion

import android.content.ContextWrapper
import android.util.AtomicFile
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.UUID

class PendingUploadStoreTest {
    private suspend fun fixture(action: suspend (PendingUploadStore, File) -> Unit) {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val root =
            File(target.cacheDir, "pending-test-${UUID.randomUUID()}").apply {
                check(mkdir())
            }
        val context =
            object : ContextWrapper(target) {
                override fun getNoBackupFilesDir() = root
            }
        try {
            action(PendingUploadStore(context), File(root, "pending-upload.json"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun roundTripInterruptedWriteAndLegacyBackupRecoverOwnedRecord() =
        runBlocking {
            fixture { store, file ->
                val id = UUID.randomUUID().toString()
                val name = "地".repeat(120)
                store.save(id, name)
                assertEquals(id to name, store.read())
                assertTrue(file.length() <= 2048)
                assertTrue(
                    runCatching {
                        store.save(UUID.randomUUID().toString(), "retired owner") {
                            false
                        }
                    }.isFailure,
                )
                assertEquals(id to name, store.read())
                val atomic = AtomicFile(file)
                val partial = atomic.startWrite()
                partial.write("partial".toByteArray())
                atomic.failWrite(partial)
                assertEquals(id to name, store.read())
                check(file.renameTo(File(file.path + ".bak")))
                assertEquals(id to name, store.read())
                assertTrue(file.exists())
                val newer = UUID.randomUUID().toString()
                store.save(newer, "newer fixture")
                store.clear(id)
                assertEquals(newer, store.read()?.first)
                store.clear(newer)
                assertNull(store.read())
                store.clear()
                assertNull(store.read())
            }
        }

    @Test fun malformedOversizedAndWrongTypeRecordsAreRejected() =
        runBlocking {
            fixture { store, file ->
                val id = UUID.randomUUID().toString()
                for (text in listOf(
                    "broken",
                    JSONObject().put("uploadId", id).put("name", 123).toString(),
                    JSONObject().put("uploadId", "bad").put("name", "fixture").toString(),
                )) {
                    file.writeText(text)
                    assertNull(store.read())
                    assertFalse(file.exists())
                }
                RandomAccessFile(file, "rw").use {
                    it.setLength(2L * 1024 * 1024)
                }
                assertNull(store.read())
                assertFalse(file.exists())
            }
        }

    @Test fun filesystemReadWriteAndClearFailuresAreNotReportedAsSuccess() =
        runBlocking {
            fixture { store, file ->
                check(file.mkdir())
                val child =
                    File(file, "keep").apply {
                        writeText("owned fixture")
                    }
                assertTrue(
                    runCatching {
                        store.read()
                    }.exceptionOrNull() is IOException,
                )
                assertTrue(child.exists())
                assertTrue(
                    runCatching {
                        store.clear()
                    }.exceptionOrNull() is IOException,
                )
                assertTrue(child.exists())
                assertTrue(
                    runCatching {
                        store.save(UUID.randomUUID().toString(), "fixture")
                    }.exceptionOrNull() is IOException,
                )
                assertTrue(child.exists())
            }
        }
}
