package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.emmah.colordock.remote.mediacenter.protocol.ActivityMode
import com.emmah.colordock.remote.mediacenter.protocol.BrowserCapabilities
import com.emmah.colordock.remote.mediacenter.protocol.BrowserState
import com.emmah.colordock.remote.mediacenter.protocol.Capabilities
import com.emmah.colordock.remote.mediacenter.protocol.ForegroundAck
import com.emmah.colordock.remote.mediacenter.protocol.McReply
import com.emmah.colordock.remote.mediacenter.protocol.McRequest
import com.emmah.colordock.remote.mediacenter.protocol.MethodName
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand
import com.emmah.colordock.remote.mediacenter.protocol.RemoteResult
import com.emmah.colordock.remote.mediacenter.protocol.ServiceCapability
import com.emmah.colordock.remote.mediacenter.protocol.SupportedState
import com.emmah.colordock.remote.ui.RemoteTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun browserReplacesNativeTransportAndBridgeLossRetiresTheKeyboard() {
        val streaming = mutableStateOf(activeStreaming())
        compose.setContent {
            RemoteTheme {
                BrowserTargetForTest(streaming.value) { true }
            }
        }
        compose.onAllNodesWithTag("transport-controls").assertCountEquals(0)
        compose.onAllNodesWithTag("browser-touchpad").assertCountEquals(1)
        compose.onNodeWithText("Keyboard").performScrollTo().performClick()
        compose.onNodeWithText("Type text").assertExists()
        compose.onNodeWithContentDescription("Backspace").assertExists()
        compose.runOnIdle { streaming.value = streaming.value.copy(target = null, browser = null) }
        compose.onNodeWithText("Type text").assertDoesNotExist()
        compose.onAllNodesWithTag("browser-touchpad").assertCountEquals(0)
        compose.onAllNodesWithTag("transport-controls").assertCountEquals(0)
    }

    @Test fun disabledStreamingTileKeepsTheArtworkBackplateOpaque() {
        compose.setContent { RemoteTheme { StreamingServices(activeStreaming(), false, {}) } }
        val artwork = compose.onNodeWithContentDescription("Open YouTube").captureToImage().toPixelMap()
        assertEquals(Color.White, artwork[artwork.width / 2, artwork.height / 8])
    }

    @Test fun showMenuAndReturnUseTheirOwnAcknowledgedContexts() {
        val sent = mutableListOf<McRequest>()
        lateinit var media: MediaSessionController
        compose.setContent {
            RemoteTheme {
                BrowserTargetForTest(activeStreaming(), onMedia = { media = it }) { sent.add(it) }
            }
        }
        compose.onNodeWithText("Menu").performScrollTo().performClick()
        compose.onAllNodesWithTag("browser-touchpad").assertCountEquals(1)
        compose.runOnIdle {
            val request = sent.last()
            media.receive(McReply.Success(request.id, RemoteResult.ActivityShowMenu(ForegroundAck(request.id))))
        }
        compose.onAllNodesWithTag("browser-touchpad").assertCountEquals(0)
        compose.onNodeWithText("Playback").performScrollTo().performClick()
        compose.runOnIdle {
            val request = sent.last()
            assertTrue(request.command is RemoteCommand.ActivityShowPlayback)
            media.receive(McReply.Success(request.id, RemoteResult.ActivityShowPlayback(ForegroundAck(request.id))))
        }
        compose.onAllNodesWithTag("browser-touchpad").assertCountEquals(1)
    }

    @Test fun unavailableCatalogEntriesExposeTheirLimitationAndCannotLaunch() {
        val sent = mutableListOf<McRequest>()
        compose.setContent {
            RemoteTheme { BrowserTargetForTest(activeStreaming()) { sent.add(it) } }
        }
        compose.onNodeWithContentDescription("Open HBO Max").assertIsNotEnabled()
        compose.onNodeWithText("Open the enrolled Chrome profile.").assertDoesNotExist()
        compose.onNodeWithContentDescription("Open YouTube").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(sent.single().command is RemoteCommand.BrowserOpen) }
    }
}

@Composable
private fun BrowserTargetForTest(
    streaming: StreamingState,
    modifier: Modifier = Modifier,
    onMedia: (MediaSessionController) -> Unit = {},
    send: (McRequest) -> Boolean,
) {
    val scope = rememberCoroutineScope()
    val media =
        remember {
            MediaSessionController(scope, send).apply {
                val snapshot = phoneMediaSnapshot()
                observe(
                    snapshot.copy(activity = snapshot.activity.copy(mode = ActivityMode.BROWSER, sessionId = "web")),
                )
            }
        }
    SideEffect { onMedia(media) }
    val controllers =
        remember {
            MediaTargetControllers(
                media,
                LibraryViewModel(scope, send),
                RendererController(scope, send),
                AudioSettingsController(send),
                browser = BrowserInputController(scope, send),
            )
        }
    val state by media.state.collectAsState()
    MediacenterTargetContent(
        MediaTargetState(state, LibraryState(connected = true), AudioSettingsState(), streaming = streaming),
        controllers,
        { null },
        modifier,
    )
}

private fun activeStreaming(): StreamingState {
    val browser = BrowserCapabilities(true, true, true, false, false, false)
    val catalog =
        Capabilities(
            MethodName.entries,
            listOf(
                ServiceCapability("youtube", SupportedState.PARTIAL, browser),
                ServiceCapability("hbo_max", SupportedState.UNSUPPORTED, browser, "Open the enrolled Chrome profile."),
            ),
            browserBridge = true,
        )
    return StreamingState(
        catalog,
        BrowserState("web", "1", "youtube", "profile", true, browser),
        BrowserTarget("epoch", "web", "1"),
    )
}
