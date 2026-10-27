package com.emmah.colordock.remote.ui

import com.emmah.colordock.remote.tv.TvApp
import com.emmah.colordock.remote.tv.TvErrorCode
import com.emmah.colordock.remote.tv.TvResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TvAppsControllerTest {
    private val netflix = TvApp("netflix", "Netflix", null)
    private val failure = TvResult.Failure(TvErrorCode.Unavailable, "fixture")

    @Test fun readySessionLoadsOnce() =
        runTest {
            var loads = 0
            val controller =
                TvAppsController(backgroundScope, {
                    loads++
                    TvResult.Success(listOf(netflix))
                }, {
                    TvResult.Success(Unit)
                })
            val token = Any()
            controller.connectionChanged(token)
            runCurrent()
            controller.connectionChanged(token)
            runCurrent()
            assertEquals(1, loads)
            assertTrue(controller.state.value.hasLoaded)
            assertEquals(listOf(netflix), controller.state.value.apps)
        }

    @Test fun lateCatalogFromOldSessionIsIgnored() =
        runTest {
            val old = CompletableDeferred<TvResult<List<TvApp>>>()
            var calls = 0
            val controller =
                TvAppsController(backgroundScope, {
                    if (calls++ == 0) {
                        withContext(NonCancellable) {
                            old.await()
                        }
                    } else {
                        TvResult.Success(emptyList())
                    }
                }, {
                    TvResult.Success(Unit)
                })
            controller.connectionChanged(Any())
            runCurrent()
            controller.connectionChanged(Any())
            runCurrent()
            old.complete(TvResult.Success(listOf(netflix)))
            runCurrent()
            assertTrue(controller.state.value.hasLoaded)
            assertEquals(emptyList<TvApp>(), controller.state.value.apps)
        }

    @Test fun refreshFailureKeepsSameSessionList() =
        runTest {
            var result: TvResult<List<TvApp>> = TvResult.Success(listOf(netflix))
            val controller =
                TvAppsController(backgroundScope, {
                    result
                }, {
                    TvResult.Success(Unit)
                })
            controller.connectionChanged(Any())
            runCurrent()
            val revision = controller.state.value.catalogRevision
            result = failure
            controller.refresh()
            runCurrent()
            assertTrue(controller.state.value.loadError)
            assertTrue(controller.state.value.hasLoaded)
            assertEquals(listOf(netflix), controller.state.value.apps)
            assertEquals(revision, controller.state.value.catalogRevision)
        }

    @Test fun failedFirstLoadIsNotEmptySuccess() =
        runTest {
            val controller =
                TvAppsController(backgroundScope, {
                    failure
                }, {
                    TvResult.Success(Unit)
                })
            controller.connectionChanged(Any())
            runCurrent()
            assertFalse(controller.state.value.hasLoaded)
            assertTrue(controller.state.value.loadError)
            assertFalse(controller.state.value.loading)
        }

    @Test fun doubleTapSendsOneLaunch() =
        runTest {
            val reply = CompletableDeferred<TvResult<Unit>>()
            val sent = mutableListOf<String>()
            val controller =
                TvAppsController(backgroundScope, {
                    TvResult.Success(listOf(netflix))
                }, {
                    sent += it
                    reply.await()
                })
            controller.connectionChanged(Any())
            runCurrent()
            controller.open("netflix")
            controller.open("netflix")
            runCurrent()
            assertEquals(listOf("netflix"), sent)
            assertEquals("netflix", controller.state.value.launchingId)
            reply.complete(TvResult.Success(Unit))
            runCurrent()
            assertEquals("netflix", controller.state.value.launchCompletedId)
        }

    @Test fun unknownIdDoesNotLaunch() =
        runTest {
            var launches = 0
            val controller =
                TvAppsController(backgroundScope, {
                    TvResult.Success(listOf(netflix))
                }, {
                    launches++
                    TvResult.Success(Unit)
                })
            controller.connectionChanged(Any())
            runCurrent()
            controller.open("unknown")
            runCurrent()
            assertEquals(0, launches)
            assertEquals("unknown", controller.state.value.launchErrorId)
            assertTrue(controller.state.value.appUnavailable)
        }

    @Test fun disconnectCancelsPendingLaunch() =
        runTest {
            var cancelled = false
            val controller =
                TvAppsController(backgroundScope, {
                    TvResult.Success(listOf(netflix))
                }, {
                    try {
                        awaitCancellation()
                    } finally {
                        cancelled = true
                    }
                })
            controller.connectionChanged(Any())
            runCurrent()
            controller.open("netflix")
            runCurrent()
            controller.connectionChanged(null)
            runCurrent()
            assertTrue(cancelled)
            assertEquals(TvAppsUiState(), controller.state.value)
        }

    @Test fun concurrentRefreshDoesNotEraseLaunchProgress() =
        runTest {
            val launchReply = CompletableDeferred<TvResult<Unit>>()
            val controller =
                TvAppsController(backgroundScope, {
                    TvResult.Success(listOf(netflix))
                }, {
                    launchReply.await()
                })
            controller.connectionChanged(Any())
            runCurrent()
            controller.open("netflix")
            runCurrent()
            controller.refresh()
            runCurrent()
            assertEquals("netflix", controller.state.value.launchingId)
            assertFalse(controller.state.value.loading)
            launchReply.complete(failure)
            runCurrent()
            assertEquals("netflix", controller.state.value.launchErrorId)
            assertFalse(controller.state.value.appUnavailable)
        }

    @Test fun consumedSuccessDoesNotDismissLaterPicker() =
        runTest {
            val controller =
                TvAppsController(backgroundScope, {
                    TvResult.Success(listOf(netflix))
                }, {
                    TvResult.Success(Unit)
                })
            controller.connectionChanged(Any())
            runCurrent()
            controller.open("netflix")
            runCurrent()
            controller.acknowledgeLaunchCompletion()
            controller.refresh()
            runCurrent()
            assertNull(controller.state.value.launchCompletedId)
        }

    @Test fun replacedRefreshAndLateLaunchCannotPublish() =
        runTest {
            var loads = 0
            val delayed = CompletableDeferred<TvResult<List<TvApp>>>()
            val launched = CompletableDeferred<TvResult<Unit>>()
            val controller =
                TvAppsController(backgroundScope, {
                    if (++loads == 2) {
                        withContext(NonCancellable) {
                            delayed.await()
                        }
                    } else {
                        TvResult.Success(listOf(netflix))
                    }
                }, {
                    withContext(NonCancellable) {
                        launched.await()
                    }
                })
            controller.connectionChanged(Any())
            runCurrent()
            controller.refresh()
            runCurrent()
            controller.refresh()
            runCurrent()
            delayed.complete(TvResult.Success(emptyList()))
            runCurrent()
            assertEquals(listOf(netflix), controller.state.value.apps)
            controller.open("netflix")
            runCurrent()
            controller.connectionChanged(Any())
            runCurrent()
            launched.complete(TvResult.Success(Unit))
            runCurrent()
            assertNull(controller.state.value.launchCompletedId)
        }

    @Test fun removedSelectionIsNotSentAfterRefresh() =
        runTest {
            var list = listOf(netflix)
            var launches = 0
            val controller =
                TvAppsController(backgroundScope, {
                    TvResult.Success(list)
                }, {
                    launches++
                    TvResult.Success(Unit)
                })
            controller.connectionChanged(Any())
            runCurrent()
            list = emptyList()
            controller.refresh()
            runCurrent()
            controller.open("netflix")
            assertEquals(0, launches)
            assertTrue(controller.state.value.appUnavailable)
        }
}
