package com.emmah.colordock.remote.tv

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class TvCredentialStoreTest {
    @Test fun keyIsEncryptedAndForgetRemovesThePairing() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = TvCredentialStore(context)
        assumeTrue("Preserve an existing real TV pairing", store.load() == null)
        try {
            store.save(TvCredentials("192.168.1.9", "fixture-device", "Fixture TV", "ab".repeat(32), "fixture-secret-never-a-real-key"))
            val disk = File(context.noBackupFilesDir, "tv.credentials").readBytes().toString(Charsets.ISO_8859_1)
            assertFalse(disk.contains("fixture-secret"))
            assertFalse(disk.contains("fixture-device"))
            val loaded = requireNotNull(store.load())
            assertEquals("fixture-secret-never-a-real-key", loaded.clientKey)
            assertEquals("ab".repeat(32), loaded.fingerprint)
            assertEquals("192.168.1.9", store.savedAddress())
            store.forget()
            assertNull(store.load())
            assertNull(store.savedAddress())
        } finally { store.forget() }
    }
}
