package com.emmah.colordock.remote.ui
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.emmah.colordock.remote.R
import com.emmah.colordock.remote.companion.DockConnectionState
import com.emmah.colordock.remote.companion.DockMethod
import com.emmah.colordock.remote.companion.DockPatch
import com.emmah.colordock.remote.companion.DockSnapshot
import com.emmah.colordock.remote.companion.DockState
import com.emmah.colordock.remote.companion.WallpaperMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// Explicit state and callbacks preserve the hoisted Compose API.
@Suppress("LongParameterList")
@Composable
internal fun WallpaperDetail(
    snapshot: DockSnapshot,
    ui: WallpaperUiState,
    detail: WallpaperMetadata,
    thumbnails: Map<String, File>,
    deleteConfirmation: Boolean,
    onDeleteConfirmation: (Boolean) -> Unit,
    onThumbnail: (WallpaperMetadata) -> Unit,
    onDetail: (WallpaperMetadata?) -> Unit,
    onPatch: (DockPatch) -> Unit,
    onImageAction: (DockMethod, WallpaperMetadata, String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnThumbnail by rememberUpdatedState(onThumbnail)
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TextButton({
            onDetail(null)
        }) {
            DockIcon(R.drawable.ic_undo)
            Spacer(Modifier.width(8.dp))
            Text("Wallpapers")
        }
        Text(detail.title, style = MaterialTheme.typography.headlineSmall)
        LaunchedEffect(detail.id, detail.thumbnailHash) {
            currentOnThumbnail(detail)
        }
        Picture(
            thumbnails[detail.id].asImageFile(),
            detail.title,
            fit = detail.fit,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(
                        WALLPAPER_PREVIEW_ASPECT_RATIO,
                    ).clip(RoundedCornerShape(14.dp)),
        )
        WallpaperDetailActions(snapshot, ui, detail, onPatch, onImageAction, onDeleteConfirmation)
        ui.error?.let {
            Text(
                it,
                Modifier.semantics {
                    liveRegion = LiveRegionMode.Polite
                },
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
    if (deleteConfirmation) {
        WallpaperDeleteDialog(detail, onDeleteConfirmation, onImageAction)
    }
}

// Explicit state and callbacks preserve the hoisted Compose API.
@Suppress("LongParameterList")
@Composable
private fun ColumnScope.WallpaperDetailActions(
    snapshot: DockSnapshot,
    ui: WallpaperUiState,
    detail: WallpaperMetadata,
    onPatch: (DockPatch) -> Unit,
    onImageAction: (DockMethod, WallpaperMetadata, String?) -> Unit,
    onDeleteConfirmation: (Boolean) -> Unit,
) {
    Button({
        onImageAction(DockMethod.Use, detail, null)
    }, Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = !ui.saving) {
        Text("Use wallpaper")
    }
    OutlinedButton({
        val pool = snapshot.wallpaper.pool
        onPatch(
            DockPatch.Wallpaper(
                pool =
                    if (detail.id in
                        pool
                    ) {
                        pool - detail.id
                    } else {
                        pool + detail.id
                    },
            ),
        )
    }, Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = !ui.saving) {
        Text(
            if (detail.id in
                snapshot.wallpaper.pool
            ) {
                "Remove from shuffle"
            } else {
                "Add to shuffle"
            },
        )
    }
    if (detail.personal) {
        ChoiceRow(
            "Picture fit",
            listOf("cover" to "Fill screen", "contain" to "Fit whole picture"),
            detail.fit,
            !ui.saving,
        ) {
            onImageAction(DockMethod.Fit, detail, it)
        }
        TextButton({
            onDeleteConfirmation(true)
        }, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !ui.saving) {
            DockIcon(R.drawable.ic_delete, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(8.dp))
            Text("Delete from TV", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun WallpaperDeleteDialog(
    detail: WallpaperMetadata,
    onDeleteConfirmation: (Boolean) -> Unit,
    onImageAction: (DockMethod, WallpaperMetadata, String?) -> Unit,
) {
    AlertDialog(onDismissRequest = {
        onDeleteConfirmation(false)
    }, title = {
        Text("Delete this picture from the TV?")
    }, text = {
        Text("The original picture on your phone will be kept.")
    }, confirmButton = {
        TextButton({
            onDeleteConfirmation(false)
            onImageAction(DockMethod.Delete, detail, null)
        }) {
            Text("Delete")
        }
    }, dismissButton = {
        TextButton({
            onDeleteConfirmation(false)
        }) {
            Text("Cancel")
        }
    })
}

private const val WALLPAPER_PREVIEW_ASPECT_RATIO = 16f / 9
