package com.emmah.colordock.remote.ui.mediacenter

import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand

internal data class RoutineSettingsActions(
    val send: (RemoteCommand) -> Unit,
    val refresh: () -> Unit,
    val dismissPairing: () -> Unit,
)
