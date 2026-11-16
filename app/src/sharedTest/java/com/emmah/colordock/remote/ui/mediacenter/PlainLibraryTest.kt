package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.emmah.colordock.remote.mediacenter.protocol.Availability
import com.emmah.colordock.remote.mediacenter.protocol.MediaItem
import com.emmah.colordock.remote.mediacenter.protocol.MediaKind
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand
import com.emmah.colordock.remote.ui.RemoteTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlainLibraryTest {
    @get:Rule val compose = createComposeRule()

    @Test fun musicHasOneReadableTitleAndSecondaryMenuNeverStartsPlayback() {
        val selected = mutableListOf<String>()
        val queued = mutableListOf<String>()
        val item = MediaItem("music-id", "source", MediaKind.MUSIC, "Sample_track.mp3", Availability.AVAILABLE)
        val actions =
            LibraryActions({ _, _ -> }, { selected.add(it.id) }, { queued.add(it.id) }, {}, {}, {}, {}, {}, {})
        compose.setContent {
            RemoteTheme {
                LibraryScreen(
                    LibraryState(
                        kind = MediaKind.MUSIC,
                        items = listOf(item),
                        connected = true,
                        message = "",
                    ),
                    actions,
                    { null },
                )
            }
        }
        compose.onNodeWithText("Sample track").assertIsDisplayed()
        compose.onNodeWithText("Options for Sample_track.mp3").assertDoesNotExist()
        compose.onNodeWithContentDescription("Options for Sample track").performClick()
        assertTrue(selected.isEmpty() && queued.isEmpty())
        compose.onNodeWithText("Add to queue").performClick()
        assertEquals(listOf("music-id"), queued)
        compose.onNodeWithText("Sample track").performClick()
        assertEquals(listOf("music-id"), selected)
    }

    @Test fun photosDoNotExposeOpaqueFilenamesAsTheirUserInterface() {
        var selected = ""
        val item = MediaItem("photo-id", "source", MediaKind.PHOTO, "0".repeat(32) + ".jpg", Availability.AVAILABLE)
        val camera = item.copy(id = "camera-photo", name = "IMG-20260101-WA0001.jpg")
        assertEquals("Holiday sunset", photoTitle("Holiday sunset.jpg", 1))
        val actions = LibraryActions({ _, _ -> }, { selected = it.id }, {}, {}, {}, {}, {}, {}, {})
        compose.setContent {
            RemoteTheme {
                LibraryScreen(
                    LibraryState(
                        kind = MediaKind.PHOTO,
                        items = listOf(item, camera),
                        connected = true,
                        message = "",
                    ),
                    actions,
                    { null },
                )
            }
        }
        compose.onNodeWithText(item.name).assertDoesNotExist()
        compose.onNodeWithText("IMG-20260101-WA0001").assertDoesNotExist()
        compose.onNodeWithContentDescription("Open photo 1").assertIsDisplayed().performClick()
        assertEquals("photo-id", selected)
        compose.onNodeWithContentDescription("Open photo 2").performClick()
        assertEquals("camera-photo", selected)
    }

    @Test fun openItemMenuKeepsItsIdentityWhenSameNamedRowsReorder() {
        val first = MediaItem("first", "source", MediaKind.MUSIC, "Same.mp3", Availability.AVAILABLE)
        val second = first.copy(id = "second")
        val items = mutableStateOf(listOf(first, second))
        val queued = mutableListOf<String>()
        val actions = LibraryActions({ _, _ -> }, {}, { queued.add(it.id) }, {}, {}, {}, {}, {}, {})
        compose.setContent {
            RemoteTheme {
                LibraryScreen(
                    LibraryState(
                        kind = MediaKind.MUSIC,
                        items = items.value,
                        connected = true,
                        message = "",
                    ),
                    actions,
                    { null },
                )
            }
        }
        compose.onAllNodesWithContentDescription("Options for Same")[1].performClick()
        compose.runOnIdle { items.value = listOf(second, first) }
        compose.onNodeWithText("Add to queue").performClick()
        assertEquals(listOf("second"), queued)
    }

    @Test fun oversizedPhotoSelectionKeepsTheShortActionNeededToRecover() {
        lateinit var library: LibraryViewModel
        val sent = mutableListOf<com.emmah.colordock.remote.mediacenter.protocol.McRequest>()
        compose.setContent {
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            val controller =
                androidx.compose.runtime.remember {
                    LibraryViewModel(scope) { sent.add(it) }.apply {
                        observe("epoch", "server")
                        search("", MediaKind.PHOTO)
                        library =
                            this
                    }
                }
            val state = controller.state.collectAsState().value
            RemoteTheme { LibraryScreen(state, LibraryActions({ _, _ -> }, {}, {}, {}, {}, {}, {}, {}, {}), { null }) }
        }
        compose.waitUntil(5_000) {
            sent.any {
                (it.command as? RemoteCommand.LibraryQuery)?.params?.kind ==
                    MediaKind.PHOTO
            }
        }
        compose.runOnIdle {
            library.receive(
                com.emmah.colordock.remote.mediacenter.protocol.McReply.Failure(
                    sent.last().id,
                    com.emmah.colordock.remote.mediacenter.protocol.ErrorPayload(
                        com.emmah.colordock.remote.mediacenter.protocol.WireErrorCode.SELECTION_TOO_LARGE,
                        "internal limit",
                        true,
                        emptyList(),
                    ),
                ),
            )
        }
        compose.onNodeWithText("Narrow your search").assertIsDisplayed()
    }
}
