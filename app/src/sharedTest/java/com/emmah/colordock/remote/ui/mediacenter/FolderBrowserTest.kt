package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.emmah.colordock.remote.mediacenter.protocol.DirectoryNode
import com.emmah.colordock.remote.mediacenter.protocol.MediaKind
import com.emmah.colordock.remote.ui.RemoteTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FolderBrowserTest {
    @get:Rule val compose = createComposeRule()

    @Test fun selectionRemainsUsableInPortraitLandscapeAndLargeFont() {
        val size = mutableStateOf(360 to 640)
        val font = mutableFloatStateOf(1f)
        val folder = DirectoryNode("opaque-music", "Music")
        val model = mutableStateOf(FolderState(items = listOf(folder), connected = true))
        var chosen = DirectoryNode("none", "None") to emptyList<MediaKind>()
        var measured = DpSize.Zero
        var measuredFont = 0f
        var pixelInDp = 1f
        compose.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(size.value.first.dp, size.value.second.dp)) then
                    DeviceConfigurationOverride.FontScale(font.floatValue),
            ) {
                val density = LocalDensity.current
                RemoteTheme {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .onGloballyPositioned {
                                measured = with(density) { DpSize(it.size.width.toDp(), it.size.height.toDp()) }
                                measuredFont = density.fontScale
                                pixelInDp = 1f / density.density
                            }.verticalScroll(rememberScrollState()),
                    ) {
                        FolderBrowser(
                            model.value,
                            false,
                            FolderActions(
                                { model.value = model.value.copy(selected = it, items = emptyList()) },
                                {},
                                {},
                                { node, mediaKinds ->
                                    chosen = node to mediaKinds
                                },
                            ),
                        )
                    }
                }
            }
        }
        compose.onNodeWithText("Music").performScrollTo().performClick()
        for ((width, height, scale) in listOf(Triple(360, 640, 1f), Triple(640, 360, 1f), Triple(360, 640, 2f))) {
            compose.runOnIdle {
                size.value = width to height
                font.floatValue = scale
            }
            compose.waitForIdle()
            compose.onAllNodesWithText("Music")[0].performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Add folder").performScrollTo().performClick()
            compose.runOnIdle {
                assertEquals(width.toFloat(), measured.width.value, pixelInDp)
                assertEquals(height.toFloat(), measured.height.value, pixelInDp)
                assertEquals(scale, measuredFont)
                assertEquals(folder, chosen.first)
                assertEquals(listOf(MediaKind.VIDEO), chosen.second)
            }
        }
    }

    @Test fun disconnectedBrowserCannotConfirmStaleSelection() {
        compose.setContent {
            RemoteTheme {
                FolderBrowser(
                    FolderState(selected = DirectoryNode("old", "Old folder")),
                    false,
                    FolderActions({
                    }, {}, {}, { _, _ -> error("stale selection") }),
                )
            }
        }
        compose.onNodeWithText("Add folder").assertIsNotEnabled()
    }
}
