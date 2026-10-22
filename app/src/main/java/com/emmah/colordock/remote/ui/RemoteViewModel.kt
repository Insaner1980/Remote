package com.emmah.colordock.remote.ui

import android.app.Application
import com.emmah.colordock.remote.ColorDockApplication
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.emmah.colordock.remote.tv.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class RepeatCommands(private val scope: CoroutineScope, private val send: suspend (TvCommand) -> Unit) {
    private var repeating: Job? = null
    private var initial: Job? = null

    fun press(command: TvCommand) {
        release()
        initial = scope.launch { send(command) }
        if (command.repeatable) repeating = scope.launch {
            delay(450)
            while (isActive) { send(command); delay(180) }
        }
    }

    fun release() { repeating?.cancel(); repeating = null }
    fun cancel() { release(); initial?.cancel(); initial = null }
}

class RemoteViewModel(application: Application) : AndroidViewModel(application) {
    val client = (application as ColorDockApplication).container.tv
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
    private var recovery:Job?=null
    private var foregroundActive=false
    private var automaticAllowed=true
    private var recoveryAttempts=0
    private val discovery=TvDiscovery(application)
    private val mutableDiscovered=MutableStateFlow<List<DiscoveredTv>>(emptyList())
    val discovered=mutableDiscovered.asStateFlow()
    private val mutableSearching=MutableStateFlow(false)
    val searching=mutableSearching.asStateFlow()
    private var searchJob:Job?=null
    private val repeats = RepeatCommands(viewModelScope) { command -> report(client.send(command)) }

    init {
        viewModelScope.launch { mutableAddress.value = credentials.savedAddress();if(foregroundActive&&automaticAllowed)reconnectSaved() }
        viewModelScope.launch { state.collect {
            if (it.connection != TvConnectionState.Ready) repeats.cancel()
            if(it.error in setOf(TvErrorCode.IdentityChanged,TvErrorCode.PairingRequired,TvErrorCode.StorageUnavailable))automaticAllowed=false
            if(it.connection==TvConnectionState.Failed&&it.error !in setOf(TvErrorCode.IdentityChanged,TvErrorCode.PairingRequired,TvErrorCode.StorageUnavailable))recover()
        } }
    }

    fun connect(address: String) {
        val target = TvTarget.parse(address) ?: return
        automaticAllowed=true
        recoveryAttempts=0
        recovery?.cancel();recovery=null
        searchJob?.cancel()
        connection?.cancel()
        connection = viewModelScope.launch {
            mutableError.value = null
            connectTarget(target,true)
        }
    }

    private suspend fun connectTarget(target:TvTarget,showError:Boolean) {
            val connected=client.connect(target)
            if(showError)report(connected)
            if (state.value.connection == TvConnectionState.Ready) {
                recoveryAttempts=0
                mutableAddress.value = credentials.savedAddress()
                when (val result = client.channels()) {
                    is TvResult.Success -> mutableChannels.value = result.value
                    is TvResult.Failure -> mutableChannels.value = emptyList()
                }
                client.refreshStatus()
            }
    }

    fun find(){searchJob?.cancel();searchJob=viewModelScope.launch {mutableSearching.value=true;mutableDiscovered.value=emptyList();mutableError.value=null;try{mutableDiscovered.value=discovery.find()}catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){mutableError.value="No TVs found. Check that your phone and TV are on the same network."}finally{mutableSearching.value=false}}}
    private fun reconnectSaved(){val target=mutableAddress.value?.let(TvTarget::parse)?:return;if(connection?.isActive==true||state.value.connection==TvConnectionState.Ready)return;connection=viewModelScope.launch {connectTarget(target,false)}}
    private fun recover(){
        if(!foregroundActive||!automaticAllowed||recovery?.isActive==true||mutableAddress.value==null||recoveryAttempts>=5)return
        recovery=viewModelScope.launch {
            while(recoveryAttempts<5) {
                val wait=listOf(1000L,2000L,5000L,10000L,10000L)[recoveryAttempts++]
                if(!foregroundActive||!automaticAllowed)return@launch
                client.reconnecting();delay(wait)
                val target=mutableAddress.value?.let(TvTarget::parse)?:return@launch
                connectTarget(target,false)
                if(state.value.connection==TvConnectionState.Ready||state.value.error in setOf(TvErrorCode.IdentityChanged,TvErrorCode.PairingRequired,TvErrorCode.StorageUnavailable))return@launch
            }
        }
    }

    fun send(command: TvCommand) {
        viewModelScope.launch {
            if(command==TvCommand.PowerOff){automaticAllowed=false;recovery?.cancel()}
            report(client.send(command))
            if (command in setOf(TvCommand.Mute, TvCommand.Unmute, TvCommand.ChannelUp, TvCommand.ChannelDown, TvCommand.LiveTv)) client.refreshStatus()
        }
    }

    fun press(command: TvCommand) { if (state.value.connection == TvConnectionState.Ready) repeats.press(command) }
    fun release() { repeats.release() }
    fun launchDock() { viewModelScope.launch {
        val result=client.launchColorDock();report(result)
        if(result is TvResult.Success) {
            val dock=(getApplication<Application>() as ColorDockApplication).container.dock
            repeat(3){if(dock.join() is com.emmah.colordock.remote.companion.DockResult.Success)return@launch;delay(1000)}
        }
    } }
    fun tune(id: String) { viewModelScope.launch { report(client.tune(id)); client.refreshStatus() } }
    fun clearError() { mutableError.value = null }

    fun disconnect() {
        automaticAllowed=false
        recovery?.cancel();recovery=null
        searchJob?.cancel()
        repeats.cancel()
        connection?.cancel()
        client.disconnect()
    }

    fun forget() {
        disconnect()
        viewModelScope.launch { credentials.forget(); mutableAddress.value = null; mutableChannels.value = emptyList() }
    }

    fun foreground() { foregroundActive=true;backgroundClose?.cancel();if(automaticAllowed)reconnectSaved() }
    fun background() {
        foregroundActive=false
        recovery?.cancel();recovery=null
        searchJob?.cancel()
        repeats.cancel()
        backgroundClose?.cancel()
        backgroundClose = viewModelScope.launch { delay(5_000);repeats.cancel();connection?.cancel();client.disconnect() }
    }

    private fun report(result: TvResult<*>) {
        if (result is TvResult.Failure) mutableError.value = result.message
    }

    override fun onCleared() { repeats.cancel(); client.disconnect() }
}
