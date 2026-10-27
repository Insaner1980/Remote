package com.emmah.colordock.remote.tv

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.emmah.colordock.remote.companion.PendingUploadStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class TvCredentialStoreTest {
    @Test fun corruptMetadataRequiresExplicitForget() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val metadata = File(context.filesDir, "datastore/television.preferences_pb")
            assumeTrue(
                "Corruption fixture requires an explicitly selected isolated package",
                context.packageName != "com.emmah.colordock.remote" &&
                    InstrumentationRegistry.getArguments().getString("isolatedStorePackage") == context.packageName,
            )
            assumeTrue(
                "Preserve existing metadata and pairing",
                !metadata.exists() && !File(context.noBackupFilesDir, "tv.credentials").exists(),
            )
            metadata.parentFile!!.mkdirs()
            val corrupt = byteArrayOf(-1, -1, -1)
            metadata.writeBytes(corrupt)
            val store = TvCredentialStore(context)
            try {
                assertTrue(
                    runCatching {
                        store.savedAddress()
                    }.exceptionOrNull() is IOException,
                )
                assertTrue(
                    runCatching {
                        store.load()
                    }.exceptionOrNull() is IOException,
                )
                assertArrayEquals(corrupt, metadata.readBytes())
                store.forget()
                assertNull(store.savedAddress())
                assertNull(store.load())
                store.save(TvCredentials("192.168.1.10", "fixture-b", "TV B", "cd".repeat(32), "fixture-key-b"))
                assertEquals("fixture-b", store.load()?.deviceId)
            } finally {
                store.forget()
            }
        }

    @Test fun keyIsEncryptedAndForgetRemovesThePairing() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val store = TvCredentialStore(context)
            assumeTrue("Preserve an existing real TV pairing", store.load() == null)
            val pending = PendingUploadStore(context)
            try {
                val uploadId = UUID.randomUUID().toString()
                pending.save(uploadId, "TV A fixture")
                store.save(
                    TvCredentials(
                        "192.168.1.9",
                        "fixture-device",
                        "Fixture TV",
                        "ab".repeat(32),
                        "fixture-secret-never-a-real-key",
                    ),
                )
                val disk = File(context.noBackupFilesDir, "tv.credentials").readBytes().toString(Charsets.ISO_8859_1)
                assertFalse(disk.contains("fixture-secret"))
                assertFalse(disk.contains("fixture-device"))
                val loaded = requireNotNull(store.load())
                assertEquals("fixture-secret-never-a-real-key", loaded.clientKey)
                assertEquals("ab".repeat(32), loaded.fingerprint)
                assertEquals("192.168.1.9", store.savedAddress())
                assertEquals(uploadId, pending.read()?.first)
                store.forget()
                assertNull(store.load())
                assertNull(store.savedAddress())
                assertNull(pending.read())
                store.save(
                    TvCredentials("192.168.1.10", "fixture-device-b", "Fixture B", "cd".repeat(32), "fixture-key-b"),
                )
                assertNull(pending.read())
            } finally {
                store.forget()
            }
        }
}
