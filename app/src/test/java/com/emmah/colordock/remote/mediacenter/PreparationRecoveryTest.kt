package com.emmah.colordock.remote.mediacenter

import com.emmah.colordock.remote.mediacenter.protocol.ActivityKind
import com.emmah.colordock.remote.mediacenter.protocol.ActivityPrepareParams
import com.emmah.colordock.remote.mediacenter.protocol.AudioPrepareParams
import com.emmah.colordock.remote.mediacenter.protocol.Expected
import com.emmah.colordock.remote.mediacenter.protocol.McRequest
import com.emmah.colordock.remote.mediacenter.protocol.OperationStatus
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PreparationRecoveryTest {
    @Test fun nestedActivityAndAudioAcceptanceRemainUnresolvedUntilNativeCompletion() =
        runTest {
            val transport = RecoveryControl()
            val repository = MediacenterRepository(transport, backgroundScope, MemoryPending()) { 0.5 }
            repository.connect(recoverySaved(), RecoveryRoute())
            runCurrent()
            transport.native()
            runCurrent()
            val commands =
                listOf(
                    RemoteCommand.ActivityPrepare(ActivityPrepareParams(ActivityKind.MUSIC, "song")),
                    RemoteCommand.AudioPrepare(AudioPrepareParams("hdmi")),
                )
            commands.forEachIndexed { index, command ->
                val id = "prepare-$index"
                assertTrue(repository.send(McRequest(id, command, Expected(serverEpoch = "epoch"))))
                transport.emit(
                    """{"v":1,"id":"$id","ok":true,
                    "result":{"status":"accepted","operation":{"operationId":"native-$index"}}}""",
                )
                runCurrent()
                val pending = repository.pending.value.single { it.requestId == id }
                assertEquals("native-$index", pending.operationId)
                assertEquals(OperationStatus.ACCEPTED, pending.status)
                assertFalse(pending.resolved)
            }
        }
}
