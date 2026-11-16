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
    navigation: SettingsNavigation,
    settings: @Composable (SettingsSection) -> Unit,
) {
    val section = navigation.section
    LaunchedEffect(
        section,
        model.media.settings.pairing
            ?.attemptId,
    ) {
        if (section != SettingsSection.Devices) controllers.media.dismissPairing()
    }
    if (section == null) {
        SettingsOverview({ navigation.section = it }, summaries = settingsSummaries(model))
        return
    }
    val state = model.media
    val sound = model.sound
    val media = controllers.media
    val audio = controllers.audio
    if (section == SettingsSection.Sound) {
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
            initiallyOpen = true,
        )
    }
    SettingsScreen(
        state,
        RoutineSettingsActions({ media.command(it) }, media::refreshSettings, media::dismissPairing),
        section = section,
    )
    if (section == SettingsSection.Media) {
        TargetSources(model, controllers)
        TargetDrives(model, controllers)
    }
    settings(section)
    LaunchedEffect(section) {
        if (section == SettingsSection.Sound) audio.retry(true)
    }
}

@Composable
internal fun ObserveRoutineSettings(media: MediaSessionController) {
    val state by media.state.collectAsState()
    LaunchedEffect(state.destination, state.snapshot?.serverEpoch, state.snapshot?.connection?.serverDeviceId) {
        if (state.destination == MediaDestination.Settings && state.snapshot != null) media.refreshSettings()
    }
}

internal fun settingsSummaries(model: MediaTargetState): SettingsSummaries {
    val settings = model.media.settings
    val outputs = model.sound.outputs
    val selected = outputs?.requestedOutputId ?: outputs?.effectiveOutputId
    val sound = soundOutputLabels(outputs?.outputs.orEmpty())[selected]
    val folders =
        model.media.snapshot
            ?.sourcesSummary
            ?.count
            ?.toLongOrNull()
            ?.takeIf { it >= 0 }
    return SettingsSummaries(
        sound = sound,
        vpn = vpnStateLabel(settings.vpn?.state),
        media = folders?.let { if (it == 1L) "1 folder" else "$it folders" },
        devices =
            settings.devices
                ?.items
                ?.size
                ?.let { if (it == 1) "1 phone" else "$it phones" },
        appearance =
            settings.native
                ?.theme
                ?.wire
                ?.replaceFirstChar { it.uppercase() },
        advanced = "Starts automatically".takeIf { settings.native?.startAtLogin == true },
    )
}
