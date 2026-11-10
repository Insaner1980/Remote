package com.emmah.colordock.remote.ui.mediacenter

import com.emmah.colordock.remote.mediacenter.protocol.Availability
import com.emmah.colordock.remote.mediacenter.protocol.ErrorPayload
import com.emmah.colordock.remote.mediacenter.protocol.McReply
import com.emmah.colordock.remote.mediacenter.protocol.McRequest
import com.emmah.colordock.remote.mediacenter.protocol.MediaItem
import com.emmah.colordock.remote.mediacenter.protocol.MediaKind
import com.emmah.colordock.remote.mediacenter.protocol.MediaPage
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand
import com.emmah.colordock.remote.mediacenter.protocol.RemoteResult
import com.emmah.colordock.remote.mediacenter.protocol.WireErrorCode
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
    @Test fun cancelsObsoleteSearchAndIgnoresLateReply() =
        runTest {
            val sent = mutableListOf<McRequest>()
            val model = LibraryViewModel(backgroundScope) { sent.add(it) }
            model.observe("epoch")
            val old = sent.last().id
            model.search("new")
            advanceTimeBy(150)
            runCurrent()
            model.receive(McReply.Success(old, RemoteResult.LibraryQuery(MediaPage(listOf(item("old"))))))
            assertTrue(
                model.state.value.items
                    .isEmpty(),
            )
            model.receive(McReply.Success(sent.last().id, RemoteResult.LibraryQuery(MediaPage(listOf(item("new"))))))
            assertEquals(
                "new",
                model.state.value.items
                    .single()
                    .id,
            )
        }

    @Test fun pagingAllowsOnlyOneOutstandingPageAndRetainsFullServerSelection() =
        runTest {
            val sent = mutableListOf<McRequest>()
            val model = LibraryViewModel(backgroundScope) { sent.add(it) }
            model.observe("epoch")
            model.receive(
                McReply.Success(
                    sent.last().id,
                    RemoteResult.LibraryQuery(MediaPage(listOf(item("first")), "cursor", "full-selection")),
                ),
            )
            model.next()
            model.next()
            assertEquals(2, sent.size)
            model.receive(
                McReply.Success(
                    sent.last().id,
                    RemoteResult.LibraryQuery(MediaPage(listOf(item("last")), selectionId = "full-selection")),
                ),
            )
            assertEquals("full-selection", model.state.value.selectionId)
            assertEquals(
                listOf("last"),
                model.state.value.items
                    .map { it.id },
            )
        }

    @Test fun disconnectDiscardsLateRepliesAndSearchLimitCountsUnicodeCharacters() =
        runTest {
            val sent = mutableListOf<McRequest>()
            val model = LibraryViewModel(backgroundScope) { sent.add(it) }
            model.observe("epoch")
            val id = sent.last().id
            model.observe(null)
            model.receive(McReply.Success(id, RemoteResult.LibraryQuery(MediaPage(listOf(item("late"))))))
            assertTrue(
                model.state.value.items
                    .isEmpty(),
            )
            assertFalse(model.state.value.connected)
            model.search("😀".repeat(200))
            assertEquals(400, model.state.value.query.length)
            model.search("😀".repeat(201))
            assertEquals(400, model.state.value.query.length)
        }

    @Test fun excessivePhotoSelectionIsExplicitAndDoesNotRetainPartialOrder() =
        runTest {
            val sent = mutableListOf<McRequest>()
            val model = LibraryViewModel(backgroundScope) { sent.add(it) }
            model.observe("epoch")
            model.receive(
                McReply.Failure(
                    sent.last().id,
                    ErrorPayload(WireErrorCode.SELECTION_TOO_LARGE, "too many", false, emptyList()),
                ),
            )
            assertTrue(
                model.state.value.message
                    .contains("10,000"),
            )
            assertNull(model.state.value.selectionId)
        }

    @Test fun timeoutRetryThenPreviousUsesLastDisplayedPageAndOwnerChangeInvalidatesIt() =
        runTest {
            val sent = mutableListOf<McRequest>()
            val m = LibraryViewModel(backgroundScope) { sent.add(it) }
            m.observe("epoch", "server-a")
            m.receive(
                McReply.Success(sent.last().id, RemoteResult.LibraryQuery(MediaPage(listOf(item("first")), "page2"))),
            )
            m.next()
            advanceTimeBy(5000)
            runCurrent()
            m.next()
            m.receive(
                McReply.Success(sent.last().id, RemoteResult.LibraryQuery(MediaPage(listOf(item("second")), "page3"))),
            )
            m.previous()
            assertNull((sent.last().command as RemoteCommand.LibraryQuery).params.cursor)
            val old = sent.last().id
            m.observe("epoch", "server-b")
            m.receive(McReply.Success(old, RemoteResult.LibraryQuery(MediaPage(listOf(item("foreign"))))))
            assertTrue(
                m.state.value.items
                    .isEmpty(),
            )
        }

    private fun item(id: String) = MediaItem(id, "source", MediaKind.PHOTO, id, Availability.AVAILABLE)
}
