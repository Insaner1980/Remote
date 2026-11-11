package com.emmah.colordock.remote.ui

import com.emmah.colordock.remote.mediacenter.MediacenterConnectionState
import com.emmah.colordock.remote.tv.TvCommand
import com.emmah.colordock.remote.tv.TvConnectionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ControlTargetViewModelTest {
    @Test fun connectionsGateOnlyTheirOwnTarget() {
        val targets = ControlTargetViewModel()
        targets.observe(TvConnectionState.Disconnected, MediacenterConnectionState.Connected)
        var tvCommands = 0
        var pcCommands = 0
        assertFalse(targets.dispatch(targets.lease()) { tvCommands++ })
        targets.select(ControlTarget.Mediacenter) {}
        assertTrue(targets.dispatch(targets.lease()) { pcCommands++ })
        targets.observe(TvConnectionState.Ready, MediacenterConnectionState.Disconnected)
        assertFalse(targets.dispatch(targets.lease()) { pcCommands++ })
        targets.select(ControlTarget.LgTv) {}
        assertTrue(targets.dispatch(targets.lease()) { tvCommands++ })
        assertEquals(1, tvCommands)
        assertEquals(1, pcCommands)
    }

    @Test fun heldAndQueuedTvInputStopsBeforeSelectionAndNeverBecomesPcInput() =
        runTest {
            val targets = ControlTargetViewModel()
            targets.observe(TvConnectionState.Ready, MediacenterConnectionState.Connected)
            val lease = targets.lease()
            val sent = mutableListOf<TvCommand>()
            val repeats = RepeatCommands(backgroundScope) { sent.add(it) }
            targets.dispatch(lease) { repeats.press(TvCommand.VolumeUp) }
            runCurrent()
            assertEquals(listOf(TvCommand.VolumeUp), sent)
            targets.select(ControlTarget.Mediacenter) {
                assertEquals(ControlTarget.LgTv, targets.state.value.selected)
                repeats.cancel()
            }
            advanceTimeBy(2000)
            runCurrent()
            assertFalse(targets.dispatch(lease) { repeats.press(TvCommand.PowerOff) })
            targets.select(ControlTarget.LgTv) { repeats.cancel() }
            assertFalse(targets.dispatch(lease) { repeats.press(TvCommand.PowerOff) })
            assertEquals(listOf(TvCommand.VolumeUp), sent)
            assertTrue(targets.state.value.tvReady)
            assertTrue(targets.state.value.mediacenterReady)
        }

    @Test fun blockingOverlayRetiresOnlyCurrentInputContextBeforeOpening() {
        val targets = ControlTargetViewModel()
        targets.observe(TvConnectionState.Ready, MediacenterConnectionState.Connected)
        targets.select(ControlTarget.Mediacenter) {}
        val origin = targets.lease()
        var cancelled = 0
        var staleCallbacks = 0
        assertTrue(
            targets.retireInput(origin) {
                assertEquals(origin, targets.lease())
                cancelled++
            },
        )
        assertEquals(ControlTarget.Mediacenter, targets.state.value.selected)
        assertEquals(origin.generation + 1, targets.state.value.generation)
        assertFalse(targets.dispatch(origin) { staleCallbacks++ })
        assertFalse(targets.retireInput(origin) { staleCallbacks++ })
        assertTrue(targets.dispatch(targets.lease()) {})
        assertTrue(targets.state.value.tvReady)
        assertTrue(targets.state.value.mediacenterReady)
        assertEquals(1, cancelled)
        var opened = 0
        targets.openContext(origin, { staleCallbacks++ }) { opened++ }
        assertEquals(0, opened)
        targets.openContext(targets.lease(), { cancelled++ }) { opened++ }
        assertEquals(1, opened)
        assertEquals(2, cancelled)
        assertEquals(0, staleCallbacks)
    }
}
