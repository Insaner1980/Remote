package com.emmah.colordock.remote.ui

import androidx.lifecycle.ViewModel
import com.emmah.colordock.remote.mediacenter.MediacenterConnectionState
import com.emmah.colordock.remote.tv.TvConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Selecting a target does not disconnect either session or change accepted repository work. */
class ControlTargetViewModel : ViewModel() {
    private val mutable = MutableStateFlow(ControlTargetState())
    internal val state = mutable.asStateFlow()

    internal fun observe(
        tv: TvConnectionState,
        mediacenter: MediacenterConnectionState,
    ) {
        mutable.value =
            state.value.copy(
                tvReady = tv == TvConnectionState.Ready,
                mediacenterReady = mediacenter == MediacenterConnectionState.Connected,
            )
    }

    internal fun select(
        target: ControlTarget,
        cancelInput: () -> Unit,
    ) {
        if (target == state.value.selected) return
        cancelInput()
        mutable.value = state.value.copy(selected = target, generation = state.value.generation + 1)
    }

    /** Blocking overlays retire input and volatile UI while leaving both sessions and journals alone. */
    internal fun retireInput(
        origin: ControlTargetLease,
        cancelInput: () -> Unit,
    ): Boolean {
        if (origin != lease()) return false
        cancelInput()
        mutable.value = state.value.copy(generation = state.value.generation + 1)
        return true
    }

    internal fun openContext(
        origin: ControlTargetLease,
        cancelInput: () -> Unit,
        open: () -> Unit,
    ) {
        if (retireInput(origin, cancelInput)) open()
    }

    /** Activity stop retires input before the repository starts its existing socket grace. */
    internal fun background(
        cancelInput: () -> Unit,
        background: () -> Unit,
    ) {
        retireInput(lease(), cancelInput)
        background()
    }

    internal fun lease() = ControlTargetLease(state.value.selected, state.value.generation)

    internal fun dispatch(
        lease: ControlTargetLease,
        action: () -> Unit,
    ): Boolean {
        val current = state.value
        if (lease.target != current.selected || lease.generation != current.generation ||
            !current.ready(lease.target)
        ) {
            return false
        }
        action()
        return true
    }
}
