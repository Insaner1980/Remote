package com.emmah.colordock.remote.companion

import com.emmah.colordock.remote.tv.TvConnectionState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class CompanionClient(private val tv: DockTransport, private val scope: CoroutineScope) : DockControl {
    private val mutableState=MutableStateFlow(DockState())
    override val state=mutableState.asStateFlow()
    private val pending=ConcurrentHashMap<String,CompletableDeferred<JSONObject>>()
    private val joining=Mutex()
    private var sessionId: String?=null
    var limits=DockLimits();private set
    init {
        scope.launch(start=CoroutineStart.UNDISPATCHED) { tv.dockMessages.collect { message ->
            if(message.optString("protocol")!="color-dock" || message.optInt("version")!=1 || message.toString().toByteArray().size>32768)return@collect
            if(message.has("event")) {
                if(sessionId!=null) when(message.optString("event")) {
                    "state.changed" -> scope.launch { snapshot() }
                    "clock.changed" -> mutableState.value=state.value.copy(snapshot=state.value.snapshot?.copy(clockActive=message.optBoolean("active")))
                }
            } else pending.remove(message.optString("id"))?.complete(message)
        } }
        scope.launch {tv.state.collect {if(it.connection!=TvConnectionState.Ready)disconnect()} }
        scope.launch {tv.dockAvailable.collect {if(!it)disconnect()} }
    }
    override suspend fun join(): DockResult<DockSnapshot> = joining.withLock {
        if(tv.state.value.connection!=TvConnectionState.Ready)return@withLock unavailable()
        if(sessionId!=null)return@withLock snapshot()
        mutableState.value=state.value.copy(connection=DockConnectionState.Connecting,message=null)
        try {
            tv.joinDockChannel()
            val hello=exchange("hello",JSONObject().put("clientName","Remote").put("supportedVersion",1),null)
            if(hello.getInt("protocolVersion")!=1)throw DockException("UNSUPPORTED_VERSION")
            sessionId=hello.getString("sessionId").also {UUID.fromString(it)}
            val offered=hello.getJSONObject("limits")
            limits=DockLimits(offered.getInt("chunkBytes").also {require(it in 1024..12288)},minOf(offered.getInt("imageBytes"),4194304),minOf(offered.getInt("thumbnailBytes"),131072))
            snapshot()
        }catch(cancelled:CancellationException){if(cancelled is TimeoutCancellationException){disconnect();unavailable()}else{disconnect();throw cancelled}}
        catch(error:Exception){disconnect();val code=(error as? DockException)?.code;mutableState.value=DockState(if(code=="UNSUPPORTED_VERSION")DockConnectionState.Incompatible else DockConnectionState.Unavailable,message=message(code));unavailable()}
    }
    override suspend fun snapshot(): DockResult<DockSnapshot> = when(val result=call(DockMethod.State,JSONObject())) {
        is DockResult.Success -> try {val snapshot=DockCodec.snapshot(result.value);mutableState.value=DockState(DockConnectionState.Ready,snapshot);DockResult.Success(snapshot)}catch(_:Exception){DockResult.Failure("INVALID_REQUEST","Color Dock returned an unreadable response.")}
        is DockResult.Failure -> result
    }
    override suspend fun patch(expectedRevision:Long,patch:DockPatch):DockResult<DockSnapshot> = when(val result=call(DockMethod.Patch,JSONObject().put("expectedRevision",expectedRevision).put("patch",patch.json()))) {
        is DockResult.Success -> try {val value=DockCodec.snapshot(result.value);mutableState.value=DockState(DockConnectionState.Ready,value);DockResult.Success(value)}catch(_:Exception){DockResult.Failure("INVALID_REQUEST","Color Dock returned an unreadable response.")}
        is DockResult.Failure -> result
    }
    override suspend fun call(method:DockMethod,params:JSONObject):DockResult<JSONObject> { return try {
        val session=sessionId?:return unavailable()
        DockResult.Success(exchange(method.wire,params,session))
    }catch(timeout:TimeoutCancellationException){DockResult.Failure("TIMEOUT","Color Dock did not reply. Reconnect and check the saved state.")}
    catch(cancelled:CancellationException){throw cancelled}
    catch(error:Exception){
        val code=(error as? DockException)?.code?:"NOT_READY"
        if(code=="REVISION_CONFLICT")scope.launch {snapshot()}
        if(code=="SESSION_EXPIRED"||code=="NOT_READY")disconnect()
        DockResult.Failure(code,message(code))
    } }
    private suspend fun exchange(method:String,params:JSONObject,session:String?):JSONObject {
        val id=UUID.randomUUID().toString();val reply=CompletableDeferred<JSONObject>();pending[id]=reply
        try {
            val request=JSONObject().put("protocol","color-dock").put("version",1).put("id",id).put("method",method).put("params",params)
            session?.let {request.put("sessionId",it)}
            require(request.toString().toByteArray().size<=32768)
            tv.sendDock(request)
            return DockCodec.reply(withTimeout(5000){reply.await()},id)
        }finally{pending.remove(id);reply.cancel()}
    }
    override fun disconnect(){sessionId=null;pending.values.forEach {it.completeExceptionally(DockException("NOT_READY"))};pending.clear();mutableState.value=DockState()}
    private fun unavailable()=DockResult.Failure("NOT_READY","Open Color Dock to continue.")
    private fun message(code:String?)=when(code){
        "REVISION_CONFLICT"->"Settings changed on the TV. Review your change and try again."
        "EMPTY_SELECTION"->"Keep at least one picture selected."
        "STORAGE_FULL"->"The TV has no space for this picture. Delete a personal picture and try again."
        "STORAGE_UNAVAILABLE"->"TV storage is unavailable. Your saved pictures and choices have been kept."
        "UPLOAD_BUSY"->"Another picture transfer is still active. Wait a moment and try again."
        "UPLOAD_TOO_LARGE"->"This picture exceeds the TV image limits. Choose another picture."
        "BUILTIN_READ_ONLY"->"Built-in pictures cannot be deleted or edited."
        "NOT_FOUND"->"This picture is no longer on the TV. Refresh the gallery and try again."
        "INVALID_REQUEST"->"This change could not be applied. Review it and try again."
        "SESSION_EXPIRED"->"The Color Dock connection expired. Reconnect and try again."
        "UNSUPPORTED_VERSION"->"Update Color Dock on your TV to continue."
        "HASH_MISMATCH","OUT_OF_ORDER"->"The picture transfer was interrupted. Please try again."
        "INVALID_IMAGE"->"This picture could not be decoded."
        else->"Open Color Dock to continue."
    }
}
