package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.emmah.colordock.remote.mediacenter.protocol.Direction
import com.emmah.colordock.remote.mediacenter.protocol.RemoteSnapshot
import com.emmah.colordock.remote.mediacenter.protocol.Route
import com.emmah.colordock.remote.tv.TvCommand
import com.emmah.colordock.remote.ui.DirectionPad

@Composable
internal fun RendererControls(
    snapshot: RemoteSnapshot?,
    controller: RendererController,
    modifier: Modifier = Modifier,
    bottom: @Composable () -> Unit = {},
) {
    val enabled = snapshot?.ui?.available == true
    val notice by controller.notice.collectAsState()
    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val status = rendererStatus(notice, enabled)
        if (status != null) {
            Text(
                status,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        BoxWithConstraints {
            DirectionPad(
                enabled = enabled,
                onCommand = { command ->
                    if (command == TvCommand.Ok) {
                        controller.activate()
                    } else {
                        nativeDirection(command)?.let(controller::move)
                    }
                },
                onPress = { nativeDirection(it)?.let(controller::press) },
                onRelease = controller::release,
                diameter = minOf(maxWidth * MEDIACENTER_PAD_WIDTH_FRACTION, 300.dp),
            )
        }
        bottom()
        if (snapshot?.ui?.route in listOf(Route.VIDEOS, Route.MUSIC, Route.PHOTOS) && enabled) {
            var value by remember(snapshot.serverEpoch, snapshot.ui.route) { mutableStateOf(TextFieldValue()) }
            PillSearchField(value, { next ->
                value = next
                if (next.composition ==
                    null
                ) {
                    controller.committed(next.text)
                } else {
                    controller.composing()
                }
            }, placeholder = "Search on computer")
        }
    }
    DisposableEffect(controller) { onDispose { controller.stop() } }
}

private fun nativeDirection(command: TvCommand): Direction? =
    when (command) {
        TvCommand.Up -> Direction.UP
        TvCommand.Down -> Direction.DOWN
        TvCommand.Left -> Direction.LEFT
        TvCommand.Right -> Direction.RIGHT
        else -> null
    }

private fun rendererStatus(
    notice: String,
    enabled: Boolean,
): String? =
    when {
        notice.isNotEmpty() -> {
            if (notice.contains("uncertain", true) || notice.contains("not confirmed", true)) {
                "Not confirmed"
            } else {
                "Try again"
            }
        }

        !enabled -> {
            "Controls unavailable"
        }

        else -> {
            null
        }
    }

private const val MEDIACENTER_PAD_WIDTH_FRACTION = .78f
