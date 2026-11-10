package com.emmah.colordock.remote.mediacenter

import com.emmah.colordock.remote.mediacenter.protocol.BrowserTextParams
import com.emmah.colordock.remote.mediacenter.protocol.Delivery
import com.emmah.colordock.remote.mediacenter.protocol.Empty
import com.emmah.colordock.remote.mediacenter.protocol.Expected
import com.emmah.colordock.remote.mediacenter.protocol.McRequest
import com.emmah.colordock.remote.mediacenter.protocol.OperationStatus
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand
import com.emmah.colordock.remote.mediacenter.protocol.Scope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
class RequestRecoveryTest {
    @Test fun acceptedNextWithLostReplyQueriesIndividualRequestAfterReconnectAndNeverSkipsAgain() =
        runTest {
            val control = RecoveryControl()
            val journal = MemoryPending()
            val repository = MediacenterRepository(control, backgroundScope, journal) { 0.5 }
            repository.connect(recoverySaved(), RecoveryRoute())
            runCurrent()
            control.native()
            runCurrent()
            assertTrue(
                repository.send(
                    McRequest(
                        "next-request",
                        RemoteCommand.QueueNext(Empty),
                        Expected(serverEpoch = "epoch", sessionId = "session", queueRevision = "0"),
                    ),
                ),
            )
            assertEquals("next-request", journal.records.single().requestId)
            control.state.value = MediacenterConnectionState.Failed
            runCurrent()
            advanceTimeBy(1000)
            runCurrent()
            control.native()
            runCurrent()
            assertEquals(1, control.sent.count { it.command is RemoteCommand.QueueNext })
            val lookup = control.sent.last { it.command is RemoteCommand.OperationsGet }
            assertEquals("next-request", (lookup.command as RemoteCommand.OperationsGet).params.requestId)
            control.emit(operationReply(lookup.id, "next-request", "succeeded"))
            runCurrent()
            assertEquals(
                OperationStatus.SUCCEEDED,
                repository.pending.value
                    .single()
                    .status,
            )
        }

    @Test fun restoredRunningOperationOmittedFromBoundedSnapshotIsQueriedByOperationId() =
        runTest {
            val control = RecoveryControl()
            val journal =
                MemoryPending().apply {
                    records =
                        listOf(
                            PendingCommand(
                                "server",
                                "old-request",
                                "old-epoch",
                                "running-op",
                                OperationStatus.RUNNING,
                                pairedDeviceId = "phone",
                            ),
                        )
                }
            val repository = MediacenterRepository(control, backgroundScope, journal) { 0.5 }
            repository.connect(recoverySaved(), RecoveryRoute())
            runCurrent()
            control.native("new-epoch")
            runCurrent()
            val lookup = control.sent.single { it.command is RemoteCommand.OperationsGet }
            assertEquals("running-op", (lookup.command as RemoteCommand.OperationsGet).params.operationId)
            assertEquals(
                OperationStatus.RUNNING,
                repository.pending.value
                    .single()
                    .status,
            )
            control.emit(operationReply(lookup.id, "old-request", "outcome_unknown", "running-op"))
            runCurrent()
            assertEquals(
                OperationStatus.OUTCOME_UNKNOWN,
                repository.pending.value
                    .single()
                    .status,
            )
            assertFalse(control.sent.any { it.command.delivery != Delivery.READ })
        }

    @Test fun acknowledgementDeadlineQueriesInsteadOfFailingOrReplayingAcceptedWork() =
        runTest {
            val control = RecoveryControl()
            val repository = MediacenterRepository(control, backgroundScope, MemoryPending()) { 0.5 }
            repository.connect(recoverySaved(), RecoveryRoute())
            runCurrent()
            control.native()
            runCurrent()
            repository.send(
                McRequest(
                    "next-request",
                    RemoteCommand.QueueNext(Empty),
                    Expected(serverEpoch = "epoch", sessionId = "session", queueRevision = "0"),
                ),
            )
            control.emit("""{"v":1,"id":"next-request","ok":true,"result":{"status":"accepted","operationId":"op"}}""")
            runCurrent()
            advanceTimeBy(5000)
            runCurrent()
            assertEquals(
                OperationStatus.ACCEPTED,
                repository.pending.value
                    .single()
                    .status,
            )
            assertTrue(control.sent.any { it.command is RemoteCommand.OperationsGet })
            assertEquals(1, control.sent.count { it.command is RemoteCommand.QueueNext })
        }

