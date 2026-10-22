package com.emmah.colordock.remote.images

import android.content.Context
import android.util.Base64
import com.emmah.colordock.remote.companion.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest

class ThumbnailCache(context:Context,private val dock:CompanionClient) {
    private val directory=File(context.cacheDir,"thumbnails").apply {mkdirs()}
    private val lock=Mutex()
    suspend fun get(identity:String,item:WallpaperMetadata):File = lock.withLock {withContext(Dispatchers.IO){
        val prefix=MessageDigest.getInstance("SHA-256").digest((identity+":"+item.id).toByteArray()).joinToString(""){"%02x".format(it)}
        item.thumbnailHash?.let {hash->File(directory,"$prefix-$hash.jpg").takeIf {it.exists()}?.let {it.setLastModified(System.currentTimeMillis());return@withContext it}}
        if(!item.personal)directory.listFiles()?.firstOrNull {it.name.startsWith("$prefix-")}?.let {it.setLastModified(System.currentTimeMillis());return@withContext it}
        val bytes=ByteArrayOutputStream();var index=0;var expectedHash:String?=null;var length=0
        do {
            val reply=dock.call(DockMethod.Thumbnail,JSONObject().put("wallpaperId",item.id).put("index",index))
            val value=(reply as? DockResult.Success)?.value?:throw java.io.IOException("The picture preview is unavailable.")
            require(value.getInt("index")==index && value.getString("wallpaperId")==item.id)
            val offered=value.getString("hash");require(offered.matches(Regex("[0-9a-f]{64}")))
            if(expectedHash==null){expectedHash=offered;length=value.getInt("bytes");require(length in 1..131072)}else require(expectedHash==offered&&length==value.getInt("bytes"))
            val data=Base64.decode(value.getString("data"),Base64.NO_WRAP);require(data.size in 1..12288&&bytes.size()+data.size<=length);bytes.write(data)
            if(value.isNull("nextIndex"))break
            index++;require(value.getInt("nextIndex")==index)
        }while(bytes.size()<length)
        require(bytes.size()==length)
        val actual=MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()).joinToString(""){"%02x".format(it)};require(actual==expectedHash)
        val file=File(directory,"$prefix-$actual.jpg");file.writeBytes(bytes.toByteArray())
        var total=directory.listFiles()?.sumOf {it.length()}?:0
        for(old in directory.listFiles().orEmpty().sortedBy {it.lastModified()}){if(total<=20L*1024*1024)break;if(old!=file){val size=old.length();if(old.delete())total-=size}}
        file
    }}
}
