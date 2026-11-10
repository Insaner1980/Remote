package com.emmah.colordock.remote.ui.mediacenter

import com.emmah.colordock.remote.mediacenter.protocol.ActionKind
import com.emmah.colordock.remote.mediacenter.protocol.ActivityPreparation
import com.emmah.colordock.remote.mediacenter.protocol.AudioOutput
import com.emmah.colordock.remote.mediacenter.protocol.ConfirmMethod
import com.emmah.colordock.remote.mediacenter.protocol.ConfirmationPreview
import com.emmah.colordock.remote.mediacenter.protocol.Expected
import com.emmah.colordock.remote.mediacenter.protocol.McReply
import com.emmah.colordock.remote.mediacenter.protocol.McRequest
import com.emmah.colordock.remote.mediacenter.protocol.OperationState
import com.emmah.colordock.remote.mediacenter.protocol.OperationStatus
import com.emmah.colordock.remote.mediacenter.protocol.OutputsTopic
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand
import com.emmah.colordock.remote.mediacenter.protocol.RemoteResult
import com.emmah.colordock.remote.mediacenter.protocol.WireErrorCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioSettingsTest {
    private val outputs =
        OutputsTopic(
            "2",
            "hdmi",
            "speakers",
            false,
            listOf(AudioOutput("hdmi", "TV", true), AudioOutput("gone", "Disconnected", false)),
        )

    @Test
    fun choosingSoundPreparesServerConfirmationWithoutClaimingEffectiveRouting() {
        val sent = mutableListOf<McRequest>()
        val controller = AudioSettingsController { sent.add(it) }
        controller.observe("epoch", outputs)
        assertTrue(controller.choose("hdmi"))
        val request = sent.single()
        assertEquals("hdmi", (request.command as RemoteCommand.AudioPrepare).params.outputId)
        assertEquals("epoch", request.expected?.serverEpoch)
        assertEquals("speakers", controller.outputs?.effectiveOutputId)
    }

    @Test
    fun disconnectedUnavailableAndUnknownOutputsCannotBeSubmitted() {
        val sent = mutableListOf<McRequest>()
        val controller = AudioSettingsController { sent.add(it) }
        assertFalse(controller.choose("hdmi"))
        controller.observe("epoch", outputs)
        assertFalse(controller.choose("gone"))
        assertFalse(controller.choose("unknown"))
        assertTrue(sent.isEmpty())
        controller.observe(null, null)
        assertFalse(controller.choose("hdmi"))
    }

    @Test
    fun onlyCorrelatedServerPreviewCanConfirmAndConfirmedOutputComesFromSnapshot() {
        val sent = mutableListOf<McRequest>()
        val controller = AudioSettingsController { sent.add(it) }
        controller.observe("epoch", outputs)
        controller.choose("hdmi")
        val preview =
            ConfirmationPreview(
                "confirmation",
                "phone",
                ActionKind.CHANGE_AUDIO,
                listOf("TV", "Current local playback"),
                Expected(serverEpoch = "epoch"),
                60000,
                confirmMethod = ConfirmMethod.AUDIO_CONFIRM,
            )
        val result = RemoteResult.AudioPrepare(ActivityPreparation.ConfirmationRequired(preview))
        controller.receive(McReply.Success("wrong-request", result))
        assertFalse(controller.confirm())
        controller.receive(McReply.Success(sent.single().id, result))
        assertTrue(controller.confirm())
        assertEquals("confirmation", (sent.last().command as RemoteCommand.AudioConfirm).params.confirmationId)
        assertEquals("speakers", controller.outputs?.effectiveOutputId)
        controller.observe("epoch", outputs.copy(revision = "3", effectiveOutputId = "hdmi"))
        assertEquals("hdmi", controller.outputs?.effectiveOutputId)
    }

    @Test
    fun lostDurableReplyUsesNativeOperationAndDisconnectClearsSheetOwnership() {
        val sent = mutableListOf<McRequest>()
        val controller = AudioSettingsController { sent.add(it) }
        controller.observe("epoch", outputs)
        controller.choose("hdmi")
        controller.observe(
            "epoch",
            outputs,
            listOf(
                OperationState("operation", sent.single().id, OperationStatus.SUCCEEDED, cancellationRequested = false),
            ),
        )
        assertFalse(controller.state.value.busy)
        assertEquals("speakers", controller.outputs?.effectiveOutputId)
        controller.observe(null, null)
        assertFalse(controller.confirm())
        assertFalse(controller.test("hdmi"))
    }

    @Test
    fun noEffectiveOutputNeverReportsConfirmedSound() {
        val sent = mutableListOf<McRequest>()
        val controller = AudioSettingsController { sent.add(it) }
        val noOutput = outputs.copy(requestedOutputId = null, effectiveOutputId = null, unavailable = false)
        controller.observe("epoch", noOutput)
        assertEquals("No playback stream has been confirmed.", controller.state.value.message)
        controller.retry(refreshOnly = true)
        controller.receive(McReply.Success(sent.single().id, RemoteResult.AudioOutputs(noOutput)))
        assertEquals("No playback stream has been confirmed.", controller.state.value.message)
        controller.observe("epoch", noOutput.copy(unavailable = true))
        assertEquals("Audio output unavailable", controller.state.value.message)
    }

    @Test
    fun readinessLossInSameEpochClearsAnActiveServerSheet() {
        val sent = mutableListOf<McRequest>()
        val controller = AudioSettingsController { sent.add(it) }
        controller.observe("epoch", outputs)
        controller.choose("hdmi")
        val preview =
            ConfirmationPreview(
                "confirmation",
                "phone",
                ActionKind.CHANGE_AUDIO,
                listOf("TV"),
                Expected(serverEpoch = "epoch"),
                60000,
                confirmMethod = ConfirmMethod.AUDIO_CONFIRM,
            )
        controller.receive(
            McReply.Success(
                sent.single().id,
                RemoteResult.AudioPrepare(ActivityPreparation.ConfirmationRequired(preview)),
            ),
        )
        assertEquals(preview, controller.state.value.preview)
        controller.observe("epoch", null)
        assertEquals(null, controller.state.value.preview)
        assertFalse(controller.confirm())
        assertFalse(controller.choose("hdmi"))
        assertEquals(1, sent.size)
    }

    @Test
    fun nativePreparationCompletionBeforeReplyDoesNotLoseTheServerSheet() {
        val sent = mutableListOf<McRequest>()
        val controller = AudioSettingsController { sent.add(it) }
        controller.observe("epoch", outputs)
        controller.choose("hdmi")
        val request = sent.single()
        controller.observe(
            "epoch",
            outputs,
            listOf(OperationState("operation", request.id, OperationStatus.SUCCEEDED, cancellationRequested = false)),
        )
        val preview =
            ConfirmationPreview(
                "confirmation",
                "phone",
                ActionKind.CHANGE_AUDIO,
                listOf("TV"),
                Expected(serverEpoch = "epoch"),
                60000,
                confirmMethod = ConfirmMethod.AUDIO_CONFIRM,
            )
        controller.receive(
            McReply.Success(request.id, RemoteResult.AudioPrepare(ActivityPreparation.ConfirmationRequired(preview))),
        )
        assertEquals(preview, controller.state.value.preview)
        assertTrue(controller.confirm())
    }

    @Test
    fun terminalConfirmationFailuresReleaseSheetAndAllowAnotherPreparation() {
        for (code in listOf(WireErrorCode.CONFIRMATION_EXPIRED, WireErrorCode.NOT_FOUND)) {
            for (cancel in listOf(false, true)) {
                val sent = mutableListOf<McRequest>()
                val controller = AudioSettingsController { sent.add(it) }
                controller.observe("epoch", nativeAudioProjection("active"))
                controller.choose("hdmi")
                controller.receive(
                    McReply.Success(
                        sent.last().id,
                        RemoteResult.AudioPrepare(ActivityPreparation.ConfirmationRequired(audioConfirmation())),
                    ),
                )
                assertTrue(if (cancel) controller.cancel() else controller.confirm())
                controller.receive(audioFailure(sent.last().id, code))
                assertEquals("Terminal $code must release the sheet", null, controller.state.value.preview)
                assertTrue(controller.choose("hdmi"))
                assertTrue(sent.last().command is RemoteCommand.AudioPrepare)
            }
        }
    }

    @Test
    fun genuinelyStaleReplacementAndRetryableFailuresKeepTheAppropriateServerSheet() {
        val sent = mutableListOf<McRequest>()
        val controller = AudioSettingsController { sent.add(it) }
        controller.observe("epoch", nativeAudioProjection("active"))
        controller.choose("hdmi")
        controller.receive(
            McReply.Success(
                sent.last().id,
                RemoteResult.AudioPrepare(ActivityPreparation.ConfirmationRequired(audioConfirmation())),
            ),
        )
        controller.confirm()
        val replacement = audioConfirmation("replacement")
        controller.receive(audioFailure(sent.last().id, WireErrorCode.CONFIRMATION_STALE, replacement))
        assertEquals(replacement, controller.state.value.preview)
        controller.confirm()
        assertEquals("replacement", (sent.last().command as RemoteCommand.AudioConfirm).params.confirmationId)
        controller.receive(audioFailure(sent.last().id, WireErrorCode.UNAVAILABLE))
        assertEquals(replacement, controller.state.value.preview)
    }

    @Test
    fun realNativeIdlePreferenceAndLostSinkStatesHaveDistinctMessages() {
        val controller = AudioSettingsController { true }
        for ((name, message) in listOf(
            "idle" to "No playback stream has been confirmed.",
            "preference" to "Sound preference saved; no playback stream has been confirmed.",
            "lost" to "Audio output unavailable",
            "lostAfterClear" to "Audio output unavailable",
            "returnedWithoutStream" to "Audio output unavailable",
        )) {
            controller.observe("epoch", nativeAudioProjection(name))
            assertEquals(name, message, controller.state.value.message)
        }
    }
}
