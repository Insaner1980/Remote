package com.emmah.colordock.remote.ui.mediacenter

import com.emmah.colordock.remote.mediacenter.RecoveryFixture
import com.emmah.colordock.remote.mediacenter.protocol.AcceptedResult
import com.emmah.colordock.remote.mediacenter.protocol.ActionKind
import com.emmah.colordock.remote.mediacenter.protocol.ActivityMode
import com.emmah.colordock.remote.mediacenter.protocol.ActivityPreparation
import com.emmah.colordock.remote.mediacenter.protocol.Availability
import com.emmah.colordock.remote.mediacenter.protocol.ConfirmMethod
import com.emmah.colordock.remote.mediacenter.protocol.ConfirmationPreview
import com.emmah.colordock.remote.mediacenter.protocol.ErrorPayload
import com.emmah.colordock.remote.mediacenter.protocol.Expected
import com.emmah.colordock.remote.mediacenter.protocol.McReply
import com.emmah.colordock.remote.mediacenter.protocol.McRequest
import com.emmah.colordock.remote.mediacenter.protocol.MediaItem
import com.emmah.colordock.remote.mediacenter.protocol.MediaKind
import com.emmah.colordock.remote.mediacenter.protocol.OperationAccepted
import com.emmah.colordock.remote.mediacenter.protocol.OperationState
import com.emmah.colordock.remote.mediacenter.protocol.OperationStatus
import com.emmah.colordock.remote.mediacenter.protocol.OperationsTopic
import com.emmah.colordock.remote.mediacenter.protocol.PhotosNextParams
import com.emmah.colordock.remote.mediacenter.protocol.PhotosStopParams
import com.emmah.colordock.remote.mediacenter.protocol.PlaybackStart
import com.emmah.colordock.remote.mediacenter.protocol.Playlist
import com.emmah.colordock.remote.mediacenter.protocol.PlaylistContents
import com.emmah.colordock.remote.mediacenter.protocol.PlaylistsSaveParams
import com.emmah.colordock.remote.mediacenter.protocol.QueueMoveParams
import com.emmah.colordock.remote.mediacenter.protocol.QueuePage
import com.emmah.colordock.remote.mediacenter.protocol.QueueRemoveParams
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand
import com.emmah.colordock.remote.mediacenter.protocol.RemoteJson
import com.emmah.colordock.remote.mediacenter.protocol.RemoteResult
import com.emmah.colordock.remote.mediacenter.protocol.Topic
import com.emmah.colordock.remote.mediacenter.protocol.WireErrorCode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaSessionControllerTest {
    @Test fun resumeChoiceOfflineAndReturnToPlaybackUseNativeIdentity() =
        runTest {
            val sent = mutableListOf<McRequest>()
            val controller = MediaSessionController(backgroundScope) { sent.add(it) }
            controller.observe(snapshot())
            val movie = item("movie", MediaKind.VIDEO).copy(resumePositionSeconds = 70.0)
            controller.choose(movie)
            assertEquals(movie, controller.state.value.resume)
            assertTrue(sent.isEmpty())
            controller.resume(PlaybackStart.RESUME)
            assertEquals(PlaybackStart.RESUME, (sent.single().command as RemoteCommand.ActivityPrepare).params.start)
            assertEquals("epoch", sent.single().expected?.serverEpoch)
            controller.observe(null)
            controller.choose(movie.copy(availability = Availability.OFFLINE))
            assertEquals(1, sent.size)
            assertNull(controller.state.value.snapshot)
            controller.destination(MediaDestination.Remote)
            assertEquals(MediaDestination.Remote, controller.state.value.destination)
        }

    @Test fun confirmedMusicAdoptsExactEntryAfterReplacement() =
        runTest {
            val sent = mutableListOf<McRequest>()
            val c = MediaSessionController(backgroundScope) { sent.add(it) }
            c.observe(snapshot())
            c.choose(item("song", MediaKind.MUSIC), entry = "duplicate-entry-b")
            val prepare = sent.last().id
            val preview = musicReplacementConfirmation()
            c.receive(
                McReply.Success(
                    prepare,
                    RemoteResult.ActivityPrepare(ActivityPreparation.ConfirmationRequired(preview)),
                ),
            )
            assertEquals(
                "sheet",
                c.state.value.preview
                    ?.confirmationId,
            )
            c.confirm()
            val confirm = sent.last().id
            c.receive(
                McReply.Success(
                    confirm,
                    RemoteResult.ActivityConfirm(AcceptedResult(OperationAccepted.ACCEPTED, "op")),
                ),
            )
            c.observe(
                snapshot().let {
                    it.copy(
                        player = it.player.copy(itemId = "song", sessionId = "new-music"),
                        queueSummary = it.queueSummary.copy(revision = "9"),
                        operations =
                            OperationsTopic(
                                "4",
                                listOf(
                                    OperationState(
                                        "op",
                                        confirm,
                                        OperationStatus.SUCCEEDED,
                                        cancellationRequested = false,
                                    ),
                                ),
                            ),
                    )
                },
            )
            val adoption = sent.last()
            assertEquals("duplicate-entry-b", (adoption.command as RemoteCommand.QueuePlayEntry).params.entryId)
            assertEquals("9", adoption.expected?.queueRevision)
            assertEquals("new-music", adoption.expected?.sessionId)
            assertTrue(sent.none { it.command is RemoteCommand.QueueNext })
        }

    @Test fun stableQueueIdsAndPlaylistConflictRefreshKeepNativeRevisions() =
        runTest {
            val sent = mutableListOf<McRequest>()
            val c = MediaSessionController(backgroundScope) { sent.add(it) }
            c.observe(snapshot())
            c.command(RemoteCommand.QueueMove(QueueMoveParams("entry-b", "entry-a")))
            assertEquals("entry-b", (sent.last().command as RemoteCommand.QueueMove).params.entryId)
            val id = sent.last().id
            c.receive(
                McReply.Failure(
                    id,
                    ErrorPayload(
                        WireErrorCode.STALE_STATE,
                        "Queue changed. Refreshed.",
                        true,
                        listOf(Topic.QUEUE_SUMMARY),
                    ),
                ),
            )
            assertTrue(sent.last().command is RemoteCommand.QueueGet)
            assertFalse(c.state.value.busy)
        }

    @Test fun photoIntervalAndQueueBatchLimitsRejectInsteadOfTruncating() =
        runTest {
            val sent = mutableListOf<McRequest>()
            val c = MediaSessionController(backgroundScope) { sent.add(it) }
            c.observe(snapshot())
            c.slideshow("selection", null, 0)
            c.slideshow("selection", null, 61)
            c.enqueue(List(101) { "id$it" })
            assertTrue(sent.isEmpty())
            c.slideshow("full-selection", "photo", 60)
            val p = (sent.single().command as RemoteCommand.PhotosSlideshowStart).params
            assertEquals("full-selection", p.selectionId)
            assertEquals(60, p.intervalSeconds)
        }

    @Test fun operationBeforeAcknowledgementIsNotLost() =
        runTest {
            val sent = mutableListOf<McRequest>()
            val c = MediaSessionController(backgroundScope) { sent.add(it) }
            c.observe(snapshot())
            c.command(RemoteCommand.QueueRemove(QueueRemoveParams("entry")))
            c.operation(OperationState("op", sent.last().id, OperationStatus.SUCCEEDED, cancellationRequested = false))
            assertTrue(c.state.value.busy)
            c.receive(
                McReply.Success(
                    sent.last().id,
                    RemoteResult.QueueRemove(AcceptedResult(OperationAccepted.ACCEPTED, "op")),
                ),
            )
            assertFalse(c.state.value.busy)
        }

    @Test fun changedPhotoSelectionRequiresServerActivityPreparation() =
        runTest {
            val sent = mutableListOf<McRequest>()
            val c = MediaSessionController(backgroundScope) { sent.add(it) }
            c.observe(
                snapshot().let {
                    it.copy(
                        activity = it.activity.copy(mode = ActivityMode.PHOTOS),
                        slideshow = it.slideshow.copy(sessionId = "old-photo-session", currentItemId = "old-photo"),
                    )
                },
            )
            c.choose(item("new-photo", MediaKind.PHOTO), "new-filtered-selection")
            assertTrue(sent.single().command is RemoteCommand.ActivityPrepare)
        }

    @Test fun acceptedPlaylistConflictRefreshes() =
        runTest {
            val sent = mutableListOf<McRequest>()
            val c = MediaSessionController(backgroundScope) { sent.add(it) }
            c.observe(snapshot())
            c.playlist("list")
            c.receive(
                McReply.Success(
                    sent.last().id,
                    RemoteResult.PlaylistsGet(
                        PlaylistContents(Playlist("list", "Songs", "3", "0"), QueuePage("3", emptyList())),
                    ),
                ),
            )
            c.command(RemoteCommand.PlaylistsSave(PlaylistsSaveParams.Rename("list", "Renamed")))
            val mutation = sent.last()
            assertEquals("3", mutation.expected?.playlistRevision)
            c.receive(
                McReply.Success(
                    mutation.id,
                    RemoteResult.PlaylistsSave(AcceptedResult(OperationAccepted.ACCEPTED, "edit")),
                ),
            )
            c.operation(OperationState("edit", mutation.id, OperationStatus.FAILED, WireErrorCode.STALE_STATE, false))
            assertTrue(sent.last().command is RemoteCommand.PlaylistsGet)
            assertFalse(c.state.value.busy)
        }

    @Test fun explicitStopRemainsAvailableWhileOtherPhotoWorkIsPending() =
        runTest {
            val sent = mutableListOf<McRequest>()
            val c = MediaSessionController(backgroundScope) { sent.add(it) }
            c.observe(snapshot().let { it.copy(slideshow = it.slideshow.copy(sessionId = "photos")) })
            assertTrue(c.command(RemoteCommand.PhotosNext(PhotosNextParams("photos"))))
            assertTrue(c.state.value.busy)
            assertTrue(c.command(RemoteCommand.PhotosStop(PhotosStopParams("photos"))))
            assertEquals(2, sent.size)
            assertTrue(sent.last().command is RemoteCommand.PhotosStop)
        }

    private fun snapshot() = RemoteJson.parseSnapshot(RecoveryFixture.snapshot())

    private fun item(
        id: String,
        kind: MediaKind,
    ) = MediaItem(id, "source", kind, id, Availability.AVAILABLE)
}

private fun musicReplacementConfirmation() =
    ConfirmationPreview(
        "sheet",
        "phone",
        ActionKind.REPLACE_ACTIVITY,
        listOf("Current video"),
        Expected(serverEpoch = "epoch"),
        60000,
        confirmMethod = ConfirmMethod.ACTIVITY_CONFIRM,
    )
