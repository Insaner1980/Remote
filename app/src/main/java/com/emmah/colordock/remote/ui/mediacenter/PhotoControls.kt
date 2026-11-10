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
        Text(if (photo?.paused != false) "Photo presentation paused" else "Slideshow playing")
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
            label = { Text("Interval (1–60 seconds)") },
            isError =
                interval !in 1..PHOTO_MAX_INTERVAL,
        )
        TextButton(
            onClick = { session?.let { command(RemoteCommand.PhotosInterval(PhotosIntervalParams(it, interval!!))) } },
            enabled =
                enabled && interval in 1..PHOTO_MAX_INTERVAL,
        ) { Text("Apply interval") }
        TextButton(
            onClick = { start(selection, photo?.currentItemId, interval!!) },
            enabled =
                state.snapshot != null && !state.busy && selection != null && interval in 1..PHOTO_MAX_INTERVAL,
        ) { Text("Start filtered slideshow") }
        TextButton(onClick = {
            session?.let {
                command(RemoteCommand.PhotosShuffle(PhotosShuffleParams(it, !photo.shuffle)))
            }
        }, enabled = enabled) { Text("Photo shuffle: ${photo?.shuffle == true}") }
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
        }, enabled = enabled) { Text("Image size: ${photo?.fit?.wire ?: "fit"}") }
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
        }, enabled = enabled) { Text("Previous photo") }
        TextButton(onClick = {
            session?.let { command(RemoteCommand.PhotosNext(PhotosNextParams(it))) }
        }, enabled = enabled) { Text("Next photo") }
    }
    Row {
        TextButton(onClick = {
            session?.let { command(RemoteCommand.PhotosPause(PhotosPauseParams(it, !photo.paused))) }
        }, enabled = enabled) {
            Text(
                if (photo?.paused !=
                    false
                ) {
                    "Resume slideshow"
                } else {
                    "Pause slideshow"
                },
            )
        }
        TextButton(onClick = {
            session?.let {
                command(RemoteCommand.PhotosStop(PhotosStopParams(it)))
            }
        }, enabled = session != null) { Text("Stop slideshow") }
    }
}
