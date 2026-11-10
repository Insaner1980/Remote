package com.emmah.colordock.remote.ui.mediacenter

import com.emmah.colordock.remote.mediacenter.protocol.ActivityMode
import com.emmah.colordock.remote.mediacenter.protocol.ActivityTopic
import com.emmah.colordock.remote.mediacenter.protocol.Availability
import com.emmah.colordock.remote.mediacenter.protocol.ConnectionTopic
import com.emmah.colordock.remote.mediacenter.protocol.Fit
import com.emmah.colordock.remote.mediacenter.protocol.MediaItem
import com.emmah.colordock.remote.mediacenter.protocol.MediaKind
import com.emmah.colordock.remote.mediacenter.protocol.OperationsTopic
import com.emmah.colordock.remote.mediacenter.protocol.OutputsTopic
import com.emmah.colordock.remote.mediacenter.protocol.PlayerPhase
import com.emmah.colordock.remote.mediacenter.protocol.PlayerTopic
import com.emmah.colordock.remote.mediacenter.protocol.QueueSummary
import com.emmah.colordock.remote.mediacenter.protocol.RemoteSnapshot
import com.emmah.colordock.remote.mediacenter.protocol.RepeatMode
import com.emmah.colordock.remote.mediacenter.protocol.SlideshowTopic
import com.emmah.colordock.remote.mediacenter.protocol.SnapshotType
import com.emmah.colordock.remote.mediacenter.protocol.SourcesSummary
import com.emmah.colordock.remote.mediacenter.protocol.SwitchState
import com.emmah.colordock.remote.mediacenter.protocol.UiTopic
import com.emmah.colordock.remote.mediacenter.protocol.VpnState
import com.emmah.colordock.remote.mediacenter.protocol.VpnTopic

internal fun phoneMediaSnapshot() =
    RemoteSnapshot(
        1,
        SnapshotType.SNAPSHOT,
        "epoch",
        "1",
        ConnectionTopic("1", "server", "Computer"),
        ActivityTopic("1", ActivityMode.MUSIC, "session", "Song"),
        PlayerTopic("1", "session", "song", PlayerPhase.PLAYING, 10.0, 180.0, 50.0, false, emptyList(), emptyList()),
        QueueSummary("7", "2", "session", "entry-a", false, RepeatMode.OFF),
        SlideshowTopic("1", "photos", "photo", true, 5, false, Fit.FIT),
        OutputsTopic("1", unavailable = false, outputs = emptyList()),
        SourcesSummary("1", "1", false),
        VpnTopic("1", VpnState.DISCONNECTED, killSwitch = SwitchState.UNKNOWN, observedAtMs = 0),
        UiTopic("1", false),
        OperationsTopic("1", emptyList()),
    )

internal fun phoneMediaItem(
    id: String = "song",
    kind: MediaKind = MediaKind.MUSIC,
) = MediaItem(id, "source", kind, id, Availability.AVAILABLE)
