package com.emmah.colordock.remote.ui

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class ImageFile(
    val path: String,
    val length: Long,
    val lastModified: Long,
)

internal fun File?.asImageFile(): ImageFile? =
    this?.let {
        ImageFile(it.path, it.length(), it.lastModified())
    }

internal suspend fun loadAppBitmap(
    file: ImageFile?,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
): androidx.compose.ui.graphics.ImageBitmap? =
    withContext(ioDispatcher) {
        file?.let {
            runCatching {
                BitmapFactory.decodeFile(it.path)?.asImageBitmap()
            }.getOrNull()
        }
    }
