package com.emmah.colordock.remote

import android.util.AtomicFile
import android.util.Log
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Gives Android's replacement rename Linux semantics on a Windows test host. */
@Implements(AtomicFile::class)
@Suppress("UtilityClassWithPublicConstructor") // Robolectric instantiates shadows reflectively.
class JvmAtomicFile {
    companion object {
        @JvmStatic
        @Implementation
        fun rename(
            source: File,
            target: File,
        ) {
            if (target.isDirectory && !target.delete()) {
                Log.e("AtomicFile", "Failed to delete directory $target")
            }
            try {
                Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            } catch (error: IOException) {
                Log.e("AtomicFile", "Failed to rename $source to $target", error)
            }
        }
    }
}
