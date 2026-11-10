package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.emmah.colordock.remote.mediacenter.protocol.Empty
import com.emmah.colordock.remote.mediacenter.protocol.PlayerAudioTrackParams
import com.emmah.colordock.remote.mediacenter.protocol.PlayerMuteParams
import com.emmah.colordock.remote.mediacenter.protocol.PlayerPauseParams
import com.emmah.colordock.remote.mediacenter.protocol.PlayerPhase
import com.emmah.colordock.remote.mediacenter.protocol.PlayerSeekParams
import com.emmah.colordock.remote.mediacenter.protocol.PlayerStopParams
import com.emmah.colordock.remote.mediacenter.protocol.PlayerSubtitleTrackParams
import com.emmah.colordock.remote.mediacenter.protocol.PlayerVolumeParams
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand

@Composable
internal fun NowPlayingControls(
    state: MediaSessionState,
    command: (RemoteCommand) -> Unit,
    queue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snapshot = state.snapshot
    val player = snapshot?.player
    val session = player?.sessionId
    val enabled = session != null && !state.busy
    Column(modifier) {
        Text(snapshot?.activity?.title ?: "Nothing playing", style = MaterialTheme.typography.titleLarge)
        Text(player?.phase?.wire ?: "Disconnected")
        Row {
            TextButton(onClick = {
                session?.let {
                    command(
                        RemoteCommand.PlayerPause(
                            PlayerPauseParams(
                                it,
                                player.phase != PlayerPhase.PAUSED,
                            ),
                        ),
                    )
                }
            }, enabled = enabled) {
                Text(
                    if (player?.phase ==
                        PlayerPhase.PAUSED
                    ) {
                        "Play"
                    } else {
                        "Pause"
                    },
                )
            }
            TextButton(onClick = {
                session?.let { command(RemoteCommand.PlayerStop(PlayerStopParams(it))) }
            }, enabled = session != null) { Text("Stop") }
            TextButton(onClick = queue, enabled = snapshot != null) { Text("Queue") }
        }
        if (snapshot?.queueSummary?.sessionId != null) {
            Row {
                TextButton(
                    onClick = { command(RemoteCommand.QueuePrevious(Empty)) },
                    enabled = enabled,
                ) { Text("Previous track") }
                TextButton(
                    onClick = { command(RemoteCommand.QueueNext(Empty)) },
                    enabled = enabled,
                ) { Text("Next track") }
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
        val duration = player.durationSeconds
        if (duration != null && duration > 0) {
            var seek by remember(
                session,
                player.positionSeconds,
            ) { mutableFloatStateOf(player.positionSeconds.toFloat()) }
            Text("${player.positionSeconds.toInt()} / ${duration.toInt()} seconds")
            Slider(
                seek.coerceIn(0f, duration.toFloat()),
                { seek = it },
                enabled = enabled,
                valueRange = 0f..duration.toFloat(),
                onValueChangeFinished = {
                    command(RemoteCommand.PlayerSeek(PlayerSeekParams(session, seek.toDouble())))
                },
            )
        }
        var volume by remember(session, player.volume) { mutableFloatStateOf(player.volume.toFloat()) }
        Text("Mediacenter volume ${volume.toInt()}")
        Slider(volume, {
            volume = it
        }, enabled = enabled, valueRange = 0f..100f, onValueChangeFinished = {
            command(RemoteCommand.PlayerVolume(PlayerVolumeParams(session, volume.toDouble())))
        })
        TextButton(onClick = {
            command(RemoteCommand.PlayerMute(PlayerMuteParams(session, !player.muted)))
        }, enabled = enabled) { Text(if (player.muted) "Unmute Mediacenter" else "Mute Mediacenter") }
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
