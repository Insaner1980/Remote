package com.emmah.colordock.remote.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.emmah.colordock.remote.ColorDockApplication
import com.emmah.colordock.remote.companion.*
import com.emmah.colordock.remote.images.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File

data class WallpaperUiState(val source:String="personal",val items:List<WallpaperMetadata> = emptyList(),val cursor:String?=null,val loading:Boolean=false,val saving:Boolean=false,val error:String?=null,val detail:WallpaperMetadata?=null,val prepared:List<PreparedImage> = emptyList(),val preparing:Boolean=false,val upload:UploadProgress?=null,val outcome:String?=null)
class WallpaperViewModel(application:Application):AndroidViewModel(application) {
    private val container=(application as ColorDockApplication).container
    val dock=container.dock
    private val mutableUi=MutableStateFlow(WallpaperUiState())
    val ui=mutableUi.asStateFlow()
    private val mutableThumbnails=MutableStateFlow<Map<String,File>>(emptyMap())
    val thumbnails=mutableThumbnails.asStateFlow()
    private val cache=ThumbnailCache(application,dock)
    private val preparing=ImagePreparer(application)
    private val pending=PendingUploadStore(application)
    private val uploading=UploadController(dock,pending)
    private val requested=mutableSetOf<String>()
    private val selectedUris=mutableListOf<Uri>()
    private var listing:Job?=null
    private var transfer:Job?=null
    private var galleryRevision:Long?=null
    private var batchToken=0
    init {
        viewModelScope.launch {container.tv.state.collect {if(it.connection==com.emmah.colordock.remote.tv.TvConnectionState.Disconnected){requested.clear();mutableThumbnails.value=emptyMap()}}}
        viewModelScope.launch {dock.state.collect {state->
            val snapshot=state.snapshot
            if(state.connection==DockConnectionState.Ready&&snapshot!=null&&snapshot.revision!=galleryRevision){galleryRevision=snapshot.revision;load(false)}
        }}
    }
    fun join(){viewModelScope.launch {
        if(dock.join() is DockResult.Success&&ui.value.upload==null)pending.read()?.let {(id,name)->
            when(val status=dock.call(DockMethod.UploadStatus,JSONObject().put("uploadId",id))){
                is DockResult.Success->{mutableUi.value=ui.value.copy(outcome=if(status.value.optString("status")=="committed")"$name was saved on the TV." else "The previous upload did not finish. Select the picture again.");pending.clear()}
                is DockResult.Failure->Unit
            }
        }
    }}
    fun source(value:String){if(value!=ui.value.source){mutableUi.value=ui.value.copy(source=value,items=emptyList(),cursor=null);load(false)}}
    fun load(more:Boolean){
        listing?.cancel();val source=ui.value.source;val cursor=if(more)ui.value.cursor else null
        listing=viewModelScope.launch {
            mutableUi.value=ui.value.copy(loading=true,error=null)
            var pageCursor=cursor
            val collected=mutableListOf<WallpaperMetadata>()
            do {
            when(val reply=dock.call(DockMethod.Gallery,JSONObject().put("source",source).put("cursor",pageCursor?:JSONObject.NULL).put("limit",24))) {
                is DockResult.Success->try {
                    val json=reply.value;val revision=json.getLong("galleryRevision")
                    if((more||collected.isNotEmpty())&&revision!=galleryRevision){load(false);return@launch}
                    galleryRevision=revision
                    val array=json.getJSONArray("items");val items=(0 until array.length()).map {DockCodec.image(array.getJSONObject(it))}
                    collected.addAll(items)
                    pageCursor=json.optString("nextCursor").takeUnless {it.isEmpty()||it=="null"}
                    if(source!="builtin"||pageCursor==null)mutableUi.value=ui.value.copy(items=if(more)ui.value.items+collected else collected.toList(),cursor=pageCursor,loading=false)
                }catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){mutableUi.value=ui.value.copy(loading=false,error="The picture list could not be read.");return@launch}
                is DockResult.Failure->{mutableUi.value=ui.value.copy(loading=false,error=reply.message);return@launch}
            }
            // Read the small built-in metadata catalog for collection filters; thumbnails stay lazy.
            }while(source=="builtin"&&pageCursor!=null)
        }
    }
    fun thumbnail(item:WallpaperMetadata){
        val identity=container.tv.identity?:return
        if(item.id in requested||item.id in thumbnails.value)return;requested.add(item.id)
        viewModelScope.launch {try {val file=cache.get(identity,item);mutableThumbnails.value=thumbnails.value+(item.id to file)}catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){requested.remove(item.id)}}
    }
    fun detail(item:WallpaperMetadata?){mutableUi.value=ui.value.copy(detail=item,error=null)}
    fun clearError(){mutableUi.value=ui.value.copy(error=null,outcome=null)}
    fun patch(patch:DockPatch){val revision=dock.state.value.snapshot?.revision?:return;mutate {dock.patch(revision,patch)}}
    fun imageAction(method:DockMethod,item:WallpaperMetadata,fit:String?=null){
        val revision=dock.state.value.snapshot?.revision?:return
        mutate(action={val params=JSONObject().put("expectedRevision",revision).put("wallpaperId",item.id);fit?.let {params.put("fit",it)};dock.call(method,params)},onSuccess={
            if(method==DockMethod.Delete)detail(null)
            if(method==DockMethod.Fit&&fit!=null)mutableUi.value=ui.value.copy(detail=ui.value.detail?.copy(fit=fit))
        })
    }
    fun next(){val revision=dock.state.value.snapshot?.revision?:return;mutate {dock.call(DockMethod.Next,JSONObject().put("expectedRevision",revision))}}
    private fun mutate(onSuccess:()->Unit={},action:suspend()->DockResult<*>) {
        if(ui.value.saving)return
        viewModelScope.launch {mutableUi.value=ui.value.copy(saving=true,error=null);try {
            when(val result=action()){is DockResult.Failure->mutableUi.value=ui.value.copy(error=result.message);is DockResult.Success->{onSuccess();dock.snapshot()}}
        }finally{mutableUi.value=ui.value.copy(saving=false)}}
    }
    fun select(uris:List<Uri>){
        if(uris.isEmpty())return;abandon();val token=++batchToken;selectedUris.addAll(uris.take(10));mutableUi.value=ui.value.copy(preparing=true,error=null,outcome=null)
        transfer=viewModelScope.launch {
            val ready=mutableListOf<PreparedImage>();val errors=mutableListOf<String>()
            try {
                for((index,uri)in selectedUris.toList().withIndex()) {
                    try {getApplication<Application>().contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)}catch(_:SecurityException){/* The current picker grant remains usable. */}
                    try {ready.add(preparing.prepare(uri,"Picture ${index+1}"))}catch(cancelled:CancellationException){throw cancelled}catch(error:Exception){errors.add("Picture ${index+1}: ${error.message}")}
                    if(token==batchToken)mutableUi.value=ui.value.copy(prepared=ready.toList())
                }
                if(token==batchToken)mutableUi.value=ui.value.copy(preparing=false,error=errors.takeIf {it.isNotEmpty()}?.joinToString("\n"))
            }catch(cancelled:CancellationException){ready.forEach {it.delete()};throw cancelled}
            finally{if(token==batchToken)mutableUi.value=ui.value.copy(preparing=false)}
        }
    }
    fun preparedFit(id:String,fit:String){mutableUi.value=ui.value.copy(prepared=ui.value.prepared.map {if(it.uploadId==id)it.copy(fit=fit)else it})}
    fun send(){
        if(ui.value.prepared.isEmpty()||ui.value.upload?.stage in listOf("Preparing","Sending","Committing"))return
        transfer=viewModelScope.launch {
            val failed=mutableListOf<PreparedImage>();val complete=mutableListOf<String>()
            try {
                for(image in ui.value.prepared.toList()) {
                    uploading.upload(image).collect {progress->
                        mutableUi.value=ui.value.copy(upload=progress,outcome="${complete.size} sent: ${image.name}")
                        if(progress.stage=="Complete"){complete.add(image.name);image.delete()}
                        if(progress.stage=="Failed")failed.add(image)
                    }
                }
                mutableUi.value=ui.value.copy(prepared=failed,upload=null,outcome=buildString {append("Sent: ");append(complete.ifEmpty {listOf("None")}.joinToString());if(failed.isNotEmpty()){append("\nFailed: ");append(failed.joinToString {it.name})}})
                if(failed.isEmpty())releaseGrants()
            }catch(cancelled:CancellationException){mutableUi.value=ui.value.copy(upload=null,outcome="Upload cancelled. Already saved pictures remain on the TV.");throw cancelled}
        }
    }
    fun cancelUpload(){transfer?.cancel()}
    fun background(){if(ui.value.upload!=null)cancelUpload()}
    fun abandon(){batchToken++;transfer?.cancel();ui.value.prepared.forEach {it.delete()};releaseGrants();mutableUi.value=ui.value.copy(prepared=emptyList(),upload=null,preparing=false)}
    private fun releaseGrants(){for(uri in selectedUris)try{getApplication<Application>().contentResolver.releasePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)}catch(_:SecurityException){/* Only grants owned by this batch are released. */};selectedUris.clear()}
    override fun onCleared(){abandon()}
}
