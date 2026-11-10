package com.emmah.colordock.remote.mediacenter

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.emmah.colordock.remote.mediacenter.protocol.PairedMediacenter
import com.emmah.colordock.remote.mediacenter.protocol.Scope
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

@RunWith(AndroidJUnit4::class)
class MediacenterCredentialStoreTest {
    private fun isolatedContext() =
        InstrumentationRegistry.getInstrumentation().targetContext.also {
            assumeTrue(
                "Run only in an explicitly selected isolated package",
                it.packageName != "com.emmah.colordock.remote" &&
                    InstrumentationRegistry.getArguments().getString("isolatedStorePackage") == it.packageName,
            )
        }

    private fun fixture() =
        StoredMediacenter(
            requireNotNull(MediacenterEndpoint.parse("192.168.1.9")),
            PairedMediacenter("fixture-server", "fixture-phone", "A".repeat(43), "ab".repeat(32), listOf(Scope.BROWSE)),
        )

    @Test fun encryptedRoundTripAndForgetPreserveTelevisionBytes() =
        runBlocking {
            val context = isolatedContext()
            val television = File(context.noBackupFilesDir, "tv.credentials")
            val televisionMetadata = File(context.filesDir, "datastore/television.preferences_pb")
            val store = MediacenterCredentialStore(context)
            assumeTrue("Preserve any existing MC pairing", store.load() == null)
            val owned = mutableListOf<File>()

            fun snapshot(
                file: File,
                sentinel: String,
            ): ByteArray {
                file.parentFile!!.mkdirs()
                if (file.createNewFile()) {
                    owned.add(file)
                    file.writeText(sentinel, Charsets.UTF_8)
                }
                return file.readBytes()
            }
            try {
                val before = snapshot(television, "isolated-task7-synthetic-tv-credential-sentinel")
                val metadataBefore = snapshot(televisionMetadata, "isolated-task7-synthetic-tv-metadata-sentinel")
                store.save(fixture())
                val encrypted = File(context.noBackupFilesDir, "mediacenter.credentials").readBytes()
                assertFalse(encrypted.toString(Charsets.ISO_8859_1).contains("AAAA"))
                assertFalse(encrypted.toString(Charsets.ISO_8859_1).contains("fixture-server"))
                assertEquals("A".repeat(43), MediacenterCredentialStore(context).load()!!.paired.credential)
                store.forget()
                assertNull(MediacenterCredentialStore(context).load())
                assertArrayEquals(before, television.readBytes())
                assertArrayEquals(metadataBefore, televisionMetadata.readBytes())
                File(context.noBackupFilesDir, "mediacenter.credentials").writeBytes(byteArrayOf(1, 2, 3))
                assertTrue(runCatching { store.load() }.isFailure)
            } finally {
                try {
                    store.forget()
                } finally {
                    owned.forEach { it.delete() }
                }
            }
        }

    // Invoke twice in an isolated package, with an OS force-stop between runner invocations.
    @Test fun encryptedRecordSurvivesAnActualProcessRestart() =
        runBlocking {
            val context = isolatedContext()
            val phase = InstrumentationRegistry.getArguments().getString("mediacenterRestartPhase")
            assumeTrue("Use explicit write/read restart phases", phase == "write" || phase == "read")
            val store = MediacenterCredentialStore(context)
            if (phase == "write") {
                assumeTrue("Preserve existing MC pairing", store.load() == null)
                store.save(fixture())
            } else {
                try {
                    assertEquals("fixture-server", store.load()!!.paired.serverDeviceId)
                } finally {
                    store.forget()
                }
            }
        }
}
