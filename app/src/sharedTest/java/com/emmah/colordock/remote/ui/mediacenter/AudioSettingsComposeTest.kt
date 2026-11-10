package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.emmah.colordock.remote.mediacenter.protocol.AudioOutput
import com.emmah.colordock.remote.mediacenter.protocol.OutputsTopic
import com.emmah.colordock.remote.ui.RemoteTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AudioSettingsComposeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun soundIsOptionalShowsRequestedAndConfirmedSeparatelyAndDisablesMutationWhenDisconnected() {
        val native =
            OutputsTopic(
                "2",
                "hdmi",
                "laptop",
                false,
                listOf(AudioOutput("hdmi", "TV", true), AudioOutput("laptop", "Laptop speakers", true)),
            )
        val state = mutableStateOf(AudioSettingsState(outputs = native))
        var reads = 0
        var mutations = 0
        val actions =
            SoundActions(
                { reads++ },
                { mutations++ },
                { mutations++ },
                { mutations++ },
                { mutations++ },
                { mutations++ },
            )
        compose.setContent { RemoteTheme { SoundOptions(state.value, actions) } }
        compose.waitForIdle()
        compose.onNodeWithText("Requested: TV").assertDoesNotExist()
        compose.onNodeWithText("Sound").performClick()
        compose
            .onNodeWithText(
                "Output selection is optional. Existing sound continues without setup changes.",
            ).assertIsDisplayed()
        compose.onNodeWithText("Requested: TV").assertIsDisplayed()
        compose.onNodeWithText("Confirmed: Laptop speakers").assertIsDisplayed()
        compose.runOnIdle { state.value = AudioSettingsState() }
        compose.onNodeWithText("Requested: Not confirmed").assertIsDisplayed()
        compose.onNodeWithText("Confirmed: Not confirmed").assertIsDisplayed()
        compose.onNodeWithText("Retry").assertIsNotEnabled()
        compose.onAllNodesWithText("Test sound").assertCountEquals(0)
        compose.runOnIdle {
            assertEquals(1, reads)
            assertEquals(0, mutations)
        }
    }
}
