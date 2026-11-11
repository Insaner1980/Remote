package com.emmah.colordock.remote.mediacenter

import com.emmah.colordock.remote.mediacenter.protocol.Empty
import com.emmah.colordock.remote.mediacenter.protocol.Expected
import com.emmah.colordock.remote.mediacenter.protocol.McRequest
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReconnectTest {
    @Test fun retriesForegroundAtOneTwoFiveTenTenSecondsThenRequiresManualRetry() =
        runTest {
            val control = RecoveryControl()
            val repository = MediacenterRepository(control, backgroundScope, MemoryPending()) { 0.5 }
            repository.connect(recoverySaved(), RecoveryRoute())
            runCurrent()
            for (delay in listOf(1000L, 2000L, 5000L, 10000L, 10000L)) {
                val before = control.connections.size
                control.state.value = MediacenterConnectionState.Failed
                runCurrent()
                advanceTimeBy(delay - 1)
                runCurrent()
                assertEquals(before, control.connections.size)
                advanceTimeBy(1)
                runCurrent()
                assertEquals(before + 1, control.connections.size)
            }
            control.state.value = MediacenterConnectionState.Failed
            runCurrent()
            advanceTimeBy(60000)
            runCurrent()
            assertEquals(6, control.connections.size)
            assertEquals(MediacenterConnectionState.Failed, repository.state.value)
            repository.retry()
            runCurrent()
            assertEquals(7, control.connections.size)
        }

    @Test fun backgroundStopsMutationsImmediatelyAndClosesOnlyAfterFiveSeconds() =
        runTest {
            val control = RecoveryControl()
            val repository = MediacenterRepository(control, backgroundScope, MemoryPending()) { 0.5 }
            repository.connect(recoverySaved(), RecoveryRoute())
            runCurrent()
            control.native()
            runCurrent()
            assertNotNull(repository.snapshot.value)
            val before = control.disconnects
            val targets =
                com.emmah.colordock.remote.ui
                    .ControlTargetViewModel()
            val oldLease = targets.lease()
            var cancelled = 0
            targets.background({ cancelled++ }, repository::background)
            assertEquals(1, cancelled)
            assertFalse(targets.dispatch(oldLease) { throw AssertionError("Retired input resumed") })
            runCurrent()
            assertFalse(
                repository.send(
                    McRequest(
                        "next",
                        RemoteCommand.QueueNext(Empty),
                        Expected(serverEpoch = "epoch", sessionId = "session", queueRevision = "0"),
                    ),
                ),
            )
            advanceTimeBy(4999)
            runCurrent()
            assertEquals(before, control.disconnects)
            repository.foreground()
            runCurrent()
            advanceTimeBy(1)
            runCurrent()
            assertEquals(before, control.disconnects)
            assertNull(repository.snapshot.value)
            repository.background()
            runCurrent()
            advanceTimeBy(5000)
            runCurrent()
            assertTrue(control.disconnects > before)
            repository.foreground()
            runCurrent()
            assertEquals(2, control.connections.size)
        }

    @Test fun newAddressAndWifiSelectionReplaceSessionWithoutChangingStoredIdentity() =
        runTest {
            val control = RecoveryControl()
            val repository = MediacenterRepository(control, backgroundScope, MemoryPending()) { 0.5 }
            val first = RecoveryRoute()
            val second = RecoveryRoute()
            repository.connect(recoverySaved(), first)
            runCurrent()
            control.native()
            runCurrent()
            repository.connect(recoverySaved("10.0.0.4"), second)
            runCurrent()
            assertNull(repository.snapshot.value)
            assertSame(second, control.connections.last().second)
            assertEquals(
                "ab".repeat(32),
                control.connections
                    .last()
                    .first.paired.certificateFingerprint,
            )
            control.emit(RecoveryFixture.snapshot())
            runCurrent()
            assertNull(repository.snapshot.value)
            control.native("new-epoch")
            runCurrent()
            assertEquals("new-epoch", repository.snapshot.value!!.serverEpoch)
        }

    @Test fun backgroundImmediatelyMarksNativeTruthStaleDuringSocketGrace() =
        runTest {
            val control = RecoveryControl()
            val repository = MediacenterRepository(control, backgroundScope, MemoryPending())
            repository.connect(recoverySaved(), RecoveryRoute())
            runCurrent()
            control.native()
            runCurrent()
            assertNotNull(repository.snapshot.value)
            val before = control.disconnects
            repository.background()
            runCurrent()
            assertNull(repository.snapshot.value)
            assertFalse(repository.state.value == MediacenterConnectionState.Connected)
            assertEquals(before, control.disconnects)
        }

    @Test fun revokedCredentialsStopRetriesAndConnectedAdmissionIsNotReadiness() =
        runTest {
            val control = RecoveryControl()
            val repository = MediacenterRepository(control, backgroundScope, MemoryPending()) { 0.5 }
            repository.connect(recoverySaved(), RecoveryRoute())
            runCurrent()
            control.state.value = MediacenterConnectionState.Connected
            runCurrent()
            assertFalse(
                repository.send(
                    McRequest(
                        "next",
                        RemoteCommand.QueueNext(Empty),
                        Expected(serverEpoch = "epoch", sessionId = "session", queueRevision = "0"),
                    ),
                ),
            )
            control.state.value = MediacenterConnectionState.PairingRequired
            runCurrent()
            advanceTimeBy(60000)
            runCurrent()
            assertEquals(1, control.connections.size)
            assertEquals(MediacenterConnectionState.PairingRequired, repository.state.value)
        }

    @Test fun pairingRequiredSurvivesBackgroundGraceWithoutAutomaticAuthentication() =
        preservesStopState(MediacenterConnectionState.PairingRequired)

    @Test fun identityChangedSurvivesBackgroundGraceWithoutAutomaticAuthentication() =
        preservesStopState(MediacenterConnectionState.IdentityChanged)

    @Test fun versionMismatchSurvivesBackgroundGraceWithoutAutomaticAuthentication() =
        preservesStopState(MediacenterConnectionState.VersionMismatch)

    @Test fun permissionRequiredSurvivesBackgroundGraceWithoutAutomaticAuthentication() =
        preservesStopState(MediacenterConnectionState.PermissionRequired)

    private fun preservesStopState(reason: MediacenterConnectionState) =
        runTest {
            val control = RecoveryControl()
            val repository = MediacenterRepository(control, backgroundScope, MemoryPending()) { 0.5 }
            repository.connect(recoverySaved(), RecoveryRoute())
            runCurrent()
            control.state.value = reason
            runCurrent()
            assertEquals(reason, repository.state.value)
            val before = control.connections.size
            repository.background()
            runCurrent()
            advanceTimeBy(5001)
            runCurrent()
            assertEquals(reason, repository.state.value)
            repository.foreground()
            runCurrent()
            advanceTimeBy(30000)
            runCurrent()
            assertEquals(reason, repository.state.value)
            assertEquals(before, control.connections.size)
            assertNull(repository.snapshot.value)
        }
}
