package com.emmah.colordock.remote.images

import android.content.Context
import android.util.Base64
import com.emmah.colordock.remote.companion.CompanionClient
import com.emmah.colordock.remote.companion.DockCodec
import com.emmah.colordock.remote.companion.DockMethod
import com.emmah.colordock.remote.companion.DockResult
import com.emmah.colordock.remote.companion.WallpaperMetadata
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

private const val MAX_THUMBNAIL_BYTES = 131072L
private const val MAX_CHUNK_BYTES = 12288
private const val THUMBNAIL_CACHE_BYTES = 20L * 1024 * 1024
private val thumbnailHashPattern = Regex("[0-9a-f]{64}")

class ThumbnailCache(
    context: Context,
    private val dock: CompanionClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val directory = File(context.cacheDir, "thumbnails").apply { mkdirs() }
    private val lock = Mutex()

    private fun hit(
        file: File,
        hash: String,
    ): File? {
        if (!file.exists()) return null
        return if (runCatching {
                file.isFile && file.length() in 1..MAX_THUMBNAIL_BYTES && ImagePreparer.sha256(file) == hash
            }.getOrDefault(false)
        ) {
            file.touchCacheFile()
            file
        } else {
            file.deleteCacheFile()
            null
        }
    }

    suspend fun get(
        identity: String,
        item: WallpaperMetadata,
    ): File {
        val origin = dock.sessionGeneration
        return lock.withLock {
            withContext(ioDispatcher) {
                val prefix = hash((identity + ":" + item.id).toByteArray())
                cached(prefix, item) ?: download(prefix, item, origin)
            }
        }
    }

    private fun cached(
        prefix: String,
        item: WallpaperMetadata,
    ): File? {
        item.thumbnailHash?.let { hash ->
            require(hash.matches(thumbnailHashPattern))
            return hit(File(directory, "$prefix-$hash.jpg"), hash)
        }
        return if (item.personal) {
            null
        } else {
            directory.listFiles().orEmpty().firstNotNullOfOrNull { file ->
                val hash = file.name.removePrefix("$prefix-").removeSuffix(".jpg")
                if (file.name == "$prefix-$hash.jpg" && hash.matches(thumbnailHashPattern)) hit(file, hash) else null
            }
        }
    }

    private suspend fun download(
        prefix: String,
        item: WallpaperMetadata,
        origin: Long,
    ): File {
        val bytes = ByteArrayOutputStream()
        var index = 0
        var expectedHash: String? = null
        var length = 0
        do {
            val reply =
                dock.call(
                    DockMethod.Thumbnail,
                    JSONObject().put("wallpaperId", item.id).put("index", index),
                    origin,
                )
            val value =
                (reply as? DockResult.Success)?.value
                    ?: throw IOException("The picture preview is unavailable.")
            val chunk = parseChunk(value, item.id, index)
            if (expectedHash == null) {
                require(item.thumbnailHash == null || item.thumbnailHash == chunk.hash)
                expectedHash = chunk.hash
                length = chunk.length
            } else {
                require(expectedHash == chunk.hash && length == chunk.length)
            }
            require(bytes.size() + chunk.data.size <= length)
            bytes.write(chunk.data)
            if (chunk.nextIndex == null) break
            require(bytes.size() < length)
            index++
            require(chunk.nextIndex == index.toLong())
        } while (bytes.size() < length)
        require(bytes.size() == length)
        val data = bytes.toByteArray()
        val actual = hash(data)
        require(actual == expectedHash)
        return save(prefix, actual, data)
    }

    private suspend fun save(
        prefix: String,
        hash: String,
        data: ByteArray,
    ): File {
        if (!directory.isDirectory && !directory.mkdirs()) {
            throw IOException("The picture preview directory could not be created.")
        }
        val file = File(directory, "$prefix-$hash.jpg")
        val temporary = File.createTempFile("$prefix-", ".pending", directory)
        try {
            temporary.writeBytes(data)
            coroutineContext.ensureActive()
            if (!temporary.renameTo(file)) throw IOException("The picture preview could not be saved.")
        } finally {
            temporary.deleteCacheFile()
        }
        var total = directory.listFiles()?.sumOf { it.length() } ?: 0
        for (old in directory.listFiles().orEmpty().sortedBy { it.lastModified() }) {
            if (total <= THUMBNAIL_CACHE_BYTES) break
            if (old != file) {
                val size = old.length()
                if (old.delete()) total -= size
            }
        }
        return file
    }
}

private data class ThumbnailChunk(
    val hash: String,
    val length: Int,
    val data: ByteArray,
    val nextIndex: Long?,
)

private fun parseChunk(
    value: JSONObject,
    wallpaperId: String,
    index: Int,
): ThumbnailChunk {
    require(DockCodec.integer(value.opt("index")) == index.toLong() && value.opt("wallpaperId") == wallpaperId)
    val hash = requireNotNull(value.opt("hash") as? String) { "Invalid thumbnail hash" }
    require(hash.matches(thumbnailHashPattern))
    val length = DockCodec.integer(value.opt("bytes"))
    require(length in 1..MAX_THUMBNAIL_BYTES)
    val data = Base64.decode(requireNotNull(value.opt("data") as? String) { "Invalid thumbnail data" }, Base64.NO_WRAP)
    require(data.size in 1..MAX_CHUNK_BYTES)
    val next = if (value.isNull("nextIndex")) null else DockCodec.integer(value.opt("nextIndex"))
    return ThumbnailChunk(hash, length.toInt(), data, next)
}

private fun hash(bytes: ByteArray) =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
        "%02x".format(it)
    }
