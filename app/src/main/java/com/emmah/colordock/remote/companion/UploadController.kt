package com.emmah.colordock.remote.companion

import android.util.Base64
import com.emmah.colordock.remote.images.PreparedImage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.json.JSONObject
import java.io.RandomAccessFile

data class UploadProgress(val stage: String, val fraction: Float = 0f, val wallpaperId: String? = null, val message: String? = null)
class UploadController(private val dock: CompanionClient,private val pending:PendingUploadStore) {
    fun upload(image:PreparedImage):Flow<UploadProgress> = flow {
        var committed=false
        try {
            emit(UploadProgress("Preparing"))
            pending.save(image.uploadId,image.name)
            val meta=JSONObject().put("uploadId",image.uploadId).put("name",image.name).put("fit",image.fit).put("width",image.width).put("height",image.height).put("imageBytes",image.image.length()).put("thumbnailBytes",image.thumbnail.length()).put("imageHash",image.imageHash).put("thumbnailHash",image.thumbnailHash)
            val begin=required(dock.call(DockMethod.UploadBegin,meta))
            if(begin.optString("status")=="committed"){committed=true;pending.clear();emit(UploadProgress("Complete",1f,begin.getString("wallpaperId")));return@flow}
            val total=image.image.length()+image.thumbnail.length();var sent=0L
            for((stream,file) in listOf("image" to image.image,"thumbnail" to image.thumbnail)) {
                withContext(Dispatchers.IO) {RandomAccessFile(file,"r")}.use {input ->
                    var index=0;val bytes=ByteArray(dock.limits.chunkBytes)
                    while(input.filePointer<input.length()) {
                        currentCoroutineContext().ensureActive();val count=withContext(Dispatchers.IO){minOf(bytes.size.toLong(),input.length()-input.filePointer).toInt().also {input.readFully(bytes,0,it)}}
                        val params=JSONObject().put("uploadId",image.uploadId).put("stream",stream).put("index",index).put("data",Base64.encodeToString(bytes,0,count,Base64.NO_WRAP))
                        var response:DockResult<JSONObject> = dock.call(DockMethod.UploadChunk,params)
                        repeat(2){if(response is DockResult.Failure && response.code=="TIMEOUT")response=dock.call(DockMethod.UploadChunk,params)}
                        val acknowledgement=required(response)
                        val completeImage=stream=="image"&&input.filePointer==input.length()
                        require(acknowledgement.getString("stream")==if(completeImage)"thumbnail" else stream)
                        require(acknowledgement.getInt("nextIndex")==if(completeImage)0 else index+1)
                        sent+=count;index++
                        emit(UploadProgress("Sending",sent.toFloat()/total))
                    }
                }
            }
            emit(UploadProgress("Committing",1f))
            val result=dock.call(DockMethod.UploadCommit,JSONObject().put("uploadId",image.uploadId))
            val id=if(result is DockResult.Success)result.value.getString("id") else {
                val status=required(dock.call(DockMethod.UploadStatus,JSONObject().put("uploadId",image.uploadId)))
                if(status.optString("status")!="committed"){required(result);throw DockException("NOT_READY")};status.getString("wallpaperId")
            }
            committed=true;pending.clear();emit(UploadProgress("Complete",1f,id));dock.snapshot()
        }catch(cancelled:CancellationException){throw cancelled}
        catch(error:Exception){emit(UploadProgress("Failed",message=if(error is UploadException)error.message else "The upload was interrupted. Reconnect to Color Dock and try again."))}
        finally {if(!committed)withContext(NonCancellable){withTimeoutOrNull(5000){dock.call(DockMethod.UploadCancel,JSONObject().put("uploadId",image.uploadId))}}}
    }
    private class UploadException(message:String):java.io.IOException(message)
    private fun required(result:DockResult<JSONObject>):JSONObject = when(result){is DockResult.Success->result.value;is DockResult.Failure->throw UploadException(result.message)}
}
