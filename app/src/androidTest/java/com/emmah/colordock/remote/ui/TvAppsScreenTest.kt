package com.emmah.colordock.remote.ui

import android.view.KeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.emmah.colordock.remote.tv.TvApp
import com.emmah.colordock.remote.tv.TvCommand
import com.emmah.colordock.remote.tv.TvConnectionState
import com.emmah.colordock.remote.tv.TvState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TvAppsScreenTest {
    @get:Rule val compose = createComposeRule()
    private val apps =
        listOf(
            TvApp("netflix", "Netflix", null),
            TvApp("youtube.leanback.v4", "YouTube", null),
            TvApp("status", "Independent television and films from around the world", null),
        )
    private val feature =
        mutableStateOf(TvAppsUiState(apps = apps, hasLoaded = true, connected = true, sessionGeneration = 1))
    private var refreshes = 0
    private var releases = 0
    private var acknowledgements = 0
    private val opened = mutableListOf<String>()
    private val commands = mutableListOf<TvCommand>()

    private fun show(
        width: Int = 400,
        scale: Float = 1f,
    ) {
        compose.setContent {
            val density = LocalWindowInfo.current.containerSize.width / width.toFloat()
            CompositionLocalProvider(LocalDensity provides Density(density, scale)) {
                RemoteTheme(AppTheme.fromStored(InstrumentationRegistry.getArguments().getString("theme"))) {
                    Surface {
                        Box(Modifier.width(width.dp)) {
                            RemoteScreen(
                                TvState(TvConnectionState.Ready, guideAvailable = true),
                                emptyList(),
                                commands::add,
                                commands::add,
                                {
                                    releases++
                                },
                                {},
                                {},
                                {},
                                apps = feature.value,
                                onRefreshApps = {
                                    refreshes++
                                },
                                onOpenApp = opened::add,
                                onAppLaunchHandle = {
                                    acknowledgements++
                                    feature.value = feature.value.copy(launchCompletedId = null)
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    private fun picker() {
        compose.onNodeWithText("All apps").performScrollTo().performClick()
    }

    private fun inSheet(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag("tv-app-sheet")))

    private fun captureSheet(name: String) {
        val theme = InstrumentationRegistry.getArguments().getString("theme") ?: "WarmLight"
        val directory =
            java.io
                .File(
                    InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
                    "design-captures",
                ).apply {
                    mkdirs()
                }
        java.io.File(directory, "$theme-picker-$name.png").outputStream().use { output ->
            compose
                .onNodeWithTag(
                    "tv-app-sheet",
                ).captureToImage()
                .asAndroidBitmap()
                .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
        }
    }

    private fun layout(
        width: Int,
        scale: Float,
    ) {
        show(width, scale)
        compose
            .onNodeWithText("Live TV")
            .performScrollTo()
            .assertIsDisplayed()
            .assertHasClickAction()
        picker()
        inSheet("Refresh").assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
        inSheet("Close").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("app-picker-list").performScrollToNode(hasText(apps.last().title))
        inSheet(apps.last().title).assertIsDisplayed()
        captureSheet("${width}dp-$scale")
        inSheet("Close").performClick()
        compose.onNodeWithTag("tv-app-sheet").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(2, refreshes)
            assertTrue(releases > 0)
            assertTrue(commands.isEmpty())
        }
    }

    @Test fun layoutAt400dp() = layout(400, 1f)

    @Test fun layoutAt320dp() = layout(320, 1f)

    @Test fun layoutAt200Percent() = layout(400, 2f)

    @Test fun layoutAt320dpAnd200Percent() = layout(320, 2f)

    @Test fun authenticFallbackAndPlainNameShareOneLaunchAction() {
        show()
        compose.onNodeWithTag("app-shortcuts").performScrollTo()
        compose.onNodeWithTag("app-artwork-netflix", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("Open Netflix on TV").performClick()
        compose.onNodeWithTag("app-shortcuts").performScrollToNode(hasText(apps.last().title))
        compose.onNodeWithText(apps.last().title).assertIsDisplayed().performClick()
        compose.onNodeWithTag("app-artwork-status", useUnmergedTree = true).assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(listOf("netflix", "status"), opened)
        }
    }

    @Test fun phoneBackDismissesLocallyAndOpeningPickerReleasesHold() {
        show()
        val before = releases
        picker()
        compose.runOnIdle {
            assertTrue(releases > before)
        }
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.onNodeWithTag("tv-app-sheet").assertDoesNotExist()
        compose.onNodeWithText("All apps").assertIsFocused()
        compose.runOnIdle {
            assertTrue(commands.isEmpty())
        }
    }

    @Test fun launchFailureKeepsPickerAndSuccessIsConsumedOnce() {
        show()
        picker()
        inSheet("Netflix").performClick()
        compose.runOnIdle {
            feature.value = feature.value.copy(launchingId = "netflix", launchTitle = "Netflix")
        }
        inSheet("Opening Netflix…").assertIsDisplayed()
        compose
            .onNode(
                hasContentDescription("Open Netflix on TV") and hasAnyAncestor(hasTestTag("tv-app-sheet")),
            ).assertIsNotEnabled()
        compose.runOnIdle {
            feature.value = feature.value.copy(launchingId = null, launchErrorId = "netflix")
        }
        inSheet("Could not open Netflix. Try again or refresh TV apps.").assertIsDisplayed()
        captureSheet("launch-failure")
        inSheet("Retry").performClick()
        compose.runOnIdle {
            feature.value = feature.value.copy(launchErrorId = null, launchCompletedId = "netflix")
        }
        compose.onNodeWithTag("tv-app-sheet").assertDoesNotExist()
        picker()
        compose.onNodeWithTag("tv-app-sheet").assertExists()
        compose.runOnIdle {
            assertEquals(1, acknowledgements)
            assertEquals(listOf("netflix", "netflix"), opened)
        }
    }

    @Test fun oldCompletionCannotCloseReopenedPicker() {
        show()
        picker()
        inSheet("Netflix").performClick()
        inSheet("Close").performClick()
        picker()
        compose.runOnIdle {
            feature.value = feature.value.copy(launchCompletedId = "netflix")
        }
        compose.onNodeWithTag("tv-app-sheet").assertExists()
    }

    @Test fun connectionReplacementClosesOldPicker() {
        show()
        picker()
        compose.runOnIdle {
            feature.value = TvAppsUiState(connected = true, sessionGeneration = 2, loading = true)
        }
        compose.onNodeWithTag("tv-app-sheet").assertDoesNotExist()
    }

    @Test fun loadingEmptyAndFailureAreDistinctAndRefreshKeepsPreviousApps() {
        feature.value = TvAppsUiState(connected = true, loading = true)
        show()
        picker()
        inSheet("Loading TV apps…").assertIsDisplayed()
        compose.onNodeWithText("No launchable apps were returned by this TV.").assertDoesNotExist()
        compose.runOnIdle {
            feature.value = feature.value.copy(loading = false, loadError = true)
        }
        inSheet("TV apps could not be loaded. Try again.").assertIsDisplayed()
        inSheet("Retry").performClick()
        compose.runOnIdle {
            feature.value = feature.value.copy(loadError = false, hasLoaded = true)
        }
        inSheet("No launchable apps were returned by this TV.").assertIsDisplayed()
        compose.runOnIdle {
            feature.value = feature.value.copy(apps = apps, loadError = true)
        }
        inSheet("TV apps could not be refreshed. Showing the previous list.").assertIsDisplayed()
        inSheet("Netflix").assertExists()
    }

    @Test fun longLaunchFailureRemainsReachableAt320dpAnd200Percent() {
        val title = "W".repeat(200)
        feature.value =
            feature.value.copy(apps = listOf(TvApp("long", title, null)), launchErrorId = "long", launchTitle = title)
        show(320, 2f)
        picker()
        inSheet("Close").assertIsDisplayed()
        inSheet("Refresh").assertIsDisplayed()
        compose.onNodeWithTag("app-picker-list").performScrollToNode(hasText("Retry"))
        inSheet("Retry").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(listOf("long"), opened)
        }
    }

    @Test fun appLaunchProgressDoesNotDisableOrdinaryRemoteControls() {
        feature.value = feature.value.copy(launchingId = "netflix", launchTitle = "Netflix")
        show()
        compose.onNodeWithContentDescription("Volume up").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(listOf(TvCommand.VolumeUp), commands)
        }
    }
}
