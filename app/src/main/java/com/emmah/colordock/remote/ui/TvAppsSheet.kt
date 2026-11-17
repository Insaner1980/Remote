package com.emmah.colordock.remote.ui
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.emmah.colordock.remote.R
import com.emmah.colordock.remote.tv.TvApp
import java.io.File

// Explicit state and callbacks preserve the hoisted Compose API.
@Suppress("LongParameterList")
@Composable
private fun AppItem(
    app: TvApp,
    state: TvAppsUiState,
    images: Map<String, File>,
    onLoadIcon: (String) -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    row: Boolean = false,
    iconOnly: Boolean = false,
) {
    val currentOnLoadIcon by rememberUpdatedState(onLoadIcon)
    LaunchedEffect(app.id, app.iconUrl, state.sessionGeneration, state.catalogRevision) {
        currentOnLoadIcon(app.id)
    }
    TvAppTile(
        app,
        images[app.id].asImageFile(),
        state.connected && state.launchingId == null,
        onOpen,
        modifier,
        row,
        iconOnly,
    )
}

// Explicit state and callbacks preserve the hoisted Compose API.
@Suppress("LongParameterList")
@Composable
internal fun TvAppsStrip(
    state: TvAppsUiState,
    images: Map<String, File>,
    onLoadIcon: (String) -> Unit,
    onOpen: (String) -> Unit,
    onRefresh: () -> Unit,
    showStatus: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.tv_apps), style = MaterialTheme.typography.titleMedium)
        if (showStatus && (state.hasCatalogStatus || state.hasLaunchStatus)) {
            TvAppsStatus(state, onRefresh, onOpen)
        }
        LazyRow(
            Modifier
                .fillMaxWidth()
                .testTag("app-shortcuts")
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        Brush.horizontalGradient(APP_FADE_START to Color.Black, 1f to Color.Transparent),
                        blendMode = BlendMode.DstIn,
                    )
                },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.apps, key = {
                it.id
            }) { app ->
                AppItem(app, state, images, onLoadIcon, onOpen, Modifier.size(72.dp), iconOnly = true)
            }
        }
    }
}

@Composable
private fun TvAppsStatus(
    state: TvAppsUiState,
    onRefresh: () -> Unit,
    onOpen: (String) -> Unit,
    refreshAvailable: Boolean = false,
) {
    Column(
        Modifier.fillMaxWidth().semantics {
            liveRegion = LiveRegionMode.Polite
        },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TvAppsLoadStatus(state, onRefresh, refreshAvailable)
        TvAppsLaunchStatus(state, onRefresh, onOpen, refreshAvailable)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
// Explicit state and callbacks preserve the hoisted Compose API.
@Suppress("LongParameterList")
@Composable
internal fun TvAppsSheet(
    state: TvAppsUiState,
    images: Map<String, File>,
    onLoadIcon: (String) -> Unit,
    onOpen: (String) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    val title = stringResource(R.string.tv_apps)
    val density = LocalDensity.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        CompositionLocalProvider(LocalDensity provides density) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(
                        APP_SHEET_HEIGHT_FRACTION,
                    ).padding(horizontal = 16.dp)
                    .testTag("tv-app-sheet")
                    .semantics {
                        paneTitle = title
                    },
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TvAppsSheetHeader(title, state.connected && !state.loading, onRefresh, onDismiss)
                TvAppsPicker(state, images, onLoadIcon, onOpen, onRefresh)
            }
        }
    }
}

@Composable
private fun TvAppsSheetHeader(
    title: String,
    canRefresh: Boolean,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
        IconButton(onRefresh, Modifier.size(RemoteDimens.MinTouch), enabled = canRefresh) {
            DockIcon(R.drawable.ic_refresh, label = stringResource(R.string.tv_apps_refresh))
        }
        IconButton(onDismiss, Modifier.size(RemoteDimens.MinTouch)) {
            DockIcon(R.drawable.ic_close, label = stringResource(R.string.tv_apps_close))
        }
    }
}

