package com.emmah.colordock.remote.tv

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.emmah.colordock.remote.companion.PendingUploadStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val MAX_CREDENTIAL_BYTES = 16384
private const val GCM_IV_BYTES = 12
private const val GCM_TAG_BITS = 128
private const val MIN_CREDENTIAL_BYTES = 29
internal const val MAX_DEVICE_ID_LENGTH = 200
internal const val MAX_DEVICE_NAME_LENGTH = 120
internal const val MAX_CLIENT_KEY_LENGTH = 4096

@Volatile private var replaceCorruptTvMetadata = false
private val Context.tvPreferences by preferencesDataStore(
    name = "television",
    corruptionHandler =
        ReplaceFileCorruptionHandler { error ->
            if (replaceCorruptTvMetadata) emptyPreferences() else throw error
        },
)

internal class TvCredentials(
    val address: String,
    val deviceId: String,
    val name: String,
    val fingerprint: String,
    val clientKey: String,
)

class TvCredentialStore(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val application = context.applicationContext
    private val file = AtomicFile(File(application.noBackupFilesDir, "tv.credentials"))
    private val alias = "remote.television.credentials"
    private val addressKey = stringPreferencesKey("address")
    private val nameKey = stringPreferencesKey("name")

    suspend fun savedAddress(): String? = application.tvPreferences.data.first()[addressKey]

    internal suspend fun load(): TvCredentials? =
        withContext(ioDispatcher) {
            lock.withLock {
                val metadata = application.tvPreferences.data.first()
                if (!file.baseFile.exists()) {
                    if (metadata[addressKey] != null) throw IOException("Pair with this TV again.")
                    return@withLock null
                }
                try {
                    val encrypted =
                        file.openRead().use {
                            it.readNBytes(MAX_CREDENTIAL_BYTES + 1)
                        }
                    if (encrypted.size !in
                        MIN_CREDENTIAL_BYTES..MAX_CREDENTIAL_BYTES
                    ) {
                        throw IOException("Invalid credentials")
                    }
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(
                        Cipher.DECRYPT_MODE,
                        key(create = false),
                        GCMParameterSpec(GCM_TAG_BITS, encrypted.copyOfRange(0, GCM_IV_BYTES)),
                    )
                    val clear = cipher.doFinal(encrypted.copyOfRange(GCM_IV_BYTES, encrypted.size))
                    try {
                        decode(clear)
                    } finally {
                        clear.fill(0)
                    }
                } catch (_: Exception) {
                    throw IOException("Pair with this TV again.")
                }
            }
        }

    internal suspend fun save(credentials: TvCredentials) =
        withContext(ioDispatcher) {
            lock.withLock {
                val data =
                    JSONObject()
                        .put("address", credentials.address)
                        .put("deviceId", credentials.deviceId)
                        .put("name", credentials.name)
                        .put("fingerprint", credentials.fingerprint)
                        .put("clientKey", credentials.clientKey)
                        .toString()
                        .toByteArray(Charsets.UTF_8)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, key(create = true))
                val encrypted =
                    try {
                        cipher.doFinal(data)
                    } finally {
                        data.fill(0)
                    }
                require(cipher.iv.size == GCM_IV_BYTES && encrypted.size + GCM_IV_BYTES <= MAX_CREDENTIAL_BYTES) {
                    "Invalid credentials"
                }
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
                application.tvPreferences.edit { preferences ->
                    preferences[addressKey] = credentials.address
                    preferences[nameKey] = credentials.name
                }
            }
        }

    suspend fun forget() =
        withContext(ioDispatcher) {
            lock.withLock {
                PendingUploadStore(application).clear()
                file.delete()
                if (file.baseFile.exists()) throw IOException("The saved TV could not be removed.")
                replaceCorruptTvMetadata = true
                try {
                    application.tvPreferences.edit {
                        it.clear()
                    }
                } finally {
                    replaceCorruptTvMetadata = false
                }
                KeyStore.getInstance("AndroidKeyStore").apply {
                    load(null)
                    deleteEntry(alias)
                }
                File(application.cacheDir, "tv-thumbnails").deleteRecursively()
                File(application.cacheDir, "thumbnails").deleteRecursively()
            }
        }

    private fun key(create: Boolean): SecretKey {
        val store =
            KeyStore.getInstance("AndroidKeyStore").apply {
                load(null)
            }
        (store.getKey(alias, null) as? SecretKey)?.let {
            return it
        }
        if (!create) throw IOException("Credential encryption key is unavailable")
        return KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply {
                init(
                    KeyGenParameterSpec
                        .Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build(),
                )
            }.generateKey()
    }

    internal companion object {
        private val lock = Mutex()

        fun decode(clear: ByteArray): TvCredentials {
            val data = JSONObject(String(clear, Charsets.UTF_8))

            fun string(name: String) = data.opt(name) as? String ?: throw IOException("Invalid credentials")
            val address = string("address")
            val device = string("deviceId")
            val name = string("name")
            val fingerprint = string("fingerprint")
            val clientKey = string("clientKey")
            require(
                TvTarget.parse(address) != null && device.isNotBlank() && device.length <= MAX_DEVICE_ID_LENGTH &&
                    name.length <= MAX_DEVICE_NAME_LENGTH &&
                    fingerprint.matches(Regex("[0-9a-f]{64}")) && clientKey.isNotBlank() &&
                    clientKey.length <= MAX_CLIENT_KEY_LENGTH,
            ) {
                "Invalid credentials"
            }
            return TvCredentials(address, device, name, fingerprint, clientKey)
        }
    }
}
