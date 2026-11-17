package com.emmah.colordock.remote.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.emmah.colordock.remote.R

@Composable
internal fun TvAppArtwork(
    appId: String,
    bitmap: ImageBitmap?,
    fallback: Int?,
    row: Boolean,
    iconOnly: Boolean,
) {
    val size =
        when {
            iconOnly -> 56.dp
            row -> 48.dp
            else -> 64.dp
        }
    val artwork = Modifier.size(size).testTag("app-artwork-$appId")
    val modifier = if (iconOnly) artwork.clip(RemoteShapes.Inner) else artwork
    when {
        bitmap != null -> Image(bitmap, null, modifier, contentScale = ContentScale.Fit)
        fallback != null -> Image(painterResource(fallback), null, modifier, contentScale = ContentScale.Fit)
        iconOnly -> Icon(painterResource(R.drawable.ic_grid_view), null, modifier)
    }
}
