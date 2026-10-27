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
import androidx.compose.foundation.lazy.grid.LazyGridScope
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
internal fun WallpaperGallery(
    state: DockState,
    ui: WallpaperUiState,
    thumbnails: Map<String, File>,
    category: String,
    onCategory: (String) -> Unit,
    shuffleOptions: Boolean,
    onShuffleOptions: (Boolean) -> Unit,
    onOpen: () -> Unit,
    onJoin: () -> Unit,
    onSource: (String) -> Unit,
    onMore: () -> Unit,
    onThumbnail: (WallpaperMetadata) -> Unit,
    onDetail: (WallpaperMetadata) -> Unit,
    onAdd: () -> Unit,
    onPatch: (DockPatch) -> Unit,
    onNext: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snapshot = state.snapshot
    LazyVerticalGrid(
        columns = GridCells.Adaptive(140.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(span = {
            GridItemSpan(maxLineSpan)
        }) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Wallpapers", style = MaterialTheme.typography.headlineSmall)
                    Text("Pictures stored on your TV", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                SettingsButton(onSettings)
            }
        }
        if (state.connection != DockConnectionState.Ready || snapshot == null) {
            item(span = {
                GridItemSpan(maxLineSpan)
            }) {
                DockUnavailable(state, onOpen, onJoin)
            }
        } else {
            item(span = {
                GridItemSpan(maxLineSpan)
            }) {
                CurrentWallpaperCard(snapshot, thumbnails, onThumbnail, onDetail)
            }
            item(span = {
                GridItemSpan(maxLineSpan)
            }) {
                AddWallpaperButton(onAdd)
            }
            item(span = {
                GridItemSpan(maxLineSpan)
            }) {
                WallpaperSourceSelector(ui.source, onSource)
            }
            wallpaperCollectionFilter(ui, category, onCategory)
            wallpaperGalleryItems(ui, category) { image ->
                WallpaperGridCard(image, snapshot, thumbnails, onThumbnail, onDetail)
            }
            wallpaperGalleryFooter(snapshot, ui, onMore, onPatch, onShuffleOptions)
        }
    }
    if (shuffleOptions &&
        snapshot != null
    ) {
        WallpaperShuffleDialog(snapshot, ui.saving, onShuffleOptions, onPatch, onNext)
    }
}

