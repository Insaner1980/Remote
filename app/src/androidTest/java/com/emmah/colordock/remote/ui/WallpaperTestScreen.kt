package com.emmah.colordock.remote.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.emmah.colordock.remote.companion.DockMethod
import com.emmah.colordock.remote.companion.DockState
import com.emmah.colordock.remote.companion.WallpaperMetadata
import java.io.File

// Mirrors the production screen callbacks while tests override only the actions under test.
@Suppress("LongParameterList")
@Composable
internal fun WallpaperTestScreen(
    state: DockState,
    ui: WallpaperUiState,
    modifier: Modifier = Modifier,
    thumbnails: Map<String, File> = emptyMap(),
    onThumbnail: (WallpaperMetadata) -> Unit = {},
    onDetail: (WallpaperMetadata?) -> Unit = {},
    onImageAction: (DockMethod, WallpaperMetadata, String?) -> Unit = { _, _, _ ->
    },
    onCancelUpload: () -> Unit = {},
    onAbandon: () -> Unit = {},
) {
    WallpaperScreen(
        state = state,
        ui = ui,
        thumbnails = thumbnails,
        modifier = modifier,
        onOpen = {},
        onJoin = {},
        onSource = {},
        onMore = {},
        onThumbnail = onThumbnail,
        onDetail = onDetail,
        onAdd = {},
        onPatch = {},
        onImageAction = onImageAction,
        onNext = {},
        onPreparedFit = { _, _ ->
        },
        onSend = {},
        onCancelUpload = onCancelUpload,
        onAbandon = onAbandon,
    )
}