@Composable
private fun ColumnScope.TvAppsLoadStatus(
    state: TvAppsUiState,
    onRefresh: () -> Unit,
    refreshAvailable: Boolean,
) {
    val loadText = tvAppsLoadMessage(state)
    loadText?.let {
        Text(stringResource(it), style = MaterialTheme.typography.bodyMedium)
    }
    if (state.loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
    val needsRefresh = state.loadError || state.hasEmptyCatalog
    val canRefresh = state.connected && !state.loading
    val showRefresh = !refreshAvailable || state.loadError
    if (canRefresh && needsRefresh && showRefresh) {
        TextButton(onRefresh, Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(if (state.loadError) R.string.tv_apps_retry else R.string.tv_apps_refresh))
        }
    }
}

@Composable
private fun ColumnScope.TvAppsLaunchStatus(
    state: TvAppsUiState,
    onRefresh: () -> Unit,
    onOpen: (String) -> Unit,
    refreshAvailable: Boolean,
) {
    state.launchingId?.let { id ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(RemoteDimens.ItemGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Text(stringResource(R.string.tv_app_opening, state.appLaunchTitle(id)))
        }
    }
    state.launchErrorId?.let { id ->
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(RemoteDimens.ItemGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DockIcon(R.drawable.ic_error, tint = MaterialTheme.colorScheme.error)
            Text(
                if (state.appUnavailable) {
                    stringResource(R.string.tv_app_removed)
                } else {
                    stringResource(R.string.tv_app_open_failed, state.appLaunchTitle(id))
                },
                Modifier.weight(1f),
            )
            if (!state.appUnavailable) {
                TextButton(
                    { onOpen(id) },
                    enabled = state.connected && state.launchingId == null,
                ) {
                    Text(stringResource(R.string.tv_apps_retry))
                }
            }
        }
        if (!refreshAvailable) {
            TextButton(
                onRefresh,
                Modifier.heightIn(min = 48.dp).align(Alignment.End),
                enabled =
                    state.connected && !state.loading,
            ) {
                Text(stringResource(R.string.tv_apps_refresh))
            }
        }
    }
}

private fun TvAppsUiState.appLaunchTitle(id: String): String =
    launchTitle ?: apps.find { app -> app.id == id }?.title.orEmpty()

private val TvAppsUiState.hasEmptyCatalog get() = hasLoaded && apps.isEmpty()
private val TvAppsUiState.hasCatalogStatus get() = !connected || loading || loadError || hasEmptyCatalog
private val TvAppsUiState.hasLaunchStatus get() = launchingId != null || launchErrorId != null

@Composable
private fun ColumnScope.TvAppsPicker(
    state: TvAppsUiState,
    images: Map<String, File>,
    onLoadIcon: (String) -> Unit,
    onOpen: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    val appContent: @Composable (TvApp, Boolean) -> Unit = { app, row ->
        AppItem(app, state, images, onLoadIcon, onOpen, Modifier.fillMaxWidth(), row = row)
    }
    if (LocalDensity.current.fontScale > APP_ROW_FONT_SCALE) {
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f).testTag("app-picker-list"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item(key = 0) {
                TvAppsStatus(state, onRefresh, onOpen, refreshAvailable = true)
            }
            items(
                state.apps,
                key = {
                    it.id
                },
            ) { app ->
                appContent(app, true)
            }
        }
    } else {
        LazyVerticalGrid(
            GridCells.Adaptive(104.dp),
            Modifier.fillMaxWidth().weight(1f).testTag("app-picker-list"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item(
                key = 0,
                span = {
                    GridItemSpan(maxLineSpan)
                },
            ) {
                TvAppsStatus(state, onRefresh, onOpen, refreshAvailable = true)
            }
            items(
                state.apps,
                key = {
                    it.id
                },
            ) { app ->
                appContent(app, false)
            }
        }
    }
}

private const val APP_ROW_FONT_SCALE = 1.4f
private const val APP_SHEET_HEIGHT_FRACTION = .94f

private fun tvAppsLoadMessage(state: TvAppsUiState): Int? =
    when {
        !state.connected -> R.string.tv_apps_reconnect
        state.loadError && state.hasLoaded -> R.string.tv_apps_refresh_failed
        state.loadError -> R.string.tv_apps_load_failed
        state.loading -> R.string.tv_apps_loading
        state.hasEmptyCatalog -> R.string.tv_apps_empty
        else -> null
    }

private const val APP_FADE_START = .92f
