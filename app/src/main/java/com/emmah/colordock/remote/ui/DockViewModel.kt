package com.emmah.colordock.remote.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.emmah.colordock.remote.ColorDockApplication
import com.emmah.colordock.remote.companion.DockControl
import com.emmah.colordock.remote.companion.DockMethod
import com.emmah.colordock.remote.companion.DockPatch
import com.emmah.colordock.remote.companion.DockResult
import com.emmah.colordock.remote.companion.WeatherLocation
import com.emmah.colordock.remote.weather.LocationSearch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

private const val MIN_LOCATION_QUERY_LENGTH = 2
private const val MAX_LOCATION_QUERY_LENGTH = 120

data class DockUiState(
    val saving: Boolean = false,
    val error: String? = null,
    val query: String = "",
    val searching: Boolean = false,
    val results: List<WeatherLocation> = emptyList(),
    val searched: Boolean = false,
)

class DockViewModel internal constructor(
    application: Application,
    private val dock: DockControl,
    private val connectionToken: () -> Any?,
    private val search: LocationSearch,
) : AndroidViewModel(application) {
    constructor(application: Application) : this(
        application,
        (application as ColorDockApplication).container.dock,
        { application.container.tv.connectionToken },
        LocationSearch(),
    )

    val state = dock.state
    private val mutableUi = MutableStateFlow(DockUiState())
    val ui = mutableUi.asStateFlow()
    private var searching: Job? = null
    private var searchGeneration = 0L

    fun join() {
        viewModelScope.launch {
            dock.join()
        }
    }

    fun patch(patch: DockPatch) {
        val snapshot = state.value.snapshot ?: return
        perform {
            dock.patch(snapshot.revision, patch)
        }
    }

    fun clock() {
        val snapshot =
            state.value.snapshot ?: return
        perform {
            dock.call(if (snapshot.clockActive) DockMethod.ClockStop else DockMethod.ClockStart, JSONObject())
        }
    }

    fun shelf(
        order: List<String>,
        hidden: List<String>,
        expectedRevision: Long,
    ) {
        perform {
            dock.patch(expectedRevision, DockPatch.Shelf(order, hidden))
        }
    }

    fun location(location: WeatherLocation) {
        patch(DockPatch.Weather(true, location))
    }

    private fun perform(action: suspend () -> DockResult<*>) {
        if (ui.value.saving) return
        val origin = connectionToken()
        viewModelScope.launch {
            mutableUi.value = ui.value.copy(saving = true, error = null)
            try {
                val reply = action()
                if (origin !== connectionToken()) return@launch
                when (reply) {
                    is DockResult.Failure -> mutableUi.value = ui.value.copy(error = reply.message)
                    is DockResult.Success -> dock.snapshot()
                }
            } finally {
                mutableUi.value = ui.value.copy(saving = false)
            }
        }
    }

    fun query(value: String) {
        searchGeneration++
        searching?.cancel()
        mutableUi.value =
            ui.value.copy(query = value, results = emptyList(), searched = false, searching = false, error = null)
    }

    fun search() {
        val query = ui.value.query
        if (query.trim().length !in
            MIN_LOCATION_QUERY_LENGTH..MAX_LOCATION_QUERY_LENGTH
        ) {
            return
        }
        val origin = ++searchGeneration
        searching?.cancel()
        searching =
            viewModelScope.launch {
                mutableUi.value = ui.value.copy(searching = true, error = null)
                try {
                    val results = search.search(query)
                    if (origin ==
                        searchGeneration
                    ) {
                        mutableUi.value = ui.value.copy(results = results, searched = true)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (origin ==
                        searchGeneration
                    ) {
                        mutableUi.value =
                            ui.value.copy(
                                error = "Location search is unavailable. Check your internet connection and try again.",
                            )
                    }
                } finally {
                    if (origin == searchGeneration) mutableUi.value = ui.value.copy(searching = false)
                }
            }
    }

    override fun onCleared() {
        searching?.cancel()
        search.close()
    }
}
