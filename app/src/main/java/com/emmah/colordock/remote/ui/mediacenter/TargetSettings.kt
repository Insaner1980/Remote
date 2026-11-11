package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

@Composable
internal fun ColumnScope.TargetSettings(
    model: MediaTargetState,
    controllers: MediaTargetControllers,
    settings: @Composable () -> Unit,
) {
    val state = model.media
    val sound = model.sound
    val media = controllers.media
    val audio = controllers.audio
    SoundOptions(
        sound,
        SoundActions({
            audio.retry(true)
        }, {
            audio.retry()
        }, {
            audio.choose(
                it,
            )
        }, { audio.test(it) }, { audio.confirm() }, { audio.cancel() }),
        showConfirmation = false,
    )
    SettingsScreen(
        state,
        RoutineSettingsActions({ media.command(it) }, media::refreshSettings, media::dismissPairing),
    )
    TargetSources(model, controllers)
    TargetDrives(model, controllers)
    settings()
}

@Composable
internal fun ObserveRoutineSettings(media: MediaSessionController) {
    val state by media.state.collectAsState()
    LaunchedEffect(state.destination, state.snapshot?.serverEpoch, state.snapshot?.connection?.serverDeviceId) {
        if (state.destination == MediaDestination.Settings && state.snapshot != null) media.refreshSettings()
    }
}
