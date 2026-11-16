package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.emmah.colordock.remote.mediacenter.protocol.Availability
import com.emmah.colordock.remote.ui.RemoteTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun targetControlsRemainCanonicalAcrossPhoneConfigurations() {
        val size = mutableStateOf(360 to 640)
        val font = mutableFloatStateOf(1f)
        var configured = DpSize.Zero
        var configuredFont = 0f
        compose.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(size.value.first.dp, size.value.second.dp)) then
                    DeviceConfigurationOverride.FontScale(font.floatValue),
            ) {
                val density = LocalDensity.current
                RemoteTheme {
                    val scope = rememberCoroutineScope()
                    val media =
                        remember { MediaSessionController(scope) { true }.apply { observe(phoneMediaSnapshot()) } }
                    val library = remember { LibraryViewModel(scope) { true } }
                    val renderer = remember { RendererController(scope) { true } }
                    val audio = remember { AudioSettingsController { true } }
                    val state by media.state.collectAsState()
                    MediacenterTargetContent(
                        MediaTargetState(state, LibraryState(connected = true), AudioSettingsState()),
                        MediaTargetControllers(media, library, renderer, audio),
                        {
                            null
                        },
                        Modifier.fillMaxSize().onGloballyPositioned {
                            configured = with(density) { DpSize(it.size.width.toDp(), it.size.height.toDp()) }
                            configuredFont = density.fontScale
                        },
                    )
                }
            }
        }
        for ((width, height, scale) in listOf(Triple(360, 640, 1f), Triple(640, 360, 1f), Triple(360, 640, 1.6f))) {
            compose.runOnIdle {
                size.value = width to height
                font.floatValue = scale
            }
            compose.onNodeWithTag("media-nav-Remote").performClick()
            compose.runOnIdle {
                assertEquals(width.toFloat(), configured.width.value, 1f)
                assertEquals(height.toFloat(), configured.height.value, 1f)
                assertEquals(scale, configuredFont, 0.01f)
            }
            compose.onAllNodesWithTag("transport-controls").assertCountEquals(1)
            compose.onNodeWithTag("media-nav-Library").performClick()
            compose.onAllNodesWithTag("transport-controls").assertCountEquals(0)
            compose.onNodeWithText("Now playing").performScrollTo().performClick()
            compose.onAllNodesWithTag("transport-controls").assertCountEquals(1)
            compose.onNodeWithTag("media-nav-Settings").performClick()
            compose.onAllNodesWithTag("transport-controls").assertCountEquals(0)
            compose.onNodeWithText("Sound").performScrollTo().assertIsDisplayed()
        }
    }

    @Test fun offlineEmptyAndErrorAreRenderedWithActionableRefresh() {
        val state = mutableStateOf(LibraryState(connected = true, message = "No matching media."))
        compose.setContent {
            RemoteTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    LibraryScreen(
                        state.value,
                        LibraryActions(
                            { _, _ -> },
                            {},
                            {},
                            {},
                            {},
                            {},
                            {},
                            {},
                            {},
                        ),
                        { null },
                    )
                }
            }
        }
        compose.onNodeWithText("No matching media.").assertIsDisplayed()
        compose.runOnIdle {
            state.value =
                state.value.copy(
                    items = listOf(phoneMediaItem().copy(availability = Availability.OFFLINE)),
                    message = "Library request timed out. Retry.",
                )
        }
        compose.onNodeWithText("song").assertIsNotEnabled()
        compose.onNodeWithText("Offline: offline").assertIsDisplayed()
        compose.onNodeWithText("Refresh library").performScrollTo().assertIsEnabled()
    }
}
