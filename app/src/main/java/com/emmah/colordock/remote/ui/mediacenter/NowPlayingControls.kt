package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.emmah.colordock.remote.R
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
import com.emmah.colordock.remote.mediacenter.protocol.RemoteSnapshot
import com.emmah.colordock.remote.ui.DockIcon

internal fun queueAvailable(state: MediaSessionState): Boolean {
    val snapshot = state.snapshot ?: return false
    return when (snapshot.activity.mode) {
        ActivityMode.BROWSER -> false
        ActivityMode.PHOTOS -> true
        else -> snapshot.player.sessionId != null || (snapshot.queueSummary.count.toLongOrNull() ?: 0) > 0
    }
}

@Composable
internal fun RemoteQueueAction(
    state: MediaSessionState,
    onQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (queueAvailable(state)) {
        MediaIconButton(
            R.drawable.ic_queue_music,
            "Queue",
            onQueue,
            true,
            modifier,
            emphasized = true,
        )
    }
}

@Composable
internal fun NowPlayingCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
internal fun NowPlayingControls(
    state: MediaSessionState,
    command: (RemoteCommand) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snapshot = state.snapshot ?: return
    val idle = snapshot.activity.mode == ActivityMode.IDLE
    if (idle && !queueAvailable(state)) return
    NowPlayingCard(modifier) {
        val title = snapshot.activity.title ?: activityTitle(snapshot.activity.mode)
        if (!idle) {
            Text(
                mediaTitle(title),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        NowPlayingTransport(snapshot, snapshot.player.sessionId != null && !state.busy, command)
        PlayerAdjustments(state, command)
    }
}

@Composable
private fun NowPlayingTransport(
    snapshot: RemoteSnapshot,
    enabled: Boolean,
    command: (RemoteCommand) -> Unit,
) {
    val player = snapshot.player
    val session = player.sessionId
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (snapshot.queueSummary.sessionId != null) {
            MediaIconButton(
                R.drawable.ic_skip_previous,
                "Previous",
                { command(RemoteCommand.QueuePrevious(Empty)) },
                enabled,
            )
        }
        if (session != null) {
            PlaybackToggle(player.phase == PlayerPhase.PAUSED, enabled, {
                command(RemoteCommand.PlayerPause(PlayerPauseParams(session, player.phase != PlayerPhase.PAUSED)))
            })
        }
        if (snapshot.queueSummary.sessionId != null) {
            MediaIconButton(R.drawable.ic_skip_next, "Next", { command(RemoteCommand.QueueNext(Empty)) }, enabled)
        }
        if (session != null) {
            MediaIconButton(
                R.drawable.ic_stop,
                "Stop",
                { command(RemoteCommand.PlayerStop(PlayerStopParams(session))) },
                true,
            )
        }
    }
}

@Composable
internal fun PlaybackToggle(
    paused: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledIconButton(
        onClick,
        modifier.size(56.dp),
        enabled = enabled,
        colors =
            IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
    ) {
        DockIcon(if (paused) R.drawable.ic_play_arrow else R.drawable.ic_pause, label = if (paused) "Play" else "Pause")
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
        Text("Volume", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        ListGroup {
            val mute = { command(RemoteCommand.PlayerMute(PlayerMuteParams(session, !player.muted))) }
            ListRow(
                if (player.muted) "Unmute" else "Mute",
                null,
                ListRowPosition.First,
                onClick = mute,
                enabled = enabled,
                trailing = { Switch(player.muted, { mute() }, enabled = enabled) },
            )
            var tracks by remember { mutableStateOf(false) }
            ListRow("Audio & subtitles", null, ListRowPosition.Last, onClick = { tracks = !tracks })
            if (tracks) {
                PlayerTrackChoices(player, session, enabled, command)
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
        Text(
            "${playbackTime(player.positionSeconds)} / ${playbackTime(duration)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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

@Composable
private fun PlayerTrackChoices(
    player: PlayerTopic,
    session: String,
    enabled: Boolean,
    command: (RemoteCommand) -> Unit,
) {
    player.audioTracks.forEach { track ->
        val choose = { command(RemoteCommand.PlayerAudioTrack(PlayerAudioTrackParams(session, track.id))) }
        ListRow(
            "Audio: ${track.label}",
            null,
            ListRowPosition.Single,
            onClick = choose,
            enabled = enabled,
            trailing = { RadioButton(track.selected, { choose() }, enabled = enabled) },
        )
    }
    ListRow("Subtitles off", null, ListRowPosition.Single, onClick = {
        command(RemoteCommand.PlayerSubtitleTrack(PlayerSubtitleTrackParams(session, null)))
    }, enabled = enabled)
    player.subtitleTracks.forEach { track ->
        val choose = {
            command(
                RemoteCommand.PlayerSubtitleTrack(PlayerSubtitleTrackParams(session, track.id)),
            )
        }
        ListRow(
            "Subtitles: ${track.label}",
            null,
            ListRowPosition.Single,
            onClick = choose,
            enabled = enabled,
            trailing = { RadioButton(track.selected, { choose() }, enabled = enabled) },
        )
    }
}
