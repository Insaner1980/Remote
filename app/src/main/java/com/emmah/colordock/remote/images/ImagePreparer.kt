package com.emmah.colordock.remote.images

import android.content.Context
import android.graphics.*
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import kotlin.coroutines.coroutineContext
import kotlin.math.min

data class PreparedImage(val uploadId: String, val name: String, val fit: String, val image: File, val thumbnail: File, val width: Int, val height: Int, val imageHash: String, val thumbnailHash: String) {
    fun delete(){image.delete();thumbnail.delete()}
}
class ImagePreparationException(message: String) : IOException(message)
class ImagePreparer(private val context: Context) {
    suspend fun prepare(uri: Uri, name: String, fit: String = "cover"): PreparedImage = withContext(Dispatchers.IO) {
        val directory=File(context.cacheDir,"uploads").apply {mkdirs()}
        val id=UUID.randomUUID().toString();val source=File(directory,"$id.source");val image=File(directory,"$id.jpg");val thumb=File(directory,"$id.thumb.jpg")
        var decoded: Bitmap?=null;var flattened: Bitmap?=null;var small: Bitmap?=null
        try {
            context.contentResolver.openInputStream(uri)?.use { input -> source.outputStream().use {out ->
                val bytes=ByteArray(16384);var total=0L
                while(true){coroutineContext.ensureActive();val count=input.read(bytes);if(count<0)break;total+=count;if(total>50L*1024*1024)throw ImagePreparationException("This picture is larger than 50 MiB.");out.write(bytes,0,count)}
            } } ?: throw ImagePreparationException("This picture is no longer accessible. Select it again.")
            val drawable=ImageDecoder.decodeDrawable(ImageDecoder.createSource(source)) { decoder,info,_ ->
                val width=info.size.width;val height=info.size.height
                if(width<1||height<1||width.toLong()*height>100_000_000)throw ImagePreparationException("This picture is larger than 100 megapixels.")
                val scale=min(1.0,min(1920.0/width,1080.0/height))
                decoder.setTargetSize(maxOf(1,(width*scale).toInt()),maxOf(1,(height*scale).toInt()))
                decoder.allocator=ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
                decoder.setOnPartialImageListener {false}
            }
            if(drawable is AnimatedImageDrawable)throw ImagePreparationException("Animated pictures are not supported. Choose a still image.")
            decoded=(drawable as? BitmapDrawable)?.bitmap?:throw ImagePreparationException("This image format is not supported.")
            val bitmap=requireNotNull(decoded)
            flattened=Bitmap.createBitmap(bitmap.width,bitmap.height,Bitmap.Config.ARGB_8888)
            Canvas(requireNotNull(flattened)).apply {drawColor(Color.rgb(17,23,25));drawBitmap(bitmap,0f,0f,null)}
            encode(requireNotNull(flattened),image,4194304)
            val scale=min(1.0,min(320.0/bitmap.width,180.0/bitmap.height))
            small=Bitmap.createScaledBitmap(requireNotNull(flattened),maxOf(1,(bitmap.width*scale).toInt()),maxOf(1,(bitmap.height*scale).toInt()),true)
            encode(requireNotNull(small),thumb,131072)
            PreparedImage(id,name.take(120).ifBlank {"Picture"},fit,image,thumb,bitmap.width,bitmap.height,sha256(image),sha256(thumb))
        } catch(error: OutOfMemoryError) {image.delete();thumb.delete();throw ImagePreparationException("This picture needs too much memory. Choose a smaller picture.")}
        catch(error:Exception){image.delete();thumb.delete();if(error is ImagePreparationException||error is kotlinx.coroutines.CancellationException)throw error;throw ImagePreparationException("This picture is corrupt or its format is not supported.")}
        finally {source.delete();small?.takeIf {it!==flattened}?.recycle();flattened?.recycle();decoded?.recycle()}
    }
    private fun encode(bitmap: Bitmap,file: File,limit: Long){
        for(quality in 90 downTo 40 step 10){file.outputStream().use {if(!bitmap.compress(Bitmap.CompressFormat.JPEG,quality,it))throw ImagePreparationException("This picture could not be prepared.")};if(file.length()<=limit)return}
        throw ImagePreparationException("This picture cannot fit within the TV image limit.")
    }
    companion object {
        fun sha256(file:File):String {val digest=MessageDigest.getInstance("SHA-256");file.inputStream().use {input->val bytes=ByteArray(16384);while(true){val count=input.read(bytes);if(count<0)break;digest.update(bytes,0,count)}};return digest.digest().joinToString(""){"%02x".format(it)}}
    }
}
