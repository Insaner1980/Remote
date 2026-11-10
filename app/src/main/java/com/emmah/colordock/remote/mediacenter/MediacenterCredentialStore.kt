package com.emmah.colordock.remote.mediacenter

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** MC has its own encrypted record and key. Nothing here opens or migrates TV storage. */
class MediacenterCredentialStore(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : MediacenterCredentials {
    private val application = context.applicationContext
    private val file = AtomicFile(File(application.noBackupFilesDir, "mediacenter.credentials"))

    override suspend fun load(): StoredMediacenter? =
        withContext(ioDispatcher) {
            lock.withLock {
                if (!file.baseFile.exists()) return@withLock null
                try {
                    val encrypted = file.openRead().use { it.readNBytes(MAX_BYTES + 1) }
                    require(encrypted.size in (IV_BYTES + TAG_BYTES)..MAX_BYTES)
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(
                        Cipher.DECRYPT_MODE,
                        key(false),
                        GCMParameterSpec(TAG_BITS, encrypted.copyOfRange(0, IV_BYTES)),
                    )
                    val clear = cipher.doFinal(encrypted.copyOfRange(IV_BYTES, encrypted.size))
                    try {
                        MediacenterCredentialCodec.decode(clear)
                    } finally {
                        clear.fill(0)
                    }
                } catch (_: Exception) {
                    throw IOException("Pair with Mediacenter again.")
                }
            }
        }

    override suspend fun save(value: StoredMediacenter) =
        withContext(ioDispatcher) {
            lock.withLock {
                val clear = MediacenterCredentialCodec.encode(value)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, key(true))
                val encrypted =
                    try {
                        cipher.doFinal(clear)
                    } finally {
                        clear.fill(0)
                    }
                require(cipher.iv.size == IV_BYTES && encrypted.size + IV_BYTES <= MAX_BYTES)
                val output = file.startWrite()
                var written = false
                try {
                    output.write(cipher.iv)
                    output.write(encrypted)
                    file.finishWrite(output)
                    written = true
                } finally {
                    if (!written) file.failWrite(output)
                }
            }
        }

    override suspend fun forget() =
        withContext(ioDispatcher) {
            lock.withLock {
                file.delete()
                if (file.baseFile.exists()) throw IOException("The Mediacenter pairing could not be removed.")
                KeyStore.getInstance("AndroidKeyStore").apply {
                    load(null)
                    deleteEntry(ALIAS)
                }
                File(application.cacheDir, "mediacenter-thumbnails").deleteRecursively()
                Unit
            }
        }

    private fun key(create: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        if (!create) throw IOException("Mediacenter encryption key unavailable")
        return KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply {
                init(
                    KeyGenParameterSpec
                        .Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build(),
                )
            }.generateKey()
    }

    private companion object {
        val lock = Mutex()
        const val ALIAS = "remote.mediacenter.credentials"
        const val MAX_BYTES = 65536
        const val IV_BYTES = 12
        const val TAG_BYTES = 16
        const val TAG_BITS = 128
    }
}