private fun LazyGridScope.wallpaperGalleryItems(
    ui: WallpaperUiState,
    category: String,
    content: @Composable (WallpaperMetadata) -> Unit,
) {
    if (ui.items.isEmpty() && !ui.loading) {
        item(span = {
            GridItemSpan(maxLineSpan)
        }) {
            Text(
                if (ui.source == "personal") {
                    "Add pictures from your phone to use them as TV wallpapers."
                } else {
                    "No built-in pictures are available. Try again."
                },
                Modifier.padding(vertical = 24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    items(
        ui.items.filter {
            ui.source != "builtin" || category == "all" || it.category == category
        },
        key = {
            it.id
        },
    ) { image ->
        content(image)
    }
}

private fun LazyGridScope.wallpaperGalleryFooter(
    snapshot: DockSnapshot,
    ui: WallpaperUiState,
    onMore: () -> Unit,
    onPatch: (DockPatch) -> Unit,
    onShuffleOptions: (Boolean) -> Unit,
) {
    if (ui.loading) {
        item(span = {
            GridItemSpan(maxLineSpan)
        }) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
    if (ui.cursor !=
        null
    ) {
        item(span = {
            GridItemSpan(maxLineSpan)
        }) {
            TextButton(
                onMore,
                Modifier.fillMaxWidth().heightIn(min = 48.dp),
                enabled = !ui.loading,
            ) {
                Text("Load more pictures")
            }
        }
    }
    item(span = {
        GridItemSpan(maxLineSpan)
    }) {
        WallpaperShuffleSettings(snapshot, ui.saving, onShuffleOptions, onPatch)
    }
    ui.error?.let { message ->
        item(span = {
            GridItemSpan(maxLineSpan)
        }) {
            Text(
                message,
                Modifier.semantics {
                    liveRegion = LiveRegionMode.Polite
                },
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
    ui.outcome?.let { message ->
        item(span = {
            GridItemSpan(maxLineSpan)
        }) {
            Text(
                message,
                Modifier.semantics {
                    liveRegion =
                        LiveRegionMode.Polite
                },
            )
        }
    }
}

@Composable
private fun CurrentWallpaperCard(
    snapshot: DockSnapshot,
    thumbnails: Map<String, File>,
    onThumbnail: (WallpaperMetadata) -> Unit,
    onDetail: (WallpaperMetadata) -> Unit,
) {
    val currentOnThumbnail by rememberUpdatedState(onThumbnail)
    LaunchedEffect(snapshot.current.id, snapshot.current.thumbnailHash) {
        currentOnThumbnail(snapshot.current)
    }
    OutlinedCard(
        onClick = {
            onDetail(snapshot.current)
        },
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box {
            Picture(
                thumbnails[snapshot.current.id].asImageFile(),
                snapshot.current.title,
                fit = snapshot.current.fit,
                modifier = Modifier.fillMaxWidth().aspectRatio(CURRENT_WALLPAPER_ASPECT_RATIO),
            )
            Surface(
                Modifier.align(Alignment.BottomStart).padding(10.dp),
                RoundedCornerShape(20.dp),
                WarmBlack.copy(alpha = CURRENT_WALLPAPER_BADGE_ALPHA),
                contentColor = OrchidWhite,
            ) {
                Text(
                    "In use",
                    Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun WallpaperSourceSelector(
    source: String,
    onSource: (String) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(2.dp), space = 0.dp) {
            listOf("personal" to "My pictures", "builtin" to "Built-in").forEach { (value, label) ->
                SegmentedButton(
                    source == value,
                    {
                        onSource(value)
                    },
                    RoundedCornerShape(
                        24.dp,
                    ),
                    border =
                        BorderStroke(
                            0.dp,
                            androidx.compose.ui.graphics.Color.Transparent,
                        ),
                    colors =
                        SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            activeContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            inactiveContainerColor = MaterialTheme.colorScheme.surface,
                        ),
                    icon = {},
                ) {
                    Text(label)
                }
            }
        }
    }
}

@Composable
private fun WallpaperGridCard(
    image: WallpaperMetadata,
    snapshot: DockSnapshot,
    thumbnails: Map<String, File>,
    onThumbnail: (WallpaperMetadata) -> Unit,
    onDetail: (WallpaperMetadata) -> Unit,
) {
    val currentOnThumbnail by rememberUpdatedState(onThumbnail)
    LaunchedEffect(image.id, image.thumbnailHash) {
        currentOnThumbnail(image)
    }
    val selected = image.id in snapshot.wallpaper.pool
    OutlinedCard(
        onClick = {
            onDetail(image)
        },
        shape = RoundedCornerShape(12.dp),
        border =
            BorderStroke(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
            ),
        modifier =
            Modifier.semantics {
                stateDescription =
                    if (selected) "Selected for shuffle" else "Not selected for shuffle"
            },
    ) {
        Box {
            Picture(
                thumbnails[image.id].asImageFile(),
                image.title,
                fit = image.fit,
                modifier = Modifier.fillMaxWidth().aspectRatio(GALLERY_WALLPAPER_ASPECT_RATIO),
            )
            if (selected) {
                Surface(
                    Modifier.align(Alignment.TopEnd).padding(6.dp).size(26.dp),
                    RoundedCornerShape(CIRCULAR_CORNER_PERCENT),
                    MaterialTheme.colorScheme.primaryContainer,
                ) {
                    DockIcon(R.drawable.ic_check, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
    }
}

@Composable
private fun WallpaperShuffleSettings(
    snapshot: DockSnapshot,
    saving: Boolean,
    onShuffleOptions: (Boolean) -> Unit,
    onPatch: (DockPatch) -> Unit,
) {
    OutlinedCard(modifier = Modifier.testTag("shuffle-settings"), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(horizontal = 14.dp)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 40.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${snapshot.wallpaper.pool.size} pictures selected", Modifier.weight(1f))
                IconButton({
                    onShuffleOptions(true)
                }) {
                    DockIcon(R.drawable.ic_keyboard_arrow_right, label = "Shuffle options")
                }
            }
            HorizontalDivider()
            Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                DockIcon(R.drawable.ic_shuffle)
                Text("Shuffle selected", Modifier.weight(1f).padding(horizontal = 10.dp))
                Switch(
                    snapshot.wallpaper.mode == "shuffle",
                    {
                        onPatch(DockPatch.Wallpaper(mode = if (it) "shuffle" else "fixed"))
                    },
                    modifier =
                        Modifier.semantics {
                            contentDescription =
                                "Shuffle selected"
                        },
                    enabled = !saving,
                )
            }
        }
    }
}

@Composable
private fun WallpaperShuffleDialog(
    snapshot: DockSnapshot,
    saving: Boolean,
    onShuffleOptions: (Boolean) -> Unit,
    onPatch: (DockPatch) -> Unit,
    onNext: () -> Unit,
) {
    AlertDialog(onDismissRequest = {
        onShuffleOptions(false)
    }, title = {
        Text("Shuffle options")
    }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ChoiceRow(
                "Change wallpaper",
                listOf("open" to "Whenever Color Dock opens", "start" to "On a fresh app start"),
                snapshot.wallpaper.trigger,
                !saving,
            ) {
                onPatch(DockPatch.Wallpaper(trigger = it))
            }
            OutlinedButton(onNext, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !saving) {
                Text("Next wallpaper")
            }
        }
    }, confirmButton = {
        TextButton({
            onShuffleOptions(false)
        }) {
            Text("Done")
        }
    })
}

private const val CURRENT_WALLPAPER_ASPECT_RATIO = 2.8f
private const val CURRENT_WALLPAPER_BADGE_ALPHA = .85f
private const val GALLERY_WALLPAPER_ASPECT_RATIO = 2.25f
private const val CIRCULAR_CORNER_PERCENT = 50

private fun LazyGridScope.wallpaperCollectionFilter(
    ui: WallpaperUiState,
    category: String,
    onCategory: (String) -> Unit,
) {
    if (ui.source == "builtin") {
        item(span = {
            GridItemSpan(maxLineSpan)
        }) {
            ChoiceRow(
                "Collection",
                listOf("all" to "All collections") +
                    ui.items
                        .mapNotNull { image ->
                            image.category?.let {
                                it to (image.categoryLabel ?: it)
                            }
                        }.distinct(),
                category,
                !ui.loading,
            ) {
                onCategory(it)
            }
        }
    }
}

@Composable
private fun AddWallpaperButton(onAdd: () -> Unit) {
    Button(
        onAdd,
        Modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = RoundedCornerShape(12.dp),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
    ) {
        DockIcon(R.drawable.ic_add)
        Spacer(Modifier.width(10.dp))
        Text("Add from phone")
    }
}
