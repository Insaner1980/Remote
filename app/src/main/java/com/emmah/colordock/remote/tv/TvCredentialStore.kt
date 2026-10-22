package com.emmah.colordock.remote.tv

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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

private val Context.tvPreferences by preferencesDataStore(name = "television")

internal class TvCredentials(
    val address: String,
    val deviceId: String,
    val name: String,
    val fingerprint: String,
    val clientKey: String,
)

class TvCredentialStore(context: Context) {
    private val application = context.applicationContext
    private val file = AtomicFile(File(application.noBackupFilesDir, "tv.credentials"))
    private val lock = Mutex()
    private val alias = "remote.television.credentials"
    private val addressKey = stringPreferencesKey("address")
    private val nameKey = stringPreferencesKey("name")

    suspend fun savedAddress(): String? = application.tvPreferences.data.first()[addressKey]

    internal suspend fun load(): TvCredentials? = withContext(Dispatchers.IO) {
        lock.withLock {
            val metadata = application.tvPreferences.data.first()
            if (!file.baseFile.exists()) {
                if (metadata[addressKey] != null) throw IOException("Pair with this TV again.")
                return@withLock null
            }
            try {
                val encrypted = file.openRead().use { it.readBytes() }
                if (encrypted.size !in 29..16384) throw IOException("Invalid credentials")
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key(create = false), GCMParameterSpec(128, encrypted.copyOfRange(0, 12)))
                val clear = cipher.doFinal(encrypted.copyOfRange(12, encrypted.size))
                val data = JSONObject(String(clear, Charsets.UTF_8))
                clear.fill(0)
                TvCredentials(data.getString("address"), data.getString("deviceId"), data.getString("name"), data.getString("fingerprint"), data.getString("clientKey"))
            } catch (_: Exception) {
                throw IOException("Pair with this TV again.")
            }
        }
    }

    internal suspend fun save(credentials: TvCredentials) = withContext(Dispatchers.IO) {
        lock.withLock {
            val data = JSONObject().put("address", credentials.address).put("deviceId", credentials.deviceId)
                .put("name", credentials.name).put("fingerprint", credentials.fingerprint).put("clientKey", credentials.clientKey)
                .toString().toByteArray(Charsets.UTF_8)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key(create = true))
            val encrypted = try { cipher.doFinal(data) } finally { data.fill(0) }
            val output = file.startWrite()
            try {
                output.write(cipher.iv)
                output.write(encrypted)
                file.finishWrite(output)
            } catch (error: Exception) {
                file.failWrite(output)
                throw error
            }
            application.tvPreferences.edit { preferences ->
                preferences[addressKey] = credentials.address
                preferences[nameKey] = credentials.name
            }
        }
    }

    suspend fun forget() = withContext(Dispatchers.IO) {
        lock.withLock {
            file.delete()
            application.tvPreferences.edit { it.clear() }
            KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
            File(application.cacheDir, "tv-thumbnails").deleteRecursively()
            File(application.cacheDir, "thumbnails").deleteRecursively()
        }
    }

    private fun key(create: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        if (!create) throw IOException("Credential encryption key is unavailable")
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
}
