package com.emmah.colordock.remote.companion

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.UUID

class PendingUploadStore(context:Context) {
    private val file=AtomicFile(File(context.noBackupFilesDir,"pending-upload.json"))
    suspend fun save(id:String,name:String)=withContext(Dispatchers.IO){
        UUID.fromString(id)
        val output=file.startWrite()
        try{output.write(JSONObject().put("uploadId",id).put("name",name.take(120)).toString().toByteArray());file.finishWrite(output)}catch(error:Exception){file.failWrite(output);throw error}
    }
    suspend fun read():Pair<String,String>?=withContext(Dispatchers.IO){
        if(!file.baseFile.exists())return@withContext null
        runCatching{val bytes=file.openRead().use {it.readNBytes(2049)};require(bytes.size<=2048);val value=JSONObject(String(bytes,Charsets.UTF_8));val id=value.getString("uploadId");UUID.fromString(id);id to value.getString("name").take(120)}.getOrElse{file.delete();null}
    }
    suspend fun clear()=withContext(Dispatchers.IO){file.delete()}
}
