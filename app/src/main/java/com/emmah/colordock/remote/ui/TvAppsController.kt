package com.emmah.colordock.remote.ui

import com.emmah.colordock.remote.tv.TvApp
import com.emmah.colordock.remote.tv.TvErrorCode
import com.emmah.colordock.remote.tv.TvResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TvAppsUiState(
    val apps: List<TvApp> = emptyList(),
    val hasLoaded: Boolean = false,
    val loading: Boolean = false,
    val loadError: Boolean = false,
    val launchingId: String? = null,
    val launchErrorId: String? = null,
    val launchCompletedId: String? = null,
    val launchTitle: String? = null,
    val appUnavailable: Boolean = false,
    val connected: Boolean = false,
    val sessionGeneration: Long = 0,
    val catalogRevision: Long = 0,
)

internal class TvAppsController(
    private val scope: CoroutineScope,
    private val load: suspend () -> TvResult<List<TvApp>>,
    private val launch: suspend (String) -> TvResult<Unit>,
) {
    private val mutableState = MutableStateFlow(TvAppsUiState())
    val state = mutableState.asStateFlow()
    private var token: Any? = null
    private var generation = 0L
    private var refreshGeneration = 0L
    private var loading: Job? = null
    private var launching: Job? = null

    fun connectionChanged(token: Any?) {
        if (this.token === token) return
        this.token = token
        generation++
        loading?.cancel()
        launching?.cancel()
        mutableState.value =
            if (token == null) TvAppsUiState() else TvAppsUiState(connected = true, sessionGeneration = generation)
        if (token != null) refresh()
    }

    fun refresh() {
        if (token == null) return
        val origin = generation
        val request = ++refreshGeneration
        loading?.cancel()
        mutableState.update {
            it.copy(loading = true, loadError = false)
        }
        loading =
            scope.launch {
                val result =
                    try {
                        load()
                    } catch (
                        cancelled: CancellationException,
                    ) {
                        throw cancelled
                    } catch (_: Exception) {
                        TvResult.Failure(TvErrorCode.Unavailable, "TV apps unavailable")
                    }
                if (generation != origin || refreshGeneration != request) return@launch
                mutableState.update {
                    when (result) {
                        is TvResult.Success -> {
                            it.copy(
                                apps = result.value,
                                hasLoaded = true,
                                loading = false,
                                loadError = false,
                                catalogRevision =
                                    it.catalogRevision + 1,
                            )
                        }

                        is TvResult.Failure -> {
                            it.copy(loading = false, loadError = true)
                        }
                    }
                }
            }
    }

    fun open(appId: String) {
        if (token == null || state.value.launchingId != null) return
        val app =
            state.value.apps.find {
                it.id == appId
            }
        if (app == null) {
            mutableState.update {
                it.copy(launchErrorId = appId, appUnavailable = true, launchCompletedId = null)
            }
            return
        }
        val origin = generation
        mutableState.update {
            it.copy(
                launchingId = appId,
                launchTitle = app.title,
                launchErrorId = null,
                launchCompletedId = null,
                appUnavailable = false,
            )
        }
        launching =
            scope.launch {
                val result =
                    try {
                        launch(appId)
                    } catch (
                        cancelled: CancellationException,
                    ) {
                        throw cancelled
                    } catch (_: Exception) {
                        TvResult.Failure(TvErrorCode.Unavailable, "TV app unavailable")
                    }
                if (generation != origin) return@launch
                mutableState.update {
                    when (result) {
                        is TvResult.Success -> {
                            it.copy(launchingId = null, launchCompletedId = appId)
                        }

                        is TvResult.Failure -> {
                            it.copy(
                                launchingId = null,
                                launchErrorId = appId,
                                appUnavailable =
                                    result.code == TvErrorCode.InvalidRequest,
                            )
                        }
                    }
                }
            }
    }

    fun acknowledgeLaunchCompletion() {
        mutableState.update {
            it.copy(launchCompletedId = null)
        }
    }

    fun clearLaunchError() {
        mutableState.update {
            it.copy(launchErrorId = null, appUnavailable = false)
        }
    }
}
