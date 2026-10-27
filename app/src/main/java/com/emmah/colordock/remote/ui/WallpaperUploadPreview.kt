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
internal fun WallpaperUploadPreview(
    ui: WallpaperUiState,
    onPreparedFit: (String, String) -> Unit,
    onSend: () -> Unit,
    onCancelUpload: () -> Unit,
    onAbandon: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Preview pictures", style = MaterialTheme.typography.headlineSmall)
        Text("Fill screen may crop the picture. Fit whole picture keeps the complete image.")
        if (ui.preparing) LinearProgressIndicator(Modifier.fillMaxWidth())
        for (image in ui.prepared) {
            Text(image.name, style = MaterialTheme.typography.titleMedium)
            Picture(
                image.image.asImageFile(),
                image.name,
                fit = image.fit,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(
                            WALLPAPER_PREVIEW_ASPECT_RATIO,
                        ).clip(RoundedCornerShape(14.dp)),
            )
            ChoiceRow(
                "Picture fit",
                listOf("cover" to "Fill screen", "contain" to "Fit whole picture"),
                image.fit,
                ui.upload == null,
            ) {
                onPreparedFit(image.uploadId, it)
            }
        }
        WallpaperUploadStatus(ui)
        if (ui.upload ==
            null
        ) {
            Button(onSend, Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = !ui.preparing) {
                Text(
                    if (ui.outcome?.contains("Failed:") ==
                        true
                    ) {
                        "Retry failed"
                    } else {
                        "Send to TV"
                    },
                )
            }
        } else {
            OutlinedButton(onCancelUpload, Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text("Cancel upload")
            }
        }
        TextButton(onAbandon, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("Cancel")
        }
    }
}

@Composable
private fun ColumnScope.WallpaperUploadStatus(ui: WallpaperUiState) {
    ui.upload?.let { progress ->
        Text(
            progress.stage,
            Modifier.semantics {
                liveRegion = LiveRegionMode.Polite
            },
        )
        LinearProgressIndicator(progress = {
            progress.fraction
        }, modifier = Modifier.fillMaxWidth())
        progress.message?.let {
            Text(
                it,
                Modifier.semantics {
                    liveRegion =
                        LiveRegionMode.Polite
                },
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
    ui.outcome?.let {
        Text(
            it,
            Modifier.semantics {
                liveRegion = LiveRegionMode.Polite
            },
        )
    }
    ui.error?.let {
        Text(
            it,
            Modifier.semantics {
                liveRegion =
                    LiveRegionMode.Polite
            },
            color = MaterialTheme.colorScheme.error,
        )
    }
}

private const val WALLPAPER_PREVIEW_ASPECT_RATIO = 16f / 9
