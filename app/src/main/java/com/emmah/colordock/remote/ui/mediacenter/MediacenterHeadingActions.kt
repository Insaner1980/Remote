package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.runtime.Composable

@Composable
internal fun MediacenterHeadingActions(
    model: MediaTargetState,
    controllers: MediaTargetControllers,
    section: SettingsSection?,
) {
    val state = model.media
    when (state.destination) {
        MediaDestination.Remote -> {
            RemoteQueueAction(state, { controllers.media.queue(true) })
        }

        MediaDestination.Library -> {
            RefreshAction(
                controllers.library::refresh,
                enabled = model.library.connected && !model.library.loading,
                label = "Refresh library",
            )
        }

        MediaDestination.Settings -> {
            when (section) {
                SettingsSection.Sound -> {
                    RefreshAction(
                        { controllers.audio.retry(true) },
                        enabled = !model.sound.busy,
                        label = "Refresh sound outputs",
                    )
                }

                SettingsSection.Advanced -> {
                    RefreshAction(
                        controllers.media::refreshSettings,
                        enabled = state.snapshot != null,
                    )
                }

                else -> {
                    Unit
                }
            }
        }
    }
}
