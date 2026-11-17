package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.emmah.colordock.remote.R
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
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
            modifier = Modifier.fillMaxWidth(),
            isError =
                interval !in 1..PHOTO_MAX_INTERVAL,
        )
        MediaKey("Apply", null, enabled && interval in 1..PHOTO_MAX_INTERVAL, {
            session?.let { command(RemoteCommand.PhotosInterval(PhotosIntervalParams(it, interval!!))) }
        }, Modifier.fillMaxWidth())
        MediaKey(
            "Start slideshow",
            null,
            state.snapshot != null && !state.busy && selection != null && interval in 1..PHOTO_MAX_INTERVAL,
            { start(selection, photo?.currentItemId, interval!!) },
            Modifier.fillMaxWidth(),
        )
        PhotoDisplayOptions(state, command)
    }
}

@Composable
private fun PhotoTransport(
    state: MediaSessionState,
    command: (RemoteCommand) -> Unit,
) {
    val photo = state.snapshot?.slideshow
    val session = photo?.sessionId
    val enabled = session != null && !state.busy
    TransportRow {
        MediaIconButton(R.drawable.ic_skip_previous, "Previous", {
            session?.let { command(RemoteCommand.PhotosPrevious(PhotosPreviousParams(it))) }
        }, enabled)
        PlaybackToggle(photo?.paused != false, enabled, {
            session?.let { command(RemoteCommand.PhotosPause(PhotosPauseParams(it, !photo.paused))) }
        })
        MediaIconButton(R.drawable.ic_skip_next, "Next", {
            session?.let { command(RemoteCommand.PhotosNext(PhotosNextParams(it))) }
        }, enabled)
        MediaIconButton(R.drawable.ic_stop, "Stop", {
            session?.let { command(RemoteCommand.PhotosStop(PhotosStopParams(it))) }
        }, session != null)
    }
}

@Composable
private fun PhotoDisplayOptions(
    state: MediaSessionState,
    command: (RemoteCommand) -> Unit,
) {
    val photo = state.snapshot?.slideshow
    val session = photo?.sessionId
    val enabled = session != null && !state.busy
    ListGroup {
        val shuffle = {
            session?.let { command(RemoteCommand.PhotosShuffle(PhotosShuffleParams(it, !photo.shuffle))) }
            Unit
        }
        ListRow(
            if (photo?.shuffle == true) "Shuffle on" else "Shuffle off",
            null,
            ListRowPosition.First,
            onClick = shuffle,
            enabled = enabled,
            trailing = { Switch(photo?.shuffle == true, { shuffle() }, enabled = enabled) },
        )
        ListRow(
            if (photo?.fit == Fit.ACTUAL) "Original size" else "Fit to screen",
            null,
            ListRowPosition.Last,
            onClick = {
                session?.let {
                    command(
                        RemoteCommand.PhotosFit(
                            PhotosFitParams(
                                it,
                                if (photo.fit == Fit.FIT) Fit.ACTUAL else Fit.FIT,
                            ),
                        ),
                    )
                }
            },
            enabled = enabled,
        )
    }
}
