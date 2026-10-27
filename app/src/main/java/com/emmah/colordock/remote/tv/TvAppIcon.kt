package com.emmah.colordock.remote.tv

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal const val MAX_APP_ICON_BYTES = 1024 * 1024
private const val ICON_TIMEOUT_MILLIS = 5_000L
private const val COPY_BUFFER_BYTES = 8192

internal suspend fun loadAppIcon(call: Call): ByteArray =
    withTimeoutOrNull(ICON_TIMEOUT_MILLIS) {
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(
                        call: Call,
                        e: IOException,
                    ) {
                        continuation.resumeWithException(e)
                    }

                    // Async callback failures, including custom response-body failures, must resume the caller.
                    @Suppress("TooGenericExceptionCaught")
                    override fun onResponse(
                        call: Call,
                        response: Response,
                    ) {
                        try {
                            continuation.resume(readAppIcon(response) { continuation.isActive })
                        } catch (error: Exception) {
                            continuation.resumeWithException(error)
                        }
                    }
                },
            )
        }
    } ?: throw IOException("Icon request timed out")

private fun readAppIcon(
    response: Response,
    isActive: () -> Boolean,
): ByteArray =
    response.use {
        if (!it.isSuccessful) throw IOException("Icon unavailable")
        val body = it.body
        if (body.contentLength() > MAX_APP_ICON_BYTES) throw IOException("Icon too large")
        body.byteStream().use { input -> readIconBytes(input, isActive) }
    }

private fun readIconBytes(
    input: InputStream,
    isActive: () -> Boolean,
): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(COPY_BUFFER_BYTES)
    while (true) {
        if (!isActive()) throw IOException("Icon request cancelled")
        val count = input.read(buffer)
        if (count < 0) break
        if (output.size() + count > MAX_APP_ICON_BYTES) throw IOException("Icon too large")
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
