package com.emmah.colordock.remote

import android.app.Application
import com.emmah.colordock.remote.images.deleteCacheFile

class ColorDockApplication : Application() {
    val container by lazy {
        AppContainer(this)
    }

    override fun onCreate() {
        super.onCreate()
        java.io
            .File(cacheDir, "uploads")
            .listFiles()
            ?.filter {
                it.isFile
            }?.forEach {
                it.deleteCacheFile()
            }
    }
}
