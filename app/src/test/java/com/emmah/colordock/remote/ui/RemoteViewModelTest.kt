package com.emmah.colordock.remote.ui

import com.emmah.colordock.remote.tv.TvCommand
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteViewModelTest {
    @Test fun cancelStopsEveryOverlappingInitialSendWithoutLosingShortTaps() =
        runTest {
            val started = mutableListOf<TvCommand>()
            val completed = mutableListOf<TvCommand>()
            val repeats =
                RepeatCommands(this) {
                    started += it
                    delay(1000)
                    completed += it
                }
            for (command in listOf(TvCommand.Back, TvCommand.Home, TvCommand.Ok)) {
                repeats.press(command)
                runCurrent()
                advanceTimeBy(100)
            }
            repeats.cancel()
            advanceTimeBy(2000)
            runCurrent()
            assertEquals(listOf(TvCommand.Back, TvCommand.Home, TvCommand.Ok), started)
            assertTrue(completed.isEmpty())
        }

    @Test fun holdsRepeatAt450Then180MillisecondsAndStopOnRelease() =
        runTest {
            val sent = mutableListOf<TvCommand>()
            val repeats =
                RepeatCommands(this) {
                    sent += it
                }
            repeats.press(TvCommand.Up)
            runCurrent()
            assertEquals(listOf(TvCommand.Up), sent)
            advanceTimeBy(449)
            runCurrent()
            assertEquals(1, sent.size)
            advanceTimeBy(1)
            runCurrent()
            assertEquals(2, sent.size)
            advanceTimeBy(180)
            runCurrent()
            assertEquals(3, sent.size)
            repeats.release()
            advanceTimeBy(2000)
            runCurrent()
            assertEquals(3, sent.size)
        }

    @Test fun okBackHomeMuteAndPowerNeverRepeat() =
        runTest {
            for (command in listOf(TvCommand.Ok, TvCommand.Back, TvCommand.Home, TvCommand.Mute, TvCommand.PowerOff)) {
                val sent = mutableListOf<TvCommand>()
                val repeats =
                    RepeatCommands(this) {
                        sent += it
                    }
                repeats.press(command)
                runCurrent()
                advanceTimeBy(2000)
                runCurrent()
                assertEquals(listOf(command), sent)
                repeats.cancel()
            }
        }

    @Test fun slowSendsDoNotBuildARepeatBacklogAndCancellationStopsPendingWork() =
        runTest {
            var started = 0
            var completed = 0
            val repeats =
                RepeatCommands(this) {
                    started++
                    delay(1000)
                    completed++
                }
            repeats.press(TvCommand.VolumeUp)
            runCurrent()
            advanceTimeBy(999)
            runCurrent()
            assertEquals(2, started)
            assertEquals(0, completed)
            repeats.cancel()
            advanceTimeBy(5000)
            runCurrent()
            assertEquals(2, started)
            assertEquals(0, completed)
        }
}
