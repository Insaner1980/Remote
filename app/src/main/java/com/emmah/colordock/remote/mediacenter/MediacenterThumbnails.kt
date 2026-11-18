package com.emmah.colordock.remote.mediacenter

import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** A pairing-owned 100 MiB disk LRU with paced preview downloads and synchronous retirement. */
class MediacenterThumbnails(
    private val directory: File,
) {
    private val lock = Any()
    private val download = Semaphore(1)
    private var lastDownload: TimeMark? = null
    private var generation = 0L
    private var owner: String? = null
    private var transport: MediacenterTransport? = null
    private var credential: String? = null

    fun select(
        value: StoredMediacenter,
        network: MediacenterNetwork,
    ) = synchronized(lock) {
        retire()
        owner = digest(value.paired.serverDeviceId + ":" + value.paired.deviceId)
        credential = value.paired.credential
        transport = MediacenterTransport(value.endpoint, network, value.paired.certificateFingerprint)
    }

    fun retire() =
        synchronized(lock) {
            generation++
            transport?.close()
            transport = null
            credential = null
            owner = null
        }

    fun forget() =
        synchronized(lock) {
            retire()
            directory.deleteRecursively()
            Unit
        }

    suspend fun load(id: String): ByteArray? {
        val request = request(id) ?: return null
        return try {
            withContext(Dispatchers.IO) {
                val bytes = cached(request) ?: download.withPermit { cached(request) ?: fetch(id, request) }
                synchronized(lock) { bytes.takeIf { generation == request.generation } }
            }
        } catch (_: IOException) {
            null
        }
    }

    private fun request(id: String): Request? =
        synchronized(lock) {
            val current = owner
            val route = transport
            val secret = credential
            val validOwner = current != null && route != null && secret != null
            if (validOwner && id.matches(Regex("[a-fA-F0-9]{64}"))) {
                Request(generation, File(File(directory, current), id), route, secret)
            } else {
                null
            }
        }

    private fun cached(request: Request): ByteArray? =
        synchronized(lock) {
            if (generation != request.generation) {
                null
            } else {
                request.file
                    .takeIf { it.isFile && it.length() <= MAX_BYTES }
                    ?.readBytes()
                    ?.takeIf(::valid)
                    ?.also { request.file.setLastModified(System.currentTimeMillis()) }
            }
        }

    private suspend fun fetch(
        id: String,
        request: Request,
    ): ByteArray? {
        // Fast cache misses can otherwise open a burst of new TLS connections on the control link.
        lastDownload?.let { delay(PREVIEW_INTERVAL - it.elapsedNow()) }
        if (synchronized(lock) { generation != request.generation }) return null
        lastDownload = TimeSource.Monotonic.markNow()
        val bytes = request.transport.thumbnail(id, request.credential)
        return synchronized(lock) {
            if (generation != request.generation || !valid(bytes)) {
                null
            } else {
                request.file.parentFile?.mkdirs()
                request.file.writeBytes(bytes)
                trimCache()
                bytes
            }
        }
    }

    private fun trimCache() {
        var retained = 0L
        directory.walkTopDown().filter { it.isFile }.sortedByDescending { it.lastModified() }.forEach {
            retained += it.length()
            if (retained > CACHE_BYTES) it.delete()
        }
    }

    private data class Request(
        val generation: Long,
        val file: File,
        val transport: MediacenterTransport,
        val credential: String,
    )

    private fun valid(bytes: ByteArray): Boolean {
        if (bytes.size > MAX_BYTES || bytes.size < PNG_HEADER_BYTES) return false
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        return options.outMimeType == "image/png" && options.outWidth in 1..MAX_DIMENSION &&
            options.outHeight in 1..MAX_DIMENSION
    }

    private fun digest(value: String) =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") {
            "%02x".format(it)
        }

    private companion object {
        val PREVIEW_INTERVAL = 400.milliseconds
        const val PNG_HEADER_BYTES = 24
        const val MAX_DIMENSION = 512
        const val MAX_BYTES = 512 * 1024
        const val CACHE_BYTES = 100L * 1024 * 1024
    }
}
