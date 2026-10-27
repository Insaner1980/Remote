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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal fun decodePicture(file: File): Bitmap? =
    try {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
            val width = info.size.width
            val height = info.size.height
            require(width > 0 && height > 0 && width.toLong() * height <= MAX_DECODE_PIXELS)
            val scale = minOf(1.0, PREVIEW_MAX_WIDTH / width, PREVIEW_MAX_HEIGHT / height)
            decoder.setTargetSize(maxOf(1, (width * scale).toInt()), maxOf(1, (height * scale).toInt()))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setOnPartialImageListener {
                false
            }
        }
    } catch (_: Exception) {
        null
    } catch (_: OutOfMemoryError) {
        null
    }

@Composable
internal fun Picture(
    file: ImageFile?,
    label: String,
    modifier: Modifier = Modifier,
    fit: String = "cover",
) {
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, file) {
        value = null
        value =
            loadPicture(file)
    }
    Surface(modifier, color = MaterialTheme.colorScheme.background) {
        if (bitmap !=
            null
        ) {
            Image(
                requireNotNull(bitmap),
                label,
                Modifier.fillMaxSize().testTag("wallpaper-image"),
                contentScale =
                    if (fit ==
                        "contain"
                    ) {
                        ContentScale.Fit
                    } else {
                        ContentScale.Crop
                    },
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                DockIcon(R.drawable.ic_image, label = label, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun DockUnavailable(
    state: DockState,
    onOpen: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            if (state.connection ==
                DockConnectionState.Connecting
            ) {
                "Connecting to Color Dock\u2026"
            } else {
                "Open Color Dock to manage wallpapers"
            },
            style = MaterialTheme.typography.titleMedium,
        )
        state.message?.let {
            Text(it)
        }
        Button(onOpen, Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text("Open Color Dock")
        }
        TextButton(onRetry, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            DockIcon(R.drawable.ic_refresh)
            Spacer(Modifier.width(8.dp))
            Text("Try again")
        }
    }
}

// Explicit state and callbacks preserve the hoisted Compose API.
@Suppress("LongParameterList")
@Composable
fun WallpaperScreen(
    state: DockState,
    ui: WallpaperUiState,
    thumbnails: Map<String, File>,
    onOpen: () -> Unit,
    onJoin: () -> Unit,
    onSource: (String) -> Unit,
    onMore: () -> Unit,
    onThumbnail: (WallpaperMetadata) -> Unit,
    onDetail: (WallpaperMetadata?) -> Unit,
    onAdd: () -> Unit,
    onPatch: (DockPatch) -> Unit,
    onImageAction: (DockMethod, WallpaperMetadata, String?) -> Unit,
    onNext: () -> Unit,
    onPreparedFit: (String, String) -> Unit,
    onSend: () -> Unit,
    onCancelUpload: () -> Unit,
    onAbandon: () -> Unit,
    modifier: Modifier = Modifier,
    onSettings: () -> Unit = {},
) {
    val snapshot = state.snapshot
    var deleteConfirmation by remember(ui.detail?.id) { mutableStateOf(false) }
    var shuffleOptions by remember { mutableStateOf(false) }
    var category by remember(ui.source) { mutableStateOf("all") }
    val detail = ui.detail
    BackHandler(detail != null || ui.prepared.isNotEmpty() || ui.preparing || ui.upload != null) {
        if (detail != null) onDetail(null) else onAbandon()
    }
    if (detail != null && snapshot != null) {
        WallpaperDetail(
            snapshot,
            ui,
            detail,
            thumbnails,
            deleteConfirmation,
            { deleteConfirmation = it },
            onThumbnail,
            onDetail,
            onPatch,
            onImageAction,
            modifier,
        )
        return
    }
    if (ui.prepared.isNotEmpty() || ui.preparing || ui.upload != null) {
        WallpaperUploadPreview(ui, onPreparedFit, onSend, onCancelUpload, onAbandon, modifier)
        return
    }
    WallpaperGallery(
        state,
        ui,
        thumbnails,
        category,
        { category = it },
        shuffleOptions,
        { shuffleOptions = it },
        onOpen,
        onJoin,
        onSource,
        onMore,
        onThumbnail,
        onDetail,
        onAdd,
        onPatch,
        onNext,
        onSettings,
        modifier,
    )
}

@Composable
internal fun ChoiceRow(
    label: String,
    choices: List<Pair<String, String>>,
    selected: String,
    enabled: Boolean = true,
    onSelect: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Column {
        OutlinedButton(
            {
                open = true
            },
            Modifier.fillMaxWidth().heightIn(
                min = 54.dp,
            ),
            enabled = enabled,
            shape =
                RoundedCornerShape(
                    12.dp,
                ),
            border =
                BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant,
                ),
            colors =
                ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
        ) {
            val value =
                choices
                    .firstOrNull {
                        it.first == selected
                    }?.second ?: selected
            ChoiceValue(label, value, Modifier.weight(1f))
            DockIcon(R.drawable.ic_keyboard_arrow_right)
        }
        DropdownMenu(open, {
            open = false
        }) {
            choices.forEach { (value, text) ->
                DropdownMenuItem(text = {
                    Text(text)
                }, onClick = {
                    open = false
                    onSelect(value)
                })
            }
        }
    }
}

private const val MAX_DECODE_PIXELS = 100_000_000
private const val PREVIEW_MAX_WIDTH = 1920.0
private const val PREVIEW_MAX_HEIGHT = 1080.0
private const val STACKED_CHOICE_FONT_SCALE = 1.3f

@Composable
private fun ChoiceValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        if (maxWidth < 260.dp ||
            LocalDensity.current.fontScale > STACKED_CHOICE_FONT_SCALE
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, style = MaterialTheme.typography.bodyMedium)
                Text(
                    value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text(
                    value,
                    Modifier.padding(horizontal = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private suspend fun loadPicture(
    file: ImageFile?,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
): androidx.compose.ui.graphics.ImageBitmap? =
    withContext(ioDispatcher) {
        file?.let {
            decodePicture(File(it.path))?.asImageBitmap()
        }
    }
