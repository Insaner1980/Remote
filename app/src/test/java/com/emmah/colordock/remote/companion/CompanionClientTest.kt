package com.emmah.colordock.remote.companion

import com.emmah.colordock.remote.tv.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CompanionClientTest {
    private class Transport:DockTransport {
        override val state=MutableStateFlow(TvState(TvConnectionState.Ready))
        override val dockMessages=MutableSharedFlow<JSONObject>(extraBufferCapacity=20)
        override val dockAvailable=MutableStateFlow(true)
        val sent=mutableListOf<JSONObject>();var joins=0;var joinFails=false
        override suspend fun joinDockChannel(){joins++;if(joinFails)throw java.io.IOException("Not running")}
        override fun sendDock(payload:JSONObject){sent.add(payload)}
        suspend fun reply(request:JSONObject,result:JSONObject){dockMessages.emit(JSONObject().put("protocol","color-dock").put("version",1).put("id",request.getString("id")).put("ok",true).put("result",result))}
    }
    private fun snapshot()=JSONObject("""{"revision":0,"settings":{"wallpaper":{"current":"a","pool":["a"],"mode":"fixed","trigger":"open"},"clock":{"idleMinutes":0},"weather":{"enabled":false,"location":null},"shelf":{"order":["one"],"hidden":[]}},"currentWallpaper":{"id":"a","title":"First","fit":"cover"},"galleryCounts":{"personal":0,"builtin":139},"clockActive":false}""")
    private suspend fun TestScope.join(client:CompanionClient,tv:Transport){
        val joined=async {client.join()};runCurrent()
        tv.reply(tv.sent.last(),JSONObject("""{"protocolVersion":1,"sessionId":"00000000-0000-4000-8000-000000000099","limits":{"chunkBytes":12288,"imageBytes":4194304,"thumbnailBytes":131072}}"""));runCurrent()
        tv.reply(tv.sent.last(),snapshot());runCurrent();assertTrue(joined.await() is DockResult.Success)
    }
    @Test fun colorDockUnavailable_doesNotDisconnectTvControl()=runTest {
        val tv=Transport().apply {joinFails=true};val client=CompanionClient(tv,backgroundScope)
        assertTrue(client.join() is DockResult.Failure);assertEquals(TvConnectionState.Ready,tv.state.value.connection);assertEquals(DockConnectionState.Unavailable,client.state.value.connection)
    }
    @Test fun responsesAreCorrelatedAndCompanionDepartureFailsPendingWithoutClosingTv()=runTest {
        val tv=Transport();val client=CompanionClient(tv,backgroundScope);join(client,tv)
        val first=async {client.call(DockMethod.ClockStart,JSONObject())};val second=async {client.call(DockMethod.ClockStop,JSONObject())};runCurrent()
        tv.reply(tv.sent.last(),JSONObject().put("active",false));runCurrent();assertFalse(first.isCompleted);assertTrue(second.await() is DockResult.Success)
        tv.dockAvailable.value=false;runCurrent();assertTrue(first.await() is DockResult.Failure);assertEquals(TvConnectionState.Ready,tv.state.value.connection)
    }
    @Test fun fiveSecondTimeoutDoesNotReplayCommandAndRepeatedJoinKeepsOneSubscription()=runTest {
        val tv=Transport();val client=CompanionClient(tv,backgroundScope);join(client,tv)
        val command=async {client.call(DockMethod.Next,JSONObject().put("expectedRevision",0))};runCurrent();val count=tv.sent.size
        advanceTimeBy(5001);runCurrent();assertEquals("TIMEOUT",(command.await() as DockResult.Failure).code);assertEquals(count,tv.sent.size)
        val rejoin=async {client.join()};runCurrent();tv.reply(tv.sent.last(),snapshot());runCurrent();assertTrue(rejoin.await() is DockResult.Success);assertEquals(1,tv.joins)
    }
    @Test fun sharedProtocolFixturesMatchWireNamesErrorCodesAndStrictRevisionType(){
        val fixtures=JSONObject(requireNotNull(javaClass.classLoader?.getResource("companion-v1.json")).readText())
        val patch=fixtures.getJSONObject("patch")
        assertEquals(DockMethod.Patch.wire,patch.getString("method"));assertEquals(DockPatch.Clock(10).json().toString(),patch.getJSONObject("params").getJSONObject("patch").toString())
        assertEquals(1,fixtures.getJSONObject("hello").getInt("version"))
        for(name in listOf("stale","unknown","malformed","unsupported","expired")){
            val id=fixtures.getJSONObject(name).getString("id");val code=fixtures.getJSONObject("errors").getString(name)
            val reply=JSONObject().put("protocol","color-dock").put("version",1).put("id",id).put("ok",false).put("error",JSONObject().put("code",code))
            try{DockCodec.reply(reply,id);fail("Expected typed error")}catch(error:DockException){assertEquals(code,error.code)}
        }
        assertEquals(0L,DockCodec.snapshot(snapshot()).revision)
        for(invalid in listOf("0",.5,-1)){try{DockCodec.snapshot(snapshot().put("revision",invalid));fail("Expected strict revision")}catch(_:IllegalArgumentException){}}
    }
}
