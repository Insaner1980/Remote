package com.emmah.colordock.remote.companion

import android.util.Base64
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import com.emmah.colordock.remote.images.PreparedImage
import com.emmah.colordock.remote.tv.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class UploadControllerTest {
    private class Transport(private val scope:CoroutineScope):DockTransport {
        override val state=MutableStateFlow(TvState(TvConnectionState.Ready))
        override val dockAvailable=MutableStateFlow(true)
        override val dockMessages=MutableSharedFlow<JSONObject>(extraBufferCapacity=20)
        val requests=mutableListOf<JSONObject>()
        val chunks=linkedMapOf<String,ByteArray>()
        val chunkArrived=CompletableDeferred<Unit>()
        var loseFirstChunk=false;var loseCommit=false;var invalidAck=false;var holdChunk=false
        private var imageBytes=0L;private var committed=false
        override suspend fun joinDockChannel()=Unit
        override fun sendDock(payload:JSONObject) {
            requests.add(JSONObject(payload.toString()))
            val params=payload.getJSONObject("params")
            val result=when(payload.getString("method")) {
                "hello"->JSONObject("""{"protocolVersion":1,"sessionId":"00000000-0000-4000-8000-000000000099","limits":{"chunkBytes":12288,"imageBytes":4194304,"thumbnailBytes":131072}}""")
                "state.get"->JSONObject("""{"revision":1,"settings":{"wallpaper":{"current":"a","pool":["a"],"mode":"fixed","trigger":"open"},"clock":{"idleMinutes":0},"weather":{"enabled":false,"location":null},"shelf":{"order":["one"],"hidden":[]}},"currentWallpaper":{"id":"a","title":"First","fit":"cover"},"galleryCounts":{"personal":1,"builtin":139},"clockActive":false}""")
                "upload.begin"->{imageBytes=params.getLong("imageBytes");JSONObject().put("status","receiving")}
                "upload.chunk"->{
                    chunkArrived.complete(Unit)
                    if(holdChunk)return
                    val stream=params.getString("stream");val index=params.getInt("index");val key="$stream:$index"
                    val bytes=Base64.decode(params.getString("data"),Base64.NO_WRAP)
                    chunks[key]?.let {assertArrayEquals(it,bytes)}?:run {chunks[key]=bytes}
                    if(loseFirstChunk){loseFirstChunk=false;return}
                    val imageComplete=stream=="image"&&chunks.filterKeys {it.startsWith("image:")}.values.sumOf {it.size.toLong()}==imageBytes
                    JSONObject().put("stream",if(imageComplete)"thumbnail" else stream).put("nextIndex",if(invalidAck)999 else if(imageComplete)0 else index+1)
                }
                "upload.commit"->{committed=true;if(loseCommit)return;JSONObject().put("id","user:fixture")}
                "upload.status"->JSONObject().put("status",if(committed)"committed" else "receiving").put("wallpaperId","user:fixture")
                "upload.cancel"->JSONObject().put("cancelled",true)
                else->error("Unexpected method")
            }
            scope.launch {dockMessages.emit(JSONObject().put("protocol","color-dock").put("version",1).put("id",payload.getString("id")).put("ok",true).put("result",result))}
        }
        fun count(method:String)=requests.count {it.getString("method")==method}
    }
    private class Harness(val transport:Transport,val client:CompanionClient,val pending:PendingUploadStore,val image:PreparedImage) {
        val controller=UploadController(client,pending)
    }
    private suspend fun harness(action:suspend Harness.()->Unit) {
        // Instrumentation runs as the target UID. Isolate its journal in an owned cache folder.
        val target=InstrumentationRegistry.getInstrumentation().targetContext
        val directory=File(target.cacheDir,"upload-test-${UUID.randomUUID()}").apply {check(mkdirs())}
        val context=object:ContextWrapper(target){override fun getNoBackupFilesDir()=directory}
        val imageFile=File(directory,"image.jpg").apply {writeBytes(ByteArray(12295){(it%251).toByte()})}
        val thumbnail=File(directory,"thumbnail.jpg").apply {writeBytes(ByteArray(111){(it%127).toByte()})}
        val image=PreparedImage(UUID.randomUUID().toString(),"Controller fixture","contain",imageFile,thumbnail,1920,1080,"image-hash","thumbnail-hash")
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        val transport=Transport(scope);val client=CompanionClient(transport,scope);val pending=PendingUploadStore(context)
        try {assertTrue(client.join() is DockResult.Success);Harness(transport,client,pending,image).action()}
        finally {client.disconnect();scope.cancel();pending.clear();directory.deleteRecursively()}
    }
    @Test fun lostChunkAcknowledgementRetriesIdenticalBytesThenCommits()=runBlocking {harness {
        transport.loseFirstChunk=true
        val progress=controller.upload(image).toList()
        assertEquals("Complete",progress.last().stage)
        assertEquals(4,transport.count("upload.chunk"));assertEquals(1,transport.count("upload.commit"))
        assertEquals(0,transport.count("upload.cancel"));assertNull(pending.read())
        val retries=transport.requests.filter {it.getString("method")=="upload.chunk"}.take(2)
        assertEquals(retries[0].getJSONObject("params").toString(),retries[1].getJSONObject("params").toString())
        assertArrayEquals(image.image.readBytes(),transport.chunks.filterKeys {it.startsWith("image:")}.values.fold(byteArrayOf()){all,bytes->all+bytes})
        assertArrayEquals(image.thumbnail.readBytes(),transport.chunks.getValue("thumbnail:0"))
    }}
    @Test fun lostCommitAcknowledgementChecksStatusWithoutCommittingTwice()=runBlocking {harness {
        transport.loseCommit=true
        val progress=controller.upload(image).toList()
        assertEquals("Complete",progress.last().stage);assertEquals("user:fixture",progress.last().wallpaperId)
        assertEquals(1,transport.count("upload.commit"));assertEquals(1,transport.count("upload.status"))
        assertEquals(0,transport.count("upload.cancel"));assertNull(pending.read())
    }}
    @Test fun invalidAcknowledgementCannotReportSuccessAndKeepsRecoveryJournal()=runBlocking {harness {
        transport.invalidAck=true
        val progress=controller.upload(image).toList()
        assertEquals("Failed",progress.last().stage);assertFalse(progress.any {it.stage=="Complete"})
        assertEquals(0,transport.count("upload.commit"));assertEquals(1,transport.count("upload.cancel"))
        assertEquals(image.uploadId,pending.read()?.first)
        assertTrue(image.image.exists())
    }}
    @Test fun cancellationStopsTransferAndSendsOneBoundedCancel()=runBlocking {harness {
        transport.holdChunk=true
        val progress=mutableListOf<UploadProgress>()
        val transfer=launch {controller.upload(image).toList(progress)}
        withTimeout(5000){transport.chunkArrived.await()};transfer.cancelAndJoin()
        assertEquals(1,transport.count("upload.chunk"));assertEquals(0,transport.count("upload.commit"))
        assertEquals(1,transport.count("upload.cancel"));assertFalse(progress.any {it.stage=="Complete"})
    }}
}
