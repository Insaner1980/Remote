package com.emmah.colordock.remote.ui

import androidx.compose.foundation.Image
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.emmah.colordock.remote.R

@Composable
internal fun TvAppArtwork(
    bitmap: androidx.compose.ui.graphics.ImageBitmap?,
    fallback: Int?,
    iconOnly: Boolean,
    modifier: Modifier = Modifier,
) {
    when {
        bitmap != null -> Image(bitmap, null, modifier, contentScale = ContentScale.Fit)
        fallback != null -> Image(painterResource(fallback), null, modifier, contentScale = ContentScale.Fit)
        iconOnly -> Icon(painterResource(R.drawable.ic_grid_view), null, modifier)
    }
}
