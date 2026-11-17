package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.emmah.colordock.remote.R
import com.emmah.colordock.remote.ui.DockIcon

@Composable
internal fun StreamingServices(
    streaming: StreamingState,
    enabled: Boolean,
    open: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val catalog = streaming.capabilities ?: return
    if (catalog.services.isEmpty()) return
    Column(modifier) {
        if (catalog.services.none { streaming.canLaunch(it.serviceId) }) {
            Row(
                Modifier.padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DockIcon(R.drawable.ic_error, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "Apps unavailable",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            Modifier
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        Brush.horizontalGradient(FADE_START to Color.Black, 1f to Color.Transparent),
                        blendMode = BlendMode.DstIn,
                    )
                }.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            catalog.services.forEach { service ->
                val id = service.serviceId
                StreamingServiceTile(id, enabled && streaming.canLaunch(id), open)
            }
            Spacer(Modifier.width(16.dp))
        }
    }
}

internal fun serviceIcon(id: String): Int =
    when (id) {
        "netflix" -> R.drawable.tv_app_netflix
        "youtube" -> R.drawable.tv_app_youtube
        "hbo_max" -> R.drawable.tv_app_hbo_max
        "viaplay" -> R.drawable.tv_app_viaplay
        "mtv_katsomo" -> R.drawable.tv_app_mtv_katsomo
        "ruutu" -> R.drawable.tv_app_ruutu
        "yle_areena" -> R.drawable.tv_app_yle_areena
        "plex" -> R.drawable.tv_app_plex
        else -> R.drawable.ic_grid_view
    }

internal fun serviceLabel(serviceId: String): String =
    when (serviceId) {
        "netflix" -> "Netflix"
        "youtube" -> "YouTube"
        "hbo_max" -> "HBO Max"
        "viaplay" -> "Viaplay"
        "mtv_katsomo" -> "MTV Katsomo"
        "ruutu" -> "Ruutu"
        "yle_areena" -> "Yle Areena"
        "plex" -> "Plex"
        else -> serviceId.replace('_', ' ').replaceFirstChar(Char::titlecase)
    }

private const val DISABLED_APP_ALPHA = .6f

private val DisabledArtwork = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

private const val FADE_START = .85f

@Composable
private fun StreamingServiceTile(
    id: String,
    enabled: Boolean,
    open: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val icon = serviceIcon(id)
    val bitmap = if (!enabled && icon != R.drawable.ic_grid_view) ImageBitmap.imageResource(icon) else null
    // The bundled wordmarks have flat, opaque backgrounds. Use their edge color behind the faded artwork.
    val backplate = bitmap?.let { remember(it) { it.toPixelMap(width = 1, height = 1)[0, 0] } }
    val painter = bitmap?.let { remember(it) { BitmapPainter(it) } } ?: painterResource(icon)
    Surface(
        onClick = { open(id) },
        enabled = enabled,
        modifier = modifier.size(64.dp),
        shape = MaterialTheme.shapes.large,
        color = backplate ?: MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (enabled) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Image(
            painter,
            contentDescription = "Open ${serviceLabel(id)}",
            modifier =
                Modifier.fillMaxSize().clip(MaterialTheme.shapes.large).alpha(
                    if (enabled) {
                        1f
                    } else {
                        DISABLED_APP_ALPHA
                    },
                ),
            contentScale = ContentScale.Fit,
            colorFilter = if (enabled) null else DisabledArtwork,
        )
    }
}
