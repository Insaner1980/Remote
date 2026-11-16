package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.emmah.colordock.remote.mediacenter.protocol.ActivityMode
import com.emmah.colordock.remote.mediacenter.protocol.Empty
import com.emmah.colordock.remote.mediacenter.protocol.PlayerAudioTrackParams
import com.emmah.colordock.remote.mediacenter.protocol.PlayerMuteParams
import com.emmah.colordock.remote.mediacenter.protocol.PlayerPauseParams
import com.emmah.colordock.remote.mediacenter.protocol.PlayerPhase
import com.emmah.colordock.remote.mediacenter.protocol.PlayerSeekParams
import com.emmah.colordock.remote.mediacenter.protocol.PlayerStopParams
import com.emmah.colordock.remote.mediacenter.protocol.PlayerSubtitleTrackParams
import com.emmah.colordock.remote.mediacenter.protocol.PlayerTopic
import com.emmah.colordock.remote.mediacenter.protocol.PlayerVolumeParams
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand

@Composable
internal fun NowPlayingControls(
    state: MediaSessionState,
    command: (RemoteCommand) -> Unit,
    queue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snapshot = state.snapshot ?: return
    val player = snapshot.player
    val session = player.sessionId
    val enabled = session != null && !state.busy
    val idle = snapshot.activity.mode == ActivityMode.IDLE
    val hasQueue = (snapshot.queueSummary.count.toLongOrNull() ?: 0) > 0
    if (idle && session == null && !hasQueue) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val title = snapshot.activity.title ?: activityTitle(snapshot.activity.mode)
        if (!idle) Text(mediaTitle(title), style = MaterialTheme.typography.titleLarge)
        Row {
            if (session != null) {
                TextButton(onClick = {
                    command(RemoteCommand.PlayerPause(PlayerPauseParams(session, player.phase != PlayerPhase.PAUSED)))
                }, enabled = enabled) {
                    Text(if (player.phase == PlayerPhase.PAUSED) "Play" else "Pause")
                }
                TextButton(onClick = { command(RemoteCommand.PlayerStop(PlayerStopParams(session))) }) { Text("Stop") }
            }
            if (hasQueue || session != null) TextButton(onClick = queue) { Text("Queue") }
        }

        if (snapshot.queueSummary.sessionId != null) {
            Row {
                TextButton(
                    onClick = { command(RemoteCommand.QueuePrevious(Empty)) },
                    enabled = enabled,
                ) { Text("Previous") }
                TextButton(
                    onClick = { command(RemoteCommand.QueueNext(Empty)) },
                    enabled = enabled,
                ) { Text("Next") }
            }
        }
        PlayerAdjustments(state, command)
    }
}

@Composable
private fun ColumnScope.PlayerAdjustments(
    state: MediaSessionState,
    command: (RemoteCommand) -> Unit,
) {
    val player = state.snapshot?.player
    val session = player?.sessionId
    val enabled = session != null && !state.busy
    if (player != null && session != null) {
        key(state.snapshot.connection.serverDeviceId, state.snapshot.serverEpoch, session) {
            PlayerSeekControl(player, session, enabled, command)
        }
        var volume by remember(session, player.volume) { mutableFloatStateOf(player.volume.toFloat()) }
        Text("Volume")
        Slider(
            volume,
            {
                volume = it
            },
            modifier =
                Modifier.semantics {
                    contentDescription = "Volume"
                },
            enabled = enabled,
            valueRange = 0f..100f,
            onValueChangeFinished = {
                command(RemoteCommand.PlayerVolume(PlayerVolumeParams(session, volume.toDouble())))
            },
        )
        TextButton(onClick = {
            command(RemoteCommand.PlayerMute(PlayerMuteParams(session, !player.muted)))
        }, enabled = enabled) { Text(if (player.muted) "Unmute" else "Mute") }
        var tracks by remember { mutableStateOf(false) }
        TextButton(onClick = { tracks = !tracks }) { Text("Audio & subtitles") }
        if (tracks) {
            player.audioTracks.forEach { track ->
                TextButton(onClick = {
                    command(RemoteCommand.PlayerAudioTrack(PlayerAudioTrackParams(session, track.id)))
                }, enabled = enabled) { Text("Audio: ${track.label}${if (track.selected) " ✓" else ""}") }
            }
            TextButton(onClick = {
                command(RemoteCommand.PlayerSubtitleTrack(PlayerSubtitleTrackParams(session, null)))
            }, enabled = enabled) { Text("Subtitles off") }
            player.subtitleTracks.forEach { track ->
                TextButton(onClick = {
                    command(RemoteCommand.PlayerSubtitleTrack(PlayerSubtitleTrackParams(session, track.id)))
                }, enabled = enabled) { Text("Subtitles: ${track.label}${if (track.selected) " ✓" else ""}") }
            }
        }
    }
}

@Composable
private fun ColumnScope.PlayerSeekControl(
    player: PlayerTopic,
    session: String,
    enabled: Boolean,
    command: (RemoteCommand) -> Unit,
) {
    val duration = player.durationSeconds
    if (duration != null && duration > 0) {
        var draft by remember { mutableStateOf<Float?>(null) }
        Text("${playbackTime(player.positionSeconds)} / ${playbackTime(duration)}")
        Slider(
            (draft ?: player.positionSeconds.toFloat()).coerceIn(0f, duration.toFloat()),
            { draft = it },
            enabled = enabled,
            modifier = Modifier.semantics { contentDescription = "Playback position" },
            valueRange = 0f..duration.toFloat(),
            onValueChangeFinished = {
                val selected = draft
                draft = null
                if (selected != null && enabled) {
                    command(RemoteCommand.PlayerSeek(PlayerSeekParams(session, selected.toDouble())))
                }
            },
        )
    }
}

private fun activityTitle(mode: ActivityMode): String =
    when (mode) {
        ActivityMode.VIDEO -> "Video"
        ActivityMode.MUSIC -> "Music"
        ActivityMode.PHOTOS -> "Photos"
        ActivityMode.BROWSER -> "Streaming"
        ActivityMode.IDLE -> ""
    }
