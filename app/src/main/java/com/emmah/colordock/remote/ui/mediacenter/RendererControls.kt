package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
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
) {
    val enabled = snapshot?.ui?.available == true
    val notice by controller.notice.collectAsState()
    Column(modifier) {
        if (notice.isNotEmpty()) Text(notice)
        if (!enabled) Text("Desktop navigation unavailable. Native media controls remain available.")
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
        )
        TextButton(onClick = { controller.back() }, enabled = enabled) { Text("Back") }
        if (snapshot?.ui?.route in listOf(Route.VIDEOS, Route.MUSIC, Route.PHOTOS) && enabled) {
            var value by remember(snapshot.serverEpoch, snapshot.ui.route) { mutableStateOf(TextFieldValue()) }
            OutlinedTextField(value, { next ->
                value = next
                if (next.composition ==
                    null
                ) {
                    controller.committed(next.text)
                } else {
                    controller.composing()
                }
            }, label = { Text("Search on computer") })
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
