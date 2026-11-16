package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.emmah.colordock.remote.mediacenter.protocol.ErrorPayload
import com.emmah.colordock.remote.mediacenter.protocol.McReply
import com.emmah.colordock.remote.mediacenter.protocol.McRequest
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand
import com.emmah.colordock.remote.mediacenter.protocol.SettingsThemeSetParams
import com.emmah.colordock.remote.mediacenter.protocol.Theme
import com.emmah.colordock.remote.mediacenter.protocol.WireErrorCode
import com.emmah.colordock.remote.ui.RemoteTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlainFeedbackTest {
    @get:Rule val compose = createComposeRule()

    @Test fun backendFailureShowsAnOrdinaryNoticeWithoutLeakingTechnicalText() {
        lateinit var media: MediaSessionController
        val requests = mutableListOf<McRequest>()
        val detail = "native server_epoch=internal-session socket rejected"
        compose.setContent {
            val scope = rememberCoroutineScope()
            val controllers =
                remember {
                    val send: (McRequest) -> Boolean = { requests.add(it) }
                    MediaTargetControllers(
                        MediaSessionController(scope, send).apply {
                            observe(phoneMediaSnapshot())
                            destination(MediaDestination.Settings)
                            media = this
                        },
                        LibraryViewModel(scope, send),
                        RendererController(scope, send),
                        AudioSettingsController(send),
                    )
                }
            val state by controllers.media.state.collectAsState()
            RemoteTheme {
                MediacenterTargetContent(
                    MediaTargetState(state, LibraryState(), AudioSettingsState()),
                    controllers,
                    { null },
                )
            }
        }
        compose.runOnIdle {
            media.command(RemoteCommand.SettingsThemeSet(SettingsThemeSetParams(Theme.LIGHT)))
            media.receive(
                McReply.Failure(requests.last().id, ErrorPayload(WireErrorCode.BUSY, detail, true, emptyList())),
            )
        }
        compose.onNodeWithContentDescription("Action needs attention").performClick()
        compose.onNodeWithText(detail).assertDoesNotExist()
        compose.onNodeWithText("Try again").assertIsDisplayed()
    }
}
