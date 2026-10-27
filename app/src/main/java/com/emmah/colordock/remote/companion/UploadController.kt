package com.emmah.colordock.remote.companion

import android.util.Base64
import com.emmah.colordock.remote.images.ImagePreparer
import com.emmah.colordock.remote.images.PreparedImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

data class UploadProgress(
    val stage: String,
    val fraction: Float = 0f,
    val wallpaperId: String? = null,
    val message: String? = null,
)

private const val CANCEL_TIMEOUT_MILLIS = 5000L

class UploadController(
    private val dock: CompanionClient,
    private val pending: PendingUploadStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    fun upload(image: PreparedImage): Flow<UploadProgress> =
        flow {
            val transfer = Transfer(image, dock.sessionGeneration, this)
            try {
                transfer.run()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: UploadException) {
                emit(UploadProgress("Failed", message = error.message))
            } catch (_: IOException) {
                emit(interrupted())
            } catch (_: IllegalArgumentException) {
                emit(interrupted())
            } catch (_: IllegalStateException) {
                emit(interrupted())
            } catch (_: SecurityException) {
                emit(interrupted())
            } catch (_: JSONException) {
                emit(interrupted())
            } finally {
                transfer.cancelIncomplete()
            }
        }

    private inner class Transfer(
        private val image: PreparedImage,
        private val origin: Long,
        private val progress: FlowCollector<UploadProgress>,
    ) {
        private var committed = false
        private var began = false
        private var sent = 0L

        private suspend fun call(
            method: DockMethod,
            params: JSONObject,
        ) = dock.call(method, params, origin)

        suspend fun run() {
            progress.emit(UploadProgress("Preparing"))
            val limits = dock.limits
            val imageBytes = validateFile(image.image, limits.imageBytes)
            val thumbnailBytes = validateFile(image.thumbnail, limits.thumbnailBytes)
            withContext(ioDispatcher) {
                require(ImagePreparer.sha256(image.image) == image.imageHash)
                require(ImagePreparer.sha256(image.thumbnail) == image.thumbnailHash)
            }
            pending.save(image.uploadId, image.name) { dock.isCurrent(origin) }
            val meta =
                JSONObject()
                    .put("uploadId", image.uploadId)
                    .put("name", image.name)
                    .put("fit", image.fit)
                    .put("width", image.width)
                    .put("height", image.height)
                    .put("imageBytes", imageBytes)
                    .put("thumbnailBytes", thumbnailBytes)
                    .put("imageHash", image.imageHash)
                    .put("thumbnailHash", image.thumbnailHash)
            began = true
            val begin = required(call(DockMethod.UploadBegin, meta))
            if (begin.opt("status") == "committed") {
                committed = true
                complete(wallpaperId(begin, "wallpaperId"))
                return
            }
            val total = imageBytes + thumbnailBytes
            sendStream("image", image.image, imageBytes, limits.chunkBytes, total)
            sendStream("thumbnail", image.thumbnail, thumbnailBytes, limits.chunkBytes, total)
            progress.emit(UploadProgress("Committing", 1f))
            complete(commit())
            dock.snapshot(origin)
        }

        private suspend fun sendStream(
            stream: String,
            file: File,
            length: Long,
            chunkBytes: Int,
            total: Long,
        ) {
            var opened: RandomAccessFile? = null
            try {
                withContext(ioDispatcher) { opened = RandomAccessFile(file, "r") }
                val input = requireNotNull(opened)
                require(input.length() == length)
                var index = 0
                val bytes = ByteArray(chunkBytes)
                while (input.filePointer < length) {
                    currentCoroutineContext().ensureActive()
                    val count =
                        withContext(ioDispatcher) {
                            require(input.length() == length)
                            minOf(bytes.size.toLong(), length - input.filePointer).toInt().also {
                                input.readFully(bytes, 0, it)
                                require(input.length() == length)
                            }
                        }
                    val params =
                        JSONObject()
                            .put("uploadId", image.uploadId)
                            .put("stream", stream)
                            .put("index", index)
                            .put("data", Base64.encodeToString(bytes, 0, count, Base64.NO_WRAP))
                    val acknowledgement = sendChunk(params)
                    val completeImage = stream == "image" && input.filePointer == length
                    require(acknowledgement.opt("stream") == if (completeImage) "thumbnail" else stream)
                    require(
                        DockCodec.integer(acknowledgement.opt("nextIndex")) ==
                            (if (completeImage) 0 else index + 1).toLong(),
                    )
                    sent += count
                    index++
                    progress.emit(UploadProgress("Sending", sent.toFloat() / total))
                }
            } finally {
                withContext(NonCancellable + ioDispatcher) {
                    try {
                        opened?.close()
                    } catch (_: IOException) {
                        // Preserve the transfer outcome if closing fails.
                    }
                }
            }
        }

        private suspend fun sendChunk(params: JSONObject): JSONObject {
            var response = call(DockMethod.UploadChunk, params)
            repeat(2) {
                if (response is DockResult.Failure && response.code == "TIMEOUT") {
                    response = call(DockMethod.UploadChunk, params)
                }
            }
            return required(response)
        }

        private suspend fun commit(): String {
            val params = JSONObject().put("uploadId", image.uploadId)
            val result = call(DockMethod.UploadCommit, params)
            return if (result is DockResult.Success) {
                committed = true
                wallpaperId(result.value, "id")
            } else {
                val status = required(call(DockMethod.UploadStatus, params))
                if (status.opt("status") != "committed") required(result)
                committed = true
                wallpaperId(status, "wallpaperId")
            }
        }

        private suspend fun complete(id: String) {
            committed = true
            try {
                pending.clear(image.uploadId)
            } catch (_: IOException) {
                // A local cleanup failure cannot undo a confirmed TV commit.
            }
            progress.emit(UploadProgress("Complete", 1f, id))
        }

        suspend fun cancelIncomplete() {
            if (began && !committed) {
                withContext(NonCancellable) {
                    withTimeoutOrNull(CANCEL_TIMEOUT_MILLIS) {
                        call(DockMethod.UploadCancel, JSONObject().put("uploadId", image.uploadId))
                    }
                }
            }
        }
    }
}

private fun validateFile(
    file: File,
    limit: Int,
): Long {
    val length = file.length()
    if (!file.isFile || !file.canRead() || length !in 1..limit.toLong()) {
        throw UploadException("This picture exceeds the TV image limits or is no longer available. Select it again.")
    }
    return length
}

private fun wallpaperId(
    value: JSONObject,
    key: String,
) = requireNotNull(value.opt(key) as? String).also {
    require(it.isNotBlank())
}

private fun interrupted() =
    UploadProgress(
        "Failed",
        message = "The upload was interrupted. Reconnect to Color Dock and try again.",
    )

private class UploadException(
    message: String,
) : IOException(message)

private fun required(result: DockResult<JSONObject>): JSONObject =
    when (result) {
        is DockResult.Success -> result.value
        is DockResult.Failure -> throw UploadException(result.message)
    }
