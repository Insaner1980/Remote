package com.emmah.colordock.remote.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.emmah.colordock.remote.ColorDockApplication
import com.emmah.colordock.remote.companion.*
import com.emmah.colordock.remote.weather.LocationSearch
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

data class DockUiState(val saving:Boolean=false,val error:String?=null,val query:String="",val searching:Boolean=false,val results:List<WeatherLocation> = emptyList(),val searched:Boolean=false)
class DockViewModel(application:Application):AndroidViewModel(application) {
    private val dock=(application as ColorDockApplication).container.dock
    val state=dock.state
    private val mutableUi=MutableStateFlow(DockUiState())
    val ui=mutableUi.asStateFlow()
    private val search=LocationSearch()
    private var searching:Job?=null
    fun join(){viewModelScope.launch {dock.join()}}
    fun patch(patch:DockPatch){val snapshot=state.value.snapshot?:return;perform {dock.patch(snapshot.revision,patch)}}
    fun clock(){val snapshot=state.value.snapshot?:return;perform {dock.call(if(snapshot.clockActive)DockMethod.ClockStop else DockMethod.ClockStart,JSONObject())}}
    fun shelf(order:List<String>,hidden:List<String>,expectedRevision:Long){perform {dock.patch(expectedRevision,DockPatch.Shelf(order,hidden))}}
    fun location(location:WeatherLocation){patch(DockPatch.Weather(true,location))}
    private fun perform(action:suspend()->DockResult<*>){
        if(ui.value.saving)return
        viewModelScope.launch {mutableUi.value=ui.value.copy(saving=true,error=null);try{when(val reply=action()){is DockResult.Failure->mutableUi.value=ui.value.copy(error=reply.message);is DockResult.Success->dock.snapshot()}}finally{mutableUi.value=ui.value.copy(saving=false)}}
    }
    fun query(value:String){searching?.cancel();mutableUi.value=ui.value.copy(query=value,results=emptyList(),searched=false,searching=false,error=null)}
    fun search(){val query=ui.value.query;if(query.trim().length<2)return;searching?.cancel();searching=viewModelScope.launch {
        mutableUi.value=ui.value.copy(searching=true,error=null)
        try {val results=search.search(query);mutableUi.value=ui.value.copy(results=results,searched=true)}catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){mutableUi.value=ui.value.copy(error="Location search is unavailable. Check your internet connection and try again.")}
        finally{mutableUi.value=ui.value.copy(searching=false)}
    }}
    override fun onCleared(){searching?.cancel();search.close()}
}
