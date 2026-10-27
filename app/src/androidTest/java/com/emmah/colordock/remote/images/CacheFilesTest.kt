package com.emmah.colordock.remote.images

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CacheFilesTest {
    @Test fun deletesExistingFilesAndToleratesAlreadyRemovedFiles() {
        val directory = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        val file = File.createTempFile("cleanup-", ".tmp", directory)
        file.deleteCacheFile()
        assertFalse(file.exists())
        file.deleteCacheFile()
    }

    @Test fun failedCleanupLeavesTheFileAvailableForRetry() {
        val directory = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        val file = File.createTempFile("cleanup-", ".tmp", directory)
        try {
            val failure =
                object : File(file.path) {
                    override fun delete() = false

                    override fun setLastModified(time: Long) = false
                }
            failure.deleteCacheFile()
            failure.touchCacheFile()
            assertTrue(file.exists())
        } finally {
            file.deleteCacheFile()
        }
    }

    @Test fun cacheHitsRefreshTheEvictionTimestamp() {
        val directory = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        val file = File.createTempFile("cleanup-", ".tmp", directory)
        try {
            assertTrue(file.setLastModified(1_000))
            file.touchCacheFile()
            assertTrue(file.lastModified() > 1_000)
        } finally {
            file.deleteCacheFile()
        }
    }
}
