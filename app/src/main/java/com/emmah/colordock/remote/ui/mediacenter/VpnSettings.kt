package com.emmah.colordock.remote.ui.mediacenter

import com.emmah.colordock.remote.mediacenter.protocol.ActivityPreparation
import com.emmah.colordock.remote.mediacenter.protocol.DeviceList
import com.emmah.colordock.remote.mediacenter.protocol.Empty
import com.emmah.colordock.remote.mediacenter.protocol.PairingPreparation
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand
import com.emmah.colordock.remote.mediacenter.protocol.RemoteResult
import com.emmah.colordock.remote.mediacenter.protocol.SettingsState
import com.emmah.colordock.remote.mediacenter.protocol.VpnCountry
import com.emmah.colordock.remote.mediacenter.protocol.VpnTopic
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PAIRING_DISPLAY_MS = 120_000L

internal data class RoutineSettingsState(
    val native: SettingsState? = null,
    val vpn: VpnTopic? = null,
    val countries: List<VpnCountry> = emptyList(),
    val devices: DeviceList? = null,
    val pairing: PairingDisplayState? = null,
)

internal fun MediaSessionController.refreshSettings() {
    submit(RemoteCommand.SettingsGet(Empty))
    submit(RemoteCommand.VpnState(Empty))
    submit(RemoteCommand.VpnCountries(Empty))
    submit(RemoteCommand.DevicesList(Empty))
}

internal fun MediaSessionController.receiveSettingsResult(result: RemoteResult): Boolean {
    val current = state.value.settings
    when (result) {
        is RemoteResult.SettingsGet -> {
            updateSettings(current.copy(native = result.value))
        }

        is RemoteResult.VpnState -> {
            updateSettings(current.copy(vpn = result.value))
            submit(RemoteCommand.SettingsGet(Empty))
        }

        is RemoteResult.VpnCountries -> {
            updateSettings(current.copy(countries = result.value.items))
        }

        is RemoteResult.DevicesList -> {
            updateSettings(current.copy(devices = result.value))
        }

        is RemoteResult.VpnPrepareChange -> {
            receivePreparation(result.value)
        }

        is RemoteResult.VpnConfirmChange -> {
            mutable.value = state.value.copy(preview = null)
            reconcileOperation()
        }

        is RemoteResult.DevicesRevoke -> {
            receiveDevicePreparation(result.value)
        }

        is RemoteResult.PairingBegin -> {
            receivePairing(result.value)
        }

        else -> {
            return false
        }
    }
    return true
}

private fun MediaSessionController.updateSettings(value: RoutineSettingsState) {
    mutable.value = state.value.copy(settings = value)
}

private fun MediaSessionController.receiveDevicePreparation(value: ActivityPreparation) {
    if (value is ActivityPreparation.ConfirmationRequired) {
        receivePreparation(value)
    } else {
        mutable.value = state.value.copy(preview = null)
        reconcileOperation()
    }
}

private fun MediaSessionController.receivePairing(value: PairingPreparation) {
    operationRequest = null
    mutable.value = state.value.copy(busy = false)
    when (value) {
        is PairingPreparation.ConfirmationRequired -> {
            mutable.value = state.value.copy(preview = value.preview)
        }

        is PairingPreparation.Ready -> {
            if (state.value.destination != MediaDestination.Settings) {
                message("Pairing code hidden after leaving Settings. The original request will not be repeated.")
                mutable.value = state.value.copy(preview = null)
                return
            }
            mutable.value =
                state.value.copy(
                    preview = null,
                    settings =
                        state.value.settings.copy(
                            pairing = PairingDisplayState(value.candidate.attemptId, value.qrRows),
                        ),
                )
            val attempt = value.candidate.attemptId
            scope.launch {
                delay(PAIRING_DISPLAY_MS)
                if (state.value.settings.pairing
                        ?.attemptId == attempt
                ) {
                    dismissPairing()
                }
            }
        }

        is PairingPreparation.AlreadyIssued -> {
            mutable.value = state.value.copy(preview = null)
            message("The pairing code was already issued. It will not be issued again by this request.")
        }
    }
}

internal fun MediaSessionController.dismissPairing() {
    updateSettings(state.value.settings.copy(pairing = null))
}

internal fun isSettingsChange(command: RemoteCommand?): Boolean =
    command is RemoteCommand.VpnConfirmChange || command is RemoteCommand.VpnRequirementSet ||
        command is RemoteCommand.SettingsThemeSet || command is RemoteCommand.DevicesRevoke
