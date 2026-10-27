package com.emmah.colordock.remote.images

import android.util.Log
import java.io.File

internal fun File.deleteCacheFile() {
    if (!delete() && exists()) Log.w("RemoteCache", "A cached file could not be removed.")
}

internal fun File.touchCacheFile() {
    if (!setLastModified(
            System.currentTimeMillis(),
        )
    ) {
        Log.w("RemoteCache", "A cached file timestamp could not be updated.")
    }
}
