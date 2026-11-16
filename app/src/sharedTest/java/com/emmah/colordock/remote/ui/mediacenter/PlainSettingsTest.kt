package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.emmah.colordock.remote.mediacenter.protocol.AudioOutput
import com.emmah.colordock.remote.mediacenter.protocol.Availability
import com.emmah.colordock.remote.mediacenter.protocol.MediaKind
import com.emmah.colordock.remote.mediacenter.protocol.OutputsTopic
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand
import com.emmah.colordock.remote.mediacenter.protocol.SettingsState
import com.emmah.colordock.remote.mediacenter.protocol.Source
import com.emmah.colordock.remote.mediacenter.protocol.Theme
import com.emmah.colordock.remote.ui.RemoteTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlainSettingsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun advancedContainsOrdinaryControlsWithoutDiagnosticsOrHelp() {
        val sent = mutableListOf<RemoteCommand>()
        compose.setContent {
            RemoteTheme { SettingsScreen(state(), RoutineSettingsActions({ sent.add(it) }, {}, {})) }
        }
        compose.onNodeWithText("nordvpn --version").assertDoesNotExist()
        compose.onNodeWithText("Phone connection: connected").assertDoesNotExist()
        compose.onNodeWithText("Viewing idle inhibition: unavailable").assertDoesNotExist()
        compose.onNodeWithContentDescription("Start automatically").performClick()
        compose.onNodeWithText("Stop playback").performClick()
        assertTrue(sent.first() is RemoteCommand.SettingsStartAtLoginSet)
        assertTrue(sent.last() is RemoteCommand.SettingsEndSession)
    }

    @Test fun soundShowsOnePlainOutputAndTestsItWithoutChangingPreference() {
        val chosen = mutableListOf<String>()
        val tested = mutableListOf<String>()
        val outputs =
            OutputsTopic(
                "1",
                "hdmi",
                null,
                false,
                listOf(AudioOutput("hdmi", "Built-in Audio Digital Stereo (HDMI)", true)),
            )
        compose.setContent {
            RemoteTheme {
                SoundOptions(
                    AudioSettingsState(outputs = outputs),
                    SoundActions({}, {}, { chosen.add(it) }, { tested.add(it) }, {}, {}),
                    initiallyOpen = true,
                )
            }
        }
        compose.onNodeWithText("TV (HDMI)").assertIsDisplayed()
        compose.onNodeWithText("Confirmed: Not confirmed").assertDoesNotExist()
        compose.onNodeWithText("Test sound").performClick()
        assertEquals(listOf("hdmi"), tested)
        assertTrue(chosen.isEmpty())
        compose.onNodeWithText("TV (HDMI)").performClick()
        assertEquals(listOf("hdmi"), chosen)
    }

    @Test fun vpnKeepsActionsWithoutConnectionLectureOrKillSwitchDump() {
        val sent = mutableListOf<RemoteCommand>()
        compose.setContent { RemoteTheme { VpnSettings(state().settings, true, { sent.add(it) }) } }
        compose.onNodeWithText("Kill Switch: disabled").assertDoesNotExist()
        compose
            .onNodeWithText(
                "VPN changes can interrupt streaming and this phone connection. Review the confirmation first.",
            ).assertDoesNotExist()
        compose.onNodeWithText("Connect").performClick()
        assertTrue(sent.single() is RemoteCommand.VpnPrepareChange)
    }

    @Test fun folderSecondaryMenuPreservesIdentityWithoutActingOnOpen() {
        val scans = mutableListOf<String>()
        val removed = mutableListOf<String>()
        val source = Source("folder-id", "Sample folder", listOf(MediaKind.VIDEO), Availability.AVAILABLE, false)
        val actions =
            SourceActions(
                {},
                {},
                { scans.add(it) },
                { removed.add(it) },
                null,
                FolderActions({}, {}, {}, { _, _ -> }),
            )
        compose.setContent {
            RemoteTheme {
                SourcesScreen(
                    SourcesState(items = listOf(source), connected = true, message = ""),
                    FolderState(),
                    false,
                    actions,
                )
            }
        }
        compose.onNodeWithText("Remove Sample folder association").assertDoesNotExist()
        compose.onNodeWithContentDescription("Folder options for Sample folder").performClick()
        assertTrue(scans.isEmpty() && removed.isEmpty())
        compose.onNodeWithText("Update").performClick()
        assertEquals(listOf("folder-id"), scans)
        compose.onNodeWithContentDescription("Folder options for Sample folder").performClick()
        compose.onNodeWithText("Remove").performClick()
        assertEquals(listOf("folder-id"), removed)
    }

    @Test fun multipleSoundPortsKeepDistinctNamesAndNeverRenameHeadphonesAsSpeakers() {
        var chosen = ""
        val outputs =
            OutputsTopic(
                "1",
                "one",
                null,
                false,
                listOf(
                    AudioOutput("one", "Built-in Audio Digital Stereo (HDMI)", true),
                    AudioOutput("two", "Built-in Audio Digital Stereo (HDMI)", true),
                    AudioOutput("phones", "Built-in Audio Headphones", true),
                ),
            )
        compose.setContent {
            RemoteTheme {
                SoundOptions(
                    AudioSettingsState(outputs = outputs),
                    SoundActions({}, {}, { chosen = it }, {}, {}, {}),
                    initiallyOpen = true,
                )
            }
        }
        compose.onNodeWithText("TV (HDMI 1)").assertIsDisplayed()
        compose.onNodeWithText("TV (HDMI 2)").performClick()
        assertEquals("two", chosen)
        compose.onNodeWithText("Headphones").assertIsDisplayed()
    }

    @Test fun uncertainSoundOutcomeIsNotTurnedIntoRetry() {
        val sent = mutableListOf<com.emmah.colordock.remote.mediacenter.protocol.McRequest>()
        val controller = AudioSettingsController { sent.add(it) }
        val outputs = OutputsTopic("1", "hdmi", null, false, listOf(AudioOutput("hdmi", "TV", true)))
        controller.observe("epoch", outputs)
        controller.choose("hdmi")
        controller.observe(
            "epoch",
            outputs,
            listOf(
                com.emmah.colordock.remote.mediacenter.protocol.OperationState(
                    "op",
                    sent.last().id,
                    com.emmah.colordock.remote.mediacenter.protocol.OperationStatus.OUTCOME_UNKNOWN,
                    cancellationRequested = false,
                ),
            ),
        )
        compose.setContent {
            RemoteTheme {
                SoundOptions(
                    controller.state.collectAsState().value,
                    SoundActions({}, {}, {}, {}, {}, {}),
                    initiallyOpen = true,
                )
            }
        }
        compose.onNodeWithText("Not confirmed").assertIsDisplayed()
        compose.onNodeWithText("Try again").assertDoesNotExist()
    }

    @Test fun playbackVolumeRemainsIdentifiable() {
        compose.setContent {
            RemoteTheme {
                NowPlayingControls(
                    MediaSessionState(snapshot = phoneMediaSnapshot()),
                    {},
                    {},
                )
            }
        }
        compose.onNodeWithContentDescription("Volume").assertExists()
    }

    private fun state() =
        MediaSessionState(
            snapshot = phoneMediaSnapshot(),
            settings =
                RoutineSettingsState(native = SettingsState("1", Theme.DARK, true), vpn = phoneMediaSnapshot().vpn),
        )
}
