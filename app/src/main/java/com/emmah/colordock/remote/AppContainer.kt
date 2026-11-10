package com.emmah.colordock.remote

import android.content.Context
import com.emmah.colordock.remote.companion.CompanionClient
import com.emmah.colordock.remote.mediacenter.FilePendingCommandStore
import com.emmah.colordock.remote.mediacenter.MediacenterClient
import com.emmah.colordock.remote.mediacenter.MediacenterCredentialStore
import com.emmah.colordock.remote.mediacenter.MediacenterCredentials
import com.emmah.colordock.remote.mediacenter.MediacenterRepository
import com.emmah.colordock.remote.tv.LgTvClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

class AppContainer(
    context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val mediacenterCredentials: MediacenterCredentials = MediacenterCredentialStore(context)
    val mediacenter =
        MediacenterRepository(
            MediacenterClient(),
            scope,
            FilePendingCommandStore(File(context.noBackupFilesDir, "mediacenter.pending")),
            thumbnails =
                com.emmah.colordock.remote.mediacenter.MediacenterThumbnails(
                    File(context.cacheDir, "mediacenter-thumbnails"),
                ),
        )
    val tv = LgTvClient(context)
    val dock = CompanionClient(tv, scope)
}
