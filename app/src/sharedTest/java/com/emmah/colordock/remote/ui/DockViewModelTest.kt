package com.emmah.colordock.remote.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.emmah.colordock.remote.companion.DockConnectionState
import com.emmah.colordock.remote.companion.DockPatch
import com.emmah.colordock.remote.companion.DockTestFixtures
import com.emmah.colordock.remote.companion.WeatherLocation
import com.emmah.colordock.remote.weather.LocationSearch
import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DockViewModelTest {
    private fun ViewModelDockFixture.model(body: () -> String = { "{}" }): DockViewModel {
        val client =
            OkHttpClient
                .Builder()
                .addInterceptor { chain ->
                    Response
                        .Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("fixture")
                        .body(body().toResponseBody("application/json".toMediaType()))
                        .build()
                }.build()
        return DockViewModel(application, dock, { token }, LocationSearch(client)).also { store.put("dock", it) }
    }

    @Test fun actionsRequireSnapshotAndRefreshAcknowledgedChanges() =
        dockScenario {
            val model = model()
            model.patch(DockPatch.Clock(5))
            model.clock()
            assertTrue(requests.isEmpty())
            model.join()
            model.state.first { it.connection == DockConnectionState.Ready }
            model.patch(DockPatch.Clock(5))
            model.ui.first { !it.saving }
            assertEquals(
                0,
                requests
                    .first {
                        it.optString("method") == "settings.patch"
                    }.getJSONObject("params")
                    .getInt("expectedRevision"),
            )
            model.clock()
            model.ui.first { !it.saving }
            assertTrue(requests.any { it.optString("method") == "clock.start" })
            snapshot.put("clockActive", true)
            dock.snapshot()
            model.clock()
            model.ui.first { !it.saving }
            assertTrue(requests.any { it.optString("method") == "clock.stop" })
            model.shelf(listOf("one", "two"), listOf("two"), 7)
            model.ui.first { !it.saving }
            assertEquals(
                7,
                requests
                    .last {
                        it.optString("method") == "settings.patch"
                    }.getJSONObject("params")
                    .getInt("expectedRevision"),
            )
            model.location(WeatherLocation("Helsinki", "", "FI", 60.17, 24.94, "Europe/Helsinki"))
            model.ui.first { !it.saving }
            assertTrue(
                requests
                    .last {
                        it.optString("method") == "settings.patch"
                    }.getJSONObject("params")
                    .getJSONObject("patch")
                    .getJSONObject("weather")
                    .getBoolean("enabled"),
            )
        }

    @Test fun rejectedActionIsVisibleAndNextActionClearsError() =
        dockScenario {
            dock.join()
            val model = model()
            failure = "NOT_FOUND"
            model.clock()
            assertNotNull(model.ui.first { !it.saving && it.error != null }.error)
            failure = null
            model.clock()
            model.ui.first { !it.saving && it.error == null }
        }

    @Test fun concurrentSavesAreIgnoredAndOldSessionReplyCannotSetError() =
        dockScenario {
            dock.join()
            val model = model()
            response = { _, _ -> null }
            model.clock()
            assertTrue(model.ui.value.saving)
            val count = requests.size
            model.clock()
            assertEquals(count, requests.size)
            val request = requests.last()
            token = Any()
            messages.emit(DockTestFixtures.reply(request, org.json.JSONObject()))
            model.ui.first { !it.saving }
            assertNull(model.ui.value.error)
        }

    @Test fun searchValidatesQueryAndPublishesResultsAndEmptyResponses() =
        dockScenario {
            var calls = 0
            var result = """{"results":[{"name":"Helsinki","latitude":60.17,"longitude":24.94,
                "timezone":"Europe/Helsinki","country":"Finland"}]}"""
            val model =
                model {
                    calls++
                    result
                }
            model.query("a")
            model.search()
            model.query("a".repeat(121))
            model.search()
            assertEquals(0, calls)
            model.query("Helsinki")
            model.search()
            assertEquals(
                "Helsinki",
                model.ui
                    .first { it.searched && !it.searching }
                    .results
                    .single()
                    .name,
            )
            model.query("Unknown")
            assertFalse(model.ui.value.searched)
            assertTrue(
                model.ui.value.results
                    .isEmpty(),
            )
            result = "{}"
            model.search()
            assertTrue(
                model.ui
                    .first { it.searched && !it.searching }
                    .results
                    .isEmpty(),
            )
            assertEquals(2, calls)
        }

    @Test fun searchFailureIsVisibleAndEditingClearsIt() =
        dockScenario {
            val model = model { throw java.io.IOException("fixture") }
            model.query("Helsinki")
            model.search()
            val failed = model.ui.first { it.error != null && !it.searching }
            assertTrue(failed.error!!.startsWith("Location search is unavailable."))
            assertFalse(failed.searched)
            model.query("Espoo")
            assertNull(model.ui.value.error)
        }
}
