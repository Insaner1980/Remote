package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.emmah.colordock.remote.mediacenter.protocol.Fit
import com.emmah.colordock.remote.mediacenter.protocol.PhotosFitParams
import com.emmah.colordock.remote.mediacenter.protocol.PhotosIntervalParams
import com.emmah.colordock.remote.mediacenter.protocol.PhotosNextParams
import com.emmah.colordock.remote.mediacenter.protocol.PhotosPauseParams
import com.emmah.colordock.remote.mediacenter.protocol.PhotosPreviousParams
import com.emmah.colordock.remote.mediacenter.protocol.PhotosShuffleParams
import com.emmah.colordock.remote.mediacenter.protocol.PhotosStopParams
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand

@Composable
internal fun PhotoControls(
    state: MediaSessionState,
    selection: String?,
    command: (RemoteCommand) -> Unit,
    start: (String?, String?, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val photo = state.snapshot?.slideshow
    val session = photo?.sessionId
    val enabled = session != null && !state.busy
    Column(modifier) {
        PhotoTransport(state, command)
        var seconds by remember(photo?.intervalSeconds) {
            mutableStateOf(
                (
                    photo?.intervalSeconds
                        ?: PHOTO_DEFAULT_INTERVAL
                ).toString(),
            )
        }
        val interval = seconds.toIntOrNull()
        OutlinedTextField(
            seconds,
            { seconds = it },
            label = { Text("Seconds") },
            isError =
                interval !in 1..PHOTO_MAX_INTERVAL,
        )
        TextButton(
            onClick = { session?.let { command(RemoteCommand.PhotosInterval(PhotosIntervalParams(it, interval!!))) } },
            enabled =
                enabled && interval in 1..PHOTO_MAX_INTERVAL,
        ) { Text("Apply") }
        TextButton(
            onClick = { start(selection, photo?.currentItemId, interval!!) },
            enabled =
                state.snapshot != null && !state.busy && selection != null && interval in 1..PHOTO_MAX_INTERVAL,
        ) { Text("Start slideshow") }
        TextButton(onClick = {
            session?.let {
                command(RemoteCommand.PhotosShuffle(PhotosShuffleParams(it, !photo.shuffle)))
            }
        }, enabled = enabled) { Text(if (photo?.shuffle == true) "Shuffle on" else "Shuffle off") }
        TextButton(onClick = {
            session?.let {
                command(
                    RemoteCommand.PhotosFit(
                        PhotosFitParams(
                            it,
                            if (photo.fit ==
                                Fit.FIT
                            ) {
                                Fit.ACTUAL
                            } else {
                                Fit.FIT
                            },
                        ),
                    ),
                )
            }
        }, enabled = enabled) { Text(if (photo?.fit == Fit.ACTUAL) "Original size" else "Fit to screen") }
    }
}

@Composable
private fun ColumnScope.PhotoTransport(
    state: MediaSessionState,
    command: (RemoteCommand) -> Unit,
) {
    val photo = state.snapshot?.slideshow
    val session = photo?.sessionId
    val enabled = session != null && !state.busy
    Row {
        TextButton(onClick = {
            session?.let {
                command(RemoteCommand.PhotosPrevious(PhotosPreviousParams(it)))
            }
        }, enabled = enabled) { Text("Previous") }
        TextButton(onClick = {
            session?.let { command(RemoteCommand.PhotosNext(PhotosNextParams(it))) }
        }, enabled = enabled) { Text("Next") }
    }
    Row {
        TextButton(onClick = {
            session?.let { command(RemoteCommand.PhotosPause(PhotosPauseParams(it, !photo.paused))) }
        }, enabled = enabled) {
            Text(
                if (photo?.paused !=
                    false
                ) {
                    "Play"
                } else {
                    "Pause"
                },
            )
        }
        TextButton(onClick = {
            session?.let {
                command(RemoteCommand.PhotosStop(PhotosStopParams(it)))
            }
        }, enabled = session != null) { Text("Stop") }
    }
}
