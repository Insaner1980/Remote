package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.emmah.colordock.remote.mediacenter.PendingCommand
import com.emmah.colordock.remote.ui.RemoteTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaAttentionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun uncertainInputRemainsAvailableWithoutAParagraphOnEveryPage() {
        compose.setContent { RemoteTheme { MediaRecoveryStatus(emptyList(), true, true) } }
        val detail = "Input outcome is uncertain. Inspect the computer before sending again."
        compose.onNodeWithText(detail).assertDoesNotExist()
        compose.onNodeWithContentDescription("Action needs attention").assertIsDisplayed().performClick()
        compose.onNodeWithText(detail).assertDoesNotExist()
        compose.onNodeWithText("Not confirmed").assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithContentDescription("Action needs attention").assertIsDisplayed()
    }

    @Test fun combinedRecoveryDetailsCanBeReadAtLargeTextInLandscape() {
        val records =
            listOf(
                PendingCommand("server", "pending", "epoch"),
                PendingCommand("server", "unknown", "epoch", resolved = true),
            )
        compose.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(640.dp, 360.dp)) then
                    DeviceConfigurationOverride.FontScale(2f),
            ) {
                RemoteTheme { MediaRecoveryStatus(records, true, false, notice = ActionNotice.RETRY) }
            }
        }
        compose.onNodeWithContentDescription("Action needs attention").performClick()
        compose.onNodeWithText("Not confirmed").assertIsDisplayed()
        compose
            .onNodeWithText(
                "Command recovery is unavailable. Reconnect after freeing phone storage.",
            ).assertDoesNotExist()
        compose.onNodeWithText("Close").assertIsDisplayed().performClick()
    }

    @Test fun pendingActionStaysVisibleAcrossDestinationsAndDisappearsWhenFinished() {
        val state = mutableStateOf(MediaSessionState(busy = true, message = "Waiting for Mediacenter…"))
        compose.setContent {
            RemoteTheme {
                InlineMediaStatus(MediaTargetState(state.value, LibraryState(), AudioSettingsState()), null)
            }
        }
        MediaDestination.entries.forEach { destination ->
            compose.runOnIdle { state.value = state.value.copy(destination = destination) }
            compose.onNodeWithTag("media-action-progress").assertIsDisplayed()
        }
        compose.runOnIdle { state.value = state.value.copy(busy = false, message = "") }
        compose.onNodeWithTag("media-action-progress").assertDoesNotExist()
    }
}
