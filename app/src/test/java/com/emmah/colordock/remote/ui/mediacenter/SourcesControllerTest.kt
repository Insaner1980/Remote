package com.emmah.colordock.remote.ui.mediacenter

import com.emmah.colordock.remote.mediacenter.protocol.Availability
import com.emmah.colordock.remote.mediacenter.protocol.McReply
import com.emmah.colordock.remote.mediacenter.protocol.McRequest
import com.emmah.colordock.remote.mediacenter.protocol.MediaKind
import com.emmah.colordock.remote.mediacenter.protocol.RemoteResult
import com.emmah.colordock.remote.mediacenter.protocol.Source
import com.emmah.colordock.remote.mediacenter.protocol.SourcePage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourcesControllerTest {
    @Test fun revisionRefreshUsesNativeScanningAndDropsPreviousOwnerReply() {
        val sent = mutableListOf<McRequest>()
        val controller = SourcesController { sent.add(it) }
        controller.observe("epoch", "server", "1")
        val old = sent.last().id
        controller.observe("epoch", "server", "2")
        assertEquals(2, sent.size)
        val source = Source("source", "Music", listOf(MediaKind.MUSIC), Availability.AVAILABLE, true)
        controller.receive(McReply.Success(old, RemoteResult.SourcesList(SourcePage("1", listOf(source)))))
        assertTrue(
            controller.state.value.items
                .isEmpty(),
        )
        controller.receive(McReply.Success(sent.last().id, RemoteResult.SourcesList(SourcePage("2", listOf(source)))))
        assertTrue(
            controller.state.value.items
                .single()
                .scanning,
        )
        controller.observe(null, null, null)
        assertTrue(
            controller.state.value.items
                .isEmpty(),
        )
        assertFalse(controller.state.value.connected)
    }
}
