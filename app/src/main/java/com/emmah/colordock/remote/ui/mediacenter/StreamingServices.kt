package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.emmah.colordock.remote.R

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
        if (catalog.services.none { streaming.canLaunch(it.serviceId) }) Text("Apps unavailable")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            catalog.services.forEach { service ->
                val id = service.serviceId
                OutlinedButton(
                    onClick = { open(id) },
                    enabled = enabled && streaming.canLaunch(id),
                    modifier = Modifier.size(64.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(6.dp),
                ) {
                    Image(
                        painterResource(serviceIcon(id)),
                        contentDescription = "Open ${serviceLabel(id)}",
                        modifier =
                            Modifier.fillMaxSize().alpha(
                                if (enabled &&
                                    streaming.canLaunch(id)
                                ) {
                                    1f
                                } else {
                                    DISABLED_APP_ALPHA
                                },
                            ),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
    }
}

private fun serviceIcon(id: String): Int =
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

private const val DISABLED_APP_ALPHA = .38f
