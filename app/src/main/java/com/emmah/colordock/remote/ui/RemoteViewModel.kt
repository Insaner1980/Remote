package com.emmah.colordock.remote.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.emmah.colordock.remote.ColorDockApplication
import com.emmah.colordock.remote.images.AppIconCache
import com.emmah.colordock.remote.tv.DiscoveredTv
import com.emmah.colordock.remote.tv.LgTvClient
import com.emmah.colordock.remote.tv.TvApp
import com.emmah.colordock.remote.tv.TvChannel
import com.emmah.colordock.remote.tv.TvCommand
import com.emmah.colordock.remote.tv.TvConnectionState
import com.emmah.colordock.remote.tv.TvCredentialStore
import com.emmah.colordock.remote.tv.TvDiscovery
import com.emmah.colordock.remote.tv.TvErrorCode
import com.emmah.colordock.remote.tv.TvResult
import com.emmah.colordock.remote.tv.TvTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

private const val REPEAT_START_DELAY_MS = 450L
private const val REPEAT_INTERVAL_MS = 180L
private const val BACKGROUND_DISCONNECT_DELAY_MS = 5_000L
private const val DOCK_JOIN_ATTEMPTS = 3
private const val DOCK_JOIN_DELAY_MS = 1_000L
private const val FIRST_RECOVERY_DELAY_MS = 1_000L
private const val SECOND_RECOVERY_DELAY_MS = 2_000L
private const val THIRD_RECOVERY_DELAY_MS = 5_000L
private const val MAX_RECOVERY_DELAY_MS = 10_000L
private val recoveryDelaysMs =
    listOf(
        FIRST_RECOVERY_DELAY_MS,
        SECOND_RECOVERY_DELAY_MS,
        THIRD_RECOVERY_DELAY_MS,
        MAX_RECOVERY_DELAY_MS,
        MAX_RECOVERY_DELAY_MS,
    )
private val nonRecoverableErrors =
    setOf(
        TvErrorCode.IdentityChanged,
        TvErrorCode.PairingRequired,
        TvErrorCode.StorageUnavailable,
        TvErrorCode.PermissionRequired,
    )

internal class RepeatCommands(
    private val scope: CoroutineScope,
    private val send: suspend (TvCommand) -> Unit,
) {
    private var repeating: Job? = null
    private val initial = mutableSetOf<Job>()

    fun press(command: TvCommand) {
        release()
        val first =
            scope.launch {
                send(command)
            }
        initial += first
        first.invokeOnCompletion {
            initial.remove(first)
        }
        if (command.repeatable) {
            repeating =
                scope.launch {
                    delay(REPEAT_START_DELAY_MS)
                    while (isActive) {
                        send(command)
                        delay(REPEAT_INTERVAL_MS)
                    }
                }
        }
    }

    fun release() {
        repeating?.cancel()
        repeating = null
    }

    fun cancel() {
        release()
        initial.toList().forEach {
            it.cancel()
        }
        initial.clear()
    }
}

