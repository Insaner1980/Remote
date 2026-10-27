package com.emmah.colordock.remote.companion

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID

private const val MAX_PENDING_NAME = 120
private const val MAX_PENDING_BYTES = 2048

class PendingUploadStore(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "pending-upload.json"))

    suspend fun save(
        id: String,
        name: String,
        isCurrent: () -> Boolean = {
            true
        },
    ) = withContext(ioDispatcher) {
        lock.withLock {
            check(isCurrent())
            UUID.fromString(id)
            val bytes =
                JSONObject()
                    .put("uploadId", id)
                    .put("name", name.take(MAX_PENDING_NAME))
                    .toString()
                    .toByteArray()
            require(bytes.size <= MAX_PENDING_BYTES)
            val output = file.startWrite()
            var saved = false
            try {
                output.write(bytes)
                file.finishWrite(output)
                if (!file
                        .openRead()
                        .use {
                            it.readNBytes(
                                MAX_PENDING_BYTES + 1,
                            )
                        }.contentEquals(bytes)
                ) {
                    throw IOException("The pending upload could not be saved.")
                }
                saved = true
            } finally {
                if (!saved) file.failWrite(output)
            }
        }
    }

    suspend fun read(): Pair<String, String>? =
        withContext(ioDispatcher) {
            lock.withLock {
                readLocked()
            }
        }

    private fun readLocked(): Pair<String, String>? {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return null
        val bytes =
            file.openRead().use {
                it.readNBytes(MAX_PENDING_BYTES + 1)
            }
        return try {
            require(bytes.size <= MAX_PENDING_BYTES)
            val value = JSONObject(String(bytes, Charsets.UTF_8))
            val id = requireNotNull(value.opt("uploadId") as? String)
            UUID.fromString(id)
            id to
                requireNotNull(value.opt("name") as? String).take(MAX_PENDING_NAME)
        } catch (_: JSONException) {
            clearLocked()
            null
        } catch (_: IllegalArgumentException) {
            clearLocked()
            null
        }
    }

    suspend fun clear(expectedId: String? = null) =
        withContext(ioDispatcher) {
            lock.withLock {
                if (expectedId == null ||
                    readLocked()?.first == expectedId
                ) {
                    clearLocked()
                }
            }
        }

    private fun clearLocked() {
        file.delete()
        if (listOf(
                "",
                ".bak",
                ".new",
            ).any {
                File(file.baseFile.path + it).exists()
            }
        ) {
            throw IOException("The pending upload could not be removed.")
        }
    }

    private companion object {
        val lock = Mutex()
    }
}