    @Test fun fileJournalSurvivesRecreationAndContainsOnlyRecoveryMetadata() {
        val directory = Files.createTempDirectory("mc-recovery-fixture").toFile()
        try {
            val file = directory.resolve("pending")
            FilePendingCommandStore(
                file,
            ).write(
                listOf(
                    PendingCommand(
                        "server",
                        "next-request",
                        "epoch",
                        "op",
                        OperationStatus.RUNNING,
                        pairedDeviceId = "phone",
                    ),
                ),
            )
            val restored = FilePendingCommandStore(file).read().single()
            assertEquals("next-request", restored.requestId)
            assertEquals("phone", restored.pairedDeviceId)
            assertEquals(OperationStatus.RUNNING, restored.status)
            assertFalse(file.readText().contains("credential"))
            assertFalse(file.readText().contains("params"))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun corruptJournalDoesNotCrashStartupAndBlocksDurableSubmission() =
        runTest {
            val broken =
                object : PendingCommandStore {
                    override fun read(): List<PendingCommand> = throw java.io.IOException("Synthetic corrupt journal")

                    override fun write(commands: List<PendingCommand>) =
                        error("Do not overwrite uncertain recovery records")
                }
            val control = RecoveryControl()
            val result = runCatching { MediacenterRepository(control, backgroundScope, broken) }
            assertTrue("Corrupt journal must produce a usable fail-closed repository", result.isSuccess)
            val repository = result.getOrThrow()
            assertFalse(repository.recoveryAvailable.value)
            repository.connect(recoverySaved(), RecoveryRoute())
            runCurrent()
            control.native()
            runCurrent()
            assertFalse(
                repository.send(
                    McRequest(
                        "next",
                        RemoteCommand.QueueNext(Empty),
                        Expected(serverEpoch = "epoch", sessionId = "session", queueRevision = "0"),
                    ),
                ),
            )
            assertFalse(control.sent.any { it.command is RemoteCommand.QueueNext })
        }

    @Test fun uncertainTextIsObservableButNeverPersistedOrReplayedAfterNewEpoch() =
        runTest {
            val control = RecoveryControl()
            val journal = MemoryPending()
            val repository = MediacenterRepository(control, backgroundScope, journal) { 0.5 }
            repository.connect(recoverySaved(), RecoveryRoute())
            runCurrent()
            control.native()
            runCurrent()
            assertTrue(
                repository.send(
                    McRequest(
                        "text",
                        RemoteCommand.BrowserText(BrowserTextParams("browser", "1", "synthetic-secret")),
                        Expected(serverEpoch = "epoch"),
                    ),
                ),
            )
            control.state.value = MediacenterConnectionState.Failed
            runCurrent()
            assertTrue(repository.inputOutcomeUncertain.value)
            assertTrue(journal.records.isEmpty())
            advanceTimeBy(1000)
            runCurrent()
            control.native("new-epoch")
            runCurrent()
            assertEquals(1, control.sent.count { it.command is RemoteCommand.BrowserText })
            assertFalse(control.sent.any { it.command is RemoteCommand.OperationsGet })
        }

    @Test fun publishedRecoveryNeverAttributesAnotherServerOrPairingRequestsToCurrentPhone() =
        runTest {
            val control = RecoveryControl()
            val journal =
                MemoryPending().apply {
                    records =
                        listOf(
                            PendingCommand("other-server", "foreign", "epoch", pairedDeviceId = "phone"),
                            PendingCommand("server", "old-pairing", "epoch", pairedDeviceId = "old-phone"),
                            PendingCommand("server", "current", "epoch", pairedDeviceId = "phone"),
                        )
                }
            val repository = MediacenterRepository(control, backgroundScope, journal)
            repository.connect(recoverySaved(), RecoveryRoute())
            runCurrent()
            control.native()
            runCurrent()
            assertEquals(listOf("current"), repository.pending.value.map { it.requestId })
            assertEquals(
                listOf("current"),
                control.sent.mapNotNull {
                    (it.command as? RemoteCommand.OperationsGet)?.params?.requestId
                },
            )
        }

    @Test fun forgetClearsSavedIdentityRecoveryEvenAfterDisconnectAndResetsTransientUncertainty() =
        runTest {
            val control = RecoveryControl()
            val journal = MemoryPending()
            val repository = MediacenterRepository(control, backgroundScope, journal)
            repository.connect(recoverySaved(), RecoveryRoute())
            runCurrent()
            control.native()
            runCurrent()
            assertTrue(
                repository.send(
                    McRequest(
                        "next",
                        RemoteCommand.QueueNext(Empty),
                        Expected(serverEpoch = "epoch", sessionId = "session", queueRevision = "0"),
                    ),
                ),
            )
            assertTrue(
                repository.send(
                    McRequest(
                        "text",
                        RemoteCommand.BrowserText(BrowserTextParams("browser", "1", "synthetic-input")),
                        Expected(serverEpoch = "epoch"),
                    ),
                ),
            )
            repository.disconnect()
            runCurrent()
            assertTrue(repository.uncertainInput)
            assertTrue(repository.forget("server"))
            runCurrent()
            assertTrue(journal.records.isEmpty())
            assertFalse(repository.inputOutcomeUncertain.value)
            assertTrue(repository.pending.value.isEmpty())
        }

    @Test fun newHelloInvalidatesTransientInputAndReadinessBeforeFreshNativeSnapshot() =
        runTest {
            val control = RecoveryControl()
            val journal = MemoryPending()
            val repository = MediacenterRepository(control, backgroundScope, journal)
            repository.connect(recoverySaved(), RecoveryRoute())
            runCurrent()
            control.native()
            runCurrent()
            assertTrue(
                repository.send(
                    McRequest(
                        "text",
                        RemoteCommand.BrowserText(BrowserTextParams("browser", "1", "synthetic-input")),
                        Expected(serverEpoch = "epoch"),
                    ),
                ),
            )
            control.emit(RecoveryFixture.hello("new-epoch"))
            runCurrent()
            assertTrue(repository.inputOutcomeUncertain.value)
            assertEquals(MediacenterConnectionState.Synchronizing, repository.state.value)
            assertNull(repository.snapshot.value)
            control.emit(RecoveryFixture.snapshot("new-epoch"))
            runCurrent()
            assertEquals(MediacenterConnectionState.Connected, repository.state.value)
            assertEquals(1, control.sent.count { it.command is RemoteCommand.BrowserText })
            assertTrue(journal.records.isEmpty())
        }

    @Test fun largeRecoverySetIsPacedBoundedAndFairEvenWhenEarlyOperationsStayRunning() =
        runTest {
            val control = RecoveryControl()
            val journal =
                MemoryPending().apply {
                    records =
                        (0 until 60).map {
                            PendingCommand(
                                "server",
                                "request-$it",
                                "epoch",
                                "op-$it",
                                OperationStatus.RUNNING,
                                pairedDeviceId = "phone",
                            )
                        }
                }
            val repository = MediacenterRepository(control, backgroundScope, journal)
            repository.connect(recoverySaved(), RecoveryRoute())
            runCurrent()
            control.native()
            runCurrent()
            assertTrue("Initial snapshot must not flood normal command burst", control.sent.size <= 1)
            advanceTimeBy(1000)
            runCurrent()
            assertTrue("At most4 pending recovery reads may be in flight", control.sent.size <= 4)
            advanceTimeBy(4000)
            runCurrent()
            val answered = mutableSetOf<String>()
            repeat(80) {
                val unanswered = control.sent.filter { it.id !in answered }
                for (lookup in unanswered) {
                    answered.add(lookup.id)
                    val operation = (lookup.command as RemoteCommand.OperationsGet).params.operationId!!
                    val request = "request-" + operation.removePrefix("op-")
                    control.emit(operationReply(lookup.id, request, "running", operation))
                }
                runCurrent()
                advanceTimeBy(250)
                runCurrent()
            }
            val first60 = control.sent.map { (it.command as RemoteCommand.OperationsGet).params.operationId }.take(60)
            assertEquals(60, first60.distinct().size)
            assertEquals(
                OperationStatus.RUNNING,
                repository.pending.value
                    .first()
                    .status,
            )
        }

    @Test fun cachedRunningSnapshotCannotRegressSucceededLookupOnUnrelatedEvent() =
        runTest {
            val control = RecoveryControl()
            val journal =
                MemoryPending().apply {
                    records =
                        listOf(
                            PendingCommand(
                                "server",
                                "next-request",
                                "epoch",
                                "op",
                                OperationStatus.RUNNING,
                                pairedDeviceId = "phone",
                            ),
                        )
                }
            val repository = MediacenterRepository(control, backgroundScope, journal)
            repository.connect(recoverySaved(), RecoveryRoute())
            runCurrent()
            control.state.value = MediacenterConnectionState.Connected
            control.emit(RecoveryFixture.hello())
            val native =
                JSONObject(RecoveryFixture.snapshot()).apply {
                    getJSONObject("operations").put(
                        "items",
                        org.json.JSONArray().put(
                            reviewOperation("running"),
                        ),
                    )
                }
            control.emit(native.toString())
            runCurrent()
            val lookup = control.sent.single { it.command is RemoteCommand.OperationsGet }
            control.emit(operationReply(lookup.id, "next-request", "succeeded"))
            runCurrent()
            assertEquals(
                OperationStatus.SUCCEEDED,
                repository.pending.value
                    .single()
                    .status,
            )
            control.emit(RecoveryFixture.event("1", "1"))
            runCurrent()
            val terminal = repository.pending.value.single()
            assertEquals(OperationStatus.SUCCEEDED, terminal.status)
            assertTrue(terminal.resolved)
            assertEquals(OperationStatus.SUCCEEDED, journal.records.single().status)
            advanceTimeBy(2500)
            runCurrent()
            assertEquals(1, control.sent.count { it.command is RemoteCommand.OperationsGet })
            assertFalse(control.sent.any { it.command.delivery != Delivery.READ })
        }

    @Test fun delayedAcceptanceCannotReplaceTerminalOperationEventOrRestartItsRecovery() =
        runTest {
            val control = RecoveryControl()
            val journal = MemoryPending()
            val repository = MediacenterRepository(control, backgroundScope, journal)
            repository.connect(recoverySaved(), RecoveryRoute())
            runCurrent()
            control.native()
            runCurrent()
            assertTrue(
                repository.send(
                    McRequest(
                        "next-request",
                        RemoteCommand.QueueNext(Empty),
                        Expected(serverEpoch = "epoch", sessionId = "session", queueRevision = "0"),
                    ),
                ),
            )
            val event =
                JSONObject()
                    .put("topic", "operations")
                    .put("serverEpoch", "epoch")
                    .put("sequence", "1")
                    .put(
                        "data",
                        JSONObject().put("revision", "1").put(
                            "items",
                            org.json.JSONArray().put(
                                reviewOperation("succeeded"),
                            ),
                        ),
                    )
            control.emit(event.toString())
            runCurrent()
            assertEquals(
                OperationStatus.SUCCEEDED,
                repository.pending.value
                    .single()
                    .status,
            )
            control.emit("""{"v":1,"id":"next-request","ok":true,"result":{"status":"accepted","operationId":"op"}}""")
            runCurrent()
            assertEquals(
                OperationStatus.SUCCEEDED,
                repository.pending.value
                    .single()
                    .status,
            )
            assertTrue(
                repository.pending.value
                    .single()
                    .resolved,
            )
            assertEquals(OperationStatus.SUCCEEDED, journal.records.single().status)
            advanceTimeBy(2500)
            runCurrent()
            assertEquals(1, control.sent.count { it.command is RemoteCommand.QueueNext })
            assertFalse(control.sent.any { it.command is RemoteCommand.OperationsGet })
        }

    private fun reviewOperation(status: String): JSONObject =
        JSONObject()
            .put("operationId", "op")
            .put("requestId", "next-request")
            .put("status", status)
            .put("cancellationRequested", false)

    private fun operationReply(
        id: String,
        request: String,
        status: String,
        operation: String = "op",
    ) = JSONObject()
        .put("v", 1)
        .put("id", id)
        .put("ok", true)
        .put(
            "result",
            JSONObject()
                .put(
                    "operationId",
                    operation,
                ).put("requestId", request)
                .put("status", status)
                .put("cancellationRequested", false),
        ).toString()
}
