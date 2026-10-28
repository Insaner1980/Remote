package com.emmah.colordock.remote.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.emmah.colordock.remote.companion.DockConnectionState
import com.emmah.colordock.remote.companion.DockMethod
import com.emmah.colordock.remote.companion.DockSnapshot
import com.emmah.colordock.remote.companion.DockState
import com.emmah.colordock.remote.companion.UploadProgress
import com.emmah.colordock.remote.companion.WallpaperMetadata
import com.emmah.colordock.remote.companion.WallpaperSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompanionScreensTest {
    @Test fun shelfAcknowledgementAfterRestorationClosesWithoutResubmitting() {
        val restoration =
            androidx.compose.ui.test.junit4
                .StateRestorationTester(compose)
        var value by mutableStateOf(snapshot)
        var saving by mutableStateOf(false)
        var closed by mutableStateOf(false)
        var saves = 0
        restoration.setContent {
            RemoteTheme {
                if (closed) {
                    androidx.compose.material3.Text("Editor closed")
                } else {
                    AppShelfScreen(value, saving, null, { _, _, _ ->
                        saves++
                        saving = true
                    }, {
                        closed = true
                    })
                }
            }
        }
        compose.onNodeWithText("Save").performScrollTo().performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle {
            assertEquals(1, saves)
            assertFalse(closed)
            value = snapshot.copy(revision = 8)
            saving = false
        }
        compose.onNodeWithText("Editor closed").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, saves)
        }
    }

    @Test fun locationInputRespectsServiceLimit() {
        var ui by mutableStateOf(DockUiState())
        var searches = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                RemoteTheme {
                    Box(Modifier.width(320.dp)) {
                        DockScreen(ready, ui, null, {}, {}, {}, {}, { _, _, _ ->
                        }, {
                            ui = ui.copy(query = it)
                        }, {
                            searches++
                        }, {})
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("Show weather").performScrollTo().performClick()
        compose.onNodeWithText("Search").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("City or place").performScrollTo().performTextInput("x".repeat(121))
        compose.runOnIdle {
            assertEquals(120, ui.query.length)
        }
        compose.onNodeWithText("Search").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1, searches)
        }
    }

    @Test fun finalUploadRefreshKeepsProgressVisibleUntilBatchFinishes() {
        var upload by mutableStateOf<UploadProgress?>(UploadProgress("Complete", 1f))
        var cancelled = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                RemoteTheme {
                    Box(Modifier.width(320.dp)) {
                        WallpaperTestScreen(
                            ready,
                            WallpaperUiState(upload = upload, outcome = "1 sent: Picture 1"),
                            onCancelUpload = {
                                cancelled++
                            },
                        )
                    }
                }
            }
        }
        compose.onNodeWithText("Complete").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Send to TV").assertDoesNotExist()
        compose.onNodeWithText("Cancel upload").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1, cancelled)
            upload = null
        }
        compose.onNodeWithText("Preview pictures").assertDoesNotExist()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("1 sent: Picture 1"))
        compose.onNodeWithText("1 sent: Picture 1").performScrollTo().assertIsDisplayed()
    }

    @Test fun backCancelsPreparationBeforeFirstPreview() {
        var preparing by mutableStateOf(true)
        var abandoned = 0
        var back: androidx.activity.OnBackPressedDispatcher? = null
        compose.setContent {
            back =
                androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current
                    ?.onBackPressedDispatcher
            RemoteTheme {
                WallpaperTestScreen(ready, WallpaperUiState(preparing = preparing), onAbandon = {
                    abandoned++
                    preparing = false
                })
            }
        }
        compose.onNodeWithText("Preview pictures").assertIsDisplayed()
        compose.runOnIdle {
            requireNotNull(back).onBackPressed()
        }
        compose.onNodeWithText("Preview pictures").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, abandoned)
        }
    }

    @Test fun deleteConfirmationDoesNotSurviveDetailReplacement() {
        val first = picture.copy(id = "user:first")
        val second = picture.copy(id = "user:second")
        var detail by mutableStateOf<WallpaperMetadata?>(first)
        val deleted = mutableListOf<String>()
        compose.setContent {
            RemoteTheme {
                WallpaperTestScreen(ready, WallpaperUiState(detail = detail), onDetail = {
                    detail = it
                }, onImageAction = {
                    method,
                    item,
                    _,
                    ->
                    if (method ==
                        DockMethod.Delete
                    ) {
                        deleted.add(item.id)
                    }
                })
            }
        }
        compose.onNodeWithText("Delete from TV").performScrollTo().performClick()
        compose.onNodeWithText("Delete this picture from the TV?").assertIsDisplayed()
        compose.runOnIdle {
            detail = null
        }
        compose.runOnIdle {
            detail = second
        }
        compose.onNodeWithText("Delete this picture from the TV?").assertDoesNotExist()
        compose.runOnIdle {
            assertTrue(deleted.isEmpty())
        }
        compose.onNodeWithText("Delete from TV").performScrollTo().performClick()
        compose.onNodeWithText("Delete", useUnmergedTree = true).performClick()
        compose.runOnIdle {
            assertEquals(listOf(second.id), deleted)
        }
    }

    @Test fun duplicateAndUnknownHiddenIdsCannotBypassLastVisibleApp() {
        var saved: List<String>? = null
        val value =
            snapshot.copy(
                shelfOrder = listOf("future-app", "future-app", "netflix"),
                shelfHidden = listOf("netflix", "unknown", "unknown"),
            )
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                RemoteTheme {
                    Box(Modifier.width(320.dp)) {
                        AppShelfScreen(value, false, null, { _, hidden, _ ->
                            saved = hidden
                        }, {})
                    }
                }
            }
        }
        compose
            .onAllNodesWithContentDescription("Show future-app")[0]
            .performScrollTo()
            .performClick()
            .assertIsOn()
        compose.onNodeWithText("Keep at least one app visible.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Save").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(value.shelfHidden, saved)
        }
    }

    @Test fun unknownShelfItemIsPreservedWithoutCrashingTheEditor() {
        var saved: List<String>? = null
        val value = snapshot.copy(shelfOrder = listOf("future-app"), shelfHidden = emptyList())
        compose.setContent {
            RemoteTheme(AppTheme.SoftDark) {
                AppShelfScreen(value, false, null, { order, _, _ ->
                    saved = order
                }, {})
            }
        }
        compose.onNodeWithText("future-app").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Save").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(listOf("future-app"), saved)
        }
    }

    @get:Rule val compose = createComposeRule()

    @Test fun changedThumbnailHashRequestsNewPreviewForSameId() {
        var current by mutableStateOf(picture)
        val requested = mutableListOf<String?>()
        compose.setContent {
            RemoteTheme(AppTheme.SoftDark) {
                WallpaperTestScreen(
                    ready.copy(
                        snapshot = snapshot.copy(current = current),
                    ),
                    WallpaperUiState(),
                    onThumbnail = {
                        requested.add(it.thumbnailHash)
                    },
                )
            }
        }
        compose.runOnIdle {
            assertEquals(listOf<String?>(null), requested)
            current = picture.copy(thumbnailHash = "ab".repeat(32))
        }
        compose.runOnIdle {
            assertEquals(listOf(null, "ab".repeat(32)), requested)
        }
    }

    private val picture = WallpaperMetadata("fixture", "Fixture picture", "cover", "paper", "Paper")
    private val snapshot =
        DockSnapshot(
            7,
            WallpaperSettings("fixture", listOf("fixture"), "fixed", "open"),
            picture,
            0,
            139,
            0,
            false,
            null,
            shelfApps.map {

                it.first
            },
            emptyList(),
            false,
        )
    private val ready = DockState(DockConnectionState.Ready, snapshot)
    private var commands = 0

    private fun show(
        width: Int,
        scale: Float,
        tab: RemoteTab = RemoteTab.Dock,
    ) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                RemoteTheme(
                    configuredTheme(),
                ) {
                    Surface {
                        Box(Modifier.width(width.dp)) {
                            var current by remember {
                                mutableStateOf(tab)
                            }
                            RemoteAppShell(current, {
                                current = it
                            }) { modifier ->
                                if (current ==
                                    RemoteTab.Dock
                                ) {
                                    DockScreen(ready, DockUiState(), null, {
                                        commands++
                                    }, {
                                        commands++
                                    }, {
                                        commands++
                                    }, {
                                        commands++
                                    }, { _, _, _ ->
                                        commands++
                                    }, {}, {}, {}, modifier)
                                } else if (current ==
                                    RemoteTab.Wallpapers
                                ) {
                                    WallpaperScreen(ready, WallpaperUiState(), emptyMap(), {
                                        commands++
                                    }, {
                                        commands++
                                    }, {}, {}, {}, {}, {
                                        commands++
                                    }, {
                                        commands++
                                    }, { _, _, _ ->
                                        commands++
                                    }, {
                                        commands++
                                    }, { _, _ ->
                                    }, {
                                        commands++
                                    }, {}, {}, modifier)
                                } else {
                                    androidx.compose.material3.Text("Remote controls", modifier)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun verify(
        width: Int,
        scale: Float,
    ) {
        show(width, scale)
        compose.onNodeWithText("Start clock mode").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Start automatically").performScrollTo().performClick()
        compose.onNodeWithText("After 30 minutes").assertIsDisplayed().performClick()
        compose.onNodeWithText("Edit app shelf").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Wallpapers").assertIsDisplayed().performClick()
        compose.onNodeWithText("Add from phone").performScrollTo().assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Shuffle selected"))
        compose.onNodeWithText("Shuffle selected").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Remote", useUnmergedTree = true).assertIsDisplayed().performClick()
        compose.onNodeWithText("Remote controls").assertIsDisplayed()
    }

    @Test fun companionAt320dp() = verify(320, 1f)

    @Test fun companionAt200PercentFont() = verify(400, 2f)

    @Test fun companionAt320dpAnd200PercentFont() = verify(320, 2f)

    @Test fun companionAtDeviceFontScale() =
        verify(
            400,
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext.resources.configuration.fontScale,
        )

    @Test fun tabNavigationSendsNoTvCommand() {
        show(400, 1f, RemoteTab.Wallpapers)
        compose.onNodeWithText("Remote", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Remote controls").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(0, commands)
        }
    }

    @Test fun companionSwitchesExposeTheirNamesAndState() {
        show(320, 2f)
        compose
            .onNodeWithContentDescription("Show weather")
            .performScrollTo()
            .assertIsOff()
            .assertHasClickAction()
        compose.onNodeWithText("Wallpapers").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription("Shuffle selected"))
        compose
            .onNodeWithContentDescription("Shuffle selected")
            .performScrollTo()
            .assertIsOff()
            .assertHasClickAction()
    }

    @Test fun builtInCollectionFiltersWithoutSendingTvCommands() {
        val second =
            picture.copy(
                id = "second",
                title = "Second picture",
                category = "nature",
                categoryLabel = "Nature",
            )
        compose.setContent {
            RemoteTheme(
                AppTheme.fromStored(
                    androidx.test.platform.app.InstrumentationRegistry
                        .getArguments()
                        .getString("theme"),
                ),
            ) {
                WallpaperTestScreen(ready, WallpaperUiState(source = "builtin", items = listOf(picture, second)))
            }
        }
        compose.onNodeWithText("Collection").performScrollTo().performClick()
        compose.onNodeWithText("Nature").performClick()
        compose.onNodeWithContentDescription("Second picture").performScrollTo().assertIsDisplayed()
        // Current preview stays visible in the list.
        compose.onAllNodesWithContentDescription("Fixture picture").assertCountEquals(1)
    }

    @Test fun shuffleOptionsKeepNextAndTriggerReachable() {
        show(320, 2f, RemoteTab.Wallpapers)
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription("Shuffle options"))
        compose.onNodeWithContentDescription("Shuffle options").performScrollTo().performClick()
        compose.onNodeWithText("Change wallpaper").performClick()
        compose.onNodeWithText("On a fresh app start").performClick()
        compose.onNodeWithText("Next wallpaper").performClick()
        compose.runOnIdle {
            assertEquals(2, commands)
        }
    }

    @Test fun unavailableCompanionRequiresExplicitOpenAction() {
        compose.setContent {
            RemoteTheme(
                AppTheme.fromStored(
                    androidx.test.platform.app.InstrumentationRegistry
                        .getArguments()
                        .getString("theme"),
                ),
            ) {
                DockUnavailable(DockState(), {
                    commands++
                }, {})
            }
        }
        compose.onNodeWithText("Open Color Dock to manage wallpapers").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(0, commands)
        }
        compose.onNodeWithText("Open Color Dock").performClick()
        compose.runOnIdle {
            assertEquals(1, commands)
        }
    }

    @Test fun connectingMessageUsesReadableEllipsis() {
        compose.setContent {
            RemoteTheme {
                DockUnavailable(DockState(DockConnectionState.Connecting), {
                    commands++
                }, {})
            }
        }
        compose.onNodeWithText("Connecting to Color Dock\u2026").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(0, commands)
        }
    }

    @Test fun staleShelfDraftKeepsOriginalRevisionAndCannotHideEveryApp() {
        var saved: Triple<List<String>, List<String>, Long>? = null
        compose.setContent {
            RemoteTheme(
                AppTheme.fromStored(
                    androidx.test.platform.app.InstrumentationRegistry
                        .getArguments()
                        .getString("theme"),
                ),
            ) {
                AppShelfScreen(snapshot, false, null, { order, hidden, revision ->
                    saved =
                        Triple(order, hidden, revision)
                }, {})
            }
        }
        compose
            .onNodeWithContentDescription("Show YouTube")
            .assertIsOn()
            .performClick()
            .assertIsOff()
        compose.onNodeWithContentDescription("Show YouTube").performClick()
        compose.onNodeWithContentDescription("Move YouTube up").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Move YouTube down").assertIsEnabled()
        compose.onNodeWithText("Save").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(7L, saved?.third)
            assertEquals(
                shelfApps.map {
                    it.first
                },
                saved?.first,
            )
            assertTrue(saved?.second?.isEmpty() == true)
        }
    }
}

private fun configuredTheme() = AppTheme.fromStored(InstrumentationRegistry.getArguments().getString("theme"))
