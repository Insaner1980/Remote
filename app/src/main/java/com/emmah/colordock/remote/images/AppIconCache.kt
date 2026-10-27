package com.emmah.colordock.remote.images

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.emmah.colordock.remote.tv.MAX_APP_ICON_BYTES
import com.emmah.colordock.remote.tv.TvApp
import com.emmah.colordock.remote.tv.TvResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

private const val PNG_QUALITY = 100
private const val MAX_ICON_DIMENSION = 4096
private const val MAX_ICON_PIXELS = 16_000_000L
private const val DECODED_ICON_SIZE = 256
private const val ICON_CACHE_BYTES = 5L * 1024 * 1024

class AppIconCache(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val fetch: suspend (String) -> TvResult<ByteArray>,
) {
    private val directory = File(context.cacheDir, "tv-app-icons")
    private val writes = Mutex()
    private val downloads = Semaphore(2)

    internal suspend fun cached(
        identity: String,
        app: TvApp,
    ): File? =
        withContext(ioDispatcher) {
            writes.withLock {
                hit(key(identity, app))
            }
        }

    suspend fun get(
        identity: String,
        app: TvApp,
        refresh: Boolean = false,
    ): File? =
        withContext(ioDispatcher) {
            downloads.withPermit {
                val key = key(identity, app)
                val previous =
                    writes.withLock {
                        hit(key)
                    }
                if (previous != null && !refresh) return@withPermit previous
                val url = app.iconUrl ?: return@withPermit previous
                try {
                    val data = (fetch(url) as? TvResult.Success)?.value ?: return@withPermit previous
                    val bitmap = decode(data) ?: return@withPermit previous
                    try {
                        save(key, data, bitmap)
                    } finally {
                        bitmap.recycle()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    previous?.takeIf {
                        it.isFile
                    }
                }
            }
        }

    private suspend fun save(
        key: String,
        data: ByteArray,
        bitmap: Bitmap,
    ): File =
        writes.withLock {
            check(directory.isDirectory || directory.mkdirs())
            val file = File(directory, "$key-${hash(data)}.png")
            val temporary = File.createTempFile("icon-", ".pending", directory)
            try {
                temporary.outputStream().use {
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it))
                }
                coroutineContext.ensureActive()
                Files.move(
                    temporary.toPath(),
                    file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } finally {
                temporary.deleteCacheFile()
            }
            directory
                .listFiles()
                .orEmpty()
                .filter {
                    it != file && it.name.startsWith("$key-")
                }.forEach {
                    it.deleteCacheFile()
                }
            var size =
                directory.listFiles().orEmpty().sumOf {
                    it.length()
                }
            for (old in directory.listFiles().orEmpty().sortedBy {
                it.lastModified()
            }) {
                if (size <= ICON_CACHE_BYTES) break
                if (old != file) {
                    val length = old.length()
                    if (old.delete()) size -= length
                }
            }
            file
        }

    private fun hit(key: String): File? {
        for (file in directory
            .listFiles()
            .orEmpty()
            .filter {
                it.name.startsWith("$key-") && it.extension == "png"
            }.sortedByDescending {
                it.lastModified()
            }) {
            if (file.length() in 1..MAX_APP_ICON_BYTES.toLong()) {
                val bitmap =
                    runCatching {
                        decode(file.readBytes())
                    }.getOrNull()
                if (bitmap != null) {
                    bitmap.recycle()
                    file.touchCacheFile()
                    return file
                }
            }
            file.deleteCacheFile()
        }
        return null
    }

    private fun key(
        identity: String,
        app: TvApp,
    ) = hash(
        listOf(identity, app.id, app.iconUrl.orEmpty())
            .joinToString("") {
                "${it.length}:$it"
            }.toByteArray(),
    )

    private fun hash(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
            "%02x".format(it)
        }

    private fun decode(bytes: ByteArray): Bitmap? {
        if (bytes.isEmpty() || bytes.size > MAX_APP_ICON_BYTES) return null
        val bounds =
            BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        return if (bounds.outWidth !in 1..MAX_ICON_DIMENSION || bounds.outHeight !in 1..MAX_ICON_DIMENSION ||
            bounds.outWidth.toLong() * bounds.outHeight > MAX_ICON_PIXELS
        ) {
            null
        } else {
            var sample = 1
            while ((maxOf(bounds.outWidth, bounds.outHeight) + sample - 1) / sample > DECODED_ICON_SIZE) sample *= 2
            BitmapFactory
                .decodeByteArray(
                    bytes,
                    0,
                    bytes.size,
                    BitmapFactory.Options().apply {
                        inSampleSize = sample
                    },
                )?.takeIf {
                    val valid = it.width <= DECODED_ICON_SIZE && it.height <= DECODED_ICON_SIZE
                    if (!valid) it.recycle()
                    valid
                }
        }
    }
}
