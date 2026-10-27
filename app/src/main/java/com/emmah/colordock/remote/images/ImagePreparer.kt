package com.emmah.colordock.remote.images

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import kotlin.coroutines.coroutineContext
import kotlin.math.min

data class PreparedImage(
    val uploadId: String,
    val name: String,
    val fit: String,
    val image: File,
    val thumbnail: File,
    val width: Int,
    val height: Int,
    val imageHash: String,
    val thumbnailHash: String,
) {
    fun delete() {
        image.deleteCacheFile()
        thumbnail.deleteCacheFile()
    }
}

class ImagePreparationException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

private const val SOURCE_BUFFER_BYTES = 16384
private const val SOURCE_UNAVAILABLE = "This picture is no longer accessible. Select it again."
private const val MAX_SOURCE_BYTES = 50L * 1024 * 1024
private const val MAX_SOURCE_PIXELS = 100_000_000L
private const val IMAGE_WIDTH = 1920.0
private const val IMAGE_HEIGHT = 1080.0
private const val THUMBNAIL_WIDTH = 320.0
private const val THUMBNAIL_HEIGHT = 180.0
private const val IMAGE_BYTES = 4194304L
private const val THUMBNAIL_BYTES = 131072L
private const val MAX_IMAGE_NAME = 120
private const val FLATTEN_BACKGROUND = 0xFF111719.toInt()
private const val JPEG_INITIAL_QUALITY = 90
private const val JPEG_MIN_QUALITY = 40
private const val JPEG_QUALITY_STEP = 10

class ImagePreparer(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    // Content providers and platform decoders can throw vendor-specific exceptions.
    // Preserve their existing user-facing failure contract.
    @Suppress("TooGenericExceptionCaught")
    suspend fun prepare(
        uri: Uri,
        name: String,
        fit: String = "cover",
    ): PreparedImage {
        var completed: PreparedImage? = null
        try {
            return withContext(ioDispatcher) {
                prepareFiles(uri, name, fit).also { completed = it }
            }
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable + ioDispatcher) { completed?.delete() }
            throw cancelled
        } catch (error: Exception) {
            throw preparationFailure(error)
        }
    }

    private fun preparationFailure(error: Exception): Exception =
        when (error) {
            is ImagePreparationException -> error
            else -> ImagePreparationException("This picture is corrupt or its format is not supported.", error)
        }

    private suspend fun prepareFiles(
        uri: Uri,
        name: String,
        fit: String,
    ): PreparedImage {
        val directory = File(context.cacheDir, "uploads").apply { mkdirs() }
        val id = UUID.randomUUID().toString()
        val source = File(directory, "$id.source")
        val image = File(directory, "$id.jpg")
        val thumb = File(directory, "$id.thumb.jpg")
        var decoded: Bitmap? = null
        var flattened: Bitmap? = null
        var small: Bitmap? = null
        var completed = false
        try {
            copySource(uri, source)
            val bitmap = decode(source).also { decoded = it }
            val opaque = createBitmap(bitmap.width, bitmap.height).also { flattened = it }
            Canvas(opaque).apply {
                drawColor(FLATTEN_BACKGROUND)
                drawBitmap(bitmap, 0f, 0f, null)
            }
            encode(opaque, image, IMAGE_BYTES)
            val factor = min(1.0, min(THUMBNAIL_WIDTH / bitmap.width, THUMBNAIL_HEIGHT / bitmap.height))
            small = opaque.scale(maxOf(1, (bitmap.width * factor).toInt()), maxOf(1, (bitmap.height * factor).toInt()))
            encode(small, thumb, THUMBNAIL_BYTES)
            return PreparedImage(
                id,
                name.take(MAX_IMAGE_NAME).ifBlank { "Picture" },
                fit,
                image,
                thumb,
                bitmap.width,
                bitmap.height,
                sha256(image),
                sha256(thumb),
            ).also { completed = true }
        } catch (error: OutOfMemoryError) {
            throw ImagePreparationException("This picture needs too much memory. Choose a smaller picture.", error)
        } finally {
            if (!completed) {
                image.deleteCacheFile()
                thumb.deleteCacheFile()
            }
            source.deleteCacheFile()
            small?.takeIf { it !== flattened }?.recycle()
            flattened?.recycle()
            decoded?.recycle()
        }
    }

    private suspend fun copySource(
        uri: Uri,
        source: File,
    ) {
        val input = openSource(uri) ?: throw ImagePreparationException(SOURCE_UNAVAILABLE)
        input.use { copyImageSource(it, source) }
    }

    private fun openSource(uri: Uri): InputStream? =
        try {
            context.contentResolver.openInputStream(uri)
        } catch (error: FileNotFoundException) {
            throw ImagePreparationException(SOURCE_UNAVAILABLE, error)
        } catch (error: SecurityException) {
            throw ImagePreparationException(SOURCE_UNAVAILABLE, error)
        }

    private suspend fun copyImageSource(
        input: InputStream,
        source: File,
    ) {
        source.outputStream().use { out ->
            val bytes = ByteArray(SOURCE_BUFFER_BYTES)
            var total = 0L
            while (true) {
                coroutineContext.ensureActive()
                val count = input.read(bytes)
                if (count < 0) break
                total += count
                if (total > MAX_SOURCE_BYTES) {
                    throw ImagePreparationException("This picture is larger than 50 MiB.")
                }
                out.write(bytes, 0, count)
            }
        }
    }

    private fun decode(source: File): Bitmap {
        val drawable =
            ImageDecoder.decodeDrawable(ImageDecoder.createSource(source)) { decoder, info, _ ->
                val width = info.size.width
                val height = info.size.height
                validateDimensions(width, height)
                val factor = min(1.0, min(IMAGE_WIDTH / width, IMAGE_HEIGHT / height))
                decoder.setTargetSize(maxOf(1, (width * factor).toInt()), maxOf(1, (height * factor).toInt()))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
                decoder.setOnPartialImageListener { false }
            }
        if (drawable is AnimatedImageDrawable) {
            throw ImagePreparationException("Animated pictures are not supported. Choose a still image.")
        }
        return (drawable as? BitmapDrawable)?.bitmap
            ?: throw ImagePreparationException("This image format is not supported.")
    }

    private fun validateDimensions(
        width: Int,
        height: Int,
    ) {
        if (width < 1 || height < 1 || width.toLong() * height > MAX_SOURCE_PIXELS) {
            throw ImagePreparationException("This picture is larger than 100 megapixels.")
        }
    }

    private fun encode(
        bitmap: Bitmap,
        file: File,
        limit: Long,
    ) {
        for (quality in JPEG_INITIAL_QUALITY downTo JPEG_MIN_QUALITY step JPEG_QUALITY_STEP) {
            file.outputStream().use {
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it)) {
                    throw ImagePreparationException("This picture could not be prepared.")
                }
            }
            if (file.length() <= limit) return
        }
        throw ImagePreparationException("This picture cannot fit within the TV image limit.")
    }

    companion object {
        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val bytes = ByteArray(SOURCE_BUFFER_BYTES)
                while (true) {
                    val count = input.read(bytes)
                    if (count < 0) break
                    digest.update(bytes, 0, count)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