// Each public method represents an existing screen or lifecycle action.
@Suppress("TooManyFunctions")
class RemoteViewModel internal constructor(
    application: Application,
    val client: LgTvClient,
    private val iconCache: AppIconCache,
) : AndroidViewModel(application) {
    constructor(application: Application) : this(application, (application as ColorDockApplication).container.tv)
    private constructor(
        application: Application,
        client: LgTvClient,
    ) : this(application, client, AppIconCache(application, fetch = client::appIcon))

    val state = client.state
    private val credentials = TvCredentialStore(application)
    private val mutableAddress = MutableStateFlow<String?>(null)
    val savedAddress = mutableAddress.asStateFlow()
    private val mutableChannels = MutableStateFlow(emptyList<TvChannel>())
    val channels = mutableChannels.asStateFlow()
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()
    private var connection: Job? = null
    private var backgroundClose: Job? = null
    private var recovery: Job? = null
    private var foregroundActive = false
    private var automaticAllowed = true
    private var intent = 0L
    private var recoveryAttempts = 0
    private val discovery = TvDiscovery(application)
    private val mutableDiscovered = MutableStateFlow<List<DiscoveredTv>>(emptyList())
    val discovered = mutableDiscovered.asStateFlow()
    private val mutableSearching = MutableStateFlow(false)
    val searching = mutableSearching.asStateFlow()
    private var searchJob: Job? = null
    private val repeats =
        RepeatCommands(viewModelScope) { command ->
            report(client.send(command))
        }
    private val tvApps = TvAppsController(viewModelScope, client::apps, client::launchApp)
    val apps = tvApps.state
    private val mutableAppIcons = MutableStateFlow<Map<String, File>>(emptyMap())
    val appIcons = mutableAppIcons.asStateFlow()
    private val iconJobs = mutableMapOf<String, Job>()
    private val requestedIcons = mutableSetOf<String>()
    private var iconToken: Any? = null
    private var iconRevision = 0L
    private var iconCatalog = emptyList<TvApp>()

    init {

        viewModelScope.launch {

            val origin = intent
            try {

                val saved = credentials.savedAddress()
                if (origin != intent) return@launch
                mutableAddress.value = saved
                if (foregroundActive && automaticAllowed) reconnectSaved()
            } catch (_: IOException) {

                if (origin != intent) return@launch
                automaticAllowed = false
                mutableError.value = "Could not load your saved TV. Please try connecting again."
            }
        }
        viewModelScope.launch {

            state.collect {

                val token =
                    client.connectionToken.takeIf { _ ->
                        it.connection == TvConnectionState.Ready
                    }
                tvApps.connectionChanged(token)
                if (token !== iconToken) {

                    iconJobs.values.forEach { job ->
                        job.cancel()
                    }
                    iconJobs.clear()
                    requestedIcons.clear()
                    mutableAppIcons.value = emptyMap()
                    iconToken = token
                    iconRevision = 0
                    iconCatalog = emptyList()
                }
                if (it.connection != TvConnectionState.Ready) {

                    repeats.cancel()
                    mutableChannels.value = emptyList()
                }
                if (it.connection == TvConnectionState.PermissionRequired || it.error in nonRecoverableErrors) {
                    automaticAllowed = false
                }
                if (it.connection == TvConnectionState.Failed && it.error !in nonRecoverableErrors) {
                    recover()
                }
            }
        }
        viewModelScope.launch {
            apps.collect(::updateIconCatalog)
        }
    }

    fun connect(address: String) {
        val target = TvTarget.parse(address) ?: return
        intent++
        automaticAllowed = true
        recoveryAttempts = 0
        recovery?.cancel()
        recovery = null
        searchJob?.cancel()
        connection?.cancel()
        connection =
            viewModelScope.launch {
                mutableError.value = null
                connectTarget(target, true)
            }
    }

    private suspend fun connectTarget(
        target: TvTarget,
        showError: Boolean,
    ) {
        val origin = intent
        val connected = client.connect(target)
        if (origin != intent) return
        if (showError) report(connected)
        if (state.value.connection == TvConnectionState.Ready) {
            recoveryAttempts = 0
            mutableAddress.value = credentials.savedAddress()
            when (val result = client.channels()) {
                is TvResult.Success -> mutableChannels.value = result.value
                is TvResult.Failure -> mutableChannels.value = emptyList()
            }
            client.refreshStatus()
        }
    }

    fun find() {
        searchJob?.cancel()
        searchJob =
            viewModelScope.launch {
                mutableSearching.value = true
                mutableDiscovered.value = emptyList()
                mutableError.value = null
                try {
                    mutableDiscovered.value =
                        discovery.find()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    mutableError.value =
                        "No TVs found. Check that your phone and TV are on the same network."
                } finally {
                    mutableSearching.value = false
                }
            }
    }

    private fun reconnectSaved() {
        val target =
            mutableAddress.value?.let(TvTarget::parse) ?: return
        if (connection?.isActive == true ||
            state.value.connection == TvConnectionState.Ready
        ) {
            return
        }
        connection =
            viewModelScope.launch {
                connectTarget(target, false)
            }
    }

    private fun recover() {
        val recoveryUnavailable = recovery?.isActive == true || mutableAddress.value == null
        if (!canRecover || recoveryUnavailable || recoveryAttempts >= recoveryDelaysMs.size) {
            return
        }
        recovery =
            viewModelScope.launch {
                while (recoveryAttempts < recoveryDelaysMs.size) {
                    val wait = recoveryDelaysMs[recoveryAttempts++]
                    if (!canRecover) return@launch
                    client.reconnecting()
                    delay(wait)
                    if (!canRecover) return@launch
                    val target = mutableAddress.value?.let(TvTarget::parse) ?: return@launch
                    connectTarget(target, false)
                    if (recoveryFinished) {
                        return@launch
                    }
                }
            }
    }

    private val canRecover get() = foregroundActive && automaticAllowed

    private val recoveryFinished get() =
        state.value.connection in setOf(TvConnectionState.Ready, TvConnectionState.PermissionRequired) ||
            state.value.error in nonRecoverableErrors

    fun send(command: TvCommand) {
        viewModelScope.launch {
            val origin = intent
            if (command == TvCommand.PowerOff) {
                automaticAllowed = false
                recovery?.cancel()
            }
            val result = client.send(command)
            if (origin != intent) return@launch
            report(result)
            if (command in
                setOf(TvCommand.Mute, TvCommand.Unmute, TvCommand.ChannelUp, TvCommand.ChannelDown, TvCommand.LiveTv)
            ) {
                client.refreshStatus()
            }
        }
    }

    fun press(command: TvCommand) {
        if (state.value.connection == TvConnectionState.Ready) repeats.press(command)
    }

    fun release() {
        repeats.release()
    }

    fun refreshApps() {
        repeats.release()
        tvApps.refresh()
    }

    fun openApp(appId: String) {
        repeats.release()
        tvApps.open(appId)
    }

    fun acknowledgeAppLaunch() {
        tvApps.acknowledgeLaunchCompletion()
    }

    fun clearAppLaunchError() {
        tvApps.clearLaunchError()
    }

    private fun updateIconCatalog(catalog: TvAppsUiState) {
        if (catalog.catalogRevision == iconRevision) return
        iconJobs.values.forEach {
            it.cancel()
        }
        iconJobs.clear()
        requestedIcons.clear()
        iconRevision = catalog.catalogRevision
        mutableAppIcons.value =
            mutableAppIcons.value.filterKeys { id ->
                val previous =
                    iconCatalog.find {
                        it.id == id
                    }
                previous != null &&
                    catalog.apps.any {
                        it == previous
                    }
            }
        iconCatalog = catalog.apps
    }

    fun loadAppIcon(appId: String) {
        val token = client.connectionToken
        val identity = client.identity
        if (token != null && identity != null) {
            val catalog = apps.value
            val app = catalog.apps.find { it.id == appId } ?: return
            loadAppIcon(app, catalog, token, identity)
        }
    }

    private fun loadAppIcon(
        app: TvApp,
        catalog: TvAppsUiState,
        token: Any,
        identity: String,
    ) {
        val appId = app.id
        updateIconCatalog(catalog)
        if (state.value.connection != TvConnectionState.Ready || !requestedIcons.add(appId)) return
        val revision = catalog.catalogRevision

        iconJobs[appId] =
            viewModelScope.launch {
                iconCache.cached(identity, app)?.let {
                    if (currentIconRequest(token, identity, revision, app)) {
                        mutableAppIcons.value = mutableAppIcons.value + (appId to it)
                    }
                }
                if (!currentIconRequest(token, identity, revision, app)) return@launch
                iconCache
                    .get(
                        identity,
                        app,
                        refresh = true,
                    )?.let {
                        if (currentIconRequest(token, identity, revision, app)) {
                            mutableAppIcons.value = mutableAppIcons.value + (appId to it)
                        }
                    }
            }
    }

    private fun currentIconRequest(
        token: Any,
        identity: String,
        revision: Long,
        app: TvApp,
    ): Boolean {
        val sameConnection =
            client.connectionToken === token && client.identity == identity &&
                state.value.connection == TvConnectionState.Ready
        return sameConnection && apps.value.catalogRevision == revision && app in apps.value.apps
    }

    fun launchDock() {
        viewModelScope.launch {
            val origin = intent
            val result = client.launchColorDock()
            if (origin != intent) return@launch
            report(result)
            if (result is TvResult.Success) {
                val dock = (getApplication<Application>() as ColorDockApplication).container.dock
                repeat(DOCK_JOIN_ATTEMPTS) {
                    if (dock.join() is com.emmah.colordock.remote.companion.DockResult.Success) return@launch
                    delay(DOCK_JOIN_DELAY_MS)
                }
            }
        }
    }

    fun tune(id: String) {
        viewModelScope.launch {
            val origin = intent
            val result = client.tune(id)
            if (origin !=
                intent
            ) {
                return@launch
            }
            report(result)
            client.refreshStatus()
        }
    }

    fun clearError() {
        mutableError.value = null
    }

    fun disconnect() {
        intent++
        automaticAllowed = false
        recovery?.cancel()
        recovery = null
        searchJob?.cancel()
        repeats.cancel()
        connection?.cancel()
        client.disconnect()
    }

    fun forget() {
        disconnect()
        viewModelScope.launch {
            try {
                credentials.forget()
                mutableAddress.value = null
                mutableChannels.value = emptyList()
            } catch (
                cancelled: CancellationException,
            ) {
                throw cancelled
            } catch (
                _: Exception,
            ) {
                mutableError.value = "The saved TV could not be removed. Check storage and try again."
            }
        }
    }

    fun foreground() {
        foregroundActive = true
        backgroundClose?.cancel()
        if (automaticAllowed) reconnectSaved()
    }

    fun background() {
        foregroundActive = false
        recovery?.cancel()
        recovery = null
        searchJob?.cancel()
        repeats.cancel()
        backgroundClose?.cancel()
        backgroundClose =
            viewModelScope.launch {
                delay(BACKGROUND_DISCONNECT_DELAY_MS)
                repeats.cancel()
                connection?.cancel()
                client.disconnect()
            }
    }

    private fun report(result: TvResult<*>) {
        if (result is TvResult.Failure) mutableError.value = result.message
    }

    override fun onCleared() {
        repeats.cancel()
        client.disconnect()
    }
}
