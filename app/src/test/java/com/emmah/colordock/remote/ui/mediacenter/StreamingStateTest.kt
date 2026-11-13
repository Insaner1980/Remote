package com.emmah.colordock.remote.ui.mediacenter

import com.emmah.colordock.remote.mediacenter.RecoveryFixture
import com.emmah.colordock.remote.mediacenter.protocol.ActionKind
import com.emmah.colordock.remote.mediacenter.protocol.ActivityMode
import com.emmah.colordock.remote.mediacenter.protocol.ActivityPreparation
import com.emmah.colordock.remote.mediacenter.protocol.BrowserApprovalContext
import com.emmah.colordock.remote.mediacenter.protocol.BrowserOpenParams
import com.emmah.colordock.remote.mediacenter.protocol.CancellationAck
import com.emmah.colordock.remote.mediacenter.protocol.Capabilities
import com.emmah.colordock.remote.mediacenter.protocol.ConfirmMethod
import com.emmah.colordock.remote.mediacenter.protocol.ConfirmationPreview
import com.emmah.colordock.remote.mediacenter.protocol.Expected
import com.emmah.colordock.remote.mediacenter.protocol.McReply
import com.emmah.colordock.remote.mediacenter.protocol.McRequest
import com.emmah.colordock.remote.mediacenter.protocol.MethodName
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand
import com.emmah.colordock.remote.mediacenter.protocol.RemoteJson
import com.emmah.colordock.remote.mediacenter.protocol.RemoteResult
import com.emmah.colordock.remote.mediacenter.protocol.ServiceCapability
import com.emmah.colordock.remote.mediacenter.protocol.SupportedState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingStateTest {
    @Test fun launchUsesOnlyAdvertisedServicesAndConfirmationSendsOnlyThePreviewId() =
        runTest {
            val sent = mutableListOf<McRequest>()
            val media = MediaSessionController(backgroundScope) { sent.add(it) }
            media.observe(RemoteJson.parseSnapshot(RecoveryFixture.snapshot()))
            val available = StreamingState(capabilities = catalog())
            media.openBrowser(available.copy(capabilities = catalog().copy(browserBridge = false)), "youtube")
            media.openBrowser(available, "unlisted")
            media.openBrowser(available, "hbo_max")
            assertTrue(sent.isEmpty())
            media.openBrowser(available, "youtube")
            val launch = sent.single()
            assertEquals(BrowserOpenParams(serviceId = "youtube", profileId = "auto"), browserOpen(launch))
            val preview =
                ConfirmationPreview(
                    "launch-confirmation",
                    "phone",
                    ActionKind.OPEN_BROWSER,
                    listOf("Current video"),
                    Expected(serverEpoch = "epoch", sessionId = "previous"),
                    60000,
                    confirmMethod = ConfirmMethod.BROWSER_OPEN,
                )
            media.receive(
                McReply.Success(launch.id, RemoteResult.BrowserOpen(ActivityPreparation.ConfirmationRequired(preview))),
            )
            assertEquals(preview, media.state.value.preview)
            media.confirm()
            media.confirm()
            val confirm = sent.last()
            assertEquals(2, sent.size)
            assertEquals(BrowserOpenParams(confirmationId = "launch-confirmation"), browserOpen(confirm))
            assertEquals(preview.expected, confirm.expected)
        }

    @Test fun browserReadCannotRestoreReplacedTargetsOrContinueWhenBridgeLosesInput() {
        val sent = mutableListOf<McRequest>()
        var time = 0L
        val controller = StreamingController({ sent.add(it) }, { time })
        val original = RemoteJson.parseSnapshot(RecoveryFixture.snapshot())
        val browser =
            original.copy(
                activity = original.activity.copy(mode = ActivityMode.BROWSER, sessionId = "browser"),
            )
        controller.observe(browser)
        controller.refresh()
        val capsRequest = sent.single { it.command.method == MethodName.CAPABILITIES_GET }
        controller.receive(McReply.Success(capsRequest.id, RemoteResult.CapabilitiesGet(catalog())))
        controller.refresh()
        val old = sent.single { it.command.method == MethodName.BROWSER_STATE }
        controller.observe(browser.copy(activity = browser.activity.copy(sessionId = "new")))
        controller.receive(McReply.Success(old.id, RemoteResult.BrowserState(browserState())))
        assertNull(controller.state.value.target)
        controller.refresh()
        val current = sent.last()
        controller.receive(
            McReply.Success(current.id, RemoteResult.BrowserState(browserState().copy(browserSessionId = "new"))),
        )
        assertEquals(BrowserTarget(browser.serverEpoch, "new", "1"), controller.state.value.target)
        time += 400
        controller.refresh()
        val detached = browserState().copy(browserSessionId = "new", enrolled = false)
        controller.receive(McReply.Success(sent.last().id, RemoteResult.BrowserState(detached)))
        assertNull(controller.state.value.target)
        assertFalse(controller.state.value.canLaunch("hbo_max"))
        controller.observe(null)
        assertNull(controller.state.value.capabilities)
    }

    @Test fun originApprovalIsBoundToTheCurrentPhoneAndTargetGeneration() =
        runTest {
            val sent = mutableListOf<McRequest>()
            val media = MediaSessionController(backgroundScope) { sent.add(it) }
            media.observe(RemoteJson.parseSnapshot(RecoveryFixture.snapshot()))
            val preview =
                ConfirmationPreview(
                    "origin-preview",
                    "phone",
                    ActionKind.APPROVE_BROWSER_ORIGIN,
                    listOf("login.example"),
                    Expected(serverEpoch = "epoch"),
                    60000,
                    browserContext = BrowserApprovalContext("browser", "1", "login.example"),
                    confirmMethod = ConfirmMethod.BROWSER_OPEN,
                )
            val target = BrowserTarget("epoch", "browser", "1")
            val streaming =
                StreamingState(catalog(), browserState().copy(pendingConfirmation = preview), target)
            media.observeBrowserApproval(streaming, "other-phone")
            assertNull(media.state.value.preview)
            media.observeBrowserApproval(streaming.copy(target = target.copy(generation = "2")), "phone")
            assertNull(media.state.value.preview)
            media.observeBrowserApproval(streaming, "phone")
            assertEquals(preview, media.state.value.preview)
            media.cancelConfirmation()
            val cancel = sent.single()
            assertTrue(cancel.command is RemoteCommand.ConfirmationsCancel)
            media.receive(McReply.Success(cancel.id, RemoteResult.ConfirmationsCancel(CancellationAck(true))))
            media.observeBrowserApproval(streaming, "phone")
            assertNull(media.state.value.preview)
        }

    private fun browserOpen(request: McRequest) = (request.command as RemoteCommand.BrowserOpen).params

    private fun catalog() =
        Capabilities(
            browserMethods,
            listOf(
                ServiceCapability("youtube", SupportedState.PARTIAL, caps),
                ServiceCapability("hbo_max", SupportedState.UNSUPPORTED, caps, "Profile unavailable."),
            ),
            true,
        )
}
