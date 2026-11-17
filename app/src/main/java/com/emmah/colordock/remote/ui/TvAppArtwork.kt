package com.emmah.colordock.remote.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.emmah.colordock.remote.R
import com.emmah.colordock.remote.tv.TvApp

@Composable
internal fun TvAppArtwork(
    appId: String,
    bitmap: ImageBitmap?,
    fallback: Int?,
    iconOnly: Boolean,
    modifier: Modifier = Modifier,
) {
    val backplate = bitmap != null && appId in lgSystemArtworkIds
    val artwork = modifier.testTag("app-artwork-$appId")
    val clipped = if (iconOnly || backplate) artwork.clip(RemoteShapes.Inner) else artwork
    val image = if (backplate) clipped.background(Masala) else clipped
    when {
        bitmap != null -> Image(bitmap, null, image, contentScale = ContentScale.Fit)
        fallback != null -> Image(painterResource(fallback), null, image, contentScale = ContentScale.Fit)
        iconOnly -> Icon(painterResource(R.drawable.ic_grid_view), null, image)
    }
}

// Built-in webOS apps supply white artwork on a transparent background.
private val lgSystemArtworkIds =
    setOf("com.webos.app.discovery", "com.webos.app.photovideo", "com.webos.app.mediadiscovery")

// Explicit state and callbacks preserve the hoisted Compose API.
@Suppress("LongParameterList")
@Composable
internal fun TvAppTile(
    app: TvApp,
    file: ImageFile?,
    enabled: Boolean,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    row: Boolean = false,
    iconOnly: Boolean = false,
) {
    val description = stringResource(R.string.open_tv_app, app.title)
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, file) {
        value =
            loadAppBitmap(file)
    }
    val fallback = tvAppFallback(app.id)
    val artSize =
        when {
            iconOnly -> 56.dp
            row -> 48.dp
            else -> 64.dp
        }
    val artwork: @Composable () -> Unit = {
        TvAppArtwork(app.id, bitmap, fallback, iconOnly, Modifier.size(artSize))
    }
    Surface(
        onClick = {
            onOpen(app.id)
        },
        enabled = enabled,
        modifier =
            modifier.heightIn(min = 48.dp).semantics(mergeDescendants = true) {
                contentDescription = description
                role = Role.Button
            },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        when {
            iconOnly -> {
                Box(Modifier.padding(4.dp), contentAlignment = Alignment.Center) { artwork() }
            }

            row -> {
                Row(
                    Modifier.padding(RemoteDimens.ItemGap),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(RemoteDimens.BlockGap),
                ) {
                    if (bitmap != null || fallback != null) artwork() else AppInitial(app.title)
                    AppTileLabel(app.title, Modifier.weight(1f))
                }
            }

            else -> {
                Column(
                    Modifier.padding(RemoteDimens.ItemGap),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(Modifier.height(64.dp), contentAlignment = Alignment.Center) {
                        if (bitmap != null || fallback != null) artwork() else AppInitial(app.title)
                    }
                    AppTileLabel(app.title, reserveTwoLines = true)
                }
            }
        }
    }
}

@Composable
private fun AppInitial(title: String) {
    Surface(
        shape = RemoteShapes.Inner,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Text(title.trimStart().take(1).uppercase(), style = MaterialTheme.typography.titleLarge, maxLines = 1)
        }
    }
}

@Composable
private fun AppTileLabel(
    title: String,
    modifier: Modifier = Modifier,
    reserveTwoLines: Boolean = false,
) {
    Text(
        title,
        modifier,
        style = MaterialTheme.typography.labelMedium,
        minLines = if (reserveTwoLines) 2 else 1,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

internal fun tvAppFallback(id: String): Int? =
    when (id) {
        "youtube.leanback.v4" -> R.drawable.tv_app_youtube
        "netflix" -> R.drawable.tv_app_netflix
        "com.wbd.stream" -> R.drawable.tv_app_hbo_max
        "viaplay-lg" -> R.drawable.tv_app_viaplay
        "fi.yle.areena" -> R.drawable.tv_app_yle_areena
        "mtvkatsomo" -> R.drawable.tv_app_mtv_katsomo
        "fi.nelonenmedia.ruutu" -> R.drawable.tv_app_ruutu
        "cdp-30" -> R.drawable.tv_app_plex
        else -> null
    }
