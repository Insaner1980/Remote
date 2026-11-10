package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand
import com.emmah.colordock.remote.ui.RemoteTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NowPlayingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun realTransportUsesCurrentSessionAndDisconnectDisablesIt() {
        val state = mutableStateOf(MediaSessionState(snapshot = phoneMediaSnapshot()))
        val sent = mutableListOf<RemoteCommand>()
        compose.setContent {
            RemoteTheme {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                ) { NowPlayingControls(state.value, { sent.add(it) }, {}) }
            }
        }
        compose.onNodeWithText("Pause").performClick()
        assertEquals("session", (sent.single() as RemoteCommand.PlayerPause).params.sessionId)
        compose.onNodeWithText("Mute Mediacenter").performScrollTo().performClick()
        assertTrue((sent.last() as RemoteCommand.PlayerMute).params.muted)
        compose.runOnIdle { state.value = MediaSessionState() }
        compose.onNodeWithText("Pause").performScrollTo().assertIsNotEnabled()
    }
}
