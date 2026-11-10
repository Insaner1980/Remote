package com.emmah.colordock.remote.ui.mediacenter

import com.emmah.colordock.remote.mediacenter.protocol.McRequest
import com.emmah.colordock.remote.mediacenter.protocol.WireErrorCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfirmationStatusReconcilerTest {
    @Test fun readsAreBoundedAndLateRepliesCannotCloseAReplacementOrNewEpoch() {
        var time = 0L
        var epoch = "epoch"
        var preview = audioConfirmation("first")
        val sent = mutableListOf<McRequest>()
        val resolved = mutableListOf<String>()
        val reconciler =
            ConfirmationStatusReconciler({ sent.add(it) }, { epoch to preview }, { resolved.add(it) }, { time })
        reconciler.refresh()
        repeat(20) { reconciler.refresh() }
        assertEquals(1, sent.size)
        time = 5000
        reconciler.refresh()
        assertEquals(2, sent.size)
        reconciler.receive(audioFailure(sent.first().id, WireErrorCode.NOT_FOUND))
        assertTrue(resolved.isEmpty())
        preview = audioConfirmation("replacement")
        reconciler.receive(audioFailure(sent.last().id, WireErrorCode.NOT_FOUND))
        assertTrue(resolved.isEmpty())
        time = 6000
        reconciler.refresh()
        epoch = "new-epoch"
        reconciler.receive(audioFailure(sent.last().id, WireErrorCode.CONFIRMATION_EXPIRED))
        assertTrue(resolved.isEmpty())
        time = 7000
        reconciler.refresh()
        reconciler.receive(audioFailure(sent.last().id, WireErrorCode.UNAVAILABLE))
        assertTrue(resolved.isEmpty())
        time = 8000
        reconciler.refresh()
        reconciler.receive(audioFailure(sent.last().id, WireErrorCode.NOT_FOUND))
        assertEquals(listOf("replacement"), resolved)
    }
}
